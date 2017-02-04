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
package de.codesourcery.javr.ui.config;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Stream;

import org.apache.commons.lang3.Validate;
import org.apache.log4j.Logger;

import de.codesourcery.javr.assembler.CompilerSettings;
import de.codesourcery.javr.assembler.ResourceFactory;
import de.codesourcery.javr.assembler.Segment;
import de.codesourcery.javr.assembler.arch.Architecture;
import de.codesourcery.javr.assembler.arch.IArchitecture;
import de.codesourcery.javr.assembler.arch.impl.ATMega328p;
import de.codesourcery.javr.assembler.util.FileResource;
import de.codesourcery.javr.assembler.util.FileResourceFactory;
import de.codesourcery.javr.assembler.util.Resource;
import de.codesourcery.javr.ui.Project;

public class ProjectConfiguration implements ResourceFactory
{
    private static final Logger LOG = Logger .getLogger(ProjectConfiguration.OutputFormat.class);
    
    public static enum OutputFormat 
    {
        RAW,
        INTEL_HEX,
        ELF_EXECUTABLE,
        ELF_RELOCATABLE
    }
    
    public static final class OutputSpec 
    {
        public final Segment segment;
        public final Resource resource;
        public final OutputFormat format;

        public OutputSpec(Resource file, Segment segment, OutputFormat format) 
        {
            Validate.notNull(file, "file must not be NULL");
            Validate.notNull(segment, "segment must not be NULL");
            Validate.notNull(format, "format must not be NULL");
            this.segment = segment;
            this.resource = file;
            this.format = format;
        }
        
        public void deleteFile() throws IOException {
            resource.delete();
        }
        
        public OutputSpec withResource(Resource f) {
            return new OutputSpec( f , segment ,format );
        }
        
        public OutputSpec withFormat(OutputFormat f) {
            return new OutputSpec( resource , segment ,f );
        }        
    }   
    
    private final Map<Segment,OutputSpec> outputFormats = new HashMap<>();
    private FileResourceFactory resourceFactory;
    
    private String projectName = "unnamed project";
    private String uploadCommand;
    private String outputName;
    private OutputFormat outputFormat = OutputFormat.INTEL_HEX;
    private IArchitecture architecture = new ATMega328p();
    private CompilerSettings compilerSettings = new CompilerSettings();
    private String compilationRoot = "main.asm";
    private String sourceFolder = "src";
    
    /*
     * <project>
     *   <name></name>
     *   <outputName></outputName>
     *   <outputFormat></outputFormat>
     *   <uploadCommand</uploadCommand>
     *   <compileRoot></compileRoot>
     * </project>
     */
    public ProjectConfiguration(ProjectConfiguration other) 
    {
        this.outputFormats.clear();
        this.outputFormats.putAll( other.outputFormats );
        this.resourceFactory = (FileResourceFactory) FileResourceFactory.createInstance( other.resourceFactory.getBaseDir() );
        
        this.projectName = other.projectName;
        this.uploadCommand = other.uploadCommand;
        this.outputName = other.outputName;
        this.outputFormat = other.outputFormat;
        this.architecture = other.architecture;
        this.compilerSettings = other.compilerSettings.createCopy();
        this.compilationRoot = other.compilationRoot;
        this.sourceFolder = other.sourceFolder;
    }
    
    public ProjectConfiguration() 
    {
        resourceFactory = (FileResourceFactory) FileResourceFactory.createInstance( Project.getCurrentWorkingDirectory() );
        Stream.of( Segment.values() ).forEach( segment -> outputFormats.put( segment ,null ) );
        setupOutputResources();        
    }

    private void setupOutputResources() 
    {
        for ( Segment segment : this.outputFormats.keySet() ) 
        {
            final String fileEnding;
            switch( outputFormat )
            {
                case INTEL_HEX: fileEnding = ".hex"; break;
                case RAW: fileEnding = ".raw"; break;
                case ELF_EXECUTABLE: fileEnding = ".elf" ; break;
                default:
                    throw new RuntimeException("Unhandled file format: "+outputFormat);
            }
            final String suffix;
            switch( segment ) 
            {
                case EEPROM: suffix = ".eeprom"; break;
                case FLASH: suffix = ".flash"; break;
                case SRAM: suffix = ".sram"; break;
                default:
                    throw new RuntimeException("Unhandled segment: "+segment);
            }
            
            try 
            {
                final Resource resource = resourceFactory.resolveResource( getOutputName() + suffix + fileEnding );
                outputFormats.put( segment , new OutputSpec( resource , segment , outputFormat ) );
            } 
            catch (IOException e) 
            {
                LOG.error("ProjectConfiguration() : ",e);
                throw new RuntimeException(e);
            }
        }
    }
    
    public ProjectConfiguration createCopy() {
        return new ProjectConfiguration(this);
    }
    
    public File getBaseDir() {
        return resourceFactory.getBaseDir();
    }
    
    public void setBaseDir(File baseDir) 
    {
        Validate.notNull(baseDir, "baseDir must not be NULL");
        this.resourceFactory = (FileResourceFactory) FileResourceFactory.createInstance( baseDir );
        setupOutputResources();
    }
    
    public Map<Segment, OutputSpec> getOutputFormats() {
        return outputFormats;
    }
    
    public OutputFormat getOutputFormat() {
        return outputFormat;
    }
    
