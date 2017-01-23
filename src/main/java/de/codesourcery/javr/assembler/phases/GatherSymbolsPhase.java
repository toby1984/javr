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

import de.codesourcery.javr.assembler.ICompilationContext;
import de.codesourcery.javr.assembler.parser.Identifier;
import de.codesourcery.javr.assembler.parser.Parser.CompilationMessage;
import de.codesourcery.javr.assembler.symbols.Symbol.Type;

/**
 * Compiler phase that is responsible for gathering all the symbols from the source code
 * so that forward references work properly.
 *
 * @author tobias.gierke@code-sourcery.de
 */
public class GatherSymbolsPhase extends AbstractPhase
{
    public GatherSymbolsPhase() {
        super("gather_symbols",true);
    }
    
    @Override
    public void run(ICompilationContext context) 
    {
        // sanity check that local variable names do not clash with any globals
        context.globalSymbolTable().visitSymbols( (symbol) -> 
        {
            if ( symbol.hasType( Type.ADDRESS_LABEL ) ) 
            {
                if ( Identifier.isLocalGlobalIdentifier( symbol.name() ) ) 
                {
                    final Identifier localPart = Identifier.getLocalIdentifierPart( symbol.name() );
                    if ( context.globalSymbolTable().isDefined( localPart ) ) 
                    {
                        if ( ! context.message( CompilationMessage.error( context.currentCompilationUnit() , "Local label '"+localPart.value+"' clashes with like-named global label" ,symbol.getNode()) ) ) {
                            return Boolean.FALSE;
                        }
                    }
                }
            }            
            return Boolean.TRUE;
        });
    }
}