package de.codesourcery.javr.assembler.symbols;

import de.codesourcery.javr.assembler.parser.Identifier;
import de.codesourcery.javr.assembler.parser.ast.DirectiveNode;

public class EquSymbol extends Symbol<DirectiveNode,Object> 
{
    public EquSymbol(Identifier name,DirectiveNode node) 
    {
        super(name, Type.EQU, node);
    }
}