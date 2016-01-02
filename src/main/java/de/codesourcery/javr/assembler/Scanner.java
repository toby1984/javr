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
package de.codesourcery.javr.assembler;

import org.apache.commons.lang3.Validate;

import de.codesourcery.javr.assembler.exceptions.ParseException;

public class Scanner {

    private final String input;
    private int offset;
    
    public Scanner(String input) 
    {
        Validate.notNull(input, "input must not be NULL");
        this.input=input;
    }
    
    public boolean eof() {
        return offset >= input.length();
    }
    
    public void pushBack() {
        if ( offset == 0 ) {
            throw new IllegalStateException("Cannot push back at offset 0");
        }
        offset--;
    }
    
    public char peek() {
        if ( eof() ) {
            throw new ParseException("Already at end of input",offset);
        }        
        return input.charAt( offset );
    }
    
    public char next() 
    {
        if ( eof() ) {
            throw new ParseException("Already at end of input",offset);
        }
        return input.charAt( offset++ );
    }
    
    public int offset() {
        return offset;
    }
    
    public void setOffset(int offset) {
        if ( offset < 0 ) {
            throw new IllegalArgumentException("Invalid offset: "+offset);
        }
        this.offset = offset;
    }
}
