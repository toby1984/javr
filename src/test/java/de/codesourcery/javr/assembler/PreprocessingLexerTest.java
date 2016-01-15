package de.codesourcery.javr.assembler;

import java.io.File;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.junit.Test;

import de.codesourcery.javr.assembler.arch.impl.ATMega88;
import de.codesourcery.javr.assembler.exceptions.ParseException;
import de.codesourcery.javr.assembler.parser.Lexer;
import de.codesourcery.javr.assembler.parser.LexerImpl;
import de.codesourcery.javr.assembler.parser.PreprocessingLexer;
import de.codesourcery.javr.assembler.parser.Scanner;
import de.codesourcery.javr.assembler.parser.Token;
import de.codesourcery.javr.assembler.parser.TokenType;
import de.codesourcery.javr.assembler.util.FileResourceFactory;
import de.codesourcery.javr.assembler.util.StringResource;
import junit.framework.TestCase;

public class PreprocessingLexerTest extends TestCase 
{
	public void testEmptyString() 
	{
		final Iterator<Token> tokens = lex("");
		assertEquals(  TokenType.EOF  , tokens.next().type );
		assertFalse( tokens.hasNext() );
	}
	
    @Test
    public void testLexConditionalCompilationNotMatched() 
    {
		final Iterator<Token> tokens = lex("#if 1 > 2\nZ+\n#endif");
		assertToken( TokenType.EOF, "" , 19 , tokens ); 
		if ( tokens.hasNext() ) {
			fail( "Expected EOF but got "+tokens.next() );
		}
    }   
    
    @Test
    public void testLexConditionalCompilationNotMatchedIfDef() 
    {
		final Iterator<Token> tokens = lex("#ifdef test\nZ+\n#endif");
		assertToken( TokenType.EOF, "" , 21 , tokens ); 
		if ( tokens.hasNext() ) {
			fail( "Expected EOF but got "+tokens.next() );
		}
    }     
    
    @Test
    public void testLexConditionalCompilationMatchedIfDef() 
    {
		final Iterator<Token> tokens = lex("#define test\n#ifdef test\nZ+\n#endif");
		assertToken( TokenType.TEXT , "Z" , 25 , tokens ); 
		assertToken( TokenType.OPERATOR , "+" , 26 , tokens ); 
		assertToken( TokenType.EOL , "\n" , 27 , tokens ); 
		assertToken( TokenType.EOF, "" , 34 , tokens ); 
		if ( tokens.hasNext() ) {
			fail( "Expected EOF but got "+tokens.next() );
		}
    }     
    
    @Test
    public void testLexConditionalCompilationMatched() 
    {
		final Iterator<Token> tokens = lex("#if 2 > 1\nZ+\n#endif");
		assertToken( TokenType.TEXT , "Z" , 10 , tokens ); 
		assertToken( TokenType.OPERATOR , "+" , 11 , tokens ); 
		assertToken( TokenType.EOL , "\n" , 12 , tokens ); 
		assertToken( TokenType.EOF, "" , 19 , tokens ); 
		if ( tokens.hasNext() ) {
			fail( "Expected EOF but got "+tokens.next() );
		}
    }     
    
    @Test
    public void testLexMismatchEndif() 
    {
    	try {
    		lex("#if 2 > 1\nZ+\n#endif\n#endif");
    		fail("Should've failed");
    	} catch(ParseException e) {
    		assertTrue("Got: "+e.getMessage() , e.getMessage().contains( "#endif without matching #if" ) );
    	}
    } 
    
    @Test
    public void testLexMismatchIf() 
    {
    	try {
    		lex("#if 2 > 1\n#if 3 > 4\n#endif");
    		fail("Should've failed");
    	} catch(ParseException e) {
    		assertTrue("Got: "+e.getMessage() , e.getMessage().contains( "Expected 1 more #endif" ) );
    	}
    }     
	
	public void testOnlineNewlines() 
	{
		final Iterator<Token> tokens = lex("\n\n\n");
		assertEquals(  TokenType.EOL, tokens.next().type );
		assertEquals(  TokenType.EOL, tokens.next().type );
		assertEquals(  TokenType.EOL, tokens.next().type );
		assertEquals(  TokenType.EOF  , tokens.next().type );
		assertFalse( tokens.hasNext() );
	}	
	
	public void testWhitespaceIsIgnored() 
	{
		final Iterator<Token> tokens = lex("a   b");
		assertToken(TokenType.TEXT,"a",0,tokens);
		assertToken(TokenType.TEXT,"b",4,tokens);
		assertToken(TokenType.EOF,"",5,tokens);
		assertFalse( tokens.hasNext() );
	}	
	
	public void testParseDefineWithNoValue() 
	{
		final Iterator<Token> tokens = lex("#define a");
		assertToken(TokenType.EOF,"",9,tokens);
		assertFalse( tokens.hasNext() );
	}	
	
