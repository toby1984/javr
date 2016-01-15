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