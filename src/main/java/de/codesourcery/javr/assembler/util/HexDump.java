package de.codesourcery.javr.assembler.util;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;

public class HexDump 
{
    private static final char[] chars = { '0' , '1','2','3','4','5','6','7','8','9','a','b','c','d','e','f' };
    
    private int bytesPerRow=16;
    private boolean printAscii;
    
    private final StringBuilder hexPart = new StringBuilder(); 
    private final StringBuilder asciiPart = new StringBuilder();
    private final StringBuilder result = new StringBuilder();
    
    private int currentAddress;
    private boolean moreThanOneRow;
    private String bytePrefix = "";
    private String byteDelimiter = " ";
    
    public String hexDump(int startAddress,byte[] data,int bytesToPrint) 
    {
        moreThanOneRow = false;
        currentAddress = startAddress;
        int bytesInThisRow = 0;
        result.setLength( 0 );
        hexPart.setLength( 0 );
        asciiPart.setLength( 0 );            
        for ( int i = 0 ; i < bytesToPrint ; i++ ) 
        {
            if ( hexPart.length() > 0 ) {
                hexPart.append( byteDelimiter );
            }
            hexPart.append( bytePrefix );
            appendHexByte( data[i] , hexPart );
            if ( printAscii ) 
            {
                final char c = (char) (data[i] & 0xff);
                asciiPart.append( c >= 32 && c < 127 ? c : '.' );
            }
            if ( ++bytesInThisRow == bytesPerRow ) 
            {
                final boolean moreToPrint = (i+1) < bytesToPrint;
                emitRow( moreToPrint );
                hexPart.setLength( 0 );
                asciiPart.setLength( 0 );
                moreThanOneRow |= moreToPrint;
            }
        }
        emitRow(false);
        
        final String tmpResult = result.toString();
        // release memory
        result.setLength( 0 );
        hexPart.setLength( 0 );
        asciiPart.setLength( 0 );    
        result.trimToSize();
        hexPart.trimToSize();
        asciiPart.trimToSize();
        return tmpResult;
    }
    
    private void emitRow(boolean appendNewline) 
    {
        if ( hexPart.length() > 0 ) 
        {
            final String hex = hexPart.toString();
            final int padLen = bytesPerRow*(2+bytePrefix.length()) + (bytesPerRow-1);
            final String paddedHex = printAscii && moreThanOneRow ? StringUtils.rightPad( hex , padLen , byteDelimiter ) : hex; 
            result.append( "0x" );
            appendHexWord( currentAddress , result ).append(": ").append( paddedHex );
            
            if ( printAscii ) {
                result.append(" : ").append( asciiPart );
            }
            if ( appendNewline ) {
                result.append("\n");
            }
            currentAddress += bytesPerRow;
        }
    }
    
    private static StringBuilder appendHexWord(int value,StringBuilder buffer) 
    {
        appendHexByte( value >> 8 , buffer );
        return appendHexByte( value , buffer );
    }
    
    private static StringBuilder appendHexByte(int value,StringBuilder buffer) 
    {
        final int low = value & 0x0f;
        final int hi = (value >> 4) & 0x0f;
        buffer.append( chars[ hi ] ).append( chars[ low ] );
        return buffer;
    }
    
    public HexDump printASCII(boolean printAscii) {
        this.printAscii = printAscii;
        return this;
    }
    
    public HexDump bytesPerRow(int bytesPerRow) {
        if ( bytesPerRow < 1 ) {
            throw new IllegalArgumentException("Bytes per row needs to be >= 1");
        }
        this.bytesPerRow = bytesPerRow;
        return this;
    }
    
    public HexDump bytePrefix(String bytePrefix) {
        Validate.notNull(bytePrefix, "bytePrefix must not be NULL");
        this.bytePrefix = bytePrefix;
        return this;
    }
    
    public HexDump byteDelimiter(String byteDelimiter) {
        Validate.notNull(byteDelimiter, "byteDelimiter must not be NULL");
        this.byteDelimiter = byteDelimiter;
        return this;
    }
}
