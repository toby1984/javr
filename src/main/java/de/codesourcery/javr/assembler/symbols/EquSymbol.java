package de.codesourcery.javr.assembler.symbols;

import de.codesourcery.javr.assembler.parser.Identifier;
import de.codesourcery.javr.assembler.parser.ast.EquNode;

public class EquSymbol extends Symbol<EquNode> 
{
    private Object value;
    
    public EquSymbol(Identifier name,EquNode node) 
    {
        super(name, Type.EQU, node);
    }
    
    public Object getValue() {
        return value;
    }
    
    public void setValue(Object value) {
        this.value = value;
    }
}