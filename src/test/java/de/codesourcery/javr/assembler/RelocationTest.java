package de.codesourcery.javr.assembler;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import java.util.stream.Collectors;

import de.codesourcery.javr.assembler.arch.Architecture;
import de.codesourcery.javr.assembler.elf.Relocation;
import de.codesourcery.javr.assembler.util.HexDump;
import de.codesourcery.javr.assembler.util.Resource;
import de.codesourcery.javr.assembler.util.StringResource;
import de.codesourcery.javr.ui.Project;
import de.codesourcery.javr.ui.config.ProjectConfiguration;
import de.codesourcery.javr.ui.config.ProjectConfiguration.OutputFormat;
import junit.framework.TestCase;

public class RelocationTest extends TestCase
{
    private CompilationUnit root;
    private ObjectCodeWriter objectCodeWriter;
    
    public void testNonRelocatable1() throws IOException 
    {
        compile("ldi r16,10");
        printDebug();
        assertTextSegmentEquals( 0x0a , 0xe0 ); // ldi r16,10
        
        assertTrue( objectCodeWriter.getRelocations( Segment.FLASH ).isEmpty() );
    }
    
    public void testNonRelocatable2() throws IOException 
    {
        compile("ldi r16,end-start\n"+
                ".dseg\n"
                + "start: .dw 1\n"
                + "end: .dw 2\n");
        printDebug();
        assertTextSegmentEquals(0x02,0xe0 ); 
        assertDataSegmentEquals(0x01,0x00,0x02,0x00); 
        
        assertNoOtherRelocationsExceptIn(Segment.FLASH);
        assertTrue( objectCodeWriter.getRelocations( Segment.FLASH ).isEmpty() );
    }    
    
    public void testRelocatable1() throws IOException 
    {
        compile("rcall test\n"+
                "test:\n");
        printDebug();
        assertTextSegmentEquals( 0x00 , 0xd0 ); // ldi r16,10
        assertNoOtherRelocationsExceptIn(Segment.FLASH);
        assertEquals( 1, objectCodeWriter.getRelocations( Segment.FLASH ).size() );
        assertRelocation(Segment.FLASH , Relocation.Kind.R_AVR_13_PCREL , 0 , 2 );            
    }    
    
    public void testRelocatable2() throws IOException 
    {
        compile("rcall test+2\n"+
                "test:\n");
        printDebug();
        assertTextSegmentEquals( 0x00 , 0xd0 ); // ldi r16,10
        assertNoOtherRelocationsExceptIn(Segment.FLASH);
        assertEquals( 1, objectCodeWriter.getRelocations( Segment.FLASH ).size() );
        assertRelocation(Segment.FLASH , Relocation.Kind.R_AVR_13_PCREL , 0 , 2 );        
    }   
    
    public void testRelocatable3() throws IOException 
    {
        compile(
                "ldi r31,HIGH(test)\n"+
                "ldi r30,LOW(test)\n"+
                ".dseg\n"+
                "test:\n");
        
        printDebug();
        assertTextSegmentEquals( 0xf0, 0xe0, 0xe0, 0xe0 ); // ldi r16,10
        assertEquals( 2, objectCodeWriter.getRelocations( Segment.FLASH ).size() );
        assertNoOtherRelocationsExceptIn(Segment.FLASH);
        assertRelocation(Segment.FLASH , Relocation.Kind.R_AVR_HI8_LDI , 0 , 0 );
        assertRelocation(Segment.FLASH , Relocation.Kind.R_AVR_LO8_LDI , 2 , 0 );
    }  
    
