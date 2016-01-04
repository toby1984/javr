package de.codesourcery.javr.assembler.util;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class HashingAlgorithm 
{
    private static final char[] HEX_CHARS = new char[] { '0','1','2','3','4','5','6','7','8','9','a','b','c','d','e','f' };
    
    private final MessageDigest md;
    
    public HashingAlgorithm() 
    {
        try {
            md = MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }
    
    public HashingAlgorithm update(byte[] data,int offset,int len) 
    {
        md.update( data , 0 , len );
        return this;
    }
    
    public String finish() {
        byte[] hash = md.digest();
        return toHexString( hash );
    }
    
    private static String toHexString(byte[] value) 
    {
        final StringBuilder result = new StringBuilder();
        for ( int i = 0 ; i < value.length ; i++ ) 
        {
            final int v = value[i];
            final char hi = HEX_CHARS[ (v & 0b11110000) >> 4 ];
            final char lo = HEX_CHARS[  v & 0b00001111 ];
            result.append( hi ).append( lo );
        }
        return result.toString();
    }
}
