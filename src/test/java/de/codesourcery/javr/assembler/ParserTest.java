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

import static org.junit.Assert.*;

import org.junit.Test;

import de.codesourcery.javr.assembler.arch.IArchitecture;
import de.codesourcery.javr.assembler.arch.impl.ATMega88;
import de.codesourcery.javr.assembler.parser.Identifier;
import de.codesourcery.javr.assembler.parser.LexerImpl;
import de.codesourcery.javr.assembler.parser.OperatorType;
import de.codesourcery.javr.assembler.parser.Parser;
import de.codesourcery.javr.assembler.parser.Scanner;
import de.codesourcery.javr.assembler.parser.ast.AST;
import de.codesourcery.javr.assembler.parser.ast.CharacterLiteralNode;
import de.codesourcery.javr.assembler.parser.ast.CommentNode;
import de.codesourcery.javr.assembler.parser.ast.CurrentAddressNode;
import de.codesourcery.javr.assembler.parser.ast.DirectiveNode;
import de.codesourcery.javr.assembler.parser.ast.DirectiveNode.Directive;
import de.codesourcery.javr.assembler.parser.ast.EquLabelNode;
import de.codesourcery.javr.assembler.parser.ast.FunctionCallNode;
import de.codesourcery.javr.assembler.parser.ast.FunctionDefinitionNode;
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
import de.codesourcery.javr.assembler.util.StringResource;

public class ParserTest 
{
    private final IArchitecture arch = new ATMega88();
    // single-line tests
    
    @Test
    public void testEmptyFile() 
    {
        AST ast = parse("");
        assertNotNull(ast);
        assertFalse( ast.hasChildren() );
        assertEquals( 0 ,  ast.childCount() );        
    }

    @Test
    public void testParseBlankLine() 
    {
        AST ast = parse("\n");
        assertNotNull(ast);
        assertFalse( ast.hasChildren() );
        assertEquals( 0 ,  ast.childCount() );
    } 
    
    @Test
    public void testParseIfDef() 
    {
        AST ast = parse("#ifdef test");
        assertNotNull(ast);
        assertTrue( ast.hasChildren() );
        assertEquals( 1 ,  ast.childCount() );
        
        final StatementNode stmt = (StatementNode) ast.child(0);
        assertNotNull(stmt);
        assertEquals( 1 , stmt.childCount() );
        
        final PreprocessorNode ins = (PreprocessorNode) stmt.child(0);
        assertNotNull(ins);
        assertEquals( Preprocessor.IF_DEFINE , ins.type );
        assertEquals( 0 , ins.arguments.size() );
        assertEquals( 1 , ins.childCount() );
        
        FunctionCallNode fn = (FunctionCallNode) ins.firstChild();
        assertEquals( FunctionCallNode.BUILDIN_FUNCTION_DEFINED , fn.functionName );
        assertEquals(1 , fn.childCount() );
        IdentifierNode in = (IdentifierNode) fn.firstChild();
        assertEquals( Identifier.of("test") ,in.name );
    }
    
    @Test
    public void testParseIfnDef() 
    {
        AST ast = parse("#ifndef test");
        assertNotNull(ast);
        assertTrue( ast.hasChildren() );
        assertEquals( 1 ,  ast.childCount() );
        
        final StatementNode stmt = (StatementNode) ast.child(0);
        assertNotNull(stmt);
        assertEquals( 1 , stmt.childCount() );
        
        final PreprocessorNode ins = (PreprocessorNode) stmt.child(0);
        assertNotNull(ins);
        assertEquals( Preprocessor.IF_NDEFINE , ins.type );
        assertEquals( 0 , ins.arguments.size() );
        assertEquals( 1 , ins.childCount() );
        
        final OperatorNode op = (OperatorNode) ins.firstChild();
        assertEquals(OperatorType.LOGICAL_NOT , op.type );
        assertEquals(1 , op.childCount() );
        
        final FunctionCallNode fn = (FunctionCallNode) op.firstChild();
        assertEquals( FunctionCallNode.BUILDIN_FUNCTION_DEFINED , fn.functionName );
        assertEquals(1 , fn.childCount() );
        IdentifierNode in = (IdentifierNode) fn.firstChild();
        assertEquals( Identifier.of("test") ,in.name );
    }    
    
    @Test
    public void testParseDefine1() 
    {
        AST ast = parse("#define test");
        assertNotNull(ast);
        assertTrue( ast.hasChildren() );
        assertEquals( 1 ,  ast.childCount() );
        
        final StatementNode stmt = (StatementNode) ast.child(0);
        assertNotNull(stmt);
        assertEquals( 1 , stmt.childCount() );
        
        final PreprocessorNode ins = (PreprocessorNode) stmt.child(0);
        assertNotNull(ins);
        assertEquals( Preprocessor.DEFINE , ins.type );        
        assertEquals( 1 , ins.childCount() );
        
        final FunctionDefinitionNode in = (FunctionDefinitionNode) ins.child(0);
        assertEquals( 0 , in.getArgumentCount() );
        assertEquals( new Identifier("test") , in.name );
    }
    
