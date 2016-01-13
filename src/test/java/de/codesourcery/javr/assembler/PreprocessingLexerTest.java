package de.codesourcery.javr.assembler;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import de.codesourcery.javr.assembler.parser.Lexer;
import de.codesourcery.javr.assembler.parser.LexerImpl;
import de.codesourcery.javr.assembler.parser.PreprocessingLexer;
import de.codesourcery.javr.assembler.parser.Scanner;
import de.codesourcery.javr.assembler.parser.Token;
import de.codesourcery.javr.assembler.parser.TokenType;
import de.codesourcery.javr.assembler.util.StringResource;
import junit.framework.TestCase;

public class PreprocessingLexerTest extends TestCase 
{
	public void testEmptyString() 
	{
		final Iterator<Token> tokens = lex("").iterator();
		assertEquals(  TokenType.EOF  , tokens.next().type );
		assertFalse( tokens.hasNext() );
	}
	
	public void testOnlineNewlines() 
	{
		final Iterator<Token> tokens = lex("\n\n\n").iterator();
		assertEquals(  TokenType.EOL, tokens.next().type );
		assertEquals(  TokenType.EOL, tokens.next().type );
		assertEquals(  TokenType.EOL, tokens.next().type );
		assertEquals(  TokenType.EOF  , tokens.next().type );
		assertFalse( tokens.hasNext() );
	}	
	
	public void testWhitespaceIsIgnored() 
	{
		final Iterator<Token> tokens = lex("a   b").iterator();
		assertToken(TokenType.TEXT,"a",0,tokens);
		assertToken(TokenType.TEXT,"b",4,tokens);
		assertToken(TokenType.EOF,"",5,tokens);
		assertFalse( tokens.hasNext() );
	}	
	
	public void testParseDefineWithNoValue() 
	{
		final Iterator<Token> tokens = lex("#define a").iterator();
		assertToken(TokenType.HASH,"#",0,tokens);
		assertToken(TokenType.TEXT,"define",1,tokens);
		assertToken(TokenType.TEXT,"a",8,tokens);
		assertToken(TokenType.EOF,"",9,tokens);
		assertFalse( tokens.hasNext() );
	}	
	
	public void testParseDefineWithOneValue() 
	{
		final Iterator<Token> tokens = lex("#define a 42").iterator();
		assertToken(TokenType.HASH,"#",0,tokens);
		assertToken(TokenType.TEXT,"define",1,tokens);
		assertToken(TokenType.TEXT,"a",8,tokens);
		assertToken(TokenType.DIGITS,"42",10,tokens);
		assertToken(TokenType.EOF,"",12,tokens);
		assertFalse( tokens.hasNext() );
	}	
	
	public void testExpandDefineWithOneValue() 
	{
		final Iterator<Token> tokens = lex("#define a 42\n"
				+ "a").iterator();
		assertToken(TokenType.HASH,"#",0,tokens);
		assertToken(TokenType.TEXT,"define",1,tokens);
		assertToken(TokenType.TEXT,"a",8,tokens);
		assertToken(TokenType.DIGITS,"42",10,tokens);
		assertToken(TokenType.EOL,"\n",12,tokens);
		assertToken(TokenType.DIGITS,"42",13,tokens);
		assertToken(TokenType.EOF,"",15,tokens);
		assertFalse( tokens.hasNext() );
	}	
	
	public void testExpandDefineWithLongValue() 
	{
		final Iterator<Token> tokens = lex("#define a xxxxx\n"
				+ "a").iterator();
		assertToken(TokenType.HASH,"#",0,tokens);
		assertToken(TokenType.TEXT,"define",1,tokens);
		assertToken(TokenType.TEXT,"a",8,tokens);
		assertToken(TokenType.TEXT,"xxxxx",10,tokens);
		assertToken(TokenType.EOL,"\n",15,tokens);
		assertToken(TokenType.TEXT,"xxxxx",16,tokens);
		assertToken(TokenType.EOF,"",21,tokens);
		assertFalse( tokens.hasNext() );
	}	
	
	public void testExpandDefineWithShortValue() 
	{
		final Iterator<Token> tokens = lex("#define TEST X\n"
				+ "TEST").iterator();
		assertToken(TokenType.HASH,"#",0,tokens);
		assertToken(TokenType.TEXT,"define",1,tokens);
		assertToken(TokenType.TEXT,"TEST",8,tokens);
		assertToken(TokenType.TEXT,"X",13,tokens);
		assertToken(TokenType.EOL,"\n",14,tokens);
		assertToken(TokenType.TEXT,"X",15,tokens);
		assertToken(TokenType.EOF,"",16,tokens);
		assertFalse( tokens.hasNext() );
	}	
	
	public void testExpandDefineWithExpression() 
	{
		final Iterator<Token> tokens = lex("#define TEST y+y\n"
				+ "TEST").iterator();
		assertToken(TokenType.HASH,"#",0,tokens);
		assertToken(TokenType.TEXT,"define",1,tokens);
		assertToken(TokenType.TEXT,"TEST",8,tokens);
		assertToken(TokenType.TEXT,"y",13,tokens);
		assertToken(TokenType.OPERATOR,"+",14,tokens);
		assertToken(TokenType.TEXT,"y",15,tokens);
		assertToken(TokenType.EOL,"\n",16,tokens);
		assertToken(TokenType.TEXT,"y",17,tokens);
		assertToken(TokenType.OPERATOR,"+",18,tokens);
		assertToken(TokenType.TEXT,"y",19,tokens);
		assertToken(TokenType.EOF,"",20,tokens);
		assertFalse( tokens.hasNext() );
	}	
	
	private void assertToken(TokenType t,String value,int offset,Iterator<Token> it) 
	{
		final Token tok = it.next();
		assertEquals( t , tok.type );
		assertEquals( value , tok.value );
		assertEquals( offset , tok.offset);
	}
	
	private List<Token> lex(String s) 
	{
		CompilationUnit unit = new CompilationUnit( new StringResource("dummy",s) );
		final Lexer lexer = new PreprocessingLexer( new LexerImpl( new Scanner(s) ) , unit );
		final List<Token> result = new ArrayList<>();
		while(true) 
		{
			Token tok = lexer.next();
			result.add( tok );
			if ( tok.hasType(TokenType.EOF ) ) {
				return result;
			}
		}
	}
}
