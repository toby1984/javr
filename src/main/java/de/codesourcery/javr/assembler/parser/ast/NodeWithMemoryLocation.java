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

import de.codesourcery.javr.assembler.Address;
import de.codesourcery.javr.assembler.parser.TextRegion;

public abstract class NodeWithMemoryLocation extends AbstractASTNode 
{
    private Address memoryLocation;
    
    public NodeWithMemoryLocation() {
        super();
    }

    public NodeWithMemoryLocation(TextRegion region) {
        super(region);
    }

    @Override
    public final Address getMemoryLocation() throws IllegalStateException 
    {
        if ( ! hasMemoryLocation() ) {
            throw new IllegalStateException( "This statement is not associated with a memory location" );
        }
        return memoryLocation;
    }
    
    @Override
    public final boolean assignMemoryLocation(Address address) 
    {
        Validate.notNull(address, "address must not be NULL");
        if ( ! hasMemoryLocation() ) {
            throw new IllegalStateException( "This statement is not associated with a memory location" );
        }        
        final boolean result = ! address.equals( this.memoryLocation );
        this.memoryLocation = address;
        return result;
    }

    @Override
    public abstract boolean hasMemoryLocation();
    
    @Override
    public abstract int getSizeInBytes() throws IllegalStateException;    
}