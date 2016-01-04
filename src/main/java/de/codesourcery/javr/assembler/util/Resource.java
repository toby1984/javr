package de.codesourcery.javr.assembler.util;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public interface Resource 
{
    public InputStream createInputStream() throws IOException;
    
    public OutputStream createOutputStream() throws IOException;
    
    public int size();
    
    public boolean exists();
    
    public String contentHash();
}