    public void testRelocatable4() throws IOException 
    {
        // FIXME: Addend calculation is currently borked, should yield 10
        compile(
                "ldi r31,HIGH(test+10)\n"+
                "ldi r30,LOW(test+10)\n"+
                ".dseg\n"+
                "test:\n");
        
        printDebug();
        assertTextSegmentEquals( 0xf0, 0xe0, 0xe0, 0xe0 ); // ldi r16,10
        assertEquals( 2, objectCodeWriter.getRelocations( Segment.FLASH ).size() );
        assertNoOtherRelocationsExceptIn(Segment.FLASH);
        assertRelocation(Segment.FLASH , Relocation.Kind.R_AVR_HI8_LDI , 0 , 10);
        assertRelocation(Segment.FLASH , Relocation.Kind.R_AVR_LO8_LDI , 2 , 10 );
    }     
    
    public void testRelocatable5() throws IOException 
    {
        compile(
                ".dseg\n"+
                "test: .dw cmd1,cmd2\n"+
                "cmd1: .dw 1\n"+
                "cmd2: .dw 1\n"
                );
        
        printDebug();
        assertDataSegmentEquals( 0x00,0x00,0x00,0x00,0x01,0x00,0x01,0x00 ); 
        assertEquals( 2, objectCodeWriter.getRelocations( Segment.SRAM).size() );
        assertNoOtherRelocationsExceptIn(Segment.SRAM);
        assertRelocation(Segment.SRAM , Relocation.Kind.R_AVR_16 , 0 , 4);
        assertRelocation(Segment.SRAM, Relocation.Kind.R_AVR_16 , 2 , 6 );        
    }
    
    public void testRelocatable6() throws IOException 
    {
        compile(
                "ldi r31,0x10\n"+
                "brcc skip\n"+
                "clr r31\n"+
                "skip:\n");
        
        printDebug();
        assertTextSegmentEquals( 0xf0,0xe1,0x00,0xf4,0xff,0x27 ); // ldi r16,10
        assertEquals( 1, objectCodeWriter.getRelocations( Segment.FLASH ).size() );
        assertNoOtherRelocationsExceptIn(Segment.FLASH);
        assertRelocation(Segment.FLASH , Relocation.Kind.R_AVR_7_PCREL , 2 , 6);
    }      
    
    public void testRelocatable7() throws IOException 
    {
        compile( "ldi r31,0x10\n" + 
                 "call skip\n" + 
                 "clr r31\n" + 
                 "skip:\n"); 
        
        printDebug();
        assertTextSegmentEquals( 0xf0,0xe1,0x0e,0x94,0x00,0x00,0xff,0x27 ); // ldi r16,10
        assertEquals( 1, objectCodeWriter.getRelocations( Segment.FLASH ).size() );
        assertNoOtherRelocationsExceptIn(Segment.FLASH);
        assertRelocation(Segment.FLASH , Relocation.Kind.R_AVR_CALL , 2 , 8);
    }    
    
    public void testRelocatable8() throws IOException 
    {
        compile( "ldi r16,skip\n" + 
                 "skip:\n"); 
        
        printDebug();
        assertTextSegmentEquals( 0x00,0xe0 ); // ldi r16,10
        assertEquals( 1, objectCodeWriter.getRelocations( Segment.FLASH ).size() );
        assertNoOtherRelocationsExceptIn(Segment.FLASH);
        assertRelocation(Segment.FLASH , Relocation.Kind.R_AVR_LDI , 0 , 2);
    }    

    public void testRelocatable9() throws IOException 
    {
        // avr-as compiles this to a R_AVR_8 relocation but later chokes with "relocation truncated to fit: R_AVR_8 against `no symbol'"
        try {
        compile(
                ".dseg\n"+
                "test: .db cmd1,cmd2\n"+
                "cmd1: .dw 1\n"+
                "cmd2: .dw 1\n"
                );
        fail("Should've failed");
        } catch(Exception e) {
            // ok
        }
    }    
    
