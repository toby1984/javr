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

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Stack;
import java.util.function.Function;

import org.apache.commons.lang3.Validate;
import org.junit.Before;
import org.junit.Test;

import de.codesourcery.javr.assembler.arch.IArchitecture;
import de.codesourcery.javr.assembler.arch.impl.ATMega88;
import de.codesourcery.javr.assembler.elf.Relocation;
import de.codesourcery.javr.assembler.exceptions.ParseException;
import de.codesourcery.javr.assembler.parser.Lexer;
import de.codesourcery.javr.assembler.parser.LexerImpl;
import de.codesourcery.javr.assembler.parser.Parser.CompilationMessage;
import de.codesourcery.javr.assembler.parser.Parser.Severity;
import de.codesourcery.javr.assembler.parser.PreprocessingLexer;
import de.codesourcery.javr.assembler.parser.Scanner;
import de.codesourcery.javr.assembler.parser.Token;
import de.codesourcery.javr.assembler.parser.TokenType;
import de.codesourcery.javr.assembler.parser.ast.ASTNode;
import de.codesourcery.javr.assembler.symbols.SymbolTable;
import de.codesourcery.javr.assembler.util.FileResourceFactory;
import de.codesourcery.javr.assembler.util.Resource;
import de.codesourcery.javr.assembler.util.StringResource;
import junit.framework.TestCase;

public class PreprocessingLexerTest extends TestCase 
{
    private final IArchitecture arch = new ATMega88();
    
    private ResourceFactory resFactory = FileResourceFactory.createInstance(new File("/"));
    private final List<CompilationMessage> messages = new ArrayList<>();
    private final Stack<CompilationUnit> unitStack = new Stack<>();
    private final Stack<Segment> segmentStack = new Stack<>();

    private Function<Resource,CompilationUnit> unitFactory = res -> {
        throw new RuntimeException("method not implemented: getOrCreateCompilationUnit()");
    };
    
    @Before
    public void setup() 
    {
        resFactory = FileResourceFactory.createInstance(new File("/"));
        messages.clear();
        unitStack.clear();
        segmentStack.clear();
        unitFactory = res -> {
            throw new RuntimeException("method not implemented: getOrCreateCompilationUnit()");
        };
    }
    
    private final ICompilationContext fakeContext = new ICompilationContext() {

        public CompilationUnit newCompilationUnit(de.codesourcery.javr.assembler.util.Resource res) 
        {
            return unitFactory.apply( res );
        };
        
        @Override
        public boolean pushCompilationUnit(CompilationUnit unit) 
        {
            Validate.notNull(unit, "unit must not be NULL");
            unitStack.push(unit);
            segmentStack.push(Segment.FLASH);
            return true;
        }

        @Override
        public void popCompilationUnit() 
        {
            unitStack.pop();
            segmentStack.pop();
        }

        @Override
        public ResourceFactory getResourceFactory() {
            return resFactory;
        }

        @Override
        public ICompilerSettings getCompilationSettings() {
            throw new RuntimeException("method not implemented: getCompilationSettings");
        }

        @Override
        public SymbolTable globalSymbolTable() {
            throw new RuntimeException("method not implemented: globalSymbolTable");
        }

        @Override
        public SymbolTable currentSymbolTable() {
            return currentCompilationUnit().getSymbolTable();
        }

        @Override
        public CompilationUnit currentCompilationUnit() {
            return unitStack.peek();
        }

        @Override
        public int currentOffset() {
            throw new RuntimeException("method not implemented: currentOffset");
        }

        @Override
        public Address currentAddress() {
            throw new RuntimeException("method not implemented: currentAddress");
        }

        @Override
        public Segment currentSegment() {
            return segmentStack.peek();
        }

        @Override
        public void setSegment(Segment s) {
            segmentStack.pop();
            segmentStack.push(s);
        }

        @Override
        public void writeByte(int value) {
            throw new RuntimeException("method not implemented: writeByte");
        }

        @Override
        public void writeWord(int value) {
            throw new RuntimeException("method not implemented: writeWord");
        }

        @Override
        public void allocateByte() {
            throw new RuntimeException("method not implemented: allocateByte");
        }

        @Override
        public void allocateWord() {
            throw new RuntimeException("method not implemented: allocateWord");
        }

        @Override
        public void allocateBytes(int numberOfBytes) {
            throw new RuntimeException("method not implemented: allocateBytes");
        }

        @Override
        public boolean error(String message, ASTNode node) 
        {
            return message( CompilationMessage.error( currentCompilationUnit() , message , node ) );
        }
        
        public boolean hasReachedMaxErrors() {
            return false;
        }

        @Override
        public boolean message(CompilationMessage msg) {
            messages.add( msg );
            return true;
        }

        @Override
        public IArchitecture getArchitecture() {
            return arch;
        }

        @Override
        public void setStartAddress(int address) {
            throw new RuntimeException("method not implemented: setStartAddress");
        }

        @Override
        public boolean isGenerateRelocations() {
            return false;
        }

        @Override
        public void addRelocation(Relocation reloc) {
        }
    };
    
