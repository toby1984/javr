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
package de.codesourcery.javr.assembler.parser.ast;

import org.apache.commons.lang3.Validate;

import de.codesourcery.javr.assembler.ICompilationContext;
import de.codesourcery.javr.assembler.exceptions.ParseException;
import de.codesourcery.javr.assembler.parser.Identifier;
import de.codesourcery.javr.assembler.parser.TextRegion;
import de.codesourcery.javr.assembler.symbols.Symbol;

public class DirectiveNode extends NodeWithMemoryLocation implements Resolvable
{
    private static final int SIZE_NOT_RESOLVED = -1;
    
    public final Directive directive;
    private int sizeInBytes = SIZE_NOT_RESOLVED;
    
    public static enum Directive 
    {
        CSEG("cseg",0,0),
        DSEG("dseg",0,0),
        ESEG("eseg",0,0),
        DEVICE("device",1,1),
        RESERVE("byte",1,1),
        INIT_BYTES("db",1,Integer.MAX_VALUE),
        INIT_WORDS("dw",1,Integer.MAX_VALUE),
        EQU("equ" , 1 , 1 );
        
        public final String literal;
        public final int minOperandCount;
        public final int maxOperandCount;
        
        private Directive(String literal,int minOperandCount,int maxOperandCount) 
        {
            this.literal = literal.toLowerCase();
            this.minOperandCount = minOperandCount;
            this.maxOperandCount = maxOperandCount;
        }
        
        public boolean isValidOperandCount(int actual) 
        {
            return minOperandCount <= actual && actual <= maxOperandCount;
        }
        
        public static Directive parse(String s) 
        {
            Directive[] v = values();
            for ( int i = 0, len = v.length ; i < len ; i++ ) {
                if ( s.equalsIgnoreCase( v[i].literal ) ) {
                    return v[i];
                }
            }
            return null;
        }
    }
    
    public boolean is(Directive d) {
        return d.equals( this.directive );
    }
    
    private DirectiveNode(Directive directive) 
    {
        Validate.notNull(directive, "directive must not be NULL");
        this.directive = directive;
    }    
    
    public DirectiveNode(Directive directive,TextRegion region) 
    {
        super(region);
        Validate.notNull(directive, "directive must not be NULL");
        this.directive = directive;
    }
    
    @Override
    protected DirectiveNode createCopy() {
        if ( getTextRegion() != null ) {
            return new DirectiveNode(this.directive , getTextRegion().createCopy() );
        }
        return new DirectiveNode(this.directive );
    }
    
    @Override
    public boolean hasMemoryLocation() 
    {
        switch( this.directive ) 
        {
            case INIT_BYTES:
            case INIT_WORDS:
            case RESERVE:
                return true;
            default:
                return false;
        }
    }
    
    public boolean resolveSize(ICompilationContext context)
    {
        if ( ! hasMemoryLocation() ) {
            return true;
        }
        switch( this.directive ) 
        {
            case INIT_BYTES: sizeInBytes = childCount(); break;
            case INIT_WORDS: sizeInBytes = childCount()*2; break;
            case RESERVE:
                final IValueNode child = (IValueNode) child(0); 
                if ( child instanceof Resolvable) {
                    if ( ! ((Resolvable) child).resolve( context ) ) {
                        return false;
                    }
                }
                Number value = (Number) child.getValue();
                if ( value != null ) 
                {
                    final int size = value.intValue();
                    if ( size < 0 ) {
                        throw new ParseException("Expected a positive number but got "+size,getTextRegion().start());
                    }
                    return true;
                }
                return value != null;
            default:
                throw new RuntimeException("Unreachable code reached");
        }  
        return sizeInBytes != SIZE_NOT_RESOLVED;
    }
    
    @Override
    public int getSizeInBytes() throws IllegalStateException 
    {
        if ( ! hasMemoryLocation() ) {
            throw new IllegalStateException( "This statement is not associated with a memory location" );
        }
        return sizeInBytes;
    }

    @Override
    public boolean resolve(ICompilationContext context) 
    {
        if ( directive == Directive.EQU ) 
        {
            // child 0 is EquLabelNode
            final Identifier identifier = ((EquLabelNode) child(0)).name;
            final ASTNode child1 = child(1);
            // chidl 1 is expression
            final boolean valueResolved;
            if ( child1 instanceof Resolvable) {
                valueResolved = ((Resolvable) child1).resolve( context );
            } else {
                valueResolved = true;
            }
            if ( valueResolved )
            {
                context.currentSymbolTable().get( identifier ).setValue( ((IValueNode) child(1)).getValue() , Symbol.Type.EQU ); 
            } else {
                context.error("Failed to resolve value",child1);
            }
        }
        return false;
    }    
}