    @Test
    public void testParseErrorMessage() 
    {
        AST ast = parse("#error test");
        assertNotNull(ast);
        assertTrue( ast.hasChildren() );
        assertEquals( 1 ,  ast.childCount() );
        
        final StatementNode stmt = (StatementNode) ast.child(0);
        assertNotNull(stmt);
        assertEquals( 1 , stmt.childCount() );
        
        final PreprocessorNode ins = (PreprocessorNode) stmt.child(0);
        assertEquals( Preprocessor.ERROR , ins.type );
        assertNotNull(ins);
        assertEquals( 0 , ins.childCount() );
        
        assertEquals( 1 , ins.arguments.size() );
        assertEquals( "test" , ins.arguments.get(0));
    }    
    
    @Test
    public void testParseWarningMessage() 
    {
        AST ast = parse("#warning test");
        assertNotNull(ast);
        assertTrue( ast.hasChildren() );
        assertEquals( 1 ,  ast.childCount() );
        
        final StatementNode stmt = (StatementNode) ast.child(0);
        assertNotNull(stmt);
        assertEquals( 1 , stmt.childCount() );
        
        final PreprocessorNode ins = (PreprocessorNode) stmt.child(0);
        assertNotNull(ins);
        assertEquals( Preprocessor.WARNING , ins.type );
        assertEquals( 0 , ins.childCount() );
        
        assertEquals( 1 , ins.arguments.size() );
        assertEquals( "test" , ins.arguments.get(0));
    }    
    
    @Test
    public void testParseInfoMessage() 
    {
        AST ast = parse("#message test");
        assertNotNull(ast);
        assertTrue( ast.hasChildren() );
        assertEquals( 1 ,  ast.childCount() );
        
        final StatementNode stmt = (StatementNode) ast.child(0);
        assertNotNull(stmt);
        assertEquals( 1 , stmt.childCount() );
        
        final PreprocessorNode ins = (PreprocessorNode) stmt.child(0);
        assertNotNull(ins);
        assertEquals( Preprocessor.MESSAGE , ins.type );
        assertEquals( 0 , ins.childCount() );
        
        assertEquals( 1 , ins.arguments.size() );
        assertEquals( "test" , ins.arguments.get(0));
    }     
    
    @Test
    public void testParseDefine2() 
    {
        AST ast = parse("#define test test2");
        assertNotNull(ast);
        assertTrue( ast.hasChildren() );
        assertEquals( 1 ,  ast.childCount() );
        
        final StatementNode stmt = (StatementNode) ast.child(0);
        assertNotNull(stmt);
        assertEquals( 1 , stmt.childCount() );
        
        final PreprocessorNode ins = (PreprocessorNode) stmt.child(0);
        assertNotNull(ins);
        assertEquals( Preprocessor.DEFINE , ins.type );
        assertEquals( 0 , ins.arguments.size() );
        
        final FunctionDefinitionNode funcDef = (FunctionDefinitionNode) ins.child(0);
        assertNotNull(funcDef);
        assertEquals( new Identifier("test") , funcDef.name );
        assertEquals( 0 , funcDef.getArgumentCount() );
        
        final IdentifierNode in = (IdentifierNode) funcDef.child(1);
        assertEquals( new Identifier("test2") , in.name );
    }    
    
    @Test
    public void testParseEndIf() 
    {
        AST ast = parse("#endif");
        assertNotNull(ast);
        assertTrue( ast.hasChildren() );
        assertEquals( 1 ,  ast.childCount() );
        
        final StatementNode stmt = (StatementNode) ast.child(0);
        assertNotNull(stmt);
        assertEquals( 1 , stmt.childCount() );
        
        final PreprocessorNode ins = (PreprocessorNode) stmt.child(0);
        assertNotNull(ins);
        assertEquals( Preprocessor.ENDIF , ins.type );
        assertEquals( 0 , ins.arguments.size() );
    }    
    
    @Test
    public void testParsePragma() 
    {
        AST ast = parse("#pragma a b c");
        assertNotNull(ast);
        assertTrue( ast.hasChildren() );
        assertEquals( 1 ,  ast.childCount() );
        
        final StatementNode stmt = (StatementNode) ast.child(0);
        assertNotNull(stmt);
        assertEquals( 1 , stmt.childCount() );
        
        final PreprocessorNode ins = (PreprocessorNode) stmt.child(0);
        assertNotNull(ins);
        assertEquals( Preprocessor.PRAGMA , ins.type );
        assertEquals( 3 , ins.arguments.size() );
        assertEquals( "a" , ins.arguments.get(0) );
        assertEquals( "b" , ins.arguments.get(1) );
        assertEquals( "c" , ins.arguments.get(2) );
    }    
    
    @Test
    public void testParseASMCommentLine() 
    {
        AST ast = parse("; test comment");
        assertNotNull(ast);
        assertTrue( ast.hasChildren() );
        assertEquals( 1 ,  ast.childCount() );
        
        final StatementNode stmt = (StatementNode) ast.child(0);
        assertNotNull(stmt);
        assertEquals( 1 , stmt.childCount() );
        
        final CommentNode comment = (CommentNode) stmt.child(0);
        assertNotNull(comment);
        assertEquals("; test comment" , comment.value );
    }
    
