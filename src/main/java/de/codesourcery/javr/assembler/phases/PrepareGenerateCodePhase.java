/**
 * Copyright 2015-2018 Tobias Gierke <tobias.gierke@code-sourcery.de>
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

import java.util.Optional;

import org.apache.log4j.Logger;

import de.codesourcery.javr.assembler.ICompilationContext;
import de.codesourcery.javr.assembler.parser.Identifier;
import de.codesourcery.javr.assembler.parser.ast.AST;
import de.codesourcery.javr.assembler.parser.ast.ASTNode;
import de.codesourcery.javr.assembler.parser.ast.ASTNode.IASTVisitor;
import de.codesourcery.javr.assembler.parser.ast.ASTNode.IIterationContext;
import de.codesourcery.javr.assembler.parser.ast.DirectiveNode;
import de.codesourcery.javr.assembler.parser.ast.IValueNode;
import de.codesourcery.javr.assembler.parser.ast.IdentifierNode;
import de.codesourcery.javr.assembler.parser.ast.LabelNode;
import de.codesourcery.javr.assembler.parser.ast.Resolvable;
import de.codesourcery.javr.assembler.parser.ast.StatementNode;
import de.codesourcery.javr.assembler.symbols.Symbol;
import de.codesourcery.javr.assembler.symbols.Symbol.Type;

/**
 * Performs any necessary preparations so that the code generation phase can be executed.
 *
 * @author tobias.gierke@code-sourcery.de
 */
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
        
        final IIterationContext<Object> fakeCtx = new IIterationContext<Object>() 
        {
            @Override
            public void stop() { };
            
            @Override
            public void dontGoDeeper() {}

            @Override
            public void stop(Object value) {
            }

            @Override
            public Object getResult() {
                return null;
            };
        };
        
        for ( ASTNode child : ast.children() ) 
        {
            final StatementNode stmt = (StatementNode) child;
            visitNode( context , stmt, fakeCtx ); 
            try {
                stmt.resolve( context );
            } catch(Exception e) {
                if ( ! context.error( e.getMessage() , stmt ) ) {
                    return;
                }
            }
            stmt.children().forEach( c -> generateCode( context , c, fakeCtx ) ); 
        }
        
        if ( context.hasReachedMaxErrors() ) {
            return;
        }
        
        // check unresolved labels to see if they maybe refer to a local label
        final IASTVisitor labelVisitor = new IASTVisitor() 
        {
            private LabelNode previousGlobalLabel;
            
            public void visit(ASTNode node,IIterationContext<?> ctx) 
            {
                if ( node instanceof DirectiveNode )
                {
                    switch( ((DirectiveNode) node).directive ) 
                    {
                        case CSEG:
                            previousGlobalLabel = null; // local labels cannot belong to a global label from a different segment 
                            break;
                        case DSEG:
                            previousGlobalLabel = null; // local labels cannot belong to a global label from a different segment 
                            break;
                        case ESEG:
                            previousGlobalLabel = null; // local labels cannot belong to a global label from a different segment 
                            break;
                        default:
                            // $$FALL-THROUGH$$
                    }
                    ctx.dontGoDeeper();
                } 
                else if ( node instanceof LabelNode) 
                {
                    if ( ((LabelNode) node).isGlobal() ) {
                        previousGlobalLabel = (LabelNode) node;
                    }
                }
                else if ( node instanceof IdentifierNode) 
                {
                    final IdentifierNode id = (IdentifierNode) node;
                    final Optional<Symbol> symbol = context.globalSymbolTable().maybeGet( id.name );
                    if ( ! symbol.isPresent() || symbol.get().isUndefined() ) // this node refers to a symbol that is missing or undefined 
                    {
                        if ( previousGlobalLabel != null ) 
                        {
                            final Identifier localVar = Identifier.newLocalGlobalIdentifier( previousGlobalLabel.identifier , id.name );
                            if ( context.globalSymbolTable().isDefined( localVar ) ) 
                            {
                                // rewrite IdentifierNode to refer to local label, 
                                // and remove the global symbol that was created when we
                                // didn't know that this is actually a local label
                                final Symbol badGlobalSymbol = id.getSymbol();
                                id.setName( localVar );
                                final Symbol localSymbol = context.globalSymbolTable().get( localVar , Type.ADDRESS_LABEL );
                                localSymbol.markAsReferenced();
                                id.setSymbol( localSymbol );
                                context.globalSymbolTable().removeSymbol( badGlobalSymbol );
                            }
                        }
                    }
                }
            }
        };
        ast.visitBreadthFirst(labelVisitor);
        
        // now try again resolving any IValueNode instances
        // that do not yield a value yet
        // This is necessary to deal with function nodes that had
        // forward references in their expressions
        // TODO: This feels very much like a hack...have a look at this again !!!
        final IASTVisitor visitor = (node,ictx) -> 
        {
            if ( node instanceof IValueNode && ((IValueNode) node).getValue() == null ) 
            {
                if ( node instanceof Resolvable ) 
                {
                    ((Resolvable) node).resolve( context );
                }
            }
        };
        for ( ASTNode child : ast.children() ) 
        {
            final StatementNode stmt = (StatementNode) child;
            try {
                stmt.visitDepthFirst(visitor);
            } catch(Exception e) {
                if ( ! context.error( e.getMessage() , stmt ) ) {
                    return;
                }
            }
        }   
        
        // Check for unresolved symbols
        final IASTVisitor unresolvedSymbolsVisitor = (node,ictx) -> 
        {
            if ( node instanceof IdentifierNode)
            {
                final IdentifierNode ln = (IdentifierNode) node;
                if ( ln.getValue() == null ) 
                {
                    if ( ! context.error("Unresolved symbol '"+ln.name+"'' with symbol "+ln.getSymbol(),node) ) {
                        ictx.stop();
                    }                    
                }
            }
        };
        ast.visitBreadthFirst( unresolvedSymbolsVisitor );
    }
}