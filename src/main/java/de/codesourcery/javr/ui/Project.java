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
package de.codesourcery.javr.ui;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Paths;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;
import org.apache.log4j.Logger;

import de.codesourcery.hex2raw.IntelHex;
import de.codesourcery.javr.assembler.Assembler;
import de.codesourcery.javr.assembler.Buffer;
import de.codesourcery.javr.assembler.CompilationUnit;
import de.codesourcery.javr.assembler.ICompilationContext;
import de.codesourcery.javr.assembler.IObjectCodeWriter;
import de.codesourcery.javr.assembler.ObjectCodeWriter;
import de.codesourcery.javr.assembler.ObjectCodeWriterWrapper;
import de.codesourcery.javr.assembler.Segment;
import de.codesourcery.javr.assembler.arch.IArchitecture;
import de.codesourcery.javr.assembler.elf.ElfFile;
import de.codesourcery.javr.assembler.parser.Lexer;
import de.codesourcery.javr.assembler.parser.LexerImpl;
import de.codesourcery.javr.assembler.parser.Parser;
import de.codesourcery.javr.assembler.parser.Parser.CompilationMessage;
import de.codesourcery.javr.assembler.parser.Scanner;
import de.codesourcery.javr.assembler.symbols.SymbolTable;
import de.codesourcery.javr.assembler.util.Misc;
import de.codesourcery.javr.assembler.util.Resource;
import de.codesourcery.javr.ui.config.IConfig;
import de.codesourcery.javr.ui.config.ProjectConfiguration;
import de.codesourcery.javr.ui.config.ProjectConfiguration.OutputFormat;
import de.codesourcery.javr.ui.config.ProjectConfiguration.OutputSpec;

public class Project implements IProject
{
    private static final Logger LOG = Logger.getLogger(Project.class);

    private IArchitecture architecture;

    private final List<CompilationUnit> units = new ArrayList<>();
    private CompilationUnit compileRoot;    
    
    private final SymbolTable globalSymbolTable = new SymbolTable( SymbolTable.GLOBAL );

    private ProjectConfiguration projectConfig = new ProjectConfiguration();
    
    private final ObjectCodeWriter writerDelegate = new ObjectCodeWriter();
    private final IObjectCodeWriter objWriter = new ObjectCodeWriterWrapper( writerDelegate ) 
    {
        @Override
        public void reset() throws IOException 
        {
            super.reset();
            artifactsGenerated = false;
            for ( OutputSpec spec : projectConfig.getOutputFormats().values() ) 
            {
                spec.deleteFile();
            }
        }

        @Override
        public void finish(ICompilationContext context,boolean success) throws IOException 
        {
            super.finish(context,success);
            
            if ( ! success ) 
            {
                compilationSuccess = false;
                return;
            }
            
            for ( Segment s : Segment.values() ) 
            {
                final OutputSpec spec = projectConfig.getOutputFormats().get( s );
                if ( spec == null ) 
                {
                    LOG.info("finish(): Not writing file, project configuration has no output spec for segment "+s);
                    continue;
                }
                final Buffer buffer = delegate.getBuffer( s );
                if ( buffer.isEmpty() ) 
                {
                    LOG.info("finish(): Not writing file, compilation produced no data for segment "+s);
                    continue;
                }
                int bytesWritten;
                try ( InputStream in = buffer.createInputStream() ; OutputStream out = spec.resource.createOutputStream() ) 
                {
                    if ( spec.format == OutputFormat.INTEL_HEX ) 
                    {
                        bytesWritten = new IntelHex().rawToHex( in , out , buffer.getStartAddress().getByteAddress() ); 
                    } 
                    else if ( spec.format == OutputFormat.RAW ) 
                    {
                        bytesWritten = IOUtils.copy( in , out );
                    } 
                    else if ( spec.format == OutputFormat.ELF_EXECUTABLE || spec.format == OutputFormat.ELF_RELOCATABLE ) 
                    {
                        final ByteArrayOutputStream program = new ByteArrayOutputStream();
                        bytesWritten = IOUtils.copy( in , program );
                        if ( s == Segment.FLASH ) {
                            new ElfFile( spec.format ).write( getArchitecture() , delegate , context.globalSymbolTable() , out );
                        }
                    } else {
                        throw new RuntimeException("Unhandled output format: "+spec.format);
                    }
                }
                if ( bytesWritten > 0 )
                {
                    artifactsGenerated = true;
                    
                    final int segSize = getArchitecture().getSegmentSize( s );
                    final float percentage = 100.0f*(bytesWritten/(float) segSize);
                    final DecimalFormat DF = new DecimalFormat("#####0.00");
                    final String msg;
                    if ( spec.format == OutputFormat.ELF_EXECUTABLE && s == Segment.SRAM ) {
                        msg = s+": "+bytesWritten+" bytes used ("+DF.format(percentage)+" %)";
                    } else {
                        msg = s+": Wrote "+bytesWritten+" bytes ("+DF.format(percentage)+" %) to "+spec.resource;
                    }
                    context.message( CompilationMessage.info( context.currentCompilationUnit() , msg ) );                    
                }
                LOG.info("finish(): Wrote "+bytesWritten+" bytes to "+spec.resource+" in format "+spec.format);
            } 
        }
    };

