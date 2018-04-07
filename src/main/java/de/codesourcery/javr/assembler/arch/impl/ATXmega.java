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
package de.codesourcery.javr.assembler.arch.impl;

import java.util.List;

import org.apache.commons.lang3.Validate;

import de.codesourcery.javr.assembler.Segment;
import de.codesourcery.javr.assembler.arch.Architecture;
import de.codesourcery.javr.assembler.arch.InstructionEncoder;
import de.codesourcery.javr.assembler.parser.ast.InstructionNode;
import de.codesourcery.javr.assembler.parser.ast.RegisterNode;

public class ATXmega extends ATMega88
{
    @Override
    protected void initInstructions()
    {
        super.initInstructions();
        
        insn("eicall", "1001 0101 0001 1001" );
        insn("eijmp",  "1001 0100 0001 1001" );        
        insn("des",    "1001 0100 KKKK 1011" , ArgumentType.FOUR_BIT_CONSTANT);        
        insn("lac",    "1001 001r rrrr 0110" , ArgumentType.Z_REGISTER , ArgumentType.SINGLE_REGISTER  ).disasmImplicitDestination("Z");
        insn("las",    "1001 001r rrrr 0101" , ArgumentType.Z_REGISTER , ArgumentType.SINGLE_REGISTER  ).disasmImplicitDestination("Z");
        insn("lat",    "1001 001r rrrr 0111" , ArgumentType.Z_REGISTER , ArgumentType.SINGLE_REGISTER  ).disasmImplicitDestination("Z");
        insn("xch",  "1001 001r rrrr 0100" , ArgumentType.Z_REGISTER , ArgumentType.SINGLE_REGISTER ).disasmImplicitDestination("Z");
        
        // ELPM
        final InstructionEncoding elpmNoArgs = new InstructionEncoding( "elpm" , new InstructionEncoder( "1001 0101 1101 1000" ) , ArgumentType.NONE, ArgumentType.NONE);
        final InstructionEncoding elpmOnlyZ = new InstructionEncoding( "elpm" , new InstructionEncoder(  "1001 000d dddd 0110" ) , ArgumentType.SINGLE_REGISTER, ArgumentType.Z_REGISTER).disasmImplicitSource("Z");
        final InstructionEncoding elpmZWithPostIncrement = new InstructionEncoding( "elpm" , new InstructionEncoder( "1001 000d dddd 0111" ) , ArgumentType.SINGLE_REGISTER, ArgumentType.Z_REGISTER).disasmImplicitSource("Z+");
        
        final InstructionSelector elpmSelector = new InstructionSelector() {

            @Override
            public InstructionEncoding pick(InstructionNode node, List<InstructionEncoding> candidates) 
            {
                if ( node.hasNoChildren() ) {
                    return elpmNoArgs;
                }
                final RegisterNode reg = (RegisterNode) node.child(1);
                if ( reg.register.isPreDecrement() ) {
                    throw new RuntimeException("Pre-decrement is not supported by ELPM");
                }
                return reg.register.isPostIncrement() ? elpmZWithPostIncrement : elpmOnlyZ;
            }

            @Override
            public int getMaxInstructionLengthInBytes(InstructionNode node,List<InstructionEncoding> candidates, boolean estimate) 
            {
                return pick( node ,candidates ).getInstructionLengthInBytes();
            }
        };
        add( new EncodingEntry( elpmSelector , elpmNoArgs , elpmZWithPostIncrement , elpmOnlyZ ) );        
    }

    @Override
    public int getSegmentSize(Segment seg) 
    {
        Validate.notNull(seg, "segment must not be NULL");
        switch(seg) {
            case EEPROM: return 1024;
            case FLASH: return 32*1024;
            case SRAM: return 2048;
            default:
                throw new RuntimeException("Unhandled segment type: "+seg);
        }
    }
    
    @Override
    public int getSRAMStartAddress() 
    {
        return 0x100;
    }    
    
    public Architecture getType() {
        return Architecture.ATMEGA328P;
    }
}
