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
import de.codesourcery.javr.assembler.parser.ast.AST;
import de.codesourcery.javr.assembler.util.Resource;
import de.codesourcery.javr.ui.IConfig;
import de.codesourcery.javr.ui.IConfigProvider;

public class ParseSourcePhase implements Phase {

    private final IConfigProvider provider;
    
    public ParseSourcePhase(IConfigProvider provider) 
    {
        Validate.notNull(provider, "provider must not be NULL");
        this.provider = provider;
    }
    
    @Override
    public String getName() {
        return "parse";
    }
    
    @Override
    public void run(ICompilationContext context) throws IOException 
    {
        context.currentCompilationUnit().setAst( parseSource(context.currentCompilationUnit().getResource() , provider.getConfig() ) );
    }

    public static AST parseSource(Resource resource,IConfig config) throws IOException 
    {
        final String input;
        try ( ByteArrayOutputStream out = new ByteArrayOutputStream() ; InputStream in = resource.createInputStream() ) 
        {
            IOUtils.copy( in , out );
            input = new String( out.toByteArray() );
        }
        final Scanner scanner = new Scanner( input );
        
        final Lexer lexer = config.createLexer( scanner );
        final Parser parser = config.createParser();

        return parser.parse( lexer );
    }
}
