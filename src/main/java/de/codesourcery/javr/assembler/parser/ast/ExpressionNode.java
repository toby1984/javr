package de.codesourcery.javr.assembler.parser.ast;

import de.codesourcery.javr.assembler.ICompilationContext;
import de.codesourcery.javr.assembler.parser.TextRegion;

public class ExpressionNode extends AbstractASTNode implements IValueNode , Resolvable {

    private Object value;
    
    public ExpressionNode(TextRegion region) {
        super(region);
    }

    @Override
    protected ExpressionNode createCopy() {
        return new ExpressionNode( getTextRegion().createCopy() );
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