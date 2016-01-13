package de.codesourcery.javr.assembler.parser;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import org.apache.commons.lang3.Validate;

import de.codesourcery.javr.assembler.CompilationUnit;
import de.codesourcery.javr.assembler.parser.Parser.CompilationMessage;

public class PreprocessingLexer implements Lexer 
{
	private final CompilationUnit unit;
	private final Lexer lexer;
	
	private boolean isIgnoreWhitespace=true;
	private final Map<String,List<Token>> macros = new HashMap<>();
	private final List<Token> tokens = new ArrayList<>();
	private final List<Token> unprocessedTokens = new ArrayList<>();
	
	private final Set<String> expanded = new HashSet<>();
	
	// offset to the actual scanner position that is adjusted 
	// each time some expression/identifier gets macro-expanded 
	private int expansionOffset = 0;
	
	public PreprocessingLexer(Lexer delegate,CompilationUnit unit) 
	{
		Validate.notNull(delegate, "delegate must not be NULL");
		this.unit = unit;
		this.lexer = delegate;
		delegate.setIgnoreWhitespace( false );
	}
	
	@Override
	public boolean eof() {
		if ( tokens.isEmpty() ) {
			parse();
		}
		return tokens.isEmpty();
	}
	
	private static boolean isEOL(Token tok) {
		return tok.hasType(TokenType.EOL) || tok.hasType( TokenType.EOF );
	}
	
	private static boolean isOperator(Token tok,String op) {
		return tok.hasType(TokenType.OPERATOR) && tok.value.equals(op);
	}
	
	private Token adjustOffset(Token token) 
	{
		token.offset += expansionOffset;
		return token;
	}
	
	private Token consume() 
	{
		return adjustOffset( lexer.next() );
	}
	
	private void parse() 
	{
		unprocessedTokens.clear();
		while ( true ) 
		{ 
			final Token next = consume();
			
			if ( isEOL( next ) ) 
			{
				unprocessedTokens.add( next );
				expandTokens();
				return;
			}

			// skip single-line comments			
			if ( isSingleLineComment(next , lexer.peek() ) ) 
			{
				expandTokens();				
				tokens.add( next );
				processTokensUntilEndOfLine( tokens::add );
				return;
			}
			
			// skip multi-line comments		
			if ( isMultiLineCommentStart( next , lexer.peek() ) ) 
			{
				expandTokens();
				
				tokens.add( next );
				tokens.add( consume() );
				while ( true ) 
				{
					Token tok = consume();
					tokens.add(tok);
					if ( tok.hasType( TokenType.EOF ) || ( isOperator(tok,"*") && isOperator( lexer.peek() , "/" ) ) )
					{
						return;
					}
				}
			}
			
			// skip string literals
			if ( next.hasType(TokenType.SINGLE_QUOTE ) || next.hasType( TokenType.DOUBLE_QUOTE ) ) 
			{
				expandTokens();
				
				tokens.add( next );
				TokenType endType = next.type;
				while ( true ) 
				{
					Token tok = consume();
					tokens.add(tok);
					if ( isEOL( tok ) || tok.hasType( endType ) )
					{
						return;
					}
				}				
			}
			
			// preprocessor directive
			if ( next.hasType(TokenType.HASH ) ) 
			{
				if ( lexer.peek().value.equalsIgnoreCase("define" ) ) // #define 
				{
					skipWhitespace( unprocessedTokens );
					expandTokens();
					tokens.add( next ); // '#'
					tokens.add( consume() ); // skip 'define'
					skipWhitespace( tokens );
					if ( isValidIdentifier( lexer.peek() ) ) 
					{
						final Token macroName = consume();
						tokens.add( macroName );
						
						skipWhitespace( tokens );
						
						// parse body
						final List<Token> macroBody = new ArrayList<>();
						processTokensUntilEndOfLine( t -> {
							macroBody.add(t);
							tokens.add( t );
						} );
						if ( macroBody.isEmpty() ) {
							macroBody.add( new Token(TokenType.DIGITS,"1" , macroName.offset ) );
						}
						if ( macros.containsKey( macroName.value ) ) 
						{
							error("Duplicate identifier: '"+macroName.value+"'", macroName );
						} else {
							macros.put( macroName.value , macroBody );
						}
						return;
					} else {
						error("Expected an identifier",lexer.peek());
					}
					return;
				} 
			}
			unprocessedTokens.add( next );
		}
	}

