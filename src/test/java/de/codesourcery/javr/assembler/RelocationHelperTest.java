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

import java.io.IOException;

import de.codesourcery.javr.assembler.RelocationHelper.Evaluated;
import de.codesourcery.javr.assembler.RelocationHelper.RelocationInfo;
import de.codesourcery.javr.assembler.elf.Relocation;
import de.codesourcery.javr.assembler.parser.ast.AST;
import de.codesourcery.javr.assembler.parser.ast.ASTNode;
import de.codesourcery.javr.assembler.parser.ast.DirectiveNode;
import de.codesourcery.javr.assembler.parser.ast.InstructionNode;
import de.codesourcery.javr.assembler.symbols.Symbol;

public class RelocationHelperTest extends AbstractCompilerTest 
{
    public void testReduce1() throws IOException 
    {
        compile( "ldi r16,10");

        final AST ast = compilationUnit.getAST();
        final InstructionNode insn = (InstructionNode) ast.child(0).child(0);
        final Evaluated reduced = RelocationHelper.convert( insn.src() );
        RelocationHelper.reduceFully( reduced );

        assertNull( RelocationHelper.getRelocationInfo( insn.src() ) );

        assertEquals( 0 , reduced.childCount() );
        assertEquals( 10 , reduced.value );
    }

    public void testReduce2() throws IOException 
    {
        compile( "ldi r16,10+10");

        final AST ast = compilationUnit.getAST();
        final InstructionNode insn = (InstructionNode) ast.child(0).child(0);
        final Evaluated reduced = RelocationHelper.convert( insn.src() );
        RelocationHelper.reduceFully( reduced );

        assertNull( RelocationHelper.getRelocationInfo( insn.src() ) );

        assertEquals( 0 , reduced.childCount() );
        assertEquals( 20 , reduced.value );
    }    

    public void testReduce3() throws IOException 
    {
        compile( "ldi r16, 1*3+5");

        final AST ast = compilationUnit.getAST();
        final InstructionNode insn = (InstructionNode) ast.child(0).child(0);
        final Evaluated reduced = RelocationHelper.convert( insn.src() );
        RelocationHelper.reduceFully( reduced );

        assertNull( RelocationHelper.getRelocationInfo( insn.src() ) );

        assertEquals( 0 , reduced.childCount() );
        assertEquals( 8 , reduced.value );
    }     

    public void testReduce4() throws IOException 
    {
        compile( "ldi r16, 5+1*3");

        final AST ast = compilationUnit.getAST();
        final InstructionNode insn = (InstructionNode) ast.child(0).child(0);
        final Evaluated reduced = RelocationHelper.convert( insn.src() );
        RelocationHelper.reduceFully( reduced );

        assertNull( RelocationHelper.getRelocationInfo( insn.src() ) );

        assertEquals( 0 , reduced.childCount() );
        assertEquals( 8 , reduced.value );
    }    

    public void testReduce5() throws IOException 
    {
        compile( "ldi r16, (5+1)*3");

        final AST ast = compilationUnit.getAST();
        final InstructionNode insn = (InstructionNode) ast.child(0).child(0);
        final Evaluated reduced = RelocationHelper.convert( insn.src() );
        RelocationHelper.reduceFully( reduced );

        assertNull( RelocationHelper.getRelocationInfo( insn.src() ) );

        assertEquals( 0 , reduced.childCount() );
        assertEquals( 18 , reduced.value );
    }    

    public void testReduce6() throws IOException 
    {
        compile( "ldi r16, label1\n"
                + "label1:\n");

        final AST ast = compilationUnit.getAST();
        final InstructionNode insn = (InstructionNode) ast.child(0).child(0);
        final Evaluated reduced = RelocationHelper.convert( insn.src() );
        RelocationHelper.reduceFully( reduced );

        assertFalse( reduced.isConstantValue() );

        final RelocationInfo reloc = RelocationHelper.getRelocationInfo( insn.src() );
        assertNotNull(reloc);
        assertEquals( 0 , reloc.expressionFlags );
        assertTrue( reloc.symbol instanceof Symbol);
        assertEquals( 2 , reloc.addent );
    }    

