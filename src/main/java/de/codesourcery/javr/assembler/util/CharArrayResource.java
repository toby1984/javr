package de.codesourcery.javr.assembler.util;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class CharArrayResource implements Resource
{
    private final char[] array;
    private final int startOffset;
    private final int len;

    public CharArrayResource(char[] array, int startOffset, int len) {

        this.array = array;
        if ( startOffset <0 ) {
            throw new IllegalArgumentException( "offset must be >= 0" );
        }
        if ( len < 0 ) {
            throw new IllegalArgumentException( "len must be >= 0" );
        }
//        if ( startOffset >= len ) {
//            throw new IllegalArgumentException( "Start offset must be < len, start: "+startOffset+", len: "+len );
//        }
        this.startOffset = startOffset;
        this.len = len;
    }

    @Override
    public InputStream createInputStream() throws IOException
    {
        return new InputStream()
        {
            private int ptr = startOffset;
            private final int end = startOffset+len;

            @Override
            public int read() throws IOException
            {
                if ( ptr >= end ) {
                    return -1;
                }
                return array[ptr++];
            }
        };
    }

    @Override
    public OutputStream createOutputStream() throws IOException
    {
        throw new UnsupportedOperationException( "Not supported" );
    }

    @Override
    public boolean pointsToSameData(Resource other)
    {
        if ( other instanceof CharArrayResource)
        {
            final CharArrayResource oa = (CharArrayResource) other;
            if ( this.len != oa.len ) {
                return false;
            }
            for ( int i = 0,ptr1 = this.startOffset, ptr2 = oa.startOffset ; i < len ; i++,ptr1++,ptr2++ ) {
                if ( this.array[ptr1] != oa.array[ptr2] ) {
                    return false;
                }
            }
            return true;
        }
        return false;
    }

    @Override
    public int size()
    {
        return len;
    }

    @Override
    public boolean exists()
    {
        return true;
    }

    @Override
    public String getEncoding()
    {
        return "UTF8";
    }

    @Override
    public void delete() throws IOException
    {
        throw new UnsupportedOperationException( "delete()" );
    }

    @Override
    public String getName()
    {
        return "CharArrayResource";
    }
}