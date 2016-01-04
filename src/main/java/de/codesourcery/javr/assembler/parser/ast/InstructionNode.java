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
import de.codesourcery.javr.assembler.ICompilationContext.Phase;
import de.codesourcery.javr.assembler.parser.TextRegion;
import de.codesourcery.javr.assembler.parser.Parser.CompilationMessage;
import de.codesourcery.javr.assembler.Instruction;
import de.codesourcery.javr.assembler.Segment;

public class InstructionNode extends ASTNode 
{
    public final Instruction instruction;

    public InstructionNode(Instruction insn,TextRegion region) 
    {
        super(region);
        Validate.notNull(insn, "insn must not be NULL");
        this.instruction = insn;
    }
    
    @Override
    public void compile(ICompilationContext ctx) 
    {
        if ( ctx.isInPhase( Phase.VALIDATE1 ) ) 
        {
            if ( ctx.currentSegment() != Segment.FLASH ) {
                ctx.message( CompilationMessage.error("Instructions need to be placed in CODE segment",this) );
            }
        } 
        else if ( ctx.isInPhase( Phase.VALIDATE2 ) ) 
        {
            ctx.getArchitecture().validate( this , ctx );
        } 
        else if ( ctx.isInPhase( Phase.GATHER_SYMBOLS ) ) 
        {
            ctx.allocateBytes( ctx.getArchitecture().getInstructionLengthInBytes( this , ctx , true ) );
        } 
        else if ( ctx.isInPhase( Phase.GENERATE_CODE) ) 
        {
            ctx.getArchitecture().compile(this, ctx );
        }        
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
}
