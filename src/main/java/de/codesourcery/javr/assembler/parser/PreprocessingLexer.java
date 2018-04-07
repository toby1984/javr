/**
 * Copyright 2015-2018 Tobias Gierke <tobias.gierke@code-sourcery.de>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.codesourcery.javr.assembler.parser;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.Stack;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.apache.commons.lang3.Validate;

import de.codesourcery.javr.assembler.CompilationUnit;
import de.codesourcery.javr.assembler.ICompilationContext;
import de.codesourcery.javr.assembler.exceptions.ParseException;
import de.codesourcery.javr.assembler.parser.Parser.CompilationMessage;
import de.codesourcery.javr.assembler.parser.Parser.Severity;
import de.codesourcery.javr.assembler.parser.ast.IValueNode;
import de.codesourcery.javr.assembler.parser.ast.Resolvable;
import de.codesourcery.javr.assembler.symbols.Symbol;
import de.codesourcery.javr.assembler.symbols.Symbol.Type;
import de.codesourcery.javr.assembler.symbols.SymbolTable;
import de.codesourcery.javr.assembler.util.Resource;

/**
 * Lexer implementation that wraps a regular lexer and
 * processes and <b>removes</b> all preprocessor directives from the token stream.
 *
 * <p>This class takes adjusts the offsets of the tokens provided by the wrapped 
 * lexer so that they reflect the true position inside the expanded/parsed source.</p>
 * 
 * @author tobias.gierke@code-sourcery.de
 */
public class PreprocessingLexer implements Lexer 
{
    private static final boolean DEBUG = false;

    // stack of lexers with the current lexer being TOS,
    // used when processing #include 
    private final Stack<Lexer> lexerStack = new Stack<>();

    // performance optimization to avoid having to call lexerStack.peek() all the time, always holds the lexer that is at TOS
    private Lexer currentLexer;

    private boolean isIgnoreWhitespace=true;

    // queue of preprocessed tokens that next() and peek() draw their data from 
    private final List<Token> tokens = new ArrayList<>();

    // populated by parse() method, holds tokens that still need to be expand()ed
    private final List<Token> unexpandedTokens = new ArrayList<>();

    // set of compilation units that were included,parsed and EOF was handled for
    private final Set<CompilationUnit> unitsPopped = new HashSet<>();

    private int currentIfNestingDepth; // current #if nesting level

    private int stoppedAtNestingDepth=-1; // #if nesting level where we started to skip tokens because of an unsatisfied conditional expression

    private final ICompilationContext compilationContext;

    // offset to the actual scanner position that is adjusted 
    // by the delta ( initial string len - expanded string len )
    // each time some expression/identifier gets macro-expanded 
    private int expansionOffset = 0;

    // used to remember the EOF offset from the initial lexer ;
    // since lexers get popped from the stack whenever they reach EOF
    // and are thus inaccessible afterwards, the offset needs to be stored somewhere
    // else
    private int eofOffset = -1;
    private int eofLine = -1;
    private int eofColumn = -1;

    /**
     * Holds a macro definition (parameter names and macro body).
     *
     * @author tobias.gierke@code-sourcery.de
     */
    private static final class MacroDefinition 
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

        public int parameterCount() {
            return parameterNames.size();
        }

        public boolean takesParameters() {
            return ! parameterNames.isEmpty();
        }

        public int getIndexForParameterName(String name) 
        {
            for ( int i = 0 , len=parameterNames.size() ; i < len ; i++ ) {
                if ( parameterNames.get(i).value.equals(name) ) {
                    return i;
                }
            }
            return -1;
        }
        
