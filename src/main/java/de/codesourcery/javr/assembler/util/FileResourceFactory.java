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