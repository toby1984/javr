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

import org.junit.Assert;
import org.junit.Test;

import de.codesourcery.javr.assembler.arch.ATMega88;
import de.codesourcery.javr.assembler.ast.AST;
import de.codesourcery.javr.assembler.ast.CommentNode;
import de.codesourcery.javr.assembler.ast.IdentifierNode;
import de.codesourcery.javr.assembler.ast.InstructionNode;
import de.codesourcery.javr.assembler.ast.LabelNode;
import de.codesourcery.javr.assembler.ast.NumberLiteralNode;
import de.codesourcery.javr.assembler.ast.RegisterNode;
import de.codesourcery.javr.assembler.ast.StatementNode;

public class ParserTest 
{
    private final IArchitecture arch = new ATMega88();
    // single-line tests
    
    @Test
    public void testEmptyFile() 
    {
        AST ast = parse("");
        Assert.assertNotNull(ast);
        Assert.assertFalse( ast.hasChildren() );
        Assert.assertEquals( 0 ,  ast.childCount() );        
    }

    @Test
    public void testParseBlankLine() 
    {
        AST ast = parse("\n");
        Assert.assertNotNull(ast);
        Assert.assertFalse( ast.hasChildren() );
        Assert.assertEquals( 0 ,  ast.childCount() );
    }    
    
    @Test
    public void testParseCommentLine() 
    {
        AST ast = parse("; test comment");
        Assert.assertNotNull(ast);
        Assert.assertTrue( ast.hasChildren() );
        Assert.assertEquals( 1 ,  ast.childCount() );
        
        final StatementNode stmt = (StatementNode) ast.child(0);
        Assert.assertNotNull(stmt);
        Assert.assertEquals( 1 , stmt.childCount() );
        
        final CommentNode comment = (CommentNode) stmt.child(0);
        Assert.assertNotNull(comment);
        Assert.assertEquals("; test comment" , comment.value );
    }   
    
    @Test
    public void testParseLabelLine() 
    {
        AST ast = parse("label:");
        Assert.assertNotNull(ast);
        Assert.assertTrue( ast.hasChildren() );
        Assert.assertEquals( 1 ,  ast.childCount() );
        
        final StatementNode stmt = (StatementNode) ast.child(0);
        Assert.assertNotNull(stmt);
        Assert.assertEquals( 1 , stmt.childCount() );
        
        final LabelNode label = (LabelNode) stmt.child(0);
        Assert.assertNotNull(label);
        Assert.assertEquals( new Identifier("label") , label.identifier );
    }    
    
    @Test
    public void testParseLabelLineWithComment() 
    {
        AST ast = parse("label: ; test comment");
        Assert.assertNotNull(ast);
        Assert.assertTrue( ast.hasChildren() );
        Assert.assertEquals( 1 ,  ast.childCount() ); 
        
        final StatementNode stmt = (StatementNode) ast.child(0);
        Assert.assertNotNull(stmt);
        Assert.assertEquals( 2 , stmt.childCount() );
        
        final LabelNode label = (LabelNode) stmt.child(0);
        Assert.assertNotNull(label);
        Assert.assertEquals( new Identifier("label") , label.identifier );
        
        final CommentNode comment = (CommentNode) stmt.child(1);
        Assert.assertNotNull(comment);
        Assert.assertEquals("; test comment" , comment.value );        
    }     
    
    @Test
    public void testParseInstructionLineWithNoOperands() 
    {
        AST ast = parse("add");
        Assert.assertNotNull(ast);
        Assert.assertTrue( ast.hasChildren() );
        Assert.assertEquals( 1 ,  ast.childCount() ); 
        
        final StatementNode stmt = (StatementNode) ast.child(0);
        Assert.assertNotNull(stmt);
        Assert.assertEquals( 1 , stmt.childCount() );
        
        final InstructionNode insn = (InstructionNode) stmt.child(0);
        Assert.assertEquals( 0 , insn.childCount() );
        Assert.assertNotNull(insn);
        Assert.assertEquals( arch.parseInstruction("add") , insn.instruction );
    }    
    