    private boolean compilationSuccess = false;
    private boolean artifactsGenerated = false;

    public Project(CompilationUnit compilationRoot) 
    {
        this( compilationRoot , new ProjectConfiguration() ); 
    }
    
    public Project( CompilationUnit compilationRoot, ProjectConfiguration config) 
    {
        Validate.notNull(compilationRoot, "compilationUnit must not be NULL");
        Validate.notNull(config, "config must not be NULL");
        
        this.architecture = config.getArchitecture();
        this.compileRoot = compilationRoot;
        this.projectConfig = config;
        units.add( compilationRoot );
    }
    
    @Override
    public IObjectCodeWriter getObjectCodeWriter() {
        return objWriter;
    }

    public void setArchitecture(IArchitecture architecture) {
        Validate.notNull(architecture, "architecture must not be NULL");
        this.architecture = architecture;
    }

    @Override
    public IArchitecture getArchitecture() {
        return architecture;
    }

    public void setCompileRoot(CompilationUnit compileRoot) 
    {
        Validate.notNull(compileRoot, "compileRoot must not be NULL");
    	System.out.println("Setting compile root "+compileRoot);
        this.compileRoot = compileRoot;
        if ( ! this.units.stream().anyMatch( existing -> existing.getResource().pointsToSameData( compileRoot.getResource() ) ) ) 
        {
        	addCompilationUnit( compileRoot );
        }
    }
    
    @Override
    public Optional<CompilationUnit> maybeGetCompilationUnit(Resource resource) 
    {
        return units.stream().filter( unit -> unit.getResource().pointsToSameData( resource ) ).findFirst();
    }
    
    @Override
    public CompilationUnit getCompilationUnit(Resource resource) 
    {
        final Optional<CompilationUnit> existing = maybeGetCompilationUnit(resource);
        if ( existing.isPresent() ) {
            return existing.get();
        }
        return addCompilationUnit( new CompilationUnit( resource , new SymbolTable( resource.getName() , globalSymbolTable) ) );
    }
    
    private CompilationUnit addCompilationUnit(CompilationUnit unit) 
    {
    	System.out.println("Adding new compilation unit: "+unit);
        units.add( unit );
		invokeProjectListeners( l -> l.unitAdded( this , unit ) ) ;   
		return unit;
    }

    @Override
    public CompilationUnit getCompileRoot() {
        return compileRoot;
    }

    @Override
    public boolean canUploadToController() 
    {
        final Collection<OutputSpec> values = projectConfig.getOutputFormats().values();
        return compilationSuccess && 
                artifactsGenerated && 
                StringUtils.isNotBlank( projectConfig.getUploadCommand() ) &&
               ! values.isEmpty() && 
                values.stream().anyMatch( s -> s != null );
    }
    
    public final boolean equals(Object other) 
    {
        if ( other instanceof IProject) {
            return getConfiguration().getBaseDir().equals( ((IProject) other).getConfiguration().getBaseDir() );
        }
        return false;
    }
    
    @Override
    public final int hashCode() 
    {
        return getConfiguration().getBaseDir().hashCode();
    }