    public void testReduce7() throws IOException 
    {
        compile( "ldi r16, label1+10\n"
                + "label1:\n");

        final AST ast = compilationUnit.getAST();
        final InstructionNode insn = (InstructionNode) ast.child(0).child(0);
        final Evaluated reduced = RelocationHelper.convert( insn.src() );
        RelocationHelper.reduceFully( reduced );

        assertFalse( reduced.isConstantValue() );

        final RelocationInfo reloc = RelocationHelper.getRelocationInfo( insn.src() );
        assertNotNull(reloc);
        assertEquals( 0 , reloc.expressionFlags );
        assertTrue( reloc.symbol instanceof Symbol);
        assertEquals( 2+10 , reloc.addent );
    }

    public void testReduce8() throws IOException 
    {
        compile( "ldi r16, label2-label1\n"
                + "label1: .word 1\n"
                + "label2:\n");

        final AST ast = compilationUnit.getAST();
        final InstructionNode insn = (InstructionNode) ast.child(0).child(0);
        final Evaluated reduced = RelocationHelper.convert( insn.src() );
        RelocationHelper.reduceFully( reduced );

        assertTrue( reduced.isConstantValue() );
        assertEquals( 2 , reduced.value );

        assertNull(RelocationHelper.getRelocationInfo( insn.src() ));
    }    

    public void testReduce9() throws IOException 
    {
        compile( "ldi r16, label2-label1+3\n"
                + "label1: .word 1\n"
                + "label2:\n");

        final AST ast = compilationUnit.getAST();
        final InstructionNode insn = (InstructionNode) ast.child(0).child(0);
        final Evaluated reduced = RelocationHelper.convert( insn.src() );
        RelocationHelper.reduceFully( reduced );

        assertTrue( reduced.isConstantValue() );
        assertEquals( 5 , reduced.value );

        assertNull(RelocationHelper.getRelocationInfo( insn.src() ));
    }      

    public void testReduce10() throws IOException 
    {
        compile( "ldi r16, label2 + 3 - label1\n"
                + "label1: .word 1\n"
                + "label2:\n");

        final AST ast = compilationUnit.getAST();
        final InstructionNode insn = (InstructionNode) ast.child(0).child(0);
        final Evaluated reduced = RelocationHelper.convert( insn.src() );
        RelocationHelper.reduceFully( reduced );
        System.out.println( reduced.printTree() );

        assertTrue( "Expression should've been reduced to a number" , reduced.isConstantValue() );
        assertEquals( 5 , reduced.value );

        assertNull(RelocationHelper.getRelocationInfo( insn.src() ));
    }

    public void testReduce11() throws IOException 
    {
        compile( "ldi r16, 3+label2 - label1\n"
                + "label1: .word 1\n"
                + "label2:\n");

        final AST ast = compilationUnit.getAST();
        final InstructionNode insn = (InstructionNode) ast.child(0).child(0);
        final Evaluated reduced = RelocationHelper.convert( insn.src() );
        System.out.println( "Converted: \n"+reduced.printTree() );

        RelocationHelper.reduceFully( reduced );
        System.out.println( reduced.printTree() );

        assertTrue( "Expression should've been reduced to a number" , reduced.isConstantValue() );
        assertEquals( 5 , reduced.value );

        assertNull(RelocationHelper.getRelocationInfo( insn.src() ));
    }    

    public void testReduce12() throws IOException 
    {
        compile( "ldi r16, 6+label2 - 4 + label1+7-1+3\n" // 6 - 4 + 7 - 1 + 3 = 11 
                + "label1: .word 1\n"
                + "label2:\n");

        final AST ast = compilationUnit.getAST();
        final InstructionNode insn = (InstructionNode) ast.child(0).child(0);
        final Evaluated reduced = RelocationHelper.convert( insn.src() );
        System.out.println( "Converted: \n"+reduced.printExpression() );

        RelocationHelper.reduceFully( reduced );
        System.out.println( reduced.printExpression() );

        RelocationHelper.reduceFully( reduced );
        assertTrue( "Expression should've been reduced to a number" , reduced.isConstantValue() );
        assertEquals( 17 , reduced.value );

        assertNull(RelocationHelper.getRelocationInfo( insn.src() ));
    }     

    public void testReduce13() throws IOException
    {
        compile( "ldi r16, label2+label1\n"
                + "label1: .word 1\n"
                + "label2:\n");

        final AST ast = compilationUnit.getAST();
        final InstructionNode insn = (InstructionNode) ast.child(0).child(0);
        final Evaluated reduced = RelocationHelper.convert( insn.src() );
        RelocationHelper.reduceFully( reduced );

        assertTrue( reduced.isConstantValue() );
        assertEquals( 6 , reduced.value );

        assertNull(RelocationHelper.getRelocationInfo( insn.src() ));
    }    

