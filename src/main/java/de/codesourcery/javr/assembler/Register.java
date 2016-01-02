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

public class Register {

    private String name;
    
    public Register(String name) {
        Validate.notBlank(name, "name must not be NULL or blank");
        this.name = name.toLowerCase();
    }
    
    @Override
    public String toString() {
        return name;
    }
    
    @Override
    public boolean equals(Object obj) 
    {
        if ( obj instanceof Register) 
        {
            return this.name.equals( ((Register) obj).name );
        }
        return false;
    }
    
    @Override
    public int hashCode() 
    {
        return name.toLowerCase().hashCode();
    }
    
    public String name() {
        return name;
    }
}