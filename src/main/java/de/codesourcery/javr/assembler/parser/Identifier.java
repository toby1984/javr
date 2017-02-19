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

import java.util.regex.Pattern;

import org.apache.commons.lang3.Validate;

/**
 * A valid identifier.
 *
 * @author tobias.gierke@code-sourcery.de
 */
public final class Identifier 
{
    public static final String LOCAL_GLOBAL_LABEL_SEPARATOR = ".";
    
    private static final Pattern ID_PATTERN = Pattern.compile("^[_]*[a-zA-Z]+[_a-zA-Z0-9]*$");
    
    public final String value;
    
    public Identifier(String value) 
    {
        if ( ! isValidIdentifier( value ) ) {
            throw new IllegalArgumentException("Not a valid identifier: '"+value+"'");
        }
        this.value = value;
    }
    
    private Identifier(String value,boolean dummy) 
    {
        this.value = value;
    }
    
    /**
     * Create a compound symbol from a local label and a global label.
     * 
     * @param globalPart name of the previous global label
     * @param localPart name of the local label
     * @return
     */
    public static Identifier newLocalGlobalIdentifier(Identifier globalPart,Identifier localPart) 
    {
        Validate.notNull(globalPart, "globalPart must not be NULL");
        Validate.notNull(localPart, "localPart must not be NULL");
        return new Identifier(globalPart+LOCAL_GLOBAL_LABEL_SEPARATOR+localPart,true);
    }
    
    /**
     * Returns a given identifier has the &lt;GLOBAL&gt.&lt;LOCAL&gt; syntax.
     * 
     * @param identifier
     * @return
     */
    public static boolean isLocalGlobalIdentifier(Identifier identifier ) {
        return identifier.value.contains( LOCAL_GLOBAL_LABEL_SEPARATOR );
    }
    
    public static Identifier getLocalIdentifierPart(Identifier localGlobal) 
    {
        if ( ! isLocalGlobalIdentifier( localGlobal ) ) {
            throw new IllegalArgumentException("Not a local-global identifier: "+localGlobal);
        }
        return new Identifier( localGlobal.value.split( Pattern.quote( LOCAL_GLOBAL_LABEL_SEPARATOR ) )[1] ); 
    }
    
    public static Identifier getGlobalIdentifierPart(Identifier localGlobal) 
    {
        if ( ! isLocalGlobalIdentifier( localGlobal ) ) {
            throw new IllegalArgumentException("Not a local-global identifier: "+localGlobal);
        }
        return new Identifier( localGlobal.value.split( Pattern.quote( LOCAL_GLOBAL_LABEL_SEPARATOR ) )[0] ); 
    }    
    
    public String getValue() {
        return value;
    }
    
    public static Identifier of(String s) {
        return new Identifier(s);
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
        if ( s != null  ) 
        {
            return ID_PATTERN.matcher( s ).matches();
        }
        return false;
    }
}