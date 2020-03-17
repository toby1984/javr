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
package de.codesourcery.javr.assembler.arch;

import de.codesourcery.javr.assembler.AbstractCompilerTest;
import de.codesourcery.javr.assembler.parser.Parser;
import de.codesourcery.javr.ui.config.ProjectConfiguration;
import org.junit.Assert;

import java.io.IOException;
import java.util.List;

public class CompilerTest extends AbstractCompilerTest
{
    public void testLDI() throws IOException
    {
        final String src = "ldi r16,0xff";
        final byte[] expected = new byte[] { 0x0f, (byte) 0xef };
        byte[] actual = compile( src+"\n" ); // TODO: newline appended to work around NPE caused by PreprocessingLexer popping the last compilationUnit too soon

        Assert.assertArrayEquals(expected, actual );
        assertEquals(src, disassemble( actual ) );
    }

    public void testSER() throws IOException
    {
        final String src = "ser r16";
        final byte[] expected = new byte[] { 0x0f, (byte) 0xef };
        byte[] actual = compile( src+"\n" ); // TODO: newline appended to work around NPE caused by PreprocessingLexer popping the last compilationUnit too soon

        Assert.assertArrayEquals(expected, actual );
        assertEquals("ldi r16,0xff", disassemble( actual ) );
    }

    public void testSERWithAdditionalArgFails() throws IOException
    {
        final String src = "ser r16,0xff";
        final byte[] expected = new byte[] { 0x0f, (byte) 0xef };

        try
        {
            compile( src + "\n" ); // TODO: newline appended to work around NPE caused by PreprocessingLexer popping the last compilationUnit too soon
            fail("Should've failed");
        }
        catch(CompilationFailedException e)
        {
            final List<Parser.CompilationMessage> msg = compilationUnit.getMessages( true );
            assertEquals(1, msg.size() );
            assertEquals( Parser.Severity.ERROR , msg.get(0).severity );
            assertEquals("SER accepts only one argument", msg.get(0).message );
        }
    }

    public void testSERWithWrongRegisterFails() throws IOException
    {
        final String src = "ser r0";
        final byte[] expected = new byte[] { 0x0f, (byte) 0xef };

        try
        {
            compile( src + "\n" ); // TODO: newline appended to work around NPE caused by PreprocessingLexer popping the last compilationUnit too soon
            fail("Should've failed");
        }
        catch(CompilationFailedException e)
        {
            final List<Parser.CompilationMessage> msg = compilationUnit.getMessages( true );
            assertEquals(1, msg.size() );
            assertEquals( Parser.Severity.ERROR , msg.get(0).severity );
            assertEquals("Operand needs to be R16...R31", msg.get(0).message );
        }
    }

    @Override
    protected void decorateProjectConfiguration(ProjectConfiguration configuration)
    {
        configuration.setOutputFormat( ProjectConfiguration.OutputFormat.RAW );
    }
}