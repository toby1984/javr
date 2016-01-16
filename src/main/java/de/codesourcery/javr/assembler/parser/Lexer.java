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
 * Turns a character stream into {@link Token}s.
 *
 * @author tobias.gierke@code-sourcery.de
 */
public interface Lexer 
{
    /**
     * Check whether the underlying character stream reached EOF
     * and only tokens of type {@link TokenType#EOF} will
     * be returned by this lexer.
     *   
     * @return
     */
	boolean eof();

	/**
	 * Returns the next token from the character stream.
	 * 
	 * If this lexer is already at {@link #eof()} ,
	 * only tokens of type {@link TokenType#EOF} will
     * be returned by this lexer.
     * 
	 * @return
	 */
	Token next();

    /**
     * Peeks at the next token from the character stream without advancing the read pointer.
     * 
     * If this lexer is already at {@link #eof()} ,
     * only tokens of type {@link TokenType#EOF} will
     * be returned by this lexer.
     * 
     * @return
     */	
	Token peek();

	/**
	 * Check whether the token returned by {@link #peek()} has a specific type.
	 * 
	 * @param t
	 * @return
	 */
	boolean peek(TokenType t);

	/**
	 * Enable/disable ignoring whitespace tokens.
	 * 
	 * @param ignoreWhitespace
	 */
	void setIgnoreWhitespace(boolean ignoreWhitespace);

	/**
	 * Returns whether this lexer is currently ignoring/swallowing whitespace tokens.
	 * @return
	 */
	boolean isIgnoreWhitespace();

	/**
	 * Push a previously read token back to the front of the lexer's internal queue.
	 * 
	 * This token will be returned by subsequent calls to {@link #peek()} or {@link #next()}.
	 * 
	 * @param tok
	 */
	void pushBack(Token tok);
}