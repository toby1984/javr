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
package de.codesourcery.javr.assembler.parser;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.Validate;

/**
 * Default lexer implementation, <b>ignores whitespace tokens by default</b>.
 *
 * @author tobias.gierke@code-sourcery.de
 */
public class LexerImpl implements Lexer 
{
    public static final boolean DEBUG = true;
    
    private final Scanner scanner;
    
    private final List<Token> tokens = new ArrayList<>();
    
    private final StringBuilder buffer = new StringBuilder();
    
    private boolean ignoreWhitespace = true;
    
    public LexerImpl(Scanner s) {
        Validate.notNull(s,"Scanner must not be null");
        this.scanner = s;
    }
    
    public boolean eof() 
    {
        parseTokens();
        return tokens.get(0).is( TokenType.EOF );
    }
    
    public Token next() 
    {
        parseTokens();
        return tokens.remove(0);
    }
    
    public Token peek() 
    {
        parseTokens();
        return tokens.get(0);
    }
    
    public boolean peek(TokenType t) {
        return peek().is( t ); 
    }
    
    private static boolean isWhitespace(char c) 
    {
        return c == ' ' || c == '\t';
    }

    private void parseTokens() 
    {
        if ( ! tokens.isEmpty() ) {
            return;
        }
        
        int offset = scanner.offset();
        buffer.setLength( 0 );
        
        while ( ! scanner.eof() && isWhitespace( scanner.peek() ) ) 
        {
            final char c = scanner.next();
            if ( ! ignoreWhitespace ) {
                buffer.append( c );
            }
        }
        
        if ( buffer.length() > 0 ) {
            addToken(TokenType.WHITESPACE, buffer.toString(), offset );
            return;
        }
        
        offset = scanner.offset();
        
outer:      
        while ( ! scanner.eof() )
        {
            final char c = scanner.next();
            if ( isWhitespace( c ) ) {
                scanner.pushBack();
                break;
            }
           
            if ( c == '-' ) 
            {
                parseBuffer(offset);
                addToken( TokenType.OPERATOR , c , scanner.offset()-1 ); 
                break outer;
            }
            
            if ( OperatorType.mayBeOperator( buffer.toString() + c ) ) 
            {
                buffer.append( c );
                continue;
            }
            else if ( OperatorType.getExactMatch( buffer.toString() ) != null ) 
            {
                addToken( TokenType.OPERATOR , buffer.toString() , offset );
                buffer.setLength(0);
                scanner.pushBack();
                break;
            } else if ( OperatorType.mayBeOperator( c ) ) {
                scanner.pushBack();
                break;
            }
            
            switch( c ) 
            {
                case '(':  parseBuffer(offset) ; addToken( TokenType.PARENS_OPEN, c , scanner.offset()-1 ); break outer;
                case ')':  parseBuffer(offset) ; addToken( TokenType.PARENS_CLOSE, c , scanner.offset()-1 ); break outer;
                case '=':  parseBuffer(offset) ; addToken( TokenType.EQUALS, c , scanner.offset()-1 ); break outer;
                case ';':  parseBuffer(offset) ; addToken( TokenType.SEMICOLON, c , scanner.offset()-1 ); break outer;
                case '\'': parseBuffer(offset) ; addToken( TokenType.SINGLE_QUOTE , c , scanner.offset()-1 );    break outer;
                case '"':  parseBuffer(offset) ; addToken( TokenType.DOUBLE_QUOTE , c , scanner.offset()-1 );    break outer;
                case '.':  parseBuffer(offset) ; addToken( TokenType.DOT   , c , scanner.offset()-1 );    break outer;
                case '#':  parseBuffer(offset) ; addToken( TokenType.HASH , c , scanner.offset()-1 );    break outer;
                case ',':  parseBuffer(offset) ; addToken( TokenType.COMMA , c , scanner.offset()-1 );    break outer;
                case '\n': parseBuffer(offset) ; addToken( TokenType.EOL   , c , scanner.offset()-1 );    break outer;
                case ':':  parseBuffer(offset) ; addToken( TokenType.COLON , ':' , scanner.offset()-1 );  break outer;
                case '\r':
                    parseBuffer(offset);
                    if ( ! scanner.eof() && scanner.peek() == '\n' ) 
                    {
                        scanner.next();
                        addToken( TokenType.EOL ,"\r\n" , scanner.offset()-2 );
                    } else { 
                        addToken( TokenType.EOL ,"\r" , scanner.offset()-1 );
                    }
                    break outer;
                default:
                    buffer.append( c );
            }
        }
        
        parseBuffer( offset );
        
        if ( scanner.eof() ) {
            addToken( TokenType.EOF , "" , scanner.offset() );
        }        
    }
    
    private void parseBuffer(int startOffset) 
    {
        if ( buffer.length() == 0 ) {
            return;
        }
        final String value = buffer.toString();
        buffer.setLength( 0 );
        
        if ( OperatorType.getExactMatch( value ) != null ) 
        {
            addToken(TokenType.OPERATOR , value , startOffset );
            return;
        }
        
        if ( "=".equals( value ) ) {
            addToken( TokenType.EQUALS, "=" , scanner.offset()-1 );
            return;
        }
        
        boolean isOnlyDigits = true;
        for ( int i = 0 , len=value.length() ; i < len ; i++ ) {
            if ( ! Character.isDigit( value.charAt( i ) ) ) 
            {
                isOnlyDigits=false;
                break;
            }
        }
        
        if ( isOnlyDigits ) 
        {
            addToken(TokenType.DIGITS , value , startOffset );
            return;
        }
        addToken(TokenType.TEXT , value , startOffset );
    }
    
    private void addToken(TokenType t, char value,int offset) 
    {
        final Token token = new Token( t , Character.toString( value ) , offset );
        if ( DEBUG ) {
            System.out.println("PARSED: "+token);
        }        
        tokens.add( token );
    }    
    
    private void addToken(TokenType t, String value,int offset) {
        final Token token = new Token( t , value , offset );
        if ( DEBUG ) {
            System.out.println("PARSED: "+token);
        }
        tokens.add( token );
    }
    
    /* (non-Javadoc)
	 * @see de.codesourcery.javr.assembler.parser.LexerIf#setIgnoreWhitespace(boolean)
	 */
    public void setIgnoreWhitespace(boolean ignoreWhitespace) 
    {
        if ( DEBUG ) {
            System.out.println("setIgnoreWhitespace(): ignore="+this.ignoreWhitespace+" => ignore="+ignoreWhitespace);
        }
        if ( this.ignoreWhitespace == ignoreWhitespace ) {
        	return;
        }
        if ( this.ignoreWhitespace && ! ignoreWhitespace ) 
        {
        	// change: ignore whitespace -> do not ignore
            if ( ! tokens.isEmpty() ) 
            {
                int offset = tokens.get(0).offset;
                if ( DEBUG ) {
                    System.out.println("current token: "+tokens.get(0));
                }            
                tokens.clear();
                scanner.setOffset( offset );
                parseTokens();
            }        	
        } 
        else 
        {
        	// change: do not ignore whitespace -> ignore whitespace
        	tokens.removeIf( tok -> tok.is(TokenType.WHITESPACE ) );
        }
        this.ignoreWhitespace = ignoreWhitespace;
    }
    
    public boolean isIgnoreWhitespace() {
        return ignoreWhitespace;
    }
    
    public void pushBack(Token tok) 
    {
        Validate.notNull(tok, "token must not be NULL");
        this.tokens.add(0,tok);
    }
    
    public String toString() 
    {
        parseTokens();
        return tokens.get(0).toString();
    }
}
