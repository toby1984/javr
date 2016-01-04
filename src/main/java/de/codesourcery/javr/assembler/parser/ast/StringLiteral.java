package de.codesourcery.javr.assembler.parser.ast;

import org.apache.commons.lang3.Validate;

import de.codesourcery.javr.assembler.parser.TextRegion;

public class StringLiteral extends ASTNode implements IValueNode
{
    public final String value;

    public StringLiteral(String value, TextRegion region) 
    {
        super(region);
        
        Validate.notNull(value, "value must not be NULL");
        this.value = value;
    }

    @Override
    protected StringLiteral createCopy() {
        return new StringLiteral(value,getTextRegion().createCopy());
    }

    @Override
    public String getValue() {
        return value;
    }
}