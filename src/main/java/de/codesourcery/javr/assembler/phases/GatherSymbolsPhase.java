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

import java.util.List;

import de.codesourcery.javr.assembler.ICompilationContext;
import de.codesourcery.javr.assembler.exceptions.DuplicateSymbolException;
import de.codesourcery.javr.assembler.parser.Identifier;
import de.codesourcery.javr.assembler.parser.Parser.CompilationMessage;
import de.codesourcery.javr.assembler.parser.ast.AST;
import de.codesourcery.javr.assembler.parser.ast.ASTNode;
import de.codesourcery.javr.assembler.parser.ast.ASTNode.IASTVisitor;
import de.codesourcery.javr.assembler.parser.ast.ASTNode.IIterationContext;
import de.codesourcery.javr.assembler.parser.ast.DirectiveNode;
import de.codesourcery.javr.assembler.parser.ast.DirectiveNode.Directive;
import de.codesourcery.javr.assembler.parser.ast.EquLabelNode;
import de.codesourcery.javr.assembler.parser.ast.FunctionDefinitionNode;
import de.codesourcery.javr.assembler.parser.ast.IdentifierNode;
import de.codesourcery.javr.assembler.parser.ast.LabelNode;
import de.codesourcery.javr.assembler.symbols.Symbol;
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
    public void run(ICompilationContext context) {

        final AST ast = context.currentCompilationUnit().getAST();
        
        final IASTVisitor visitor = new IASTVisitor() 
        {
            @Override
            public void visit(ASTNode node, IIterationContext ctx) 
            {
                visitNode( context , node , ctx );
                
                if ( node instanceof DirectiveNode ) 
                {
                    final DirectiveNode dn = (DirectiveNode) node;
                    if ( dn.is( Directive.EQU ) ) 
                    {
                        final EquLabelNode label = (EquLabelNode) dn.firstChild();
                        declareSymbol(context, label.name );
                    }
                } 
                else if ( node instanceof FunctionDefinitionNode ) 
                {
                    final FunctionDefinitionNode func =(FunctionDefinitionNode) node;
                    if ( ! defineSymbol( func , new Symbol(func.name,Symbol.Type.PREPROCESSOR_MACRO , context.currentCompilationUnit() , func ) ) ) {
                        ctx.stop();
                    }
                } 
                else if ( node instanceof IdentifierNode) 
                {
                    final Identifier identifier = ((IdentifierNode) node).name;
                    declareSymbol(context, identifier);
                } 
                else if ( node instanceof LabelNode ) 
                {
                    final LabelNode label = (LabelNode) node;
                    final Identifier identifier;
                    if ( label.isGlobal() ) {
                        identifier = label.identifier;
                    } else {
                        identifier = Identifier.newLocalGlobalIdentifier( previousGlobalLabel.identifier , label.identifier );
                    }
                    final Symbol symbol = new Symbol( identifier , Symbol.Type.ADDRESS_LABEL , context.currentCompilationUnit() , label );
                    label.setSymbol( symbol );
                    if ( ! defineSymbol( label , symbol ) ) 
                    {
                        ctx.stop();
                    }
                } 
            }

            private void declareSymbol(ICompilationContext context,final Identifier identifier) 
            {
                context.currentSymbolTable().declareSymbol( identifier , context.currentCompilationUnit() );
            }

            private boolean defineSymbol(ASTNode node,final Symbol symbol) throws DuplicateSymbolException 
            {
                try {
                    context.currentSymbolTable().defineSymbol( symbol );
                } 
                catch(DuplicateSymbolException e) 
                {
                    return context.message( CompilationMessage.error( context.currentCompilationUnit() , "Duplicate symbol: "+symbol.name() ,node) );
                }
                return true;
            }
        };
        
        // gather symbols
        ast.visitBreadthFirst( visitor );
        
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