    public void testRelocatable10() throws IOException 
    {
        // FIXME: Currently finds no relocations, should return 2
        compile(
                ".dseg\n"+
                "test: .db HIGH(cmd1),LOW(cmd2)\n"+
                "cmd1: .dw 1\n"+
                "cmd2: .dw 1\n"
                );
        
        printDebug();
        assertDataSegmentEquals( 0x00,0x04,0x01,0x00,0x01,0x00 ); 
        assertEquals( 2, objectCodeWriter.getRelocations( Segment.SRAM).size() );
        assertNoOtherRelocationsExceptIn(Segment.SRAM);
        assertRelocation(Segment.SRAM , Relocation.Kind.R_AVR_8_HI8 , 0 , 2);
        assertRelocation(Segment.SRAM, Relocation.Kind.R_AVR_8_LO8 , 1 , 4 );             
    }      
    
    public void testRelocatable11() throws IOException 
    {
        compile( "lds r16,test\n" + 
                ".dseg\n" + 
                "test: .dw 1\n"); 
        printDebug();
        assertTextSegmentEquals(0x00,0x91,0x00,0x00 ); 
        assertDataSegmentEquals(0x01,0x00);
        assertEquals( 1, objectCodeWriter.getRelocations(Segment.FLASH).size() );
        assertNoOtherRelocationsExceptIn(Segment.FLASH);
        assertRelocation(Segment.FLASH , Relocation.Kind.R_AVR_16 , 2 , 0);
    }
    
    public void testRelocatable12() throws IOException 
    {
        // FIXME: Works in avr-as
        compile( "ldi r16,LOW(-(test))\n" + 
                ".dseg\n" + 
                "test: .dw 1\n"); 
        printDebug();
        assertTextSegmentEquals(0x00,0xe0);
        assertDataSegmentEquals(0x01,0x00);
        assertEquals( 1, objectCodeWriter.getRelocations(Segment.FLASH).size() );
        assertNoOtherRelocationsExceptIn(Segment.FLASH);
        assertRelocation(Segment.FLASH , Relocation.Kind.R_AVR_LO8_LDI_NEG , 2 , 0);
    }  
    
    public void testRelocatable13() throws IOException 
    {
        // FIXME: Works in avr-as        
        compile( "ldi r16,HIGH(-(test))\n" + 
                ".dseg\n" + 
                "test: .dw 1\n"); 
        printDebug();
        assertTextSegmentEquals(0x00,0xe0);
        assertDataSegmentEquals(0x01,0x00);
        assertEquals( 1, objectCodeWriter.getRelocations(Segment.FLASH).size() );
        assertNoOtherRelocationsExceptIn(Segment.FLASH);
        assertRelocation(Segment.FLASH , Relocation.Kind.R_AVR_HI8_LDI_NEG , 2 , 0);
    }    
    
    public void testRelocatable14() throws IOException 
    {
        // FIXME: Works in avr-as        
        compile( "ldi r16,HIGH(test >> 1)\n" + 
                ".dseg\n" + 
                "test: .dw 1\n"); 
        printDebug();
        assertTextSegmentEquals(0x00,0xe0);
        assertDataSegmentEquals(0x01,0x00);
        assertEquals( 1, objectCodeWriter.getRelocations(Segment.FLASH).size() );
        assertNoOtherRelocationsExceptIn(Segment.FLASH);
        assertRelocation(Segment.FLASH , Relocation.Kind.R_AVR_HI8_LDI_PM , 2 , 0);
    }   
    
    public void testRelocatable15() throws IOException 
    {
        // FIXME: Works in avr-as        
        compile( "ldi r16,LOW(test >> 1)\n" + 
                ".dseg\n" + 
                "test: .dw 1\n"); 
        printDebug();
        assertTextSegmentEquals(0x00,0xe0);
        assertDataSegmentEquals(0x01,0x00);
        assertEquals( 1, objectCodeWriter.getRelocations(Segment.FLASH).size() );
        assertNoOtherRelocationsExceptIn(Segment.FLASH);
        assertRelocation(Segment.FLASH , Relocation.Kind.R_AVR_LO8_LDI_PM , 2 , 0);
    }     
    
