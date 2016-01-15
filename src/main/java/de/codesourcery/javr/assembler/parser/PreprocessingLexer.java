package de.codesourcery.javr.assembler.parser;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.Stack;
import java.util.function.Consumer;

import org.apache.commons.lang3.Validate;

import de.codesourcery.javr.assembler.Address;
import de.codesourcery.javr.assembler.CompilationUnit;
import de.codesourcery.javr.assembler.ICompilationContext;
import de.codesourcery.javr.assembler.ResourceFactory;
import de.codesourcery.javr.assembler.Segment;
import de.codesourcery.javr.assembler.arch.IArchitecture;
import de.codesourcery.javr.assembler.exceptions.ParseException;
import de.codesourcery.javr.assembler.parser.Parser.CompilationMessage;
import de.codesourcery.javr.assembler.parser.ast.ASTNode;
import de.codesourcery.javr.assembler.parser.ast.FunctionCallNode;
import de.codesourcery.javr.assembler.parser.ast.IValueNode;
import de.codesourcery.javr.assembler.parser.ast.IdentifierNode;
import de.codesourcery.javr.assembler.parser.ast.OperatorNode;
import de.codesourcery.javr.assembler.parser.ast.Resolvable;
import de.codesourcery.javr.assembler.symbols.Symbol;
import de.codesourcery.javr.assembler.symbols.SymbolTable;

public class PreprocessingLexer implements Lexer 
{
	private final CompilationUnit unit;

	private final Stack<Lexer> lexerStack = new Stack<>();
	private Lexer currentLexer;

	private final ResourceFactory resourceFactory;
	private final IArchitecture architecture;
	private boolean isIgnoreWhitespace=true;
	private final SymbolTable symbols = new SymbolTable("preproc");
	private final List<Token> tokens = new ArrayList<>();
	private final List<Token> unprocessedTokens = new ArrayList<>();

	private int currentIfNestingDepth; // current #if nesting level

	private int stoppedAtNestingDepth=-1; // #if nesting level where we started to skip tokens because of an unsatisfied conditional expression

	private final WrappingContext compilationContext = new WrappingContext();

	private final Set<String> expanded = new HashSet<>();

	// offset to the actual scanner position that is adjusted 
	// each time some expression/identifier gets macro-expanded 
	private int expansionOffset = 0;

	protected final class MacroDefinition 
	{
		public final List<Token> parameterNames;
		public final List<Token> tokens;

		public MacroDefinition(List<Token> parameterNames,List<Token> tokens) 
		{
			final Set<String> names = new HashSet<>();
			for ( Token t : parameterNames ) 
			{
				if ( names.contains( t.value ) ) {
					throw new ParseException("Macro definition has duplicate parameter name '"+t.value+"'",t);
				}
				names.add( t.value );
			}
			this.parameterNames = parameterNames;
			this.tokens = tokens;
		}

		public int getIndexForParameterName(String name) {
			for ( int i = 0 , len=parameterNames.size() ; i < len ; i++ ) {
				if ( parameterNames.get(i).value.equals(name) ) {
					return i;
				}
			}
			return -1;
		}
	}

	/**
	 * 
	 * @param delegate
	 * @param unit
	 * @param arch required for creating a fake <code>ICompilationContext</code> when parsing conditional expressions in #if statements
	 * @param resourceFactory required to resolve #include files 
	 */
	public PreprocessingLexer(Lexer delegate,CompilationUnit unit,IArchitecture arch,ResourceFactory resourceFactory) 
	{
		Validate.notNull(delegate, "delegate must not be NULL");
		Validate.notNull(unit, "unit must not be NULL");
		Validate.notNull(arch, "arch must not be NULL");
		Validate.notNull(resourceFactory, "rf must not be NULL");
		this.unit = unit;
		this.architecture = arch;
		this.resourceFactory = resourceFactory;
		pushLexer( delegate );
	}

	private void pushLexer(Lexer l) 
	{
		l.setIgnoreWhitespace( false );		
		lexerStack.push(l);
		currentLexer = l;
	}

	@Override
	public boolean eof() {
		if ( tokens.isEmpty() ) {
			parse();
		}
		return tokens.isEmpty();
	}