    @Test
    public void testParseCStyleCommentLine() 
    {
        AST ast = parse("// test comment");
        assertNotNull(ast);
        assertTrue( ast.hasChildren() );
        assertEquals( 1 ,  ast.childCount() );
        
        final StatementNode stmt = (StatementNode) ast.child(0);
        assertNotNull(stmt);
        assertEquals( 1 , stmt.childCount() );
        
        final CommentNode comment = (CommentNode) stmt.child(0);
        assertNotNull(comment);
        assertEquals("// test comment" , comment.value );
    }     
    
    @Test
    public void testParseCStyleMultiLineComment1() 
    {
        AST ast = parse("/* test * comment */");
        assertNotNull(ast);
        assertTrue( ast.hasChildren() );
        assertEquals( 1 ,  ast.childCount() );
        
        final StatementNode stmt = (StatementNode) ast.child(0);
        assertNotNull(stmt);
        assertEquals( 1 , stmt.childCount() );
        
        final CommentNode comment = (CommentNode) stmt.child(0);
        assertNotNull(comment);
        assertEquals("/* test * comment */" , comment.value );
    }     
    
    @Test
    public void testParseCStyleMultiLineComment2() 
    {
        AST ast = parse("/* test \ncomment */");
        assertNotNull(ast);
        assertTrue( ast.hasChildren() );
        assertEquals( 1 ,  ast.childCount() );
        
        final StatementNode stmt = (StatementNode) ast.child(0);
        assertNotNull(stmt);
        assertEquals( 1 , stmt.childCount() );
        
        final CommentNode comment = (CommentNode) stmt.child(0);
        assertNotNull(comment);
        assertEquals("/* test \ncomment */" , comment.value );
    }     
    
    @Test
    public void testParseLabelLine() 
    {
        AST ast = parse("label:");
        assertNotNull(ast);
        assertTrue( ast.hasChildren() );
        assertEquals( 1 ,  ast.childCount() );
        
        final StatementNode stmt = (StatementNode) ast.child(0);
        assertNotNull(stmt);
        assertEquals( 1 , stmt.childCount() );
        
        final LabelNode label = (LabelNode) stmt.child(0);
        assertNotNull(label);
        assertEquals( new Identifier("label") , label.identifier );
    }    
    
    @Test
    public void testParseLabelLineWithComment() 
    {
        AST ast = parse("label: ; test comment");
        assertNotNull(ast);
        assertTrue( ast.hasChildren() );
        assertEquals( 1 ,  ast.childCount() ); 
        
        final StatementNode stmt = (StatementNode) ast.child(0);
        assertNotNull(stmt);
        assertEquals( 2 , stmt.childCount() );
        
        final LabelNode label = (LabelNode) stmt.child(0);
        assertNotNull(label);
        assertEquals( new Identifier("label") , label.identifier );
        
        final CommentNode comment = (CommentNode) stmt.child(1);
        assertNotNull(comment);
        assertEquals("; test comment" , comment.value );        
    }     
    
    @Test
    public void testParseInstructionLineWithNoOperands() 
    {
        AST ast = parse("add");
        assertNotNull(ast);
        assertTrue( ast.hasChildren() );
        assertEquals( 1 ,  ast.childCount() ); 
        
        final StatementNode stmt = (StatementNode) ast.child(0);
        assertNotNull(stmt);
        assertEquals( 1 , stmt.childCount() );
        
        final InstructionNode insn = (InstructionNode) stmt.child(0);
        assertEquals( 0 , insn.childCount() );
        assertNotNull(insn);
        assertEquals( "add" , insn.instruction.getMnemonic());
    }    
    
    @Test
    public void testParseInstructionWithOneDecimalNumberOperand() 
    {
        AST ast = parse("add 123");
        assertNotNull(ast);
        assertTrue( ast.hasChildren() );
        assertEquals( 1 ,  ast.childCount() ); 
        
        final StatementNode stmt = (StatementNode) ast.child(0);
        assertNotNull(stmt);
        assertEquals( 1 , stmt.childCount() );
        
        final InstructionNode insn = (InstructionNode) stmt.child(0);
        assertEquals( 1 , insn.childCount() );        
        assertNotNull(insn);
        assertEquals( "add" , insn.instruction.getMnemonic());
        
        final NumberLiteralNode num = (NumberLiteralNode) insn.child( 0 );
        assertEquals( 123 , num.getValue().intValue() );
        assertEquals( NumberLiteralNode.LiteralType.DECIMAL , num.getType() );
    }    
    
    @Test
    public void testParseInstructionWithTwoDecimalNumberOperands() 
    {
        AST ast = parse("add 123 , 456");
        assertNotNull(ast);
        assertTrue( ast.hasChildren() );
        assertEquals( 1 ,  ast.childCount() ); 
        
        final StatementNode stmt = (StatementNode) ast.child(0);
        assertNotNull(stmt);
        assertEquals( 1 , stmt.childCount() );
        
        final InstructionNode insn = (InstructionNode) stmt.child(0);
        assertEquals( 2 , insn.childCount() );        
        assertNotNull(insn);
        assertEquals( "add" , insn.instruction.getMnemonic());
        
        final NumberLiteralNode num1 = (NumberLiteralNode) insn.child( 0 );
        assertEquals( 123 , num1.getValue().intValue() );
        assertEquals( NumberLiteralNode.LiteralType.DECIMAL , num1.getType() );
        
        final NumberLiteralNode num2 = (NumberLiteralNode) insn.child( 1 );
        assertEquals( 456 , num2.getValue().intValue()  );
        assertEquals( NumberLiteralNode.LiteralType.DECIMAL , num2.getType() );        
    }  
    
