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
package de.codesourcery.javr.assembler.parser;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;

import org.apache.commons.lang3.Validate;

import de.codesourcery.javr.assembler.exceptions.ParseException;
import de.codesourcery.javr.assembler.util.Resource;

public class Scanner {

    private int bufferSize;
    private int fetchSize;
    
    private final char[] buffer;
    
    private int minOffset;
    private int maxOffset;
    
    private int readPtr;
    private int writePtr;
    
    private int bytesAvailable;    
    
    private int totalBytesRead;
    
    private final Resource resource;
    private final Reader input;
    private int offset;
    private boolean eofReached = false;
    
    public Scanner(Resource res) 
    {
        this(res,1024);
    }
    
    public Scanner(Resource res,int bufferSize) 
    {
        Validate.notNull(res, "res must not be NULL");
        if ( bufferSize < 3 ) {
            throw new RuntimeException("Buffer size needs to be at least 3 bytes");
        }
        this.resource = res;
        this.bufferSize = bufferSize;
        this.buffer = new char[bufferSize];
        this.fetchSize = this.bufferSize/2;
        try {
            this.input = new InputStreamReader( res.createInputStream() , res.getEncoding() );
            fillBuffer();
        } catch(IOException e) {
            throw new ParseException("Failed to read input stream",0,e);
        }
    }
    
    public Resource getResource() {
        return resource;
    }
    
    private void fillBuffer() 
    {
        if ( eofReached ) 
        {
            return;
        }
        
        try 
        {
            final int spaceInBuffer = bufferSize - bytesAvailable;
            // don't fill the whole remaining space as this would make backtracking past the current
            // readPtr location impossible (because it would overwrite old data) 
            final int bytesToRead = Math.min( spaceInBuffer , fetchSize );

            int bytesRead;
            if ( (writePtr+bytesToRead) <= bufferSize ) 
            {
                bytesRead = input.read( buffer , writePtr , bytesToRead );
            }  
            else 
            {
	            final int firstSliceLen = bufferSize - writePtr;
	            bytesRead = input.read( buffer , writePtr , firstSliceLen );
	            if ( bytesRead <= 0 ) 
	            {
	                closeQuietly();
	                return;
	            }
	            final int secondSliceLen = bytesToRead - firstSliceLen;
	            final int len = input.read( buffer , 0 , secondSliceLen );
	            if ( len > 0 ) {
	                bytesRead += len;
	            } else {
	            	closeQuietly();
	            }
            }
            if ( bytesRead <= 0 ) {
            	closeQuietly();
            	return;
            }

            totalBytesRead += bytesRead;            
            bytesAvailable += bytesRead;                
            writePtr = (writePtr+bytesRead)%bufferSize;
         
            if ( maxOffset >= bufferSize ) 
            {
                maxOffset += bytesRead;
            	minOffset += bytesRead;
            } 
            else if ( maxOffset < bufferSize && (maxOffset+bytesRead) >= bufferSize ) {
            	maxOffset += bytesRead;
            	minOffset += (maxOffset-bufferSize);            	
            } else {
                maxOffset += bytesRead;
            }
            System.out.println("fillBuffer(): min="+minOffset+",max="+maxOffset+",readPtr: "+readPtr+",writePtr: "+writePtr);
        } 
        catch(IOException e) 
        {
            bytesAvailable = 0;
            closeQuietly();
            throw new ParseException("Failed to read input stream @ "+offset,offset,e);
        }
    }
    
    private void closeQuietly() 
    {
        eofReached = true;
        try { input.close(); } catch(IOException e) { /* can't help it */ }
    }
    
    public boolean eof() 
    {
        if ( bytesAvailable == 0 ) 
        {
            if ( eofReached ) {
                return true;
            }
            fillBuffer();
            return bytesAvailable == 0 && eofReached;
        }
        return false;
    }
    
    public char peek() 
    {
        if ( eof() ) {
            throw new ParseException("Already at end of input",offset);
        }        
        return buffer[ readPtr ];
    }
    
    public char next() 
    {
        if ( eof() ) {
            throw new ParseException("Already at end of input",offset);
        }
        final char result = buffer[readPtr];
        offset++;
        readPtr = ( readPtr +1 ) % bufferSize;
        bytesAvailable--;
        return result;
    }
    
    public int offset() {
        return offset;
    }

    public void pushBack() 
    {
        setOffset(this.offset-1);
    }    
    
    public void setOffset(int offset) 
    {
    	System.out.println("setOffset("+offset+"): current="+offset+",min="+minOffset+",max="+maxOffset+",readPtr: "+readPtr+",writePtr: "+writePtr);        	
        if ( offset < minOffset || offset > maxOffset ) 
        {
            throw new IllegalStateException("Cannot go to offset "+offset+", buffer contents either lost or not available yet");
        }        
        final int delta = offset - this.offset;
        int newPtr = readPtr;
        for ( int toSkip = Math.abs( delta ); toSkip > 0 ; toSkip-- ) 
        {
            if ( delta < 0 )
            {
                newPtr--;
                if ( newPtr < 0 ) {
                    newPtr += bufferSize;
                }
            } 
            else 
            {
                newPtr = (newPtr+1) % bufferSize;
            }
        }
        readPtr = newPtr;
        bytesAvailable -= delta;
        this.offset=offset;
    }
}