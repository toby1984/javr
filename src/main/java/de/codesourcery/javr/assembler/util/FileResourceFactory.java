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
    public Resource resolveResource(Resource parent, String child) throws IOException {
        return new FileResource( new File(baseDir,child) ,  Resource.ENCODING_UTF );
    }

    @Override
    public Resource resolveResource(String child) throws IOException 
    {
        return new FileResource( new File(baseDir , child ) , Resource.ENCODING_UTF );
    }    
}