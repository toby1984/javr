package de.codesourcery.javr.assembler.parser.ast;

import org.apache.commons.lang3.Validate;

import de.codesourcery.javr.assembler.parser.Identifier;
import de.codesourcery.javr.assembler.parser.TextRegion;
import de.codesourcery.javr.assembler.symbols.Symbol;

/**
 * Children:
 * 
 * child (0): ArgumentNamesNode
 * child (1): Either node is absent , a single expression or the AST of the function/macro body
 * 
 * @author tobias.gierke@code-sourcery.de
 */
public class FunctionDefinitionNode extends AbstractASTNode 
{
    public final Symbol.Type type;
    public final Identifier name;
    
    public FunctionDefinitionNode(Identifier name,Symbol.Type type,TextRegion region) {
        super(region);
        
        Validate.notNull(name, "name must not be NULL");
        Validate.notNull(type, "type must not be NULL");
        this.name = name;
        this.type = type;
    }
    
    public int getArgumentCount() {
        return ((ArgumentNamesNode) child(0) ).childCount();
    }
    
    public boolean hasArguments() {
        return getArgumentCount() > 0;
    }

    @Override
    protected FunctionDefinitionNode createCopy() {
        return new FunctionDefinitionNode( this.name , this.type , getTextRegion().createCopy() );
    }
}
