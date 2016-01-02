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

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import org.apache.commons.lang3.Validate;

import de.codesourcery.javr.assembler.ast.AST;
import de.codesourcery.javr.assembler.ast.ASTNode;
import de.codesourcery.javr.assembler.ast.CommentNode;
import de.codesourcery.javr.assembler.ast.IdentifierNode;
import de.codesourcery.javr.assembler.ast.InstructionNode;
import de.codesourcery.javr.assembler.ast.LabelNode;
import de.codesourcery.javr.assembler.ast.NumberLiteralNode;
import de.codesourcery.javr.assembler.ast.RegisterNode;
import de.codesourcery.javr.assembler.ast.StatementNode;
import de.codesourcery.javr.assembler.exceptions.ParseException;

public class Parser {

    private Lexer lexer;
    private IArchitecture arch; 
    private AST ast = new AST();
    
    private final Set<TokenType> recoveryTokens = new HashSet<>(); 
    
    public static enum Severity 
    {
        INFO,WARNING,ERROR;
    }
    
    public static final class CompilationMessage 
    {
        public final Severity severity;
        public final String message;
        public final TextRegion region;
        public final ASTNode node;

        public CompilationMessage(Severity severity, String message,TextRegion region) 
        {
            this.severity = severity;
            this.message = message;
            this.region = region;
            this.node = null;
        }
        
        public CompilationMessage(Severity severity, String message,ASTNode node) 
        {
            this.severity = severity;
            this.message = message;
            this.node = node;
            this.region = node.getTextRegion();
        }        

        public CompilationMessage(Severity severity, String message) 
        {
            this( severity , message , (TextRegion) null );
        }        
    }
    
    public Parser() {
    }
    
    public void setArchitecture(IArchitecture arch) {
        
        Validate.notNull(arch, "arch must not be NULL");
        this.arch = arch;
    }
    
    public AST parse(Lexer lexer) 
    {
        Validate.notNull(lexer, "lexer must not be NULL");
        if ( arch == null ) {
            throw new IllegalStateException("architecture must be set");
        }
        
        this.lexer = lexer;
        this.ast = new AST();
        
        // skip leading newlines since parseStatement()
        // only consumes trailing newlines
        parseEOL();
        
        while ( ! lexer.eof() ) 
        {
            try {
                ast.add( parseStatement() );
            } 
            catch(Exception e) 
            {
                e.printStackTrace();
                ast.addMessage( new CompilationMessage(Severity.ERROR,e.getMessage(),lexer.peek().region() ) );
                recoveryTokens.add(TokenType.EOL);
                recover();
            }
        }
        return ast;
    }
    
    private void recover() 
    {
        if ( ! recoveryTokens.isEmpty() && ! lexer.eof() ) 
        {
            while ( ! lexer.eof() && ! recoveryTokens.contains( lexer.peek().type ) ) 
            {
                lexer.next();
            }
        }
    }
    
    private StatementNode parseStatement() 
    {
        final StatementNode result = new StatementNode();
        
        // parse label
        final LabelNode label = parseLabel();
        if ( label != null ) {
            result.add( label );
        }
        
        // parse instruction
        final InstructionNode insn = parseInstruction();
        if ( insn!= null ) {
            result.add( insn );
        }        
        
        // parse comment
        final CommentNode comment = parseComment();
        if ( comment != null ) {
            result.add( comment );
        }           
        
        // parse EOF/EOL
        if ( ! parseEOL() ) {
            throw new ParseException("Expected EOF/EOL but got "+lexer.peek(),currentOffset());
        }
        return result;
    }
    
    private void recover(TokenType t1,TokenType...t2) 
    {
        recoveryTokens.add(t1);
        if ( t2 != null ) {
            Stream.of( t2 ).forEach( t -> recoveryTokens.add( t ) );
        }
        recover();        
    }
    
    private int currentOffset() 
    {
        return lexer.peek().offset;
    }
    