	private boolean isMultiLineCommentStart(final Token current,Token next) {
		return isOperator( current , "/" ) && isOperator( next , "*" );
	}

	private boolean isSingleLineComment(final Token currentToken,Token nextToken) {
		return currentToken.hasType( TokenType.SEMICOLON ) || ( isOperator( currentToken , "/" ) && isOperator( nextToken , "/" ) );
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
		for (int i = 0,len=tokens.size(); i < len ; i++) 
		{
			final Token tok = tokens.get(i);
			if ( isValidIdentifier( tok ) ) 
			{
				final List<Token> body = macros.get( tok.value );
				if ( body == null || alreadyExpandedMacros.contains( tok.value ) ) 
				{
					continue;
				}
				expanded.add( tok.value );
				anyIdentifiersExpanded = true;
				final int expandedLength;
				if ( body.isEmpty() ) 
				{
					expandedLength= 1;
					tokens.set( i , new Token( TokenType.DIGITS , "1" , tok.offset ) );
				} 
				else 
				{
					tokens.remove( i );
					int offset = tok.offset;
					for ( int j = 0 ; j < body.size() ; j++ ) 
					{
						final Token exp = body.get(j ).copyWithOffset( offset );
						offset += exp.value.length();
						
						tokens.add( i+j , exp );
					}
					expandedLength = offset-tok.offset;
				}
				// calculate delta between size of expanded identifier and expansion
				final int delta = expandedLength - tok.value.length();
				// adjust any tokens remaining in our queue
				for (int j = i+1 ; j < len ; j++ ) {
					tokens.get(j).offset += delta;
				}
				totalOffset += delta;
			}
		}
		expansionOffset += totalOffset;
		alreadyExpandedMacros.addAll( expanded );
		if ( anyIdentifiersExpanded ) 
		{
			expand( tokens , alreadyExpandedMacros );
		}
	}
	
	private void processTokensUntilEndOfLine(Consumer<Token> consumer) 
	{
		while ( true ) {
			Token tok = lexer.peek();
			if ( isEOL(tok ) )
			{
				return;
			}
			consumer.accept( consume() );
		}
	}
	
	private static boolean isValidIdentifier(Token tok) 
	{
		return tok.hasType( TokenType.TEXT ) && Identifier.isValidIdentifier( tok.value );
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
			final boolean isWhitespace = tokens.get(0).hasType(TokenType.WHITESPACE );
			if ( ! isWhitespace || ! isIgnoreWhitespace )  {
				return tokens.remove(0);
			}
			// advance to next non-whitespace token
			for ( int i = 0 , len = tokens.size() ; i < len ; i++ ) 
			{
				final Token tok = tokens.get(i );
				if ( ! tok.hasType( TokenType.WHITESPACE ) ) 
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
			final Token tok = lexer.peek();
			if ( ! tok.hasType( TokenType.WHITESPACE ) ) {
				return;
			}
			toAdd.add( consume() );
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
			final boolean isWhitespace = tokens.get(i).hasType(TokenType.WHITESPACE );
			if ( ! isWhitespace || ! isIgnoreWhitespace )  {
				return tokens.remove(i);
			}
			// advance to next non-whitespace token
			for ( int len = tokens.size() ; i < len ; i++ ) 
			{
				final Token tok = tokens.get(i );
				if ( ! tok.hasType( TokenType.WHITESPACE ) ) 
				{
					return tok;
				}
			}
			parse();
		}
	}

	@Override
	public boolean peek(TokenType t) {
		return peek().hasType( t );
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
}