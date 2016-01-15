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