    // LDI r31, HIGH(framebuffer+7*8*BYTES_PER_ROW)

    public void testReduce14() throws IOException
    {
        compile( ".equ BYTES_PER_ROW = 8\n"
                + "ldi r16,HIGH(framebuffer+7*8*BYTES_PER_ROW)\n"
                +".dseg\n"
                +".dw 1\n"
                + "framebuffer: "
                + ".byte 1024\n");

        final AST ast = compilationUnit.getAST();
        final InstructionNode insn = (InstructionNode) ast.child(1).child(0);
        final Evaluated reduced = RelocationHelper.convert( insn.src() );
        RelocationHelper.reduceFully( reduced );

        System.out.println("DEBUG: "+reduced.printTree());
        assertFalse( reduced.isConstantValue() );

        final RelocationInfo info = RelocationHelper.getRelocationInfo( insn.src() );
        assertNotNull(info);
        assertEquals( info.expressionFlags , Relocation.EXPR_FLAG_HI );
        assertEquals( "framebuffer" , info.symbol.name().value ); 
        assertEquals( 2 + 448, info.addent ); 
    }

    public void testReduce15() throws IOException 
    {
        compile( ".equ TEST = 9\n"
                + "ldi r16,3*TEST\n");

        final AST ast = compilationUnit.getAST();
        final InstructionNode insn = (InstructionNode) ast.child(1).child(0);
        final Evaluated reduced = RelocationHelper.convert( insn.src() );
        RelocationHelper.reduceFully( reduced );

        assertNull( RelocationHelper.getRelocationInfo( insn.src() ) );

        assertEquals( 0 , reduced.childCount() );
        assertEquals( 27 , reduced.value );
    }    

    public void testReduce16() throws IOException 
    {
        compile( ".equ TEST = 0x1234\n"
                + "ldi r16,HIGH(TEST)\n");

        final AST ast = compilationUnit.getAST();
        final InstructionNode insn = (InstructionNode) ast.child(1).child(0);
        final Evaluated reduced = RelocationHelper.convert( insn.src() );
        RelocationHelper.reduceFully( reduced );

        assertNull( RelocationHelper.getRelocationInfo( insn.src() ) );

        assertEquals( 0 , reduced.childCount() );
        assertEquals( 0x12 , reduced.value );
    }    

    public void testReduce17() throws IOException 
    {
        compile( "ldi r16,HIGH(label+10)\n"
                + ".dseg\n"
                + ".dw 1\n"
                + "label: .dw 1\n");

        final AST ast = compilationUnit.getAST();
        final InstructionNode insn = (InstructionNode) ast.child(0).child(0);
        final Evaluated reduced = RelocationHelper.convert( insn.src() );
        RelocationHelper.reduceFully( reduced );

        assertFalse( reduced.isConstantValue() );

        final RelocationInfo reloc = RelocationHelper.getRelocationInfo( insn.src() );
        assertNotNull( reloc );
        assertEquals( reloc.expressionFlags , Relocation.EXPR_FLAG_HI );
        assertEquals( "label" , reloc.symbol.name().value ); 
        assertEquals( 2 + 10 , reloc.addent );
    }

    public void testReduce18() throws IOException 
    {
        compile( "ldi r16, HIGH(label1 >> 1)\n"
                + ".dseg\n"
                + ".dw 1\n"
                + "label1:\n");

        final AST ast = compilationUnit.getAST();
        final InstructionNode insn = (InstructionNode) ast.child(0).child(0);
        final Evaluated reduced = RelocationHelper.convert( insn.src() );
        RelocationHelper.reduceFully( reduced );

        assertFalse( reduced.isConstantValue() );

        final RelocationInfo reloc = RelocationHelper.getRelocationInfo( insn.src() );
        assertNotNull(reloc);
        assertEquals( Relocation.EXPR_FLAG_HI | Relocation.EXPR_FLAG_PM  , reloc.expressionFlags );
        assertEquals( "label1" , reloc.symbol.name().value );            
        assertEquals( 2 , reloc.addent );
    }

