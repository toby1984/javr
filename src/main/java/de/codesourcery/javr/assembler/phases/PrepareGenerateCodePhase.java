package de.codesourcery.javr.assembler.phases;

import org.apache.log4j.Logger;

import de.codesourcery.javr.assembler.ICompilationContext;
import de.codesourcery.javr.assembler.parser.ast.AST;
import de.codesourcery.javr.assembler.parser.ast.ASTNode;
import de.codesourcery.javr.assembler.parser.ast.ASTNode.IIterationContext;
import de.codesourcery.javr.assembler.parser.ast.StatementNode;
import de.codesourcery.javr.assembler.symbols.Symbol;
import de.codesourcery.javr.assembler.symbols.Symbol.Type;

public class PrepareGenerateCodePhase extends GenerateCodePhase
{
    @SuppressWarnings("unused")
    private static final Logger LOG = Logger.getLogger(PrepareGenerateCodePhase.class);
    
    public PrepareGenerateCodePhase() 
    {
        super("prepare_generate_code",true);
    }
    
    @Override
    public void run(ICompilationContext context) throws Exception
    {
        final AST ast = context.currentCompilationUnit().getAST();
        
        final IIterationContext fakeCtx = new IIterationContext() 
        {
            @Override
            public void stop() { };
            
            @Override
            public void dontGoDeeper() {};
        };
        
        for ( ASTNode child : ast.children() ) 
        {
            final StatementNode stmt = (StatementNode) child;
            if ( visitNode( context , stmt, fakeCtx ) ) 
            {
                stmt.resolve( context ); 
                stmt.children().forEach( c -> generateCode( context , c, fakeCtx ) ); 
            }
        }
        
        // sanity check
        context.globalSymbolTable().getAllSymbolsUnsorted().stream().filter( Symbol::isUnresolved ).forEach( symbol -> 
        {
            if ( ! symbol.hasType( Type.PREPROCESSOR_MACRO ) ) // preprocessor macros/defines are special since "#define something" is a valid statement and needs to value/body
            {
                context.error("Unresolved symbol '"+symbol.name()+"'",symbol.getNode());
            }
        });                 
    }
}