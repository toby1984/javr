package de.codesourcery.javr.assembler.parser;

public interface Lexer {

	boolean eof();

	Token next();

	Token peek();

	boolean peek(TokenType t);

	void setIgnoreWhitespace(boolean ignoreWhitespace);

	boolean isIgnoreWhitespace();

	void pushBack(Token tok);

}
