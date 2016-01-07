package de.codesourcery.javr.assembler.parser.ast;

import de.codesourcery.javr.assembler.ICompilationContext;

public interface Resolvable extends ASTNode
{
    /**
     * Resolve this node and any of its children.
     * 
     * Note that this method will recursively resolve all child nodes
     * so there's no need to do this again.
     *  
     * @param context
     * @return
     */
    public boolean resolve(ICompilationContext context);
}
