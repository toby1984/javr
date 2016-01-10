package de.codesourcery.javr.assembler.util;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class StringResource implements Resource {

    private final String encoding = "UTF8";
    
    private final InMemoryResource res;
    
    public StringResource(String name,String value) 
    {
        res = new InMemoryResource(name,encoding);
        
        try ( OutputStream out = res.createOutputStream(); ) 
        {
            out.write( value.getBytes() ); 
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
    
    @Override
    public boolean pointsToSameData(Resource other) {
        return other == this;
    }
    
    @Override
    public InputStream createInputStream() throws IOException {
        return res.createInputStream();
    }

    @Override
    public OutputStream createOutputStream() throws IOException {
        return res.createOutputStream();
    }

    @Override
    public int size() {
        return res.size();
    }

    @Override
    public String toString() {
        return res.toString();
    }
    
    @Override
    public boolean exists() {
        return true;
    }

    @Override
    public String contentHash() {
        return res.contentHash();
    }
    
    @Override
    public String getEncoding() {
        return encoding;
    }

    @Override
    public void delete() throws IOException {
        res.delete();
    }
}