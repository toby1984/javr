package de.codesourcery.javr.assembler.phases;

import java.io.IOException;

import org.apache.commons.lang3.Validate;

import de.codesourcery.javr.assembler.CompilationUnit;
import de.codesourcery.javr.assembler.ICompilationContext;
import de.codesourcery.javr.assembler.parser.Lexer;
import de.codesourcery.javr.assembler.parser.Parser;
import de.codesourcery.javr.assembler.parser.PreprocessingLexer;
import de.codesourcery.javr.assembler.parser.Scanner;
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
        parseSource(context.currentCompilationUnit() , provider.getConfig() );
    }

    public static void parseSource(CompilationUnit unit,IConfig config) throws IOException 
    {
        final Scanner scanner = new Scanner( unit.getResource() );
        
        final Lexer lexer = new PreprocessingLexer(config.createLexer( scanner ) , unit , config.getArchitecture() );
        final Parser parser = config.createParser();
        
        parser.parse( unit , lexer ); // assigns AST to unit as well
    }
}
