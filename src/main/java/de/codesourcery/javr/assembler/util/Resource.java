/**
 * Copyright 2015-2018 Tobias Gierke <tobias.gierke@code-sourcery.de>
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
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

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

    public static Resource charArray(char[] array,int offset,int len) {
        return new CharArrayResource( array, offset, len );
    }

    public static Resource inputStream(InputStreamResource.StreamSupplier supplier, String encoding) {
        return new InputStreamResource( supplier, encoding );
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
    
    public default String contentHash()
    {
        try (InputStream stream = createInputStream() )
        {
            final MessageDigest digest = MessageDigest.getInstance( "MD5" );
            final byte[] buffer = new byte[10*1024];
            int len;
            while ( ( len = stream.read(buffer) ) > 0 )
            {
                digest.update( buffer, 0 , len );
            }
            final byte[] hash = digest.digest();
            return HexDump.toHex( hash, 0 , hash.length );

        }
        catch (IOException | NoSuchAlgorithmException e)
        {
            throw new RuntimeException(e);
        }
    }
    
    public String getEncoding();
    
    public void delete() throws IOException;
    
    public String getName();
}