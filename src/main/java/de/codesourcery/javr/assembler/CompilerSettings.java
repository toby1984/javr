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
    
    public CompilerSettings setFailOnAddressOutOfRange(boolean failOnAddressOutOfRange) {
        this.failOnAddressOutOfRange = failOnAddressOutOfRange;
        return this;
    }
    
    @Override
    public boolean isFailOnAddressOutOfRange() {
        return failOnAddressOutOfRange;
    }
}