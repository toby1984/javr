package de.codesourcery.javr.assembler.parser.ast;

import org.apache.commons.lang3.Validate;

import de.codesourcery.javr.assembler.parser.Identifier;
import de.codesourcery.javr.assembler.parser.TextRegion;

public class FunctionDefinitionNode extends ASTNode 
{
    public final Identifier name;
    
    public FunctionDefinitionNode(Identifier name,TextRegion region) {
        super(region);
        
        Validate.notNull(name, "name must not be NULL");
        this.name = name;
    }
    
    public int getArgumentCount() {
        return ((ArgumentNamesNode) child(0) ).childCount();
    }
    
    public boolean hasArguments() {
        return getArgumentCount() > 0;
    }

    @Override
    protected FunctionDefinitionNode createCopy() {
        return new FunctionDefinitionNode( this.name , getTextRegion().createCopy() );
    }
}
