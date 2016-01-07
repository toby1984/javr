package de.codesourcery.javr.assembler.util;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * 
 * <p>
 * </p>
 * @author tobias.gierke@code-sourcery.de
 */
public interface Resource 
{
    public static final String ENCODING_UTF = "utf8";
    
    public InputStream createInputStream() throws IOException;
    
    public OutputStream createOutputStream() throws IOException;
    
    /**
     * Check whether this instance uses the same underlying storage (location)
     * as another resource.
     * 
     * @param obj
     * @return
     */
    public boolean pointsToSameData(Resource other);
    
    public int size();
    
    public boolean exists();
    
    public String contentHash();
    
    public String getEncoding();
    
    public void delete() throws IOException;
}