    public void testRelocatable16() throws IOException 
    {
        // FIXME: Passes with avr-as
        compile( "ldi r16,HIGH( -(test >> 1))\n" +  // ldi r16,hi8(-(pm(test)))
                ".dseg\n" + 
                "test: .dw 1\n"); 
        printDebug();
        assertTextSegmentEquals(0x00,0xe0);
        assertDataSegmentEquals(0x01,0x00);
        assertEquals( 1, objectCodeWriter.getRelocations(Segment.FLASH).size() );
        assertNoOtherRelocationsExceptIn(Segment.FLASH);
        assertRelocation(Segment.FLASH , Relocation.Kind.R_AVR_HI8_LDI_PM_NEG , 2 , 0);
    }     
    
    public void testRelocatable17() throws IOException 
    {
        // FIXME: Passes with avr-as        
        compile( "ldi r16,LOW( -(test >> 1))\n" + // ldi r16,lo8(-(pm(test)))
                ".dseg\n" + 
                "test: .dw 1\n"); 
        printDebug();
        assertTextSegmentEquals(0x00,0xe0);
        assertDataSegmentEquals(0x01,0x00);
        assertEquals( 1, objectCodeWriter.getRelocations(Segment.FLASH).size() );
        assertNoOtherRelocationsExceptIn(Segment.FLASH);
        assertRelocation(Segment.FLASH , Relocation.Kind.R_AVR_LO8_LDI_PM_NEG , 2 , 0);
    }      
    
    public void testRelocatable18() throws IOException 
    {
        // FIXME: Works in avr-as        
        compile(
                ".dseg\n"+
                "test: .dw cmd2>>1\n"+
                "cmd1: .dw 1\n"+
                "cmd2: .dw 1\n"
                );
        
        printDebug();
        assertDataSegmentEquals( 0x00,0x00,0x01,0x00,0x01,0x00 ); 
        assertEquals( 1, objectCodeWriter.getRelocations( Segment.SRAM).size() );
        assertNoOtherRelocationsExceptIn(Segment.SRAM);
        assertRelocation(Segment.SRAM, Relocation.Kind.R_AVR_16_PM , 0 , 2 );             
    }  
    
    public void testRelocatable19() throws IOException 
    {
        // avr-ld fails with
        // relocation truncated to fit: R_AVR_6_ADIW against `no symbol'
        // FIXME: Make sure we fail with a truncation-related error message her
        compile( "main:\n" + 
                "        adiw r31:r30,cmd1\n" + 
                ".dseg\n" + 
                "test: .dw 1\n" + 
                "cmd1: .dw 2\n" + 
                "cmd2: .dw 3\n");
        printDebug();
        assertTextSegmentEquals(0x30,0x96);
        assertDataSegmentEquals(0x01,0x00,0x02,0x00,0x03,0x00);
        assertEquals( 1, objectCodeWriter.getRelocations(Segment.FLASH).size() );
        assertNoOtherRelocationsExceptIn(Segment.FLASH);
        assertRelocation(Segment.FLASH , Relocation.Kind.R_AVR_6_ADIW  , 0 , 2);
    }     
    
    private void assertNoOtherRelocationsExceptIn(Segment segment)
    {
        for ( Segment s : Segment.values() ) {
            final List<Relocation> relocs = objectCodeWriter.getRelocations( s );
            if ( s != segment && ! relocs.isEmpty() ) {
                fail("Expected to find no relocations except for segment "+segment+" but got "+relocs);
            }
        }
    }
    
    private void assertRelocation(Segment segment,Relocation.Kind kind,int locationOffset , int addend) 
    {
        Relocation match = null;
        final List<Relocation> relocs = objectCodeWriter.getRelocations( segment );
        
        final Runnable details = () -> {
            System.out.flush();
            System.err.println("Relocations:\n"+relocs.stream().map( r -> r.toString() ).collect( Collectors.joining("\n" ) ) );
        };
        
        for ( Relocation rel : relocs ) {
            if ( rel.locationOffset == locationOffset ) {
                if ( match != null ) {
                    details.run();
                    throw new IllegalStateException("More than one relocation for offset "+locationOffset+" ??");
                }
                match = rel;
            }
        }
        if ( match == null ) {
            details.run();
            throw new RuntimeException("Found no relocation at offset "+locationOffset);
        }
        if ( match.kind != kind ) 
        {
            details.run();
            fail( "Expected kind "+kind+" but was "+match.kind);
        }
        if ( addend != match.addend ) {
            details.run();
            assertEquals( "Expected addend "+addend+" but was "+match.addend, addend , match.addend );
        }
    }
    
