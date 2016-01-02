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
package de.codesourcery.javr.assembler.arch;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;

import de.codesourcery.javr.assembler.Architecture;
import de.codesourcery.javr.assembler.IArchitecture;
import de.codesourcery.javr.assembler.ICompilationContext;
import de.codesourcery.javr.assembler.Instruction;
import de.codesourcery.javr.assembler.Parser.CompilationMessage;
import de.codesourcery.javr.assembler.Parser.Severity;
import de.codesourcery.javr.assembler.Register;
import de.codesourcery.javr.assembler.arch.InstructionEncoder.Transform;
import de.codesourcery.javr.assembler.ast.ASTNode;
import de.codesourcery.javr.assembler.ast.InstructionNode;
import de.codesourcery.javr.assembler.ast.NumberLiteralNode;
import de.codesourcery.javr.assembler.ast.RegisterNode;

public class ATMega88 implements IArchitecture 
{
    private static final InstructionSelector DEFAULT_INSN_SELECTOR = new InstructionSelector() {

        @Override
        public InstructionEncoding pick(InstructionNode node,List<InstructionEncoding> candidates) 
        {
            if ( candidates.size() != 1 ) {
                throw new RuntimeException("This instruction selector only supports exactly 1 candidate encoding (got: "+candidates+")");
            }
            return candidates.get(0);
        }
    };
    
    private static final Transform TRANSFORM_R16_TO_R31 =  x -> 
    {
        if ( x < 16 || x > 31 ) {
            throw new RuntimeException("Illegal register r"+x+", expected r16...r31");
        }
        return x-16;
    };
    
    private static final Transform TRANSFORM_R16_TO_R23 =  x -> 
    {
        if ( x < 16 || x > 23 ) {
            throw new RuntimeException("Illegal register r"+x+", expected r16...r23");
        }
        return x-16;
    };    
    
    private static final Transform TRANSFORM_COMPOUND_REGISTERS_R24_TO_R30 =  x -> 
    {
        int value;
        switch( x ) 
        {
            case 24: value = 0; break;
            case 26: value = 1; break;
            case 28: value = 2; break;
            case 30: value = 3; break;
            default: throw new RuntimeException("Illegal register r"+x+", expected r24/r26/r28/r30");
        }
        return value;
    };    
    
    protected interface InstructionSelector 
    {
        public InstructionEncoding pick(InstructionNode node,List<InstructionEncoding> candidates);
    }
    
    protected static final class EncodingEntry 
    {
        public final InstructionSelector selector;
        public final List<InstructionEncoding> encodings = new ArrayList<>();
        
        public EncodingEntry(InstructionSelector chooser,InstructionEncoding encoding) 
        {
            Validate.notNull(chooser, "chooser must not be NULL");
            Validate.notNull(encoding, "encoding must not be NULL");
            this.selector = chooser;
            this.encodings.add( encoding );
        }
        
        public void add( InstructionEncoding enc) {
            Validate.notNull(enc, "enc must not be NULL");
            this.encodings.add(enc);
        }
        
        public InstructionEncoding getEncoding(InstructionNode insn) {
            return selector.pick( insn , encodings );
        }
    }
    
    
    private final Map<String,EncodingEntry> instructions = new HashMap<>();
    
