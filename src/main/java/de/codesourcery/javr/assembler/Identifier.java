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

import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;

public final class Identifier {

    private static final Pattern ID_PATTERN = Pattern.compile("^[a-zA-Z]+[_a-zA-Z0-9]*$");
    
    public final String value;
    
    public Identifier(String value) 
    {
        if ( ! isValidIdentifier( value ) ) {
            throw new IllegalArgumentException("Not a valid identifier: '"+value+"'");
        }
        this.value = value;
    }
    
    @Override
    public boolean equals(Object obj) {
        if ( obj instanceof Identifier) {
            return this.value.equals( ((Identifier) obj).value );
        }
        return false;
    }
    
    @Override
    public int hashCode() {
        return value.hashCode();
    }
    
    @Override
    public String toString() {
        return value;
    }

    public static boolean isValidIdentifier(String s) 
    {
        if ( ! StringUtils.isBlank( s ) ) 
        {
            return ID_PATTERN.matcher( s ).matches();
        }
        return false;
    }
}
