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
package de.codesourcery.javr.assembler.arch.impl;

import java.util.List;

import org.apache.commons.lang3.Validate;

import de.codesourcery.javr.assembler.Address;
import de.codesourcery.javr.assembler.Register;
import de.codesourcery.javr.assembler.Segment;
import de.codesourcery.javr.assembler.arch.AbstractAchitecture;
import de.codesourcery.javr.assembler.arch.Architecture;
import de.codesourcery.javr.assembler.arch.InstructionEncoder;
import de.codesourcery.javr.assembler.parser.ast.ASTNode;
import de.codesourcery.javr.assembler.parser.ast.IValueNode;
import de.codesourcery.javr.assembler.parser.ast.InstructionNode;
import de.codesourcery.javr.assembler.parser.ast.RegisterNode;

public class ATMega88 extends AbstractAchitecture 
{
    public ATMega88() 
    {
        final InstructionEncoding adc = insn("adc",   "0001 11rd dddd rrrr" , ArgumentType.SINGLE_REGISTER , ArgumentType.SINGLE_REGISTER ); 
        final InstructionEncoding add = insn("add",   "0000 11rd dddd rrrr" , ArgumentType.SINGLE_REGISTER , ArgumentType.SINGLE_REGISTER );  
        insn("adiw",  "1001 0110 KKdd KKKK" , ArgumentType.COMPOUND_REGISTERS_R24_TO_R30 , ArgumentType.SIX_BIT_CONSTANT );
        final InstructionEncoding and = insn("and",   "0010 00rd dddd rrrr" , ArgumentType.SINGLE_REGISTER , ArgumentType.SINGLE_REGISTER );
        final InstructionEncoding andi = insn("andi",  "0111 KKKK dddd KKKK" , ArgumentType.R16_TO_R31 , ArgumentType.EIGHT_BIT_CONSTANT );
        insn("asr",   "1001 010d dddd 0101" , ArgumentType.SINGLE_REGISTER );
        insn("bclr",  "1001 0100 1ddd 1000" , ArgumentType.THREE_BIT_CONSTANT );
        insn("bld",   "1111 100d dddd 0sss" , ArgumentType.SINGLE_REGISTER , ArgumentType.THREE_BIT_CONSTANT );
        
        insn("brbs",  "1111 00ss ssss sddd" , ArgumentType.THREE_BIT_CONSTANT , ArgumentType.SEVEN_BIT_SIGNED_JUMP_OFFSET );
        insn("brhs",  "1111 00kk kkkk k101" , ArgumentType.SEVEN_BIT_SIGNED_JUMP_OFFSET );
        
        final InstructionEncoding brcc = insn("brcc",  "1111 01kk kkkk k000" , ArgumentType.SEVEN_BIT_SIGNED_JUMP_OFFSET );
        final InstructionEncoding brcs = insn("brcs",  "1111 00kk kkkk k000" , ArgumentType.SEVEN_BIT_SIGNED_JUMP_OFFSET );
        insn("break", "1001 0101 1001 1000" );
        insn("breq",  "1111 00kk kkkk k001" , ArgumentType.SEVEN_BIT_SIGNED_JUMP_OFFSET );
        insn("brbc",  "1111 01ss ssss sddd" , ArgumentType.THREE_BIT_CONSTANT , ArgumentType.SEVEN_BIT_SIGNED_JUMP_OFFSET );
        insn("brge",  "1111 01kk kkkk k100" , ArgumentType.SEVEN_BIT_SIGNED_JUMP_OFFSET );
        insn("brhc",  "1111 01kk kkkk k101" , ArgumentType.SEVEN_BIT_SIGNED_JUMP_OFFSET );
        insn("brid",  "1111 01kk kkkk k111" , ArgumentType.SEVEN_BIT_SIGNED_JUMP_OFFSET );
        insn("brie",  "1111 00kk kkkk k111" , ArgumentType.SEVEN_BIT_SIGNED_JUMP_OFFSET );
        
        final InstructionEncoding brlo = insn("brlo",  "1111 00kk kkkk k000" , ArgumentType.SEVEN_BIT_SIGNED_JUMP_OFFSET );
        brlo.aliasOf( brcs );
        brcs.aliasOf( brlo );
        
        insn("brlt",  "1111 00kk kkkk k100" , ArgumentType.SEVEN_BIT_SIGNED_JUMP_OFFSET );
        insn("brmi",  "1111 00kk kkkk k010" , ArgumentType.SEVEN_BIT_SIGNED_JUMP_OFFSET );
        insn("brne",  "1111 01kk kkkk k001" , ArgumentType.SEVEN_BIT_SIGNED_JUMP_OFFSET );
        insn("brpl",  "1111 01kk kkkk k010" , ArgumentType.SEVEN_BIT_SIGNED_JUMP_OFFSET );
        final InstructionEncoding brsh = insn("brsh",  "1111 01kk kkkk k000" , ArgumentType.SEVEN_BIT_SIGNED_JUMP_OFFSET );
        brsh.aliasOf( brcc );
        brcc.aliasOf( brsh);
        insn("brtc",  "1111 01kk kkkk k110" , ArgumentType.SEVEN_BIT_SIGNED_JUMP_OFFSET );
        insn("brts",  "1111 00kk kkkk k110" , ArgumentType.SEVEN_BIT_SIGNED_JUMP_OFFSET );
        insn("brvc",  "1111 01kk kkkk k011" , ArgumentType.SEVEN_BIT_SIGNED_JUMP_OFFSET );
        insn("brvs",  "1111 00kk kkkk k011" , ArgumentType.SEVEN_BIT_SIGNED_JUMP_OFFSET );
        insn("bset",  "1001 0100 0sss 1000" , ArgumentType.THREE_BIT_CONSTANT );
        insn("bst",   "1111 101d dddd 0sss" , ArgumentType.SINGLE_REGISTER , ArgumentType.THREE_BIT_CONSTANT );
        
        insn("call",  "1001 010k kkkk 111k kkkk kkkk kkkk kkkk" , ArgumentType.TWENTYTWO_BIT_FLASH_MEM_ADDRESS );
        
        insn("cbi",   "1001 1000 dddd dsss" , ArgumentType.FIVE_BIT_IO_REGISTER_CONSTANT , ArgumentType.THREE_BIT_CONSTANT );
        final InstructionEncoding cbr = insn("cbr",   "0111 KKKK dddd KKKK" , ArgumentType.R16_TO_R31 , ArgumentType.EIGHT_BIT_CONSTANT ).srcTransform( value -> 
        {
          return ~value &0xff; // CBR is implemented as AND with inverted src value     
        });   
        andi.aliasOf( cbr );
        cbr.aliasOf( andi );
        insn("clc",    "1001 0100 1000 1000" );
        insn("clh",    "1001 0100 1101 1000" );
        insn("cli",    "1001 0100 1111 1000" );
        insn("cln",    "1001 0100 1010 1000" );
        final InstructionEncoding clr = insn("clr",    "0010 01dd dddd dddd" , ArgumentType.SINGLE_REGISTER );
        insn("cls",    "1001 0100 1100 1000" );
        insn("clt",    "1001 0100 1110 1000" );
        insn("clv",    "1001 0100 1011 1000" );
        insn("clz",    "1001 0100 1001 1000" );
        insn("com",    "1001 010d dddd 0000" , ArgumentType.SINGLE_REGISTER);
        insn("cp",      "0001 01rd dddd rrrr" , ArgumentType.SINGLE_REGISTER , ArgumentType.SINGLE_REGISTER);
        insn("cpc",    "0000 01rd dddd rrrr" , ArgumentType.SINGLE_REGISTER , ArgumentType.SINGLE_REGISTER);
        insn("cpi",    "0011 KKKK dddd KKKK" , ArgumentType.R16_TO_R31 , ArgumentType.EIGHT_BIT_CONSTANT);
        insn("cpse",   "0001 00rd dddd rrrr" , ArgumentType.SINGLE_REGISTER , ArgumentType.SINGLE_REGISTER);
        insn("dec",    "1001 010d dddd 1010" , ArgumentType.SINGLE_REGISTER);
        insn("des",    "1001 0100 KKKK 1011" , ArgumentType.FOUR_BIT_CONSTANT);
        insn("eicall", "1001 0101 0001 1001" );
        insn("eijmp",  "1001 0100 0001 1001" );
        
        // ELPM
        final InstructionEncoding elpmNoArgs = new InstructionEncoding( "elpm" , new InstructionEncoder( "1001 0101 1101 1000" ) , ArgumentType.NONE, ArgumentType.NONE);
        final InstructionEncoding elpmOnlyZ = new InstructionEncoding( "elpm" , new InstructionEncoder(  "1001 000d dddd 0110" ) , ArgumentType.SINGLE_REGISTER, ArgumentType.Z_REGISTER).disasmImplicitSource("Z");
        final InstructionEncoding elpmZWithPostIncrement = new InstructionEncoding( "elpm" , new InstructionEncoder( "1001 000d dddd 0111" ) , ArgumentType.SINGLE_REGISTER, ArgumentType.Z_REGISTER);
        
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
        
        final InstructionEncoding eor = insn("eor",    "0010 01rd dddd rrrr" , ArgumentType.SINGLE_REGISTER, ArgumentType.SINGLE_REGISTER);
        
        final DisassemblySelector eorOrClr = new SameOperandsDisassemblySelector(clr,eor);
        eor.disassemblySelector( eorOrClr );
        clr.disassemblySelector( eorOrClr );
        
        insn("fmul",   "0000 0011 0ddd 1rrr" , ArgumentType.R16_TO_R23, ArgumentType.R16_TO_R23);
        insn("fmuls",  "0000 0011 1ddd 0rrr" , ArgumentType.R16_TO_R23, ArgumentType.R16_TO_R23);
        insn("fmulsu", "0000 0011 1ddd 1rrr" , ArgumentType.R16_TO_R23, ArgumentType.R16_TO_R23);
        insn("icall",  "1001 0101 0000 1001" );
        insn("ijmp",   "1001 0100 0000 1001" );
        insn("in",     "1011 0ssd dddd ssss" , ArgumentType.SINGLE_REGISTER, ArgumentType.SIX_BIT_IO_REGISTER_CONSTANT );
        insn("inc",    "1001 010d dddd 0011" , ArgumentType.SINGLE_REGISTER );
        insn("jmp",    "1001 010k kkkk 110k kkkk kkkk kkkk kkkk" , ArgumentType.TWENTYTWO_BIT_FLASH_MEM_ADDRESS );
        insn("lac",    "1001 001r rrrr 0110" , ArgumentType.SINGLE_REGISTER ).disasmImplicitDestination("Z");
        insn("las",    "1001 001r rrrr 0101" , ArgumentType.SINGLE_REGISTER ).disasmImplicitDestination("Z");
        insn("lat",    "1001 001r rrrr 0111" , ArgumentType.SINGLE_REGISTER ).disasmImplicitDestination("Z");
        
        // LD Rd,-(X|Y|Z)+
        final InstructionEncoding ldOnlyX = new InstructionEncoding( "ld" , new InstructionEncoder( "1001 000d dddd 1100" ) , ArgumentType.SINGLE_REGISTER, ArgumentType.X_REGISTER ).disasmImplicitSource("X");
        final InstructionEncoding ldOnlyY = new InstructionEncoding( "ld" , new InstructionEncoder( "1000 000d dddd 1000" ) , ArgumentType.SINGLE_REGISTER, ArgumentType.Y_REGISTER ).disasmImplicitSource("Y");
        final InstructionEncoding ldOnlyZ = new InstructionEncoding( "ld" , new InstructionEncoder( "1000 000d dddd 0000" ) , ArgumentType.SINGLE_REGISTER, ArgumentType.Z_REGISTER ).disasmImplicitSource("Z");
        
        final InstructionEncoding ldXWithPostIncrement = new InstructionEncoding( "ld" , new InstructionEncoder(  "1001 000d dddd 1101" ) , ArgumentType.SINGLE_REGISTER, ArgumentType.X_REGISTER).disasmImplicitSource("X+");
        final InstructionEncoding ldYWithPostIncrement = new InstructionEncoding( "ld" , new InstructionEncoder(  "1001 000d dddd 1001" ) , ArgumentType.SINGLE_REGISTER, ArgumentType.Y_REGISTER).disasmImplicitSource("Y+");
        final InstructionEncoding ldZWithPostIncrement = new InstructionEncoding( "ld" , new InstructionEncoder(  "1001 000d dddd 0001" ) , ArgumentType.SINGLE_REGISTER, ArgumentType.Z_REGISTER).disasmImplicitSource("Z+");
        
        final InstructionEncoding ldXWithPreDecrement = new InstructionEncoding( "ld" , new InstructionEncoder( "1001 000d dddd 1110" ) , ArgumentType.SINGLE_REGISTER, ArgumentType.X_REGISTER) .disasmImplicitSource("-X");
        final InstructionEncoding ldYWithPreDecrement = new InstructionEncoding( "ld" , new InstructionEncoder( "1001 000d dddd 1010" ) , ArgumentType.SINGLE_REGISTER, ArgumentType.Y_REGISTER) .disasmImplicitSource("-Y");
        final InstructionEncoding ldZYWithPreDecrement = new InstructionEncoding( "ld" , new InstructionEncoder( "1001 000d dddd 0010" ) , ArgumentType.SINGLE_REGISTER, ArgumentType.Z_REGISTER).disasmImplicitSource("-Z");
        
        final InstructionSelector ldSelector = new InstructionSelector() {

            @Override
            public InstructionEncoding pick(InstructionNode node, List<InstructionEncoding> candidates) 
            {
                final RegisterNode reg = (RegisterNode) node.src();
                switch ( reg.register.getRegisterNumber() ) 
                {
                    case Register.REG_X:
                        if ( reg.register.isPreDecrement() ) {
                            return ldXWithPreDecrement;
                        }
                        return reg.register.isPostIncrement() ? ldXWithPostIncrement : ldOnlyX;                        
                    case Register.REG_Y:
                        if ( reg.register.isPreDecrement() ) {
                            return ldYWithPreDecrement;
                        }
                        return reg.register.isPostIncrement() ? ldYWithPostIncrement : ldOnlyY;    
                    case Register.REG_Z:
                        if ( reg.register.isPreDecrement() ) {
                            return ldZYWithPreDecrement;
                        }
                        return reg.register.isPostIncrement() ? ldZWithPostIncrement : ldOnlyZ;                         
                    default:
                        throw new RuntimeException("Unsupported register: "+reg.register);
                }
            }

            @Override
            public int getMaxInstructionLengthInBytes(InstructionNode node,List<InstructionEncoding> candidates, boolean estimate) 
            {
                return pick( node ,candidates ).getInstructionLengthInBytes();
            }
        };
        add( new EncodingEntry( ldSelector , ldOnlyX , ldXWithPostIncrement , ldXWithPreDecrement ) );
        
        // LDD Y / LDD Z
        final InstructionEncoding lddY = new InstructionEncoding( "ldd" , new InstructionEncoder( "10s0 ss0d dddd 1sss" ) , ArgumentType.SINGLE_REGISTER, ArgumentType.Y_REGISTER_SIX_BIT_DISPLACEMENT);
        final InstructionEncoding lddZ = new InstructionEncoding( "ldd" , new InstructionEncoder( "10s0 ss0d dddd 0sss" ) , ArgumentType.SINGLE_REGISTER, ArgumentType.Z_REGISTER_SIX_BIT_DISPLACEMENT);
        
        final InstructionSelector lddSelector = new InstructionSelector() {

            @Override
            public InstructionEncoding pick(InstructionNode node,List<InstructionEncoding> candidates) 
            {
                final RegisterNode rn =(RegisterNode) node.src();
                switch ( rn.register.getRegisterNumber() ) 
                {
                    case Register.REG_Y: return lddY;
                    case Register.REG_Z: return lddZ;
                    default:
                        throw new RuntimeException("Unsupported register: "+rn.register);
                }
            }

            @Override
            public int getMaxInstructionLengthInBytes(InstructionNode node,List<InstructionEncoding> candidates, boolean estimate) 
            {
                return pick(node,candidates).getInstructionLengthInBytes();
            }
        };
        
        add( new EncodingEntry( lddSelector , lddY , lddZ ) );
        
        insn("ldi",    "1110 ssss dddd ssss" , ArgumentType.R16_TO_R31 , ArgumentType.EIGHT_BIT_CONSTANT );
        
        final InstructionEncoding ldsShort = new InstructionEncoding( "lds" , new InstructionEncoder( "1010 0kkk dddd kkkk" ) , ArgumentType.R16_TO_R31, ArgumentType.SEVEN_BIT_SRAM_MEM_ADDRESS);
        final InstructionEncoding ldsLong  = new InstructionEncoding( "lds" , new InstructionEncoder( "1001 000d dddd 0000 kkkk kkkk kkkk kkkk" ) , ArgumentType.SINGLE_REGISTER, ArgumentType.SIXTEEN_BIT_SRAM_MEM_ADDRESS);
        
        final InstructionSelector ldsSelector = new InstructionSelector() {

            @Override
            public InstructionEncoding pick(InstructionNode node, List<InstructionEncoding> candidates)
            {
                final Object value = ((IValueNode) node.src() ).getValue();
                final int adr;
                if ( value instanceof Address) 
                {
                    adr = ((Address) value).getByteAddress();
                } else if ( value instanceof Number) {
                    adr = ((Number) value).intValue();
                } else {
                    throw new RuntimeException("Internal error, don't know how to turn "+value+" into a number");
                }
                return adr <= 127 ? ldsShort : ldsLong;
            }

            @Override
            public int getMaxInstructionLengthInBytes(InstructionNode node, List<InstructionEncoding> candidates, boolean estimate) 
            {
                final Object value = ((IValueNode) node.src() ).getValue();
                if ( value == null ) {
                    return ldsLong.getInstructionLengthInBytes();
                }
                return pick(node,candidates).getInstructionLengthInBytes();
            }

        };
        add( new EncodingEntry( ldsSelector , ldsShort , ldsLong ) );
        
        // LPM
        final InstructionEncoding lpmNoArgs = new InstructionEncoding( "lpm" , new InstructionEncoder( "1001 0101 1100 1000" ) , ArgumentType.NONE, ArgumentType.NONE);
        final InstructionEncoding lpmOnlyZ = new InstructionEncoding( "lpm" , new InstructionEncoder(  "1001 000d dddd 0100" ) , ArgumentType.SINGLE_REGISTER, ArgumentType.Z_REGISTER);
        final InstructionEncoding lpmZWithPostIncrement = new InstructionEncoding( "lpm" , new InstructionEncoder( "1001 000d dddd 0101" ) , ArgumentType.SINGLE_REGISTER, ArgumentType.Z_REGISTER);
        
        final InstructionSelector lpmSelector = new InstructionSelector() {

            @Override
            public InstructionEncoding pick(InstructionNode node, List<InstructionEncoding> candidates) 
            {
                if ( node.hasNoChildren() ) {
                    return lpmNoArgs;
                }
                final RegisterNode reg = (RegisterNode) node.child(1);
                if ( reg.register.isPreDecrement() ) {
                    throw new RuntimeException("Pre-decrement is not supported by LPM");
                }
                return reg.register.isPostIncrement() ? lpmZWithPostIncrement : lpmOnlyZ;
            }

            @Override
            public int getMaxInstructionLengthInBytes(InstructionNode node,List<InstructionEncoding> candidates, boolean estimate) 
            {
                return pick( node ,candidates ).getInstructionLengthInBytes();
            }
        };
        add( new EncodingEntry( lpmSelector , lpmNoArgs , lpmZWithPostIncrement , lpmOnlyZ ) );        
        
        
        final InstructionEncoding lsl = insn("lsl",   "0000 11dd dddd dddd" , ArgumentType.SINGLE_REGISTER );
        
        final DisassemblySelector addOrLsl = new SameOperandsDisassemblySelector(lsl,add);
        add.disassemblySelector( addOrLsl );
        lsl.disassemblySelector( addOrLsl );
        
        insn("lsr",   "1001 010d dddd 0110" , ArgumentType.SINGLE_REGISTER );
        insn("mov",   "0010 11rd dddd rrrr" , ArgumentType.SINGLE_REGISTER , ArgumentType.SINGLE_REGISTER );
        
        
        // MOVW 28,30  0000 0001 1101 1110
        insn("movw",  "0000 0001 dddd rrrr" , ArgumentType.COMPOUND_REGISTER_FOUR_BITS , ArgumentType.COMPOUND_REGISTER_FOUR_BITS);
        insn("mul",   "1001 11rd dddd rrrr" , ArgumentType.SINGLE_REGISTER , ArgumentType.SINGLE_REGISTER);
        insn("muls",  "0000 0010 dddd rrrr" , ArgumentType.R16_TO_R31, ArgumentType.R16_TO_R31);
        insn("mulsu", "0000 0011 0ddd 0rrr" , ArgumentType.R16_TO_R23, ArgumentType.R16_TO_R23);
        insn("neg",   "1001 010d dddd 0001" , ArgumentType.SINGLE_REGISTER);
        insn("nop",   "0000 0000 0000 0000");
        insn("or",    "0010 10rd dddd rrrr" , ArgumentType.SINGLE_REGISTER , ArgumentType.SINGLE_REGISTER);
        final InstructionEncoding ori = insn("ori",   "0110 ssss dddd ssss" , ArgumentType.R16_TO_R31 , ArgumentType.EIGHT_BIT_CONSTANT);
        
        //             1011 1AAr rrrr AAAA
        insn("out",   "1011 1dds ssss dddd" , ArgumentType.SIX_BIT_IO_REGISTER_CONSTANT, ArgumentType.SINGLE_REGISTER );
        insn("pop",   "1001 000d dddd 1111" , ArgumentType.SINGLE_REGISTER);
        insn("push",  "1001 001d dddd 1111" , ArgumentType.SINGLE_REGISTER );
        insn("rcall", "1101 kkkk kkkk kkkk" , ArgumentType.TWELVE_BIT_SIGNED_JUMP_OFFSET );
        insn("ret",   "1001 0101 0000 1000");
        insn("reti",  "1001 0101 0001 1000");
        insn("rjmp",  "1100 kkkk kkkk kkkk" , ArgumentType.TWELVE_BIT_SIGNED_JUMP_OFFSET );
        final InstructionEncoding rol = insn("rol",   "0001 11dd dddd dddd" , ArgumentType.SINGLE_REGISTER );
        
        final DisassemblySelector adcOrRol = new SameOperandsDisassemblySelector(rol,adc);
        adc.disassemblySelector( adcOrRol );
        rol.disassemblySelector( adcOrRol );
        
        insn("ror",   "1001 010d dddd 0111" , ArgumentType.SINGLE_REGISTER );
        insn("sbc",   "0000 10rd dddd rrrr" , ArgumentType.SINGLE_REGISTER , ArgumentType.SINGLE_REGISTER );
        insn("sbci",  "0100 KKKK dddd KKKK" , ArgumentType.R16_TO_R31 , ArgumentType.EIGHT_BIT_CONSTANT );
        insn("sbi",   "1001 1010 dddd dsss" , ArgumentType.FIVE_BIT_IO_REGISTER_CONSTANT, ArgumentType.THREE_BIT_CONSTANT );
        insn("sbic",  "1001 1001 dddd dsss" , ArgumentType.FIVE_BIT_IO_REGISTER_CONSTANT, ArgumentType.THREE_BIT_CONSTANT );
        insn("sbis",  "1001 1011 dddd dsss" , ArgumentType.FIVE_BIT_IO_REGISTER_CONSTANT, ArgumentType.THREE_BIT_CONSTANT );
        
        insn("sbiw",  "1001 0111 KKdd KKKK" , ArgumentType.COMPOUND_REGISTERS_R24_TO_R30, ArgumentType.SIX_BIT_CONSTANT );
        
        final InstructionEncoding sbr = insn("sbr",   "0110 KKKK dddd KKKK" , ArgumentType.R16_TO_R31 , ArgumentType.EIGHT_BIT_CONSTANT );
        sbr.aliasOf( ori );
        ori.aliasOf( sbr );
        insn("sbrc",  "1111 110d dddd 0sss" , ArgumentType.SINGLE_REGISTER , ArgumentType.THREE_BIT_CONSTANT );
        insn("sbrs",  "1111 111d dddd 0sss" , ArgumentType.SINGLE_REGISTER , ArgumentType.THREE_BIT_CONSTANT );
        insn("sec",   "1001 0100 0000 1000");
        insn("seh",   "1001 0100 0101 1000");
        insn("sei",   "1001 0100 0111 1000");
        insn("sen",   "1001 0100 0010 1000");
        insn("ser",   "1110 1111 dddd 1111" , ArgumentType.R16_TO_R31 );
        insn("ses",   "1001 0100 0100 1000");
        insn("set",   "1001 0100 0110 1000");
        insn("sev",   "1001 0100 0011 1000");
        insn("sez",   "1001 0100 0001 1000");
        insn("sleep", "1001 0101 1000 1000");
        
        // SPM
        final InstructionEncoding spmNoArgs = new InstructionEncoding( "spm" , new InstructionEncoder( "1001 0101 1110 1000" ) , ArgumentType.NONE, ArgumentType.NONE);
        final InstructionEncoding spmZWithPostIncrement = new InstructionEncoding( "spm" , new InstructionEncoder( "1001 0101 1111 1000" ) , ArgumentType.SINGLE_REGISTER, ArgumentType.Z_REGISTER);
        
        final InstructionSelector spmSelector = new InstructionSelector() {

            @Override
            public InstructionEncoding pick(InstructionNode node, List<InstructionEncoding> candidates) 
            {
                if ( node.hasNoChildren() ) {
                    return spmNoArgs;
                }
                final RegisterNode reg = (RegisterNode) node.child(1);
                if ( reg.register.isPreDecrement() ) {
                    throw new RuntimeException("Pre-decrement is not supported by LPM");
                }
                if ( ! reg.register.isPostIncrement() ) {
                    throw new RuntimeException("Only SPM and SPM Z+ are supported");
                }                
                return spmZWithPostIncrement;
            }

            @Override
            public int getMaxInstructionLengthInBytes(InstructionNode node,List<InstructionEncoding> candidates, boolean estimate) 
            {
                return pick( node ,candidates ).getInstructionLengthInBytes();
            }
        };
        add( new EncodingEntry( spmSelector , spmNoArgs , spmZWithPostIncrement) );
        
        // ST
        final InstructionEncoding stOnlyX = new InstructionEncoding( "st" , new InstructionEncoder( "1001 001r rrrr 1100" ) , ArgumentType.X_REGISTER , ArgumentType.SINGLE_REGISTER).disasmImplicitDestination("X");
        final InstructionEncoding stOnlyY = new InstructionEncoding( "st" , new InstructionEncoder( "1000 001r rrrr 1000" ) , ArgumentType.Y_REGISTER , ArgumentType.SINGLE_REGISTER).disasmImplicitDestination("Y");
        final InstructionEncoding stOnlyZ = new InstructionEncoding( "st" , new InstructionEncoder( "1000 001r rrrr 0000" ) , ArgumentType.Z_REGISTER , ArgumentType.SINGLE_REGISTER).disasmImplicitDestination("Z");
                                  
        final InstructionEncoding stXWithPostIncrement = new InstructionEncoding( "st" , new InstructionEncoder( "1001 001r rrrr 1101" ) , ArgumentType.X_REGISTER , ArgumentType.SINGLE_REGISTER).disasmImplicitDestination("X+");
        final InstructionEncoding stXWithPreDecrement = new InstructionEncoding( "st" , new InstructionEncoder(  "1001 001r rrrr 1110" ) , ArgumentType.X_REGISTER , ArgumentType.SINGLE_REGISTER).disasmImplicitDestination("-X");
        
        final InstructionEncoding stYWithPostIncrement = new InstructionEncoding( "st" , new InstructionEncoder( "1001 001r rrrr 1001" ) , ArgumentType.Y_REGISTER , ArgumentType.SINGLE_REGISTER).disasmImplicitDestination("Y+");
        final InstructionEncoding stYWithPreDecrement = new InstructionEncoding( "st" , new InstructionEncoder(  "1001 001r rrrr 1010" ) , ArgumentType.Y_REGISTER , ArgumentType.SINGLE_REGISTER).disasmImplicitDestination("-Y");
        final InstructionEncoding stYWithDisplacement = new InstructionEncoding( "st" , new InstructionEncoder(  "10d0 dd1s ssss 1ddd" ) , ArgumentType.Y_REGISTER_SIX_BIT_DISPLACEMENT , ArgumentType.SINGLE_REGISTER );
        
        final InstructionEncoding stZWithPostIncrement = new InstructionEncoding( "st" , new InstructionEncoder( "1001 001r rrrr 0001" ) , ArgumentType.Z_REGISTER , ArgumentType.SINGLE_REGISTER).disasmImplicitDestination("Z+");
        final InstructionEncoding stZYWithPreDecrement = new InstructionEncoding( "st" , new InstructionEncoder( "1001 001r rrrr 0010" ) , ArgumentType.Z_REGISTER , ArgumentType.SINGLE_REGISTER).disasmImplicitDestination("-Z");
        final InstructionEncoding stZWithDisplacement = new InstructionEncoding( "st" , new InstructionEncoder(  "10d0 dd1s ssss 0ddd" ) , ArgumentType.Z_REGISTER_SIX_BIT_DISPLACEMENT , ArgumentType.SINGLE_REGISTER);
                                 
        final InstructionEncoding[] candidates = new InstructionEncoding[]{ stOnlyX, stOnlyY, 
                stOnlyZ, stXWithPostIncrement, stYWithPostIncrement, stZWithPostIncrement, 
                stYWithDisplacement, stZWithDisplacement, stXWithPreDecrement, stYWithPreDecrement,stZYWithPreDecrement };
        
        final InstructionSelector stSelector = new InstructionSelector() {

            @Override
            public InstructionEncoding pick(InstructionNode node, List<InstructionEncoding> candidates) 
            {
                final RegisterNode reg = (RegisterNode) node.dst();
                switch ( reg.register.getRegisterNumber() ) 
                {
                    case Register.REG_X:
                        if ( reg.hasChildren() ) {
                            throw new RuntimeException("No displacement supported with ST X,Rr");
                        }
                        if ( reg.register.isPreDecrement() ) {
                            return stXWithPreDecrement;
                        }
                        return reg.register.isPostIncrement() ? stXWithPostIncrement : stOnlyX;                        
                    case Register.REG_Y:
                        if ( reg.hasChildren() ) {
                            return stYWithDisplacement;
                        }
                        if ( reg.register.isPreDecrement() ) {
                            return stYWithPreDecrement;
                        }
                        return reg.register.isPostIncrement() ? stYWithPostIncrement : stOnlyY;    
                    case Register.REG_Z:
                        if ( reg.hasChildren() ) {
                            return stZWithDisplacement;
                        }                        
                        if ( reg.register.isPreDecrement() ) {
                            return stZYWithPreDecrement;
                        }
                        return reg.register.isPostIncrement() ? stZWithPostIncrement : stOnlyZ;                         
                    default:
                        throw new RuntimeException("Unsupported destination register: "+reg.register+" for instruction "+node.instruction.getMnemonic().toUpperCase());
                }
            }

            @Override
            public int getMaxInstructionLengthInBytes(InstructionNode node,List<InstructionEncoding> candidates, boolean estimate) 
            {
                return pick( node ,candidates ).getInstructionLengthInBytes();
            }
        };
        add( new EncodingEntry( stSelector , candidates ) );        
        
        // STS
        // short:                                                                                      1010 1kkk dddd kkkk
        final InstructionEncoding stsShort = new InstructionEncoding( "sts" , new InstructionEncoder( "1010 1ddd ssss dddd" ) , ArgumentType.SEVEN_BIT_SRAM_MEM_ADDRESS , ArgumentType.R16_TO_R31);
        // long:                                                                                       1001 001d dddd 0000 kkkk kkkk kkkk kkkk
        final InstructionEncoding stsLong  = new InstructionEncoding( "sts" , new InstructionEncoder( "1001 001s ssss 0000 dddd dddd dddd dddd" ) , ArgumentType.SIXTEEN_BIT_SRAM_MEM_ADDRESS, ArgumentType.SINGLE_REGISTER );
        
        final InstructionSelector stsSelector = new InstructionSelector() {

            @Override
            public InstructionEncoding pick(InstructionNode node, List<InstructionEncoding> candidates)
            {
                final Object value = ((IValueNode) node.dst() ).getValue();
                final int adr;
                if ( value instanceof Address) 
                {
                    adr = ((Address) value).getByteAddress();
                } else if ( value instanceof Number) {
                    adr = ((Number) value).intValue();
                } else {
                    throw new RuntimeException("Internal error, don't know how to turn "+value+" into a number");
                }
                // STS short opcode requires operand <= 127 and only supports R16...R31
                if ( adr <= 127 ) 
                {
                    final ASTNode srcNode = node.src();
                    final Register src = ((RegisterNode) srcNode ).register;
                    if ( src.isCompoundRegister() ) {
                        throw new RuntimeException("STS src register must be a single register");
                    }
                    if ( src.getRegisterNumber() >= 16 ) { // short opcode only supports R16..R31
                        return stsShort;
                    }
                }
                return stsLong;
            }

            @Override
            public int getMaxInstructionLengthInBytes(InstructionNode node, List<InstructionEncoding> candidates, boolean estimate) 
            {
                final Object value = ((IValueNode) node.dst() ).getValue();
                if ( value == null ) {
                    return ldsLong.getInstructionLengthInBytes();
                }
                return pick(node,candidates).getInstructionLengthInBytes();
            }

        };
        add( new EncodingEntry( stsSelector , stsShort , stsLong ) );        
        
        // end 
        insn("sub",  "0001 10rd dddd rrrr" , ArgumentType.SINGLE_REGISTER , ArgumentType.SINGLE_REGISTER );
        insn("subi", "0101 KKKK dddd KKKK" , ArgumentType.R16_TO_R31 , ArgumentType.EIGHT_BIT_CONSTANT );
        insn("swap", "1001 010d dddd 0010" , ArgumentType.SINGLE_REGISTER );
        InstructionEncoding tst = insn("tst",  "0010 00dd dddd dddd" , ArgumentType.SINGLE_REGISTER );
        
        final SameOperandsDisassemblySelector tstOrAnd = new SameOperandsDisassemblySelector(tst,and);
        tst.disassemblySelector( tstOrAnd );
        and.disassemblySelector( tstOrAnd );
        
        insn("wdr",  "1001 0101 1010 1000");
        insn("xch",  "1001 001r rrrr 0100" , ArgumentType.SINGLE_REGISTER ).disasmImplicitDestination("Z");
    }
 
