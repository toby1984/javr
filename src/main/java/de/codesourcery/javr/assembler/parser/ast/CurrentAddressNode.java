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