    @Test
    public void testParseInstructionWithOneDecimalNumberOperand() 
    {
        AST ast = parse("add 123");
        Assert.assertNotNull(ast);
        Assert.assertTrue( ast.hasChildren() );
        Assert.assertEquals( 1 ,  ast.childCount() ); 
        
        final StatementNode stmt = (StatementNode) ast.child(0);
        Assert.assertNotNull(stmt);
        Assert.assertEquals( 1 , stmt.childCount() );
        
        final InstructionNode insn = (InstructionNode) stmt.child(0);
        Assert.assertEquals( 1 , insn.childCount() );        
        Assert.assertNotNull(insn);
        Assert.assertEquals( arch.parseInstruction("add") , insn.instruction );
        
        final NumberLiteralNode num = (NumberLiteralNode) insn.child( 0 );
        Assert.assertEquals( 123 , num.getValue() );
        Assert.assertEquals( NumberLiteralNode.LiteralType.DECIMAL , num.getType() );
    }    
    
    @Test
    public void testParseInstructionWithTwoDecimalNumberOperands() 
    {
        AST ast = parse("add 123 , 456");
        Assert.assertNotNull(ast);
        Assert.assertTrue( ast.hasChildren() );
        Assert.assertEquals( 1 ,  ast.childCount() ); 
        
        final StatementNode stmt = (StatementNode) ast.child(0);
        Assert.assertNotNull(stmt);
        Assert.assertEquals( 1 , stmt.childCount() );
        
        final InstructionNode insn = (InstructionNode) stmt.child(0);
        Assert.assertEquals( 2 , insn.childCount() );        
        Assert.assertNotNull(insn);
        Assert.assertEquals( arch.parseInstruction("add") , insn.instruction );
        
        final NumberLiteralNode num1 = (NumberLiteralNode) insn.child( 0 );
        Assert.assertEquals( 123 , num1.getValue() );
        Assert.assertEquals( NumberLiteralNode.LiteralType.DECIMAL , num1.getType() );
        
        final NumberLiteralNode num2 = (NumberLiteralNode) insn.child( 1 );
        Assert.assertEquals( 456 , num2.getValue() );
        Assert.assertEquals( NumberLiteralNode.LiteralType.DECIMAL , num2.getType() );        
    }     
    
    @Test
    public void testParseInstructionWithTwoRegisterOperands() 
    {
        AST ast = parse("add r0,r1");
        Assert.assertNotNull(ast);
        Assert.assertTrue( ast.hasChildren() );
        Assert.assertEquals( 1 ,  ast.childCount() ); 
        
        final StatementNode stmt = (StatementNode) ast.child(0);
        Assert.assertNotNull(stmt);
        Assert.assertEquals( 1 , stmt.childCount() );
        
        final InstructionNode insn = (InstructionNode) stmt.child(0);
        Assert.assertEquals( 2 , insn.childCount() );        
        Assert.assertNotNull(insn);
        Assert.assertEquals( arch.parseInstruction("add") , insn.instruction );
        
        final RegisterNode num1 = (RegisterNode) insn.child( 0 );
        Assert.assertEquals( arch.parseRegister( "r0" ) , num1.register );
        
        final RegisterNode num2 = (RegisterNode) insn.child( 1 );
        Assert.assertEquals( arch.parseRegister( "r1" ) , num2.register );        
    }     
    
    @Test
    public void testParseInstructionWithRegisterAndIdentifierOperands() 
    {
        AST ast = parse("add r0,counter");
        Assert.assertNotNull(ast);
        Assert.assertTrue( ast.hasChildren() );
        Assert.assertEquals( 1 ,  ast.childCount() ); 
        
        final StatementNode stmt = (StatementNode) ast.child(0);
        Assert.assertNotNull(stmt);
        Assert.assertEquals( 1 , stmt.childCount() );
        
        final InstructionNode insn = (InstructionNode) stmt.child(0);
        Assert.assertEquals( 2 , insn.childCount() );        
        Assert.assertNotNull(insn);
        Assert.assertEquals( arch.parseInstruction("add") , insn.instruction );
        
        final RegisterNode num1 = (RegisterNode) insn.child( 0 );
        Assert.assertEquals( arch.parseRegister( "r0" ) , num1.register );
        
        final IdentifierNode num2 = (IdentifierNode) insn.child( 1 );
        Assert.assertEquals( new Identifier( "counter" ) , num2.value);        
    }     
    