	public void testParseDefineWithOneValue() 
	{
		final Iterator<Token> tokens = lex("#define a 42");
		assertToken(TokenType.EOF,"",12,tokens);
		assertFalse( tokens.hasNext() );
	}	
	
	public void testExpandDefineWithOneValue() 
	{
		final Iterator<Token> tokens = lex("#define a 42\n"
				+ "a");
		assertToken(TokenType.DIGITS,"42",13,tokens);
		assertToken(TokenType.EOF,"",15,tokens);
		assertFalse( tokens.hasNext() );
	}	
	
    public void testMacroBodyIsExpandedOnlyOnce() 
    {
        final Iterator<Token> tokens = lex("#define a a\n"
                + "a");
        assertToken(TokenType.TEXT,"a",12,tokens);
        assertToken(TokenType.EOF,"",13,tokens);
        assertFalse( tokens.hasNext() );
    }	
    
    public void testMacroBodyIsExpandedRecursively() 
    {
        final Iterator<Token> tokens = lex("#define b c\n"
                + "#define a b\n"
                + "a");
        assertToken(TokenType.TEXT,"c",24,tokens);
        assertToken(TokenType.EOF,"",25,tokens);
        assertFalse( tokens.hasNext() );
    }    
	
	public void testExpandDefineWithLongValue() 
	{
		final Iterator<Token> tokens = lex("#define a xxxxx\n"
				+ "a");
		assertToken(TokenType.TEXT,"xxxxx",16,tokens);
		assertToken(TokenType.EOF,"",21,tokens);
		assertFalse( tokens.hasNext() );
	}	
	
	public void testExpandDefineWithShortValue() 
	{
		final Iterator<Token> tokens = lex("#define TEST X\n"
				+ "TEST");
		assertToken(TokenType.TEXT,"X",15,tokens);
		assertToken(TokenType.EOF,"",16,tokens);
		assertFalse( tokens.hasNext() );
	}	
	
	public void testExpandDefineWithExpression() 
	{
		final Iterator<Token> tokens = lex("#define TEST y+y\n"
				+ "TEST");
		assertToken(TokenType.TEXT,"y",17,tokens);
		assertToken(TokenType.OPERATOR,"+",18,tokens);
		assertToken(TokenType.TEXT,"y",19,tokens);
		assertToken(TokenType.EOF,"",20,tokens);
		assertFalse( tokens.hasNext() );
	}	
	
    public void testExpandMacroWithOneArg() 
    {
        final Iterator<Token> tokens = lex("#define func(x) x+x\n"
                + "func(2)");
        assertToken(TokenType.DIGITS,"2",20,tokens);
        assertToken(TokenType.OPERATOR,"+",21,tokens);
        assertToken(TokenType.DIGITS,"2",22,tokens);
        assertToken(TokenType.EOF,"",23,tokens);
        assertFalse( tokens.hasNext() );
    }	
    
    public void testExpandMacroWithTwoArgs() 
    {
        final Iterator<Token> tokens = lex("#define func(a,b) a+b\n"
                + "func(1,2)");
        assertToken(TokenType.DIGITS,"1",22,tokens);
        assertToken(TokenType.OPERATOR,"+",23,tokens);
        assertToken(TokenType.DIGITS,"2",24,tokens);
        assertToken(TokenType.EOF,"",25,tokens);
        assertFalse( tokens.hasNext() );
    }   
    
    public void testExpandMacroWithTwoArgsAndWhiteSpace() 
    {
        final Iterator<Token> tokens = lex("#define func(a,b) a + b\n"
                + "func(1,2)");
        assertToken(TokenType.DIGITS,"1",24,tokens);
        assertToken(TokenType.OPERATOR,"+",26,tokens);
        assertToken(TokenType.DIGITS,"2",28,tokens);
        assertToken(TokenType.EOF,"",29,tokens);
        assertFalse( tokens.hasNext() );
    }    
	
	private void assertToken(TokenType t,String value,int offset,Iterator<Token> it) 
	{
		final Token tok = it.next();
		assertEquals( t , tok.type );
		assertEquals( value , tok.value );
		assertEquals( offset , tok.offset);
	}
	
	private Iterator<Token> lex(String s) 
	{
		final StringResource resource = new StringResource("dummy",s);
        CompilationUnit unit = new CompilationUnit( resource );
        final ResourceFactory resFactory = FileResourceFactory.createInstance(new File("/"));
        final LexerImpl delegate = new LexerImpl( new Scanner(resource ) );
		final Lexer lexer = new PreprocessingLexer( delegate , unit , new ATMega88() , resFactory );
		final List<Token> result = new ArrayList<>();
		while(true) 
		{
			Token tok = lexer.next();
			result.add( tok );
			if ( tok.is(TokenType.EOF ) ) 
			{
				return new Iterator<Token>() {

					private final Iterator<Token> wrapped = result.iterator();
					
					@Override
					public boolean hasNext() {
						return wrapped.hasNext();
					}

					@Override
					public Token next() {
						final Token result = wrapped.next();
						System.out.println( result );
						return result;
					}
				};
			}
		}
	}
}