    @Test
    public void testEmptyString() 
    {
        final Iterator<Token> tokens = lex("");
        assertEquals(  TokenType.EOF  , tokens.next().type );
        assertFalse( tokens.hasNext() );
    }

    @Test
    public void testLexConditionalCompilationNotMatched() 
    {
        final Iterator<Token> tokens = lex("#if 1 > 2\nZ+\n#endif");
        assertToken( TokenType.EOF, "" , 19 , tokens ); 
        if ( tokens.hasNext() ) {
            fail( "Expected EOF but got "+tokens.next() );
        }
    }   

    @Test
    public void testLexConditionalCompilationNotMatchedIfDef() 
    {
        final Iterator<Token> tokens = lex("#ifdef test\nZ+\n#endif");
        assertToken( TokenType.EOF, "" , 21 , tokens ); 
        if ( tokens.hasNext() ) {
            fail( "Expected EOF but got "+tokens.next() );
        }
    }     
    
    @Test public void testInclude1() 
    {
        resFactory = new ResourceFactory() {

            @Override
            public Resource resolveResource(String child) throws IOException {
                throw new RuntimeException("method not implemented: resolveResource");
            }

            @Override
            public Resource resolveResource(Resource parent, String child) throws IOException 
            {
                assertEquals("funky",child);
                return new StringResource("funky", "#message test");
            }
        };
        unitFactory = res -> 
        {
            assertTrue( res instanceof StringResource);
            final String resName = ((StringResource) res).getName();
            assertTrue( "Expected ://funky but got "+resName , resName.endsWith("://funky") );
            return new CompilationUnit( res , fakeContext.currentSymbolTable() );
        };
        
        final Iterator<Token> tokens = lex("#include \"funky\"");
        assertToken( TokenType.EOF, "" , 16 , tokens ); 
        assertEquals(1,messages.size());
        assertEquals("test", messages.get(0).message );
        assertEquals( Severity.INFO , messages.get(0).severity );        
    }
    
    @Test public void testIncludeWithSymbolFromParentScope() 
    {
        resFactory = new ResourceFactory() 
        {
            @Override
            public Resource resolveResource(String child) throws IOException {
                throw new RuntimeException("method not implemented: resolveResource");
            }

            @Override
            public Resource resolveResource(Resource parent, String child) throws IOException 
            {
                assertEquals("funky",child);
                return new StringResource("funky", "#message test");
            }
        };
        unitFactory = res -> 
        {
            assertTrue( res instanceof StringResource);
            final String resName = ((StringResource) res).getName();
            assertTrue( "Expected ://funky but got "+resName , resName.endsWith("://funky") );
            return new CompilationUnit( res , fakeContext.currentSymbolTable() );
        };
        
        final Iterator<Token> tokens = lex("#define test blubb\n#include \"funky\"");
        assertToken( TokenType.EOF, "" , 35 , tokens ); 
        assertEquals(1,messages.size());
        assertEquals("blubb", messages.get(0).message );
        assertEquals( Severity.INFO , messages.get(0).severity );        
    }    

    @Test
    public void testLexConditionalCompilationMatchedIfDef() 
    {
        final Iterator<Token> tokens = lex("#define test\n#ifdef test\nZ+\n#endif");
        assertToken( TokenType.TEXT , "Z" , 25 , tokens ); 
        assertToken( TokenType.OPERATOR , "+" , 26 , tokens ); 
        assertToken( TokenType.EOL , "\n" , 27 , tokens ); 
        assertToken( TokenType.EOF, "" , 34 , tokens ); 
        if ( tokens.hasNext() ) {
            fail( "Expected EOF but got "+tokens.next() );
        }
    }     

    @Test
    public void testLexConditionalCompilationMatched() 
    {
        final Iterator<Token> tokens = lex("#if 2 > 1\nZ+\n#endif");
        assertToken( TokenType.TEXT , "Z" , 10 , tokens ); 
        assertToken( TokenType.OPERATOR , "+" , 11 , tokens ); 
        assertToken( TokenType.EOL , "\n" , 12 , tokens ); 
        assertToken( TokenType.EOF, "" , 19 , tokens ); 
        if ( tokens.hasNext() ) {
            fail( "Expected EOF but got "+tokens.next() );
        }
    }     

    @Test
    public void testLexMismatchEndif() 
    {
        try {
            lex("#if 2 > 1\nZ+\n#endif\n#endif");
            fail("Should've failed");
        } catch(ParseException e) {
            assertTrue("Got: "+e.getMessage() , e.getMessage().contains( "#endif without matching #if" ) );
        }
    } 

