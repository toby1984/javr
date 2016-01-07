package de.codesourcery.javr.assembler.parser.ast;

import org.apache.commons.lang3.Validate;

import de.codesourcery.javr.assembler.parser.Identifier;
import de.codesourcery.javr.assembler.parser.TextRegion;

public class IdentifierDefNode extends AbstractASTNode 
{
    public final Identifier name;
    
    public IdentifierDefNode(Identifier name,TextRegion r) {
        super(r);
        Validate.notNull(name, "name must not be NULL");
        this.name = name;
    }
    
    @Override
    protected IdentifierDefNode createCopy() {
        return new IdentifierDefNode( this.name , getTextRegion().createCopy() );
    }

}
