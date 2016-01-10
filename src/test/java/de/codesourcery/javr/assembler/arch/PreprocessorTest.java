package de.codesourcery.javr.assembler.arch;

import java.io.IOException;
import java.util.ArrayList;

import org.apache.commons.lang3.Validate;

import de.codesourcery.javr.assembler.Address;
import de.codesourcery.javr.assembler.CompilerSettings;
import de.codesourcery.javr.assembler.CompilationUnit;
import de.codesourcery.javr.assembler.ICompilationContext;
import de.codesourcery.javr.assembler.Segment;
import de.codesourcery.javr.assembler.arch.impl.ATMega88;
import de.codesourcery.javr.assembler.parser.Lexer;
import de.codesourcery.javr.assembler.parser.Parser;
import de.codesourcery.javr.assembler.parser.Parser.CompilationMessage;
import de.codesourcery.javr.assembler.parser.Parser.Severity;
import de.codesourcery.javr.assembler.parser.Scanner;
import de.codesourcery.javr.assembler.parser.ast.AST;
import de.codesourcery.javr.assembler.parser.ast.ASTNode;
import de.codesourcery.javr.assembler.phases.PreProcessor;
import de.codesourcery.javr.assembler.symbols.SymbolTable;
import de.codesourcery.javr.assembler.util.StringResource;
import junit.framework.TestCase;

public class PreprocessorTest extends TestCase 
{
    public void testMessage() 
    {
        AST ast = preprocess("#message test");
        assertMessage(Severity.INFO,"test" , ast );
    }
    
    private void assertMessage(Severity s , String text, AST ast ) 
    {
        assertEquals(1,ast.getMessages().size());
        final CompilationMessage msg = ast.getMessages().get(0);
        assertEquals( text ,msg.message);
        assertEquals( s , msg.severity );
        if ( s == Severity.ERROR ) {
            assertTrue( ast.hasErrors() );
        } else {
            assertFalse( ast.hasErrors() );
        }
    }
    
    public void testWarning() 
    {
        AST ast = preprocess("#warning test");
        assertMessage(Severity.WARNING,"test" , ast );
    }
    
    public void testError() 
    {
        AST ast = preprocess("#error test");
        assertMessage(Severity.ERROR,"test" , ast );
    }    
    
    public void testParseIfDefWithoutEndIf1() 
    {
        AST ast = preprocess("#ifndef test");
        assertMessage(Severity.ERROR,"Missing #endif",ast);
    }    
    
    public void testParseMismatchedEndif() 
    {
        AST ast = preprocess("#ifndef test\n"
                + "#ifndef test\n"
                + "#endif");
        assertMessage(Severity.ERROR,"Missing #endif",ast);
    }      
    
    public void testParseNestedIfDef() 
    {
        AST ast = preprocess("#ifndef test\n"
                + "#ifndef test\n"
                + "#endif\n"
                + "#endif");
        assertFalse(ast.hasErrors());
    }   
    
    public void testIfDefWorks() 
    {
        AST ast = preprocess("#define test\n"
                + "#ifdef test\n"
                + "#message test\n"
                + "#endif");
        assertMessage(Severity.INFO,"test" , ast );
        assertFalse(ast.hasErrors());
    }   
    
    public void testIfNDefWorks2() 
    {
        AST ast = preprocess("#define test\n"
                + "#ifndef test\n"
                + "#message test\n"               
                + "#endif");
        assertEquals(new ArrayList<>() , ast.getMessages());
        assertFalse(ast.hasErrors());
    }      

    public void testIfNDefWorks() 
    {
        AST ast = preprocess("#ifndef test\n"
                + "#message test\n"
                + "#endif");
        assertMessage(Severity.INFO,"test" , ast );
        assertFalse(ast.hasErrors());
    }     
    
    public void testParseIfDefWithEndIf1() 
    {
        AST ast = preprocess("#ifndef test\n#endif");
        assertFalse(ast.hasErrors());
    }      
    
    private AST preprocess(String source) {
        
        final Parser parser = new Parser();
        final ATMega88 arch = new ATMega88();
        parser.setArchitecture( arch );
        
        final CompilationUnit unit = new CompilationUnit( new StringResource("test",source) );
        final AST ast = parser.parse( new Lexer( new Scanner(source ) ) );
        unit.setAst( ast );
        final FakeCtx ctx = new FakeCtx( unit );
        new PreProcessor().preprocess(ast , ctx );
        return ast;
    }
    
    protected static final class FakeCtx implements ICompilationContext {

        private final CompilerSettings settings = new CompilerSettings();
        private final SymbolTable global = new SymbolTable( SymbolTable.GLOBAL );
        private final SymbolTable local = new SymbolTable("local",global);
        
        private final ATMega88 arch = new ATMega88();
        private final CompilationUnit unit;
        
        public FakeCtx(CompilationUnit unit) {
            Validate.notNull(unit, "unit must not be NULL");
            this.unit = unit;
        }
        @Override
        public AST parseInclude(String file) throws IOException {
            throw new UnsupportedOperationException();
        }

        @Override
        public ICompilationSettings getCompilationSettings() {
            return settings;
        }

        @Override
        public SymbolTable globalSymbolTable() {
            return global;
        }

        @Override
        public SymbolTable currentSymbolTable() {
            return local;
        }

        @Override
        public CompilationUnit currentCompilationUnit() {
            return unit;
        }

        @Override
        public int currentOffset() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Address currentAddress() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Segment currentSegment() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void setSegment(Segment s) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void writeByte(int value) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void writeWord(int value) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void allocateByte() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void allocateWord() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void allocateBytes(int numberOfBytes) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void error(String message, ASTNode node) {
            message(CompilationMessage.error(message, node) );
        }

        @Override
        public void message(CompilationMessage msg) {
            unit.getAST().addMessage( msg );
            System.err.println( msg );
        }

        @Override
        public IArchitecture getArchitecture() {
            return arch;
        }
    }
}