    @Test
    public void testLexMismatchIf() 
    {
        try {
            lex("#if 2 > 1\n#if 3 > 4\n#endif");
            fail("Should've failed");
        } catch(ParseException e) {
            assertTrue("Got: "+e.getMessage() , e.getMessage().contains( "Expected 1 more #endif" ) );
        }
    }     

    public void testOnlineNewlines() 
    {
        final Iterator<Token> tokens = lex("\n\n\n");
        assertEquals(  TokenType.EOL, tokens.next().type );
        assertEquals(  TokenType.EOL, tokens.next().type );
        assertEquals(  TokenType.EOL, tokens.next().type );
        assertEquals(  TokenType.EOF  , tokens.next().type );
        assertFalse( tokens.hasNext() );
    }	

    public void testWhitespaceIsIgnored() 
    {
        final Iterator<Token> tokens = lex("a   b");
        assertToken(TokenType.TEXT,"a",0,tokens);
        assertToken(TokenType.TEXT,"b",4,tokens);
        assertToken(TokenType.EOF,"",5,tokens);
        assertFalse( tokens.hasNext() );
    }	

    public void testParseDefineWithNoValue() 
    {
        final Iterator<Token> tokens = lex("#define a");
        assertToken(TokenType.EOF,"",9,tokens);
        assertFalse( tokens.hasNext() );
    }	

    public void testParseDefineWithOneValue() 
    {
        final Iterator<Token> tokens = lex("#define a 42");
        assertToken(TokenType.EOF,"",12,tokens);
        assertFalse( tokens.hasNext() );
    }	

    public void testExpandDefineWithOneValue() 
    {
        final Iterator<Token> tokens = lex("#define a 42\n"
                + "a");
        assertToken(TokenType.DIGITS,"42",13,tokens);
        assertToken(TokenType.EOF,"",14,tokens);
        assertFalse( tokens.hasNext() );
    }	

    public void testMacroBodyIsExpandedOnlyOnce() 
    {
        final Iterator<Token> tokens = lex("#define a a\n"
                + "a");
        assertToken(TokenType.TEXT,"a",12,tokens);
        assertToken(TokenType.EOF,"",13,tokens);
        assertFalse( tokens.hasNext() );
    }	

    public void testMacroBodyIsExpandedRecursively() 
    {
        final Iterator<Token> tokens = lex("#define b c\n"
                + "#define a b\n"
                + "a");
        assertToken(TokenType.TEXT,"c",24,tokens);
        assertToken(TokenType.EOF,"",25,tokens);
        assertFalse( tokens.hasNext() );
    }    

    public void testExpandDefineWithLongValue() 
    {
        final Iterator<Token> tokens = lex("#define a xxxxx\n"
                + "a");
        assertToken(TokenType.TEXT,"xxxxx",16,tokens);
        assertToken(TokenType.EOF,"",17,tokens);
        assertFalse( tokens.hasNext() );
    }	
    
    public void testParseCharacterLiteral() 
    {
        final Iterator<Token> tokens = lex("lsl 'x'\n");
        assertToken(TokenType.TEXT,"lsl",0,tokens);
        assertToken(TokenType.SINGLE_QUOTE,"'",4,tokens);
        assertToken(TokenType.TEXT,"x",5,tokens);
        assertToken(TokenType.SINGLE_QUOTE,"'",6,tokens);
        assertTokenType(TokenType.EOL,7,tokens);
        assertTokenType(TokenType.EOF,8,tokens);
        assertFalse( tokens.hasNext() );
    }   
    
    public void testExpandDefineWithShortValue() 
    {
        final Iterator<Token> tokens = lex("#define TEST X\n"
                + "TEST");
        assertToken(TokenType.TEXT,"X",15,tokens);
        assertToken(TokenType.EOF,"",19,tokens);
        assertFalse( tokens.hasNext() );
    }	

    public void testExpandDefineWithExpression() 
    {
        final Iterator<Token> tokens = lex("#define TEST y+y\n"
                + "TEST");
        assertToken(TokenType.TEXT,"y",17,tokens);
        assertToken(TokenType.OPERATOR,"+",18,tokens);
        assertToken(TokenType.TEXT,"y",19,tokens);
        assertToken(TokenType.EOF,"",21,tokens);
        assertFalse( tokens.hasNext() );
    }	

    public void testExpandMacroWithOneArg() 
    {
        final Iterator<Token> tokens = lex("#define func(x) x+x\n"
                + "func(2)");
        assertToken(TokenType.DIGITS,"2",20,tokens);
        assertToken(TokenType.OPERATOR,"+",21,tokens);
        assertToken(TokenType.DIGITS,"2",22,tokens);
        assertToken(TokenType.EOF,"",27,tokens);
        assertFalse( tokens.hasNext() );
    }	
    