    private void assertTextSegmentEquals(int byte0,int... moreBytes) throws IOException 
    {
        assertSegmentEquals(Segment.FLASH,byte0,moreBytes);
    }
    
    private void assertDataSegmentEquals(int byte0,int... moreBytes) throws IOException 
    {
        assertSegmentEquals(Segment.SRAM,byte0,moreBytes);
    }    
    
    private void assertSegmentEquals(Segment segment,int byte0,int... moreBytes) throws IOException 
    {
        final int len = 1 + ( ( moreBytes != null ) ? moreBytes.length : 0 );
        final byte[] expected = new byte[ len ];
        expected[0] = (byte) byte0;
        if ( moreBytes != null ) {
            for ( int i = 0 ; i < moreBytes.length ; i++ ) {
                expected[1+i]= (byte) moreBytes[i];
            }
        }
        final byte[] actual = objectCodeWriter.getBuffer( segment ).toByteArray();
        assertEquals( "Segment "+segment+" size mismatch, expected "+expected.length+" bytes but got "+actual.length,expected.length, actual.length );
        
        for ( int i = 0 ; i < len ; i++ ) {
            if ( expected[i] != actual[i] ) {
                fail("Mismatch in "+segment+" segment: Expected 0x"+Integer.toHexString( expected[i] & 0xff )+" but got 0x"+Integer.toHexString( actual[i] & 0xff )+" at offset "+i);
            }
        }
    }
    
    private void printDebug() throws IOException 
    {
        for ( Segment type : Segment.values() ) 
        {
            final Buffer buffer = objectCodeWriter.getBuffer( type );
            System.out.println( " ============== "+type+" segment: "+buffer.size()+" bytes =========");
            if ( ! buffer.isEmpty() ) 
            {
                final byte[] data = buffer.toByteArray();
                System.out.println( new HexDump().byteDelimiter(",").bytePrefix("0x").hexDump(0 , data , data.length ) );
                final OutputStream out = new OutputStream() {
                    @Override
                    public void write(int b) throws IOException {
                        System.out.write( b );
                    }
                };
                new Disassembler().disassemble(new ByteArrayInputStream(data), out );
                System.out.println();
            }
        }        
    }
    
    private void compile(String s) throws IOException 
    {
        final Assembler asm = new Assembler();
        
        final ResourceFactory resourceFactory = new ResourceFactory() {
            
            @Override
            public Resource resolveResource(Resource parent, String child) throws IOException {
                throw new RuntimeException("method not implemented: resolveResource");
            }
            
            @Override
            public Resource resolveResource(String child) throws IOException {
                throw new RuntimeException("method not implemented: resolveResource");
            }
        };
        
        objectCodeWriter = new ObjectCodeWriter();
        
        final CompilationUnit root = new CompilationUnit( new StringResource( "in-memory" , s ) );
        final ProjectConfiguration projConfig = new ProjectConfiguration();
        projConfig.setArchitecture( Architecture.ATMEGA328P );
        projConfig.setBaseDir( new File("/tmp") );
        
        final CompilerSettings compilerSettings = new CompilerSettings();
        projConfig.setCompilerSettings( compilerSettings );
        projConfig.setOutputFormat(OutputFormat.ELF_RELOCATABLE);
        
        final Project project = new Project(root,projConfig);
        
        if ( ! asm.compile(project , objectCodeWriter, resourceFactory , project) ) {
            throw new RuntimeException("Compilation failed: "+root.getMessages( true ).stream().map( m -> m.message ).collect( Collectors.joining(",") ) );
        }
    }
}