    @Override
    public void uploadToController() throws IOException 
    {
        if ( ! canUploadToController() ) {
            throw new IllegalStateException("No upload command configured on this project");
        }

        /*
         * Expand commandline arguments.
         * 
         * %f => expands to file holding the flash data
         * %fa => expands to byte address where flash data should be uploaded to
         * 
         * %e => expands to file holding the EEPROM data
         * %ea => expands to byte address where EEPROM data should be uploaded to
         */
        final Map<String,String> params = new HashMap<>();
        for ( Segment s : new Segment[]{Segment.FLASH,Segment.EEPROM} ) 
        {
            final Buffer buffer = writerDelegate.getBuffer( s );
            final OutputSpec spec = projectConfig.getOutputFormats().get( s );
            if ( spec != null ) 
            {
                switch( s ) 
                {
                    case FLASH:
                        params.put("f", spec.resource.toString() );
                        params.put("fa", Integer.toString( buffer.getStartAddress().getByteAddress() ) );                        
                        break;
                    case EEPROM:
                        params.put("e", spec.resource.toString() );
                        params.put("ea", Integer.toString( buffer.getStartAddress().getByteAddress() ) );                       
                        break;
                    default:
                        throw new RuntimeException("Unhandled switch/case: "+s);
                }
            }
        }
        
        if ( params.isEmpty() ) {
            throw new RuntimeException("Internal error, no parameters ?");
        }

        final List<String> arguments = Misc.expand( projectConfig.getUploadCommand() , params );

        // invoke command
        final String cmd = arguments.stream().collect(Collectors.joining(" "));
        LOG.info("uploadToController(): "+cmd);

        final ProcessWindow window = new ProcessWindow("Upload to uC" , "Upload using \n\n"+cmd , true );
        window.execute( arguments );
    }

    @Override
    public boolean compile() throws IOException 
    {
    	try 
    	{
	        compilationSuccess  = false;
	        artifactsGenerated = false;
	        
	        final Assembler asm = new Assembler();
	        compilationSuccess = asm.compile(  this , getObjectCodeWriter() , projectConfig , this );
	        return compilationSuccess;
    	} 
    	finally {
    		invokeProjectListeners( l -> l.compilationFinished( Project.this , compilationSuccess ) );
    	}
    }
    
    @Override
    public IConfig getConfig()
    {
        return new IConfig() {

            @Override
            public IArchitecture getArchitecture() {
                return Project.this.getArchitecture();
            }

            @Override
            public Lexer createLexer(Scanner s) {
                return new LexerImpl(s);
            }

            @Override
            public Parser createParser() 
            {
                return new Parser(Project.this.getArchitecture() );
            }

            @Override
            public String getEditorIndentString() {
                return "  ";
            }
        };
    }

    public static File getCurrentWorkingDirectory() {
        return new File( Paths.get(".").toAbsolutePath().normalize().toString() );
    }

    @Override
    public ProjectConfiguration getConfiguration() {
        return this.projectConfig.createCopy();
    }

    @Override
    public void setConfiguration(ProjectConfiguration newConfig) throws IOException 
    {
    	Resource resource = newConfig.getCompilationRootResource();
    	if ( ! resource.exists() ) {
    		throw new IllegalArgumentException("Failed to find compilation root file: "+resource);
    	}
    	setCompileRoot( getCompilationUnit( resource ) );
        this.compilationSuccess = false;
        this.artifactsGenerated = false;
        this.architecture = newConfig.getArchitecture();
        this.projectConfig = newConfig.createCopy();
    }
    
    @Override
    public Resource resolveResource(String child) throws IOException 
    {
        return projectConfig.resolveResource(child);
    }

    @Override
    public Resource resolveResource(Resource parent, String child) throws IOException {
        return projectConfig.resolveResource(parent,child);
    }

    @Override
    public SymbolTable getGlobalSymbolTable() {
        return globalSymbolTable;
    }

	@Override
	public void removeCompilationUnit(CompilationUnit unit) 
	{
		if ( this.units.remove( unit ) ) {
			invokeProjectListeners( l -> l.unitRemoved( this , unit ) ) ;
		}
	}
	
	private final List<IProjectChangeListener> projectListeners = new ArrayList<>();

	@Override
	public void addProjectChangeListener(IProjectChangeListener listener) {
		Validate.notNull(listener, "listener must not be NULL");
		if ( ! projectListeners.contains( listener ) ) 
		{
		    projectListeners.add(listener);
		}
	}

    private void invokeProjectListeners(Consumer<IProjectChangeListener> callback) 
    {
		projectListeners.forEach( l -> 
		{
			try {
				callback.accept( l );
			} catch(Exception e) {
				LOG.error("invokeProjectListeners(): Failed to invoke "+callback,e);
			}
		});
    }
    
	@Override
	public void removeProjectChangeListener(IProjectChangeListener listener) {
		Validate.notNull(listener, "listener must not be NULL");
		projectListeners.remove(listener);		
	}	
}