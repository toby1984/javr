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

    @Override
    public void setScanner(Scanner scanner)
    {
        delegate.setScanner( scanner );
    }
}