    @Test
    public void testParseInstructionWithCurrentAddress1() 
    {
        AST ast = parse("rjmp .+2");
        assertNotNull(ast);
        assertTrue( ast.hasChildren() );
        assertEquals( 1 ,  ast.childCount() ); 
        
        final StatementNode stmt = (StatementNode) ast.child(0);
        assertNotNull(stmt);
        assertEquals( 1 , stmt.childCount() );
        
        final InstructionNode insn = (InstructionNode) stmt.child(0);
        assertEquals( 1 , insn.childCount() );        
        assertNotNull(insn);
        assertEquals( "rjmp" , insn.instruction.getMnemonic());
        
        final OperatorNode op = (OperatorNode) insn.child( 0 );
        assertEquals( 2 , op.childCount() );     
        assertEquals( OperatorType.PLUS , op.type );
        
        assertEquals( CurrentAddressNode.class , op.child(0).getClass() );
        assertEquals( NumberLiteralNode.class , op.child(1).getClass() );
        
        assertEquals( 2 , ((NumberLiteralNode) op.child(1)).getValue().intValue() );
    }   
    
    @Test
    public void testParseInstructionWithCurrentAddress2() 
    {
        AST ast = parse("rjmp .-2");
        assertNotNull(ast);
        assertTrue( ast.hasChildren() );
        assertEquals( 1 ,  ast.childCount() ); 
        
        final StatementNode stmt = (StatementNode) ast.child(0);
        assertNotNull(stmt);
        assertEquals( 1 , stmt.childCount() );
        
        final InstructionNode insn = (InstructionNode) stmt.child(0);
        assertEquals( 1 , insn.childCount() );        
        assertNotNull(insn);
        assertEquals( "rjmp" , insn.instruction.getMnemonic());
        
        final OperatorNode op = (OperatorNode) insn.child( 0 );
        assertEquals( 2 , op.childCount() );     
        assertEquals( OperatorType.BINARY_MINUS , op.type );
        
        assertEquals( CurrentAddressNode.class , op.child(0).getClass() );
        assertEquals( NumberLiteralNode.class , op.child(1).getClass() );
        
        assertEquals( 2 , ((NumberLiteralNode) op.child(1)).getValue().intValue() );
    }     
    
    @Test
    public void testParseInstructionWithTwoRegisterOperands() 
    {
        AST ast = parse("add r0,r1");
        assertNotNull(ast);
        assertTrue( ast.hasChildren() );
        assertEquals( 1 ,  ast.childCount() ); 
        
        final StatementNode stmt = (StatementNode) ast.child(0);
        assertNotNull(stmt);
        assertEquals( 1 , stmt.childCount() );
        
        final InstructionNode insn = (InstructionNode) stmt.child(0);
        assertEquals( 2 , insn.childCount() );        
        assertNotNull(insn);
        assertEquals( "add" , insn.instruction.getMnemonic());
        
        final RegisterNode num1 = (RegisterNode) insn.child( 0 );
        assertFalse( num1.register.isCompoundRegister() );
        assertEquals( 0 , num1.register.getRegisterNumber() );
        
        final RegisterNode num2 = (RegisterNode) insn.child( 1 );
        assertFalse( num2.register.isCompoundRegister() );
        assertEquals( 1 , num2.register.getRegisterNumber() );      
    }     
    
    @Test
    public void testParseInstructionWithPostIncrement() 
    {
        AST ast = parse("add r0,Z+");
        assertNotNull(ast);
        assertTrue( ast.hasChildren() );
        assertEquals( 1 ,  ast.childCount() ); 
        
        final StatementNode stmt = (StatementNode) ast.child(0);
        assertNotNull(stmt);
        assertEquals( 1 , stmt.childCount() );
        
        final InstructionNode insn = (InstructionNode) stmt.child(0);
        assertEquals( 2 , insn.childCount() );        
        assertNotNull(insn);
        assertEquals( "add" , insn.instruction.getMnemonic());
        
        final RegisterNode num1 = (RegisterNode) insn.child( 0 );
        assertFalse( num1.register.isCompoundRegister() );
        assertEquals( 0 , num1.register.getRegisterNumber() );
        
        final RegisterNode num2 = (RegisterNode) insn.child( 1 );
        assertTrue( num2.register.isCompoundRegister() );
        assertTrue( num2.register.isPostIncrement() );
        assertFalse( num2.register.isPreDecrement() );
        assertEquals( Register.REG_Z , num2.register.getRegisterNumber() );      
    }    
    
