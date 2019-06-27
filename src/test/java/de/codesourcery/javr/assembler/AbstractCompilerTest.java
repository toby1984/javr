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

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileAttribute;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import org.junit.After;
import org.junit.Before;

import de.codesourcery.javr.assembler.arch.Architecture;
import de.codesourcery.javr.assembler.elf.Relocation;
import de.codesourcery.javr.assembler.util.HexDump;
import de.codesourcery.javr.assembler.util.Resource;
import de.codesourcery.javr.assembler.util.StringResource;
import de.codesourcery.javr.ui.Project;
import de.codesourcery.javr.ui.config.ProjectConfiguration;
import de.codesourcery.javr.ui.config.ProjectConfiguration.OutputFormat;
import junit.framework.TestCase;

public abstract class AbstractCompilerTest extends TestCase
{
    private final long time = System.currentTimeMillis();
    private final AtomicLong index = new AtomicLong(0);
    
    private final List<File> toDelete = new ArrayList<>();
    private File tmpDir;
    
    protected CompilationUnit compilationUnit;
    protected ObjectCodeWriter objectCodeWriter;    
    protected Project project;
    
    protected ResourceFactory resourceFactory;
    
    protected final File createTmpDir() throws IOException 
    {
        final File dir = new File( tmpDir , "dir_"+time+"_"+index.incrementAndGet());
        Files.createDirectories( dir.toPath() );
        toDelete.add(dir);
        return dir;
    }
    @Before
    @Override
    protected void setUp() throws Exception 
    {
        final File parent = new File( System.getProperty("java.io.tmpdir") );
        tmpDir = new File( parent, "inbintest_"+time+"_"+index.incrementAndGet());
        if ( ! tmpDir.mkdirs() ) {
            throw new IOException("Failed to create temp dir "+tmpDir.getAbsolutePath());
        }
        toDelete.add( tmpDir );
        
        resourceFactory = new ResourceFactory() {
            
            @Override
            public Resource resolveResource(Resource parent, String child) throws IOException {
                throw new RuntimeException("method not implemented: resolveResource(Resource,String)");
            }
            
            @Override
            public Resource resolveResource(String child) throws IOException {
                throw new RuntimeException("method not implemented: resolveResource(String)");
            }
        };
    }
    
    @After
    @Override
    protected void tearDown() 
    {
        try {
            for ( File f : toDelete ) 
            {
                try {
                    delete( f );
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        } finally {
            toDelete.clear();
        }
    }    
    
    protected final File createFile(String name,String content) throws FileNotFoundException, IOException {
        return createFile(name,content.getBytes());
    }
    
    protected final File createFile(String name,byte[] content) throws FileNotFoundException, IOException 
    {
        return writeFile( new File( tmpDir, name ), content );
    }
    
    protected final File writeFile(File f,byte[] data) throws FileNotFoundException, IOException {
        try ( FileOutputStream out = new FileOutputStream( f ) ) {
            out.write( data );
        }
        return f;
    }
    
    protected final void delete(File file) throws IOException 
    {
        if ( ! file.exists() ) {
            return;
        }
        Path directory = file.toPath();
        Files.walkFileTree(directory, new SimpleFileVisitor<Path>() {
           @Override
           public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
               Files.delete(file);
               return FileVisitResult.CONTINUE;
           }

           @Override
           public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
               Files.delete(dir);
               return FileVisitResult.CONTINUE;
           }
        });        
    }    
    
    protected void assertNoOtherRelocationsExceptIn(Segment segment)
    {
        for ( Segment s : Segment.values() ) {
            final List<Relocation> relocs = objectCodeWriter.getRelocations( s );
            if ( s != segment && ! relocs.isEmpty() ) {
                fail("Expected to find no relocations except for segment "+segment+" but got "+relocs);
            }
        }
    }
    
    protected void assertRelocation(Segment segment,Relocation.Kind kind,int locationOffset , int addend) 
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
    
    protected void assertTextSegmentEquals(int byte0,int... moreBytes) throws IOException 
    {
        assertSegmentEquals(Segment.FLASH,byte0,moreBytes);
    }
    
    protected void assertDataSegmentEquals(int byte0,int... moreBytes) throws IOException 
    {
        assertSegmentEquals(Segment.SRAM,byte0,moreBytes);
    }    
    
    protected void assertSegmentEquals(Segment segment,int byte0,int... moreBytes) throws IOException 
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
    
    protected void printDebug() throws IOException 
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
    
    protected void compile(String s) throws IOException 
    {
        compile( new StringResource( "in-memory" , s ) );
    }
    
    protected void compile(Resource source) throws IOException 
    {
        final Assembler asm = new Assembler();
        
        objectCodeWriter = new ObjectCodeWriter();
        
        compilationUnit = new CompilationUnit(source);
        final ProjectConfiguration projConfig = new ProjectConfiguration();
        projConfig.setArchitecture( Architecture.ATMEGA328P );
        projConfig.setBaseDir( new File("/tmp") );
        
        final CompilerSettings compilerSettings = new CompilerSettings();
        decorateCompilerSettings( compilerSettings );
        projConfig.setCompilerSettings( compilerSettings );
        projConfig.setOutputFormat(OutputFormat.ELF_RELOCATABLE);
        
        project = new Project(compilationUnit,projConfig);
        
        if ( ! asm.compile(project , objectCodeWriter, resourceFactory , project) ) {
            throw new RuntimeException("Compilation failed: "+compilationUnit.getMessages( true ).stream().map( m -> m.message ).collect( Collectors.joining(",") ) );
        }
    }

    protected void decorateCompilerSettings( CompilerSettings settings) {

    }
}