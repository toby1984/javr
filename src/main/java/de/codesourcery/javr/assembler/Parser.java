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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.apache.commons.lang3.Validate;

import de.codesourcery.javr.assembler.ast.AST;
import de.codesourcery.javr.assembler.ast.ASTNode;
import de.codesourcery.javr.assembler.ast.CharacterLiteralNode;
import de.codesourcery.javr.assembler.ast.CommentNode;
import de.codesourcery.javr.assembler.ast.EquLabelNode;
import de.codesourcery.javr.assembler.ast.EquNode;
import de.codesourcery.javr.assembler.ast.IdentifierNode;
import de.codesourcery.javr.assembler.ast.InitMemNode;
import de.codesourcery.javr.assembler.ast.InitMemNode.ElementSize;
import de.codesourcery.javr.assembler.ast.InstructionNode;
import de.codesourcery.javr.assembler.ast.LabelNode;
import de.codesourcery.javr.assembler.ast.NumberLiteralNode;
import de.codesourcery.javr.assembler.ast.RegisterNode;
import de.codesourcery.javr.assembler.ast.ReserveMemNode;
import de.codesourcery.javr.assembler.ast.SegmentNode;
import de.codesourcery.javr.assembler.ast.SegmentNode.Segment;
import de.codesourcery.javr.assembler.ast.StatementNode;
import de.codesourcery.javr.assembler.ast.StringLiteral;
import de.codesourcery.javr.assembler.exceptions.ParseException;

public class Parser 
{
    private Lexer lexer;
    private IArchitecture arch; 
    private AST ast = new AST();

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
        
        public static CompilationMessage error(String msg,ASTNode node) {
            return new CompilationMessage(Severity.ERROR , msg , node ); 
        }
        
        public static CompilationMessage error(String msg,TextRegion region) {
            return new CompilationMessage(Severity.ERROR , msg , region ); 
        }        
        
        public static CompilationMessage info(String msg,ASTNode node) {
            return new CompilationMessage(Severity.INFO , msg , node ); 
        }
        
        public static CompilationMessage info(String msg,TextRegion region) {
            return new CompilationMessage(Severity.INFO, msg , region ); 
        }        
        
        public static CompilationMessage warning(String msg,ASTNode node) {
            return new CompilationMessage(Severity.WARNING , msg , node ); 
        }        
        