    public ATMega88() 
    {
        insn("adc",   "0001 11rd dddd rrrr"  , ArgumentType.SINGLE_REGISTER , ArgumentType.SINGLE_REGISTER ); 
        insn("add",   "0000 11rd dddd rrrr"  , ArgumentType.SINGLE_REGISTER , ArgumentType.SINGLE_REGISTER );  
        insn("adiw",  "1001 0110 KKdd KKKK" , ArgumentType.COMPOUND_REGISTERS_R24_TO_R30 , ArgumentType.SIX_BIT_CONSTANT );
        insn("and",   "0010 00rd dddd rrrr" , ArgumentType.SINGLE_REGISTER , ArgumentType.SINGLE_REGISTER );
        insn("andi",  "0111 KKKK dddd KKKK" , ArgumentType.R16_TO_R31 , ArgumentType.EIGHT_BIT_CONSTANT );
        insn("asr",   "1001 010d dddd 0101" , ArgumentType.SINGLE_REGISTER );
        insn("bclr",  "1001 0100 1ddd 1000" , ArgumentType.THREE_BIT_CONSTANT );
        insn("bld",   "1111 100d dddd 0sss" , ArgumentType.SINGLE_REGISTER , ArgumentType.THREE_BIT_CONSTANT );
        insn("brbc",  "1111 01ss ssss sddd" , ArgumentType.THREE_BIT_CONSTANT , ArgumentType.SEVEN_BIT_SIGNED_CONSTANT );
        insn("brbs",  "1111 00ss ssss sddd" , ArgumentType.THREE_BIT_CONSTANT , ArgumentType.SEVEN_BIT_SIGNED_CONSTANT );
        insn("brcc",  "1111 01kk kkkk k000" , ArgumentType.SEVEN_BIT_SIGNED_CONSTANT );
        insn("brcs",  "1111 00kk kkkk k000" , ArgumentType.SEVEN_BIT_SIGNED_CONSTANT );
        insn("break", "1001 0101 1001 1000" );
        insn("breq",  "1111 00kk kkkk k001" , ArgumentType.SEVEN_BIT_SIGNED_CONSTANT );
        insn("brge",  "1111 00kk kkkk k001" , ArgumentType.SEVEN_BIT_SIGNED_CONSTANT );
        insn("brhc",  "1111 01kk kkkk k100" , ArgumentType.SEVEN_BIT_SIGNED_CONSTANT );
        insn("brhs",  "1111 00kk kkkk k101" , ArgumentType.SEVEN_BIT_SIGNED_CONSTANT );
        insn("brid",  "1111 01kk kkkk k111" , ArgumentType.SEVEN_BIT_SIGNED_CONSTANT );
        insn("brie",  "1111 00kk kkkk k111" , ArgumentType.SEVEN_BIT_SIGNED_CONSTANT );
        insn("brlo",  "1111 00kk kkkk k000" , ArgumentType.SEVEN_BIT_SIGNED_CONSTANT );
        insn("brlt",  "1111 00kk kkkk k100" , ArgumentType.SEVEN_BIT_SIGNED_CONSTANT );
        insn("brmi",  "1111 00kk kkkk k010" , ArgumentType.SEVEN_BIT_SIGNED_CONSTANT );
        insn("brne",  "1111 01kk kkkk k001" , ArgumentType.SEVEN_BIT_SIGNED_CONSTANT );
        insn("brpl",  "1111 01kk kkkk k010" , ArgumentType.SEVEN_BIT_SIGNED_CONSTANT );
        insn("brsh",  "1111 01kk kkkk k000" , ArgumentType.SEVEN_BIT_SIGNED_CONSTANT );
        insn("brtc",  "1111 01kk kkkk k110" , ArgumentType.SEVEN_BIT_SIGNED_CONSTANT );
        insn("brts",  "1111 00kk kkkk k110" , ArgumentType.SEVEN_BIT_SIGNED_CONSTANT );
        insn("brvc",  "1111 01kk kkkk k011" , ArgumentType.SEVEN_BIT_SIGNED_CONSTANT );
        insn("brvs",  "1111 00kk kkkk k011" , ArgumentType.SEVEN_BIT_SIGNED_CONSTANT );
        insn("bset",  "1001 0100 0sss 1000" , ArgumentType.THREE_BIT_CONSTANT );
        insn("bst",   "1111 101d dddd 0sss" , ArgumentType.SINGLE_REGISTER , ArgumentType.THREE_BIT_CONSTANT );
        
        insn("call",  "1001 010k kkkk 111k kkkk kkkk kkkk kkkk" , ArgumentType.FLASH_MEM_ADDRESS );
        
        insn("cbi",   "1001 1000 dddd dsss" , ArgumentType.FIVE_BIT_IO_REGISTER_CONSTANT , ArgumentType.THREE_BIT_CONSTANT );
        insn("cbr",   "0111 KKKK dddd KKKK" , ArgumentType.R16_TO_R31 , ArgumentType.EIGHT_BIT_CONSTANT ).srcTransform( value -> 
        {
          return ~value &0xff; // CBR is implemented as AND with inverted src value     
        });      
        insn("clc",    "1001 0100 1000 1000" );
        insn("clh",    "1001 0100 1101 1000" );
        insn("cli",    "1001 0100 1111 1000" );
        insn("cln",    "1001 0100 1010 1000" );
        insn("clr",    "0010 01dd dddd dddd" , ArgumentType.SINGLE_REGISTER );
        insn("cls",    "1001 0100 1100 1000" );
        insn("clt",    "1001 0100 1110 1000" );
        insn("clv",    "1001 0100 1011 1000" );
        insn("clz",    "1001 0100 1001 1000" );
        insn("com",    "1001 010d dddd 0000" , ArgumentType.SINGLE_REGISTER);
        insn("c",      "0001 01rd dddd rrrr" , ArgumentType.SINGLE_REGISTER , ArgumentType.SINGLE_REGISTER);
        insn("cpc",    "0000 01rd dddd rrrr" , ArgumentType.SINGLE_REGISTER , ArgumentType.SINGLE_REGISTER);
        insn("cpi",    "0011 KKKK dddd KKKK" , ArgumentType.R16_TO_R31 , ArgumentType.EIGHT_BIT_CONSTANT);
        insn("cpse",   "0001 00rd dddd rrrr" , ArgumentType.SINGLE_REGISTER , ArgumentType.SINGLE_REGISTER);
        insn("dec",    "1001 010d dddd 1010" , ArgumentType.SINGLE_REGISTER);
        insn("des",    "1001 0100 KKKK 1011" , ArgumentType.FOUR_BIT_CONSTANT);
        insn("eicall", "1001 0101 0001 1001" );
        insn("eijmp",  "1001 0100 0001 1001" );
        
        // TODO: Add support for ELPM instructions ( encodings differ based on addressing mode )
        // insn("elpm",  "1001 0101 1101 1000" , InstructionSelector);
        
        insn("eor",    "0010 01rd dddd rrrr" , ArgumentType.SINGLE_REGISTER, ArgumentType.SINGLE_REGISTER);
        insn("fmul",   "0000 0011 0ddd 1rrr" , ArgumentType.R16_TO_R23, ArgumentType.R16_TO_R23);
        insn("fmuls",  "0000 0011 1ddd 0rrr" , ArgumentType.R16_TO_R23, ArgumentType.R16_TO_R23);
        insn("fmulsu", "0000 0011 1ddd 1rrr" , ArgumentType.R16_TO_R23, ArgumentType.R16_TO_R23);
        insn("icall",  "1001 0101 0000 1001" );
        insn("ijmp",   "1001 0100 0000 1001" );
        insn("in",     "1011 0ssd dddd ssss" , ArgumentType.SINGLE_REGISTER, ArgumentType.SIX_BIT_IO_REGISTER_CONSTANT );
        insn("inc",    "1001 010d dddd 0011" , ArgumentType.SINGLE_REGISTER );
        insn("jmp",    "1001 010k kkkk 110k kkkk kkkk kkkk kkkk" , ArgumentType.FLASH_MEM_ADDRESS );
        insn("lac",    "1001 001r rrrr 0110" , ArgumentType.SINGLE_REGISTER );
        insn("las",    "1001 001r rrrr 0101" , ArgumentType.SINGLE_REGISTER );
        insn("lat",    "1001 001r rrrr 0111" , ArgumentType.SINGLE_REGISTER );
        
        // TODO: Add support for LD instructions ( encodings differ based on addressing mode )
        // insn("ld",  "1001 0101 1101 1000" , InstructionSelector );
        
        // TODO: Add support for LDD instructions ( encodings differ based on addressing mode )
        // insn("ldd",  "1001 0101 1101 1000" , InstructionSelector);
        
        insn("ldi",    "1110 ssss dddd ssss" , ArgumentType.R16_TO_R31 , ArgumentType.EIGHT_BIT_CONSTANT );
        
        // TODO: Add support for LDS instruction ( encodings differ based on size of constant )
        // insn("lds",    "1001 000d dddd 0000 ssss ssss ssss ssss" , InstructionSelector );
        
        // TODO: Add support for LPM instructions ( encodings differ based on addressing mode )
        // insn("lpm",    "1001 000d dddd 0000 ssss ssss ssss ssss" , InstructionSelector );
        insn("lsl",   "0000 11dd dddd dddd" , ArgumentType.SINGLE_REGISTER );
        insn("lsr",   "1001 010d dddd 0110" , ArgumentType.SINGLE_REGISTER );
        insn("mov",   "0010 11rd dddd rrrr" , ArgumentType.SINGLE_REGISTER , ArgumentType.SINGLE_REGISTER );
        insn("movw",  "0000 0001 dddd rrrr" , ArgumentType.COMPOUND_REGISTER , ArgumentType.COMPOUND_REGISTER);
        insn("mul",   "1001 11rd dddd rrrr" , ArgumentType.SINGLE_REGISTER , ArgumentType.SINGLE_REGISTER);
        insn("muls",  "0000 0010 dddd rrrr" , ArgumentType.R16_TO_R31, ArgumentType.R16_TO_R31);
        insn("mulsu", "0000 0011 0ddd 0rrr" , ArgumentType.R16_TO_R23, ArgumentType.R16_TO_R23);
        insn("neg",   "1001 010d dddd 0001" , ArgumentType.SINGLE_REGISTER);
        insn("nop",   "0000 0000 0000 0000");
        insn("or",    "0010 10rd dddd rrrr" , ArgumentType.SINGLE_REGISTER , ArgumentType.SINGLE_REGISTER);
        insn("ori",   "0110 ssss dddd ssss" , ArgumentType.SINGLE_REGISTER , ArgumentType.EIGHT_BIT_CONSTANT);
        insn("out",   "1011 1ssd dddd ssss" , ArgumentType.SINGLE_REGISTER , ArgumentType.SIX_BIT_IO_REGISTER_CONSTANT);
        insn("pop",   "1001 000d dddd 1111" , ArgumentType.SINGLE_REGISTER);
        insn("push",  "1001 001d dddd 1111" , ArgumentType.SINGLE_REGISTER );
        insn("rcall", "1101 kkkk kkkk kkkk" , ArgumentType.TWELVE_BIT_SIGNED_CONSTANT );
        insn("ret",   "1001 0101 0000 1000");
        insn("reti",  "1001 0101 0001 1000");
        insn("rjmp",  "1100 kkkk kkkk kkkk" , ArgumentType.TWELVE_BIT_SIGNED_CONSTANT );
        insn("rol",   "0001 11dd dddd dddd" , ArgumentType.SINGLE_REGISTER );
        insn("ror",   "1001 010d dddd 0111" , ArgumentType.SINGLE_REGISTER );
        insn("sbc",   "0000 10rd dddd rrrr" , ArgumentType.SINGLE_REGISTER , ArgumentType.SINGLE_REGISTER );
        insn("sbci",  "0100 KKKK dddd KKKK" , ArgumentType.SINGLE_REGISTER , ArgumentType.EIGHT_BIT_CONSTANT );
        insn("sbi",   "1001 1010 dddd dsss" , ArgumentType.FIVE_BIT_IO_REGISTER_CONSTANT, ArgumentType.THREE_BIT_CONSTANT );
        insn("sbic",  "1001 1001 dddd dsss" , ArgumentType.FIVE_BIT_IO_REGISTER_CONSTANT, ArgumentType.THREE_BIT_CONSTANT );
        insn("sbis",  "1001 1011 dddd dsss" , ArgumentType.FIVE_BIT_IO_REGISTER_CONSTANT, ArgumentType.THREE_BIT_CONSTANT );
        insn("sbiw",  "1001 0111 KKdd KKKK" , ArgumentType.COMPOUND_REGISTERS_R24_TO_R30, ArgumentType.SIX_BIT_CONSTANT );
        insn("sbr",   "0110 KKKK dddd KKKK" , ArgumentType.R16_TO_R31 , ArgumentType.EIGHT_BIT_CONSTANT );
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
        
        // TODO: Add support for SPM instructions ( encodings differ based on addressing mode )        
        // insn("spm",   "1001 0101 1110 1000");
        
        // TODO: Add support for ST instructions ( encodings differ based on addressing mode )        
        // insn("spm",   "1001 0101 1110 1000");     
        
        // TODO: Also add support for 16-bit STS instructions ( encodings differ based on size of constant )          
        insn("sts",  "1001 001d dddd 0000 kkkk kkkk kkkk kkkk" , ArgumentType.SINGLE_REGISTER, ArgumentType.DATASPACE_16_BIT_ADDESS );
        insn("sub",  "0001 10rd dddd rrrr" , ArgumentType.SINGLE_REGISTER , ArgumentType.SINGLE_REGISTER );
        insn("subi", "0101 KKKK dddd KKKK" , ArgumentType.SINGLE_REGISTER , ArgumentType.EIGHT_BIT_CONSTANT );
        insn("swap", "1001 010d dddd 0010" , ArgumentType.SINGLE_REGISTER );
        insn("tst",  "0010 00dd dddd dddd" , ArgumentType.SINGLE_REGISTER );
        insn("wdr",  "1001 0101 1010 1000");
        insn("xch",  "1001 001r rrrr 0100" , ArgumentType.SINGLE_REGISTER );
    }
    
