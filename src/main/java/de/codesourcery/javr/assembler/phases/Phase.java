package de.codesourcery.javr.assembler.phases;

import de.codesourcery.javr.assembler.ICompilationContext;

public interface Phase 
{
    public void run(ICompilationContext context) throws Exception;
    
    public default boolean stopOnErrors() {
        return true;
    }
}
