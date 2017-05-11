/**
 * Copyright 2015 Tobias Gierke <tobias.gierke@code-sourcery.de>
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
                            symbol.markAsReferenced();
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
