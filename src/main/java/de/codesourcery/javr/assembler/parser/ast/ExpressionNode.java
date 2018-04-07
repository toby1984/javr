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
package de.codesourcery.javr.assembler.parser.ast;

import de.codesourcery.javr.assembler.ICompilationContext;

public class ExpressionNode extends AbstractASTNode implements IValueNode , Resolvable {

    private Object value;
    
    public ExpressionNode() {
        super();
    }

    @Override
    protected ExpressionNode createCopy() 
    {
        final ExpressionNode result = new ExpressionNode();
        // TODO: Maybe value needs to be deep-copied here instead ? ....
        result.value = value;
        return result;
    }
    
    @Override
    public boolean resolve(ICompilationContext context) 
    {
        boolean result = true;
        if ( hasChildren() ) 
        {
            if ( child(0) instanceof Resolvable) 
            {
                result &= ((Resolvable) child(0)).resolve( context );
            }
            this.value = ((IValueNode) child(0)).getValue();
        }
        return result;
    }
    
    @Override
    public Object getValue() {
        return value;
    }
}