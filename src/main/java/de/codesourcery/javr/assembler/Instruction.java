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
package de.codesourcery.javr.assembler;

import org.apache.commons.lang3.Validate;

/**
 * Wraps the string representation of a CPU instruction/mnemonic.
 *
 * TODO: Maybe remove this class, doesn't seem to have any useful value right now ??
 * 
 * @author tobias.gierke@code-sourcery.de
 */
public class Instruction 
{
    private final String name;
    
    public Instruction(String name) 
    {
        Validate.notBlank(name, "name must not be NULL or blank");
        this.name = name.toLowerCase();
    }
    
    public boolean equals(Object obj) {
        if ( obj instanceof Instruction) {
            return this.name.equals( ((Instruction) obj).name );
        }
        return false;
    }
    
    public Instruction createCopy() {
        return this;
    }
    
    public String getMnemonic() {
        return name;
    }
    
    @Override
    public int hashCode() {
        return name.hashCode();
    }
    
    @Override
    public String toString() {
        return name;
    }
}
