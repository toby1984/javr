package de.codesourcery.javr.assembler;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;

public final class Address {

    private final Segment segment;
    private final int byteOffset;
    
    private Address(Segment s,int byteOffset) 
    {
        Validate.notNull(byteOffset, "byteOffset must not be NULL");
        if ( byteOffset < 0 ) {
            throw new IllegalArgumentException("Byte offset needs to be >= 0,was: "+byteOffset);
        }
        this.segment = s;
        this.byteOffset = byteOffset;
    }
    
    public int getByteAddress() {
        return byteOffset;
    }
    
    public int getWordAddress() 
    {
        if ( ( byteOffset & 1) != 0 ) {
            throw new IllegalStateException("Refusing to convert odd byte-address "+this+" into word address");
        }
        return byteOffset >> 1;
    }
    
    public Segment getSegment() {
        return segment;
    }
    
    public boolean hasSegment(Segment s) {
        return s.equals( this.segment );
    }
    
    @Override
    public String toString() {
        return "0x"+StringUtils.leftPad( Integer.toHexString( byteOffset ) , 4 , '0' )+" ("+segment+")";
    }
    
    public static Address byteAddress(Segment s,int byteOffset) 
    {
        return new Address(s,byteOffset);
    }
    
    public static Address wordAddress(Segment s,int byteOffset) 
    {
        if ( (byteOffset&1) != 0 ) {
            throw new IllegalArgumentException("Word-address must be even, was: "+byteOffset);
        }
        return new Address(s,byteOffset);
    }
}