    private InstructionEncoding insn(String mnemonic,String pattern) 
    {
        return insn( mnemonic , pattern , ArgumentType.NONE , ArgumentType.NONE , false , DEFAULT_INSN_SELECTOR);
    }
    
    private InstructionEncoding insn(String mnemonic,String pattern,ArgumentType dstType) 
    {
        return insn( mnemonic , pattern , dstType , ArgumentType.NONE , false , DEFAULT_INSN_SELECTOR );
    }  
    
    private InstructionEncoding insn(String mnemonic,String pattern,ArgumentType dstType,ArgumentType srcType) 
    {
        return insn( mnemonic , pattern , dstType , srcType , false , DEFAULT_INSN_SELECTOR );
    }
    
    private InstructionEncoding insn(String mnemonic,String pattern,ArgumentType dstType,ArgumentType srcType,InstructionSelector chooser) 
    {
        if ( chooser == DEFAULT_INSN_SELECTOR ) {
            throw new IllegalArgumentException("Internal error, default selector does not support multiple encodings");
        }    
        return insn(mnemonic, pattern, dstType, srcType, true , chooser);
    }
    
    private InstructionEncoding insn(String mnemonic,String pattern,ArgumentType dstType,ArgumentType srcType,boolean multipleEncodings,InstructionSelector chooser) 
    {
        final InstructionEncoder enc = new InstructionEncoder( pattern );
        final int expArgCount = ( (dstType==ArgumentType.NONE) ? 0 : 1 ) + ( (srcType==ArgumentType.NONE) ? 0 : 1 );
        if ( enc.getArgumentCount() != expArgCount ) {
            throw new RuntimeException("Internal error, number of arguments in pattern ("+enc.getArgumentCount()+") does not match expectations ("+expArgCount+")");
        }
        
        final InstructionEncoding ins = new InstructionEncoding( mnemonic , enc , dstType , srcType );
        
        final EncodingEntry existing = instructions.get( mnemonic );
        if ( existing != null ) 
        {
            if ( ! multipleEncodings ) {
                throw new RuntimeException("Internal error, duplicate instruction '"+mnemonic+"'");
            }
            existing.add( ins );
        } 
        else 
        {
            instructions.put( mnemonic , new EncodingEntry( chooser , ins ) );
        }
        
        if ( dstType == ArgumentType.R16_TO_R23 ) 
        {
            ins.encoder.dstTransform(TRANSFORM_R16_TO_R23);
        } 
        else if ( dstType == ArgumentType.R16_TO_R31 ) 
        {
            ins.encoder.dstTransform(TRANSFORM_R16_TO_R31);
        } else if ( dstType == ArgumentType.COMPOUND_REGISTERS_R24_TO_R30 ) {
            ins.encoder.dstTransform(TRANSFORM_COMPOUND_REGISTERS_R24_TO_R30);
        }
        
        if ( srcType == ArgumentType.R16_TO_R23 ) 
        {
            ins.encoder.srcTransform(TRANSFORM_R16_TO_R23);
        } 
        else if ( srcType == ArgumentType.R16_TO_R31 ) 
        {
            ins.encoder.srcTransform(TRANSFORM_R16_TO_R31);
        }  else if ( srcType == ArgumentType.COMPOUND_REGISTERS_R24_TO_R30 ) {
            ins.encoder.srcTransform(TRANSFORM_COMPOUND_REGISTERS_R24_TO_R30);
        }       
        return ins;
    }     
    