    public void setOutputFormat(OutputFormat format) 
    {
        Validate.notNull(format, "format must not be NULL");
        this.outputFormat = format;
        setupOutputResources();
    }
    
    public String getUploadCommand() {
        return uploadCommand;
    }
    
    public void setUploadCommand(String uploadCommand) {
        this.uploadCommand = uploadCommand;
    }

    @Override
    public Resource resolveResource(String child) throws IOException {
        return resourceFactory.resolveResource( child );
    }

    @Override
    public Resource resolveResource(Resource parent, String child) throws IOException {
        return resourceFactory.resolveResource(parent, child);
    }
    
    public String getOutputName() {
        return outputName != null ? outputName : projectName;
    }
    
    public void setOutputName(String outputName) 
    {
        this.outputName = outputName;
        setupOutputResources();
    }
    
    public void save(OutputStream out) throws IOException 
    {
        Validate.notNull(out, "out must not be NULL");
        
        final Properties props = new Properties();
        props.put("projectName" , projectName );
        if ( uploadCommand != null ) {
            props.put("uploadCommand" , uploadCommand );
        }
        if ( outputName != null ) {
            props.put("outputName" , outputName );
        }
        props.put( "sourceFolder" , sourceFolder );
        props.put( "compilationRoot" , compilationRoot );
        props.put( "outputFormat" , outputFormat.name() );
        props.put( "architecture" , architecture.getType().name() );
        props.put( "failOnAddressOutOfBounds" , Boolean.toString( getCompilerSettings().isFailOnAddressOutOfRange() ) );
        props.store( out , "DO NOT EDIT - GENERATED FILE, WILL BE OVERWRITTEN" );
    }
    
    public static ProjectConfiguration load(File baseDir , InputStream in) throws IOException 
    {
        LOG.info("load(): Loading project configuration from "+baseDir);
        Validate.notNull(baseDir, "baseDir must not be NULL");
        Validate.notNull(in, "in must not be NULL");
        
        final Properties props = new Properties();
        props.load( in );
        
        final ProjectConfiguration config = new ProjectConfiguration();
        for ( String key : props.stringPropertyNames() ) 
        {
            final String value = props.getProperty( key );
            System.out.println("===> Got property: "+key+"="+value);
            
            switch( key ) 
            {
                case "projectName": config.setProjectName( value ); break;
                case "uploadCommand" : config.setUploadCommand( value ); break;
                case "outputFormat" : config.setOutputFormat( OutputFormat.valueOf( value ) ); break;
                case "architecture" : config.setArchitecture( Architecture.valueOf( value ) ); break;
                case "outputName": config.setOutputName( value); break;
                case "sourceFolder": config.setSourceFolder( value); break;
                case "compilationRoot": config.setCompilationRoot( value); break;
                default:
                    LOG.warn("load(): Ignored unknown property '"+key+"'");
            }
        }
        
        config.setBaseDir( baseDir );
        
        final CompilerSettings settings = new CompilerSettings();
        if ( props.containsKey( "failOnAddressOutOfBounds" ) ) 
        {
            settings.setFailOnAddressOutOfRange( Boolean.valueOf( props.getProperty( "failOnAddressOutOfBounds"  ) ) );
        }
        config.setCompilerSettings( settings );
        
        return config;
    }
    
    public void setProjectName(String projectName) {
        Validate.notBlank(projectName, "projectName must not be NULL or blank");
        this.projectName = projectName;
    }
    
    public IArchitecture getArchitecture() {
        return architecture;
    }
    
    public void setArchitecture(Architecture architecture) 
    {
        Validate.notNull(architecture, "architecture must not be NULL");
        if ( this.architecture.getType() != architecture ) 
        {
            this.architecture = architecture.createImplementation();
        }
    }
    
    public CompilerSettings getCompilerSettings() {
        return compilerSettings.createCopy();
    }
    
    public void setCompilerSettings(CompilerSettings compilerSettings) 
    {
        Validate.notNull(compilerSettings, "compilerSettings must not be NULL");
        this.compilerSettings = compilerSettings.createCopy();
        this.compilerSettings = compilerSettings;
    }
    
    public String getProjectName() {
        return projectName;
    }
    
    public String getSourceFolder() {
        return sourceFolder;
    }

    public void setSourceFolder(String sourceFolder) 
    {
        Validate.notBlank(sourceFolder, "sourceFolder must not be NULL or blank");
        this.sourceFolder = sourceFolder;
    }
    
    public String getCompilationRoot() {
        return compilationRoot;
    }
    
    public void setCompilationRoot(String compilationRoot) 
    {
        Validate.notBlank(compilationRoot,"compilationRoot must not be NULL or blank");
        this.compilationRoot = compilationRoot;
    }
    
    public Resource getSourceFile(String name) throws IOException 
    {
        final File srcFolder = new File( getBaseDir() , sourceFolder );
        if ( ! srcFolder.exists() ) {
            if ( ! srcFolder.mkdirs() ) {
                throw new IOException("Failed to create source folder "+srcFolder);
            }
        }
        final File file = new File( srcFolder , name );
        return new FileResource( file , Resource.ENCODING_UTF );
    }
    
    public Resource getCompilationRootResource() throws IOException 
    {
        return getSourceFile( getCompilationRoot() );
    }
    
}