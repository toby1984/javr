package de.codesourcery.javr.assembler.phases;

import de.codesourcery.javr.assembler.ICompilationContext;

/**
 * A compiler phase.
 *
 * @author tobias.gierke@code-sourcery.de
 */
public interface Phase 
{
    /**
     * Invoked once before this phase's {@link #run(ICompilationContext)} method is called.
     * 
     * @param ctx
     */
    public default void beforeRun(ICompilationContext ctx) {
    }
    
    /**
     * Invoked after this phase's {@link #run(ICompilationContext)} method returned and
     * the current compilation unit showed no errors.
     * 
     * @param ctx
     */    
    public default void afterSuccessfulRun(ICompilationContext context) {
    }

    /**
     * Returns a human-readable name for this phase.
     * @return
     */
    public String getName();
    
    /**
     * Run this phase.
     * 
     * @param context
     * @throws Exception
     */
    public void run(ICompilationContext context) throws Exception;
}
