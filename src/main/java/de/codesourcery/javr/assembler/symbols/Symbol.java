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
import org.apache.log4j.Logger;

import de.codesourcery.javr.assembler.Address;
import de.codesourcery.javr.assembler.CompilationUnit;
import de.codesourcery.javr.assembler.parser.Identifier;
import de.codesourcery.javr.assembler.parser.ast.ASTNode;
import de.codesourcery.javr.assembler.parser.ast.IValueNode;

/**
 * An entry in the {@link SymbolTable}.
 *
 * @author tobias.gierke@code-sourcery.de
 */
public final class Symbol 
{
    private static final Logger LOG = Logger.getLogger(Symbol.class);
    
    private final Identifier name;
    private final ASTNode node;
    private final CompilationUnit compilationUnit;
    private Type type;
    private Object value;
    
    public static enum Type 
    {
        /**
         * Symbol marks a memory address.
         * 
         * Value is of type {@link Address}.
         */
        ADDRESS_LABEL,
        /**
         * Symbol was defined through an <code>.equ</code> directive.
         * Value is the result of {@link IValueNode#getValue()} if the
         * symbol could be resolved.
         */
        EQU,
        /**
         * Symbol was defined through a <code>#define xxxxx &lt;expr&gt;</code> directive.
         */
        PREPROCESSOR_MACRO,
        /**
         * Symbol was declared but not defined yet (=unresolved symbol).
         */        
        UNDEFINED
    }
    
    public Symbol(Identifier name,Type type,CompilationUnit compilationUnit, ASTNode node) 
    {
        Validate.notNull(compilationUnit, "com must not be NULL");
        Validate.notNull(name, "name must not be NULL");
        Validate.notNull(type, "type must not be NULL");
        if ( type != Type.UNDEFINED && type != Type.PREPROCESSOR_MACRO ) {
            Validate.notNull(node, "node must not be NULL");
        }
        this.compilationUnit = compilationUnit;
        this.name = name;
        this.node = node;
        this.type = type;
    }
    
    @Override
    public String toString() {
        return name.getValue()+" [ "+type+" ] , value= "+value+", hash code "+hashCode();
    }
    
    public CompilationUnit getCompilationUnit() {
        return compilationUnit;
    }
    
    public final Type getType() {
        return type;
    }
    
    public final boolean hasType(Type t) {
        return t.equals( this.type );
    }
    
    public boolean isUnresolved() {
        return hasType( Type.UNDEFINED ) || getValue() == null;
    }
    
    public final Identifier name() {
        return name;
    }
    
    public final ASTNode getNode() {
        return node;
    }
    
    public final Object getValue() {
        return value;
    }
    
    public final void setValue(Object value,Type type) 
    {
        Validate.notNull(type, "type must not be NULL");
        
        if ( LOG.isDebugEnabled() ) {
            LOG.debug("setValue( "+name+","+type+" ) = "+value);
        }
        
        Validate.notNull(value, "value for symbol '"+name+"' must not be NULL");
        if ( ! hasType(Type.UNDEFINED ) ) 
        {
            throw new IllegalStateException("Refusing to set value on symbol that already has a type "+this);
        }        
        this.type = type;
        this.value = value;
    }    
    
    public final void setValue(Object value) 
    {
        LOG.debug("setValue( "+name+") = "+value);
        Validate.notNull(value, "value for symbol '"+name+"' must not be NULL");
        if ( hasType(Type.UNDEFINED ) ) {
            throw new IllegalStateException("Setting a value on a UNDEFINED symbol requires a type, use setValue(Object,Type) instead.");
        }
        this.value = value;
    }
}