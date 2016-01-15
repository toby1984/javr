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

/**
 * Enumeration of all {@link Token} types the lexer can produce.
 *
 * @author tobias.gierke@code-sourcery.de
 */
public enum TokenType 
{
    // multiple characters
    /**
     * Tokens with this type hold an adjacent region of whitespaces
     * found in the input stream.
     */
    WHITESPACE,
    /**
     * Tokens with this type contain any data that was found
     * between other recognized tokens and does not match
     * any of the over token types.
     */
    TEXT,
    /**
     * A token that consists of one or more digits.
     */
    DIGITS,
    /**
     * An operator token.
     */
    OPERATOR,
    // single character     
    /**
     * Special token that is always returned by lexers once they
     * reached the end of the underlying character stream.
     */    
    EOF,
    /**
     * An end-of-line sequence was recognized in the input stream.
     * Supports UNIX and Windows end-of-line sequences. 
     */
    EOL, 
    PARENS_OPEN,
    PARENS_CLOSE,
    HASH,
    EQUALS, // note: currently not treated as an operator
    COLON,
    SEMICOLON,
    DOT,
    COMMA,
    SINGLE_QUOTE,
    DOUBLE_QUOTE;
}