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
package de.codesourcery.javr.assembler;

import de.codesourcery.javr.assembler.arch.IArchitecture;
import de.codesourcery.javr.assembler.elf.Relocation;
import de.codesourcery.javr.assembler.parser.Parser.CompilationMessage;
import de.codesourcery.javr.assembler.parser.ast.ASTNode;
import de.codesourcery.javr.assembler.symbols.SymbolTable;
import de.codesourcery.javr.assembler.util.Resource;

/**
 * Provides access to the state of the current compilation process.
 * 
 * This interface is used by all compilation phases to perform the
 * actual compilation process.
 * 
 * @author tobias.gierke@code-sourcery.de
 */
public interface ICompilationContext
{
    interface ICompilerSettings
    {
        /**
         * Whether compilation should fail if generated code does not fit
         * into the memory of the target architecture.
         *
         */
        boolean isFailOnAddressOutOfRange();
        
        ICompilerSettings setFailOnAddressOutOfRange(boolean failOnAddressOutOfRange);
        
        /**
         * Returns the max. number of error messages permitted before compilation is aborted.
         * 
         * @return
         */
        int getMaxErrors();
        
        /**
         * Sets the max. number of error messages permitted before compilation is aborted.
         *      
         * @param maxErrors
         * @return this instance (for chaining)     
         */
        ICompilerSettings setMaxErrors(int maxErrors);

        /**
         * Returns whether a warning should be generated when
         * LDS/STS are used with a register address that's within
         * the IN/OUT instruction's range.
         *
         * @return
         * @see #setWarnIfInOutCanBeUsed(boolean)
         */
        boolean isWarnIfInOutCanBeUsed();

        /**
         * Returns whether a warning should be generated when
         * LDS/STS are used with a register address that's within
         * the IN/OUT instruction's range.
         *
         * @return
         * @see #setWarnIfInOutCanBeUsed(boolean)
         */
        void setWarnIfInOutCanBeUsed(boolean warnIfInOutCanBeUsed);
    }
    
    public void setStartAddress(int address);
    
    // symbol tables
    public SymbolTable globalSymbolTable();
    
    public SymbolTable currentSymbolTable();
    
    // code generation
    public int currentOffset();
    
    public Address currentAddress();
    
    public Segment currentSegment();
    
    public void setSegment(Segment s);
    
    public void writeByte(int value);
    
    public void writeWord(int value);
    
    public void allocateByte();
    
    public void allocateWord();
    
    public void allocateBytes(int numberOfBytes);    
    
    public boolean isGenerateRelocations();
    
    public void addRelocation(Relocation reloc);
    
    // error handling
    /**
     * 
     * @param message
     * @param node
     * @return <code>false</code> if the maximum number of compilation errors has already been reached, and compilation should be aborted, otherwise <code>false</code>.
     */
    public boolean error(String message,ASTNode node);
    
    public boolean hasReachedMaxErrors();
    
    /**
     * 
     * @param msg
     * @return <code>false</code> if the message has <code>Severity.ERROR</code> and the maximum number of compilation errors has already been reached
     * and compilation should be aborted, otherwise <code>true</code>
     */
    public boolean message(CompilationMessage msg);
    
    // #include handling
    public boolean pushCompilationUnit(CompilationUnit unit);
    
    public void popCompilationUnit();
    
    /**
     * 
     * @param res
     * 
     * @return always a new <code>CompilationUnit</code> instance (important for #include processing
     * since the same compilation unit may be expanded differently depending on which macros
     * are #define'd or #undef'ined
     */
    public CompilationUnit newCompilationUnit(Resource res);

    // misc
    public ResourceFactory getResourceFactory();
    
    public ICompilerSettings getCompilationSettings();
    
    public CompilationUnit currentCompilationUnit();
    
    public IArchitecture getArchitecture();    
}