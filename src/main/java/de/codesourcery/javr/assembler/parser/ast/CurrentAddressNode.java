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

import de.codesourcery.javr.assembler.Address;
import de.codesourcery.javr.assembler.ICompilationContext;
import de.codesourcery.javr.assembler.parser.TextRegion;

public class CurrentAddressNode extends AbstractASTNode implements IValueNode , Resolvable
{
    private Address value;
    
    public CurrentAddressNode(TextRegion r) {
        super(r);
    }
    
    public void setValue(Address value) {
        this.value = value;
    }
    
    @Override
    public Address getValue() {
        return value;
    }

    @Override
    protected CurrentAddressNode createCopy() {
        return new CurrentAddressNode( getTextRegion().createCopy() );
    }
    
    @Override
    public boolean resolve(ICompilationContext context) {
        value = context.currentAddress();
//        new Exception("CurrentAddressNode#resolveValue()").printStackTrace();
//        System.err.println( "resolveValue(): "+getParent().toString()+" => "+value);
        return true;
    }
}