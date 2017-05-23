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
import de.codesourcery.javr.assembler.Segment;
import de.codesourcery.javr.assembler.parser.Identifier;
import de.codesourcery.javr.assembler.parser.TextRegion;
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
    private TextRegion textRegion;
    private boolean isReferenced;
    private Segment segment;
    private ObjectType objectType=ObjectType.UNDEFINED;
    private int objectSize;
    
    public Symbol createShallowCopy() {
        return new Symbol(this);
    }
    
    /**
     * Creates a <b>SHALLOW</b> copy of another symbol.
     * 
     * Both symbols will share the same references to objects
     * they depend on.
     * 
     * @param other
     */
    public Symbol(Symbol other) {
        this( other , other.name );
    }
    
    private Symbol(Symbol other,Identifier name) 
    {
        this.name = name;
        this.node = other.node;
        this.compilationUnit = other.compilationUnit;
        this.type = other.type;
        this.value = other.value;
        this.textRegion = other.textRegion;
        this.isReferenced = other.isReferenced;
        this.segment = other.segment;
        this.objectType = other.objectType;
        this.objectSize = other.objectSize;
    }    
    
    /**
     * Creates a copy of this symbol with a different name.
     * 
     * @param name
     * @return
     */
    public Symbol withName(Identifier name) 
    {
        return new Symbol(this,name);
    }
    
    public static enum ObjectType 
    {
        FUNCTION,DATA,UNDEFINED;
    }
    
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
        Validate.notNull(compilationUnit, "internal error,compilation unit must not be NULL");
        Validate.notNull(name, "internal error,name must not be NULL");
        Validate.notNull(type, "internal error,type must not be NULL");
        if ( type != Type.UNDEFINED && type != Type.PREPROCESSOR_MACRO ) {
            Validate.notNull(node, "node must not be NULL");
        }
        this.compilationUnit = compilationUnit;
        this.name = name;
        this.node = node;
        this.type = type;
        this.textRegion = node != null ? node.getTextRegion() : null;
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
        if ( ! hasType(Type.UNDEFINED ) && ! type.equals( this.type ) ) 
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
    
    public TextRegion getTextRegion() 
    {
        final TextRegion result = node != null ? node.getTextRegion() : null;
        return result != null ? result : textRegion;
    }
    
    public void setTextRegion(TextRegion textRegion) {
        this.textRegion = textRegion;
    }
    
    /**
     * Marks this symbol as being referenced at least once somewhere.
     */
    public void markAsReferenced() {
        this.isReferenced = true;
    }
    
    /**
     * Returns whether this symbol is referenced at least once somewhere.
     * 
     * @return
     */
    public boolean isReferenced() {
        return isReferenced;
    }
    
    /**
     * Check whether this symbol has a type that is not {@link Type#UNDEFINED}.
     * 
     * @return
     */
    public boolean isDefined() 
    {
        return this.getType() != Type.UNDEFINED;
    }
    
    /**
     * Returns whether this is a local label that uses the special
     * {@link Identifier#isLocalGlobalIdentifier(Identifier)} syntax for its identifier.
     * 
     * @return
     * @see #isLocalSymbol()
     * @see #isGlobalSymbol()
     */
    public boolean isLocalSymbol() {
        return Identifier.isLocalGlobalIdentifier( name() );
    }

    /**
     * Returns whether this is a global label.
     * 
     * @return
     */
    public boolean isGlobalSymbol() {
        return ! isLocalSymbol();
    }
    
    /**
     * Returns the local part of this symbol's name.
     * 
     * @return
     * @throws IllegalArgumentException if this is not a local symbol
     */
    public Identifier getLocalNamePart() {
        return Identifier.getLocalIdentifierPart( name() );
    }
    
    /**
     * Returns the global part of this symbol's name.
     * 
     * @return
     * @see #isGlobalLabel()
     * @see #isLocalLabel()
     * @see #getLocalNamePart()
     */
    public Identifier getGlobalNamePart() 
    {
        if ( isLocalSymbol() ) {
            return Identifier.getGlobalIdentifierPart( name() );
        }
        return name();
    }
    
    /**
     * Check whether this symbol has type {@link Type#UNDEFINED}.
     * 
     * @return
     */    
    public boolean isUndefined() 
    {
        return ! isDefined();
    }
    
    /**
     * Sets the segment this symbol resides in.
     * 
     * @param segment
     */
    public void setSegment(Segment segment) 
    {
        Validate.notNull(segment, "segment must not be NULL");
        this.segment = segment;
    }
    
    /**
     * Returns the segment this symbol resides in.
     * 
     * @return segment or <code>NULL</code> if this symbol is not related to any segment  in particular
     */
    public Segment getSegment() {
        return segment;
    }
    
    /**
     * Returns the type of object this label refers to (if applicable).
     * 
     * Currently only labels of type {@link Symbol.Type#ADDRESS_LABEL} have
     * an objec type set.
     * 
     * @return
     */
    public ObjectType getObjectType() {
        return objectType;
    }
    
    /**
     * Returns whether this symbol has a given object type.
     * 
     * @param type
     * @return
     * @see #getObjectType()
     * @see #setObjectType(ObjectType)
     */
    public boolean hasObjectType(ObjectType type) {
        return type.equals( this.objectType );
    }
    
    /**
     * Set the type of object this label refers to.
     * 
     * Currently only labels of type {@link Symbol.Type#ADDRESS_LABEL} have
     * an objec type set. 
     * 
     * @param objectType
     * @throws UnsupportedOperationException If trying to call this method on a symbol that is not an address symbol.
     * @see #getObjectType()
     * @see #hasObjectType(ObjectType)
     */
    public void setObjectType(ObjectType objectType) {
        Validate.notNull(objectType, "objectType must not be NULL");
        if ( ! hasType( Type.ADDRESS_LABEL ) ) {
            throw new UnsupportedOperationException("Only address labels may have an object type set");
        }
        this.objectType = objectType;
    }
    
    /**
     * Returns the size in bytes of the object associated with this label (if applicable)
     * 
     * This value is only available for symbols with object type {@link ObjectType#DATA}.
     * 
     * @return size in bytes or zero
     * @see #incObjectSize(int)
     */
    public int getObjectSize() {
        return objectSize;
    }
    
    /**
     * Increments the size in bytes of the object associated with this label (if applicable).
     * 
     * This method must only be called on symbols with object type {@link ObjectType#DATA}.
     * 
     * @return size in bytes or zero
     * @throws UnsupportedOperationException when trying to assign a size to any symbol that does have object type {@link ObjectType#DATA}.
     * @see #getObjectSize()
     */    
    public void incObjectSize(int incInBytes) 
    {
        if ( ! hasObjectType( ObjectType.DATA ) ) {
            throw new UnsupportedOperationException("Currently only symbols that refer to DATA may have a size assigned"); 
        }
        if ( incInBytes < 0 ) {
            throw new IllegalArgumentException("Size must be >= 0, was: "+incInBytes);
        }
        this.objectSize += incInBytes;
    }
}