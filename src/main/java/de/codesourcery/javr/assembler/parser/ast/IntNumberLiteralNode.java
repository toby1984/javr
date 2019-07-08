/**
 * Copyright 2015-2018 Tobias Gierke <tobias.gierke@code-sourcery.de>
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
package de.codesourcery.javr.assembler.parser.ast;

import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;

import de.codesourcery.javr.assembler.parser.TextRegion;
import de.codesourcery.javr.assembler.util.Misc;

public class IntNumberLiteralNode extends AbstractASTNode implements IValueNode
{
    private static final Pattern HEX_PATTERN = Pattern.compile("^0x[0-9A-Fa-f]+(_*[0-9A-Fa-f]+)*$");
    
    public enum LiteralType
    {
        DECIMAL,
        HEXADECIMAL,
        BINARY;
    }
    
    private final LiteralType type;
    private final int value;

    public IntNumberLiteralNode(int value, LiteralType type, TextRegion region) {
        super(region);
        Validate.notNull(type, "type must not be NULL");
        this.type = type;
        this.value = value;
    }
    
    public IntNumberLiteralNode(String value, TextRegion region)
    {
        super(region);
        if ( isHexadecimalNumber( value ) ) 
        {
            this.type = LiteralType.HEXADECIMAL;
            this.value = Misc.parseHexInt( stripStartAndUnderscores(value,2) );
        } else if ( isBinaryNumber( value ) ) {
            this.type = LiteralType.BINARY;
            this.value = Integer.parseInt( stripStartAndUnderscores(value, 1) , 2 );
        } else if ( isDecimalNumber( value ) ) {
            this.type = LiteralType.DECIMAL;
            this.value = Integer.parseInt(stripStartAndUnderscores(value,0));
        } else {
            throw new IllegalArgumentException("Not a valid number literal: '"+value+"'");
        }
    }

    private static String stripStartAndUnderscores(String input,int charsAtStartToRemove) {

        final int len = input.length();
        if ( charsAtStartToRemove == 0 )
        {
            boolean containsUnderscore = false;
            for (int i = 0; i < len; i++)
            {
                if (input.charAt(i) == '_')
                {
                    containsUnderscore = true;
                    break;
                }
            }
            if (!containsUnderscore)
            {
                return input;
            }
        }
        final StringBuilder result = new StringBuilder(len);
        for ( int i = charsAtStartToRemove ; i < len ; i++ ) {
            final char c = input.charAt(i);
            if ( c != '_' ) {
                result.append(c);
            }
        }
        return result.toString();
    }
    
    @Override
    protected IntNumberLiteralNode createCopy()
    {
        return new IntNumberLiteralNode( this.value , this.type , getTextRegion().createCopy() );
    }
    
    @Override
    public Integer getValue() 
    {
        return value;
    }    
    
    public LiteralType getType() {
        return type;
    }
    
    public static boolean isValidNumberLiteral(String s) 
    {
        return isHexadecimalNumber( s ) || isBinaryNumber( s ) || isDecimalNumber( s );
    }
    
    private static boolean isDecimalNumber(String s)
    {
        if ( s != null && ! s.isEmpty() )
        {
            char c = s.charAt(0);
            if ( c == '_' || ! Character.isDigit(c ) ) {
                return false;
            }
            final int len = s.length();
            if ( s.charAt( len-1 ) == '_' ) {
                return false;
            }
            for ( int i = 1  ; i < len ; i++ )
            {
                c = s.charAt(i);
                if ( c != '_' && ! Character.isDigit(c) )
                {
                    return false;
                }
            }
            return true;
        }
        return false;
    }
    
    private static boolean isHexadecimalNumber(String s)
    {
        if ( s != null && s.startsWith("0x") && s.length() >= 3 )
        {
            return HEX_PATTERN.matcher( s ).matches();
        }
        return false;
    }    
    
    private static boolean isBinaryNumber(String s)
    {
        if (s != null)
        {
            final int len = s.length();
            if ( len >= 2 && s.charAt(0) == '%')
            {
                if (s.charAt(1) == '_')
                {
                    return false;
                }
                if (s.charAt(len - 1) == '_')
                {
                    return false;
                }
                for (int i = 1; i < len; i++)
                {
                    switch (s.charAt(i))
                    {
                        case '_':
                        case '0':
                        case '1':
                            break;
                        default:
                            return false;
                    }
                }
                return true;
            }
        }
        return false;
    }      
    
    @Override
    public String getAsString() {
        switch( type ) {
            case BINARY:
                return "%"+Integer.toBinaryString( value );
            case DECIMAL:
                return Integer.toString( value );
            case HEXADECIMAL:
                return "0x"+Integer.toHexString( value );
            default:
                throw new RuntimeException("Unreachable code reached");
        }
    }
}