    protected static enum ArgumentType 
    {
        SINGLE_REGISTER,
        COMPOUND_REGISTERS_R24_TO_R30,
        COMPOUND_REGISTER,
        R0_TO_R15,
        R16_TO_R31,
        R16_TO_R23,
        FLASH_MEM_ADDRESS, // device-dependent, covers whole address range
        SRAM_MEM_ADDRESS, // device-dependent, covers whole address range
        THREE_BIT_CONSTANT,
        FOUR_BIT_CONSTANT,
        FIVE_BIT_IO_REGISTER_CONSTANT,
        SIX_BIT_CONSTANT,
        SIX_BIT_IO_REGISTER_CONSTANT,
        SEVEN_BIT_SIGNED_CONSTANT,
        EIGHT_BIT_CONSTANT,
        TWELVE_BIT_SIGNED_CONSTANT,
        DATASPACE_16_BIT_ADDESS,
        NONE
    }
    
    protected static final class InstructionEncoding 
    {
        public final String mnemonic;
        private final InstructionEncoder encoder;
        public final ArgumentType srcType;
        public final ArgumentType dstType;
        
        public InstructionEncoding(String mnemonic,InstructionEncoder enc,ArgumentType dstType,ArgumentType srcType) 
        {
            Validate.notBlank(mnemonic, "mnemonic must not be NULL or blank");
            Validate.notNull(enc, "enc must not be NULL");
            Validate.notNull(dstType, "dstType must not be NULL");
            Validate.notNull(srcType, "dstType must not be NULL");
            this.mnemonic = mnemonic;
            this.encoder = enc;
            this.srcType = srcType;
            this.dstType = dstType;
        }     
        
