/**
 * Copyright 2015 Tobias Gierke <tobias.gierke@code-sourcery.de>
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
package de.codesourcery.javr.assembler;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.junit.Before;
import org.junit.Test;

public class LexerTest {
    
    private Lexer lexer;
    
    @Before
    public void setup() {
        this.lexer = null;
    }
    
    @Test
    public void testEmptyString() 
    {
        lexAll("");
        assertEOF();
    }
    
    @Test
    public void testLexOperators2() 
    {
        final List<Token> tokens = lexAll("Z+");
        assertEquals(2,tokens.size());
        assertEquals( token(TokenType.TEXT,"Z",0) , tokens.get(0) );
        assertEquals( token(TokenType.OPERATOR,"+",1) , tokens.get(1) );
    }     
    
    @Test
    public void testLexUnixEOL() 
    {
        final List<Token> tokens = lexAll("\n");
        assertEquals(1,tokens.size());
        assertEquals( token(TokenType.EOL,"\n",0) , tokens.get(0) );
    }    
    
    @Test
    public void testLexWindowsEOL() 
    {
        final List<Token> tokens = lexAll("\r\n");
        assertEquals(1,tokens.size());
        assertEquals( token(TokenType.EOL,"\r\n",0) , tokens.get(0) );
    }     
    
    @Test
    public void testLexSingleCharacters() 
    {
        final List<Token> tokens = lexAll(":.,\"';");
        assertEquals(6,tokens.size());
        assertEquals( token(TokenType.COLON,":" , 0) , tokens.get(0) );
        assertEquals( token(TokenType.DOT  ,"." , 1) , tokens.get(1) );
        assertEquals( token(TokenType.COMMA,"," , 2) , tokens.get(2) );
        assertEquals( token(TokenType.DOUBLE_QUOTE,"\"", 3) , tokens.get(3) );
        assertEquals( token(TokenType.SINGLE_QUOTE,"'" , 4) , tokens.get(4) );
        assertEquals( token(TokenType.SEMICOLON,";" , 5) , tokens.get(5) );
        assertEOF();
    }     
    
    @Test
    public void testLexDontIgnoreWhitespace() 
    {
        final List<Token> tokens = lexAll("a    b",false);
        assertEquals(3,tokens.size());
        assertEquals( token(TokenType.TEXT,"a" , 0) , tokens.get(0) );        
        assertEquals( token(TokenType.WHITESPACE, "    " , 1) , tokens.get(1) );        
        assertEquals( token(TokenType.TEXT,"b" , 5) , tokens.get(2) );
        assertEOF();
    }
    
    @Test
    public void testLexIgnoreWhitespace() 
    {
        final List<Token> tokens = lexAll("a    b");
        assertEquals(2,tokens.size());
        assertEquals( token(TokenType.TEXT,"a" , 0) , tokens.get(0) );        
        assertEquals( token(TokenType.TEXT,"b" , 5) , tokens.get(1) );
        assertEOF();
    }    
    
    @Test
    public void testLexOperators() 
    {
        final List<Token> tokens = lexAll("+-*/");
        assertEquals(4,tokens.size());
        assertEquals( token(TokenType.OPERATOR,"+" , 0) , tokens.get(0) );        
        assertEquals( token(TokenType.OPERATOR,"-" , 1) , tokens.get(1) );        
        assertEquals( token(TokenType.OPERATOR,"*" , 2) , tokens.get(2) );        
        assertEquals( token(TokenType.OPERATOR,"/" , 3) , tokens.get(3) );        
        assertEOF();
    }  
    
    @Test
    public void testLexText() 
    {
        final List<Token> tokens = lexAll("abc de1f 12ab ab12");
        assertEquals(4,tokens.size());
        assertEquals( token(TokenType.TEXT,"abc" , 0) , tokens.get(0) );  
        assertEquals( token(TokenType.TEXT,"de1f" , 4) , tokens.get(1) );  
        assertEquals( token(TokenType.TEXT,"12ab" , 9) , tokens.get(2) );  
        assertEquals( token(TokenType.TEXT,"ab12" , 14) , tokens.get(3) );
        assertEOF();
    }
    
    // LDD r0,Y+42
    @Test
    public void testLexLDD() 
    {
        final List<Token> tokens = lexAll("LDD r0,Y+42");
        assertEquals(6,tokens.size());
        final Iterator<Token> it = tokens.iterator();
        assertEquals( token(TokenType.TEXT,"LDD" , 0) , it.next() );
        assertEquals( token(TokenType.TEXT,"r0" , 4) , it.next() );
        assertEquals( token(TokenType.COMMA,"," , 6) , it.next() );
        assertEquals( token(TokenType.TEXT,"Y" , 7) , it.next() );
        assertEquals( token(TokenType.OPERATOR,"+" , 8) , it.next() );
        assertEquals( token(TokenType.DIGITS,"42" , 9) , it.next() );
        assertEOF();
    }    
    
    @Test
    public void testLexDigits() 
    {
        final List<Token> tokens = lexAll("12 34 56");
        assertEquals(3,tokens.size());
        assertEquals( token(TokenType.DIGITS,"12" , 0) , tokens.get(0) );        
        assertEquals( token(TokenType.DIGITS,"34" , 3) , tokens.get(1) );        
        assertEquals( token(TokenType.DIGITS,"56" , 6) , tokens.get(2) );        
        assertEOF();
    }     
    
    private Token token(TokenType t,String value,int offset) {
        return new Token(t,value,offset);
    }

    private void assertEOF() 
    {
        assertTrue( lexer.eof() );
        lexer.peek().hasType(TokenType.EOF);
        lexer.next().hasType(TokenType.EOF);
    }
    
    private List<Token> lexAll(String s) 
    {
        return lexAll(s,true);
    }
    
    private List<Token> lexAll(String s,boolean ignoreWhitespace) 
    {
        this.lexer = new Lexer(new Scanner(s ) );
        lexer.setIgnoreWhitespace( ignoreWhitespace );
        final List<Token> result = new ArrayList<>();
        while ( ! lexer.eof() ) {
            result.add( lexer.next() );
        }
        assertEOF();
        return result;
    }
}
