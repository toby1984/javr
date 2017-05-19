package de.codesourcery.javr.assembler;

import java.io.IOException;
import java.util.List;

import de.codesourcery.javr.assembler.RelocationHelper.Evaluated;
import de.codesourcery.javr.assembler.RelocationHelper.Pair;
import de.codesourcery.javr.assembler.RelocationHelper.RelocationInfo;
import de.codesourcery.javr.assembler.parser.ast.AST;
import de.codesourcery.javr.assembler.parser.ast.InstructionNode;

public class RelocationHelperTest extends AbstractCompilerTest 
{
    public void testReduce1() throws IOException 
    {
           compile( "ldi r16,10");
           
           final AST ast = compilationUnit.getAST();
           final InstructionNode insn = (InstructionNode) ast.child(0).child(0);
           final Evaluated converted = RelocationHelper.convert( insn.src() );
           final Evaluated reduced = RelocationHelper.reduce( converted , compilationUnit.getSymbolTable() );
           
           assertNull( RelocationHelper.getRelocationInfo( insn.src() , compilationUnit.getSymbolTable() ) );
           
           assertEquals( 0 , reduced.childCount() );
           assertEquals( 10 , reduced.value );
           assertEquals( 10 , converted.value );
    }
    
    public void testReduce2() throws IOException 
    {
           compile( "ldi r16,10+10");
           
           final AST ast = compilationUnit.getAST();
           final InstructionNode insn = (InstructionNode) ast.child(0).child(0);
           final Evaluated converted = RelocationHelper.convert( insn.src() );
           final Evaluated reduced = RelocationHelper.reduce( converted , compilationUnit.getSymbolTable() );
           
           assertNull( RelocationHelper.getRelocationInfo( insn.src() , compilationUnit.getSymbolTable() ) );
           
           assertEquals( 0 , reduced.childCount() );
           assertEquals( 20 , reduced.value );
    }    
    
    public void testReduce3() throws IOException 
    {
           compile( "ldi r16, 1*3+5");
           
           final AST ast = compilationUnit.getAST();
           final InstructionNode insn = (InstructionNode) ast.child(0).child(0);
           final Evaluated converted = RelocationHelper.convert( insn.src() );
           final Evaluated reduced = RelocationHelper.reduce( converted , compilationUnit.getSymbolTable() );
           
           final List<Pair> list = RelocationHelper.toPairs( reduced );
           assertEquals( 1 , list.size() );
           assertTrue( list.get(0).p1.isNumber() );
           assertEquals( 8 , list.get(0).p1.value );
           assertNull( RelocationHelper.getRelocationInfo( insn.src() , compilationUnit.getSymbolTable() ) );
           
           assertEquals( 0 , reduced.childCount() );
           assertEquals( 8 , reduced.value );
    }     
    
    public void testReduce4() throws IOException 
    {
           compile( "ldi r16, 5+1*3");
           
           final AST ast = compilationUnit.getAST();
           final InstructionNode insn = (InstructionNode) ast.child(0).child(0);
           final Evaluated converted = RelocationHelper.convert( insn.src() );
           final Evaluated reduced = RelocationHelper.reduce( converted , compilationUnit.getSymbolTable() );
           
           assertNull( RelocationHelper.getRelocationInfo( insn.src() , compilationUnit.getSymbolTable() ) );
           
           assertEquals( 0 , reduced.childCount() );
           assertEquals( 8 , reduced.value );
    }    
    
    public void testReduce5() throws IOException 
    {
           compile( "ldi r16, (5+1)*3");
           
           final AST ast = compilationUnit.getAST();
           final InstructionNode insn = (InstructionNode) ast.child(0).child(0);
           final Evaluated converted = RelocationHelper.convert( insn.src() );
           final Evaluated reduced = RelocationHelper.reduce( converted , compilationUnit.getSymbolTable() );
           
           assertNull( RelocationHelper.getRelocationInfo( insn.src() , compilationUnit.getSymbolTable() ) );
           
           assertEquals( 0 , reduced.childCount() );
           assertEquals( 18 , reduced.value );
    }    
    