        @Override
        public String toString() {
            return mnemonic.toUpperCase()+"( "+dstType+" , "+srcType+" ) = "+encoder;
        }
        
        public int getInstructionLengthInBytes() {
            return encoder.getInstructionLengthInBytes();
        }
        
        public int getArgumentCount() {
            return encoder.getArgumentCount();
        }
        
        public InstructionEncoding dstTransform(Transform t) {
            encoder.dstTransform( t );
            return this;
        }
        
        public InstructionEncoding srcTransform(Transform t) {
            encoder.srcTransform( t );
            return this;
        }        
        
        public int encode(int dstValue,int srcValue) 
        {
            return encoder.encode( dstValue , srcValue );
        }
    }
    
    @Override
    public Architecture getType() {
        return Architecture.ATMEGA88;
    }

    @Override
    public boolean hasType(Architecture t) {
        return t.equals( getType() );
    }

    private static final String SINGLE_REG = "[rR]([0-9]|([1-3][0-9]))";
    private static final String COMPOUND_REG1 = "("+SINGLE_REG+":"+SINGLE_REG+")";
    private static final String COMPOUND_REG2 = "[xXyYzZ][\\+]{0,1}";
    
    private static final Pattern REGISTER_PATTERN = Pattern.compile("("+SINGLE_REG+")|("+COMPOUND_REG1+")|("+COMPOUND_REG2+")");
    
