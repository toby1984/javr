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
package de.codesourcery.javr.assembler.util;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.lang3.Validate;

import de.codesourcery.javr.assembler.ResourceFactory;
import de.codesourcery.javr.ui.IProject;
import de.codesourcery.javr.ui.config.ProjectConfiguration;

public class FileResourceFactory implements ResourceFactory {

    private File baseDir2;
    
    public FileResourceFactory() {
    }
    
    public FileResourceFactory(File parentPath) 
    {
        Validate.notNull(parentPath, "parentPath must not be NULL or blank");
        this.baseDir2 = parentPath;
    }
    
    public static ResourceFactory createInstance(File parentPath) 
    {
        return new FileResourceFactory( parentPath );
    }    

    public File getBaseDir() {
        return baseDir2;
    }
    
    @Override
    public Resource resolveResource(Resource parent, String child) throws IOException 
    {
    	if ( child.startsWith("/" ) ) 
    	{
            return new FileResource( new File( getBaseDir() ,child) ,  Resource.ENCODING_UTF );
    	}
    	if ( parent instanceof FileResource) {
    		 final File root = ((FileResource) parent).getFile().getParentFile();
    		return new FileResource(new File(root , child ) , Resource.ENCODING_UTF ); 
    	}
    	throw new RuntimeException("Don't know how to resolve child '"+child+"' relative to "+parent);
    }

    @Override
    public Resource resolveResource(String child) throws IOException 
    {
        return new FileResource( new File(getBaseDir() , child ) , Resource.ENCODING_UTF );
    }

    @Override
    public List<Resource> getAllAssemblerFiles(IProject project) throws IOException 
    {
        final Set<String> suffixes = new HashSet<>();
        for ( String suffix : project.getConfiguration().getAsmFileNameSuffixes() ) {
            suffixes.add( suffix.toLowerCase() );
        }
        
        final ProjectConfiguration projectConfig = project.getConfiguration();
        final String srcFolder = projectConfig.getSourceFolder();
        final File folder = new File( projectConfig.getBaseDir() , srcFolder );
        final String encoding = projectConfig.getSourceFileEncoding();
        
        final List<Resource> result = new ArrayList<>();
        collect( folder , suffixes , encoding , result );
        return result;
    }    
    
    private void collect(File currentFile,Set<String> suffixes, String  fileEncoding, List<Resource> result ) throws IOException 
    {
        if ( currentFile.isDirectory() ) 
        {
            File[] files = currentFile.listFiles();
            if ( files == null ) {
                throw new IOException("Failed to list files in "+currentFile);
            }
            for ( File f : files ) {
                collect( f , suffixes , fileEncoding , result );
            }
        } 
        else
        {
            for ( String suffix : suffixes ) 
            {
                if ( currentFile.getName().toLowerCase().endsWith( suffix ) ) 
                {
                    result.add( new FileResource( currentFile , fileEncoding ) ); 
                    break;
                }
            }
        }
    }
}