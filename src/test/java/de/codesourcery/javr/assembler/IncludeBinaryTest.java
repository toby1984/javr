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

import static org.junit.Assert.assertArrayEquals;

import java.io.File;
import java.io.IOException;

import org.junit.Test;

import de.codesourcery.javr.assembler.util.FileResourceFactory;
import de.codesourcery.javr.assembler.util.Resource;

public class IncludeBinaryTest extends AbstractCompilerTest {

    @Test
    public void testIncludeBinaryAbsolutePath() throws IOException
    {
        final byte[] data = new byte[] { 0x01,0x02,0x03 };

        final File dir = createTmpDir();
        final File sourceFile = new File( dir, "source.asm");
        final File binary = writeFile( new File( dir, "test.bin") , data );
        final String src = "#incbin \""+binary.getAbsolutePath()+"\"";
        writeFile(sourceFile, src.getBytes() );
        
        resourceFactory = FileResourceFactory.createInstance(new File("/")); // test will break on Windoze...
        
        compile( Resource.file( sourceFile ) );
        
        final Buffer buffer = objectCodeWriter.getBuffer(Segment.FLASH);
        assertEquals(3,buffer.size());
        final byte[] written = buffer.toByteArray();
        assertArrayEquals(data,written);
    }
    
    @Test
    public void testIncludeBinaryRelativePath() throws IOException
    {
        final byte[] data = new byte[] { 0x01,0x02,0x03 };

        final File dir = createTmpDir();
        final File sourceFile = new File( dir, "source.asm");
        final File binary = writeFile( new File( dir, "test.bin") , data );
        final String src = "#incbin \""+binary.getName()+"\"";
        writeFile(sourceFile, src.getBytes() );
        
        resourceFactory = FileResourceFactory.createInstance(dir); // test will break on Windoze...
        
        compile( Resource.file( sourceFile ) );
        
        final Buffer buffer = objectCodeWriter.getBuffer(Segment.FLASH);
        assertEquals(3,buffer.size());
        final byte[] written = buffer.toByteArray();
        assertArrayEquals(data,written);
    }    
}