	private Token adjustOffset(Token token) 
	{
		token.offset += expansionOffset;
		return token;
	}

	private Token consume() 
	{
		return adjustOffset( lexer().next() );
	}

	private void parse() 
	{
		unprocessedTokens.clear();

		// process tokens up to the next EOL or EOF (=full line)
		while ( true ) 
		{ 
			final Token next = consume();
			final boolean compilationDisabled = stoppedAtNestingDepth != -1;
			final boolean compilationEnabled = ! compilationDisabled;

			if ( next.isEOF() ) 
			{
				if (  currentIfNestingDepth > 0 ) {
					throw new ParseException("Expected "+currentIfNestingDepth+" more #endif",next);
				} 
				unprocessedTokens.add( next );
				expandTokens();
				return;
			}

			if ( next.isEOL() && compilationEnabled ) 
			{
				unprocessedTokens.add( next );
				expandTokens();
				return;
			}

			// parse single-line comments			
			if ( isSingleLineComment(next , lexer().peek() ) && compilationEnabled ) 
			{
				expandTokens();				
				tokens.add( next );
				processTokensUntilEndOfLine( tokens::add );
				return;
			}

			// parse multi-line comments		
			if ( isMultiLineCommentStart( next , lexer().peek() ) && compilationEnabled ) 
			{
				expandTokens();

				tokens.add( next );
				tokens.add( consume() );
				while ( true ) 
				{
					Token tok = consume();
					tokens.add(tok);
					if ( tok.isEOF() || ( tok.isOperator("*") && lexer().peek().isOperator( "/" ) ) )
					{
						return;
					}
				}
			}

			// skip string literals
			if ( ( next.is( TokenType.SINGLE_QUOTE ) || next.is( TokenType.DOUBLE_QUOTE ) ) && compilationEnabled ) 
			{
				expandTokens();

				tokens.add( next );
				TokenType endType = next.type;
				while ( true ) 
				{
					Token tok = consume();
					tokens.add(tok);
					if ( tok.isEOLorEOF() || tok.is( endType ) )
					{
						return;
					}
				}				
			}

			if ( ! next.is(TokenType.HASH ) ) 
			{
				if ( compilationEnabled ) {
					unprocessedTokens.add( next );
				}
				continue;
			}

			/*
			 * Preprocessor directive.
			 */
			final String directive = lexer().peek().value;

			if ( directive.equalsIgnoreCase("endif" ) ) 
			{
				consume();
				if ( currentIfNestingDepth == 0 ) 
				{
					throw new ParseException("#endif without matching #if", next );
				}
				currentIfNestingDepth--;
				if ( compilationDisabled && stoppedAtNestingDepth == currentIfNestingDepth ) 
				{
					stoppedAtNestingDepth = -1;
				}
				skipToNextLine();
				continue;					
			} 
			else if ( directive.equalsIgnoreCase("if") || directive.equalsIgnoreCase("ifdef" ) || directive.equalsIgnoreCase("ifndef" ) ) // #if
			{
				currentIfNestingDepth++;
				
				if ( compilationDisabled ) {
					continue;
				}
				
				consume(); // consume 'if' / 'ifdef' / 'ifndef'
				expandTokens();
				skipWhitespace();
				
				final boolean boolValue;
				if ( directive.equalsIgnoreCase("ifdef" ) || directive.equalsIgnoreCase("ifndef" ) ) // shorthand syntax for '#if [!] defined( IDENTIFIER) 
				{
					final Token idNode = lexer().peek();
					if ( ! idNode.isValidIdentifier() ) 
					{
						throw new ParseException("Identifier expected", idNode );
					}
					consume();
					
					final Identifier varName = new Identifier( idNode.value );
					if ( directive.equalsIgnoreCase("ifdef" ) ) {
						boolValue = symbols.isDefined( varName );
					} else {
						boolValue = ! symbols.isDefined( varName );
					}
				} 
				else 
				{
					final List<Token> tmp = new ArrayList<>();
					// gather tokens of the conditional expression up to the start of a comment or EOL/EOF
					final List<Token> expression = new ArrayList<>();
					boolean commentFound = false;
					while ( true ) 
					{
						if ( lexer().peek().isEOLorEOF() ) {
							break;
						}
						final Token tok = consume();
						tmp.add( tok );						
						if ( ! commentFound ) 
						{
							if ( isCommentStart( tok , lexer().peek() ) ) {
								break;
							}
							expression.add( tok );
						}
					}
					if ( expression.isEmpty() ) {
						throw new ParseException("Expected an expression", tmp.get( tmp.size()-1 ) );
					}
					final IValueNode exprAST = parseExpression( expression );
					if ( exprAST == null ) {
						throw new ParseException("Failed to parse expression",expression.get(0) );
					}		
					final Object value = evaluateExpression(exprAST);
					if ( value == null ) {
						throw new ParseException("Failed to evaluate expression",expression.get(0) );
					}
					if ( !(value instanceof Boolean) ) {
						throw new ParseException("Expression needs to evaluate to a boolean value but was "+value,expression.get(0) );
					}
					boolValue = ((Boolean) value).booleanValue();					
				}

				if ( ! boolValue ) 
				{
					this.stoppedAtNestingDepth = currentIfNestingDepth-1;
				}
				skipToNextLine();
				continue;
			} 

			if ( directive.equalsIgnoreCase("define" ) ) // #define 
			{
				consume(); // swallow 'define'
				expandTokens();
				skipWhitespace();

				if ( ! lexer().peek().isValidIdentifier() ) 
				{
					throw new ParseException("Expected an identifier",lexer().peek());
				}
				final Token macroName = consume();
				final Identifier macroId = new Identifier( macroName.value );

				skipWhitespace();

				// parse body
				final List<Token> argumentNames = new ArrayList<>();						
				if ( lexer().peek( TokenType.PARENS_OPEN ) ) { // #define func(a,b,c)

					consume();

					skipWhitespace();

					while ( ! ( lexer().peek().isEOLorEOF() || lexer().peek(TokenType.PARENS_CLOSE ) ) ) 
					{
						if ( ! lexer().peek().isValidIdentifier() ) {
							throw new ParseException("Expected an identifier", lexer().peek() );
						}
						final Token argName = consume();

						argumentNames.add( argName );
						skipWhitespace();
						if ( ! lexer().peek( TokenType.COMMA ) ) {
							break;
						}
						consume(); // consume comma
						skipWhitespace();
					}
					skipWhitespace();
					if ( ! lexer().peek(TokenType.PARENS_CLOSE ) ) 
					{
						throw new ParseException("Unclosed macro definition", lexer().peek() );
					}
					consume();
				}
				skipWhitespace();						
				final List<Token> macroBody = new ArrayList<>();
				processTokensUntilEndOfLine( t -> {
					macroBody.add(t);
				} );
				if ( macroBody.isEmpty() ) {
					macroBody.add( new Token(TokenType.DIGITS,"1" , macroName.offset ) );
				}
				if ( symbols.isDeclared(macroId) ) 
				{
					error("Duplicate identifier: '"+macroName.value+"'", macroName );
				} 
				else 
				{
					final Symbol symbol = new Symbol(macroId,Symbol.Type.PREPROCESSOR_MACRO,unit,null);
					symbol.setValue( new MacroDefinition( argumentNames , macroBody ) );
					symbols.defineSymbol( symbol );
				}
				skipToNextLine();
				continue;
			} 
		}
	}

