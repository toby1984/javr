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

import de.codesourcery.javr.assembler.parser.Parser;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.List;
import java.util.function.Consumer;

public class CompilerWarningTest extends AbstractCompilerTest
{
    private Consumer<ICompilationContext.ICompilerSettings> decorator;

    @Before
    public void before() {
        decorator = x -> {};
    }

    @Test
    public void testInOutCouldBeUsedWarningSTS() throws IOException
    {
        decorator = config -> config.setWarnIfInOutCanBeUsed( true );
        compile( "sts 63,r16" );
        final List<Parser.CompilationMessage> messages =
                compilationUnit.getMessages( false );
        assertTrue( messages.stream().anyMatch( x -> x.message.contains("could use OUT instruction") ) );
    }

    @Test
    public void testInOutCouldBeUsedWarningSTS2() throws IOException
    {
        decorator = config -> config.setWarnIfInOutCanBeUsed( true );
        compile( "sts 64,r16" );
        final List<Parser.CompilationMessage> messages =
                compilationUnit.getMessages( false );
        assertFalse( messages.stream().anyMatch( x -> x.message.contains("could use OUT instruction") ) );
    }

    @Test
    public void testInOutCouldBeUsedWarningLDS() throws IOException
    {
        decorator = config -> config.setWarnIfInOutCanBeUsed( true );
        compile( "lds r16,63" );
        final List<Parser.CompilationMessage> messages =
                compilationUnit.getMessages( false );
        assertTrue( messages.stream().anyMatch( x -> x.message.contains("could use IN instruction") ) );
    }

    @Test
    public void testInOutCouldBeUsedWarningLDS2() throws IOException
    {
        decorator = config -> config.setWarnIfInOutCanBeUsed( true );
        compile( "lds r16,64" );
        final List<Parser.CompilationMessage> messages =
                compilationUnit.getMessages( false );
        assertFalse( messages.stream().anyMatch( x -> x.message.contains("could use IN instruction") ) );
    }

    @Override
    protected void decorateCompilerSettings(CompilerSettings settings)
    {
        super.decorateCompilerSettings( settings );
    }
}
