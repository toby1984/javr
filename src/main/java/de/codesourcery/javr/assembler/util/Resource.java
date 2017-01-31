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
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Abstract resource.
 *
 * @author tobias.gierke@code-sourcery.de
 */
public interface Resource 
{
    public static final String ENCODING_UTF = "utf8";
    
    public static Resource file(File file) throws IOException 
    {
    	return new FileResource( file , ENCODING_UTF );
    }
    
    public static Resource file(String file) throws IOException 
    {
    	return new FileResource( file , ENCODING_UTF );
    }
    
    public static Resource forString(String resourceName,String resourceValue) {
        return new StringResource(resourceName,resourceValue);
    }
    
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
    
    public String getName();
}