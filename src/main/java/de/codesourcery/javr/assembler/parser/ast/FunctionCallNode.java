package de.codesourcery.javr.assembler.parser.ast;

import org.apache.commons.lang3.Validate;

import de.codesourcery.javr.assembler.ICompilationContext;
import de.codesourcery.javr.assembler.parser.Identifier;
import de.codesourcery.javr.assembler.parser.OperatorType;
import de.codesourcery.javr.assembler.parser.TextRegion;

public class FunctionCallNode extends ASTNode implements IValueNode {

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
    public boolean resolveValue(ICompilationContext context) 
    {
        this.value = OperatorType.evaluate( this , context.globalSymbolTable() );
        return value != null;
    }    

    @Override
    public Object getValue() {
        return value;
    }
}