    @Test
    public void testParseInstructionWithPreDecrement() 
    {
        AST ast = parse("add r0,-X");
        assertNotNull(ast);
        assertTrue( ast.hasChildren() );
        assertEquals( 1 ,  ast.childCount() ); 
        
        final StatementNode stmt = (StatementNode) ast.child(0);
        assertNotNull(stmt);
        assertEquals( 1 , stmt.childCount() );
        
        final InstructionNode insn = (InstructionNode) stmt.child(0);
        assertEquals( 2 , insn.childCount() );        
        assertNotNull(insn);
        assertEquals( "add" , insn.instruction.getMnemonic());
        
        final RegisterNode num1 = (RegisterNode) insn.child( 0 );
        assertFalse( num1.register.isCompoundRegister() );
        assertEquals( 0 , num1.register.getRegisterNumber() );
        
        final RegisterNode num2 = (RegisterNode) insn.child( 1 );
        assertTrue( num2.register.isCompoundRegister() );
        assertFalse( num2.register.isPostIncrement() );
        assertTrue( num2.register.isPreDecrement() );
        assertEquals( Register.REG_X , num2.register.getRegisterNumber() );      
    }      
    
    @Test
    public void testParseInstructionWithExpression() 
    {
        AST ast = parse("add r0,1+2");
        assertNotNull(ast);
        assertTrue( ast.hasChildren() );
        assertEquals( 1 ,  ast.childCount() ); 
        
        final StatementNode stmt = (StatementNode) ast.child(0);
        assertNotNull(stmt);
        assertEquals( 1 , stmt.childCount() );
        
        final InstructionNode insn = (InstructionNode) stmt.child(0);
        assertEquals( 2 , insn.childCount() );        
        assertNotNull(insn);
        assertEquals( "add" , insn.instruction.getMnemonic());
        
        final RegisterNode num1 = (RegisterNode) insn.child( 0 );
        assertFalse( num1.register.isCompoundRegister() );
        assertEquals( 0 , num1.register.getRegisterNumber() );
        
        final OperatorNode num2 = (OperatorNode) insn.child( 1 );
        assertEquals( OperatorType.PLUS , num2.type );
        assertEquals( 2 , num2.childCount() );
        assertEquals( 1 , ((NumberLiteralNode) num2.child(0)).getValue().intValue() );
        assertEquals( 2 , ((NumberLiteralNode) num2.child(1)).getValue().intValue() );
    }  

    @Test
    public void testParseInstructionWithFunction() 
    {
        AST ast = parse("add r0,HIGH( 1,2 )");
        assertNotNull(ast);
        assertTrue( ast.hasChildren() );
        assertEquals( 1 ,  ast.childCount() ); 
        
        final StatementNode stmt = (StatementNode) ast.child(0);
        assertNotNull(stmt);
        assertEquals( 1 , stmt.childCount() );
        
        final InstructionNode insn = (InstructionNode) stmt.child(0);
        assertEquals( 2 , insn.childCount() );        
        assertNotNull(insn);
        assertEquals( "add" , insn.instruction.getMnemonic());
        
        final RegisterNode num1 = (RegisterNode) insn.child( 0 );
        assertFalse( num1.register.isCompoundRegister() );
        assertEquals( 0 , num1.register.getRegisterNumber() );
        
        final FunctionCallNode num2 = (FunctionCallNode) insn.child( 1 );
        assertEquals( new Identifier("HIGH") , num2.functionName );
        assertEquals( 2 , num2.childCount() );
        assertEquals( 1 , ((NumberLiteralNode) num2.child(0)).getValue().intValue() );
        assertEquals( 2 , ((NumberLiteralNode) num2.child(1)).getValue().intValue() );
    }    
    
    @Test
    public void testParseInstructionWithCompoundRegister() 
    {
        AST ast = parse("add r0,r4:r3");
        assertNotNull(ast);
        assertTrue( ast.hasChildren() );
        assertEquals( 1 ,  ast.childCount() ); 
        
        final StatementNode stmt = (StatementNode) ast.child(0);
        assertNotNull(stmt);
        assertEquals( 1 , stmt.childCount() );
        
        final InstructionNode insn = (InstructionNode) stmt.child(0);
        assertEquals( 2 , insn.childCount() );        
        assertNotNull(insn);
        assertEquals( "add" , insn.instruction.getMnemonic());
        
        final RegisterNode num1 = (RegisterNode) insn.child( 0 );
        assertFalse( num1.register.isCompoundRegister() );
        assertEquals( 0 , num1.register.getRegisterNumber() );
        
        final RegisterNode num2 = (RegisterNode) insn.child( 1 );
        assertTrue( num2.register.isCompoundRegister() );
        assertFalse( num2.register.isPostIncrement() );
        assertFalse( num2.register.isPreDecrement() );
        assertEquals( 3 , num2.register.getRegisterNumber() );      
    }     
    
