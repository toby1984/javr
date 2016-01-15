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

/**
 * Uses the preprocessor lexer to turn the source file(s) into an abstract syntax tree (AST).
 * 
 * Note that the parser performs as little semantic checks as possible so that an AST can be 
 * produced even in the presence of semantic errors. This is necessary so that an AST
 * for syntax highlighting is still available.
 * 
 * The AST will be associated with the current compilation unit.
 *
 * @author tobias.gierke@code-sourcery.de
 */
public class ParseSourcePhase implements Phase 
{
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
        final CompilationUnit unit = context.currentCompilationUnit();
        final Scanner scanner = new Scanner( unit.getResource() );
//        final Scanner scanner = new SimpleScanner( unit.getResource() );
        
        final IConfig config = provider.getConfig();
        final Lexer lexer = new PreprocessingLexer( config.createLexer( scanner ) , context );
//        final Lexer lexer = config.createLexer( scanner );
        final Parser parser = config.createParser();
        
        parser.parse( unit , lexer ); // assigns AST to unit as well
    }
}