    public void testReduce19() throws IOException 
    {
        compile( "ldi r16, LOW(label1 >> 1)\n"
                + ".dseg\n"
                + ".dw 1\n"
                + "label1:\n");

        final AST ast = compilationUnit.getAST();
        final InstructionNode insn = (InstructionNode) ast.child(0).child(0);
        final Evaluated reduced = RelocationHelper.convert( insn.src() );
        RelocationHelper.reduceFully( reduced );

        assertFalse( reduced.isConstantValue() );

        final RelocationInfo reloc = RelocationHelper.getRelocationInfo( insn.src() );
        assertNotNull(reloc);
        assertEquals( Relocation.EXPR_FLAG_LO | Relocation.EXPR_FLAG_PM  , reloc.expressionFlags );
        assertEquals( "label1" , reloc.symbol.name().value );            
        assertEquals( 2 , reloc.addent );
    } 

    public void testReduce20() throws IOException 
    {
        compile( "ldi r16, LOW(-(label1 >> 1))\n"
                + ".dseg\n"
                + ".dw 1\n"
                + "label1:\n");

        final AST ast = compilationUnit.getAST();
        final InstructionNode insn = (InstructionNode) ast.child(0).child(0);
        final Evaluated reduced = RelocationHelper.convert( insn.src() );
        RelocationHelper.reduceFully( reduced );

        assertFalse( reduced.isConstantValue() );

        final RelocationInfo reloc = RelocationHelper.getRelocationInfo( insn.src() );
        assertNotNull(reloc);
        assertEquals( Relocation.EXPR_FLAG_LO | Relocation.EXPR_FLAG_PM | Relocation.EXPR_FLAG_NEG , reloc.expressionFlags );
        assertEquals( "label1" , reloc.symbol.name().value );            
        assertEquals( 2 , reloc.addent );
    }     

    public void testReduce21() throws IOException 
    {
        compile( "ldi r16, LOW(-label1)\n"
                + ".dseg\n"
                + ".dw 1\n"
                + "label1:\n");

        final AST ast = compilationUnit.getAST();
        final InstructionNode insn = (InstructionNode) ast.child(0).child(0);
        final Evaluated reduced = RelocationHelper.convert( insn.src() );
        RelocationHelper.reduceFully( reduced );

        assertFalse( reduced.isConstantValue() );

        final RelocationInfo reloc = RelocationHelper.getRelocationInfo( insn.src() );
        assertNotNull(reloc);
        assertEquals( Relocation.EXPR_FLAG_LO | Relocation.EXPR_FLAG_NEG , reloc.expressionFlags );
        assertEquals( "label1" , reloc.symbol.name().value );            
        assertEquals( 2 , reloc.addent );
    }   

    public void testReduce22() throws IOException 
    {
        compile( "ldi r16, HIGH(-label1)\n"
                + ".dseg\n"
                + ".dw 1\n"
                + "label1:\n");

        final AST ast = compilationUnit.getAST();
        final InstructionNode insn = (InstructionNode) ast.child(0).child(0);
        final Evaluated reduced = RelocationHelper.convert( insn.src() );
        RelocationHelper.reduceFully( reduced );

        assertFalse( reduced.isConstantValue() );

        final RelocationInfo reloc = RelocationHelper.getRelocationInfo( insn.src() );
        assertNotNull(reloc);
        assertEquals( Relocation.EXPR_FLAG_HI | Relocation.EXPR_FLAG_NEG , reloc.expressionFlags );
        assertEquals( "label1" , reloc.symbol.name().value );            
        assertEquals( 2 , reloc.addent );
    }   
    
    public void testReduce23() throws IOException 
    {
        compile( " ret\n"
                + "commands: .dw cmd1,1\n"
                + ".dseg\n"
                + ".dw 2\n"
                + "cmd1: .dw 1\n");

        final AST ast = compilationUnit.getAST();
        final ASTNode insn = (ASTNode) ((DirectiveNode) ast.child(1).child(1)).child(0);
        final Evaluated reduced = RelocationHelper.convert( insn );
        RelocationHelper.reduceFully( reduced );

        assertFalse( reduced.isConstantValue() );

        final RelocationInfo reloc = RelocationHelper.getRelocationInfo( insn );
        assertNotNull(reloc);
        assertEquals( 0 , reloc.expressionFlags );
        assertEquals( "cmd1" , reloc.symbol.name().value );
        assertEquals( 2 , reloc.addent );
    }      

}