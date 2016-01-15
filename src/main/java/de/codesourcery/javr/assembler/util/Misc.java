package de.codesourcery.javr.assembler.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;

import de.codesourcery.javr.assembler.exceptions.ParseException;

/**
 * Contains various utility methods.
 *
 * @author tobias.gierke@code-sourcery.de
 */
public class Misc 
{
    private static final String CHARS = "0123456789abcdef";
    
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
    
    protected static enum QuoteType {
        SINGLE,DOUBLE,NONE
    }
    
    public static List<String> expand(String input,Map<String,String> placeholders) 
    {
        final List<String> cmdLine = new ArrayList<>();
        final StringBuilder command = new StringBuilder();
        QuoteType quoted= QuoteType.NONE;
        for ( int i = 0 , len = input.length() ; i < len ; i++ )
        {
            final char c = input.charAt(i);
            if ( (c == '\'' || c == '"' ) ) 
            {
                command.append( c );
                if ( quoted == QuoteType.NONE ) {
                    quoted = c == '\'' ? QuoteType.SINGLE : QuoteType.DOUBLE;
                } else if ( quoted == QuoteType.DOUBLE && c == '"' ) {
                    quoted = QuoteType.NONE;
                } else if ( quoted == QuoteType.SINGLE && c == '\'' ) {
                    quoted = QuoteType.NONE;
                }
                continue;
            }
            if ( quoted == QuoteType.NONE && Character.isWhitespace( c ) ) 
            {
                if ( command.length() > 0 && StringUtils.isNotBlank( command.toString() ) ) 
                {
                    cmdLine.add( command.toString() );
                    command.setLength(0);
                }
                continue;
            }
            if ( c == '%' ) 
            {
                int end = i+1;
                while ( end < len )
                {
                    final char current = input.charAt(end);
                    if ( !( Character.isLetter( current ) || Character.isDigit( current ) ) ) 
                    {
                        break;
                    }
                    
                    end++;
                }
                if ( end == i ) {
                    throw new ParseException("'%' character lacks variable name",end);
                }

                final String key = input.substring( i+1 , end  );
                final String value = placeholders.get( key );
                if ( value == null ) {
                    throw new ParseException("Unknown placeholder '%"+key+"'", i );
                }
                command.append( value );
                i+= key.length();
                continue;
            } 
            command.append( c );
        }
        if ( command.length() > 0 && StringUtils.isNotBlank( command ) ) 
        {
            cmdLine.add( command.toString() );
        }        
        return cmdLine;
    }
}