    @Test
    public void testParseInstructionWithCompoundRegisterAndDisplacement() 
    {
        AST ast = parse("ADD r0,Y+42");
        assertNotNull(ast);
        assertTrue( ast.hasChildren() );
        assertEquals( 1 ,  ast.childCount() ); 
        
        final StatementNode stmt = (StatementNode) ast.child(0);
        assertNotNull(stmt);
        assertEquals( 1 , stmt.childCount() );
        
        final InstructionNode insn = (InstructionNode) stmt.child(0);
        assertEquals( 2 , insn.childCount() );        
        assertNotNull(insn);
        assertEquals( "add" , insn.instruction.getMnemonic());
        
        final RegisterNode num1 = (RegisterNode) insn.child( 0 );
        assertFalse( num1.register.isCompoundRegister() );
        assertEquals( 0 , num1.register.getRegisterNumber() );
        
        final RegisterNode num2 = (RegisterNode) insn.child( 1 );
        assertTrue( num2.register.isCompoundRegister() );
        assertFalse( num2.register.isPostIncrement() );
        assertFalse( num2.register.isPreDecrement() );
        assertEquals( Register.REG_Y , num2.register.getRegisterNumber() );
        
        assertEquals( 1 , num2.childCount() );
        assertTrue( num2.child(0) instanceof NumberLiteralNode);
        NumberLiteralNode num3 = (NumberLiteralNode) num2.child(0);
        assertEquals( 42 , num3.getValue().intValue()  );
    }       
    
    @Test
    public void testParseInstructionWithRegisterAndIdentifierOperands() 
    {
        AST ast = parse("add r0,counter");
        assertNotNull(ast);
        assertTrue( ast.hasChildren() );
        assertEquals( 1 ,  ast.childCount() ); 
        
        final StatementNode stmt = (StatementNode) ast.child(0);
        assertNotNull(stmt);
        assertEquals( 1 , stmt.childCount() );
        
        final InstructionNode insn = (InstructionNode) stmt.child(0);
        assertEquals( 2 , insn.childCount() );        
        assertNotNull(insn);
        assertEquals( "add" , insn.instruction.getMnemonic());
        
        final RegisterNode num1 = (RegisterNode) insn.child( 0 );
        assertFalse( num1.register.isCompoundRegister() );
        assertEquals( 0 , num1.register.getRegisterNumber() );
        
        final IdentifierNode num2 = (IdentifierNode) insn.child( 1 );
        assertEquals( new Identifier( "counter" ) , num2.name);        
    }     
    
    @Test
    public void testParseInstructionWithOneHexadecimalNumberOperand() 
    {
        AST ast = parse("add 0x123");
        assertNotNull(ast);
        assertTrue( ast.hasChildren() );
        assertEquals( 1 ,  ast.childCount() ); 
        
        final StatementNode stmt = (StatementNode) ast.child(0);
        assertNotNull(stmt);
        assertEquals( 1 , stmt.childCount() );
        
        final InstructionNode insn = (InstructionNode) stmt.child(0);
        assertEquals( 1 , insn.childCount() );        
        assertNotNull(insn);
        assertEquals( "add" , insn.instruction.getMnemonic());
        
        final NumberLiteralNode num = (NumberLiteralNode) insn.child( 0 );
        assertEquals( 0x123 , num.getValue().intValue()  );
        assertEquals( NumberLiteralNode.LiteralType.HEXADECIMAL , num.getType() );
    }    
    
    @Test
    public void testParseReserveBytes() 
    {
        AST ast = parse(".byte 10");
        assertNotNull(ast);
        assertTrue( ast.hasChildren() );
        assertEquals( 1 ,  ast.childCount() ); 
        
        final StatementNode stmt = (StatementNode) ast.child(0);
        assertNotNull(stmt);
        assertEquals( 1 , stmt.childCount() );
        
        final DirectiveNode node = (DirectiveNode) stmt.child(0);
        assertNotNull(node);
        assertEquals( 1 , node.childCount() );
        
        final NumberLiteralNode num = (NumberLiteralNode) node.child( 0 );
        assertEquals( 10 , num.getValue().intValue()  );
        assertEquals( NumberLiteralNode.LiteralType.DECIMAL , num.getType() );
    }
    
    @Test
    public void testParseDevice() 
    {
        AST ast = parse(".device ATMega88");
        assertNotNull(ast);
        assertTrue( ast.hasChildren() );
        assertEquals( 1 ,  ast.childCount() ); 
        
        final StatementNode stmt = (StatementNode) ast.child(0);
        assertNotNull(stmt);
        assertEquals( 1 , stmt.childCount() );
        
        final DirectiveNode node = (DirectiveNode) stmt.child(0);
        assertEquals( Directive.DEVICE , node.directive );
        assertNotNull(node);
        assertEquals( 1 , node.childCount() );
        
        final StringLiteral num = (StringLiteral) node.child( 0 );
        assertEquals( "ATMega88" , num.value );
    }    
    
    @Test
    public void testParseInitBytes() 
    {
        AST ast = parse(".db 1,2");
        assertNotNull(ast);
        assertTrue( ast.hasChildren() );
        assertEquals( 1 ,  ast.childCount() ); 
        
        final StatementNode stmt = (StatementNode) ast.child(0);
        assertNotNull(stmt);
        assertEquals( 1 , stmt.childCount() );
        
        final DirectiveNode node = (DirectiveNode) stmt.child(0);
        assertNotNull(node);
        assertEquals( 2 , node.childCount() );
        assertEquals( Directive.INIT_BYTES , node.directive );
        
        final NumberLiteralNode num = (NumberLiteralNode) node.child( 0 );
        assertEquals( 1 , num.getValue().intValue()  );
        assertEquals( NumberLiteralNode.LiteralType.DECIMAL , num.getType() );
        
        final NumberLiteralNode num2 = (NumberLiteralNode) node.child( 1 );
        assertEquals( 2 , num2.getValue().intValue()  );
        assertEquals( NumberLiteralNode.LiteralType.DECIMAL , num2.getType() );        
    }  
    
