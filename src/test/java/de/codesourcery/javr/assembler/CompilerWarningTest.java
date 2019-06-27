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
        compile( "sts 31,r16" );
        final List<Parser.CompilationMessage> messages =
                compilationUnit.getMessages( false );
        assertTrue( messages.stream().anyMatch( x -> x.message.contains("could use OUT instruction") ) );
    }

    @Test
    public void testInOutCouldBeUsedWarningSTS2() throws IOException
    {
        decorator = config -> config.setWarnIfInOutCanBeUsed( true );
        compile( "sts 32,r16" );
        final List<Parser.CompilationMessage> messages =
                compilationUnit.getMessages( false );
        assertFalse( messages.stream().anyMatch( x -> x.message.contains("could use OUT instruction") ) );
    }

    @Test
    public void testInOutCouldBeUsedWarningLDS() throws IOException
    {
        decorator = config -> config.setWarnIfInOutCanBeUsed( true );
        compile( "lds r16,31" );
        final List<Parser.CompilationMessage> messages =
                compilationUnit.getMessages( false );
        assertTrue( messages.stream().anyMatch( x -> x.message.contains("could use IN instruction") ) );
    }

    @Test
    public void testInOutCouldBeUsedWarningLDS2() throws IOException
    {
        decorator = config -> config.setWarnIfInOutCanBeUsed( true );
        compile( "lds r16,32" );
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
