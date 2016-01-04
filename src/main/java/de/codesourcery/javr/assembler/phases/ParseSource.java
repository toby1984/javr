package de.codesourcery.javr.assembler.phases;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.Validate;

import de.codesourcery.javr.assembler.ICompilationContext;
import de.codesourcery.javr.assembler.parser.Lexer;
import de.codesourcery.javr.assembler.parser.Parser;
import de.codesourcery.javr.assembler.parser.Scanner;
import de.codesourcery.javr.assembler.util.Resource;
import de.codesourcery.javr.ui.IConfig;
import de.codesourcery.javr.ui.IConfigProvider;

public class ParseSource implements Phase {

    private final IConfigProvider provider;
    
    public ParseSource(IConfigProvider provider) 
    {
        Validate.notNull(provider, "provider must not be NULL");
        this.provider = provider;
    }
    
    @Override
    public void run(ICompilationContext context) throws IOException 
    {
        final Resource resource = context.getCompilationUnit().getResource();
        
        final String input;
        try ( ByteArrayOutputStream out = new ByteArrayOutputStream() ; InputStream in = resource.createInputStream() ) 
        {
            IOUtils.copy( in , out );
            input = new String( out.toByteArray() );
        }
        final Scanner scanner = new Scanner( input );
        
        final IConfig config = provider.getConfig();
        final Lexer lexer = config.createLexer( scanner );
        final Parser parser = config.createParser();

        context.getCompilationUnit().setAst( parser.parse( lexer ) );
    }

}
