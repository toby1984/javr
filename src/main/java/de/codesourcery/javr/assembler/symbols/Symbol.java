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
package de.codesourcery.javr.assembler.symbols;

import org.apache.commons.lang3.Validate;

import de.codesourcery.javr.assembler.parser.Identifier;

public abstract class Symbol<T,VALUETYPE> 
{
    private final Identifier name;
    private final T node;
    private Type type;
    private VALUETYPE value;
    
    public static enum Type 
    {
        LABEL,
        EQU
    }
    
    public Symbol(Identifier name,Type type,T node) 
    {
        Validate.notNull(name, "name must not be NULL");
        Validate.notNull(node, "node must not be NULL");
        Validate.notNull(type, "type must not be NULL");
        this.name = name;
        this.node = node;
        this.type = type;
    }
    
    public final Type getType() {
        return type;
    }
    
    public final boolean hasType(Type t) {
        return t.equals( this.type );
    }
    
    public final Identifier name() {
        return name;
    }
    
    public final T getNode() {
        return node;
    }
    
    public final VALUETYPE getValue() {
        return value;
    }
    
    public final void setValue(VALUETYPE value) {
        Validate.notNull(value, "value must not be NULL");
        this.value = value;
    }
}
