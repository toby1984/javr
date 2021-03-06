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

import java.io.IOException;
import java.util.IdentityHashMap;
import java.util.Stack;

import de.codesourcery.javr.assembler.phases.Phase;
import org.apache.commons.lang3.Validate;
import org.apache.log4j.Logger;

import de.codesourcery.javr.assembler.arch.IArchitecture;
import de.codesourcery.javr.assembler.elf.Relocation;
import de.codesourcery.javr.assembler.parser.Parser.CompilationMessage;
import de.codesourcery.javr.assembler.parser.Parser.Severity;
import de.codesourcery.javr.assembler.parser.ast.ASTNode;
import de.codesourcery.javr.assembler.symbols.SymbolTable;
import de.codesourcery.javr.assembler.util.Resource;
import de.codesourcery.javr.ui.config.IConfig;

public final class CompilationContext implements ICompilationContext 
{
    private static final Logger LOG = Logger.getLogger(CompilationContext.class);
    
    private final SymbolTable globalSymbolTable;
    
    private final CompilerSettings compilerSettings;
    
    private final IObjectCodeWriter objectCodeWriter;
    
    private final ResourceFactory resourceFactory;

    private boolean generateLocations;
    
    private final IConfig config;        
    
    // stack to keep track of the current compilation unit while processing #include directives
    private final Stack<CompilationUnit> compilationUnits = new Stack<>();
    
    // the compilation unit that was passed to start the compilation process
    private final CompilationUnit rootCompilationUnit;
    
    // performance optimization to avoid calling Stack#peek() all the time,
    // updated each time a compilation unit is pushed to the stack/popped from the stack
    private CompilationUnit currentCompilationUnit;
    
    // limit at which compilation is aborted
    private final int maxErrorsLimit;
    
    private int errorCount; // total error count
    
    public CompilationContext(
    		CompilationUnit rootCompilationUnit,
    		SymbolTable globalSymbolTable,
            IObjectCodeWriter objectCodeWriter, 
            ResourceFactory resourceFactory,
            CompilerSettings compilerSettings,
            IConfig config) 
    {
        Validate.notNull(objectCodeWriter, "objectCodeWriter must not be NULL");
        Validate.notNull(resourceFactory, "resourceFactory must not be NULL");
        Validate.notNull(compilerSettings,"compilerSettings must not be NULL");
        Validate.notNull(config,"config must not be NULL");
        this.resourceFactory = resourceFactory;
        this.rootCompilationUnit = rootCompilationUnit;
        this.objectCodeWriter = objectCodeWriter;
        this.globalSymbolTable = globalSymbolTable;
        this.compilerSettings = compilerSettings;
        this.config = config;
        this.maxErrorsLimit = compilerSettings.getMaxErrors();
        
        pushCompilationUnit( rootCompilationUnit );
    }
    
    @Override
    public CompilationUnit newCompilationUnit(Resource res) 
    {
    	return new CompilationUnit( res , globalSymbolTable );
    }
    
    @Override
    public boolean hasReachedMaxErrors() 
    {
        return errorCount >= maxErrorsLimit;
    }
    
    @Override
    public boolean pushCompilationUnit(CompilationUnit newUnit) 
    {
        Validate.notNull(newUnit, "unit must not be NULL");
        if ( newUnit == currentCompilationUnit() ) {
            throw new IllegalArgumentException("Cannot push current compilation unit");
        }
        
        /*
         * Check for circular dependencies BEFORE pushing the new unit to the stack
         */
        final Stack<CompilationUnit> stack = new Stack<>();
        if ( currentCompilationUnit != null ) { // NULL when this method is called by the constructor
            stack.push( currentCompilationUnit() );
        }
        
        final IdentityHashMap<CompilationUnit, Boolean> unique = new IdentityHashMap<>();
        
        while ( ! stack.isEmpty() ) 
        {
            final CompilationUnit current = stack.pop();
            
            if ( unique.containsKey( current ) ) 
            {
                LOG.error("pushCompilationUnit(): Circular includes detected: "+unique.keySet()+" in "+newUnit.getAST());
                error("Circular includes detected: "+unique.keySet(),newUnit.getAST());
                return false;
            }
            unique.put( current, Boolean.TRUE );
            stack.addAll( current.getDependencies() );
            if ( current == currentCompilationUnit() ) // fake adding the new compilation unit
            {
                stack.add( newUnit );
            }
        }
        
        if ( currentCompilationUnit != null ) { // NULL when this method is called by the constructor
            currentCompilationUnit().addDependency( newUnit );
        }
        compilationUnits.push( newUnit );
        this.currentCompilationUnit = newUnit;
        return true;
    }
    
