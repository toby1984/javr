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
package de.codesourcery.javr.ui;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Stack;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;
import org.apache.log4j.Logger;

import de.codesourcery.javr.assembler.Assembler;
import de.codesourcery.javr.assembler.Buffer;
import de.codesourcery.javr.assembler.CompilationContext;
import de.codesourcery.javr.assembler.CompilationUnit;
import de.codesourcery.javr.assembler.FileObjectCodeWriter;
import de.codesourcery.javr.assembler.ICompilationContext;
import de.codesourcery.javr.assembler.IObjectCodeWriter;
import de.codesourcery.javr.assembler.ObjectCodeWriter;
import de.codesourcery.javr.assembler.ResourceFactory;
import de.codesourcery.javr.assembler.Segment;
import de.codesourcery.javr.assembler.arch.IArchitecture;
import de.codesourcery.javr.assembler.parser.Identifier;
import de.codesourcery.javr.assembler.parser.ast.AST;
import de.codesourcery.javr.assembler.parser.ast.PreprocessorNode;
import de.codesourcery.javr.assembler.phases.ParseSourcePhase;
import de.codesourcery.javr.assembler.symbols.Symbol;
import de.codesourcery.javr.assembler.symbols.SymbolTable;
import de.codesourcery.javr.assembler.util.FileResource;
import de.codesourcery.javr.assembler.util.FileResourceFactory;
import de.codesourcery.javr.assembler.util.Misc;
import de.codesourcery.javr.assembler.util.Resource;
import de.codesourcery.javr.ui.config.ProjectConfiguration;
import de.codesourcery.javr.ui.config.ProjectConfiguration.OutputFormat;

public class Project implements IProject
{
    private static final Logger LOG = Logger.getLogger(Project.class);

    private final class ResourceAndWriter 
    {
        public final IObjectCodeWriter objWriter;
        public final CompilationUnit unit;
        
        public ResourceAndWriter(CompilationUnit unit) {
            Validate.notNull(unit,"unit must not be NULL");
            this.unit = unit;
            this.objWriter = new FileObjectCodeWriter() 
            {
                @Override
                protected Optional<Resource> getOutputFile(CompilationUnit unit, Segment s) throws IOException {
                    return Project.this.getOutputResource( unit.getResource() , s );
                }
            };
        }
    }
    
    private Long id;
    private final ResourceFactory resourceFactory;
    private final List<CompilationUnit> units = new ArrayList<>();
    private final ProjectConfiguration projectConfig = new ProjectConfiguration();
    private final List<ResourceAndWriter> writers = new ArrayList<>();
    
    public Project( ProjectConfiguration config) 
    {
        Validate.notNull(config, "config must not be NULL");
        
        this.resourceFactory = new FileResourceFactory() 
        {
            public File getBaseDir() 
            {
                return getConfiguration().getBaseDir();
            }
        };
        this.projectConfig.populateFrom( config );
    }
    
    @Override
    public IArchitecture getArchitecture() {
        return getConfiguration().getCompilerSettings().getArchitecture();
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
        return addCompilationUnit( new CompilationUnit( resource , new SymbolTable( resource.getName() ) ) );
    }
    
    private Optional<IObjectCodeWriter> getObjectCodeWriter(CompilationUnit unit) 
    {
        return getResourceAndWriter( unit ).map( r -> r.objWriter );
    }
    
    private Optional<ResourceAndWriter> getResourceAndWriter(CompilationUnit unit) 
    {
        return writers.stream().filter( w -> w.unit.getResource().pointsToSameData( unit.getResource() ) ).findFirst();
    }
    
