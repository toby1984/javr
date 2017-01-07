package de.codesourcery.javr.assembler.parser;

public class DebugLexer implements Lexer {

    private final Lexer delegate;

    public DebugLexer(Lexer delegate) {
        this.delegate = delegate;
    }
    
    @Override
    public boolean eof() {
        return delegate.eof();
    }

    @Override
    public Token next() {
        Token result = delegate.next();
        System.out.println("DEBUG: next()- "+result);
        return result;
    }

    @Override
    public Token peek() {
        Token result = delegate.peek();
        System.out.println("DEBUG: peek()- "+result);
        return result;
    }

    @Override
    public boolean peek(TokenType t) {
        Token tok = delegate.peek();
        final boolean result = delegate.peek(t);
        System.out.println("DEBUG: peek("+t+")- "+tok+" => "+result);
        return result;
    }

    @Override
    public void setIgnoreWhitespace(boolean ignoreWhitespace) {
        delegate.setIgnoreWhitespace( ignoreWhitespace );
    }

    @Override
    public boolean isIgnoreWhitespace() {
        return delegate.isIgnoreWhitespace();
    }

    @Override
    public void pushBack(Token tok) {
        System.out.println("DEBUG: pushBack("+tok+")");
        delegate.pushBack( tok );
    }

}
