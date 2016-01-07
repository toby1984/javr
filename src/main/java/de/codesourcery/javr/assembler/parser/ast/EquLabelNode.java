package de.codesourcery.javr.assembler.parser.ast;

import org.apache.commons.lang3.Validate;

import de.codesourcery.javr.assembler.parser.Identifier;
import de.codesourcery.javr.assembler.parser.TextRegion;

public class EquLabelNode extends AbstractASTNode {

    public final Identifier name;

    public EquLabelNode(Identifier name,TextRegion region) {
        super(region);
        Validate.notNull(name, "name must not be NULL");
        this.name = name;
    }    
    
    @Override
    protected EquLabelNode createCopy() 
    {
        return new EquLabelNode( this.name , getTextRegion().createCopy() );
    }       
}
