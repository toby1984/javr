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
package de.codesourcery.javr.assembler.parser.ast;

import org.apache.commons.lang3.Validate;

import de.codesourcery.javr.assembler.parser.Identifier;
import de.codesourcery.javr.assembler.parser.TextRegion;
import de.codesourcery.javr.assembler.symbols.Symbol;

/**
 * Children:
 * 
 * child (0): ArgumentNamesNode
 * child (1): Either node is either a single expression or the AST of the function/macro body
 * 
 * @author tobias.gierke@code-sourcery.de
 */
public class FunctionDefinitionNode extends AbstractASTNode 
{
    public final Symbol.Type type;
    public final Identifier name;
    
    public FunctionDefinitionNode(Identifier name,Symbol.Type type,TextRegion region) {
        super(region);
        
        Validate.notNull(name, "name must not be NULL");
        Validate.notNull(type, "type must not be NULL");
        this.name = name;
        this.type = type;
    }
    
    public int getArgumentCount() 
    {
        return getArgumentNames().childCount();
    }
    
    public boolean hasArguments() {
        return getArgumentCount() > 0;
    }

    @Override
    protected FunctionDefinitionNode createCopy() {
        return new FunctionDefinitionNode( this.name , this.type , getTextRegion().createCopy() );
    }
    
    public ArgumentNamesNode getArgumentNames() 
    {
        return (ArgumentNamesNode) child(0); 
    }
    
    public ASTNode getBody() {
        return child(1);
    }
}
