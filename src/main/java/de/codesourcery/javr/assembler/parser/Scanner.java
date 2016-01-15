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
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;

import org.apache.commons.lang3.Validate;

import de.codesourcery.javr.assembler.exceptions.ParseException;
import de.codesourcery.javr.assembler.util.Resource;

/**
 * Reads single characters from a {@link Resource}.
 * 
 * <p>Because of the fact that lexing ignores whitespace by default
 * and that the lexer reads/buffers tokens in advance , this class
 * needs to support the {@link #setOffset(int)} method to jump to
 * a specific offset in the input stream (but usually only a few characters 
 * forwards/backwards from the current read position).</p>
 * 
 * <p>This requirement is not met by the {@link InputStream#mark(int)}
 * functionality of the JDK since a.) the position that should be marked needs
 * to be known in advance which the parser doesn't and the <code>mark()</code>
 * method is an optional feature that is not implemented on all input streams.</p>
 * 
 * <p>This class uses a ring buffer and two special min/max pointers to keep
 * track of the region inside the buffer that may be jumped around in using
 * {@link #setOffset(int)} with no special support required by the <code>InputStream</code>
 * this scanner operates on.</p> 
 *
 * @author tobias.gierke@code-sourcery.de
 */
public class Scanner 
{
    public static final boolean DEBUG = false;
    
    // size of char buffer
    private int bufferSize;
    private final char[] buffer;
    
    // how many bytes to read from the underlying
    // input stream when the buffer runs empty.
    // This value is always half of the actual buffer size
    // so that back-tracking from the current read offset
    // by at least half a buffer is always possible.
    private int fetchSize;

    // valid offset range for setOffset(int) calls
    private int minOffset;
    private int maxOffset;
    
    // where to read the next character in the buffer
    private int readPtr;
    // where to write the next character in the buffer
    private int writePtr;
    
    // the number of bytes are in the buffer
    // before new data needs to be fetched from the underlying
    // input steam
    private int bytesAvailable;    
    
    // underlying input stream
    private final Reader input;
    
    // marker so we know that reading from the underlying input stream
    // is pointless (and it's in fact already closed)
    private boolean eofReached = false;
    
    // the current offset from the underlying InputStream
    // where the next character returned by next() / peek()
    // is read from
    private int offset;
    
    /**
     * Creates a scanner with a default buffer size of 1024 characters
     * and a backtracking window of 512 bytes.
     * 
     * Given that more than 1024 bytes have been consumed from this scanner,
     * it's always valid to backtrack at most <code>bufferSize+512</code> bytes from the largest 
     * valid read offset in the buffer (or less if one already moved the read pointer to an earlier offset).
     * 
     * @param res
     */
    public Scanner(Resource res) 
    {
        this(res,1024);
    }
    
    /**
     * Creates a scanner.
     * 
     * The size of the backtracking window is always set to <code>bufferSize/2</code> bytes.
     * 
     * Given that more than <code>bufferSize</code> bytes have been consumed from this scanner,
     * it's always valid to backtrack at most <code>bufferSize+bufferSize/2</code> bytes from the largest 
     * valid read offset in the buffer (or less if one already moved the read pointer to an earlier offset).
     * 
     * @param res
     */    
    public Scanner(Resource res,int bufferSize) 
    {
        Validate.notNull(res, "res must not be NULL");
        if ( bufferSize < 3 ) {
            throw new RuntimeException("Buffer size needs to be at least 3 bytes");
        }
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
            if ( DEBUG ) {
                System.out.println("fillBuffer(): min="+minOffset+",max="+maxOffset+",readPtr: "+readPtr+",writePtr: "+writePtr);
            }
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
    
    /**
     * Returns whether this scanner reached eof.
     * 
     * Trying to {@link peek()} or {@link #next()} on a scanner
     * whose <code>eof()</code> method returned false
     * will throw an {@link ParseException}.
     * 
     * @return
     */
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
    
    /**
     * Get the character at the current read offset without advancing the
     * read pointer.
     *  
     * @return
     * @throws ParseException if this scanner is already at {@link #eof()}.
     */
    public char peek() 
    {
        if ( eof() ) {
            throw new ParseException("Already at end of input",offset);
        }        
        return buffer[ readPtr ];
    }
    
    /**
     * Reads the next character at the current read offset and advances the
     * read pointer by one.
     *  
     * @return
     * @throws ParseException if this scanner is already at {@link #eof()}.
     */    
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
    
    /**
     * Returns the offset from the underlying input stream where the next character would be
     * read from.
     * 
     * @return
     */
    public int offset() {
        return offset;
    }

    /**
     * Moves the read pointer backwards by one character.
     * @throws IllegalStateException if the read pointer cannot be moved back any further (either because it's at the
     * start of the input stream or any characters in the ring buffer <b>before</b> the current read position have already
     * been overwritten with data from later in the input stream. 
     */
    public void pushBack() 
    {
        setOffset(this.offset-1);
    }    
    
    /**
     * Sets the read pointer to a specific offset within the underlying input stream.
     * 
     * @param offset
     * @throws IllegalStateException if the read pointer cannot be moved back any further (either because it's at the
     * start of the input stream or any characters in the ring buffer <b>before</b> the current read position have already
     * been overwritten with data from later in the input stream.      
     */
    public void setOffset(int offset) 
    {
        if ( DEBUG ) {
            System.out.println("setOffset("+offset+"): current="+offset+",min="+minOffset+",max="+maxOffset+",readPtr: "+readPtr+",writePtr: "+writePtr);
        }
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