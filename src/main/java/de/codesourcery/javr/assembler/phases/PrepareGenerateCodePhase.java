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

import org.apache.log4j.Logger;

import de.codesourcery.javr.assembler.ICompilationContext;
import de.codesourcery.javr.assembler.parser.ast.AST;
import de.codesourcery.javr.assembler.parser.ast.ASTNode;
import de.codesourcery.javr.assembler.parser.ast.ASTNode.IASTVisitor;
import de.codesourcery.javr.assembler.parser.ast.ASTNode.IIterationContext;
import de.codesourcery.javr.assembler.parser.ast.IValueNode;
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
            visitNode( context , stmt, fakeCtx ); 
            stmt.resolve( context ); 
            stmt.children().forEach( c -> generateCode( context , c, fakeCtx ) ); 
        }
        
        if ( context.hasReachedMaxErrors() ) {
            return;
        }
        
        // sanity check
        context.globalSymbolTable().getAllSymbolsUnsorted().stream().filter( Symbol::isUnresolved ).forEach( symbol -> 
        {
            if ( ! symbol.hasType( Type.PREPROCESSOR_MACRO ) ) // preprocessor macros/defines are special since "#define something" is a valid statement and needs to value/body
            {
                context.error("Unresolved symbol '"+symbol.name()+"'",symbol.getNode());
            }
        });   
        
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
            stmt.visitDepthFirst(visitor);
        }        
    }
}