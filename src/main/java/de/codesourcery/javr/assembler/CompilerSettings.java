/**
 * Copyright 2015 Tobias Gierke <tobias.gierke@code-sourcery.de>
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
package de.codesourcery.javr.assembler;

import de.codesourcery.javr.assembler.ICompilationContext.ICompilationSettings;

public class CompilerSettings implements ICompilationSettings {

    private int maxErrors = 100;
    
    private boolean failOnAddressOutOfRange=true;

    public CompilerSettings() {
    }
    
    public CompilerSettings(CompilerSettings other) {
        this.failOnAddressOutOfRange = other.failOnAddressOutOfRange;
    }
    
    public CompilerSettings createCopy() {
        return new CompilerSettings(this);
    }
    
    /**
     * Sets whether compilation should fail if generated code does not fit
     * into the memory of the target architecture.
     * 
     * @param failOnAddressOutOfRange
     * @return this instance (for chaining)
     */
    public CompilerSettings setFailOnAddressOutOfRange(boolean failOnAddressOutOfRange) {
        this.failOnAddressOutOfRange = failOnAddressOutOfRange;
        return this;
    }
    
    /**
     * Whether compilation should fail if generated code does not fit
     * into the memory of the target architecture.
     * 
     */
    @Override
    public boolean isFailOnAddressOutOfRange() {
        return failOnAddressOutOfRange;
    }
    
    /**
     * Returns the max. number of error messages permitted before compilation is aborted.
     * 
     * @return
     */
    public int getMaxErrors() {
        return maxErrors;
    }
    
    /**
     * Sets the max. number of error messages permitted before compilation is aborted.
     *      
     * @param maxErrors
     * @return this instance (for chaining)     
     */
    public CompilerSettings setMaxErrors(int maxErrors) {
        this.maxErrors = maxErrors;
        return this;
    }
}