	private boolean isCommentStart(final Token current,Token next) 
	{
		return isSingleLineComment( current , next ) || isMultiLineCommentStart(current,next);
	}

	private boolean isMultiLineCommentStart(final Token current,Token next) {
		return current.isOperator( "/" ) && next.isOperator( "*" );
	}

	private boolean isSingleLineComment(final Token currentToken,Token nextToken) {
		return currentToken.is( TokenType.SEMICOLON ) || ( currentToken.isOperator( "/" ) && nextToken.isOperator( "/" ) );
	}

	private void error(String message,Token tok) 
	{
		unit.addMessage( CompilationMessage.error( message , tok.region() ) );
	}

	private void expandTokens() 
	{
		expanded.clear();
		expand( unprocessedTokens, expanded );
		tokens.addAll( unprocessedTokens );
		unprocessedTokens.clear();
	}

	private void expand(List<Token> tokens,Set<String> alreadyExpandedMacros) 
	{
		final Set<String> expanded = new HashSet<>();
		boolean anyIdentifiersExpanded = false;

		int totalOffset = 0;
		for (int tokenIdx = 0 ; tokenIdx < tokens.size() ; tokenIdx++) 
		{
			final Token macroName = tokens.get(tokenIdx);
			if ( macroName.isValidIdentifier() ) // check whether the identifier refers to a macro definition
			{
				final Optional<Symbol> optSymbol = symbols.maybeGet( new Identifier( macroName.value ) );
				if ( ! optSymbol.isPresent() || alreadyExpandedMacros.contains( macroName.value ) ) // prevent infinite expansion
				{
					continue;
				}
				expanded.add( macroName.value );
				anyIdentifiersExpanded = true;

				final Symbol symbol = optSymbol.get();
				final MacroDefinition macroDef = (MacroDefinition) symbol.getValue();		

				// check whether first non-whitespace token is opening parenthesis (=start of argument list) 
				boolean hasParameters=false;
				int openingParensIdx = tokenIdx+1;
				for ( final int len = tokens.size() ; openingParensIdx < len ; openingParensIdx++) 
				{
					if ( tokens.get(openingParensIdx).isNoWhitespace() ) {
						hasParameters = tokens.get(openingParensIdx).is( TokenType.PARENS_OPEN );
						break;
					}
				}

				// parse argument list (if any)
				final List<List<Token>> macroParameters;
				int lastArgIdx = hasParameters ? openingParensIdx+1 : tokenIdx;
				if ( hasParameters ) 
				{
					macroParameters = new ArrayList<>();
					List<Token> currentArg = new ArrayList<>();
					boolean commaExpected = false;
					for ( final int len = tokens.size() ; lastArgIdx < len ; lastArgIdx++ ) 
					{
						final Token arg = tokens.get(lastArgIdx);
						if ( arg.isWhitespace() ) {
							continue;
						}
						if ( arg.is( TokenType.PARENS_CLOSE ) ) {
							break;
						}
						if ( arg.is( TokenType.COMMA ) ) 
						{
							if ( ! commaExpected ) {
								throw new ParseException("Stray comma" , arg );
							}
							macroParameters.add( currentArg );
							currentArg = new ArrayList<>();
							commaExpected = false;
							continue;
						}
						currentArg.add( arg );
						commaExpected = true;
					}

					if ( ! currentArg.isEmpty() ) {
						macroParameters.add( currentArg );
					}
					if ( lastArgIdx >= tokens.size() || tokens.get(lastArgIdx).isNot( TokenType.PARENS_CLOSE ) ) 
					{
						throw new ParseException("Unterminated macro argument list", tokens.get(lastArgIdx-1) );
					}
				} else {
					macroParameters = Collections.emptyList();
				}

				if( macroParameters.size() != macroDef.parameterNames.size() ) 
				{
					throw new ParseException("Expected "+macroDef.parameterNames.size()+" arguments but got "+macroParameters.size(),macroName);
				}				

				final List<Token> macroBody = macroDef.tokens;

				final int expandedLength;
				int replacedTokensLen = 0;
				if ( macroBody.isEmpty() ) // expand #define IDENTIFIER
				{
					expandedLength= 1; // just replace the identifier with '1'
					replacedTokensLen = macroName.value.length();
					tokens.set( tokenIdx , new Token( TokenType.DIGITS , "1" , macroName.offset ) );
				} 
				else 
				{
					// expand #define IDENTIFIER(a,b,...) EXPRESSION
					for ( int count = lastArgIdx ; count >= tokenIdx ; count-- ) {
						replacedTokensLen += tokens.remove( tokenIdx ).value.length();
					}
					int offset = macroName.offset;
					int ptr = tokenIdx;
					for ( int j = 0 ; j < macroBody.size() ; j++ ) 
					{
						final Token bodyToken = macroBody.get(j );
						// check whether a token from the macro body needs to replaced with a parameter from the invocation
						final int paramIdx = bodyToken.isValidIdentifier() ? macroDef.getIndexForParameterName( bodyToken.value ) : -1;
						final List<Token> paramValues = paramIdx == -1 ? Collections.singletonList( bodyToken ) : macroParameters.get(paramIdx );
						for (int i = paramValues.size()-1 ; i >= 0 ; i--) { // iterate in reverse order , tokens.add(index,tok ) will reverse the order again
							Token paramValue = paramValues.get(i);
							final Token expr = paramValue.copyWithOffset( offset );
							offset += expr.value.length();
							tokens.add( ptr , expr );
							ptr += 1;
						}
						tokenIdx += paramValues.size();
					}
					expandedLength = offset-macroName.offset;
				}
				// calculate delta between size of expanded identifier and expansion
				final int delta = expandedLength - replacedTokensLen;
				// adjust any tokens remaining in our queue
				for (int j = tokenIdx ; j < tokens.size() ; j++ ) {
					tokens.get(j).offset += delta;
				}
				totalOffset += delta;
			}
		}
		expansionOffset += totalOffset;
		alreadyExpandedMacros.addAll( expanded );
		if ( anyIdentifiersExpanded ) // recursively try to expand more
		{
			expand( tokens , alreadyExpandedMacros );
		}
	}

