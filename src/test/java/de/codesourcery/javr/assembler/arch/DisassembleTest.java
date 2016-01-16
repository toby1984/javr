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
package de.codesourcery.javr.assembler.arch;

import static org.junit.Assert.*;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;

import org.apache.commons.io.IOUtils;
import org.junit.Test;

import de.codesourcery.javr.assembler.Assembler;
import de.codesourcery.javr.assembler.Buffer;
import de.codesourcery.javr.assembler.CompilationUnit;
import de.codesourcery.javr.assembler.ObjectCodeWriter;
import de.codesourcery.javr.assembler.ResourceFactory;
import de.codesourcery.javr.assembler.Segment;
import de.codesourcery.javr.assembler.arch.IArchitecture.DisassemblerSettings;
import de.codesourcery.javr.assembler.arch.impl.ATMega88;
import de.codesourcery.javr.assembler.parser.Lexer;
import de.codesourcery.javr.assembler.parser.LexerImpl;
import de.codesourcery.javr.assembler.parser.Parser;
import de.codesourcery.javr.assembler.parser.Parser.Severity;
import de.codesourcery.javr.assembler.parser.Scanner;
import de.codesourcery.javr.assembler.util.Resource;
import de.codesourcery.javr.assembler.util.StringResource;
import de.codesourcery.javr.ui.config.IConfig;
import de.codesourcery.javr.ui.config.IConfigProvider;

public class DisassembleTest  {

    private final ATMega88 arch = new ATMega88();
    
    private static final DisassemblerSettings settings = new DisassemblerSettings();
    
    @Test public void testRoundTrip() throws IOException 
    {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        for ( int i = 0 ; i < 256 ; i++ ) 
        {
            for ( int j = 0 ; j < 256 ; j++ ) 
            {
                out.write( (byte) i);
                out.write( (byte) j);
            }
        }
        roundTrip( out.toByteArray() );
    }    
    
    @Test public void testDisassemble8() throws IOException 
    {
        final byte[] bytes = new byte[] { (byte) 0x8d ,(byte) 0x93 };
        roundTrip( bytes , true );
    }
    
    //    f12a: f8 95           spm Z+
    @Test 
    public void testDisassemble9() {
        
        final byte[] bytes = new byte[] { (byte) 0xf8 ,(byte) 0x95 };
        
        final String output = arch.disassemble( bytes , bytes.length , settings );
        System.out.println("Got "+output);
        assertEquals("spm z+" , output );
    }    
    
    // ; disassembled 8192 bytes from /home/tobi/atmel/asm/random.ra
    //  ld r17, z+                                                   | .db 0x11 , 0x91
    
    @Test 
    public void testDisassemble7() {
        
        final byte[] bytes = new byte[] { (byte) 0x11 ,(byte) 0x91 };
        
        final String output = arch.disassemble( bytes , bytes.length , settings );
        System.out.println("Got "+output);
        assertEquals("ld r17,z+" , output );
    }
    
    @Test public void testDisassemble6() {
        
        final byte[] bytes = new byte[] { (byte) 0x44 ,(byte) 0x92 };
        
        final String output = arch.disassemble( bytes , bytes.length , settings );
        System.out.println("Got "+output);
        assertEquals("xch z,r4" , output );
    } 
    
    // 97 91           elpm    r25, Z+
    @Test public void testDisassemble5() {
        
        final byte[] bytes = new byte[] { (byte) 0x97 ,(byte) 0x91 };
        
        final String output = arch.disassemble( bytes , bytes.length , settings );
        System.out.println("Got "+output);
        assertEquals("elpm r25,z+" , output );
    }    
    
    // sts 0x02,r20 ; ; 42 a8 ==>  ldd r4, z+50 
    @Test public void testDisassemble4() {
        
        final byte[] bytes = new byte[] { (byte) 0x42  ,(byte) 0xa8 };
        
        final String output = arch.disassemble( bytes , bytes.length , settings );
        System.out.println("Got "+output);
        assertEquals("ldd r4,z+50" , output );
    }
    
    // call 0x2bdfac  ; 5f 95 ac df ==> needs to be  call 0x57bf58
    @Test public void testDisassemble3() {
        
        final byte[] bytes = new byte[] { (byte) 0x5f ,(byte) 0x95 ,(byte) 0xac ,(byte) 0xdf};
        
        final String output = arch.disassemble( bytes , bytes.length , settings );
        assertEquals("call 0x57bf58" , output );
    }
    
    // 8f 83
    // st y+24,r7 ==> NEEDS TO BE  std y+7, r24
    @Test public void testDisassemble2() {
        
        final byte[] bytes = new byte[] { (byte) 0x8f ,(byte) 0x83 };
        
        final String output = arch.disassemble( bytes , bytes.length , settings );
        assertEquals("std y+7,r24" , output );
    }
    
    /*
1c2c:    ST R24         ; 8d 93
1c2e:    ST R24         ; 8c 93

    */
    @Test public void testDisassembleST() {
        
        final byte[] bytes = new byte[] { (byte) 0x8d, (byte) 0x93 , (byte) 0x8c, (byte) 0x93};
        
        final String output = arch.disassemble( bytes , bytes.length , settings );
        System.out.println( ">>>>>> "+output );
    }
    