    public Architecture getType() {
        return Architecture.ATMEGA88;
    }
    
    @Override
    public int getSegmentSize(Segment seg) 
    {
        Validate.notNull(seg, "segment must not be NULL");
        switch(seg) {
            case EEPROM: return 512;
            case FLASH: return 8192;
            case SRAM: return 1024;
            default:
                throw new RuntimeException("Unhandled segment type: "+seg);
        }
    }
    
    @Override
    protected boolean isValidFlashAdress(int address) 
    {
        return address >= 0 && address < getSegmentSize(Segment.FLASH);
    }

    @Override
    protected boolean isValidSRAMAdress(int address) 
    {
        return address >= 0 && address < getSegmentSize(Segment.SRAM);
    }

    @Override
    protected boolean isValidRegisterNumber(int number) {
        return number >= 0 && number <= 31;
    }

    @Override
    protected boolean isValidIOSpaceAdress(int address) 
    {
        /*
         * Address space layout:
         * 
         * 0...1f   register file
         * 20...5f  i/o space
         * 60..ff   extended IO space
         */        
        return address >= 0x20 && address <= 0xff;
    }

    @Override
    protected boolean isValidEEPROMAdress(int address) {
        return address >= 0 && address < getSegmentSize(Segment.EEPROM);
    }
    
    protected int getGeneralPurposeRegisterCount() {
        return 32;
    }
}