    @Test
    public void testParseInitWords() 
    {
        AST ast = parse(".dw 1,2");
        assertNotNull(ast);
        assertTrue( ast.hasChildren() );
        assertEquals( 1 ,  ast.childCount() ); 
        
        final StatementNode stmt = (StatementNode) ast.child(0);
        assertNotNull(stmt);
        assertEquals( 1 , stmt.childCount() );
        
        final DirectiveNode node = (DirectiveNode) stmt.child(0);
        assertNotNull(node);
        assertEquals( 2 , node.childCount() );
        assertEquals( Directive.INIT_WORDS , node.directive );
        
        final NumberLiteralNode num = (NumberLiteralNode) node.child( 0 );
        assertEquals( 1 , num.getValue().intValue()  );
        assertEquals( NumberLiteralNode.LiteralType.DECIMAL , num.getType() );
        
        final NumberLiteralNode num2 = (NumberLiteralNode) node.child( 1 );
        assertEquals( 2 , num2.getValue().intValue()  );
        assertEquals( NumberLiteralNode.LiteralType.DECIMAL , num2.getType() );        
    }      
    
    @Test
    public void testParseEQU() 
    {
        AST ast = parse(".equ A = 1");
        assertNotNull(ast);
        assertTrue( ast.hasChildren() );
        assertEquals( 1 ,  ast.childCount() ); 
        
        final StatementNode stmt = (StatementNode) ast.child(0);
        assertNotNull(stmt);
        assertEquals( 1 , stmt.childCount() );
        
        final DirectiveNode node = (DirectiveNode) stmt.child(0);
        assertEquals(Directive.EQU , node.directive );
        assertNotNull(node);
        assertEquals( 2 , node.childCount() );

        final EquLabelNode label = (EquLabelNode) node.child(0);
        final NumberLiteralNode num2 = (NumberLiteralNode) node.child(1);
        
        assertEquals( new Identifier("A") , label.name );
        assertEquals( 1 , num2.getValue().intValue() );
    }     
    
    @Test
    public void testParseEQUWithHexValue() 
    {
        AST ast = parse(".equ _SIGNATURE_000   = 0x1e");
        assertNotNull(ast);
        assertTrue( ast.hasChildren() );
        assertEquals( 1 ,  ast.childCount() ); 
        
        final StatementNode stmt = (StatementNode) ast.child(0);
        assertNotNull(stmt);
        assertEquals( 1 , stmt.childCount() );
        
        final DirectiveNode node = (DirectiveNode) stmt.child(0);
        assertEquals(Directive.EQU , node.directive );
        assertNotNull(node);
        assertEquals( 2 , node.childCount() );

        final EquLabelNode label = (EquLabelNode) node.child(0);
        final NumberLiteralNode num2 = (NumberLiteralNode) node.child(1);
        assertEquals( LiteralType.HEXADECIMAL , num2.getType() );
        assertEquals( new Identifier("_SIGNATURE_000") , label.name );
        assertEquals( 0x1e , num2.getValue().intValue() );
    }       
    
    @Test
    public void testParseInstructionWithOneBinaryNumberOperand() 
    {
        AST ast = parse("add %1011");
        assertNotNull(ast);
        assertTrue( ast.hasChildren() );
        assertEquals( 1 ,  ast.childCount() ); 
        
        final StatementNode stmt = (StatementNode) ast.child(0);
        assertNotNull(stmt);
        assertEquals( 1 , stmt.childCount() );
        
        final InstructionNode insn = (InstructionNode) stmt.child(0);
        assertEquals( 1 , insn.childCount() );        
        assertNotNull(insn);
        assertEquals( "add" , insn.instruction.getMnemonic());
        
        final NumberLiteralNode num = (NumberLiteralNode) insn.child( 0 );
        assertEquals( 0b1011 , num.getValue().intValue()  );
        assertEquals( NumberLiteralNode.LiteralType.BINARY , num.getType() );
    }     
    
    @Test
    public void testParseCSEG() 
    {
        AST ast = parse(".cseg");
        assertNotNull(ast);
        assertTrue( ast.hasChildren() );
        assertEquals( 1 ,  ast.childCount() ); 
        
        final StatementNode stmt = (StatementNode) ast.child(0);
        assertNotNull(stmt);
        assertEquals( 1 , stmt.childCount() );
        
        final DirectiveNode insn = (DirectiveNode) stmt.child(0);
        assertNotNull(insn);
        assertEquals( Directive.CSEG , insn.directive );
    }    
    
    @Test
    public void testParseDSEG() 
    {
        AST ast = parse(".dseg");
        assertNotNull(ast);
        assertTrue( ast.hasChildren() );
        assertEquals( 1 ,  ast.childCount() ); 
        
        final StatementNode stmt = (StatementNode) ast.child(0);
        assertNotNull(stmt);
        assertEquals( 1 , stmt.childCount() );
        
        final DirectiveNode insn = (DirectiveNode) stmt.child(0);
        assertNotNull(insn);
        assertEquals( Directive.DSEG , insn.directive );
    }     
    
