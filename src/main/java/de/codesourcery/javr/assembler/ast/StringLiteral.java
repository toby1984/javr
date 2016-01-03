package de.codesourcery.javr.assembler.ast;

import org.apache.commons.lang3.Validate;

import de.codesourcery.javr.assembler.ICompilationContext;
import de.codesourcery.javr.assembler.TextRegion;

public class StringLiteral extends ASTNode {

    public final String value;

    public StringLiteral(String value, TextRegion region) 
    {
        super(region);
        
        Validate.notNull(value, "value must not be NULL");
        this.value = value;
    }
}
