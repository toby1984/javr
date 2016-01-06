package de.codesourcery.javr.assembler.arch;
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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;
import org.apache.log4j.Logger;

import de.codesourcery.javr.assembler.Address;
import de.codesourcery.javr.assembler.ICompilationContext;
import de.codesourcery.javr.assembler.Register;
import de.codesourcery.javr.assembler.Segment;
import de.codesourcery.javr.assembler.arch.AbstractAchitecture.InstructionEncoding;
import de.codesourcery.javr.assembler.arch.InstructionEncoder.Transform;
import de.codesourcery.javr.assembler.parser.Parser.CompilationMessage;
import de.codesourcery.javr.assembler.parser.ast.ASTNode;
import de.codesourcery.javr.assembler.parser.ast.IValueNode;
import de.codesourcery.javr.assembler.parser.ast.InstructionNode;
import de.codesourcery.javr.assembler.parser.ast.RegisterNode;

public abstract class AbstractAchitecture implements IArchitecture 
{
    @SuppressWarnings("unused")
    private static final Logger LOG = Logger.getLogger(AbstractAchitecture.class);

    private static final long VALUE_UNAVAILABLE= 0xdeadabcdbeefdeadL;
    
    private static final InstructionSelector DEFAULT_INSN_SELECTOR = new InstructionSelector() {

        @Override
        public InstructionEncoding pick(InstructionNode node,List<InstructionEncoding> candidates) 
        {
            if ( candidates.size() != 1 ) {
                throw new RuntimeException("This instruction selector only supports exactly 1 candidate encoding (got: "+candidates+")");
            }
            return candidates.get(0);
        }

        @Override
        public int getMaxInstructionLengthInBytes(InstructionNode node,List<InstructionEncoding> candidates,boolean estimate) 
        {
            if ( candidates.size() != 1 ) {
                throw new RuntimeException("This instruction selector only supports exactly 1 candidate encoding (got: "+candidates+")");
            }            
            return candidates.get(0).getInstructionLengthInBytes();
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
    
    public interface InstructionSelector 
    {
        public InstructionEncoding pick(InstructionNode node,List<InstructionEncoding> candidates);
        
        public int getMaxInstructionLengthInBytes(InstructionNode node,List<InstructionEncoding> candidates,boolean estimate);
    }
    
    public static final class EncodingEntry 
    {
        public final InstructionSelector selector;
        public final List<InstructionEncoding> encodings = new ArrayList<>();
        
        public EncodingEntry(InstructionSelector chooser,InstructionEncoding[] encodings) 
        {
            this( chooser , encodings[0] , Arrays.copyOfRange( encodings , 1 , encodings.length) );
        }
        
        public EncodingEntry(InstructionSelector chooser,InstructionEncoding encoding1,InstructionEncoding... additional) 
        {
            Validate.notNull(chooser, "chooser must not be NULL");
            this.selector = chooser;
            add( encoding1 );
            if ( additional != null ) 
            {
                Stream.of(additional).forEach( this::add );
            }
        }
        
        public void add( InstructionEncoding enc) 
        {
            Validate.notNull(enc, "enc must not be NULL");
            if ( ! this.encodings.isEmpty() ) 
            {
                if ( ! enc.mnemonic.equals( this.encodings.get(0).mnemonic ) ) {
                    throw new IllegalArgumentException("Mnemonics differ");
                }
            }
            this.encodings.add(enc);
        }
        
        public InstructionEncoding getEncoding(InstructionNode insn) {
            return selector.pick( insn , encodings );
        }
        
        public int getInstructionLengthInBytes(InstructionNode insn,boolean estimate) {
            return selector.getMaxInstructionLengthInBytes( insn , encodings , estimate );
        }
    }
    
    protected final Map<String,EncodingEntry> instructions = new HashMap<>();
    
    protected InstructionEncoding insn(String mnemonic,String pattern) 
    {
        return insn( mnemonic , pattern , ArgumentType.NONE , ArgumentType.NONE , false , DEFAULT_INSN_SELECTOR);
    }
    
    protected InstructionEncoding insn(String mnemonic,String pattern,ArgumentType dstType) 
    {
        return insn( mnemonic , pattern , dstType , ArgumentType.NONE , false , DEFAULT_INSN_SELECTOR );
    }  
    
    protected InstructionEncoding insn(String mnemonic,String pattern,ArgumentType dstType,ArgumentType srcType) 
    {
        return insn( mnemonic , pattern , dstType , srcType , false , DEFAULT_INSN_SELECTOR );
    }
    
    protected InstructionEncoding insn(String mnemonic,String pattern,ArgumentType dstType,ArgumentType srcType,InstructionSelector chooser) 
    {
        if ( chooser == DEFAULT_INSN_SELECTOR ) {
            throw new IllegalArgumentException("Internal error, default selector does not support multiple encodings");
        }    
        return insn(mnemonic, pattern, dstType, srcType, true , chooser);
    }
    
    protected final void add(EncodingEntry entry) 
    {
        Validate.notNull(entry, "entry must not be NULL");
        if ( entry.encodings.isEmpty() ) {
            throw new IllegalArgumentException("Encoding entry needs to have at least one encoding");
        }
        final String mnemonic = entry.encodings.get(0).mnemonic;
        if ( ! mnemonic.toLowerCase().equals( mnemonic ) ) {
            throw new RuntimeException("Mnemonics need to be lower-case");
        }        
        if ( entry.encodings.stream().anyMatch( enc -> ! enc.mnemonic.equals( mnemonic ) ) ) {
            throw new RuntimeException("Refusing to add entry that contains mixed mnemonics");
        }
        if ( instructions.containsKey( mnemonic ) ) {
            throw new RuntimeException("Duplicate entry for mnemonic '"+mnemonic+"'");
        }
        instructions.put( mnemonic , entry );
    }
    
    protected InstructionEncoding insn(String mnemonic,String pattern,ArgumentType dstType,ArgumentType srcType,boolean multipleEncodings,InstructionSelector chooser) 
    {
        if ( ! mnemonic.toLowerCase().equals( mnemonic ) ) {
            throw new RuntimeException("Mnemonics need to be lower-case");
        }
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
    
    public static enum ArgumentType 
    {
        SINGLE_REGISTER,
        COMPOUND_REGISTERS_R24_TO_R30,
        COMPOUND_REGISTER_FOUR_BITS,
        R0_TO_R15,
        R16_TO_R31,
        R16_TO_R23,
        X_REGISTER,
        Y_REGISTER,
        Z_REGISTER,
        Y_REGISTER_DISPLACEMENT,
        Z_REGISTER_DISPLACEMENT,
        // addresses
        FLASH_MEM_ADDRESS, // device-dependent, covers whole address range
        SIXTEEN_BIT_SRAM_MEM_ADDRESS, // device-dependent, covers whole address range
        SEVEN_BIT_SRAM_MEM_ADDRESS,
        DATASPACE_16_BIT_ADDESS,
        // constant values
        THREE_BIT_CONSTANT,
        FOUR_BIT_CONSTANT,
        SIX_BIT_CONSTANT,
        SEVEN_BIT_SIGNED_BRANCH_OFFSET,
        EIGHT_BIT_CONSTANT,
        TWELVE_BIT_SIGNED_JUMP_OFFSET,
        // IO register constants
        FIVE_BIT_IO_REGISTER_CONSTANT,
        SIX_BIT_IO_REGISTER_CONSTANT,        
        // no-argument marker
        NONE
    }
    
    protected interface DisassemblySelector 
    {
        public InstructionEncoding pick(List<InstructionEncoding> candidates,int value);
    }
    
    protected static final class SameOperandsDisassemblySelector implements DisassemblySelector 
    {
        private final InstructionEncoding sameOperands;
        private final InstructionEncoding differentOperands;
        
        public SameOperandsDisassemblySelector(InstructionEncoding sameOperands,InstructionEncoding differentOperands) 
        {
            Validate.notNull(sameOperands, "sameOperands must not be NULL");
            Validate.notNull(differentOperands, "differentOperands must not be NULL");
            this.sameOperands = sameOperands;
            this.differentOperands = differentOperands;
            if ( ! ( sameOperands.getArgumentCount() == 2 && differentOperands.getArgumentCount() == 1 ) &&
                 ! ( sameOperands.getArgumentCount() == 1 && differentOperands.getArgumentCount() == 2 ) ) 
            {
                throw new IllegalArgumentException("Unsupported argument counts: "+sameOperands.getArgumentCount()+" <-> "+differentOperands.getArgumentCount());
            }
        }
        
        @Override
        public InstructionEncoding pick(List<InstructionEncoding> candidates,int value) 
        {
            if ( candidates.size() == 1 ) {
                return candidates.get(0);
            }
            if ( candidates.size() != 2 ) {
                throw new IllegalArgumentException("Unsupported candidate count");
            }
            final InstructionEncoding enc1 = candidates.get(0);
            final InstructionEncoding enc2 = candidates.get(1);
            if ( (enc1 == sameOperands && enc2 == differentOperands) || (enc1 == differentOperands && enc2 == sameOperands ) ) 
            {
                final List<Integer> result1 = sameOperands.encoder.decode( value );
                final List<Integer> result2 = differentOperands.encoder.decode( value );
                if ( result1.equals( result2 ) ) {
                    return sameOperands;
                }
                // first check failed, 
                // missing src operand is returned as NULL value from decode(), 
                // copy value from dst operand and re-try comparison
                // this is to handle equivalent operations like ADD r0,r0 <=> ROL r0
                if ( result1.get(1) == null ) {
                    result1.set(1,result1.get(0) );
                }
                if ( result2.get(1) == null ) {
                    result2.set(1,result2.get(0) );
                }   
                if ( result1.equals( result2 ) ) {
                    return sameOperands;
                }                
                return differentOperands;
            } 
            throw new IllegalArgumentException("Unsupported candidates");
        }
    }
    
    protected static final DisassemblySelector DEFAULT_DISASM_SELECTOR = (candidates,value) -> 
    {
        switch(candidates.size() ) {
            case 0:
                throw new IllegalArgumentException("No candidates?");
            case 1:
                return candidates.get(0);
            case 2:
                if ( candidates.get(0).isAliasOf( candidates.get(1)) &&
                     candidates.get(1).isAliasOf( candidates.get(0)) ) 
                {
                    return candidates.get(0);
                }
            default:
                throw new IllegalArgumentException("More than one candidate: "+candidates);
        }
    };
    
    public static final class InstructionEncoding 
    {
        public final String mnemonic;
        public final InstructionEncoder encoder;
        public final ArgumentType srcType;
        public final ArgumentType dstType;
        public DisassemblySelector disasmSelector = DEFAULT_DISASM_SELECTOR;
        public InstructionEncoding aliasOf;
        
        public InstructionEncoding(String mnemonic,InstructionEncoder enc,ArgumentType dstType,ArgumentType srcType) 
        {
            Validate.notBlank(mnemonic, "mnemonic must not be NULL or blank");
            Validate.notNull(enc, "enc must not be NULL");
            Validate.notNull(dstType, "dstType must not be NULL");
            Validate.notNull(srcType, "dstType must not be NULL");
            if ( ! mnemonic.toLowerCase().equals( mnemonic ) ) {
                throw new IllegalArgumentException("Mnemonics need to be lower-case");
            }            
            this.mnemonic = mnemonic;
            this.encoder = enc;
            this.srcType = srcType;
            this.dstType = dstType;
        }     
        
        public void disassemblySelector(DisassemblySelector sel) 
        {
            Validate.notNull(sel, "sel must not be NULL");
            this.disasmSelector = sel;
        }
        
        public void aliasOf(InstructionEncoding other) 
        {
            Validate.notNull(other, "encoding must not be NULL");
            if ( other == this ) {
                throw new IllegalArgumentException("same instance doesn't make sense");
            }
            if ( this.aliasOf != null ) {
                throw new IllegalStateException("Alias already assigned");
            }
            this.aliasOf = other;
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

        public boolean isAliasOf(InstructionEncoding enc) 
        {
            return this.aliasOf != null && this.aliasOf == enc;
        }
    }
    
    @Override
    public final boolean hasType(Architecture t) {
        return t.equals( getType() );
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
    public int getInstructionLengthInBytes(InstructionNode node, ICompilationContext context,boolean estimate) 
    {
        final EncodingEntry variants = instructions.get( node.instruction.getMnemonic().toLowerCase() );
        if ( variants == null ) {
            throw new RuntimeException("Unknown instruction: "+node.instruction.getMnemonic()); 
        }          
        return variants.getInstructionLengthInBytes( node , estimate );
    }
    
    @Override
    public boolean validate(InstructionNode node,ICompilationContext context) 
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
            context.message( CompilationMessage.error( encoding.mnemonic.toUpperCase()+" expects "+encoding.getArgumentCount()+" arguments but got "+argCount,node ) );
            return false;
        }  
        
        boolean result = true;
        if ( encoding.dstType != ArgumentType.NONE ) {
            result &= ( getDstValue( child1 , encoding.dstType , context ,false ) != VALUE_UNAVAILABLE );
        }
        if ( encoding.srcType != ArgumentType.NONE ) {
            result &= ( getSrcValue( child2 , encoding.srcType , context ,false ) != VALUE_UNAVAILABLE );
        }
        return result;
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
        
        final long dstValue = getDstValue( child1 , encoding.dstType , context , true );
        final long srcValue = getSrcValue( child2 , encoding.srcType , context , true );

        if ( dstValue != VALUE_UNAVAILABLE && srcValue != VALUE_UNAVAILABLE ) 
        {
            final int instruction = encoding.encode( (int) dstValue , (int) srcValue );
            debugAssembly(node, encoding, (int) dstValue, (int) srcValue, instruction); // TODO: Remove debug code
            switch( encoding.getInstructionLengthInBytes() ) {
                case 2:
                    context.writeWord( instruction );
                    break;
                case 4:
                    context.writeWord( instruction >> 16 );
                    context.writeWord( instruction );
                default:
                    throw new RuntimeException("Unsupported length: "+encoding.getInstructionLengthInBytes());
            }
        }
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
    
    private long getSrcValue( ASTNode node, ArgumentType type, ICompilationContext context,boolean validateAddressRanges) 
    {
        try 
        {
            return getValue(node,type,context,validateAddressRanges);
        } 
        catch(Exception e) {
            throw new RuntimeException("SRC operand: "+e.getMessage(),e);
        }
    }
    
    private long getDstValue( ASTNode node, ArgumentType type, ICompilationContext context,boolean validateAddressRanges) 
    {
        try 
        {
            return getValue(node,type,context,validateAddressRanges);
        } 
        catch(Exception e) {
            throw new RuntimeException("DST operand: "+e.getMessage(),e);
        }
    }
    
    private long fail(String msg,ASTNode node,ICompilationContext context) 
    {
        context.message( CompilationMessage.error( msg , node ) );
        return VALUE_UNAVAILABLE;
    }
    
    private long getValue( ASTNode node, ArgumentType type, ICompilationContext context,boolean validateAddressRanges) 
    {
        switch( type ) 
        {
            case X_REGISTER:
            case Y_REGISTER:
            case Z_REGISTER:
            case Y_REGISTER_DISPLACEMENT:
            case Z_REGISTER_DISPLACEMENT:
            case COMPOUND_REGISTER_FOUR_BITS:
            case COMPOUND_REGISTERS_R24_TO_R30:
                if ( ! (node instanceof RegisterNode) || ! ((RegisterNode) node).register.isCompoundRegister() ) 
                {
                    return fail( "Operand needs to be a compound register expression",node,context );
                }
                final int val = ((RegisterNode) node).register.getRegisterNumber();
                if ( type == ArgumentType.COMPOUND_REGISTER_FOUR_BITS ) 
                {
                    // REG e {0,2,4,...30} encoded as 4 bit value 
                    return val >>> 1;
                }
                if ( type == ArgumentType.COMPOUND_REGISTERS_R24_TO_R30 ) 
                {
                    if ( val != 24 && val != Register.REG_X && val != Register.REG_Y && val != Register.REG_Z ) 
                    {
                    }
                    switch( val ) 
                    {
                        case 24:
                            return 0;
                        case Register.REG_X:
                            return 1;
                        case Register.REG_Y:
                            return 2;
                        case Register.REG_Z:
                            return 3;
                        default:
                            return fail("Operand needs to be a compound register expression with X/Y/Z (r25:r24/r27:r26/r29:r28/r31:r30)",node,context);
                    }
                } 
                if ( type == ArgumentType.X_REGISTER && val != Register.REG_X ) {
                    return fail("Operand needs to be Z register",node,context);
                } 
                if ( ( type == ArgumentType.Y_REGISTER || type == ArgumentType.Y_REGISTER_DISPLACEMENT ) && val != Register.REG_Y ) {
                    return fail("Operand needs to be Y register",node,context);
                } 
                if ( ( type == ArgumentType.Z_REGISTER || type == ArgumentType.Z_REGISTER_DISPLACEMENT ) && val != Register.REG_Z ) {
                    return fail("Operand needs to be Z register",node,context);
                }
                break;
            case SINGLE_REGISTER:
                if ( ! (node instanceof RegisterNode) || ((RegisterNode) node).register.isCompoundRegister() ) 
                {
                    return fail( "Operand needs to be a single register",node,context );
                }                
                break;
            case EIGHT_BIT_CONSTANT:
            case SEVEN_BIT_SIGNED_BRANCH_OFFSET:
            case TWELVE_BIT_SIGNED_JUMP_OFFSET:                
            case SIX_BIT_CONSTANT:
            case FOUR_BIT_CONSTANT:
            case THREE_BIT_CONSTANT:
            case FIVE_BIT_IO_REGISTER_CONSTANT:
            case SIX_BIT_IO_REGISTER_CONSTANT:
            case DATASPACE_16_BIT_ADDESS: // TODO: Validate range according to spec
            case SEVEN_BIT_SRAM_MEM_ADDRESS:                
            case SIXTEEN_BIT_SRAM_MEM_ADDRESS:                
            case FLASH_MEM_ADDRESS:                
                if ( ! (node instanceof IValueNode)) {
                    return fail("Operand must evaluate to a constant (expected: " +type+", was: "+node.getClass().getName()+")",node,context);
                }                
                break;
            case NONE:
                if ( node != null ) {
                    return fail("Instruction does not support any operands",node,context);
                }
                return 0;
            case R0_TO_R15:
            case R16_TO_R31:
                if ( ! (node instanceof RegisterNode)) {
                    return fail( "Operand must be a register",node,context);
                }                
                final RegisterNode reg = (RegisterNode) node;
                if ( reg.register.isCompoundRegister() ) {
                    return fail("Operand must not be a compound register expression",node,context);
                }
                final int regNum = reg.register.getRegisterNumber();
                if ( type == ArgumentType.R0_TO_R15 ) {
                    if ( regNum > 15 ) {
                        throw new RuntimeException("Operand must be R0...15");
                    }
                } else if ( type == ArgumentType.R16_TO_R31 ) {
                    if ( regNum < 16 ) {
                        return fail("Operand must be R16...R31",node,context);
                    }                    
                } else {
                    throw new RuntimeException("Unreachable code reached");
                }
                break;
            default:
                throw new RuntimeException("Internal error,unhandled switch/case: "+type);
        }
        
        final int result;
        if ( node instanceof IValueNode ) 
        {
            final Object value = ((IValueNode) node).getValue();
            if ( value instanceof Number) {
                result = ((Number) value).intValue();
            } 
            else if ( value instanceof Address) 
            {
                final Address actual = (Address) value;
                if ( ! actual.hasSegment( Segment.FLASH ) ) {
                    return fail("Expected at address in the code segment",node,context);
                }
                final Address current = context.currentAddress();   
                if ( ! current.hasSegment( Segment.FLASH ) ) {
                    throw new RuntimeException("Expected the ICompilationContext to be in FLASH");
                }                
                final int delta = actual.getWordAddress() - current.getWordAddress();
                switch( type ) 
                {
                    case TWELVE_BIT_SIGNED_JUMP_OFFSET:
                        if ( validateAddressRanges && ( delta < 2041 || delta > 2048 ) ) {
                            return fail("Jump distance out of 12-bit range (was: "+delta+")",node,context);
                        }
                        result = delta;
                        break;
                    case SEVEN_BIT_SIGNED_BRANCH_OFFSET:
                        if ( validateAddressRanges && ( delta < -64 || delta > 63 ) ) 
                        {
                            return fail("Jump distance out of 7-bit range (was: "+delta+")",node,context);
                        }                        
                        result = delta;
                        break;
                    default:
                        result = ((Address) value).getByteAddress();
                }
            } else {
                return fail("Operand needs to evaluate to a number",node,context);
            }
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
        
        switch ( type ) 
        {
            case FLASH_MEM_ADDRESS:
                if ( result < 0 || result > getFlashMemorySize() ) {
                    return fail("Address "+result+" is out-of-range, target architecture only has "+getFlashMemorySize()+" bytes of flash memory",node,context);
                }
                break;
            case SEVEN_BIT_SRAM_MEM_ADDRESS:
            case SIXTEEN_BIT_SRAM_MEM_ADDRESS: 
                if ( result < 0 || result > getSRAMMemorySize() ) {
                    return fail("Address "+result+" is out-of-range, target architecture only has "+getSRAMMemorySize()+" bytes of SRAM memory",node,context);
                }
                break;
        }
        return result;
    }
    
    private int getValue(RegisterNode node,ArgumentType type,ICompilationContext context) 
    {
        if ( type == ArgumentType.Z_REGISTER_DISPLACEMENT || type == ArgumentType.Y_REGISTER_DISPLACEMENT )
        {
            if ( node.hasNoChildren() ) {
                return 0;
            }
            if ( node.childCount() != 1 ) {
                throw new RuntimeException("Expected register node to have exactly one child node (the displacement)");
            }
            final IValueNode vn = (IValueNode) node.child(0);
            final Object value = vn.getValue();
            if ( !(value instanceof Number) ) {
                fail("Expected a number",node,context);
                return -1;
            }
            return ((Number) value).intValue();
        }
        int result = -1;
        try {
            result = node.register.getRegisterNumber();
            if ( result == -1 ) {
                fail( "Expected a valid register expression" , node , context);
            }
        } 
        catch(Exception e) 
        {
            fail( e.getMessage() , node , context );
        }
        return result;
    }
    
    private static String insertEvery(String input,int distance,String s) 
    {
        final StringBuilder builder = new StringBuilder();
        for ( int i = 0 ; i < input.length() ; i++ ) {
            if ( i > 0 && (i%distance) == 0 ) {
                builder.append( s );
            }
            builder.append( input.charAt(i) );
        }
        return builder.toString();
    }
    
    private static int reverseBytes(int value,int byteCount) 
    {
        final int result;
        switch(byteCount) 
        {
            case 1: result=value; break;
            case 2: result=reverse2Bytes(value); break;
            case 3: result=reverse3Bytes(value); break;
            case 4: result=reverse4Bytes(value); break;
            default:
                throw new RuntimeException("Illegal byte count: "+byteCount);
        }
        
        String in = StringUtils.leftPad( Integer.toBinaryString( value ) , 8*byteCount , '0' );
        String out = StringUtils.leftPad( Integer.toBinaryString( result ) , 8*byteCount , '0' );
        System.out.println("REVERSED: \n"+insertEvery(in,8,"_")+"\n"+insertEvery(out,8,"_"));
        return result;
    }    
    
    private static int reverse4Bytes(int value) 
    {
        return (value & 0xff000000) >>> 8 | (value & 0x00ff0000) << 8 | (value & 0x0000ff00) >>> 8 | (value & 0xff) << 8;
    }
    
    private static int reverse3Bytes(int value) 
    {
        return (value & 0xff000000) >>> 8 | (value & 0x00ff0000) <<8  | (value & 0x0000ff00);
    }    
    
    private static int reverse2Bytes(int value) 
    {
        return (value & 0xff000000) >>> 8  | (value & 0x00ff0000) << 8;
    } 

    @Override
    public String disassemble(byte[] data,int len,boolean printAddresses,int startAddress) 
    {
        // use prefix tree to narrow-down the
        // number of potential matches
        
        // note that the tree requires the input 
        // data to be in big-endian order, otherwise
        // if wouldn't work (and the instructions
        // in little-endian order have only very short
        // fixed prefixes so the tree approach would basically
        // be a waste
        final PrefixTree tree = new PrefixTree();
        for ( EncodingEntry entry : this.instructions.values() ) 
        {
            for ( InstructionEncoding i : entry.encodings ) {
                tree.add( i );
            }
        }
        
        final StringBuilder buffer = new StringBuilder();
        
        int ptr = 0;
        while ( ptr < len ) 
        {
            final int remaining = len - ptr;
            final int bytesToProcess = remaining >= 4 ? 4 : remaining;
            int value=0;
            for ( int i = 0 ; i < bytesToProcess ; i++ ) 
            {
                value <<= 8;
                value |= data[ptr+i] & 0xff;
            }
            // decoding assumes that data
            // starts with the MSB
            value <<= (4-bytesToProcess)*8;
            
            // convert to big endian so that the prefix tree works properly 
            value = reverseBytes( value , bytesToProcess );
            
            System.out.println("0x"+Integer.toHexString( ptr+startAddress)+": Trying to match "+bytesToProcess+" bytes : "+Integer.toBinaryString( value ) );
            final List<InstructionEncoding> matches = tree.getMatch( value );
            if ( buffer.length() > 0 ) 
            {
                buffer.append("\n");
            }
            
//            buffer.append("; 0x").append( StringUtils.leftPad( Integer.toHexString( startAddress+ptr ) , 4 , '0' ) ).append(":    ");            
            
            if ( matches.isEmpty() )
            {
                buffer.append(".db ");
                final int skip = remaining >= 2 ? 2 : remaining;
                for ( int i = 0 ; i < skip ; i++ ) {
                    buffer.append( "0x"+Integer.toHexString( data[ptr+i] & 0xff ) );
                    if ((i+1) < remaining) {
                        buffer.append(" , ");
                    }
                }
                buffer.append("\n");
                ptr += skip;
            } 
            else 
            {
                matches.sort( (a,b) -> {
                    return Integer.compare( b.encoder.getOpcodeBitCount() , a.encoder.getOpcodeBitCount() );
                });
                final int longestMatch = matches.get(0).encoder.getOpcodeBitCount();
                matches.removeIf( m -> m.encoder.getOpcodeBitCount() < longestMatch );
                
                final InstructionEncoding result = matches.get(0).disasmSelector.pick( matches , value );
                if ( printAddresses ) {
                    buffer.append( StringUtils.leftPad( Integer.toHexString( startAddress+ptr ) , 4 , '0' ) ).append(":    ");
                }
                print(result, buffer, value);
                ptr += result.getInstructionLengthInBytes();
            }
        }
        return buffer.toString();
    }

    private void print(final InstructionEncoding result, final StringBuilder buffer, int value) 
    {
        final List<Integer> operands = result.encoder.decode( value );
        System.out.println("DECODED: "+result.mnemonic+" "+operands);
        buffer.append( result.mnemonic.toUpperCase() );
        if ( result.getArgumentCount() == 1 ) 
        {
            buffer.append(" ").append( prettyPrint( operands.get(0) , result.dstType ) );
        } 
        else if ( result.getArgumentCount() == 2 ) 
        {
            buffer.append(" ").append( prettyPrint( operands.get(0) , result.dstType ) );
            buffer.append(",").append( prettyPrint( operands.get(1) , result.srcType ) );
        }
    }
    
    private String prettyPrint(Integer value, ArgumentType type) 
    {
        if ( type == ArgumentType.NONE ) {
            return "";
        }
        switch( type ) 
        {
            case DATASPACE_16_BIT_ADDESS:
            case EIGHT_BIT_CONSTANT:
            case FIVE_BIT_IO_REGISTER_CONSTANT:
            case FOUR_BIT_CONSTANT:
            case SEVEN_BIT_SIGNED_BRANCH_OFFSET:
            case SEVEN_BIT_SRAM_MEM_ADDRESS:
            case SIXTEEN_BIT_SRAM_MEM_ADDRESS:
            case FLASH_MEM_ADDRESS:
            case SIX_BIT_CONSTANT:
            case SIX_BIT_IO_REGISTER_CONSTANT:
            case THREE_BIT_CONSTANT:
            case TWELVE_BIT_SIGNED_JUMP_OFFSET:
                boolean signed=false;
                int bitCount=0;
                switch(type) {
                    case DATASPACE_16_BIT_ADDESS: signed=false ; bitCount = 16 ;break;
                    case EIGHT_BIT_CONSTANT:      signed=false ; bitCount =  8 ;break;
                    case FIVE_BIT_IO_REGISTER_CONSTANT:  signed=false ; bitCount = 5 ;break;
                    case FOUR_BIT_CONSTANT:  signed=false ; bitCount = 4 ;break;
                    case SEVEN_BIT_SIGNED_BRANCH_OFFSET:  signed=true ; bitCount = 7 ;break;
                    case SEVEN_BIT_SRAM_MEM_ADDRESS:  signed=false ; bitCount = 7 ;break;
                    case SIXTEEN_BIT_SRAM_MEM_ADDRESS:  signed=false ; bitCount = 16 ;break;
                    case FLASH_MEM_ADDRESS: 
                    case SIX_BIT_CONSTANT:  signed=false ; bitCount = 6 ;break;
                    case SIX_BIT_IO_REGISTER_CONSTANT:  signed=false ; bitCount = 6 ;break;
                    case THREE_BIT_CONSTANT:  signed=false ; bitCount = 3 ;break;
                    case TWELVE_BIT_SIGNED_JUMP_OFFSET:  signed=true; bitCount = 12 ;break;
                }
                return printConstant(value,signed,bitCount);
            default:
                //$$FALL-THROUGH;
        }
        
        switch(type) 
        {
            case COMPOUND_REGISTER_FOUR_BITS: // X/Y/Z (r27:r26/r29:r28/r31:r30
            case COMPOUND_REGISTERS_R24_TO_R30:
                if ( type == ArgumentType.COMPOUND_REGISTERS_R24_TO_R30 ) 
                {
                    switch( value ) 
                    {
                        case 0: value = 24; break;
                        case 1: value = 26; break;
                        case 2: value = 28; break;
                        case 3: value = 30; break;
                        default:
                            throw new RuntimeException("Invalid compound-register value "+value+" , must not be odd");
                    }
                } else if ( type == ArgumentType.COMPOUND_REGISTER_FOUR_BITS ) {
                    value <<= 1;
                } else {
                    throw new RuntimeException("Unreachable code reached");
                }
                switch( value ) 
                {
                    case Register.REG_X:
                        return "X";
                    case Register.REG_Y:
                        return "Y";
                    case Register.REG_Z:
                        return "Z";
                }
                return "R"+(value+1)+":R"+value;
            case R0_TO_R15:
            case SINGLE_REGISTER:
                return "R"+value;
            case R16_TO_R23:
                return "R"+(16+value);
            case R16_TO_R31:
                return "R"+(16+value);
            case X_REGISTER:
                return "X";
            case Y_REGISTER:
                return "Y";
            case Y_REGISTER_DISPLACEMENT:
                return "Y+";
            case Z_REGISTER:
                return "Z";
            case Z_REGISTER_DISPLACEMENT:
                return "Z+";
            default:
                // $$FALL-THROUGH$
        }
        throw new RuntimeException("Unhandled type: "+type);
    }
    
    private String printConstant(int value,boolean signed,int bitCount) 
    {
//        if ( signed ) {
//            value = signExtend(value,bitCount);
//        }
//        if ( bitCount <= 3 || signed) { // <= 3 bit => print decimal
//            return Integer.toString( value ); 
//        }
        String result = Integer.toHexString( value );
        if ( (result.length()&1) == 1 ) {
            result = "0"+result;
        }
        return "0x"+result;
    }
    
    private static int signExtend(int value,int bitCount) 
    {
        // 100
        final int mask = 1<<(bitCount-1);
        final boolean isNegative = (value&mask) != 0;
        if ( isNegative ) {
            final int signMask = ~((1<<bitCount)-1);
            return value | signMask;
        }
        return value;
    }
}