    @Test
    public void testParseStringLiteral1() 
    {
        AST ast = parse("lsl \"abc def 123 .,;.\"");
        assertNotNull(ast);
        assertTrue( ast.hasChildren() );
        assertEquals( 1 ,  ast.childCount() ); 
        
        final StatementNode stmt = (StatementNode) ast.child(0);
        assertNotNull(stmt);
        assertEquals( 1 , stmt.childCount() );
        
        final InstructionNode insn = (InstructionNode) stmt.child(0);
        assertEquals( 1 , insn.childCount() );        
        assertNotNull(insn);
        assertEquals( "lsl" , insn.instruction.getMnemonic());
        
        final StringLiteral num = (StringLiteral) insn.child( 0 );
        assertEquals( "abc def 123 .,;." , num.value);
    }   
    
    @Test
    public void testParseStringLiteralOnlyWhitespace() 
    {
        AST ast = parse("lsl \" \"");
        assertNotNull(ast);
        assertTrue( ast.hasChildren() );
        assertEquals( 1 ,  ast.childCount() ); 
        
        final StatementNode stmt = (StatementNode) ast.child(0);
        assertNotNull(stmt);
        assertEquals( 1 , stmt.childCount() );
        
        final InstructionNode insn = (InstructionNode) stmt.child(0);
        assertEquals( 1 , insn.childCount() );        
        assertNotNull(insn);
        assertEquals( "lsl" , insn.instruction.getMnemonic());
        
        final StringLiteral num = (StringLiteral) insn.child( 0 );
        assertEquals( " " , num.value);
    }      
    
    @Test
    public void testParseCharLiteral() 
    {
        AST ast = parse("lsl 'x'");
        assertNotNull(ast);
        assertTrue( ast.hasChildren() );
        assertEquals( 1 ,  ast.childCount() ); 
        
        final StatementNode stmt = (StatementNode) ast.child(0);
        assertNotNull(stmt);
        assertEquals( 1 , stmt.childCount() );
        
        final InstructionNode insn = (InstructionNode) stmt.child(0);
        assertEquals( 1 , insn.childCount() );        
        assertNotNull(insn);
        assertEquals( "lsl" , insn.instruction.getMnemonic());
        
        final CharacterLiteralNode num = (CharacterLiteralNode) insn.child( 0 );
        assertEquals( "Got : >"+num.value+"<" , 'x' , num.value);
    }     
    
    @Test
    public void testParseCharLiteralWhitespace() 
    {
        AST ast = parse("lsl ' '");
        assertNotNull(ast);
        assertTrue( ast.hasChildren() );
        assertEquals( 1 ,  ast.childCount() ); 
        
        final StatementNode stmt = (StatementNode) ast.child(0);
        assertNotNull(stmt);
        assertEquals( 1 , stmt.childCount() );
        
        final InstructionNode insn = (InstructionNode) stmt.child(0);
        assertEquals( 1 , insn.childCount() );        
        assertNotNull(insn);
        assertEquals( "lsl" , insn.instruction.getMnemonic());
        
        final CharacterLiteralNode num = (CharacterLiteralNode) insn.child( 0 );
        assertEquals( "Got : >"+num.value+"<" , ' ' , num.value);
    }     
    
    @Test
    public void testParseESEG() 
    {
        AST ast = parse(".eseg");
        assertNotNull(ast);
        assertTrue( ast.hasChildren() );
        assertEquals( 1 ,  ast.childCount() ); 
        
        final StatementNode stmt = (StatementNode) ast.child(0);
        assertNotNull(stmt);
        assertEquals( 1 , stmt.childCount() );
        
        final DirectiveNode insn = (DirectiveNode) stmt.child(0);
        assertNotNull(insn);
        assertEquals( Directive.ESEG , insn.directive );
    }    
    
    @Test
    public void testParseCommentLines() 
    {
        AST ast = parse("; test comment1\n; test comment2");
        assertNotNull(ast);
        assertTrue( ast.hasChildren() );
        assertEquals( 2 , ast.childCount() );
        
        {
            final StatementNode stmt = (StatementNode) ast.child(0);
            assertNotNull(stmt);
            assertEquals( 1 , stmt.childCount() );
            
            final CommentNode comment = (CommentNode) stmt.child(0);
            assertNotNull(comment);
            assertEquals("; test comment1" , comment.value );
        }
        
        {
            final StatementNode stmt = (StatementNode) ast.child(1);
            assertNotNull(stmt);
            assertEquals( 1 , stmt.childCount() );
            
            final CommentNode comment = (CommentNode) stmt.child(0);
            assertNotNull(comment);
            assertEquals("; test comment2" , comment.value );            
        }        
    }     
    
    // multi-line tests
    
    @Test
    public void testParseBlankLines() 
    {
        AST ast = parse("\n\n\n");
        assertNotNull(ast);
        assertFalse( ast.hasChildren() );
        assertEquals( 0 ,  ast.childCount() );
    }      
    
    // helper functions
    private AST parse(String s) 
    {
        final Parser p = new Parser(arch);
        final StringResource resource = new StringResource("dummy", s);
        CompilationUnit unit = new CompilationUnit( resource );
        return p.parse( unit , new LexerImpl(new Scanner(resource) ) );
    }
}
