package de.codesourcery.javr.assembler.util;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;

public class Misc 
{
    private static final String CHARS = "0123456789abcdef";
    
    public static void main(String[] args) {
        System.out.println("Value: "+Integer.toHexString( parseHexInt( "ffffe5be" ) ));
    }
    
    /**
     * Convert a hexadecimal number literal into a 32-bit value.
     * 
     * <p>Unlike {@link Integer#parseInt(String, int)} this method does not
     * fail on negative values like "ffffe5be".</p>
     * 
     * @param value
     * @return
     */
    public static int parseHexInt(String value) 
    {
        Validate.notNull(value, "value must not be NULL");
        value = value.toLowerCase();
        if ( StringUtils.isBlank( value ) ) {
            throw new NumberFormatException("Not a valid hexadecimal number: "+value);
        }
        int result = 0;
        for ( int i = 0 , len = value.length() ; i < len ; i++) 
        {
            result <<= 4;
            final int idx = CHARS.indexOf( Character.toLowerCase( value.charAt( i ) ) );
            if ( idx == -1 ) {
                throw new NumberFormatException("Not a valid hexadecimal number: "+value);
            }
            result |= idx;
        }
        return result;
    }
}
