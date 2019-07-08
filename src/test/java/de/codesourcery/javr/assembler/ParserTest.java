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
package de.codesourcery.javr.assembler;

import java.util.List;

import de.codesourcery.javr.assembler.parser.ast.FloatNumberLiteralNode;
import org.junit.Test;

import de.codesourcery.javr.assembler.parser.Identifier;
import de.codesourcery.javr.assembler.parser.OperatorType;
import de.codesourcery.javr.assembler.parser.TextRegion;
import de.codesourcery.javr.assembler.parser.ast.AST;
import de.codesourcery.javr.assembler.parser.ast.ASTNode;
import de.codesourcery.javr.assembler.parser.ast.CharacterLiteralNode;
import de.codesourcery.javr.assembler.parser.ast.CommentNode;
import de.codesourcery.javr.assembler.parser.ast.CurrentAddressNode;
import de.codesourcery.javr.assembler.parser.ast.DirectiveNode;
import de.codesourcery.javr.assembler.parser.ast.DirectiveNode.Directive;
import de.codesourcery.javr.assembler.parser.ast.EquLabelNode;
import de.codesourcery.javr.assembler.parser.ast.FunctionCallNode;
import de.codesourcery.javr.assembler.parser.ast.FunctionDefinitionNode;
import de.codesourcery.javr.assembler.parser.ast.IValueNode;
import de.codesourcery.javr.assembler.parser.ast.IdentifierDefNode;
import de.codesourcery.javr.assembler.parser.ast.IdentifierNode;
import de.codesourcery.javr.assembler.parser.ast.InstructionNode;
import de.codesourcery.javr.assembler.parser.ast.LabelNode;
import de.codesourcery.javr.assembler.parser.ast.IntNumberLiteralNode;
import de.codesourcery.javr.assembler.parser.ast.IntNumberLiteralNode.LiteralType;
import de.codesourcery.javr.assembler.parser.ast.OperatorNode;
import de.codesourcery.javr.assembler.parser.ast.PreprocessorNode;
import de.codesourcery.javr.assembler.parser.ast.PreprocessorNode.Preprocessor;
import de.codesourcery.javr.assembler.parser.ast.RegisterNode;
import de.codesourcery.javr.assembler.parser.ast.StatementNode;
import de.codesourcery.javr.assembler.parser.ast.StringLiteral;

public class ParserTest extends ParseTestHelper
{
    @Test
    public void testEmptyFile() 
    {
        AST ast = parse("");
        assertNotNull(ast);
        assertFalse( ast.hasChildren() );
        assertEquals( 0 ,  ast.childCount() );        
    }

    @Test
    public void testParseFloatingPointNumber() {
        AST ast = parse(".equ a = 1.25");
        assertNotNull(ast);
        assertTrue( ast.hasChildren() );

        StatementNode stmt = (StatementNode) ast.child( 0);

        System.out.println("GOT: ast = \n"+ast);

        final DirectiveNode directive = (DirectiveNode) stmt.child( 0 );
        assertEquals( Directive.EQU, directive.directive );

        final ASTNode identifier = directive.child( 0 );
        assertTrue( "Not an identifier node: "+identifier.getClass().getName() , identifier instanceof EquLabelNode );
        assertEquals( "a" , ((EquLabelNode) identifier).name.value );

        assertTrue( directive.child(1) instanceof FloatNumberLiteralNode);
        assertEquals( 1.25d , ((FloatNumberLiteralNode) directive.child(1)).getValue() );
    }
    
    @Test
    public void testParseIncludeBinary() 
    {
        AST ast = parse("#incbin \"test\"");
        assertNotNull(ast);
        assertEquals(1 , ast.childCount() );
        
        final ASTNode stmt = ast.child(0);
        assertEquals( StatementNode.class     , stmt.getClass() );
        assertEquals( 1 , stmt.childCount() );
        
        ASTNode child = stmt.child(0);
        assertEquals( PreprocessorNode.class , child.getClass() );

        final PreprocessorNode pn = (PreprocessorNode) child;
        assertEquals( Preprocessor.INCLUDE_BINARY , pn.type );
        final List<String> args = pn.getArguments();
        assertEquals(1,args.size());
        assertEquals("\"test\"",args.get(0) );
        final TextRegion region = pn.getTextRegion();
        assertEquals(0,region.start());
        assertEquals(14,region.length());
    }    
    
