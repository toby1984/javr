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
package de.codesourcery.javr.assembler.parser;

import org.apache.commons.lang3.Validate;

public final class Token {

    public final int offset;
    public final String value;
    public final TokenType type;
    
    public Token(TokenType type,String value,int offset) 
    {
        Validate.notNull(type, "type must not be NULL");
        Validate.notNull(value, "value must not be NULL");
        
        if ( type != TokenType.EOF && value.equals("") ) 
        {
            throw new IllegalArgumentException("value must not be blank");
        }
        if ( offset < 0 ) {
            throw new IllegalArgumentException("Invalid offset: "+offset);
        }
        this.offset = offset;
        this.value = value;
        this.type = type;
    }
    
    public int endOffset() {
        return offset+value.length();
    }
    
    public boolean equals(Object o) 
    {
        if ( o instanceof Token) 
        {
            final Token other = (Token) o;
            return this.offset == other.offset &&
                   this.value.equals( other.value ) &&
                   this.type == other.type;
        }
        return false;
    }
    
    @Override
    public int hashCode() {
        int result = 31*offset;
        result = (31*result)+value.hashCode();
        result = (31*result)+type.hashCode();
        return 31*result;
    }
    
    public boolean hasType(TokenType t) {
        return t.equals( this.type );
    }
    
    public boolean isOperator(String op) 
    {
        Validate.notBlank(op, "op must not be NULL or blank");
        return hasType( TokenType.OPERATOR ) && value.equals( op );
    }
    
    public boolean hasType(TokenType t1,TokenType... t2) 
    {
        if ( t1.equals( this.type ) ) {
            return true;
        }
        if ( t2 != null ) 
        {
            for ( int i = 0, len=t2.length ; i < len ; i++ ) 
            {
                if ( t2[i].equals( this.type ) ) {
                    return true;
                }
            }
        }
        return false;
    }    
    
    @Override
    public String toString() 
    {
        return "Token[ "+type+" , '"+value+"' , offset="+offset+" ]";
    }
    
    public TextRegion region() 
    {
        return new TextRegion(this.offset, this.value.length() );
    }
    
    public boolean intersects(TextRegion r) 
    {
        return r.start() <= this.offset && this.offset < r.end();
    }
}