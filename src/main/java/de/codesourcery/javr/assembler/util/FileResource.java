package de.codesourcery.javr.assembler.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.apache.commons.lang3.Validate;

public class FileResource implements Resource 
{
    private final File file;
    private String contentHash;
    
    public FileResource(String s) throws IOException {
        this( new File(s) );
    }
    
    public FileResource(File file) throws IOException 
    {
        Validate.notNull(file, "file must not be NULL");
        this.file = file;
        updateContentHash();
    }
    
    private void updateContentHash() throws FileNotFoundException, IOException 
    {
        final HashingAlgorithm digest = new HashingAlgorithm();
        if ( ! file.exists() ) 
        {
            this.contentHash = digest.finish();
        }
        final byte[] buffer = new byte[1024];
        try ( InputStream in = new FileInputStream(file ) ) {
            int read = 0;
            while ( ( read = in.read( buffer ) ) != -1 ) {
                digest.update( buffer , 0 , read );
            }
        }
        this.contentHash = digest.finish();
    }

    @Override
    public InputStream createInputStream() throws IOException {
        return new FileInputStream(file);
    }

    @Override
    public OutputStream createOutputStream() throws IOException {
        return new FileOutputStream(file) 
        {
            @Override
            public void close() throws IOException {
                super.close();
                updateContentHash();
            }
        };
    }

    @Override
    public int size() {
        return (int) file.length();
    }

    @Override
    public boolean exists() 
    {
        return file.exists();
    }

    @Override
    public String contentHash() {
        return contentHash;
    }
}