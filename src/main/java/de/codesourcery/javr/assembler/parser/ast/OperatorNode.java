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

import org.apache.commons.lang3.Validate;

import de.codesourcery.javr.assembler.ICompilationContext;
import de.codesourcery.javr.assembler.parser.OperatorType;
import de.codesourcery.javr.assembler.parser.TextRegion;

public class OperatorNode extends AbstractASTNode implements IValueNode , Resolvable
{
    public OperatorType type;
    private Object value;

    public OperatorNode(OperatorType type,TextRegion region) 
    {
        super(region);
        Validate.notNull(type, "type must not be NULL");
        this.type= type;
    }

    @Override
    protected OperatorNode createCopy() {
        return new OperatorNode(this.type , getTextRegion().createCopy() );
    }
    
    public ASTNode lhs() {
        return child(0);
    }
    
    public ASTNode rhs() {
        return child(0);
    }    
    
    public OperatorType getOperatorType() {
        return type;
    }
    
    public boolean hasAllOperands() 
    {
        return childCount() == type.getArgumentCount();
    }
    
    public void setType(OperatorType type) {
        this.type = type;
    }
    
    @Override
    public boolean resolve(ICompilationContext context) 
    {
        this.value = OperatorType.evaluate( this , context , false );
        return value != null;
    }

    @Override
    public Object getValue() {
        return value;
    }
    
    @Override
    public String toString() 
    {
        return "Operator: "+this.type.getSymbol()+"( "+children()+")";
    }
    
    @Override
    public boolean isOperatorNode() {
        return true;
    }
    
    @Override
    public boolean isOperator(OperatorType type) 
    {
        return type.equals( this.type );
    }    
}