    @Test
    public void testParseInstructionWithOneHexadecimalNumberOperand() 
    {
        AST ast = parse("add 0x123");
        Assert.assertNotNull(ast);
        Assert.assertTrue( ast.hasChildren() );
        Assert.assertEquals( 1 ,  ast.childCount() ); 
        
        final StatementNode stmt = (StatementNode) ast.child(0);
        Assert.assertNotNull(stmt);
        Assert.assertEquals( 1 , stmt.childCount() );
        
        final InstructionNode insn = (InstructionNode) stmt.child(0);
        Assert.assertEquals( 1 , insn.childCount() );        
        Assert.assertNotNull(insn);
        Assert.assertEquals( arch.parseInstruction("add") , insn.instruction );
        
        final NumberLiteralNode num = (NumberLiteralNode) insn.child( 0 );
        Assert.assertEquals( 0x123 , num.getValue() );
        Assert.assertEquals( NumberLiteralNode.LiteralType.HEXADECIMAL , num.getType() );
    }    
    
    @Test
    public void testParseInstructionWithOneBinaryNumberOperand() 
    {
        AST ast = parse("add %1011");
        Assert.assertNotNull(ast);
        Assert.assertTrue( ast.hasChildren() );
        Assert.assertEquals( 1 ,  ast.childCount() ); 
        
        final StatementNode stmt = (StatementNode) ast.child(0);
        Assert.assertNotNull(stmt);
        Assert.assertEquals( 1 , stmt.childCount() );
        
        final InstructionNode insn = (InstructionNode) stmt.child(0);
        Assert.assertEquals( 1 , insn.childCount() );        
        Assert.assertNotNull(insn);
        Assert.assertEquals( arch.parseInstruction("add") , insn.instruction );
        
        final NumberLiteralNode num = (NumberLiteralNode) insn.child( 0 );
        Assert.assertEquals( 0b1011 , num.getValue() );
        Assert.assertEquals( NumberLiteralNode.LiteralType.BINARY , num.getType() );
    }     
    
    @Test
    public void testParseCommentLines() 
    {
        AST ast = parse("; test comment1\n; test comment2");
        Assert.assertNotNull(ast);
        Assert.assertTrue( ast.hasChildren() );
        Assert.assertEquals( 2 , ast.childCount() );
        
        {
            final StatementNode stmt = (StatementNode) ast.child(0);
            Assert.assertNotNull(stmt);
            Assert.assertEquals( 1 , stmt.childCount() );
            
            final CommentNode comment = (CommentNode) stmt.child(0);
            Assert.assertNotNull(comment);
            Assert.assertEquals("; test comment1" , comment.value );
        }
        
        {
            final StatementNode stmt = (StatementNode) ast.child(1);
            Assert.assertNotNull(stmt);
            Assert.assertEquals( 1 , stmt.childCount() );
            
            final CommentNode comment = (CommentNode) stmt.child(0);
            Assert.assertNotNull(comment);
            Assert.assertEquals("; test comment2" , comment.value );            
        }
        
    }     
    
    // multi-line tests
    
    @Test
    public void testParseBlankLines() 
    {
        AST ast = parse("\n\n\n");
        Assert.assertNotNull(ast);
        Assert.assertFalse( ast.hasChildren() );
        Assert.assertEquals( 0 ,  ast.childCount() );
    }      
    
    // helper functions
    private AST parse(String s) 
    {
        final Parser p = new Parser();
        p.setArchitecture( arch );
        return p.parse( new Lexer(new Scanner(s) ) );
    }
}
