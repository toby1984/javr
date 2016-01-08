package de.codesourcery.javr.assembler;

import de.codesourcery.javr.assembler.ICompilationContext.ICompilationSettings;

public class CompilationSettings implements ICompilationSettings {

    private boolean failOnAddressOutOfRange;

    public CompilationSettings setFailOnAddressOutOfRange(boolean failOnAddressOutOfRange) {
        this.failOnAddressOutOfRange = failOnAddressOutOfRange;
        return this;
    }
    
    @Override
    public boolean isFailOnAddressOutOfRange() {
        return failOnAddressOutOfRange;
    }
}