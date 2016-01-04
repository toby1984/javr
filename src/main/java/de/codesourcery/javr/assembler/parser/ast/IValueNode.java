package de.codesourcery.javr.assembler.parser.ast;

import de.codesourcery.javr.assembler.ICompilationContext;

public interface IValueNode 
{
    public void resolveValue(ICompilationContext context);
    
    public Object getValue();
}