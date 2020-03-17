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
    public static final boolean DEBUG = false;
    
    private final Scanner scanner;
    
    private final List<Token> tokens = new ArrayList<>();
    
    private final StringBuilder buffer = new StringBuilder();
    
    private boolean ignoreWhitespace = true;
    
    private int line = 1;
    private int column = 0;
    
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
        
        int startOffset = scanner.offset();
        int startColumn = column;
        buffer.setLength( 0 );
        
        while ( ! scanner.eof() && isWhitespace( scanner.peek() ) ) 
        {
            final char c = scanner.next();
            column++;
            if ( ! ignoreWhitespace ) {
                buffer.append( c );
            }
        }
        
        if ( buffer.length() > 0 ) {
            addToken(TokenType.WHITESPACE, buffer.toString(), startOffset , line , startColumn );
            return;
        }
        
        startOffset = scanner.offset();
        
outer:      
        while ( ! scanner.eof() )
        {
            char c = scanner.next();
            if ( isWhitespace( c ) ) {
                scanner.pushBack();
                break;
            }
            column++;
           
            if ( c == '-' ) 
            {
                parseBuffer(startOffset,startColumn);
                addToken( TokenType.OPERATOR , c , scanner.offset()-1 , line , column ); 
                break outer;
            }

            if ( OperatorType.mayBeOperator(c) )
            {
                final int opStartCol = column;
                final int opStartOffset = scanner.offset()-1;
                final StringBuilder tmp = new StringBuilder();
                while ( OperatorType.mayBeOperator(tmp.toString() + c ) ) {
                    tmp.append(c);
                    if ( scanner.eof() ) {
                        break;
                    }
                    c = scanner.next();
                    column++;
                }
                final OperatorType op = OperatorType.getExactMatch(tmp.toString());
                if ( ! scanner.eof() || (scanner.eof() && ! tmp.toString().endsWith(String.valueOf(c) ) ) ) {
                    // we aborted parsing because tmp.toString() +c is not a valid operator
                    scanner.pushBack();
                    column--;
                }
                if ( op != null ) {
                    parseBuffer(startOffset, startColumn);
                    addToken(TokenType.OPERATOR,tmp.toString(),opStartOffset,line,opStartCol);
                    break;
                }
                for (int i = 0, len = tmp.length() ; i < len; i++ ) {
                    scanner.pushBack();
                    column--;
                }
                c = scanner.next();
                column++;
            }
            
            switch( c )
            {
                case '(':  parseBuffer(startOffset, startColumn) ; addToken( TokenType.PARENS_OPEN, c , scanner.offset()-1 , line ,column ); break outer;
                case ')':  parseBuffer(startOffset, startColumn) ; addToken( TokenType.PARENS_CLOSE, c , scanner.offset()-1 , line ,column); break outer;
                case '=':  parseBuffer(startOffset, startColumn) ; addToken( TokenType.EQUALS, c , scanner.offset()-1 , line ,column); break outer;
                case ';':  parseBuffer(startOffset, startColumn) ; addToken( TokenType.SEMICOLON, c , scanner.offset()-1 , line ,column); break outer;
                case '\'': parseBuffer(startOffset, startColumn) ; addToken( TokenType.SINGLE_QUOTE , c , scanner.offset()-1 , line ,column);    break outer;
                case '"':  parseBuffer(startOffset, startColumn) ; addToken( TokenType.DOUBLE_QUOTE , c , scanner.offset()-1 , line ,column);    break outer;
                case '.':  parseBuffer(startOffset, startColumn) ; addToken( TokenType.DOT   , c , scanner.offset()-1 , line ,column);    break outer;
                case '#':  parseBuffer(startOffset, startColumn) ; addToken( TokenType.HASH , c , scanner.offset()-1 , line ,column);    break outer;
                case ',':  parseBuffer(startOffset, startColumn) ; addToken( TokenType.COMMA , c , scanner.offset()-1 , line ,column);    break outer;
                // hint: carriage return can never be seen by the lexer as Scanner#isSkipCarriageReturn() is TRUE by default
                case '\n': parseBuffer(startOffset, startColumn) ; addToken( TokenType.EOL   , c , scanner.offset()-1 , line ,column);    break outer;
                case ':':  parseBuffer(startOffset, startColumn) ; addToken( TokenType.COLON , ':' , scanner.offset()-1 , line ,column);  break outer;
                default:
                    buffer.append( c );
            }
        }
        
        parseBuffer( startOffset , startColumn );
        
        if ( scanner.eof() ) {
            addToken( TokenType.EOF , "" , scanner.offset() , line ,column );
        }        
    }
    
    private void parseBuffer(int startOffset,int startColumn) 
    {
        if ( buffer.length() == 0 ) {
            return;
        }
        final String value = buffer.toString();
        buffer.setLength( 0 );
        
        if ( OperatorType.getExactMatch( value ) != null ) 
        {
            addToken(TokenType.OPERATOR , value , startOffset , line , startColumn );
            return;
        }
        
        if ( "=".equals( value ) ) {
            addToken( TokenType.EQUALS, "=" , scanner.offset()-1 , line , startColumn );
            return;
        }
        
        boolean isOnlyDigits = Character.isDigit( value.charAt(0) );
        for ( int i = 1 , len=value.length() ; i < len ; i++ )
        {
            final char c = value.charAt( i );
            if ( ! Character.isDigit( c ) && c != '_' )
            {
                isOnlyDigits = false;
                break;
            }
        }
        
        if ( isOnlyDigits ) 
        {
            addToken(TokenType.DIGITS , value , startOffset , line , startColumn );
            return;
        }
        addToken(TokenType.TEXT , value , startOffset , line , startColumn);
    }
    
    private void addToken(TokenType t, char value,int offset,int line,int column) 
    {
        final Token token = new Token( t , Character.toString( value ) , offset ,line,column);
        if ( DEBUG ) {
            System.out.println("PARSED: "+token);
        }
        addToken(token);
    }
    
    private void addToken(TokenType t, String value,int offset,int line,int column) {
        final Token token = new Token( t , value , offset , line , column );
        if ( DEBUG ) {
            System.out.println("PARSED: "+token);
        }
        addToken(token);
    }

    private void addToken(Token t) {
        System.out.println("---> adding "+t);
        this.tokens.add(t);
    }
    
    /* (non-Javadoc)
	 * @see de.codesourcery.javr.assembler.parser.LexerIf#setIgnoreWhitespace(boolean)
	 */
    public void setIgnoreWhitespace(boolean newSetting) 
    {
        final boolean oldSetting = this.ignoreWhitespace;
        
        if ( DEBUG ) {
            System.out.println("setIgnoreWhitespace(): ignore="+oldSetting+" => ignore="+newSetting);
        }
        if ( oldSetting == newSetting ) {
        	return;
        }
        if ( oldSetting && ! newSetting )  // change: ignore whitespace -> do not ignore whitespace
        {
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
        this.ignoreWhitespace = newSetting;
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
        if ( tokens.isEmpty() ) {
            return "< no tokens >, scanner @ "+(scanner.eof()?"EOF" : "'"+scanner.peek()+"'");
        }
        return tokens.get(0).toString();
    }
}
