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
    private final String encoding;
    
    public FileResource(String s,String encoding) throws IOException {
        this( new File(s) , encoding );
    }
    
    public FileResource(File file,String encoding) throws IOException 
    {
        Validate.notNull(file, "file must not be NULL");
        Validate.notBlank(encoding,"encoding must not be NULL or blank");
        
        if ( file.exists() && ! file.isFile() ) {
            throw new IllegalArgumentException("Not a file: "+file.getAbsolutePath());
        }
        this.file = file;
        this.encoding = encoding;
        updateContentHash();
    }
    
    public static String nameFor(File file) {
        return "file://"+file;
    }
    
    @Override
    public String getName() 
    {
        return nameFor(this.file);
    }
    
    public File getFile() {
        return file;
    }
    
    @Override
    public boolean pointsToSameData(Resource other) 
    {
        return other == this || ( other instanceof FileResource && ((FileResource) other).file.equals( this.file ) );
    }
    
    private void updateContentHash() throws FileNotFoundException, IOException 
    {
        final HashingAlgorithm digest = new HashingAlgorithm();
        if ( ! file.exists() ) 
        {
            this.contentHash = digest.finish();
            return;
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
    public OutputStream createOutputStream() throws IOException 
    {
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

    @Override
    public String getEncoding() {
        return encoding;
    }

    @Override
    public void delete() throws IOException 
    {
        if ( exists() ) 
        {
            if ( ! file.delete() ) {
                throw new IOException("Failed to delete "+file.getAbsolutePath());
            }
        }
    }
    
    @Override
    public String toString() {
        return file.getAbsolutePath();
    }
}