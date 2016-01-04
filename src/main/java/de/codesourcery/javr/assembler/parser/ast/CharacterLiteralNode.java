package de.codesourcery.javr.assembler.parser.ast;

import de.codesourcery.javr.assembler.parser.TextRegion;

public class CharacterLiteralNode extends ASTNode implements IValueNode {

    public final char value;

    public CharacterLiteralNode(char value, TextRegion region) {
        super(region);
        this.value = value;
    }

    @Override
    public Integer getValue() 
    {
        return Integer.valueOf( value );
    }

    @Override
    protected CharacterLiteralNode createCopy() 
    {
        return new CharacterLiteralNode( this.value , getTextRegion().createCopy() );
    }      
}
