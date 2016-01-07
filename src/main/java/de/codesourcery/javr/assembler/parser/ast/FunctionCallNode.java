package de.codesourcery.javr.assembler.parser.ast;

import org.apache.commons.lang3.Validate;

import de.codesourcery.javr.assembler.ICompilationContext;
import de.codesourcery.javr.assembler.parser.Identifier;
import de.codesourcery.javr.assembler.parser.TextRegion;

public class FunctionCallNode extends AbstractASTNode implements IValueNode, Resolvable {

    public final Identifier functionName;
    
    private Object value;

    public FunctionCallNode(Identifier functionName,TextRegion region) {
        super(region);
        Validate.notNull(functionName, "functionName must not be NULL");
        this.functionName = functionName;
    }

    @Override
    protected FunctionCallNode createCopy() {
        return new FunctionCallNode(this.functionName , getTextRegion().createCopy() );
    }
    
    @Override
    public boolean resolve(ICompilationContext context) 
    {
        if ( childCount() == 1 ) 
        {
            final IValueNode child = (IValueNode) child(0);
            final String name = functionName.value;
            if ( name.equals("HIGH") || name.equals("LOW" ) ) 
            {
                if ( child instanceof Resolvable) {
                    ((Resolvable) child).resolve( context );
                }
                Number v = (Number) child.getValue();
                if ( v == null ) {
                    return false;
                }
                
                switch ( name ) {
                    case "HIGH": v = ( v.intValue() >>> 8 ) & 0xff; break;
                    case "LOW ": v = v.intValue() & 0xff; break;
                    default: throw new RuntimeException("Unreachable code reached");
                }
                this.value = v;
                return true;
            }
        }
        return false;
    }    

    @Override
    public Object getValue() {
        return value;
    }
}