        public static CompilationMessage warning(String msg,TextRegion region) {
            return new CompilationMessage(Severity.WARNING, msg , region ); 
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
                break; // TODO: Implement parse recovery
            }
        }
        return ast;
    }

    private StatementNode parseStatement() 
    {
        final StatementNode result = new StatementNode();

        // parse label
        final LabelNode label = parseLabel();
        if ( label != null ) {
            result.add( label );
        }

        // parse .XXXX directive
        final ASTNode directive = parseDirective();
        if ( directive != null ) {
            result.add( directive );
        } 
        else 
        {
            // parse instruction
            final InstructionNode insn = parseInstruction();
            if ( insn!= null ) {
                result.add( insn );
            }       
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

    private ASTNode parseDirective() 
    {
        Token tok = lexer.peek();
        if ( tok.hasType( TokenType.DOT ) ) 
        {
            final TextRegion region = lexer.next().region();
            
            final Token tok2 = lexer.peek();
            if ( tok2.hasType(TokenType.TEXT ) ) 
            {
                lexer.next();
                final ASTNode result = parseDirective2( tok2 );
                if ( result != null ) 
                {
                    region.merge( tok2 );
                    result.setRegion( region );
                    return result;                    
                }
            }
            lexer.pushBack( tok );
        }
        return null;
    }

    private ASTNode parseDirective2(Token tok2) 
    {
        final String value = tok2.value;
        switch( value.toLowerCase() ) 
        {
            case "byte":
                final ASTNode expr = parseExpression();
                if ( expr != null ) 
                {
                    final ReserveMemNode result = new ReserveMemNode();
                    result.add( expr );
                    return result;
                }
                break;
            case "cseg": return new SegmentNode(Segment.FLASH, tok2.region() );
            case "db":
            case "dw":
                final List<ASTNode> values = parseExpressionList();
                if ( ! values.isEmpty() )
                {
                    final ElementSize size = value.toLowerCase().equals("db") ? ElementSize.BYTE : ElementSize.WORD;
                    final InitMemNode result = new InitMemNode( size );
                    result.add( values );
                    return result;
                }
                break;            
            case "dseg": 
                return new SegmentNode(Segment.SRAM, tok2.region() );
            case "eseg": 
                return new SegmentNode(Segment.EEPROM, tok2.region() );
            case "equ":
                if ( lexer.peek(TokenType.TEXT ) && Identifier.isValidIdentifier( lexer.peek().value ) ) 
                {
                    final Token tok = lexer.next();
                    final Identifier name = new Identifier( tok.value );
                    if ( lexer.peek( TokenType.OPERATOR ) && lexer.peek().value.equals("=") ) 
                    {
                        lexer.next();
                        final ASTNode expr2 = parseExpression();
                        if ( expr2 != null ) {
                            final EquNode result = new EquNode();
                            result.add( new EquLabelNode( name , tok.region() ) );
                            result.add( expr2 );
                            return result;
                        }
                        throw new ParseException("Expected an expression",lexer.peek());
                    } 
                    throw new ParseException("Expected '='",lexer.peek());
                }
                break;
            default:
                throw new RuntimeException("Unknown directive: ."+tok2.value);
        }
        return null;
    }
    
    private List<ASTNode> parseExpressionList() 
    {
        final List<ASTNode> result = new ArrayList<>();
        while(true)
        {
            ASTNode expr = parseExpression();
            if ( expr == null ) {
                return result;
            }
            result.add( expr );
            if ( lexer.peek( TokenType.COMMA ) ) {
                lexer.next();
            }
        } 
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
            final Instruction instruction = new Instruction( tok.value );
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
        // register ref
        ASTNode result = parseRegisterRef();
        if ( result != null ) {
            return result;
        } 
        return parseExpression();
    }    

    private CharacterLiteralNode parseCharLiteral() 
    {
        final Token tok = lexer.peek();
        if ( tok.hasType( TokenType.SINGLE_QUOTE ) ) 
        {
            lexer.setIgnoreWhitespace( false );
            try 
            {            
                lexer.next();
                final Token tok2 = lexer.next();
                if ( tok2.value.length() != 1 ) 
                {
                    throw new ParseException("Expected a single character", tok2.offset);
                }
                final Token tok3 = lexer.next();
                if ( ! tok3.hasType( TokenType.SINGLE_QUOTE ) ) {
                    throw new ParseException("Expected closing single-quote" , tok3.offset);
                }
                return new CharacterLiteralNode( tok2.value.charAt(0) , tok.region().merge( tok2 ).merge( tok3 ) );
            } finally {
                lexer.setIgnoreWhitespace(true);
            }
        }
        return null;
    }

    private StringLiteral parseStringLiteral() 
    {
        final Token tok = lexer.peek();
        if ( tok.hasType( TokenType.DOUBLE_QUOTE ) ) 
        {
            lexer.setIgnoreWhitespace( false );
            try 
            {
                lexer.next();
                final StringBuilder buffer = new StringBuilder();
                do 
                {
                    final Token tok2 = lexer.peek();
                    if ( tok2.hasType( TokenType.EOL ,TokenType.EOF , TokenType.DOUBLE_QUOTE ) ) 
                    {
                        break;
                    }
                    lexer.next();
                    buffer.append( tok2.value );
                } while ( ! lexer.eof() );

                if ( ! lexer.peek( TokenType.DOUBLE_QUOTE ) ) {
                    throw new ParseException("Unterminated string literal", lexer.peek().offset );
                }
                final Token tok3 = lexer.next();
                return new StringLiteral( buffer.toString() , tok.region().merge( tok3) );
            } finally {
                lexer.setIgnoreWhitespace( true );
            }
        }
        return null;
    }    

    private RegisterNode parseRegisterRef() 
    {
        final Token tok = lexer.peek();
        TextRegion region = tok.region();
        boolean preDecrement = tok.hasType( TokenType.OPERATOR ) && tok.value.equals("-");
        if ( preDecrement) {
            lexer.next();
        }
        String name = parseRegisterName(region);
        if ( name != null ) 
        {
            if ( ! isCompoundRegister( name ) ) 
            {
                if ( lexer.peek(TokenType.COLON ) ) {
                    region.merge( lexer.next() );
                    final String otherName = parseRegisterName( region );
                    if ( otherName == null ) {
                        throw new ParseException("Expected another register name",lexer.peek());
                    }
                    name = name +":" + otherName;
                }
            }
            final boolean postIncrement = lexer.peek(TokenType.OPERATOR) && lexer.peek().value.equals("+");
            ASTNode displacement = null;
            if ( postIncrement ) 
            {
                if ( preDecrement ) {
                    throw new ParseException("Either pre-decrement or post-increment are possible but not both",lexer.peek());
                }
                region.merge( lexer.next() );
                displacement = parseExpression();
            }            
            final Register reg = new Register( name , postIncrement && displacement == null , preDecrement );
            final RegisterNode result = new RegisterNode( reg , region );
            if ( displacement != null ) {
                result.add( displacement );
            }
            return result;
        }
        if ( preDecrement ) {
            lexer.pushBack( tok );
        }
        return null;
    }
    
    private ASTNode parseExpression() 
    {
        // character literal
        ASTNode result = parseCharLiteral();
        if ( result != null ) {
            return result;
        }

        // string literal
        result = parseStringLiteral();
        if ( result != null ) {
            return result;
        }

        // number literal
        result = parseNumber();
        if ( result != null ) {
            return result;
        }

        // identifier
        return parseIdentifier(); 
    }
    
    private boolean isCompoundRegister(String name) {
        switch( name.toLowerCase() ) {
            case "x":
            case "y":
            case "z":
                return true;
            default:
                return false;
        }
    }
    
    private String parseRegisterName(TextRegion region) 
    {
        final Token tok = lexer.peek();
        if ( tok.hasType(TokenType.TEXT) )
        {
            lexer.next();
            final String lower = tok.value.toLowerCase();
            if ( lower.charAt(0) == 'r' ) 
            {
                final String sub = tok.value.substring(1);
                if ( isNumber(sub) ) 
                {
                    region.merge( tok );
                    return tok.value;
                }
            }
            switch( lower ) {
                case "x":
                case "y":
                case "z":
                    region.merge( tok );
                    return tok.value;
            }
            lexer.pushBack( tok );
        }
        return null;
    }
    
    private static final boolean isNumber(String s) 
    {
        if ( s == null || s.length() == 0) {
            return false;
        }
        for ( int i = 0 , len=s.length() ; i < len ; i++ ) {
            if ( ! Character.isDigit( s.charAt(i) ) ) {
                return false;
            }
        }
        return true;
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