    @Test
    public void testParseIncludeBinaryWithWhitespace() 
    {
        AST ast = parse("#incbin \"test1 test2\"");
        assertNotNull(ast);
        assertEquals(1 , ast.childCount() );
        
        final ASTNode stmt = ast.child(0);
        assertEquals( StatementNode.class     , stmt.getClass() );
        assertEquals( 1 , stmt.childCount() );
        
        ASTNode child = stmt.child(0);
        assertEquals( PreprocessorNode.class , child.getClass() );

        final PreprocessorNode pn = (PreprocessorNode) child;
        assertEquals( Preprocessor.INCLUDE_BINARY , pn.type );
        final List<String> args = pn.getArguments();
        assertEquals(1,args.size());
        assertEquals("\"test1 test2\"",args.get(0) );
        final TextRegion region = pn.getTextRegion();
        assertEquals(0,region.start());
        assertEquals(21,region.length());
    }      
    
    @Test
    public void testParseIncludeBinaryNoArgs() 
    {
        AST ast = parse("#incbin");
        assertTrue( unit.hasErrors( false ) );
        assertNotNull(ast);
        assertEquals(0 , ast.childCount() );
    }
    
    @Test
    public void testParseIncludeBinaryTooManyArgs() 
    {
        AST ast = parse("#incbin \"a\" \"b\"");
        assertTrue( unit.hasErrors( false ) );
        assertNotNull(ast);
        assertEquals(0 , ast.childCount() );
    }  
    
    @Test
    public void testParseIncludeBinaryWrongArg() 
    {
        AST ast = parse("#incbin 3");
        assertTrue( unit.hasErrors( false ) );
        assertNotNull(ast);
        assertEquals(0 , ast.childCount() );
    }    
    
    @Test
    public void testParseCommentAfterEQUWithPreprocessingLexer() 
    {
        final String asm = ".equ    EICRA   = 0x69  ; MEMORY MAPPED"; 
        
        AST ast = parseWithPreprocessor( asm );
        ast.visitBreadthFirst( (node,ctx) -> 
        {
            System.out.println("GOT: "+node.getClass().getSimpleName());
        });
        assertNotNull(ast);
        assertEquals(1 , ast.childCount() );
        
        final ASTNode stmt = ast.child(0);
        assertEquals( StatementNode.class     , stmt.getClass() );
        assertEquals( 2 , stmt.childCount() );
        
        final ASTNode directive = stmt.child(0);
        assertEquals( DirectiveNode.class     , directive.getClass() );
        assertEquals( EquLabelNode.class      , directive.child(0).getClass() );
        assertEquals( IntNumberLiteralNode.class , directive.child( 1).getClass() );
        
        final ASTNode commentNode = stmt.child(1);
        assertEquals( CommentNode.class       , commentNode.getClass() );
        assertEquals("; MEMORY MAPPED" , ((CommentNode) commentNode).value );
    }
    
    @Test
    public void testParseComment() {
        final String asm = ";***** THIS IS A MACHINE GENERATED FILE - DO NOT EDIT ********************\n" + 
                           ";***** Created: 2007-09-11 14:24 ******* Source: ATmega88.xml ************\n"+
                           "ldi r16,32";
        
        AST ast = parse( asm );
        assertNotNull(ast);
        assertEquals( 3 ,  ast.childCount() );
        
        // line no. 1
        final String line1 = ";***** THIS IS A MACHINE GENERATED FILE - DO NOT EDIT ********************";
        System.out.println("line1 has length "+line1.length());
        StatementNode stmt = (StatementNode) ast.child(0);
        assertEquals(1,stmt.childCount());
        
        CommentNode comment = (CommentNode) stmt.child(0);
        assertEquals(line1 , comment.value);
        assertEquals( line1.length() , comment.getTextRegion().length() );
        
        // line no. 2
        final String line2 = ";***** Created: 2007-09-11 14:24 ******* Source: ATmega88.xml ************";
        System.out.println("line2 has length "+line2.length());
        stmt = (StatementNode) ast.child(1);
        assertEquals(1,stmt.childCount());
        
        comment = (CommentNode) stmt.child(0);
        assertEquals(";***** Created: 2007-09-11 14:24 ******* Source: ATmega88.xml ************" , comment.value);
        assertEquals( 75 , comment.getTextRegion().start() );
        assertEquals( line2.length() , comment.getTextRegion().length() );
        
        // line no . 3
        stmt = (StatementNode) ast.child(2);
        assertEquals(1,stmt.childCount());
        
        final InstructionNode ins = (InstructionNode) stmt.child(0);
        System.out.println("Instruction covers "+ins.getTextRegion());
    }
    
