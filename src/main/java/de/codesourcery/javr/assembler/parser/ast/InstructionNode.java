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
package de.codesourcery.javr.assembler.parser.ast;

import java.util.Objects;

import org.apache.commons.lang3.Validate;

import de.codesourcery.javr.assembler.ICompilationContext;
import de.codesourcery.javr.assembler.Instruction;
import de.codesourcery.javr.assembler.parser.TextRegion;
import de.codesourcery.javr.assembler.symbols.Symbol;

public class InstructionNode extends NodeWithMemoryLocation implements Resolvable
{
    public Instruction instruction;
    private int sizeInBytes;

    public InstructionNode(Instruction insn,TextRegion region) 
    {
        super(region);
        Validate.notNull(insn, "insn must not be NULL");
        this.instruction = insn;
    }

    @Override
    protected InstructionNode createCopy() {
        return new InstructionNode( this.instruction.createCopy() , getTextRegion().createCopy() );
    }

    public static boolean isSameSymbol(Symbol a,Symbol b) 
    {
        return a.name().equals( b.name() ) &&
                a.hasType( b.getType() ) &&
                Objects.equals( a.getSegment() , b.getSegment() ) &&
                a.getCompilationUnit().hasSameResourceAs( b.getCompilationUnit() );             
    }    

    public ASTNode src() {
        return child(1);
    }

    public ASTNode dst() {
        return child(0);
    }    

    @Override
    public String getAsString() {
        return instruction.getMnemonic().toUpperCase();
    }

    @Override
    public boolean hasMemoryLocation() {
        return true;
    }

    @Override
    public int getSizeInBytes() throws IllegalStateException 
    {
        if ( this.sizeInBytes <= 0 ) {
            throw new IllegalStateException("Size not resolved yet");
        }
        return sizeInBytes;
    }    

    @Override
    public boolean resolve(ICompilationContext context) 
    {
        assignMemoryLocation( context.currentAddress() );
        for ( ASTNode child : children() ) 
        {
            child.visitDepthFirst( (node,ctx) -> 
            {
                if ( node instanceof Resolvable) 
                {
                    ((Resolvable) node).resolve( context );
                }
            });
        }
        try {
            this.sizeInBytes = context.getArchitecture().getInstructionLengthInBytes( this, context , true );
        } catch(Exception e) {
            context.error( e.getMessage() , this );
        }
        return true;
    }
}