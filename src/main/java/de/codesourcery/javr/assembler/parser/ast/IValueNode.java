package de.codesourcery.javr.assembler.parser.ast;

import de.codesourcery.javr.assembler.ICompilationContext;

public interface IValueNode 
{
    public default boolean resolveValue(ICompilationContext context) {
        return true;
    }
    
    public Object getValue();
}