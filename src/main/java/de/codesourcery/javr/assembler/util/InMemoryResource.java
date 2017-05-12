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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Objects;

import org.apache.commons.lang3.Validate;

public class InMemoryResource implements Resource 
{
    private final String encoding;
    
    private byte[] data = new byte[1024];
    private String contentHash;
    private int len = 0;
    
    private int openReaders = 0;
    private int openWriters = 0;
    
    private final String name;
    
    public InMemoryResource(String name,String encoding) 
    {
        Validate.notBlank(name, "name must not be NULL or blank");
        Validate.notBlank(encoding, "encoding must not be NULL or blank");
        this.encoding = encoding;
        this.name = name;
        updateContentHash();
    }
    
    @Override
    public String getName() {
        return "inmemory://"+name;
    }
    
    @Override
    public boolean pointsToSameData(Resource other) 
    {
        if ( other == this ) {
            return true;
        }
        if ( other instanceof InMemoryResource) 
        {
            final InMemoryResource obj = (InMemoryResource) other;
            return Objects.equals( this.contentHash , obj.contentHash );
        }
        return false;
    }    
    
    private void updateContentHash() 
    {
        this.contentHash = new HashingAlgorithm().update( this.data , 0 , len ).finish();
    }
    
    @Override
    public InputStream createInputStream() throws IOException 
    {
        open(false);
        return new InputStream() 
        {
            private int ptr = 0;
            
            @Override
            public void close() throws IOException 
            {
                InMemoryResource.this.close(false);
            }

            @Override
            public int read() throws IOException 
            {
                if ( ptr >= len ) {
                    return -1;
                }
                return data[ptr++] & 0xff;
            }
        };
    }

    private synchronized void open(boolean forWriting) 
    {
        if ( forWriting & openReaders > 0 ) {
            throw new IllegalStateException("readers are still open");
        }
        if ( openWriters > 0 ) {
            throw new IllegalStateException("writers are still open");
        }        
        if ( forWriting ) 
        {
            openWriters++;
            len = 0;
        } else {
            openReaders++;
        }
    }
    
    private synchronized void close(boolean forWriting) 
    {
        if ( forWriting ) 
        {
            if ( openWriters > 0 ) {
                openWriters--;
            }
            updateContentHash();
        }
        else 
        {
            if ( openReaders > 0 ) {
                openReaders--;
            }
        }
    }    
    
    @Override
    public OutputStream createOutputStream() throws IOException 
    {
        open(true);
        return new OutputStream() 
        {
            @Override
            public void close() throws IOException 
            {
                InMemoryResource.this.close(true);
            }
            
            @Override
            public void write(int b) throws IOException 
            {
                if ( len >= data.length ) 
                {
                    byte[] newData = new byte[ data.length*2 ];
                    System.arraycopy( data , 0 , newData , 0 , data.length );
                    data = newData;
                }
                data[ len++ ] = (byte) b;
            }
        };
    }

    @Override
    public boolean exists() {
        return true;
    }

    @Override
    public int size() {
        return len;
    }

    @Override
    public String contentHash() {
        return contentHash;
    }
    
    @Override
    public String toString() {
        return name+" (in-memory)";
    }
    
    @Override
    public String getEncoding() {
        return encoding;
    }

    @Override
    public void delete() throws IOException 
    {
        if ( openReaders > 0 ) {
            throw new IOException("readers are still open");
        }
        if ( openWriters > 0 ) {
            throw new IOException("writers are still open");
        }         
        data = new byte[0];
        len = 0;
    }
}