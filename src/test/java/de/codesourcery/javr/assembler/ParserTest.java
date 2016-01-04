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

import de.codesourcery.javr.assembler.arch.IArchitecture;
import de.codesourcery.javr.assembler.arch.impl.ATMega88;
import de.codesourcery.javr.assembler.parser.Identifier;
import de.codesourcery.javr.assembler.parser.Lexer;
import de.codesourcery.javr.assembler.parser.OperatorType;
import de.codesourcery.javr.assembler.parser.Parser;
import de.codesourcery.javr.assembler.parser.Scanner;
import de.codesourcery.javr.assembler.parser.ast.AST;
import de.codesourcery.javr.assembler.parser.ast.CharacterLiteralNode;
import de.codesourcery.javr.assembler.parser.ast.CommentNode;
import de.codesourcery.javr.assembler.parser.ast.DirectiveNode;
import de.codesourcery.javr.assembler.parser.ast.DirectiveNode.Directive;
import de.codesourcery.javr.assembler.parser.ast.EquLabelNode;
import de.codesourcery.javr.assembler.parser.ast.FunctionCallNode;
import de.codesourcery.javr.assembler.parser.ast.IdentifierNode;
import de.codesourcery.javr.assembler.parser.ast.InstructionNode;
import de.codesourcery.javr.assembler.parser.ast.LabelNode;
import de.codesourcery.javr.assembler.parser.ast.NumberLiteralNode;
import de.codesourcery.javr.assembler.parser.ast.NumberLiteralNode.LiteralType;
import de.codesourcery.javr.assembler.parser.ast.OperatorNode;
import de.codesourcery.javr.assembler.parser.ast.PreprocessorNode;
import de.codesourcery.javr.assembler.parser.ast.PreprocessorNode.Preprocessor;
import de.codesourcery.javr.assembler.parser.ast.RegisterNode;
import de.codesourcery.javr.assembler.parser.ast.StatementNode;
import de.codesourcery.javr.assembler.parser.ast.StringLiteral;

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
    public void testParseIfDef() 
    {
        AST ast = parse("#ifdef test");
        Assert.assertNotNull(ast);
        Assert.assertTrue( ast.hasChildren() );
        Assert.assertEquals( 1 ,  ast.childCount() );
        
        final StatementNode stmt = (StatementNode) ast.child(0);
        Assert.assertNotNull(stmt);
        Assert.assertEquals( 1 , stmt.childCount() );
        
        final PreprocessorNode ins = (PreprocessorNode) stmt.child(0);
        Assert.assertNotNull(ins);
        Assert.assertEquals( Preprocessor.IF_DEFINE , ins.type );
        Assert.assertEquals( 1 , ins.arguments.size() );
        Assert.assertEquals( "test" , ins.arguments.get(0) );
    }
    
    @Test
    public void testParseDefine1() 
    {
        AST ast = parse("#define test");
        Assert.assertNotNull(ast);
        Assert.assertTrue( ast.hasChildren() );
        Assert.assertEquals( 1 ,  ast.childCount() );
        
        final StatementNode stmt = (StatementNode) ast.child(0);
        Assert.assertNotNull(stmt);
        Assert.assertEquals( 1 , stmt.childCount() );
        
        final PreprocessorNode ins = (PreprocessorNode) stmt.child(0);
        Assert.assertNotNull(ins);
        Assert.assertEquals( Preprocessor.DEFINE , ins.type );
        Assert.assertEquals( 1 , ins.arguments.size() );
        Assert.assertEquals( "test" , ins.arguments.get(0) );
    }
    
    @Test
    public void testParseDefine2() 
    {
        AST ast = parse("#define test test2");
        Assert.assertNotNull(ast);
        Assert.assertTrue( ast.hasChildren() );
        Assert.assertEquals( 1 ,  ast.childCount() );
        
        final StatementNode stmt = (StatementNode) ast.child(0);
        Assert.assertNotNull(stmt);
        Assert.assertEquals( 1 , stmt.childCount() );
        
        final PreprocessorNode ins = (PreprocessorNode) stmt.child(0);
        Assert.assertNotNull(ins);
        Assert.assertEquals( Preprocessor.DEFINE , ins.type );
        Assert.assertEquals( 2 , ins.arguments.size() );
        Assert.assertEquals( "test" , ins.arguments.get(0) );
        Assert.assertEquals( "test2" , ins.arguments.get(1) );
    }    
    
    @Test
    public void testParseEndIf() 
    {
        AST ast = parse("#endif");
        Assert.assertNotNull(ast);
        Assert.assertTrue( ast.hasChildren() );
        Assert.assertEquals( 1 ,  ast.childCount() );
        
        final StatementNode stmt = (StatementNode) ast.child(0);
        Assert.assertNotNull(stmt);
        Assert.assertEquals( 1 , stmt.childCount() );
        
        final PreprocessorNode ins = (PreprocessorNode) stmt.child(0);
        Assert.assertNotNull(ins);
        Assert.assertEquals( Preprocessor.ENDIF , ins.type );
        Assert.assertEquals( 0 , ins.arguments.size() );
    }    
    
    @Test
    public void testParsePragma() 
    {
        AST ast = parse("#pragma a b c");
        Assert.assertNotNull(ast);
        Assert.assertTrue( ast.hasChildren() );
        Assert.assertEquals( 1 ,  ast.childCount() );
        
        final StatementNode stmt = (StatementNode) ast.child(0);
        Assert.assertNotNull(stmt);
        Assert.assertEquals( 1 , stmt.childCount() );
        
        final PreprocessorNode ins = (PreprocessorNode) stmt.child(0);
        Assert.assertNotNull(ins);
        Assert.assertEquals( Preprocessor.PRAGMA , ins.type );
        Assert.assertEquals( 3 , ins.arguments.size() );
        Assert.assertEquals( "a" , ins.arguments.get(0) );
        Assert.assertEquals( "b" , ins.arguments.get(1) );
        Assert.assertEquals( "c" , ins.arguments.get(2) );
    }    
    
    @Test
    public void testParseASMCommentLine() 
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
    public void testParseCStyleCommentLine() 
    {
        AST ast = parse("// test comment");
        Assert.assertNotNull(ast);
        Assert.assertTrue( ast.hasChildren() );
        Assert.assertEquals( 1 ,  ast.childCount() );
        
        final StatementNode stmt = (StatementNode) ast.child(0);
        Assert.assertNotNull(stmt);
        Assert.assertEquals( 1 , stmt.childCount() );
        
        final CommentNode comment = (CommentNode) stmt.child(0);
        Assert.assertNotNull(comment);
        Assert.assertEquals("// test comment" , comment.value );
    }     
    
    @Test
    public void testParseCStyleMultiLineComment1() 
    {
        AST ast = parse("/* test * comment */");
        Assert.assertNotNull(ast);
        Assert.assertTrue( ast.hasChildren() );
        Assert.assertEquals( 1 ,  ast.childCount() );
        
        final StatementNode stmt = (StatementNode) ast.child(0);
        Assert.assertNotNull(stmt);
        Assert.assertEquals( 1 , stmt.childCount() );
        
        final CommentNode comment = (CommentNode) stmt.child(0);
        Assert.assertNotNull(comment);
        Assert.assertEquals("/* test * comment */" , comment.value );
    }     
    
    @Test
    public void testParseCStyleMultiLineComment2() 
    {
        AST ast = parse("/* test \ncomment */");
        Assert.assertNotNull(ast);
        Assert.assertTrue( ast.hasChildren() );
        Assert.assertEquals( 1 ,  ast.childCount() );
        
        final StatementNode stmt = (StatementNode) ast.child(0);
        Assert.assertNotNull(stmt);
        Assert.assertEquals( 1 , stmt.childCount() );
        
        final CommentNode comment = (CommentNode) stmt.child(0);
        Assert.assertNotNull(comment);
        Assert.assertEquals("/* test \ncomment */" , comment.value );
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
        Assert.assertEquals( "add" , insn.instruction.getMnemonic());
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
        Assert.assertEquals( "add" , insn.instruction.getMnemonic());
        
        final NumberLiteralNode num = (NumberLiteralNode) insn.child( 0 );
        Assert.assertEquals( 123 , num.getValue().intValue() );
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
        Assert.assertEquals( "add" , insn.instruction.getMnemonic());
        
        final NumberLiteralNode num1 = (NumberLiteralNode) insn.child( 0 );
        Assert.assertEquals( 123 , num1.getValue().intValue() );
        Assert.assertEquals( NumberLiteralNode.LiteralType.DECIMAL , num1.getType() );
        
        final NumberLiteralNode num2 = (NumberLiteralNode) insn.child( 1 );
        Assert.assertEquals( 456 , num2.getValue().intValue()  );
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
        Assert.assertEquals( "add" , insn.instruction.getMnemonic());
        
        final RegisterNode num1 = (RegisterNode) insn.child( 0 );
        Assert.assertFalse( num1.register.isCompoundRegister() );
        Assert.assertEquals( 0 , num1.register.getRegisterNumber() );
        
        final RegisterNode num2 = (RegisterNode) insn.child( 1 );
        Assert.assertFalse( num2.register.isCompoundRegister() );
        Assert.assertEquals( 1 , num2.register.getRegisterNumber() );      
    }     
    
    @Test
    public void testParseInstructionWithPostIncrement() 
    {
        AST ast = parse("add r0,Z+");
        Assert.assertNotNull(ast);
        Assert.assertTrue( ast.hasChildren() );
        Assert.assertEquals( 1 ,  ast.childCount() ); 
        
        final StatementNode stmt = (StatementNode) ast.child(0);
        Assert.assertNotNull(stmt);
        Assert.assertEquals( 1 , stmt.childCount() );
        
        final InstructionNode insn = (InstructionNode) stmt.child(0);
        Assert.assertEquals( 2 , insn.childCount() );        
        Assert.assertNotNull(insn);
        Assert.assertEquals( "add" , insn.instruction.getMnemonic());
        
        final RegisterNode num1 = (RegisterNode) insn.child( 0 );
        Assert.assertFalse( num1.register.isCompoundRegister() );
        Assert.assertEquals( 0 , num1.register.getRegisterNumber() );
        
        final RegisterNode num2 = (RegisterNode) insn.child( 1 );
        Assert.assertTrue( num2.register.isCompoundRegister() );
        Assert.assertTrue( num2.register.isPostIncrement() );
        Assert.assertFalse( num2.register.isPreDecrement() );
        Assert.assertEquals( Register.REG_Z , num2.register.getRegisterNumber() );      
    }    
    
    @Test
    public void testParseInstructionWithPreDecrement() 
    {
        AST ast = parse("add r0,-X");
        Assert.assertNotNull(ast);
        Assert.assertTrue( ast.hasChildren() );
        Assert.assertEquals( 1 ,  ast.childCount() ); 
        
        final StatementNode stmt = (StatementNode) ast.child(0);
        Assert.assertNotNull(stmt);
        Assert.assertEquals( 1 , stmt.childCount() );
        
        final InstructionNode insn = (InstructionNode) stmt.child(0);
        Assert.assertEquals( 2 , insn.childCount() );        
        Assert.assertNotNull(insn);
        Assert.assertEquals( "add" , insn.instruction.getMnemonic());
        
        final RegisterNode num1 = (RegisterNode) insn.child( 0 );
        Assert.assertFalse( num1.register.isCompoundRegister() );
        Assert.assertEquals( 0 , num1.register.getRegisterNumber() );
        
        final RegisterNode num2 = (RegisterNode) insn.child( 1 );
        Assert.assertTrue( num2.register.isCompoundRegister() );
        Assert.assertFalse( num2.register.isPostIncrement() );
        Assert.assertTrue( num2.register.isPreDecrement() );
        Assert.assertEquals( Register.REG_X , num2.register.getRegisterNumber() );      
    }      
    
    @Test
    public void testParseInstructionWithExpression() 
    {
        AST ast = parse("add r0,1+2");
        Assert.assertNotNull(ast);
        Assert.assertTrue( ast.hasChildren() );
        Assert.assertEquals( 1 ,  ast.childCount() ); 
        
        final StatementNode stmt = (StatementNode) ast.child(0);
        Assert.assertNotNull(stmt);
        Assert.assertEquals( 1 , stmt.childCount() );
        
        final InstructionNode insn = (InstructionNode) stmt.child(0);
        Assert.assertEquals( 2 , insn.childCount() );        
        Assert.assertNotNull(insn);
        Assert.assertEquals( "add" , insn.instruction.getMnemonic());
        
        final RegisterNode num1 = (RegisterNode) insn.child( 0 );
        Assert.assertFalse( num1.register.isCompoundRegister() );
        Assert.assertEquals( 0 , num1.register.getRegisterNumber() );
        
        final OperatorNode num2 = (OperatorNode) insn.child( 1 );
        Assert.assertEquals( OperatorType.PLUS , num2.type );
        Assert.assertEquals( 2 , num2.childCount() );
        Assert.assertEquals( 1 , ((NumberLiteralNode) num2.child(0)).getValue().intValue() );
        Assert.assertEquals( 2 , ((NumberLiteralNode) num2.child(1)).getValue().intValue() );
    }  

    @Test
    public void testParseInstructionWithFunction() 
    {
        AST ast = parse("add r0,HIGH( 1,2 )");
        Assert.assertNotNull(ast);
        Assert.assertTrue( ast.hasChildren() );
        Assert.assertEquals( 1 ,  ast.childCount() ); 
        
        final StatementNode stmt = (StatementNode) ast.child(0);
        Assert.assertNotNull(stmt);
        Assert.assertEquals( 1 , stmt.childCount() );
        
        final InstructionNode insn = (InstructionNode) stmt.child(0);
        Assert.assertEquals( 2 , insn.childCount() );        
        Assert.assertNotNull(insn);
        Assert.assertEquals( "add" , insn.instruction.getMnemonic());
        
        final RegisterNode num1 = (RegisterNode) insn.child( 0 );
        Assert.assertFalse( num1.register.isCompoundRegister() );
        Assert.assertEquals( 0 , num1.register.getRegisterNumber() );
        
        final FunctionCallNode num2 = (FunctionCallNode) insn.child( 1 );
        Assert.assertEquals( new Identifier("HIGH") , num2.functionName );
        Assert.assertEquals( 2 , num2.childCount() );
        Assert.assertEquals( 1 , ((NumberLiteralNode) num2.child(0)).getValue().intValue() );
        Assert.assertEquals( 2 , ((NumberLiteralNode) num2.child(1)).getValue().intValue() );
    }    
    
    @Test
    public void testParseInstructionWithCompoundRegister() 
    {
        AST ast = parse("add r0,r4:r3");
        Assert.assertNotNull(ast);
        Assert.assertTrue( ast.hasChildren() );
        Assert.assertEquals( 1 ,  ast.childCount() ); 
        
        final StatementNode stmt = (StatementNode) ast.child(0);
        Assert.assertNotNull(stmt);
        Assert.assertEquals( 1 , stmt.childCount() );
        
        final InstructionNode insn = (InstructionNode) stmt.child(0);
        Assert.assertEquals( 2 , insn.childCount() );        
        Assert.assertNotNull(insn);
        Assert.assertEquals( "add" , insn.instruction.getMnemonic());
        
        final RegisterNode num1 = (RegisterNode) insn.child( 0 );
        Assert.assertFalse( num1.register.isCompoundRegister() );
        Assert.assertEquals( 0 , num1.register.getRegisterNumber() );
        
        final RegisterNode num2 = (RegisterNode) insn.child( 1 );
        Assert.assertTrue( num2.register.isCompoundRegister() );
        Assert.assertFalse( num2.register.isPostIncrement() );
        Assert.assertFalse( num2.register.isPreDecrement() );
        Assert.assertEquals( 3 , num2.register.getRegisterNumber() );      
    }     
    
    @Test
    public void testParseInstructionWithCompoundRegisterAndDisplacement() 
    {
        AST ast = parse("ADD r0,Y+42");
        Assert.assertNotNull(ast);
        Assert.assertTrue( ast.hasChildren() );
        Assert.assertEquals( 1 ,  ast.childCount() ); 
        
        final StatementNode stmt = (StatementNode) ast.child(0);
        Assert.assertNotNull(stmt);
        Assert.assertEquals( 1 , stmt.childCount() );
        
        final InstructionNode insn = (InstructionNode) stmt.child(0);
        Assert.assertEquals( 2 , insn.childCount() );        
        Assert.assertNotNull(insn);
        Assert.assertEquals( "add" , insn.instruction.getMnemonic());
        
        final RegisterNode num1 = (RegisterNode) insn.child( 0 );
        Assert.assertFalse( num1.register.isCompoundRegister() );
        Assert.assertEquals( 0 , num1.register.getRegisterNumber() );
        
        final RegisterNode num2 = (RegisterNode) insn.child( 1 );
        Assert.assertTrue( num2.register.isCompoundRegister() );
        Assert.assertFalse( num2.register.isPostIncrement() );
        Assert.assertFalse( num2.register.isPreDecrement() );
        Assert.assertEquals( Register.REG_Y , num2.register.getRegisterNumber() );
        
        Assert.assertEquals( 1 , num2.childCount() );
        Assert.assertTrue( num2.child(0) instanceof NumberLiteralNode);
        NumberLiteralNode num3 = (NumberLiteralNode) num2.child(0);
        Assert.assertEquals( 42 , num3.getValue().intValue()  );
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
        Assert.assertEquals( "add" , insn.instruction.getMnemonic());
        
        final RegisterNode num1 = (RegisterNode) insn.child( 0 );
        Assert.assertFalse( num1.register.isCompoundRegister() );
        Assert.assertEquals( 0 , num1.register.getRegisterNumber() );
        
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
        Assert.assertEquals( "add" , insn.instruction.getMnemonic());
        
        final NumberLiteralNode num = (NumberLiteralNode) insn.child( 0 );
        Assert.assertEquals( 0x123 , num.getValue().intValue()  );
        Assert.assertEquals( NumberLiteralNode.LiteralType.HEXADECIMAL , num.getType() );
    }    
    
    @Test
    public void testParseReserveBytes() 
    {
        AST ast = parse(".byte 10");
        Assert.assertNotNull(ast);
        Assert.assertTrue( ast.hasChildren() );
        Assert.assertEquals( 1 ,  ast.childCount() ); 
        
        final StatementNode stmt = (StatementNode) ast.child(0);
        Assert.assertNotNull(stmt);
        Assert.assertEquals( 1 , stmt.childCount() );
        
        final DirectiveNode node = (DirectiveNode) stmt.child(0);
        Assert.assertNotNull(node);
        Assert.assertEquals( 1 , node.childCount() );
        
        final NumberLiteralNode num = (NumberLiteralNode) node.child( 0 );
        Assert.assertEquals( 10 , num.getValue().intValue()  );
        Assert.assertEquals( NumberLiteralNode.LiteralType.DECIMAL , num.getType() );
    }
    
    @Test
    public void testParseDevice() 
    {
        AST ast = parse(".device ATMega88");
        Assert.assertNotNull(ast);
        Assert.assertTrue( ast.hasChildren() );
        Assert.assertEquals( 1 ,  ast.childCount() ); 
        
        final StatementNode stmt = (StatementNode) ast.child(0);
        Assert.assertNotNull(stmt);
        Assert.assertEquals( 1 , stmt.childCount() );
        
        final DirectiveNode node = (DirectiveNode) stmt.child(0);
        Assert.assertEquals( Directive.DEVICE , node.directive );
        Assert.assertNotNull(node);
        Assert.assertEquals( 1 , node.childCount() );
        
        final StringLiteral num = (StringLiteral) node.child( 0 );
        Assert.assertEquals( "ATMega88" , num.value );
    }    
    
    @Test
    public void testParseInitBytes() 
    {
        AST ast = parse(".db 1,2");
        Assert.assertNotNull(ast);
        Assert.assertTrue( ast.hasChildren() );
        Assert.assertEquals( 1 ,  ast.childCount() ); 
        
        final StatementNode stmt = (StatementNode) ast.child(0);
        Assert.assertNotNull(stmt);
        Assert.assertEquals( 1 , stmt.childCount() );
        
        final DirectiveNode node = (DirectiveNode) stmt.child(0);
        Assert.assertNotNull(node);
        Assert.assertEquals( 2 , node.childCount() );
        Assert.assertEquals( Directive.INIT_BYTES , node.directive );
        
        final NumberLiteralNode num = (NumberLiteralNode) node.child( 0 );
        Assert.assertEquals( 1 , num.getValue().intValue()  );
        Assert.assertEquals( NumberLiteralNode.LiteralType.DECIMAL , num.getType() );
        
        final NumberLiteralNode num2 = (NumberLiteralNode) node.child( 1 );
        Assert.assertEquals( 2 , num2.getValue().intValue()  );
        Assert.assertEquals( NumberLiteralNode.LiteralType.DECIMAL , num2.getType() );        
    }  
    
    @Test
    public void testParseInitWords() 
    {
        AST ast = parse(".dw 1,2");
        Assert.assertNotNull(ast);
        Assert.assertTrue( ast.hasChildren() );
        Assert.assertEquals( 1 ,  ast.childCount() ); 
        
        final StatementNode stmt = (StatementNode) ast.child(0);
        Assert.assertNotNull(stmt);
        Assert.assertEquals( 1 , stmt.childCount() );
        
        final DirectiveNode node = (DirectiveNode) stmt.child(0);
        Assert.assertNotNull(node);
        Assert.assertEquals( 2 , node.childCount() );
        Assert.assertEquals( Directive.INIT_WORDS , node.directive );
        
        final NumberLiteralNode num = (NumberLiteralNode) node.child( 0 );
        Assert.assertEquals( 1 , num.getValue().intValue()  );
        Assert.assertEquals( NumberLiteralNode.LiteralType.DECIMAL , num.getType() );
        
        final NumberLiteralNode num2 = (NumberLiteralNode) node.child( 1 );
        Assert.assertEquals( 2 , num2.getValue().intValue()  );
        Assert.assertEquals( NumberLiteralNode.LiteralType.DECIMAL , num2.getType() );        
    }      
    
    @Test
    public void testParseEQU() 
    {
        AST ast = parse(".equ A = 1");
        Assert.assertNotNull(ast);
        Assert.assertTrue( ast.hasChildren() );
        Assert.assertEquals( 1 ,  ast.childCount() ); 
        
        final StatementNode stmt = (StatementNode) ast.child(0);
        Assert.assertNotNull(stmt);
        Assert.assertEquals( 1 , stmt.childCount() );
        
        final DirectiveNode node = (DirectiveNode) stmt.child(0);
        Assert.assertEquals(Directive.EQU , node.directive );
        Assert.assertNotNull(node);
        Assert.assertEquals( 2 , node.childCount() );

        final EquLabelNode label = (EquLabelNode) node.child(0);
        final NumberLiteralNode num2 = (NumberLiteralNode) node.child(1);
        
        Assert.assertEquals( new Identifier("A") , label.name );
        Assert.assertEquals( 1 , num2.getValue().intValue() );
    }     
    
    @Test
    public void testParseEQUWithHexValue() 
    {
        AST ast = parse(".equ _SIGNATURE_000   = 0x1e");
        Assert.assertNotNull(ast);
        Assert.assertTrue( ast.hasChildren() );
        Assert.assertEquals( 1 ,  ast.childCount() ); 
        
        final StatementNode stmt = (StatementNode) ast.child(0);
        Assert.assertNotNull(stmt);
        Assert.assertEquals( 1 , stmt.childCount() );
        
        final DirectiveNode node = (DirectiveNode) stmt.child(0);
        Assert.assertEquals(Directive.EQU , node.directive );
        Assert.assertNotNull(node);
        Assert.assertEquals( 2 , node.childCount() );

        final EquLabelNode label = (EquLabelNode) node.child(0);
        final NumberLiteralNode num2 = (NumberLiteralNode) node.child(1);
        Assert.assertEquals( LiteralType.HEXADECIMAL , num2.getType() );
        Assert.assertEquals( new Identifier("_SIGNATURE_000") , label.name );
        Assert.assertEquals( 0x1e , num2.getValue().intValue() );
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
        Assert.assertEquals( "add" , insn.instruction.getMnemonic());
        
        final NumberLiteralNode num = (NumberLiteralNode) insn.child( 0 );
        Assert.assertEquals( 0b1011 , num.getValue().intValue()  );
        Assert.assertEquals( NumberLiteralNode.LiteralType.BINARY , num.getType() );
    }     
    
    @Test
    public void testParseCSEG() 
    {
        AST ast = parse(".cseg");
        Assert.assertNotNull(ast);
        Assert.assertTrue( ast.hasChildren() );
        Assert.assertEquals( 1 ,  ast.childCount() ); 
        
        final StatementNode stmt = (StatementNode) ast.child(0);
        Assert.assertNotNull(stmt);
        Assert.assertEquals( 1 , stmt.childCount() );
        
        final DirectiveNode insn = (DirectiveNode) stmt.child(0);
        Assert.assertNotNull(insn);
        Assert.assertEquals( Directive.CSEG , insn.directive );
    }    
    
    @Test
    public void testParseDSEG() 
    {
        AST ast = parse(".dseg");
        Assert.assertNotNull(ast);
        Assert.assertTrue( ast.hasChildren() );
        Assert.assertEquals( 1 ,  ast.childCount() ); 
        
        final StatementNode stmt = (StatementNode) ast.child(0);
        Assert.assertNotNull(stmt);
        Assert.assertEquals( 1 , stmt.childCount() );
        
        final DirectiveNode insn = (DirectiveNode) stmt.child(0);
        Assert.assertNotNull(insn);
        Assert.assertEquals( Directive.DSEG , insn.directive );
    }     
    
    @Test
    public void testParseStringLiteral1() 
    {
        AST ast = parse("lsl \"abc def 123 .,;.\"");
        Assert.assertNotNull(ast);
        Assert.assertTrue( ast.hasChildren() );
        Assert.assertEquals( 1 ,  ast.childCount() ); 
        
        final StatementNode stmt = (StatementNode) ast.child(0);
        Assert.assertNotNull(stmt);
        Assert.assertEquals( 1 , stmt.childCount() );
        
        final InstructionNode insn = (InstructionNode) stmt.child(0);
        Assert.assertEquals( 1 , insn.childCount() );        
        Assert.assertNotNull(insn);
        Assert.assertEquals( "lsl" , insn.instruction.getMnemonic());
        
        final StringLiteral num = (StringLiteral) insn.child( 0 );
        Assert.assertEquals( "abc def 123 .,;." , num.value);
    }   
    
    @Test
    public void testParseStringLiteralOnlyWhitespace() 
    {
        AST ast = parse("lsl \" \"");
        Assert.assertNotNull(ast);
        Assert.assertTrue( ast.hasChildren() );
        Assert.assertEquals( 1 ,  ast.childCount() ); 
        
        final StatementNode stmt = (StatementNode) ast.child(0);
        Assert.assertNotNull(stmt);
        Assert.assertEquals( 1 , stmt.childCount() );
        
        final InstructionNode insn = (InstructionNode) stmt.child(0);
        Assert.assertEquals( 1 , insn.childCount() );        
        Assert.assertNotNull(insn);
        Assert.assertEquals( "lsl" , insn.instruction.getMnemonic());
        
        final StringLiteral num = (StringLiteral) insn.child( 0 );
        Assert.assertEquals( " " , num.value);
    }      
    
    @Test
    public void testParseCharLiteral() 
    {
        AST ast = parse("lsl 'x'");
        Assert.assertNotNull(ast);
        Assert.assertTrue( ast.hasChildren() );
        Assert.assertEquals( 1 ,  ast.childCount() ); 
        
        final StatementNode stmt = (StatementNode) ast.child(0);
        Assert.assertNotNull(stmt);
        Assert.assertEquals( 1 , stmt.childCount() );
        
        final InstructionNode insn = (InstructionNode) stmt.child(0);
        Assert.assertEquals( 1 , insn.childCount() );        
        Assert.assertNotNull(insn);
        Assert.assertEquals( "lsl" , insn.instruction.getMnemonic());
        
        final CharacterLiteralNode num = (CharacterLiteralNode) insn.child( 0 );
        Assert.assertEquals( "Got : >"+num.value+"<" , 'x' , num.value);
    }     
    
    @Test
    public void testParseCharLiteralWhitespace() 
    {
        AST ast = parse("lsl ' '");
        Assert.assertNotNull(ast);
        Assert.assertTrue( ast.hasChildren() );
        Assert.assertEquals( 1 ,  ast.childCount() ); 
        
        final StatementNode stmt = (StatementNode) ast.child(0);
        Assert.assertNotNull(stmt);
        Assert.assertEquals( 1 , stmt.childCount() );
        
        final InstructionNode insn = (InstructionNode) stmt.child(0);
        Assert.assertEquals( 1 , insn.childCount() );        
        Assert.assertNotNull(insn);
        Assert.assertEquals( "lsl" , insn.instruction.getMnemonic());
        
        final CharacterLiteralNode num = (CharacterLiteralNode) insn.child( 0 );
        Assert.assertEquals( "Got : >"+num.value+"<" , ' ' , num.value);
    }     
    
    @Test
    public void testParseESEG() 
    {
        AST ast = parse(".eseg");
        Assert.assertNotNull(ast);
        Assert.assertTrue( ast.hasChildren() );
        Assert.assertEquals( 1 ,  ast.childCount() ); 
        
        final StatementNode stmt = (StatementNode) ast.child(0);
        Assert.assertNotNull(stmt);
        Assert.assertEquals( 1 , stmt.childCount() );
        
        final DirectiveNode insn = (DirectiveNode) stmt.child(0);
        Assert.assertNotNull(insn);
        Assert.assertEquals( Directive.ESEG , insn.directive );
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