    private void roundTrip(byte[] expected) throws IOException 
    {
        roundTrip(expected,false);
    }
    private void roundTrip(byte[] expected,boolean printSource) throws IOException 
    {
        final DisassemblerSettings settings = new DisassemblerSettings();
        settings.printBytes = true;
        settings.printAddresses = true;
        settings.resolveRelativeAddresses=true;
        
        final String source = arch.disassemble( expected , expected.length , settings );
        if ( printSource ) {
            System.out.println("\n===== SOURCE =====\n"+source);
        }
     
        final Assembler asm = new Assembler();
        asm.getCompilerSettings().setFailOnAddressOutOfRange( false );
        
        final CompilationUnit unit = new CompilationUnit( new StringResource("dummy" , source ) );
        final ResourceFactory factory = new ResourceFactory() 
        {
            @Override
            public Resource resolveResource(Resource parent, String child) throws IOException {
                throw new UnsupportedOperationException();
            }

            @Override
            public Resource resolveResource(String child) throws IOException {
                throw new UnsupportedOperationException();
            }
        };
        
        final IConfig config = new IConfig() {
            
            @Override
            public String getEditorIndentString() {
                return "  ";
            }
            
            @Override
            public IArchitecture getArchitecture() {
                return arch;
            }
            
            @Override
            public Parser createParser() {
                return new Parser(arch);
            }
            
            @Override
            public Lexer createLexer(Scanner s) {
                return new LexerImpl( s );
            }
        };
        IConfigProvider configProvider = new IConfigProvider() {
            
            @Override
            public IConfig getConfig() {
                return config;
            }
        };
        System.err.println("Compiling ...");
        System.err.flush();
        final ObjectCodeWriter objectCodeWriter = new ObjectCodeWriter();
        final boolean success = asm.compile(unit , objectCodeWriter, factory , configProvider );
        System.err.println("Compilation finished");
        System.err.flush();
        if ( ! success ) {
            System.err.println("Compilation had errors: \n");
            unit.getMessages(true).stream().filter( msg -> msg.severity == Severity.ERROR ).forEach( msg -> 
            { 
                System.err.println( msg );
                if ( msg.region != null ) 
                {
                    int idx = msg.region.start();
                    int start = idx;
                    while ( idx >= 0 && source.charAt( idx ) != '\n' ) {
                        idx--;
                    }
                    start = idx;
                    idx = msg.region.end();
                    while ( idx < source.length() && source.charAt( idx ) != '\n' ) {
                        idx++;
                    }
                    int end = idx;
                    System.err.println( source.substring( start , end  ) );
                }
            });
            dump(source,expected,null);
            fail("Compilation failed with errors");
        }
        
        final Buffer resource = objectCodeWriter.getBuffer(Segment.FLASH);
        assertNotNull( resource );
        
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        final InputStream in = resource.createInputStream();
        IOUtils.copy( in , out );
        in.close();
        out.close();
        
        final byte[] actual = out.toByteArray();
        try {
            assertArrayEquals(expected,actual);
        } 
        catch(Error e) 
        {
            dump(source,expected,actual);
            e.printStackTrace();
            throw e;
        }
//        assertTrue( "Arrays do not match: \n"+printArrays(expected,actual) , Arrays.equals( expected , actual ) );
    }
    
    private void dump(String source,byte[] expected,byte[] actual) throws IOException 
    {
        System.err.flush();
        System.out.flush();
        
        final File input = new File("source.input.raw");
        final FileOutputStream binWriter = new FileOutputStream(input);
        binWriter.write( expected );
        binWriter.close();
        System.err.println("Input binary written to "+input.getAbsolutePath());

        if ( actual != null ) {
            final File compOut = new File("source.compiled.raw");
            final FileOutputStream binWriter2 = new FileOutputStream(compOut);
            binWriter2.write( actual );
            binWriter2.close();    
            System.err.println("Compiled binary written to "+input.getAbsolutePath());
        }
        
        final File file = new File("source.asm");
        PrintWriter writer = new PrintWriter(file);
        writer.write( source );
        writer.close();

        System.err.println("Source written to "+file.getAbsolutePath()); 
        System.err.flush();
    }
    
//    private String printArrays(byte[] expected,byte[] actual) {
//        
//        final StringBuilder s1 = new StringBuilder();
//        final StringBuilder s2 = new StringBuilder();
//        
//        final int max = Math.max(expected.length,actual.length);
//        for ( int i = 0 ; i < max ; i++ ) 
//        {
//            s1.append("0x");
//            s2.append("0x");
//            if ( i < expected.length ) {
//                s1.append( StringUtils.leftPad( Integer.toHexString( expected[i] & 0xff ) , 2 , '0' ) ).append(" ");
//            } else {
//                s1.append("   ");
//            }
//            if ( i < actual.length ) {
//                s2.append( StringUtils.leftPad( Integer.toHexString( actual[i] & 0xff ) , 2 , '0' ) ).append(" ");
//            } else {
//                s2.append("   ");
//            }            
//        }
//        return "Expected: "+s1+"\n"+
//               "Actual  : "+s2;
//    }
}