    public static void main(String[] args) {
    
        ATMega88 arch = new ATMega88();
        
        System.out.println("valid: "+arch.isValidRegister("j+"));
    }
    
    @Override
    public boolean isValidRegister(String s) 
    {
        if ( StringUtils.isNotBlank(s) ) 
        {
            return REGISTER_PATTERN.matcher( s ).matches();
        }
        return false;
    }
    
    @Override
    public Register parseRegister(String s) 
    {
        if ( ! isValidRegister( s ) ) {
            throw new IllegalArgumentException("Not a valid register spec: '"+s+"'");
        }
        return new Register( s.toLowerCase() );
    }

    @Override
    public boolean isValidInstruction(String s) 
    {
        if ( StringUtils.isNotBlank( s ) ) 
        {
            return instructions.containsKey( s.toLowerCase() );
        }
        return false;
    }

    @Override
    public Instruction parseInstruction(String s) {
        if ( ! isValidInstruction( s ) ) {
            throw new IllegalArgumentException("Not a valid instruction: '"+s+"'");
        }
        return new Instruction( s.toLowerCase() );
    }

    @Override
    public void compile(InstructionNode node, ICompilationContext context) 
    {
        ASTNode child1 = null;
        ASTNode child2 = null;
        switch( node.childCount() ) 
        {
            case 0: 
                break;
            case 2: child2 = node.child(1);
            //      $$FALL-THROUGH$$
            case 1: child1 = node.child(0); 
                break;
            default:
        }
        final int argCount = ( child1 != null ? 1 : 0 ) + (child2 != null ? 1 : 0 );
        
        final EncodingEntry variants = instructions.get( node.instruction.getMnemonic().toLowerCase() );
        if ( variants == null ) {
            throw new RuntimeException("Unknown instruction: "+node.instruction.getMnemonic()); 
        }        
        final InstructionEncoding encoding = variants.getEncoding( node );
        if ( argCount != encoding.getArgumentCount() ) 
        {
            throw new RuntimeException( encoding.mnemonic+" expects "+encoding.getArgumentCount()+" arguments but got "+argCount);
        }
        
        final int dstValue = getDstValue( child1 , encoding.dstType , context );
        final int srcValue = getSrcValue( child2 , encoding.srcType , context );

        final int instruction = encoding.encode( dstValue , srcValue );
        
        debugAssembly(node, encoding, dstValue, srcValue, instruction); // TODO: Remove debug code
        
        context.writeAsBytes( instruction , encoding.getInstructionLengthInBytes() );
    }

