package de.codesourcery.javr.assembler.parser.ast;

import de.codesourcery.javr.assembler.ICompilationContext;
import de.codesourcery.javr.assembler.parser.TextRegion;

public class ExpressionNode extends ASTNode implements IValueNode {

    private Object value;
    
    public ExpressionNode(TextRegion region) {
        super(region);
    }

    @Override
    protected ExpressionNode createCopy() {
        return new ExpressionNode( getTextRegion().createCopy() );
    }
    
    @Override
    public boolean resolveValue(ICompilationContext context) 
    {
        boolean result = ((IValueNode) child(0)).resolveValue( context );
        if ( result ) {
            value = ((IValueNode) child(0)).getValue();
        }
        return false;
    }

    @Override
    public Object getValue() {
        return value;
    }

}