	private void processTokensUntilEndOfLine(Consumer<Token> consumer) 
	{
		while ( true ) {
			final Token tok = lexer().peek();
			if ( tok.isEOLorEOF() )
			{
				return;
			}
			consumer.accept( consume() );
		}
	}

	@Override
	public Token next() 
	{
		while ( true ) 
		{
			if ( tokens.isEmpty() ) 
			{
				parse();
			}
			final boolean isWhitespace = tokens.get(0).isWhitespace();
			if ( ! isWhitespace || ! isIgnoreWhitespace )  {
				return tokens.remove(0);
			}
			// advance to next non-whitespace token
			for ( int i = 0 , len = tokens.size() ; i < len ; i++ ) 
			{
				final Token tok = tokens.get(i );
				if ( ! tok.isWhitespace() ) 
				{
					for ( ; i > 0 ; i-- ) { // discard all tokens we skipped
						tokens.remove(i);
					}
					return tok;
				}
			}
			tokens.clear();
		}
	}

	private void skipWhitespace(List<Token> toAdd) 
	{
		while ( true ) 
		{
			final Token tok = lexer().peek();
			if ( ! tok.isWhitespace() ) {
				return;
			}
			toAdd.add( consume() );
		}
	}

	private void skipWhitespace() 
	{
		while ( lexer().peek().isWhitespace() ) 
		{
			consume();
		}
	}	

