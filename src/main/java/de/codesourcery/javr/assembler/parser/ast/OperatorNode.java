package de.codesourcery.javr.assembler.parser.ast;

import org.apache.commons.lang3.Validate;

import de.codesourcery.javr.assembler.ICompilationContext;
import de.codesourcery.javr.assembler.parser.OperatorType;
import de.codesourcery.javr.assembler.parser.TextRegion;

public class OperatorNode extends ASTNode implements IValueNode
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
    public boolean resolveValue(ICompilationContext context) 
    {
        this.value = OperatorType.evaluate( this , context.getSymbolTable() );
        return value != null;
    }

    @Override
    public Object getValue() {
        return value;
    }
}