    @Override
    public ResourceFactory getResourceFactory() {
        return resourceFactory;
    }
    
    @Override
    public void popCompilationUnit() 
    {
        compilationUnits.pop();
        this.currentCompilationUnit = compilationUnits.isEmpty() ? null : compilationUnits.peek();
    }        
    
    @Override
    public ICompilerSettings getCompilationSettings() {
        return compilerSettings;
    }

    public void beforePhase() throws IOException
    {
        compilationUnits.clear();
        currentCompilationUnit = null;
        pushCompilationUnit( rootCompilationUnit );
        objectCodeWriter.reset();
        if ( ! isGenerateRelocations() ) 
        {
            // we're directly generating an executable binary here. 
            // As AVR maps registers at the start of RAM we'll need to add the architecture's 
            // register offset so we don't access registers instead of memory.
            objectCodeWriter.setStartAddress( Address.byteAddress( Segment.SRAM , config.getArchitecture().getSRAMStartAddress() ) );
        }
        objectCodeWriter.setCurrentSegment( Segment.FLASH );
    }

    @Override
    public SymbolTable currentSymbolTable() {
        final CompilationUnit unit = currentCompilationUnit();
        if ( unit == null ) {
            LOG.error("Internal error, no current compilation unit in context,root: "+this.rootCompilationUnit+", stack: "+compilationUnits);
            throw new RuntimeException("Internal error, no current compilation unit in context,root: "+this.rootCompilationUnit+", stack: "+compilationUnits);
        }
        return unit.getSymbolTable();
    }

    @Override
    public SymbolTable globalSymbolTable() {
        return globalSymbolTable;
    }

    @Override
    public Address currentAddress() 
    {
        return Address.byteAddress( currentSegment() , currentOffset() );
    }

    @Override
    public int currentOffset() 
    {
        return objectCodeWriter.getCurrentByteAddress();
    }

    @Override
    public Segment currentSegment() {
        return objectCodeWriter.getCurrentSegment();
    }

    @Override
    public void setSegment(Segment s) {
        objectCodeWriter.setCurrentSegment( s );
    }

    @Override
    public void writeByte(int value) 
    {
        objectCodeWriter.writeByte(value);
    }

    @Override
    public void writeWord(int value) 
    {
        objectCodeWriter.writeWord( value );
    }

    @Override
    public boolean error(String message, ASTNode node) {
        return message( CompilationMessage.error(currentCompilationUnit() , message,node ) );
    }

    @Override
    public boolean message(CompilationMessage msg) 
    {
        if ( msg.severity.equalOrGreater( Severity.ERROR ) ) 
        {
            if ( errorCount >= maxErrorsLimit ) {
                return false;
            }
            errorCount++;
        }
        currentCompilationUnit().addMessage( msg );
        return true;
    }

    @Override
    public IArchitecture getArchitecture() {
        return config.getArchitecture();
    }

    @Override
    public void allocateByte() 
    {
        allocateBytes(1);
    }

    public void allocateBytes(int bytes)
    {
        objectCodeWriter.allocateBytes( bytes );
    }

    @Override
    public void allocateWord() {
        allocateBytes(2);
    }

    @Override
    public CompilationUnit currentCompilationUnit() {
        return currentCompilationUnit;
    }

    @Override
    public void setStartAddress(int address) {
        objectCodeWriter.setStartAddress( Address.byteAddress( currentSegment() , address ) );
    }
    
    @Override
    public String toString() {
        return "Compilation context "+super.toString()+" - current unit: "+currentCompilationUnit+", unit stack size: "+this.compilationUnits.size();
    }

    @Override
    public boolean isGenerateRelocations() {
        return generateLocations;
    }

    @Override
    public void addRelocation(Relocation reloc) {
        objectCodeWriter.addRelocation( reloc );
    }
    
    public void setGenerateRelocations(boolean yesNo) {
        this.generateLocations = yesNo;
    }
}