	private void skipToNextLine() 
	{
		while(true) {
			Token tok = lexer().peek();
			if ( tok.isEOL() ) 
			{
				consume();
				return;
			}
			if ( tok.isEOF() ) {
				return;
			}
			consume();
		}
	}		

	@Override
	public Token peek() 
	{
		if ( tokens.isEmpty() ) 
		{
			parse();
		}
		int i = 0;
		while ( true ) 
		{
			final boolean isWhitespace = tokens.get(i).isWhitespace();
			if ( ! isWhitespace || ! isIgnoreWhitespace )  {
				return tokens.remove(i);
			}
			// advance to next non-whitespace token
			for ( int len = tokens.size() ; i < len ; i++ ) 
			{
				final Token tok = tokens.get(i );
				if ( ! tok.isWhitespace() ) 
				{
					return tok;
				}
			}
			parse();
		}
	}

	@Override
	public boolean peek(TokenType t) {
		return peek().is( t );
	}

	@Override
	public void setIgnoreWhitespace(boolean ignoreWhitespace) 
	{
		this.isIgnoreWhitespace = ignoreWhitespace;
	}

	@Override
	public boolean isIgnoreWhitespace() 
	{
		return this.isIgnoreWhitespace; 
	}

	@Override
	public void pushBack(Token tok) 
	{
		Validate.notNull(tok, "tok must not be NULL");
		tokens.add( 0, tok );
	}