    @Test
    public void testParseBlankLine() 
    {
        AST ast = parse("\n");
        assertNotNull(ast);
        assertFalse( ast.hasChildren() );
    } 
    
    @Test
    public void testParsePragma2() 
    {
        AST ast = parse("#pragma partinc 0");
        assertNotNull(ast);
        assertTrue( ast.hasChildren() );
        assertEquals( 1 ,  ast.childCount() );
        
        final StatementNode stmt = (StatementNode) ast.child(0);
        assertNotNull(stmt);
        assertEquals( 1 , stmt.childCount() );
        
        final PreprocessorNode ins = (PreprocessorNode) stmt.child(0);
        assertEquals( Preprocessor.PRAGMA , ins.type );
        assertEquals( 0 , ins.childCount() );
        assertEquals( 2 , ins.arguments.size() );
        assertEquals("partinc" , ins.arguments.get(0) );
        assertEquals("0" , ins.arguments.get(1) );
    }    
    
    @Test
    public void testParseRegisterAlias() 
    {
        AST ast = parse(".def TEST = r16");
        assertNotNull(ast);
        assertTrue( ast.hasChildren() );
        assertEquals( 1 ,  ast.childCount() );
        
        final StatementNode stmt = (StatementNode) ast.child(0);
        assertNotNull(stmt);
        assertEquals( 1 , stmt.childCount() );
        
        final DirectiveNode ins = (DirectiveNode) stmt.child(0);
        assertNotNull(ins);
        assertEquals( Directive.DEF , ins.directive );
        assertEquals( 2 , ins.childCount() );
        
        final IdentifierDefNode name = (IdentifierDefNode) ins.child(0);
        final RegisterNode register = (RegisterNode) ins.child(1);
        
        assertEquals( Identifier.of("test") , name.name );
        assertEquals( new Register("r16",false,false), register.register );
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
    public void testParseDefineInstruction() 
    {
        parse("#define test sbi 0x05 , 1\n"
                + "test");
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
        
        final IntNumberLiteralNode num = (IntNumberLiteralNode) insn.child( 0 );
        assertEquals( 123 , num.getValue().intValue() );
        assertEquals( IntNumberLiteralNode.LiteralType.DECIMAL , num.getType() );
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
        
        final IntNumberLiteralNode num1 = (IntNumberLiteralNode) insn.child( 0 );
        assertEquals( 123 , num1.getValue().intValue() );
        assertEquals( IntNumberLiteralNode.LiteralType.DECIMAL , num1.getType() );
        
        final IntNumberLiteralNode num2 = (IntNumberLiteralNode) insn.child( 1 );
        assertEquals( 456 , num2.getValue().intValue()  );
        assertEquals( IntNumberLiteralNode.LiteralType.DECIMAL , num2.getType() );
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
        assertEquals( IntNumberLiteralNode.class , op.child( 1).getClass() );
        
        assertEquals( 2 , ((IntNumberLiteralNode) op.child( 1)).getValue().intValue() );
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
        assertEquals( IntNumberLiteralNode.class , op.child( 1).getClass() );
        
        assertEquals( 2 , ((IntNumberLiteralNode) op.child( 1)).getValue().intValue() );
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
        assertEquals( 1 , ((IntNumberLiteralNode) num2.child( 0)).getValue().intValue() );
        assertEquals( 2 , ((IntNumberLiteralNode) num2.child( 1)).getValue().intValue() );
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
        assertEquals( 1 , ((IntNumberLiteralNode) num2.child( 0)).getValue().intValue() );
        assertEquals( 2 , ((IntNumberLiteralNode) num2.child( 1)).getValue().intValue() );
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
        assertTrue( num2.child(0) instanceof IntNumberLiteralNode);
        IntNumberLiteralNode num3 = (IntNumberLiteralNode) num2.child( 0);
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
        
        final IntNumberLiteralNode num = (IntNumberLiteralNode) insn.child( 0 );
        assertEquals( 0x123 , num.getValue().intValue()  );
        assertEquals( IntNumberLiteralNode.LiteralType.HEXADECIMAL , num.getType() );
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
        
        final IntNumberLiteralNode num = (IntNumberLiteralNode) node.child( 0 );
        assertEquals( 10 , num.getValue().intValue()  );
        assertEquals( IntNumberLiteralNode.LiteralType.DECIMAL , num.getType() );
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
        
        final IntNumberLiteralNode num = (IntNumberLiteralNode) node.child( 0 );
        assertEquals( 1 , num.getValue().intValue()  );
        assertEquals( IntNumberLiteralNode.LiteralType.DECIMAL , num.getType() );
        
        final IntNumberLiteralNode num2 = (IntNumberLiteralNode) node.child( 1 );
        assertEquals( 2 , num2.getValue().intValue()  );
        assertEquals( IntNumberLiteralNode.LiteralType.DECIMAL , num2.getType() );
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
        
        final IntNumberLiteralNode num = (IntNumberLiteralNode) node.child( 0 );
        assertEquals( 1 , num.getValue().intValue()  );
        assertEquals( IntNumberLiteralNode.LiteralType.DECIMAL , num.getType() );
        
        final IntNumberLiteralNode num2 = (IntNumberLiteralNode) node.child( 1 );
        assertEquals( 2 , num2.getValue().intValue()  );
        assertEquals( IntNumberLiteralNode.LiteralType.DECIMAL , num2.getType() );
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
        final IntNumberLiteralNode num2 = (IntNumberLiteralNode) node.child( 1);
        
        assertEquals( new Identifier("A") , label.name );
        assertEquals( 1 , num2.getValue().intValue() );
    }     
    
    @Test
    public void testParseIRQ() 
    {
        AST ast = parse(".irq 12");
        assertNotNull(ast);
        assertTrue( ast.hasChildren() );
        assertEquals( 1 ,  ast.childCount() ); 
        
        final StatementNode stmt = (StatementNode) ast.child(0);
        assertNotNull(stmt);
        assertEquals( 1 , stmt.childCount() );
        
        final DirectiveNode node = (DirectiveNode) stmt.child(0);
        assertEquals(Directive.IRQ_ROUTINE, node.directive );
        assertNotNull(node);
        assertEquals( 1 , node.childCount() );

        final IntNumberLiteralNode num2 = (IntNumberLiteralNode) node.child( 0);
        assertEquals( 12 , num2.getValue().intValue() );
    }     
    
    @Test
    public void testParseExpression() {
        
        AST ast = parse(".equ a = ((16000000 * 500)/1000)-3");
        assertNotNull(ast);
        assertTrue( ast.hasChildren() );
        assertEquals( 1 ,  ast.childCount() ); 
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
        final IntNumberLiteralNode num2 = (IntNumberLiteralNode) node.child( 1);
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
        
        final IntNumberLiteralNode num = (IntNumberLiteralNode) insn.child( 0 );
        assertEquals( 0b1011 , num.getValue().intValue()  );
        assertEquals( IntNumberLiteralNode.LiteralType.BINARY , num.getType() );
    }     
    
    @Test
    public void testParseORG() 
    {
        AST ast = parse(".org 42");
        assertNotNull(ast);
        assertTrue( ast.hasChildren() );
        assertEquals( 1 ,  ast.childCount() ); 
        
        final StatementNode stmt = (StatementNode) ast.child(0);
        assertNotNull(stmt);
        assertEquals( 1 , stmt.childCount() );
        
        final DirectiveNode insn = (DirectiveNode) stmt.child(0);
        assertNotNull(insn);
        assertEquals( Directive.ORG , insn.directive );
        assertEquals(1,insn.childCount());
        
        assertTrue( insn.child(0) instanceof IValueNode);
        assertEquals( (int) 42 , ((Number) ((IValueNode) insn.child(0)).getValue()).intValue() );
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
}