    public CompilationUnit addCompilationUnit(CompilationUnit unit) 
    {
    	System.out.println("Adding new compilation unit: "+unit);
    	final Optional<ResourceAndWriter> existing = getResourceAndWriter( unit );
    	if ( existing.isPresent() ) 
    	{
    	    writers.remove( existing.get() );
    	}
    	writers.add( new ResourceAndWriter( unit ) );
        units.add( unit );
		invokeProjectListeners( l -> l.unitAdded( this , unit ) ) ;   
		return unit;
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
    public CheckResult checkCanUploadToController() 
    {
        if ( ! hasProjectType( ProjectType.EXECUTABLE ) ) {
            return CheckResult.uploadNotPossible( "Wrong project type, does not generate an executable");
        }
        if ( StringUtils.isBlank( projectConfig.getUploadCommand() ) ) {
            return CheckResult.uploadNotPossible( "Project configuration has no upload command configured");
        }
        
        final List<CompilationUnit> roots;
        try {
            roots = getCompilationRoots();
        } catch (IOException e) {
            LOG.error("canUploadToController(): Caught ",e);
            return CheckResult.uploadNotPossible( "Internal error: "+e.getMessage());
        }
        if ( roots.size() != 1 ) { // don't know which one to upload
            return CheckResult.uploadNotPossible( "Project has multiple compilation roots, don't know which one to upload");
        }
        
        final IObjectCodeWriter writer = getObjectCodeWriter( roots.get(0) ).orElseThrow( () -> new RuntimeException("Internal error, no object code writer for "+roots.get(0) ) );
        if ( ! writer.isCompilationSuccess() ) {
            return CheckResult.uploadNotPossible( "Project has not been successfully compiled (yet)");
        }
        if ( ! Arrays.asList( Segment.FLASH , Segment.EEPROM ).stream().anyMatch( segment -> writer.getBuffer( segment ).isNotEmpty() ) ) {
            return CheckResult.uploadNotPossible( "Compilation didn't produce any output");
        }
        return CheckResult.uploadPossible("ok");
    }    

    @Override
    public void uploadToController() throws IOException 
    {
        if ( ! checkCanUploadToController().uploadPossible ) {
            throw new IllegalStateException("No upload command configured on this project");
        }
        
        final CompilationUnit root = getCompilationRoots().get(0);
        final IObjectCodeWriter objWriter = getObjectCodeWriter( root ).get();

        
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
            final Buffer buffer = objWriter.getBuffer( s );
            final Optional<Resource> resource = getOutputResource( root.getResource() , s );
            if ( resource.isPresent() )
            {
                switch( s ) 
                {
                    case FLASH:
                        params.put("f", resource.get().toString() );
                        params.put("fa", Integer.toString( buffer.getStartAddress().getByteAddress() ) );                        
                        break;
                    case EEPROM:
                        params.put("e", resource.get().toString() );
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
        boolean compilationSuccess  = true ;
    	try 
    	{
            final Assembler asm = new Assembler();    	    
	        for ( CompilationUnit unit : getCompilationRoots() ) 
	        {
	            final IObjectCodeWriter objWriter = getObjectCodeWriter( unit ).get();
                compilationSuccess &= asm.compile(  unit , getConfiguration().getCompilerSettings() , objWriter , projectConfig );
	        }
	        return compilationSuccess;
    	} 
    	finally 
    	{
    	    final boolean finalSuccess = compilationSuccess;
    		invokeProjectListeners( l -> l.compilationFinished( Project.this , finalSuccess ) );
    	}
    }

    public static File getCurrentWorkingDirectory() {
        return new File( Paths.get(".").toAbsolutePath().normalize().toString() );
    }

    @Override
    public ProjectConfiguration getConfiguration() {
        return this.projectConfig;
    }
    
    @Override
    public void setConfiguration(ProjectConfiguration config) 
    {
        Validate.notNull(config,"config must not be NULL");
        this.projectConfig.populateFrom( config );
    }

    @Override
    public Resource resolveResource(String child) throws IOException 
    {
        return resourceFactory.resolveResource(child);
    }

    @Override
    public Resource resolveResource(Resource parent, String child) throws IOException {
        return resourceFactory.resolveResource(parent,child);
    }

    @Override
    public List<Resource> getAllAssemblerFiles(IProject project) throws IOException 
    {
        if ( project != this ) {
            throw new IllegalArgumentException("Called for project that is not THIS project ?");
        }
        return resourceFactory.getAllAssemblerFiles(project);
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

    @Override
    public Long getID() {
        return id;
    }

    @Override
    public void setID(long projectId) {
        this.id = projectId;
    }
    
    private static final class Dependency 
    {
        public final CompilationUnit unit;
        public final boolean hasMainFunction;
        public final AST ast;
        public final List<Dependency> dependencies = new ArrayList<>();
        public boolean visited = false;
        
        public Dependency(CompilationUnit unit,AST ast,boolean hasMainFunction) 
        {
            Validate.notNull(unit,"unit must not be NULL");
            Validate.notNull(ast,"AST must not be NULL");
            this.unit = unit;
            this.ast = ast;
            this.hasMainFunction = hasMainFunction;
        }
    }
    
    private List<List<Dependency>> findCyclicDependencies(List<Dependency> deps) 
    {
        final List<List<Dependency>> result = new ArrayList<>();
        final Stack<Dependency> stack = new Stack<>();
        for ( Dependency d : deps ) 
        {
            deps.forEach( dep -> dep.visited=false );
            stack.clear();
            stack.push( d );
            while ( ! stack.isEmpty() ) 
            {
                Dependency current = stack.pop();
                if ( current.visited ) 
                {
                    result.add( new ArrayList<>( stack ) );
                    break;
                }
                current.visited = true;
                stack.addAll( current.dependencies );
            }
        }
        return result;
    }
    
    private List<Dependency> getDependencies() throws IOException 
    {
        final List<CompilationUnit> units = 
                getAllAssemblerFiles( this ).stream().map( s -> getCompilationUnit( s ) ).collect( Collectors.toList() );
        
        final List<Dependency> result = new ArrayList<>();
        for ( CompilationUnit unit : units ) 
        {
            final CompilationUnit dummy = new CompilationUnit( unit.getResource() );
            
            // FIXME: Performance - I'm currently always parsing the files again even when they've been already compiled before....this makes everything quite robust 
            // FIXME: but obviously comes with a performance penalty...
            // parse without processing any includes
            final IObjectCodeWriter objectCodeWriter = new ObjectCodeWriter();
            final ICompilationContext context = new CompilationContext(dummy,
                    objectCodeWriter,
                    resourceFactory, getConfiguration().getCompilerSettings() );
            ParseSourcePhase.parseWithoutIncludes( context , unit );
            
            final AST ast = dummy.getAST();
            if ( ast != null )
            {
                boolean hasMainFunction=false;
                if ( getProjectType() == ProjectType.EXECUTABLE ) {
                    // check for 'main' symbol
                    hasMainFunction = dummy.getSymbolTable().maybeGet( new Identifier("main") , Symbol.Type.ADDRESS_LABEL ).isPresent();
                }
                result.add( new Dependency( unit , ast , hasMainFunction ) );
            } else {
                LOG.error("getDependencies(): Warning, failed to parse "+unit+" ... ignored as a potential compilation root");
            }
        }
        
        // now analyze dependencies between units by looking for #include commands
        for ( Dependency dep : result ) 
        {
            // collect #include nodes 
            final List<PreprocessorNode> includes = new ArrayList<>();
            dep.ast.visitDepthFirstWithResult( includes , (node,ctx) -> 
            {
                if ( node instanceof PreprocessorNode ) 
                {
                    final PreprocessorNode p = (PreprocessorNode) node;
                    if ( p.type == PreprocessorNode.Preprocessor.INCLUDE ) {
                        includes.add( p );
                    }
                }
            });
            for (int i = 0; i < includes.size(); i++) 
            {
                final PreprocessorNode include = includes.get(i);
                final String path = include.getArguments().get(0);
                final Resource resource = resourceFactory.resolveResource( dep.unit.getResource() , path );
                Dependency match = null;
                for (int j = 0; j < result.size(); j++) 
                {
                    final Dependency dd = result.get(j);
                    if ( dd.unit.getResource().pointsToSameData( resource ) ) {
                        match = dd;
                        break;
                    }
                }
                if ( match != null ) 
                {
                    dep.dependencies.add( match );
                } else {
                    LOG.error("getDependencies(): Ignoring #include \""+path+"\" as it's not in our set of candidate compilation units (maybe parsing failed for the dependency?)");
                }
            }
        }
        
        // make sure there are no cyclic dependencies so we don't get stuck in an infinite loop
        List<List<Dependency>> cycles = findCyclicDependencies( result );
        for ( List<Dependency> cycle : cycles ) 
        {
            final String msg = "Found cyclic dependency between compilation units: "+StringUtils.join( cycle , " <-> " );
            System.err.println( "ERROR: "+msg );
            LOG.error("getDependencies(): "+msg);
        }
        if ( ! cycles.isEmpty() ) {
            throw new RuntimeException("Found cyclic dependency between compilation units, cannot continue");
        }
        return result;
    }
    
    /**
     * Returns the root {@link CompilationUnit}s that will each be passed to the compiler.
     * 
     * @return
     * @throws IOException 
     */
    public List<CompilationUnit> getCompilationRoots() throws IOException 
    {
        long time = System.currentTimeMillis();
        final List<Dependency> dependencies = getDependencies();
        long time2 = System.currentTimeMillis();
        LOG.info("getCompilationRoots(): Gathering compilation roots took "+(time2-time)+" ms");
        switch( getProjectType() ) 
        {
            case EXECUTABLE:
                // return only the units that have a 'main' symbol in their symbol table
                return dependencies.stream().filter( d -> d.hasMainFunction ).map( d -> d.unit ).collect( Collectors.toCollection( ArrayList::new ) );                
            case LIBRARY:
                // just return all the units
                return dependencies.stream().map( d -> d.unit ).collect( Collectors.toCollection( ArrayList::new ) );
            default:
                throw new RuntimeException("Unhandled switch/case: "+getProjectType());
        }
    }

    @Override
    public ProjectType getProjectType() {
        return getConfiguration().getProjectType();
    }
    
    private Optional<Resource> getOutputResource(Resource sourceFile,Segment segment) throws IOException 
    {
        if ( segment == Segment.SRAM ) {
            return Optional.empty();
        }
        
        if ( !( sourceFile instanceof FileResource ) ) {
            return Optional.empty();
        } 
        final String name = ((FileResource) sourceFile).getFile().getName();
        final File baseName = new File( getConfiguration().getBaseDir() , name );
        final Optional<File> outputFile = getOutputFileName( getConfiguration().getOutputFormat(),segment, baseName);
        if ( ! outputFile.isPresent() ) {
            return Optional.empty();
        }
        return Optional.of( new FileResource(outputFile.get(),getConfiguration().getSourceFileEncoding() ) );
    }

    public static Optional<File> getOutputFileName( OutputFormat outputFormat, Segment segment, final File baseName) 
    {
        String basePath = baseName.getAbsolutePath();
        if ( basePath.contains("." ) ) { // strip suffix
            final String[] parts = basePath.split("\\.");
            basePath = StringUtils.join( parts , "" , 0 , parts.length -1 );
        }
        
        final String fileEnding;
        switch( outputFormat )
        {
            case INTEL_HEX: fileEnding = ".hex"; break;
            case RAW: fileEnding = ".raw"; break;
            case ELF_EXECUTABLE: fileEnding = ".out" ; break;
            case ELF_RELOCATABLE: fileEnding = ".o" ; break;                
            default:
                throw new RuntimeException("Unhandled file format: "+outputFormat);
        }
        final String suffix;
        switch( segment ) 
        {
            case EEPROM: suffix = ".eeprom"; break;
            case FLASH: suffix = ""; break;
            case SRAM: return Optional.empty();
            default:
                throw new RuntimeException("Unhandled segment: "+segment);
        }
        return Optional.of( new File( basePath + suffix + fileEnding ) );
    }        
}