	private Object evaluateExpression(IValueNode node) 
	{
		node.visitDepthFirst( (n,ctx) -> 
		{
			if ( n instanceof Resolvable) 
			{
				((Resolvable) n).resolve( compilationContext );
			}
		});
		return node.getValue();
	}

	private IValueNode parseExpression(List<Token> tokens) 
	{
		return (IValueNode) Parser.parseExpression( new WrappingLexer(tokens ) );
	}

	private Lexer lexer() {
		return currentLexer;
	}

	protected final class WrappingContext implements ICompilationContext {

		@Override
		public void pushCompilationUnit(CompilationUnit unit) { throw new UnsupportedOperationException(); }

		@Override
		public void popCompilationUnit() { throw new UnsupportedOperationException(); }

		@Override
		public ResourceFactory getResourceFactory() { return resourceFactory; }

		@Override
		public ICompilationSettings getCompilationSettings() { throw new UnsupportedOperationException(); }

		@Override
		public SymbolTable globalSymbolTable() { throw new UnsupportedOperationException(); }

		@Override
		public SymbolTable currentSymbolTable() { return symbols; }

		@Override
		public CompilationUnit currentCompilationUnit() { throw new UnsupportedOperationException(); }

		@Override
		public int currentOffset() { throw new UnsupportedOperationException(); }

		@Override
		public Address currentAddress() { throw new UnsupportedOperationException(); }

		@Override
		public Segment currentSegment() { throw new UnsupportedOperationException(); }

		@Override
		public void setSegment(Segment s) { throw new UnsupportedOperationException(); }

		@Override
		public void writeByte(int value) { throw new UnsupportedOperationException(); }

		@Override
		public void writeWord(int value) { throw new UnsupportedOperationException(); }

		@Override
		public void allocateByte() { throw new UnsupportedOperationException(); }

		@Override
		public void allocateWord() { throw new UnsupportedOperationException(); }

		@Override
		public void allocateBytes(int numberOfBytes) { throw new UnsupportedOperationException(); }

		@Override
		public void error(String message, ASTNode node) { throw new UnsupportedOperationException(); }

		@Override
		public void message(CompilationMessage msg) { throw new UnsupportedOperationException(); }

		@Override
		public IArchitecture getArchitecture() { return architecture; }

	}

	protected static final class WrappingLexer implements Lexer {

		private final List<Token> tokens;
		private final int lastOffset;

		private boolean ignoreWhitespace = true;

		public WrappingLexer(List<Token> tokens) {
			if ( tokens.isEmpty() ) {
				throw new IllegalArgumentException("At least one token needs to be present"); // ...otherwise we cannot get the offset at EOF
			}
			this.tokens = tokens;
			final Token lastToken = tokens.get(tokens.size()-1);
			this.lastOffset = lastToken.offset+lastToken.value.length();
		}

		@Override
		public boolean eof() {
			return tokens.isEmpty();
		}

		@Override
		public Token next() 
		{
			while ( ignoreWhitespace && ! tokens.isEmpty() && tokens.get(0).isWhitespace() ) {
				tokens.remove(0);
			}
			if ( tokens.isEmpty() ) {
				return new Token(TokenType.EOF,"",lastOffset);
			}
			return tokens.remove(0);
		}

		@Override
		public Token peek() 
		{
			if ( ignoreWhitespace ) 
			{
				for ( int i = 0,len=tokens.size() ; i < len ; i++ ) 
				{
					final Token tok = tokens.get(i);
					if ( ! tok.isWhitespace() ) {
						return tok;
					}
				}
				return new Token(TokenType.EOF,"",lastOffset);
			}
			if ( tokens.isEmpty() ) {
				return new Token(TokenType.EOF,"",lastOffset);
			}
			return tokens.get(0);
		}

		@Override
		public boolean peek(TokenType t) {
			return peek().is( t );
		}

		@Override
		public void setIgnoreWhitespace(boolean ignoreWhitespace) 
		{
			this.ignoreWhitespace = ignoreWhitespace;
		}

		@Override
		public boolean isIgnoreWhitespace() {
			return ignoreWhitespace;
		}

		@Override
		public void pushBack(Token tok) 
		{
			Validate.notNull(tok, "tok must not be NULL");
			tokens.add(0,tok);
		}	    
	}
}