    private LabelNode parseLabel() 
    {
        final Token tok = lexer.peek();
        if ( tok.hasType(TokenType.TEXT ) && Identifier.isValidIdentifier( tok.value ) ) 
        {
            final Identifier id = new Identifier( lexer.next().value );
            if ( lexer.peek( TokenType.COLON ) ) 
            {
                lexer.next();
                return new LabelNode( id , tok.region().incLength() );
            } 
            lexer.pushBack( tok );
        }
        return null;
    }
    
    private InstructionNode parseInstruction() 
    {
        Token tok = lexer.peek();
        if ( tok.hasType( TokenType.TEXT ) && arch.isValidInstruction( tok.value ) ) 
        {
            lexer.next();
            final Instruction instruction = arch.parseInstruction( tok.value );
            final InstructionNode result = new InstructionNode(instruction, tok.region());
            result.add( parseOperands() );
            return result;
        }
        return null;
    }
    
    private List<ASTNode> parseOperands() {
        ASTNode n1 = parseOperand();
        if ( n1 == null ) {
            return Collections.emptyList();
        }
        if ( lexer.peek( TokenType.COMMA ) ) 
        {
            final Token tok = lexer.next();
            ASTNode n2 = parseOperand();
            if ( n2 == null ) {
                throw new ParseException("Expected an operand", tok.offset );
            }
            return Arrays.asList(n1,n2);
        } 
        return Arrays.asList(n1);
    }
    
    private ASTNode parseOperand() 
    {
        // number literal
        ASTNode result = parseRegisterRef();
        if ( result != null ) {
            return result;
        }          
        
        result = parseNumber();
        if ( result != null ) {
            return result;
        }
        
        // identifier
        return parseIdentifier();
    }    
    
    private RegisterNode parseRegisterRef() 
    {
        Token tok = lexer.peek();
        if ( arch.isValidRegister( tok.value ) ) 
        {
            final StringBuilder buffer = new StringBuilder();
            final TextRegion region = tok.region();
            buffer.append( lexer.next().value );
            while ( ! lexer.eof() && arch.isValidRegister( buffer.toString() + lexer.peek().value ) ) {
                tok = lexer.next();
                region.merge( tok.region() );
                buffer.append( tok.value );
            }
            return new RegisterNode( arch.parseRegister( buffer.toString() ) , region );
        }
        return null;
    }
    
    private NumberLiteralNode parseNumber() 
    {
        Token tok = lexer.peek();
        if ( tok.hasType( TokenType.DIGITS ) || tok.hasType( TokenType.TEXT ) && NumberLiteralNode.isValidNumberLiteral( tok.value ) ) 
        {
            lexer.next();
            return new NumberLiteralNode( tok.value , tok.region() );
        }
        return null;
    }
    
    private IdentifierNode parseIdentifier() {
        Token tok = lexer.peek();
        if ( Identifier.isValidIdentifier( tok.value ) ) 
        {
            lexer.next();
            return new IdentifierNode( new Identifier( tok.value ) , tok.region() );
        }
        return null;
    }
    
    private CommentNode parseComment() 
    {
        final Token tok = lexer.peek();
        if ( tok.hasType( TokenType.SEMICOLON ) ) 
        {
            lexer.setIgnoreWhitespace( false );
            final StringBuilder buffer = new StringBuilder();
            try 
            {
                final TextRegion region = lexer.peek().region();
                while ( ! lexer.eof() && ! lexer.peek( TokenType.EOL ) ) 
                {
                    final Token tok2 = lexer.next();
                    region.merge( tok2.region() );
                    buffer.append( tok2.value );
                }
                return new CommentNode( buffer.toString(), region );
            } finally 
            {
                lexer.setIgnoreWhitespace( true );
            }
        }
        return null;
    }
    
    private boolean parseEOL() {
        
        if ( lexer.eof() ) {
            return true;
        }
                
        if ( lexer.peek( TokenType.EOL ) ) 
        {
            do {
                lexer.next();
            } while ( lexer.peek( TokenType.EOL ) );
            return true;
        }
        return false;
    }
}