    public void testSymbolGetsExpandedInMessage() 
    {
        final Iterator<Token> tokens = lex("#define test blubb\n#message test");
        assertToken(TokenType.EOF,"",32,tokens);        
        assertEquals(1,messages.size());
        assertEquals("blubb", messages.get(0).message );
        assertEquals( Severity.INFO , messages.get(0).severity );
    }      
    
    public void testMessage() 
    {
        final Iterator<Token> tokens = lex("#message test");
        assertToken(TokenType.EOF,"",13,tokens);        
        assertEquals(1,messages.size());
        assertEquals("test", messages.get(0).message );
        assertEquals( Severity.INFO , messages.get(0).severity );
    }    
    
    public void testWarning() 
    {
        final Iterator<Token> tokens = lex("#warning test");
        assertToken(TokenType.EOF,"",13,tokens);        
        assertEquals(1,messages.size());
        assertEquals("test", messages.get(0).message );
        assertEquals( Severity.WARNING , messages.get(0).severity );
    }  
    
    public void testError() 
    {
        try {
            lex("#error test");
            fail("Should've failed");
        }
        catch(ParseException e) 
        {
            assertEquals("test" , e.getMessage() );
        }
    }     
    
    @Test
    public void testBug() {
        // st x+,r24; 0000:    8d 93
        final Iterator<Token> tokens = lex("st x+,r24; 0000:    8d 93");
        assertToken(TokenType.TEXT,"st",0,tokens);
        assertToken(TokenType.TEXT,"x",3,tokens);
        assertToken(TokenType.OPERATOR,"+",4,tokens);
        assertToken(TokenType.COMMA,",",5,tokens);
        assertToken(TokenType.TEXT,"r24",6,tokens);
        assertToken(TokenType.SEMICOLON,";",9,tokens);
        assertToken(TokenType.DIGITS,"0000",11,tokens);
        assertToken(TokenType.COLON,":",15,tokens);
        assertToken(TokenType.TEXT,"8d",20,tokens);
        assertToken(TokenType.DIGITS,"93",23,tokens);
        assertToken(TokenType.EOF,"",25,tokens);         
    }    

    public void testExpandMacroWithTwoArgs() 
    {
        final Iterator<Token> tokens = lex("#define func(a,b) a+b\n"
                + "func(1,2)");
        assertToken(TokenType.DIGITS,"1",22,tokens);
        assertToken(TokenType.OPERATOR,"+",23,tokens);
        assertToken(TokenType.DIGITS,"2",24,tokens);
        assertToken(TokenType.EOF,"",31,tokens);
        assertFalse( tokens.hasNext() );
    }   

    public void testExpandMacroWithTwoArgsAndWhiteSpace() 
    {
        final Iterator<Token> tokens = lex("#define func(a,b) a + b\n"
                + "func(1,2)");
        assertToken(TokenType.DIGITS,"1",24,tokens);
        assertToken(TokenType.OPERATOR,"+",26,tokens);
        assertToken(TokenType.DIGITS,"2",28,tokens);
        assertToken(TokenType.EOF,"",33,tokens);
        assertFalse( tokens.hasNext() );
    }    
    
    private void assertTokenType(TokenType t,int offset,Iterator<Token> it) 
    {
        final Token tok = it.next();
        if ( tok.type != t || tok.offset != offset ) {
            fail("expected token type "+t+" @ offset "+offset+" but got token type "+tok.type+" @ offset "+tok.offset);
        }
    }
    
    private void assertToken(TokenType t,String value,int offset,Iterator<Token> it) 
    {
        final Token tok = it.next();
        if ( tok.type != t || ! tok.value.equals(value) || tok.offset != offset ) {
            final Token expected=new Token(t,value,offset,-1,-1);
            fail("expected: "+expected+" but got: "+tok);
        }
    }

    private Iterator<Token> lex(String s) 
    {
        final StringResource resource = new StringResource("dummy",s);
        final CompilationUnit unit = new CompilationUnit( resource );
        unitStack.push(unit);
        segmentStack.push(Segment.FLASH);
        
        final LexerImpl delegate = new LexerImpl( new Scanner(resource ) );
        final Lexer lexer = new PreprocessingLexer( delegate , fakeContext );
        final List<Token> result = new ArrayList<>();
        while(true) 
        {
            Token tok = lexer.next();
            result.add( tok );
            if ( tok.is(TokenType.EOF ) ) 
            {
                return new Iterator<Token>() {

                    private final Iterator<Token> wrapped = result.iterator();

                    @Override
                    public boolean hasNext() {
                        return wrapped.hasNext();
                    }

                    @Override
                    public Token next() {
                        final Token result = wrapped.next();
                        System.out.println( result );
                        return result;
                    }
                };
            }
        }
    }
}
