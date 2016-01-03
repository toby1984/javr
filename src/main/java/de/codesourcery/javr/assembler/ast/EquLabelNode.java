package de.codesourcery.javr.assembler.ast;

import org.apache.commons.lang3.Validate;

import de.codesourcery.javr.assembler.Identifier;
import de.codesourcery.javr.assembler.TextRegion;

public class EquLabelNode extends ASTNode {

    public final Identifier name;

    public EquLabelNode(Identifier name,TextRegion region) {
        super(region);
        Validate.notNull(name, "name must not be NULL");
        this.name = name;
    }    
}