    private void debugAssembly(InstructionNode node,
            final InstructionEncoding encoding, final int dstValue,
            final int srcValue, final int instruction) 
    {
        final String hex;
        final String bin;
        switch( encoding.getInstructionLengthInBytes() ) 
        {
            case 1:
                hex = StringUtils.leftPad( Integer.toHexString( instruction & 0xff ) , 2 , '0' );
                bin = StringUtils.leftPad( Integer.toBinaryString( instruction & 0xff ) , 8 , '0' );
                break;
            case 2:
                hex = StringUtils.leftPad( Integer.toHexString( instruction & 0xffff ) , 4 , '0' );
                bin = StringUtils.leftPad( Integer.toBinaryString( instruction & 0xffff ) , 16 , '0' );
                break;
            case 3:
                hex = StringUtils.leftPad( Integer.toHexString( instruction & 0xffffff ) , 6 , '0' );
                bin = StringUtils.leftPad( Integer.toBinaryString( instruction & 0xffffff ) , 24 , '0' );
                break;
            case 4:
                hex = StringUtils.leftPad( Integer.toHexString( instruction ) , 8 , '0' );
                bin = StringUtils.leftPad( Integer.toBinaryString( instruction ) , 32 , '0' );
                break;
            default:
                throw new RuntimeException( "Unreachable code reached");
        }
        System.out.println( node.instruction.getMnemonic().toUpperCase()+" "+dstValue+" , "+srcValue+" [ "+node+" ]"+
                            " compiled => "+prettyPrint(hex,2)+" ( "+prettyPrint(bin,4)+" )");
        System.out.println( "ENCODING: "+encoding);
    }
    
    private static String prettyPrint(final String hex,int indent) 
    {
        final StringBuilder  hex2 = new StringBuilder();
        for ( int i = 0 ; i < hex.length() ; i++ ) {
            if ( i > 0 && (i%indent) == 0 && (i+1) < hex.length() ) {
                hex2.append(' ');
            }
            hex2.append( hex.charAt( i ) );
        }
        return hex2.toString();
    }
    
    private int getSrcValue( ASTNode node, ArgumentType type, ICompilationContext context) 
    {
        try {
            return getValue(node,type,context);
        } 
        catch(Exception e) {
            throw new RuntimeException("SRC operand: "+e.getMessage(),e);
        }
    }
    
    private int getDstValue( ASTNode node, ArgumentType type, ICompilationContext context) 
    {
        try {
            return getValue(node,type,context);
        } 
        catch(Exception e) {
            throw new RuntimeException("DST operand: "+e.getMessage(),e);
        }
    }
    
    private int getValue( ASTNode node, ArgumentType type, ICompilationContext context) 
    {
        switch( type ) 
        {
            case COMPOUND_REGISTER:
            case COMPOUND_REGISTERS_R24_TO_R30:
                if ( ! (node instanceof RegisterNode) || ! isCompoundRegister( ((RegisterNode) node).register ) ) 
                {
                    throw new RuntimeException("Operand needs to be a compound register expression");
                }
                final int val = getRegisterNumber( ((RegisterNode) node).register.name() );
                if ( type == ArgumentType.COMPOUND_REGISTERS_R24_TO_R30 ) 
                {
                    if ( val != 24 && val != 26 && val != 28 && val != 30 ) 
                    {
                        throw new RuntimeException("Operand needs to be a compound register expression with X/Y/Z (r26:r25/r28:r27/r31:r30)");                        
                    }
                }
                break;
            case SINGLE_REGISTER:
                if ( ! (node instanceof RegisterNode) || isCompoundRegister( ((RegisterNode) node).register ) ) 
                {
                    throw new RuntimeException("Operand needs to be a single register");
                }                
                break;
            case EIGHT_BIT_CONSTANT:
            case SEVEN_BIT_SIGNED_CONSTANT:
            case TWELVE_BIT_SIGNED_CONSTANT:                
            case SIX_BIT_CONSTANT:
            case FOUR_BIT_CONSTANT:
            case THREE_BIT_CONSTANT:
            case FIVE_BIT_IO_REGISTER_CONSTANT:
            case SIX_BIT_IO_REGISTER_CONSTANT:
            case DATASPACE_16_BIT_ADDESS: // TODO: Validate range according to spec                
            case SRAM_MEM_ADDRESS:                
            case FLASH_MEM_ADDRESS:                
                if ( ! (node instanceof NumberLiteralNode)) {
                    throw new RuntimeException("Operand must evaluate to a constant number (expected: " +type+", was: "+node.getClass().getName()+")");
                }                
                break;
            case NONE:
                if ( node != null ) {
                    throw new RuntimeException("Expected no argument");
                }
                return 0;
            case R0_TO_R15:
            case R16_TO_R31:
                if ( ! (node instanceof RegisterNode)) {
                    throw new RuntimeException("Operand must be a register");
                }                
                final RegisterNode reg = (RegisterNode) node;
                if ( isCompoundRegister( reg.register ) ) {
                    throw new RuntimeException("Operand must not be a compound register expression");
                }
                final int regNum = getRegisterNumber( reg.register.name() );
                if ( type == ArgumentType.R0_TO_R15 ) {
                    if ( regNum > 15 ) {
                        throw new RuntimeException("Operand must be R0...15");
                    }
                } else if ( type == ArgumentType.R16_TO_R31 ) {
                    if ( regNum < 16 ) {
                        throw new RuntimeException("Operand must be R16...R31");
                    }                    
                } else {
                    throw new RuntimeException("Unreachable code reached");
                }
                break;
            default:
                throw new RuntimeException("Internal error,unhandled switch/case: "+type);
        }
        if ( node == null ) {
            throw new RuntimeException("Missing operand");
        }
        
        final int result;
        if ( node instanceof NumberLiteralNode ) {
            result = ((NumberLiteralNode) node).getValue();
        } 
        else if ( node instanceof RegisterNode) 
        {
            result = getValue( (RegisterNode) node , type , context );
            if ( result == -1 ) { // something went wrong
                return 0; // RETURN zero value as this should always be encodeable by the InstructionEncoder 
            }
        } else {
            throw new RuntimeException("Internal error, don't know how to get value from "+node);
        }
        if ( type == ArgumentType.FLASH_MEM_ADDRESS ) 
        {
            if ( result < 0 || result > getFlashMemorySize() ) {
                throw new RuntimeException("Address "+result+" is out-of-range, target architecture only has "+getFlashMemorySize()+" bytes of flash memory");
            }
        } 
        else if ( type == ArgumentType.SRAM_MEM_ADDRESS ) 
        {
            if ( result < 0 || result > getSRAMMemorySize() ) {
                throw new RuntimeException("Address "+result+" is out-of-range, target architecture only has "+getFlashMemorySize()+" bytes of SRAM memory");
            }
        }
        return result;
    }
    
