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
package de.codesourcery.javr.assembler.parser.ast;

import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;

import de.codesourcery.javr.assembler.parser.TextRegion;

public class NumberLiteralNode extends ASTNode implements IValueNode
{
    private static final Pattern HEX_PATTERN = Pattern.compile("^[0-9A-Fa-f]+$");
    
    public static enum LiteralType 
    {
        DECIMAL,
        HEXADECIMAL,
        BINARY;
    }
    
    private final LiteralType type;
    private final int value;
    
    private NumberLiteralNode(int value,LiteralType type,TextRegion region) {
        super(region);
        Validate.notNull(type, "type must not be NULL");
        this.type = type;
        this.value = value;
    }
    
    public NumberLiteralNode(String value, TextRegion region) 
    {
        super(region);
        if ( isHexadecimalNumber( value ) ) 
        {
            this.type = LiteralType.HEXADECIMAL;
            this.value = Integer.parseInt(value.substring(2 ) , 16 );
        } else if ( isBinaryNumber( value ) ) {
            this.type = LiteralType.BINARY;
            this.value = Integer.parseInt(value.substring(1) , 2 );
        } else if ( isDecimalNumber( value ) ) {
            this.type = LiteralType.DECIMAL;
            this.value = Integer.parseInt(value);
        } else {
            throw new IllegalArgumentException("Not a valid number literal: '"+value+"'");
        }
    }

    @Override
    protected NumberLiteralNode createCopy() 
    {
        return new NumberLiteralNode( this.value , this.type , getTextRegion().createCopy() );
    }
    
    @Override
    public Integer getValue() 
    {
        return Integer.valueOf( value );
    }    
    
    public LiteralType getType() {
        return type;
    }
    
    public static boolean isValidNumberLiteral(String s) 
    {
        return isHexadecimalNumber( s ) || isDecimalNumber( s ) || isBinaryNumber( s );
    }
    
    public static boolean isDecimalNumber(String s) 
    {
        if ( StringUtils.isNotBlank( s ) ) 
        {
            for ( int i = 0 , len= s.length() ; i < len ; i++ ) {
                if ( ! Character.isDigit( s.charAt( i ) ) ) {
                    return false;
                }
            }
            return true;
        }
        return false;
    }
    
    public static boolean isHexadecimalNumber(String s) 
    {
        if ( StringUtils.isNotBlank( s ) && s.startsWith("0x") && s.length() >= 3 ) 
        {
            return HEX_PATTERN.matcher( s.substring(2) ).matches();
        }
        return false;
    }    
    
    public static boolean isBinaryNumber(String s) 
    {
        if ( StringUtils.isNotBlank( s ) && s.startsWith("%") && s.length() >= 2 ) 
        {
            for ( int i = 1 , len= s.length() ; i < len ; i++ ) 
            {
                final char c = s.charAt(i);
                if ( c != '0' && c != '1' ) 
                {
                    return false;
                }
            }
            return true;
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