        @Override
        public String toString() { // use for debugging only !
            StringBuilder buffer = new StringBuilder();
            for ( Token tok : tokens ) 
            {
                buffer.append( tok.value );
            }
            return buffer.toString();
        }
    }

    /**
     * 
     * @param delegate
     * @param unit
     * @param arch required for creating a fake <code>ICompilationContext</code> when parsing conditional expressions in #if statements
     * @param resourceFactory required to resolve #include files 
     */
    public PreprocessingLexer(Lexer delegate,ICompilationContext context) 
    {
        Validate.notNull(delegate, "delegate must not be NULL");
        Validate.notNull(context, "context must not be NULL");
        this.compilationContext = context;
        pushLexer( delegate );
    }	

    private void pushLexer(Lexer l) 
    {
        l.setIgnoreWhitespace( false );		
        lexerStack.push(l);
        currentLexer = l;
    }

    private Lexer popLexer() 
    {
        final Lexer result = lexerStack.pop();
        result.setIgnoreWhitespace( true );
        currentLexer = lexerStack.isEmpty() ? null : lexerStack.peek();
        compilationContext.popCompilationUnit();  
        return result;
    }

    @Override
    public boolean eof() 
    {
        return peek().isEOF();
    }

    private Token adjustOffset(Token token) 
    {
        // token.offset += expansionOffset;
        return token;
    }

    private Token consume() 
    {
        return adjustOffset( lexer().next() );
    }
    
    private void parse() 
    {
        final boolean noTokens = tokens.isEmpty();
        internalParse();
        if ( noTokens && tokens.isEmpty() ) {
            throw new RuntimeException("Internal error,parse() did not add any tokens ??");
        }
    }

    private void internalParse() 
    {
        if ( lexerStack.isEmpty() ) 
        {
            if ( eofOffset == -1 ) {
                throw new RuntimeException("Internal error, no more lexers but EOF offset not set ?");
            }
            tokens.add( new Token(TokenType.EOF , "" , eofOffset , eofLine , eofColumn) );
            return;
        }
        unexpandedTokens.clear();

        // processes tokens up to the next EOL or EOF (=full line)
        while ( true ) 
        { 
            final Token next = consume();
            final boolean compilationDisabled = stoppedAtNestingDepth != -1;
            final boolean compilationEnabled = ! compilationDisabled;

            if ( next.isEOF() ) 
            {
                if ( lexerStack.size() == 1 ) // swallow EOF token unless we're at the top level
                {
                    unexpandedTokens.add( next );
                }

                try {
                    expandTokens();
                } 
                finally 
                {
                    if ( ! unitsPopped.contains( compilationContext.currentCompilationUnit() ) ) 
                    {
                        unitsPopped.add( compilationContext.currentCompilationUnit() );
                        final Lexer popped = popLexer();
                        
                        if ( lexerStack.isEmpty() ) 
                        {
                            final Token lastToken = popped.peek();
                            if ( ! lastToken.is(TokenType.EOF ) ) {
                                throw new RuntimeException("Internal error, expected lexer to be at EOF");
                            }
                            eofOffset = lastToken.offset;
                            eofLine = lastToken.line;
                            eofColumn = lastToken.column;
                        }
                    }			
                }
                if (  currentIfNestingDepth > 0 ) {
                    throw new ParseException("Expected "+currentIfNestingDepth+" more #endif",next);
                }
                internalParse();
                return;
            }

            if ( next.isEOL() && compilationEnabled ) 
            {
                unexpandedTokens.add( next );
                expandTokens();
                return;
            }

            // parse single-line comments			
            if ( isSingleLineComment(next , lexer().peek() ) && compilationEnabled ) 
            {
                expandTokens();			
                // directly added to tokens since comments must never be expanded
                tokens.add( next );
                processTokensUntilEndOfLine( tokens::add );
                return;
            }

            // parse multi-line comments		
            if ( isMultiLineCommentStart( next , lexer().peek() ) && compilationEnabled ) 
            {
                expandTokens();

                // directly added to tokens since comments must never be expanded
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

                // directly added to tokens since stuff in string literals never gets expanded 
                tokens.add( next );
                final TokenType endType = next.type;
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
                    unexpandedTokens.add( next );
                }
                continue;
            }

            if ( "pragma".equalsIgnoreCase( lexer().peek().value ) )  // TODO: #pragma is currently ignored
            {
                expandTokens();

                tokens.add( next ); // add hash
                while ( true ) 
                {
                    final Token tok = consume();
                    tokens.add(tok);
                    if ( tok.isEOLorEOF() )
                    {
                        return;
                    }
                }                
            }

            // We found a Preprocessor directive.
            final String directive = lexer().peek().value;
            consume(); // consume keyword
            if ( directive.equalsIgnoreCase( "include" ) ) // #include "file"
            {
                expandTokens();

                int skipped = 1 + directive.length();
                while ( true ) {
                    final Token tok = lexer().peek();
                    if ( ! tok.isWhitespace() ) {
                        break;
                    }
                    skipped += consume().value.length();
                }

                if ( ! compilationEnabled ) { 
                    skipToNextLine();
                    continue;
                }
                if ( ! lexer().peek().is( TokenType.DOUBLE_QUOTE ) ) {
                    throw new ParseException("Expected a string literal",lexer().peek() );
                }
                skipped += consume().value.length(); // swallow "

                final List<Token> tokens = new ArrayList<>();
                while ( true ) 
                {
                    final Token tmp = lexer().peek();
                    if ( tmp.isEOLorEOF() ) {
                        break;
                    }
                    skipped += tmp.value.length();
                    tokens.add( consume() );
                    if ( tmp.is(TokenType.DOUBLE_QUOTE ) ) {
                        break;
                    }
                } 
                if ( tokens.isEmpty() || tokens.get( tokens.size()-1 ).isNot( TokenType.DOUBLE_QUOTE ) ) {
                    throw new ParseException("Unclosed string literal", tokens.isEmpty() ? next : tokens.get(tokens.size()-1) );
                }
                tokens.remove( tokens.size()-1 ); // discard double quote
                this.expansionOffset += skipped;

                final String path = tokens.stream().map( tok -> tok.value ).collect( Collectors.joining() );
                try 
                {
                    final Resource res = compilationContext.getResourceFactory().resolveResource(compilationContext.currentCompilationUnit().getResource(), path );
                    final CompilationUnit newUnit = compilationContext.newCompilationUnit(res);
                    if ( ! compilationContext.pushCompilationUnit( newUnit ) ) {
                        throw new ParseException( "Aborting compilation due to circular dependency", tokens.get(0) );
                    }
                    boolean success = false;
                    try 
                    {
                        final Lexer delegate = new LexerImpl( new Scanner( res ) );
                        pushLexer( delegate );
                        success = true;
                    }
                    finally 
                    {
                        if ( ! success ) 
                        {
                            compilationContext.popCompilationUnit();
                        }
                    }
                } catch (IOException e) {
                    throw new ParseException("I/O error while trying to open include \""+path+"\" ", tokens.get(0) );
                }
                continue;
            } 

            // #message / #warning / #error
            if ( directive.equalsIgnoreCase("message" ) || directive.equalsIgnoreCase("warning" ) || directive.equalsIgnoreCase("error" ) ) 
            {
                if ( compilationEnabled ) 
                {
                    expandTokens();
                    skipWhitespace();	

                    final List<Token> msg = new ArrayList<>();
                    processTokensUntilEndOfLine(msg::add);
                    expand( msg , new HashSet<>() );
                    final StringBuilder buffer = new StringBuilder();
                    msg.stream().map( t -> t.value ).forEach( buffer::append );
                    final Severity severity;
                    if ( directive.equalsIgnoreCase("message" ) ) {
                        severity = Severity.INFO;
                    } else if ( directive.equalsIgnoreCase("warning" ) ) {
                        severity = Severity.WARNING;
                    } else if ( directive.equalsIgnoreCase("error" ) ) 
                    {
                        throw new ParseException( buffer.toString() , next );
                    } else {
                        throw new RuntimeException("Internal error,unhandled severity level: #"+directive);
                    }
                    final CompilationMessage compMsg = new CompilationMessage( compilationContext.currentCompilationUnit() , severity ,  buffer.toString(), next.region() ); 
                    compilationContext.message( compMsg );
                } else {
                    skipToNextLine();
                }
                continue;
            } 

            if ( directive.equalsIgnoreCase("endif" ) ) // #endif
            {
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

            if ( directive.equalsIgnoreCase("if") || directive.equalsIgnoreCase("ifdef" ) || directive.equalsIgnoreCase("ifndef" ) ) // #if
            {
                currentIfNestingDepth++;

                if ( compilationDisabled ) {
                    continue;
                }

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
                        boolValue = symbols().isDefined( varName );
                    } else {
                        boolValue = ! symbols().isDefined( varName );
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
                    final Token tmpToken = lexer().peek(); // get token to take line/column from
                    macroBody.add( new Token(TokenType.DIGITS,"1" , macroName.offset , tmpToken.line , tmpToken.column ) );
                }
                if ( symbols().isDeclared(macroId) ) 
                {
                    error("Duplicate identifier: '"+macroName.value+"'", macroName );
                } 
                else 
                {
                    final Symbol symbol = new Symbol(macroId,Symbol.Type.PREPROCESSOR_MACRO,compilationContext.currentCompilationUnit(),null);
                    symbol.setValue( new MacroDefinition( argumentNames , macroBody ) );
                    symbol.setTextRegion( macroName.region() );
                    symbols().defineSymbol( symbol , compilationContext.currentSegment() );
                }
                skipToNextLine();
                continue;
            } 
            throw new ParseException("Unknown preprocessor directive: "+directive,next);
        }
    }

    private SymbolTable symbols() {
        return compilationContext.currentSymbolTable();
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
        compilationContext.message( CompilationMessage.error( compilationContext.currentCompilationUnit() , message , tok.region() ) );
    }

    private void expandTokens() 
    {
        expand( unexpandedTokens, new HashSet<>() );
        tokens.addAll( unexpandedTokens );
        unexpandedTokens.clear();
    }

    private void expand(List<Token> tokens,Set<String> alreadyExpandedMacros) 
    {
        if ( compilationContext.currentCompilationUnit() == null ) {
            return;
        }
        final Set<String> expanded = new HashSet<>();
        boolean anyIdentifiersExpanded = false;

        int totalOffset = 0;
        for (int tokenIdx = 0 ; tokenIdx < tokens.size() ; tokenIdx++) 
        {
            final Token macroName = tokens.get(tokenIdx);
            if ( macroName.isValidIdentifier() ) // check whether the identifier refers to a macro definition
            {
                final Optional<Symbol> optSymbol = symbols().maybeGet( new Identifier( macroName.value ) );
                if ( ! optSymbol.isPresent() || alreadyExpandedMacros.contains( macroName.value ) || ! optSymbol.get().hasType( Type.PREPROCESSOR_MACRO ) ) // prevent infinite expansion
                {
                    continue;
                }
                
                expanded.add( macroName.value );
                anyIdentifiersExpanded = true;

                final Symbol symbol = optSymbol.get();
                symbol.markAsReferenced();
                final MacroDefinition macroDef = (MacroDefinition) symbol.getValue();		

                // check whether first non-whitespace token is opening parenthesis (=start of argument list) 
                boolean hasParameters=false;
                int openingParensIdx = tokenIdx+1;
                if ( macroDef.takesParameters() ) 
                {
                    for ( final int len = tokens.size() ; openingParensIdx < len ; openingParensIdx++) 
                    {
                        if ( tokens.get(openingParensIdx).isNoWhitespace() ) {
                            hasParameters = tokens.get(openingParensIdx).is( TokenType.PARENS_OPEN );
                            break;
                        }
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

                if( macroParameters.size() != macroDef.parameterCount() ) 
                {
                    throw new ParseException("Expected "+macroDef.parameterCount()+" macro arguments but got "+macroParameters.size(),macroName);
                }				

                final List<Token> macroBody = macroDef.tokens;

                final int expandedLength;
                int replacedTokensLen = 0;
                if ( macroBody.isEmpty() ) // expand #define IDENTIFIER
                {
                    expandedLength= 1; // just replace the identifier with '1'
                    replacedTokensLen = macroName.value.length();
                    tokens.set( tokenIdx , new Token( TokenType.DIGITS , "1" , macroName.offset , macroName.line , macroName.column ) );
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
                        for (int i = paramValues.size()-1 ; i >= 0 ; i--) { // iterate in reverse order , tokens.add(ptr,expr) will reverse the order again
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
                    // tokens.get(j).offset += delta;
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
                if ( DEBUG ) {
                    System.out.println("NEXT: "+tokens.get(0));
                }
                return tokens.remove(0);
            }
            // advance to next non-whitespace token
            for ( int i = 0 , len = tokens.size() ; i < len ; i++ ) 
            {
                final Token tok = tokens.get(i );
                if ( ! tok.isWhitespace() ) 
                {
                    for (  int j = i; j >= 0 ; j-- ) { // discard all tokens we skipped
                        tokens.remove(0);
                    }
                    if ( DEBUG ) {
                        System.out.println("NEXT: "+tok);
                    }                    
                    return tok;
                }
            }
            tokens.clear();
        }
    }    

    @Override
    public Token peek() 
    {
        return internalPeek(false);
    }
    
    private Token internalPeek(boolean consumeToken) 
    {
        while ( true ) 
        {
            if ( tokens.isEmpty() ) 
            {
                parse();
            }  
            for ( int i = 0 , len = tokens.size() ; i < len ; i++ ) 
            {
                final Token tok = tokens.get(i);
                if ( ! tok.isWhitespace() || ! isIgnoreWhitespace ) 
                {
                    if ( consumeToken ) 
                    {
                        if ( DEBUG ) {
                            System.out.println("NEXT: "+tok);
                        }
                        final int toRemove = 1+i;
                        for ( int x = 0 ; x < toRemove ; x++ ) 
                        {
                            tokens.remove(0);
                        }
                    } else if ( DEBUG ) {
                        System.out.println("PEEK: "+tok);
                    }
                    return tok;
                }
                if ( isIgnoreWhitespace ) 
                {
                    tokens.remove( 0 );
                    i--;
                    len--;
                }
            }
            // found only whitespace tokens, try again
            tokens.clear();
        }        
    }

    @Override
    public boolean peek(TokenType t) {
        return peek().is( t );
    }

    @Override
    public void setIgnoreWhitespace(boolean ignoreWhitespace) 
    {
        boolean oldState = this.isIgnoreWhitespace;
        this.isIgnoreWhitespace = ignoreWhitespace;
        if ( ! oldState && ignoreWhitespace ) // transition: do not ignore whitespace -> ignore whitespace
        {
            tokens.removeIf( Token::isWhitespace );
        }
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
        return (IValueNode) Parser.parseExpression( new WrappingLexer(tokens ) , this.compilationContext );
    }

    private Lexer lexer() {
        return currentLexer;
    }

    protected static final class WrappingLexer implements Lexer {

        private final List<Token> tokens;
        private final int lastOffset;
        private final int lastLine;
        private final int lastColumn;

        private boolean ignoreWhitespace = true;

        public WrappingLexer(List<Token> tokens) {
            if ( tokens.isEmpty() ) {
                throw new IllegalArgumentException("At least one token needs to be present"); // ...otherwise we cannot get the offset at EOF
            }
            this.tokens = tokens;
            final Token lastToken = tokens.get(tokens.size()-1);
            this.lastOffset = lastToken.offset+lastToken.value.length();
            this.lastLine = lastToken.line;
            this.lastColumn = lastToken.column;
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
                return new Token(TokenType.EOF,"",lastOffset,lastLine,lastColumn);
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
                return new Token(TokenType.EOF,"",lastOffset,lastLine,lastColumn);
            }
            if ( tokens.isEmpty() ) {
                return new Token(TokenType.EOF,"",lastOffset,lastLine,lastColumn);
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

    @Override
    public String toString() {
        return tokens.isEmpty() ? "<no tokens>" : tokens.get(0).toString();
    }
}