    private int getValue(RegisterNode node,ArgumentType type,ICompilationContext context) 
    {
        final int result = getRegisterNumber( node.register.name() );
        if ( result == -1 ) {
            context.message( new CompilationMessage(Severity.ERROR , "Register needs to be r0...r31" , node ) );
        }
        return result;
    }
    
    private int getRegisterNumber(String name) {
        final int result = internalGetRegisterNumber(name);
        System.out.println("getRegisterNumber( "+name+") => "+result);
        return result;
    }
    
    private int internalGetRegisterNumber(String name) 
    {
        name = name.toLowerCase();
        
        if ( name.contains(":" ) ) { // r26:r25
            final String[] parts = name.split(":");
            final int regUpper = getRegisterNumber( parts[0] );
            final int regLower = getRegisterNumber( parts[1] );
            if ( (regUpper%1) == 0 ) {
                throw new RuntimeException("Upper compound register must be an odd register");
            }            
            if ( (regLower%1) != 0 ) {
                throw new RuntimeException("Lower compound register must be an even register");
            }
            if ( regUpper <= regLower ) {
                throw new RuntimeException("Invalid compound register (bad register order)");                
            }
            if ( (regUpper-regLower) != 1 ) {
                throw new RuntimeException("Invalid compound register (non-adjacent registers)");                
            }
            // always return lower part for compound registers, assumed by instruction encoding
            return regLower;
        }
        
        // X=R27:R26, Y=R29: R28 and Z=R31:R30
        if ( name.equals("x") || name.equals("x+" ) ) {
            return 26; // always return lower part for compound registers, assumed by instruction encoding
        }
        if ( name.equals("y") || name.equals("y+" ) ) {
            return 28; // always return lower part for compound registers, assumed by instruction encoding
        }
        if ( name.equals("z") || name.equals("z+" ) ) {
            return 30; // always return lower part for compound registers, assumed by instruction encoding
        }        
        
        int register = -1;        
        if ( name.length() == 2 ) 
        { 
            // r0...r8
            if ( name.charAt(0) == 'r' ) {
                register = Integer.parseInt( name.substring(1) );
            }
        } 
        else if ( name.length() == 3 ) 
        {
            final char first = name.charAt(1);
            if ( first != '0' ) 
            {
                register = Integer.parseInt( name.substring(1) );
            }
        } 
        return register;
    }
    
    private boolean isCompoundRegister(Register r)
    {
        final String name = r.name();
        if ( name.equals("x") || name.equals("x+" ) ) {
            return true;
        }
        if ( name.equals("y") || name.equals("y+" ) ) {
            return true;
        }
        if ( name.equals("z") || name.equals("z+" ) ) {
            return true;
        }         
        return name.contains(":");
    }
    
    @Override
    public int getFlashMemorySize() {
        return 8192;
    }

    @Override
    public int getSRAMMemorySize() {
        return 512;
    }
}