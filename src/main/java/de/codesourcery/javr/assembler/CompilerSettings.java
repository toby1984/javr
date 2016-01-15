package de.codesourcery.javr.assembler;

import de.codesourcery.javr.assembler.ICompilationContext.ICompilationSettings;

public class CompilerSettings implements ICompilationSettings {

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
}