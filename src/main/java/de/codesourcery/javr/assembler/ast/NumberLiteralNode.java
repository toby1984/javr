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
package de.codesourcery.javr.assembler.ast;

import org.apache.commons.lang3.StringUtils;

import de.codesourcery.javr.assembler.TextRegion;

public class NumberLiteralNode extends ASTNode 
{
    public static enum LiteralType {
        DECIMAL,
        HEXADECIMAL,
        BINARY;
    }
    
    private LiteralType type;
    private int value;
    
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
    
    public int getValue() 
    {
        return value;
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
            for ( int i = 2 , len= s.length() ; i < len ; i++ ) {
                if ( ! Character.isDigit( s.charAt( i ) ) ) {
                    return false;
                }
            }
            return true;
        }
        return false;
    }    
    
    public static boolean isBinaryNumber(String s) 
    {
        if ( StringUtils.isNotBlank( s ) && s.startsWith("%") && s.length() >= 2 ) 
        {
            for ( int i = 2 , len= s.length() ; i < len ; i++ ) 
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
