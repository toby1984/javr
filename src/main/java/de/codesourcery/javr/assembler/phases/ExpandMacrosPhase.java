package de.codesourcery.javr.assembler.phases;

import de.codesourcery.javr.assembler.CompilationUnit;
import de.codesourcery.javr.assembler.ICompilationContext;
import de.codesourcery.javr.assembler.parser.Identifier;
import de.codesourcery.javr.assembler.parser.Parser.CompilationMessage;
import de.codesourcery.javr.assembler.parser.ast.AST;
import de.codesourcery.javr.assembler.parser.ast.FunctionDefinitionNode;
import de.codesourcery.javr.assembler.parser.ast.IdentifierNode;
import de.codesourcery.javr.assembler.symbols.Symbol.Type;

public class ExpandMacrosPhase extends AbstractPhase 
{
    public ExpandMacrosPhase()
    {
        super("macro-expansion",true);
    }

    @Override
    public void run(ICompilationContext context) throws Exception 
    {
        final CompilationUnit unit = context.currentCompilationUnit();
        final AST ast = unit.getAST();

        final boolean[] macrosExpanded = {false};
        do 
        {
            macrosExpanded[0] = false;
            ast.visitDepthFirst( (node , ictx ) -> 
            {
                // TODO: Implement expanding macros that take parameters
                if ( node instanceof IdentifierNode) 
                {
                    final Identifier name = ((IdentifierNode) node).name;
                    unit.getSymbolTable().maybeGet( name ).ifPresent( symbol -> 
                    {
                        if ( symbol.getValue() != null && symbol.hasType( Type.PREPROCESSOR_MACRO ) ) 
                        {
                            final FunctionDefinitionNode macroDefinition = (FunctionDefinitionNode) symbol.getValue();
                            if ( macroDefinition.hasArguments() ) 
                            {
                                unit.addMessage( CompilationMessage.error( unit , "Macro takes "+macroDefinition.getArgumentCount()+" parameters" , node ) );
                            }
                            macrosExpanded[0] = true;
                            node.replaceWith( macroDefinition.getBody().createCopy( true ) );
                        }
                    });
                }
            });
        } 
        while ( macrosExpanded[0] );
    }
}