    public void testReduce6() throws IOException 
    {
           compile( "ldi r16, label1\n"
                   + "label1:\n");
           
           final AST ast = compilationUnit.getAST();
           final InstructionNode insn = (InstructionNode) ast.child(0).child(0);
           final Evaluated converted = RelocationHelper.convert( insn.src() );
           final Evaluated reduced = RelocationHelper.reduce( converted , compilationUnit.getSymbolTable() );
           
           assertFalse( reduced.isNumber() );
           
           final RelocationInfo info = RelocationHelper.getRelocationInfo( insn.src() , compilationUnit.getSymbolTable() );
           assertNotNull(info);
           assertEquals( 2 , info.addent );
           assertEquals( 0 , info.s );
    }    
    
    public void testReduce7() throws IOException 
    {
           compile( "ldi r16, label1+10\n"
                   + "label1:\n");
           
           final AST ast = compilationUnit.getAST();
           final InstructionNode insn = (InstructionNode) ast.child(0).child(0);
           final Evaluated converted = RelocationHelper.convert( insn.src() );
           final Evaluated reduced = RelocationHelper.reduce( converted , compilationUnit.getSymbolTable() );
           
           assertFalse( reduced.isNumber() );
           
           final RelocationInfo info = RelocationHelper.getRelocationInfo( insn.src() , compilationUnit.getSymbolTable() );
           assertNotNull(info);
           assertEquals( 2 , info.addent );
           assertEquals( 10 , info.s );
    }
    
    public void testReduce8() throws IOException 
    {
           compile( "ldi r16, label2-label1\n"
                   + "label1: .word 1\n"
                   + "label2:\n");
           
           final AST ast = compilationUnit.getAST();
           final InstructionNode insn = (InstructionNode) ast.child(0).child(0);
           final Evaluated converted = RelocationHelper.convert( insn.src() );
           final Evaluated reduced = RelocationHelper.reduce( converted , compilationUnit.getSymbolTable() );
           
           assertTrue( reduced.isNumber() );
           assertEquals( 2 , reduced.value );
           
           final RelocationInfo info = RelocationHelper.getRelocationInfo( insn.src() , compilationUnit.getSymbolTable() );
           assertNull(info);
    }    
    
    public void testReduce9() throws IOException 
    {
           compile( "ldi r16, label2-label1+3\n"
                   + "label1: .word 1\n"
                   + "label2:\n");
           
           final AST ast = compilationUnit.getAST();
           final InstructionNode insn = (InstructionNode) ast.child(0).child(0);
           final Evaluated converted = RelocationHelper.convert( insn.src() );
           final Evaluated reduced = RelocationHelper.reduce( converted , compilationUnit.getSymbolTable() );
           
           assertTrue( reduced.isNumber() );
           assertEquals( 5 , reduced.value );
           
           final RelocationInfo info = RelocationHelper.getRelocationInfo( insn.src() , compilationUnit.getSymbolTable() );
           assertNull(info);
    }      
    
    public void testReduce10() throws IOException 
    {
           compile( "ldi r16, label2 + 3 - label1\n"
                   + "label1: .word 1\n"
                   + "label2:\n");
           
           final AST ast = compilationUnit.getAST();
           final InstructionNode insn = (InstructionNode) ast.child(0).child(0);
           final Evaluated converted = RelocationHelper.convert( insn.src() );
           final Evaluated reduced = RelocationHelper.reduce( converted , compilationUnit.getSymbolTable() );
           
           assertTrue( reduced.isNumber() );
           assertEquals( 5 , reduced.value );
           
           final RelocationInfo info = RelocationHelper.getRelocationInfo( insn.src() , compilationUnit.getSymbolTable() );
           assertNull(info);
    }
    
    public void testReduce11() throws IOException 
    {
           compile( "ldi r16, label2+label1\n"
                   + "label1: .word 1\n"
                   + "label2:\n");
           
           final AST ast = compilationUnit.getAST();
           final InstructionNode insn = (InstructionNode) ast.child(0).child(0);
           final Evaluated converted = RelocationHelper.convert( insn.src() );
           final Evaluated reduced = RelocationHelper.reduce( converted , compilationUnit.getSymbolTable() );
           
           assertTrue( reduced.isNumber() );
           assertEquals( 6 , reduced.value );
           
           final RelocationInfo info = RelocationHelper.getRelocationInfo( insn.src() , compilationUnit.getSymbolTable() );
           assertNull(info);
    }    
}