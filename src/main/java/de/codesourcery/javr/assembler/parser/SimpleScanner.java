package de.codesourcery.javr.assembler.parser;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;

import de.codesourcery.javr.assembler.exceptions.ParseException;
import de.codesourcery.javr.assembler.util.Resource;

class SimpleScanner extends Scanner {

    private final char[] data;
    private int offset;
    
    public SimpleScanner(Resource res,int bufferSize)
    {
        this(res);
    }
    
    public SimpleScanner(Resource res)
    {
        super(res);
        
        char[] result = new char[0];
        char[] buffer = new char[1024];
        try ( Reader r = new InputStreamReader(res.createInputStream() ) ) 
        {
            while( true ) 
            {
                int len = r.read(buffer);
                if ( len <= 0 ) {
                    break;
                }
                final char[] tmp = new char[ result.length + len ];
                if ( result.length > 0 ) {
                    System.arraycopy( result , 0 , tmp , 0 , result.length );
                }
                final int start = tmp.length-len;
                System.arraycopy( buffer , 0 , tmp , start , len );
                result = tmp;
            }
        } catch(IOException e) {
            throw new RuntimeException(e);
        }
        data = result;
    }
    
    @Override
    public boolean eof() 
    {
        return offset >= data.length;
    }
    
    @Override
    public char peek() 
    {
        if ( eof() ) {
            throw new ParseException("Already at EOF",offset);
        }
        return data[offset];
    }
    
    @Override
    public char next() {
        if ( eof() ) {
            throw new ParseException("Premature end of input",offset);
        }
        return data[offset++];
    }
    
    @Override
    public int offset() {
        return offset;
    }
    
    @Override
    public void setOffset(int offset) 
    {
        if ( offset < 0 || offset > data.length ) {
            throw new IllegalArgumentException("Offset out of range: "+offset);
        }
        this.offset = offset; 
    }
    
    @Override
    public void pushBack() 
    {
        if ( offset == 0 ) {
            throw new IllegalArgumentException("Already at offset 0");
        }
        offset--;
    }
}