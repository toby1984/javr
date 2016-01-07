package de.codesourcery.javr.assembler.phases;

import de.codesourcery.javr.assembler.ICompilationContext;

public interface Phase 
{
    public default void beforeRun(ICompilationContext ctx) {
    }
    
    public default void afterSuccessfulRun(ICompilationContext context) {
        
    }
    
    public String getName();
    
    public void run(ICompilationContext context) throws Exception;
    
    public default boolean stopOnErrors() {
        return true;
    }
}
