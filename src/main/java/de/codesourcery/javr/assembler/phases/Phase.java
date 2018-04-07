/**
 * Copyright 2015-2018 Tobias Gierke <tobias.gierke@code-sourcery.de>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
