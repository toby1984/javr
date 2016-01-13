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

import java.io.IOException;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Stack;

import org.apache.commons.lang3.Validate;
import org.apache.log4j.Logger;

import de.codesourcery.javr.assembler.arch.IArchitecture;
import de.codesourcery.javr.assembler.parser.Parser.CompilationMessage;
import de.codesourcery.javr.assembler.parser.ast.ASTNode;
import de.codesourcery.javr.assembler.phases.GatherSymbolsPhase;
import de.codesourcery.javr.assembler.phases.GenerateCodePhase;
import de.codesourcery.javr.assembler.phases.ParseSourcePhase;
import de.codesourcery.javr.assembler.phases.Phase;
import de.codesourcery.javr.assembler.phases.PrepareGenerateCodePhase;
import de.codesourcery.javr.assembler.phases.SyntaxCheckPhase;
import de.codesourcery.javr.assembler.symbols.SymbolTable;
import de.codesourcery.javr.assembler.util.Resource;
import de.codesourcery.javr.ui.IConfig;
import de.codesourcery.javr.ui.IConfigProvider;

public class Assembler 
{
    private static final Logger LOG = Logger.getLogger(Assembler.class);

    private IConfig config;
    private CompilationContext compilationContext;
    private SymbolTable globalSymbolTable = new SymbolTable( SymbolTable.GLOBAL );
    
    private ResourceFactory resourceFactory;

    private final CompilerSettings compilerSettings = new CompilerSettings();

    private final class CompilationContext implements ICompilationContext 
    {
        private final Stack<CompilationUnit> compilationUnits = new Stack<>();
        private final CompilationUnit rootCompilationUnit;
        private CompilationUnit compilationUnit;
        
        private final IObjectCodeWriter objectCodeWriter;

        public CompilationContext(CompilationUnit unit,IObjectCodeWriter objectCodeWriter) 
        {
            Validate.notNull(unit, "unit must not be NULL");
            Validate.notNull(objectCodeWriter, "objectCodeWriter must not be NULL");
            this.rootCompilationUnit = unit;
            this.compilationUnit = unit;
            this.objectCodeWriter = objectCodeWriter;
        }
        
        @Override
        public void pushCompilationUnit(CompilationUnit newUnit) 
        {
			Validate.notNull(newUnit, "unit must not be NULL");
        	if ( newUnit == currentCompilationUnit() ) {
        		throw new IllegalArgumentException("Cannot push current compilation unit");
        	}
        	final Stack<CompilationUnit> stack = new Stack<>();
        	stack.push( currentCompilationUnit() );
        	
        	final IdentityHashMap<CompilationUnit, Boolean> unique = new IdentityHashMap<>();
        	
        	while ( ! stack.isEmpty() ) 
        	{
        		final CompilationUnit current = stack.pop();
        		
        		if ( unique.containsKey( current ) ) 
        		{
        			error("Circular includes detected: "+unique.keySet(),newUnit.getAST());
        			return;
        		}
        		unique.put( current, Boolean.TRUE );
        		stack.addAll( current.getDependencies() );
        		if ( current == currentCompilationUnit() ) // fake adding the new compilation unit
        		{
        			stack.add( newUnit );
        		}
        	}
        	
        	currentCompilationUnit().addDependency( newUnit );
        	compilationUnits.add( newUnit );
        	this.compilationUnit = newUnit;
        }
        
        @Override
        public ResourceFactory getResourceFactory() {
        	return resourceFactory;
        }
        
        @Override
        public void popCompilationUnit() 
        {
        	this.compilationUnit = compilationUnits.pop();
        }        
        
        public CompilationUnit parseInclude(String path) throws IOException
        {
            final Resource newResource = resourceFactory.resolveResource( currentCompilationUnit().getResource() , path );
            for ( CompilationUnit existing : currentCompilationUnit().getDependencies() ) 
            {
                if ( existing.getResource().pointsToSameData( newResource ) ) 
                {
                    return currentCompilationUnit();
                }
            }
            
            final SymbolTable symbolTable = new SymbolTable( path , currentSymbolTable() );
			final CompilationUnit result = new CompilationUnit( newResource , symbolTable );
            
            ParseSourcePhase.parseSource( result , config );
            return result;
        }

        @Override
        public ICompilationSettings getCompilationSettings() {
            return compilerSettings;
        }

        public void beforePhase() throws IOException
        {
            objectCodeWriter.reset();
            objectCodeWriter.setCurrentSegment( Segment.FLASH );
        }

        @Override
        public SymbolTable currentSymbolTable() {
            return currentCompilationUnit().getSymbolTable();
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
        public void error(String message, ASTNode node) {
            message( CompilationMessage.error(message,node ) );
        }

        @Override
        public void message(CompilationMessage msg) {
            currentCompilationUnit().addMessage( msg );
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
            return compilationUnit;
        }
    }

    public boolean compile(CompilationUnit unit,IObjectCodeWriter codeWriter,ResourceFactory rf, IConfigProvider config) throws IOException 
    {
        Validate.notNull(unit, "compilation unit must not be NULL");
        Validate.notNull(codeWriter, "codeWriter must not be NULL");
        Validate.notNull(rf, "resourceFactory must not be NULL");
        Validate.notNull(config, "provider must not be NULL");
        
        this.globalSymbolTable = new SymbolTable(SymbolTable.GLOBAL);
        
        unit.beforeCompilationStarts(globalSymbolTable);

        this.compilationContext = new CompilationContext( unit , codeWriter );
        this.config = config.getConfig();
        this.resourceFactory = rf;
        
        final List<Phase> phases = new ArrayList<>();
        phases.add( new ParseSourcePhase(config) );
        phases.add( new SyntaxCheckPhase() );
        phases.add( new GatherSymbolsPhase() );
        phases.add( new PrepareGenerateCodePhase() );
        phases.add( new GenerateCodePhase() );

        LOG.info("assemble(): Now compiling "+unit);

        boolean success = false;
        try 
        {
            for ( Phase p : phases )
            {
                LOG.debug("Assembler phase: "+p);
                compilationContext.beforePhase();

                boolean hasErrors;
                try 
                {
                    p.beforeRun( compilationContext );

                    p.run( compilationContext );

                    hasErrors = unit.hasErrors(true);
                    if ( ! hasErrors ) {
                        p.afterSuccessfulRun( compilationContext );
                    } 
                } 
                catch (Exception e) 
                {
                    LOG.error("assemble(): ",e);
                    if ( e instanceof RuntimeException) {
                        throw (RuntimeException) e;
                    }
                    throw new RuntimeException(e);
                }

                if ( hasErrors ) 
                {
                    LOG.error("compile(): Compilation failed with errors in phase "+p);
                    return false;
                }
            }
            
            success = true;
        } 
        finally 
        {
            codeWriter.finish( success );
        }
        return true;
    }

    public SymbolTable getGlobalSymbolTable() 
    {
        return globalSymbolTable;
    }

    public CompilerSettings getCompilerSettings() {
        return compilerSettings;
    }
}