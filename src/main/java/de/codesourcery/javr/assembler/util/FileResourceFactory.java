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

import org.apache.commons.lang3.Validate;

import de.codesourcery.javr.assembler.ResourceFactory;

public class FileResourceFactory implements ResourceFactory {

    private final File baseDir;
    
    private FileResourceFactory(File parentPath) 
    {
        Validate.notNull(parentPath, "parentPath must not be NULL or blank");
        this.baseDir = parentPath;
    }
    
    public static ResourceFactory createInstance(File parentPath) 
    {
        return new FileResourceFactory( parentPath );
    }    

    public File getBaseDir() {
        return baseDir;
    }
    
    @Override
    public Resource resolveResource(Resource parent, String child) throws IOException 
    {
    	if ( child.startsWith("/" ) ) 
    	{
            return new FileResource( new File(baseDir,child) ,  Resource.ENCODING_UTF );
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
        return new FileResource( new File(baseDir , child ) , Resource.ENCODING_UTF );
    }    
}