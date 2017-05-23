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
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;
import org.apache.log4j.Logger;

import de.codesourcery.javr.assembler.CompilerSettings;
import de.codesourcery.javr.assembler.ResourceFactory;
import de.codesourcery.javr.assembler.Segment;
import de.codesourcery.javr.assembler.arch.Architecture;
import de.codesourcery.javr.assembler.arch.IArchitecture;
import de.codesourcery.javr.assembler.util.FileResource;
import de.codesourcery.javr.assembler.util.FileResourceFactory;
import de.codesourcery.javr.assembler.util.Resource;
import de.codesourcery.javr.ui.IProject;
import de.codesourcery.javr.ui.IProject.ProjectType;
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
        {
            public boolean requiresRelocationInfo() {
                return true;
            }            
        };
        
        public boolean requiresRelocationInfo() {
            return false;
        }
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
    
    private FileResourceFactory resourceFactory;
    
    private ProjectType projectType = ProjectType.EXECUTABLE;
    private String projectName = "unnamed project";
    private String uploadCommand;
    private CompilerSettings compilerSettings = new CompilerSettings();
    private String sourceFolder = "src";
    
    private String editorIndentString = "  ";
    
    // Set of file endings that files needing compilation will have (so basically, everything that is not a .h file)
    private final Set<String> asmFileEndings = new HashSet<>( Arrays.asList( ".s" , ".asm" ) );
    
    private String sourceFileEncoding = "UTF-8";
    
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
        populateFrom( other );
    }
    
    public void populateFrom(ProjectConfiguration other) 
    {
        this.projectName = other.projectName;
        this.uploadCommand = other.uploadCommand;
        this.compilerSettings = other.compilerSettings.createCopy();
        this.sourceFolder = other.sourceFolder;
        this.sourceFileEncoding = other.sourceFileEncoding;
        this.asmFileEndings.addAll( other.asmFileEndings );
        this.resourceFactory = other.resourceFactory;        
        this.projectType = other.projectType;
    }
    
    public ProjectConfiguration() 
    {
        resourceFactory = (FileResourceFactory) FileResourceFactory.createInstance( Project.getCurrentWorkingDirectory() );
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
    }
    
    public void setOutputFormat(OutputFormat format,ProjectType type) 
    {
        this.projectType = type;
        getCompilerSettings().setOutputFormat( format );
    }
    
    public OutputFormat getOutputFormat() {
        return getCompilerSettings().getOutputFormat();
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
    
    public void save(OutputStream out) throws IOException 
    {
        Validate.notNull(out, "out must not be NULL");
        
        final Properties props = new Properties();
        props.put("projectName" , projectName );
        if ( uploadCommand != null ) {
            props.put("uploadCommand" , uploadCommand );
        }
        props.put( "sourceFolder" , sourceFolder );
        props.put( "outputFormat" , getCompilerSettings().getOutputFormat().name() );
        props.put( "projectType" , getProjectType().name() );
        props.put( "architecture" , getCompilerSettings().getArchitecture().getType().name() );
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
        final CompilerSettings settings = config.getCompilerSettings();
        
        for ( String key : props.stringPropertyNames() ) 
        {
            final String value = props.getProperty( key );
            System.out.println("===> Got property: "+key+"="+value);
            
            switch( key ) 
            {
                case "projectName": config.setProjectName( value ); break;
                case "uploadCommand" : config.setUploadCommand( value ); break;
                case "architecture" : settings.setArchitecture( Architecture.valueOf( value ).getImplementation() ); break;
                case "sourceFolder": config.setSourceFolder( value); break;
                default:
                    LOG.warn("load(): Ignored unknown property '"+key+"'");
            }
        }
        
        if ( props.containsKey("outputFormat") || props.containsKey("projectType" ) ) 
        { 
            final String output = props.getProperty( "outputFormat" );
            final String type = props.getProperty( "projectType" );
            final OutputFormat outputFormat = StringUtils.isBlank( output ) ? null : OutputFormat.valueOf( output );
            final ProjectType projType = StringUtils.isBlank( type ) ?  null : ProjectType.valueOf( type );
            
            // FIXME: Remove logic that deals with only one of the values present...this is only for backwards compatibility with older save files
            if ( outputFormat != null && projType != null )
            {
                config.setOutputFormat( outputFormat , projType );
            } 
            else if ( projType != null ) 
            {
                switch( projType ) 
                {
                    case EXECUTABLE:
                        config.setOutputFormat( OutputFormat.RAW , projType );
                        break;
                    case LIBRARY:
                        config.setOutputFormat( OutputFormat.ELF_RELOCATABLE, projType );
                        break;
                    default:
                        throw new RuntimeException("Unhandled switch/case: "+projType);
                }
            }
            else 
            {
                switch( outputFormat ) 
                {
                    case ELF_RELOCATABLE:
                        config.setOutputFormat( outputFormat , ProjectType.LIBRARY );
                        break;
                    case ELF_EXECUTABLE:
                    case INTEL_HEX:
                    case RAW:
                        config.setOutputFormat( outputFormat , ProjectType.EXECUTABLE);
                        break;
                    default:
                        break;
                    
                }
            }
        }
        
        config.setBaseDir( baseDir );
        
        if ( props.containsKey( "failOnAddressOutOfBounds" ) ) 
        {
            settings.setFailOnAddressOutOfRange( Boolean.valueOf( props.getProperty( "failOnAddressOutOfBounds"  ) ) );
        }
        config.setCompilerSettings( settings );
        
        return config;
    }
    
    public void setProjectName(String projectName) 
    {
        Validate.notBlank(projectName, "projectName must not be NULL or blank");
        this.projectName = projectName;
    }
    
    public IArchitecture getArchitecture() {
        return getCompilerSettings().getArchitecture();
    }
    
    public CompilerSettings getCompilerSettings() {
        return compilerSettings;
    }
    
    public void setCompilerSettings(CompilerSettings compilerSettings) 
    {
        Validate.notNull(compilerSettings, "compilerSettings must not be NULL");
        this.compilerSettings = compilerSettings.createCopy();
    }
    
    public String getProjectName() {
        return projectName;
    }
    
    /**
    * Returns the source folder's location relative to this project's root folder.     
     * @return
     */
    public String getSourceFolder() {
        return sourceFolder;
    }

    /**
     * Sets the source folder's location relative to this project's root folder.
     * 
     * @param sourceFolder
     */
    public void setSourceFolder(String sourceFolder) 
    {
        Validate.notBlank(sourceFolder, "sourceFolder must not be NULL or blank");
        this.sourceFolder = sourceFolder;
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
    
    @Override
    public List<Resource> getAllAssemblerFiles(IProject project) throws IOException {
        // TODO Auto-generated method stub
        throw new RuntimeException("method not implemented: getAllAssemblerFiles");
    }
    
    /**
     * Returns filename suffixes that all files needing compilation must have.
     * 
     *  The files need to be stored below the {@link #getSourceFolder()}. All
     *  filename suffixes are compared case-insensitive.
     *  
     * @param fileEndings
     */
    public void setAsmFileNameSuffixes(Set<String> fileEndings) {
        
        Validate.notNull(fileEndings, "fileEndings must not be NULL");
        if ( fileEndings == null || fileEndings.isEmpty() ) {
            throw new IllegalArgumentException("at least one file ending is required");
        }
        for ( String ending : fileEndings ) {
            if ( StringUtils.isBlank( ending ) ) {
                throw new IllegalArgumentException("NULL/blank file endings are not allowed");
            }
        }
        this.asmFileEndings.clear();
        this.asmFileEndings.addAll( fileEndings );
    }
    
    public Set<String> getAsmFileNameSuffixes() {
        return new HashSet<>( asmFileEndings );
    }
    
    public void setSourceFileEncoding(String sourceFileEncoding) {
        this.sourceFileEncoding = sourceFileEncoding;
    }
    
    public String getSourceFileEncoding() {
        return sourceFileEncoding;
    }
    
    public String getEditorIndentString() {
        return editorIndentString;
    }
    
    public void setEditorIndentString(String editorIndentString) {
        Validate.notNull(editorIndentString,"editorIndentString must not be NULL");
        this.editorIndentString = editorIndentString;
    }
    
    public ProjectType getProjectType() {
        return projectType;
    }
}