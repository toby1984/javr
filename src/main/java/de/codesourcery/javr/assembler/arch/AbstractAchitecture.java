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
    
    public static enum ArgumentType 
    {
        // single register
        SINGLE_REGISTER,
        // compound register
        COMPOUND_REGISTERS_R24_TO_R30, // register is encoded as two-bit value, 00=r25:r24 , 01 = r27:r26 , 10 = r29:r28 , 11 = r31:r30  
        COMPOUND_REGISTER_FOUR_BITS, // value used in opcode is the number of the lower register
        R0_TO_R15,
        R16_TO_R31, // value used in opcode is regNum-16
        R16_TO_R23, // value used in opcode is regNum-16
        X_REGISTER, 
        Y_REGISTER,
        Z_REGISTER,
        // predecrement
        X_REGISTER_PREDECREMENT, 
        Y_REGISTER_PREDECREMENT,
        Z_REGISTER_PREDECREMENT,
        // post inc
        X_REGISTER_POST_INCREMENT,
        Y_REGISTER_POST_INCREMENT,
        Z_REGISTER_POST_INCREMENT,
        // displacement
        Y_REGISTER_SIX_BIT_DISPLACEMENT,
        Z_REGISTER_SIX_BIT_DISPLACEMENT,
        // unsigned constant values
        THREE_BIT_CONSTANT,
        FOUR_BIT_CONSTANT,
        SIX_BIT_CONSTANT,        
        EIGHT_BIT_CONSTANT,
        // absolute addresses
        TWENTYTWO_BIT_FLASH_MEM_ADDRESS, // device-dependent, covers whole address range
        SIXTEEN_BIT_SRAM_MEM_ADDRESS, // device-dependent, covers whole address range
        SEVEN_BIT_SRAM_MEM_ADDRESS,
        // signed jump offsets
        SEVEN_BIT_SIGNED_COND_BRANCH_OFFSET,
        TWELVE_BIT_SIGNED_JUMP_OFFSET,
        // IO register constants
        FIVE_BIT_IO_REGISTER_CONSTANT,
        SIX_BIT_IO_REGISTER_CONSTANT,        
        // special no-argument type
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
            if ( ! ( sameOperands.getArgumentCountFromPattern() == 2 && differentOperands.getArgumentCountFromPattern() == 1 ) &&
                 ! ( sameOperands.getArgumentCountFromPattern() == 1 && differentOperands.getArgumentCountFromPattern() == 2 ) ) 
            {
                throw new IllegalArgumentException("Unsupported argument counts: "+sameOperands.getArgumentCountFromPattern()+" <-> "+differentOperands.getArgumentCountFromPattern());
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
                final int maxLenInBytes = candidates.stream().mapToInt( cand -> cand.encoder.getInstructionLengthInBytes() ).max().getAsInt();
                LOG.error("pick(): More than one matching candidate",new Exception("More than one matching candidate"));
                LOG.error("pick(): value = "+StringUtils.leftPad( Integer.toBinaryString( value ) , maxLenInBytes*8, '0' ));
                candidates.forEach( cand -> {
                    LOG.error("pick(): "+cand);
                });
                throw new IllegalArgumentException("More than one matching candidate: "+candidates);
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
        public String disasmImplicitDestination;
        public String disasmImplicitSource;
        public String disasmMnemonic;
        
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
        
        public boolean hasImplicitSourceArgument() {
            return disasmImplicitSource != null;
        }        
        
        public boolean hasImplicitDestinationArgument() {
            return disasmImplicitDestination != null;
        }          
        
        public InstructionEncoding disasmMnemonic(String mnemonic) {
            Validate.notBlank(mnemonic, "mnemonic must not be NULL or blank");
            if ( ! mnemonic.toLowerCase().equals( mnemonic ) ) {
                throw new RuntimeException("mnemonics need to be all lower-case: "+mnemonic);
            }
            this.disasmMnemonic= mnemonic;
            return this;
        }        
        
        public InstructionEncoding disasmImplicitDestination(String disasmImplicitDestination) {
            Validate.notBlank(disasmImplicitDestination, "disasmImplicitDestination must not be NULL or blank");
            this.disasmImplicitDestination = disasmImplicitDestination;
            return this;
        }
        
        public InstructionEncoding disasmImplicitSource(String disasmImplicitSource) {
            Validate.notBlank(disasmImplicitSource, "disasmImplicitSource must not be NULL or blank");
            this.disasmImplicitSource = disasmImplicitSource;
            return this;
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
        
        /**
         * Returns the number of arguments (values) that are EXPLICITLY encoded IN THE OPCODE.
         * 
         * <p>Note that some opcodes automatically imply a certain src/dst argument ,
         * implied arguments are not accounted for by this method.</p>
         * <p>Use {@link #hasImplicitSourceArgument()} and {@link hasImplicitDestinationArgument()} to check whether this is the case.</p>
         * 
         * @return
         */
        public int getArgumentCountFromPattern() {
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
            if ( getArgumentCountFromPattern() == 1 && srcType != ArgumentType.NONE && dstType != ArgumentType.NONE ) 
            {
                // pattern has only one placeholder but ASM syntax takes two arguments => one of the arguments needs to be implicit
                if ( ! hasImplicitDestinationArgument() && ! hasImplicitSourceArgument() ) {
                    throw new RuntimeException("Pattern declares only one argument but encoding specified two argument types");
                }
                if ( hasImplicitDestinationArgument() ) { // need to swap src and dst because InstructionEncoder will always use the destination if pattern has only one value
                    int tmp = dstValue;
                    dstValue = srcValue;
                    srcValue = tmp;
                }
            }
            return encoder.encode( dstValue , srcValue );
        }

        public boolean isAliasOf(InstructionEncoding enc) 
        {
            return this.aliasOf != null && this.aliasOf == enc;
        }
    }
    
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
                    throw new IllegalArgumentException("Mnemonics differ: "+enc.mnemonic+" vs. "+this.encodings.get(0).mnemonic );
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
    
    // key is mnemonic in lower-case, value is corresponding encoding entry
    protected final Map<String,EncodingEntry> instructions = new HashMap<>();
    
    // map of alternative names
    // key is alternative mnemonic, value is mnemonic used as key in 'instructions' hashmap
    private final Map<String,String> alternativeMnemonics = new HashMap<>();
    
    // holds instruction prefixes in big-endian order
    protected final PrefixTree prefixTree = new PrefixTree();
    
    public AbstractAchitecture() 
    {
        initInstructions();
        
        // check for ambiguous patterns
        final Map<Integer,List<InstructionEncoding>> patterns = new HashMap<>();
        for ( EncodingEntry entry : instructions.values() ) 
        {
            for ( InstructionEncoding enc : entry.encodings ) 
            {
                sanityCheck( enc );
                List<InstructionEncoding> list = patterns.get( enc.encoder.getBinaryPattern() );
                if ( list == null ) {
                    list = new ArrayList<>();
                    patterns.put( enc.encoder.getBinaryPattern() , list );
                }
                list.add( enc );
            }
        }
        patterns.values().stream().filter( list -> list.size() > 1 ).forEach( list -> 
        {
            
            LOG.debug("Instruction encodings with ambiguous bit patterns:");
            final int longestPattern  = list.stream().mapToInt( enc -> enc.encoder.getPattern().length() ).max().orElse(0);
            for ( InstructionEncoding enc : list ) 
            {
                LOG.debug( StringUtils.leftPad( enc.mnemonic , 5 , " ")+" : "+StringUtils.leftPad( enc.encoder.getPattern() , longestPattern, ' ' ) );
            }
        });
    }
    
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
    
    protected InstructionEncoding insn(String mnemonic,String pattern,ArgumentType dstType,ArgumentType srcType,boolean multipleEncodings,InstructionSelector chooser) 
    {
        if ( ! mnemonic.toLowerCase().equals( mnemonic ) ) {
            throw new IllegalArgumentException("Mnemonics need to be lower-case");
        }            
        final InstructionEncoder enc = new InstructionEncoder( pattern );
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
        prefixTree.add( ins );        
        return ins;
    }     
    
    private void sanityCheck(InstructionEncoding entry) 
    {
        final String mnemonic = entry.mnemonic;
        if ( ! mnemonic.toLowerCase().equals( mnemonic ) ) {
            throw new RuntimeException("Mnemonics need to be all lower-case: "+entry);
        }
        int expArgCount = ( (entry.dstType==ArgumentType.NONE) ? 0 : 1 ) + ( (entry.srcType==ArgumentType.NONE) ? 0 : 1 );
        if ( entry.hasImplicitDestinationArgument() ) {
            expArgCount--;
        }
        if ( entry.hasImplicitSourceArgument() ) {
            expArgCount--;
        }        
        if ( expArgCount < 0  ) {
            throw new RuntimeException("Internal error, number of implied arguments does what's in the pattern ("+entry.encoder.getArgumentCount()+" vs. "+expArgCount+"): "+entry);
        }
        if ( entry.encoder.getArgumentCount() != expArgCount ) {
            throw new RuntimeException("Internal error, number of arguments in pattern ("+entry.encoder.getArgumentCount()+") does not match expectations ("+expArgCount+"): "+entry);
        }        
    }
    
    protected final void add(EncodingEntry entry) 
    {
        Validate.notNull(entry, "entry must not be NULL");
        if ( entry.encodings.isEmpty() ) {
            throw new IllegalArgumentException("Encoding entry needs to have at least one encoding");
        }
        final String mnemonic = entry.encodings.get(0).mnemonic;
        if ( entry.encodings.stream().anyMatch( enc -> ! enc.mnemonic.equals( mnemonic ) ) ) {
            throw new RuntimeException("Refusing to add entry that contains mixed mnemonics");
        }
        if ( instructions.containsKey( mnemonic ) ) {
            throw new RuntimeException("Already existing entry for mnemonic '"+mnemonic+"'");
        }
        instructions.put( mnemonic , entry );
        entry.encodings.forEach( prefixTree::add );
    }    
    
    @Override
    public final boolean hasType(Architecture t) {
        return t.equals( getType() );
    }

    @Override
    public boolean isValidMnemonic(String s) 
    {
        if ( StringUtils.isNotBlank( s ) ) 
        {
            return lookupInstruction( s ) != null;
        }
        return false;
    }
    
    @Override
    public int getInstructionLengthInBytes(InstructionNode node, ICompilationContext context,boolean estimate) 
    {
        final EncodingEntry variants = lookupInstruction( node.instruction.getMnemonic().toLowerCase() );
        if ( variants == null ) {
            throw new RuntimeException("Unknown instruction: "+node.instruction.getMnemonic()); 
        }          
        return variants.getInstructionLengthInBytes( node , estimate );
    }
    
    @Override
    public boolean validate(InstructionNode node,ICompilationContext context) 
    {
        ASTNode dstArgument = null;
        ASTNode srcArgument = null;
        switch( node.childCount() ) 
        {
            case 0: 
                break;
            case 2: srcArgument = node.child(1);
            //      $$FALL-THROUGH$$
            case 1: dstArgument = node.child(0); 
                break;
            default:
        }
        final int argCount = ( dstArgument != null ? 1 : 0 ) + (srcArgument != null ? 1 : 0 );
        
        final EncodingEntry variants = lookupInstruction( node.instruction.getMnemonic() );
        final InstructionEncoding encoding = variants.getEncoding( node );
        int expectedArgumentCount = encoding.getArgumentCountFromPattern();
        if ( encoding.hasImplicitSourceArgument() ) 
        {
            expectedArgumentCount++;
        }
        if ( encoding.hasImplicitDestinationArgument() ) 
        {
            expectedArgumentCount++;
        }        
        
        if ( argCount != expectedArgumentCount ) 
        {
            context.message( CompilationMessage.error( encoding.mnemonic.toUpperCase()+" expects "+encoding.getArgumentCountFromPattern()+" arguments but got "+argCount,node ) );
            return false;
        }        
        
        final boolean failOnAddressOutOfBounds = context.getCompilationSettings().isFailOnAddressOutOfRange();
        boolean result = true;
        if ( encoding.dstType != ArgumentType.NONE ) {
            result &= ( getDstValue( dstArgument , encoding.dstType , context ,true , failOnAddressOutOfBounds) != VALUE_UNAVAILABLE );
        }
        if ( encoding.srcType != ArgumentType.NONE ) {
            result &= ( getSrcValue( srcArgument , encoding.srcType , context ,true , failOnAddressOutOfBounds) != VALUE_UNAVAILABLE );
        }
        return result;
    }

    @Override
    public void compile(InstructionNode node, ICompilationContext context) 
    {
        ASTNode dstArgument = null;
        ASTNode srcArgument = null;
        switch( node.childCount() ) 
        {
            case 0: 
                break;
            case 2: srcArgument = node.child(1);
            //      $$FALL-THROUGH$$
            case 1: dstArgument = node.child(0); 
                break;
            default:
        }
        final int argCount = ( dstArgument != null ? 1 : 0 ) + (srcArgument != null ? 1 : 0 );
        
        final EncodingEntry variants = lookupInstruction( node.instruction.getMnemonic() );
        final InstructionEncoding encoding = variants.getEncoding( node );
        int expectedArgumentCount = encoding.getArgumentCountFromPattern();
        if ( encoding.hasImplicitSourceArgument() ) 
        {
            expectedArgumentCount++;
        }
        if ( encoding.hasImplicitDestinationArgument() ) 
        {
            expectedArgumentCount++;
        }        
        
        if ( argCount != expectedArgumentCount ) 
        {
            throw new RuntimeException( encoding.mnemonic+" expects "+encoding.getArgumentCountFromPattern()+" arguments but got "+argCount);
        }
        
        final boolean failOnAddressOutOfBounds = context.getCompilationSettings().isFailOnAddressOutOfRange();        
        final int dstValue = (int) getDstValue( dstArgument , encoding.dstType , context , false , failOnAddressOutOfBounds );
        final int srcValue = (int) getSrcValue( srcArgument , encoding.srcType , context , false , failOnAddressOutOfBounds );

        final int instruction = encoding.encode( dstValue , srcValue );
        if ( LOG.isDebugEnabled() ) {
            debugAssembly(node, encoding, dstValue, srcValue, instruction); // TODO: Remove debug code
        }
        switch( encoding.getInstructionLengthInBytes() ) {
            case 2:
                context.writeWord( instruction );
                break;
            case 4:
                context.writeWord( instruction >> 16 );
                context.writeWord( instruction );
                break;
            default:
                throw new RuntimeException("Unsupported instruction word length: "+encoding.getInstructionLengthInBytes()+" bytes");
        }
    }

    private void debugAssembly(InstructionNode node,final InstructionEncoding encoding, final int dstValue,final int srcValue, final int instruction) 
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
        LOG.debug( "compile():"+node.instruction.getMnemonic().toUpperCase()+" "+dstValue+" , "+srcValue+" [ "+node+" ]"+
                            " compiled => "+prettyPrint(hex,2)+" ( "+prettyPrint(bin,4)+" )");
        LOG.debug( "compile(): ENCODING: "+encoding);
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
    
    private long getSrcValue( ASTNode node, ArgumentType type, ICompilationContext context,boolean calledInResolvePhase,boolean failOnAddressOutOfBounds) 
    {
        try 
        {
            return getValue(node,type,context,calledInResolvePhase,failOnAddressOutOfBounds);
        } 
        catch(Exception e) {
            throw new RuntimeException("SRC operand: "+e.getMessage(),e);
        }
    }
    
    private long getDstValue( ASTNode node, ArgumentType type, ICompilationContext context,boolean calledInResolvePhase,boolean failOnAddressOutOfBounds) 
    {
        try 
        {
            return getValue(node,type,context,calledInResolvePhase,failOnAddressOutOfBounds);
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
    
    private void warn(String msg,ASTNode node,ICompilationContext context) 
    {
        context.message( CompilationMessage.warning( msg , node ) );
    }
    
    private static boolean isSingleRegister(ASTNode node) {
        return node instanceof RegisterNode && ! ((RegisterNode) node).register.isCompoundRegister();
    }
    
    private static boolean isCompoundRegister(ASTNode node) {
        return node instanceof RegisterNode && ((RegisterNode) node).register.isCompoundRegister();
    }    
    
    private static boolean fitsInBitfield(int value,int bitCount) 
    {
        // 3 bits = 0111
        // 1<<3    = 1000
        // 1<<3 -1 = 0111 
        final int mask = ~((1<<bitCount)-1);
        return ( value & mask ) == 0;
    }
    
    private static boolean fitsInSignedBitfield(int value,int bitCount) 
    {
        // 3 bits  = 0111
        // 1<<3    = 1000
        // 1<<3 -1 = 0111 
        final int mask = ~((1<<bitCount)-1);
        if ( ( value & mask ) == 0 ) {
            return true; // ok, positive number
        }
        // might be negative, make sure all upper bits are set
        return ( value & mask ) == mask;
    }    
    
    /**
     * Returns the raw value for a given instruction operand, ready for encoding it into the opcode.
     * 
     * <p>The returned value is validated and transformed in such a way that it satisfies all requirements/constraints
     * of the given argument type and is ready to be incorporated into the opcode.</p>
     * 
     * @param node
     * @param type
     * @param context
     * @param calledInResolvePhase
     * @return argument value or {@link #VALUE_UNAVAILABLE} if for some reason the value could not be determined.
     *           Messages will be added to the {@link ICompilationContext#error(String, ASTNode) context} as needed.
     */
    private long getValue( ASTNode node, ArgumentType type, ICompilationContext context,boolean calledInResolvePhase,boolean failOnAddressOutOfBounds) 
    {
        if ( type == ArgumentType.NONE ) {
            return 0;
        }
        
        final int result;
        if ( node instanceof IValueNode ) 
        {
            final Object value = ((IValueNode) node).getValue();
            
            if ( value == null && calledInResolvePhase ) {
                return VALUE_UNAVAILABLE;
            }
            if ( value instanceof Number) {
                result = ((Number) value).intValue();
            } 
            else if ( value instanceof Address) 
            {
                result = ((Address) value).getByteAddress();
            } else {
                return fail("Operand needs to evaluate to a number but was "+value,node,context);
            }
        } 
        else if ( node instanceof RegisterNode) 
        {
            result = getRegisterValue( (RegisterNode) node , type , context );
            if ( result == -1 ) { // something went wrong...
                return fail("Failed to get register value from ",node,context);
            }
        } else {
            throw new RuntimeException("Internal error, don't know how to get value from unhandled node type: "+node);
        }
        
        switch( type ) 
        {
            // single register
            case SINGLE_REGISTER:
                if ( ! isSingleRegister( node ) ) {
                    return fail( "Operand needs to be a single register",node,context);
                }  
                if ( result < 0 || result >= getGeneralPurposeRegisterCount() ) {
                    throw new RuntimeException("Operand must be R0..."+(getGeneralPurposeRegisterCount()-1));
                }                
                return result;
            case R0_TO_R15:
                if ( ! isSingleRegister( node ) ) {
                    return fail( "Operand needs to be a single register",node,context);
                }                   
                if ( result < 0 || result > 15 ) {
                    throw new RuntimeException("Operand needs to be R0...15");
                }
                return result; 
            case R16_TO_R23:
                if ( ! isSingleRegister( node ) ) {
                    return fail( "Operand needs to be a single register",node,context);
                }                   
                if ( result < 16 || result > 23 ) {
                    return fail("Operand neeeds to be R16...R23",node,context);
                }           
                return result-16;
            case R16_TO_R31:
                if ( ! isSingleRegister( node ) ) {
                    return fail( "Operand needs to be a single register",node,context);
                }                
                if ( result < 16 || result >= getGeneralPurposeRegisterCount() ) {
                    return fail("Operand needs to be R16...R31",node,context);
                }           
                return result-16;
            // compound registers
            case X_REGISTER_POST_INCREMENT:
            case Y_REGISTER_POST_INCREMENT:
            case Z_REGISTER_POST_INCREMENT:
                if ( ! isCompoundRegister( node ) ) 
                {
                    return fail( "Operand needs to be a compound register expression",node,context );
                }   
                if ( ! ((RegisterNode) node).register.isPostIncrement() ) {
                    return fail( "Operand needs to be a post-increment compound register expression",node,context );
                }
                return 0; // value doesn't matter, these argument types are used for operands that are always implied by the opcode itself 
            case X_REGISTER_PREDECREMENT:
            case Y_REGISTER_PREDECREMENT:
            case Z_REGISTER_PREDECREMENT:
                if ( ! isCompoundRegister( node ) ) 
                {
                    return fail( "Operand needs to be a compound register expression",node,context );
                }      
                if ( ! ((RegisterNode) node).register.isPreDecrement() ) {
                    return fail( "Operand needs to be a pre-decrement compound register expression",node,context );
                }                
                return 0; // value doesn't matter, these argument types are used for operands that are always implied by the opcode itself
            case X_REGISTER:
            case Y_REGISTER:
            case Z_REGISTER:
            case Y_REGISTER_SIX_BIT_DISPLACEMENT:
            case Z_REGISTER_SIX_BIT_DISPLACEMENT:
            case COMPOUND_REGISTER_FOUR_BITS:
            case COMPOUND_REGISTERS_R24_TO_R30:
                if ( ! isCompoundRegister( node ) ) 
                {
                    return fail( "Operand needs to be a compound register expression",node,context );
                }
                if ( type == ArgumentType.COMPOUND_REGISTER_FOUR_BITS ) 
                {
                    // REG e {0,2,4,...30} encoded as 4-bit value 
                    return result >>> 1;
                }
                if ( type == ArgumentType.COMPOUND_REGISTERS_R24_TO_R30 ) // encoded as 3-bit value
                {
                    switch( result ) 
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
                            return fail("Operand needs to be a compound register expression with ?/X/Y/Z (r25:r24/r27:r26/r29:r28/r31:r30)",node,context);
                    }
                } 
                if ( type == ArgumentType.X_REGISTER && result != Register.REG_X ) {
                    return fail("Operand needs to be X register",node,context);
                } 
                if ( type == ArgumentType.Y_REGISTER_SIX_BIT_DISPLACEMENT ) 
                {
                    // Z register is implied by opcode so this method must only return the displacement offset (see getRegisterValue() )
                    // check that the actual register used in the source code matches the implied one
                    if ( ((RegisterNode) node).register.getRegisterNumber() != Register.REG_Y ) {
                        return fail("Operand needs to be Y register",node,context);
                    }
                }                
                if ( type == ArgumentType.Y_REGISTER && result != Register.REG_Y ) {
                    return fail("Operand needs to be Y register",node,context);
                } 
                if ( type == ArgumentType.Z_REGISTER_SIX_BIT_DISPLACEMENT ) 
                {
                    // Z register is implied by opcode so this method must only return the displacement offset (see getRegisterValue() )
                    // check that the actual register used in the source code matches the implied one
                    if ( ((RegisterNode) node).register.getRegisterNumber() != Register.REG_Z ) {
                        return fail("Operand needs to be Z register",node,context);
                    }
                }
                if ( ( type == ArgumentType.Z_REGISTER && result != Register.REG_Z ) ) {
                    return fail("Operand needs to be Z register",node,context);
                }
                return result;
            case EIGHT_BIT_CONSTANT:
                if ( ! fitsInBitfield( result , 8 ) ) {
                    return fail("Operand out of 8-bit range: "+result,node,context);
                }
                return result;
            case SIX_BIT_CONSTANT:
                if ( ! fitsInBitfield( result , 6 ) ) {
                    return fail("Operand out of 6-bit range: "+result,node,context);
                }
                return result;                
            case FOUR_BIT_CONSTANT:
                if ( ! fitsInBitfield( result , 4 ) ) {
                    return fail("Operand out of 4-bit range: "+result,node,context);
                }
                return result;                 
            case THREE_BIT_CONSTANT:
                if ( ! fitsInBitfield( result , 3 ) ) {
                    return fail("Operand out of 3-bit range: "+result,node,context);
                }
                return result;                  
            case FIVE_BIT_IO_REGISTER_CONSTANT:  // 0..63
                if ( ! fitsInBitfield( result , 5 ) ) {
                    return fail("Operand out of 5-bit range (IO register): "+result,node,context);
                }
                if ( ! isValidIOSpaceAdress( getGeneralPurposeRegisterCount() + result ) ) { // I/O address space starts right after register file
                    return fail("Operand is not a valid I/O register address: "+result,node,context);
                }
                return result;
            case SIX_BIT_IO_REGISTER_CONSTANT: 
                if ( ! fitsInBitfield( result , 6 ) ) {
                    return fail("Operand out of 6-bit range (IO register): "+result,node,context);
                }
                if ( ! isValidIOSpaceAdress( getGeneralPurposeRegisterCount() + result ) ) { // I/O address space starts right after register file
                    final String msg = "Operand is not a valid I/O register address: "+result;
                    if ( failOnAddressOutOfBounds ) { 
                        return fail(msg,node,context);
                    } 
                    warn(msg,node,context);
                }                
                return result; 
            case SEVEN_BIT_SRAM_MEM_ADDRESS:       
                if ( ! fitsInBitfield( result , 7 ) ) {
                    return fail("Operand out of 7-bit range (SRAM address): "+result,node,context);
                }
                if ( ! isValidSRAMAdress( result ) ) 
                {
                    final String msg = "SRAM address out of range (0..."+(getSegmentSize(Segment.SRAM)-1)+"): "+result;
                    if ( failOnAddressOutOfBounds ) { 
                        return fail(msg,node,context);
                    }
                    warn(msg,node,context);
                }
                return result;
            case SIXTEEN_BIT_SRAM_MEM_ADDRESS:
                if ( ! fitsInBitfield( result , 16 ) ) {
                    return fail("Operand out of 16-bit range (SRAM address): "+result,node,context);
                }
                if ( ! isValidSRAMAdress( result ) ) 
                {
                    final String msg = "SRAM address out of range (0..."+(getSegmentSize(Segment.SRAM)-1)+"): "+result;
                    if ( failOnAddressOutOfBounds ) {
                        return fail(msg,node,context);
                    }
                    warn(msg,node,context);
                }                
                return result;
            // flash ram addresses / branch offsets
            case SEVEN_BIT_SIGNED_COND_BRANCH_OFFSET:
            case TWELVE_BIT_SIGNED_JUMP_OFFSET:                     
            case TWENTYTWO_BIT_FLASH_MEM_ADDRESS:                
                if ( ! (node instanceof IValueNode)) {
                    return fail("Operand must evaluate to a constant (expected: " +type+", was: "+node.getClass().getName()+")",node,context);
                }                
                // convert to word address since the assembler uses byte addresses internally
                if ( ( result & 1 ) != 0 ) {
                    return fail("FLASH memory destination address needs to be even but was 0x"+Integer.toHexString(result),node,context);
                }
                if ( ! isValidFlashAdress( result ) ) 
                {
                    final String msg = "Destination address out of FLASH memory range (0.."+(getSegmentSize(Segment.FLASH)-1)+"): "+result;
                    if ( failOnAddressOutOfBounds ) {
                        return fail(msg,node,context);
                    }
                    warn(msg,node,context);
                }
                
                final int wordAddress = result >> 1; // convert byte -> word address
                final Address current = context.currentAddress();
                if ( current.getSegment() != Segment.FLASH ) {
                    return fail("Operand must evaluate to a constant (expected: " +type+", was: "+node.getClass().getName()+")",node,context);                    
                }
                
                if ( type == ArgumentType.TWELVE_BIT_SIGNED_JUMP_OFFSET || type == ArgumentType.SEVEN_BIT_SIGNED_COND_BRANCH_OFFSET ) 
                {
                    // relative jump using signed offset
                    int deltaWords = wordAddress - current.getWordAddress() - 1; // relative branch instructions implicitly add +1 to the offset 
                    switch( type ) 
                    {
                        case TWELVE_BIT_SIGNED_JUMP_OFFSET:
                            if ( ! fitsInSignedBitfield( deltaWords,  12 ) ) {
                                return fail("Jump distance out of 12-bit range (was: "+deltaWords+" words)",node,context);
                            }
                            return deltaWords;
                        case SEVEN_BIT_SIGNED_COND_BRANCH_OFFSET:
                            if ( ! fitsInSignedBitfield( deltaWords,  7 ) ) 
                            {
                                return fail("Jump distance out of 7-bit range (was: "+deltaWords+" words)",node,context);
                            }                      
                            return deltaWords;
                        default:
                            throw new RuntimeException("Unreachable code reached");                            
                    }                     
                }
                // jump to absolute address
                if ( ! fitsInBitfield( wordAddress ,  22 ) ) {
                    return fail("FLASH address out of 22-bit range (was: 0x"+Integer.toHexString(result)+")",node,context);
                }                
                return wordAddress;
            default:
                throw new RuntimeException("Unhandled argument type: "+type);
        }
    }
    
    private int getRegisterValue(RegisterNode node,ArgumentType type,ICompilationContext context) 
    {
        if ( type == ArgumentType.Z_REGISTER_SIX_BIT_DISPLACEMENT || type == ArgumentType.Y_REGISTER_SIX_BIT_DISPLACEMENT )
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

    private List<InstructionEncoding> getMatches(int bigEndianMSBLeft,int bytesInValue) 
    {
        final List<InstructionEncoding> result = new ArrayList<>();
        for ( EncodingEntry entry : instructions.values() ) 
        {
            for ( InstructionEncoding enc : entry.encodings ) 
            {
                if ( enc.encoder.matches( bigEndianMSBLeft ) ) {
                    result.add( enc );
                }
            }
        }
        return result;
    }
    
    @Override
    public String disassemble(byte[] data,int len,DisassemblerSettings settings) 
    {
        // this code uses a prefix tree to narrow-down the
        // number of potential matches
        
        // note that the prefix tree requires the input 
        // data to be in big-endian order, otherwise
        // if wouldn't work (and the instructions
        // in little-endian order have only very short
        // fixed prefixes so the tree approach would basically
        // be a waste
        final StringBuilder buffer = new StringBuilder();
        
        int ptr = 0;
        while ( ptr < len ) 
        {
            final int remaining = len - ptr;
            final int bytesToProcess = remaining >= 4 ? 4 : remaining;
            int bigEndianMSBLeft=0;
            for ( int i = 0 ; i < bytesToProcess ; i++ ) 
            {
                bigEndianMSBLeft <<= 8;
                bigEndianMSBLeft |= data[ptr+i] & 0xff;
            }
            // decoding assumes that data
            // starts with the MSB
            bigEndianMSBLeft <<= (4-bytesToProcess)*8;
            
            // convert to big endian so that the prefix tree works properly 
            bigEndianMSBLeft = reverseBytes( bigEndianMSBLeft , bytesToProcess );
            
            final int currentByteAddress = ptr+settings.startAddress;
            String comment = "";
            
//            System.out.println("0x"+Integer.toHexString( currentByteAddress )+": Trying to match "+bytesToProcess+" bytes : "+Integer.toBinaryString( bigEndianMSBLeft ) );
            final List<InstructionEncoding> matches = getMatches(bigEndianMSBLeft,bytesToProcess);
            if ( buffer.length() > 0 ) 
            {
                buffer.append("\n");
            }
            
            if ( matches.isEmpty() ) // unknown opcode, print as .db XX
            {
                buffer.append(".db ");
                final int skip = remaining >= 2 ? 2 : remaining;
                for ( int i = 0 ; i < skip ; i++ ) {
                    buffer.append( "0x"+Integer.toHexString( data[ptr+i] & 0xff ) );
                    if ((i+1) < skip ) {
                        buffer.append(" , ");
                    }
                }
                ptr += skip;
            } 
            else 
            {
                matches.sort( (a,b) -> { // sort descending by number of significant ('1' or '0' bits) in matched pattern
                    return Integer.compare( b.encoder.getOpcodeBitCount() , a.encoder.getOpcodeBitCount() );
                });
                // keep only the longest matches and discard anything else
                final int longestMatch = matches.get(0).encoder.getOpcodeBitCount();
                matches.removeIf( m -> m.encoder.getOpcodeBitCount() < longestMatch );
                
                final InstructionEncoding result = matches.get(0).disasmSelector.pick( matches , bigEndianMSBLeft );
                if ( settings.printAddresses ) {
                    comment += StringUtils.leftPad( Integer.toHexString( currentByteAddress ) , 4 , '0' )+":    ";
                }
                
                if ( settings.printBytes ) {
                    comment += toHex( data , ptr , result.getInstructionLengthInBytes() );
                }
                print(result, buffer, bigEndianMSBLeft , settings , currentByteAddress);                    

                if ( comment.length() > 0 ) {
                    buffer.append("; ").append( comment );
                }
                ptr += result.getInstructionLengthInBytes();
            }
        }
        return buffer.toString().toLowerCase();
    }
    
    private String toHex(byte[] data,int offset,int len) 
    {
        final StringBuilder result = new StringBuilder();
        for ( int i = 0 ; i <len ; i++ ) 
        {
            if ( result.length() > 0 ) {
                result.append(" ");
            }
            result.append( StringUtils.leftPad( Integer.toHexString( data[offset+i] & 0xff ) , 2 , '0' ) );
        }
        return result.toString();
    }

    private void print(final InstructionEncoding result, final StringBuilder buffer, int value,DisassemblerSettings settings,int currentByteAddress) 
    {
        final List<Integer> operands = result.encoder.decode( value );
        final int argCountInAsmSyntax = (result.srcType == ArgumentType.NONE ? 0:1)+(result.dstType == ArgumentType.NONE ? 0:1);
        if ( argCountInAsmSyntax != result.encoder.getArgumentCount() ) 
        {
            
        }
        
        final String mnemonic = result.disasmMnemonic == null ? result.mnemonic : result.disasmMnemonic;
//        System.out.println("DECODED: "+mnemonic+" "+operands);
        buffer.append( mnemonic.toUpperCase() );
        if ( result.getArgumentCountFromPattern() == 0 ) 
        {
            buffer.append(" ");
            
            if ( result.disasmImplicitSource != null && result.disasmImplicitDestination != null ) {
                throw new IllegalStateException("Command may only have implicit src or destination but not both");
            } 
            
            if ( result.disasmImplicitDestination != null ) 
            {
                buffer.append( result.disasmImplicitDestination );
            } 
            else if ( result.disasmImplicitSource != null ) 
            {
                buffer.append( result.disasmImplicitSource );
            } 
        }  
        else if ( result.getArgumentCountFromPattern() == 1 ) 
        {
            buffer.append(" ");
            
            if ( result.disasmImplicitSource != null && result.disasmImplicitDestination != null ) {
                throw new IllegalStateException("Command may only have implicit src or destination but not both");
            } 
            
            if ( result.disasmImplicitDestination != null ) 
            {
                buffer.append( result.disasmImplicitDestination ).append(",");
                buffer.append( prettyPrint( operands.get(0) , result.srcType , settings, currentByteAddress ) );
            } 
            else if ( result.disasmImplicitSource != null ) 
            {
                buffer.append( prettyPrint( operands.get(0) , result.dstType , settings, currentByteAddress ) ).append(",");
                buffer.append( result.disasmImplicitSource );
            } 
            else {
                buffer.append( prettyPrint( operands.get(0) , result.dstType , settings, currentByteAddress ) );
            }
        } 
        else if ( result.getArgumentCountFromPattern() == 2 ) 
        {
            // new InstructionEncoding( "lpm" , new InstructionEncoder(  "1001 000d dddd 0100" ) , ArgumentType.SINGLE_REGISTER, ArgumentType.Z_REGISTER).disasmImplicitSource("Z");
            buffer.append(" ");
            if ( result.disasmImplicitDestination != null ) 
            {
                buffer.append( result.disasmImplicitDestination );
            } else {
                buffer.append( prettyPrint( operands.get(0) , result.dstType , settings, currentByteAddress ) );
            }
            buffer.append(",");
            if ( result.disasmImplicitSource != null ) 
            {
                buffer.append( result.disasmImplicitSource );
            } else {
                buffer.append( prettyPrint( operands.get(1) , result.srcType , settings, currentByteAddress ) );
            }
        }
    }
    
    private String prettyPrint(Integer value, ArgumentType type,DisassemblerSettings settings,int currentByteAddress) 
    {
        if ( type == ArgumentType.NONE ) {
            return "";
        }
        switch( type ) 
        {
            case THREE_BIT_CONSTANT:
            case FOUR_BIT_CONSTANT:
            case FIVE_BIT_IO_REGISTER_CONSTANT:
            case SIX_BIT_CONSTANT:
            case SIX_BIT_IO_REGISTER_CONSTANT:
            case EIGHT_BIT_CONSTANT:
            // absolute addresses
            case SEVEN_BIT_SRAM_MEM_ADDRESS:
            case SIXTEEN_BIT_SRAM_MEM_ADDRESS:
            case TWENTYTWO_BIT_FLASH_MEM_ADDRESS:
            // relative addresses
            case SEVEN_BIT_SIGNED_COND_BRANCH_OFFSET:
            case TWELVE_BIT_SIGNED_JUMP_OFFSET:
                int bitCount=0;
                switch(type) 
                {
                    case EIGHT_BIT_CONSTANT:      
                        bitCount =  8 ;
                        break;
                    case FIVE_BIT_IO_REGISTER_CONSTANT:  
                        bitCount = 5 ;
                        break;
                    case FOUR_BIT_CONSTANT:  
                        bitCount = 4 ;
                        break;
                    case SEVEN_BIT_SIGNED_COND_BRANCH_OFFSET:  
                        bitCount = 7 ;
                        int byteOffset = 2*signExtend(value,bitCount);
                        if ( settings.resolveRelativeAddresses ) 
                        {
                            // all branch instructions implicitly add +1 word to the offset since BRANCH 0 makes no sense
                            byteOffset += 2;
                            return "0x"+Integer.toHexString( currentByteAddress + byteOffset ); // always print addresses as bytes
                        }
                        return "."+ ( (byteOffset >= 0 ) ? "+"+byteOffset : ""+byteOffset );
                    case SEVEN_BIT_SRAM_MEM_ADDRESS:  
                        bitCount = 7 ;
                        break;
                    case TWENTYTWO_BIT_FLASH_MEM_ADDRESS:
                        bitCount = 22;
                        value <<= 1; // word -> byte address
                        break;
                    case SIXTEEN_BIT_SRAM_MEM_ADDRESS:  
                        bitCount = 16 ;
                        break;                        
                    case SIX_BIT_CONSTANT:  
                        bitCount = 6 ;
                        break;
                    case SIX_BIT_IO_REGISTER_CONSTANT:  
                        bitCount = 6 ;
                        break;
                    case THREE_BIT_CONSTANT:  
                        bitCount = 3 ;
                        break;
                    case TWELVE_BIT_SIGNED_JUMP_OFFSET:  
                        bitCount = 12 ;
                        byteOffset = 2*signExtend(value,bitCount);
                        if ( settings.resolveRelativeAddresses ) 
                        {
                            // all branch instructions implicitly add +1 word to the offset since BRANCH 0 makes no sense
                            byteOffset += 2;
                            return "0x"+Integer.toHexString( currentByteAddress + byteOffset ); // always print addresses as bytes
                        }                        
                        return "."+ ( (byteOffset >= 0 ) ? "+"+byteOffset : ""+byteOffset );
                    default:
                        // $$FALL-THROUGH$$
                }
                return printPaddedHexValue(value,bitCount);
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
                        return settings.printCompoundRegistersAsLower ? "r"+Register.REG_X : "X";
                    case Register.REG_Y:
                        return settings.printCompoundRegistersAsLower ? "r"+Register.REG_Y : "Y";
                    case Register.REG_Z:
                        return settings.printCompoundRegistersAsLower ? "r"+Register.REG_Z : "Z";
                }
                return settings.printCompoundRegistersAsLower ? "r"+value : "R"+(value+1)+":R"+value;
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
            case Y_REGISTER_SIX_BIT_DISPLACEMENT:
                return "Y+"+value;
            case Z_REGISTER:
                return "Z";
            case Z_REGISTER_SIX_BIT_DISPLACEMENT:
                return "Z+"+value;
            default:
                throw new RuntimeException("Unhandled type: "+type);
        }
    }
    
    private String printPaddedHexValue(int value,int bitCount) 
    {
        if ( value < 0 || bitCount <= 4 ) { 
            return Integer.toString( value ); 
        }
        String result = Integer.toHexString( value );
        if ( (result.length()&1) == 1 ) { // padd to even number of characters
            result = "0"+result;
        }
        return "0x"+result;
    }
    
    private static int signExtend(int value,int bitCount) 
    {
        // 100
        final int msb = 1<<(bitCount-1);
        final boolean msbSet = (value&msb) != 0;
        if ( msbSet ) {
            final int signMask = ~((1<<bitCount)-1);
            return value | signMask;
        }
        return value;
    }
    
    protected abstract boolean isValidFlashAdress(int byteAddress);
    protected abstract boolean isValidSRAMAdress(int byteAddress);
    protected abstract boolean isValidRegisterNumber(int number);
    protected abstract boolean isValidIOSpaceAdress(int byteAddress);
    protected abstract boolean isValidEEPROMAdress(int byteAddress);
    protected abstract int getGeneralPurposeRegisterCount();
    protected abstract void initInstructions();
    
    protected final EncodingEntry lookupInstruction(String mnemonic) 
    {
        Validate.notBlank(mnemonic, "mnemonic must not be NULL or blank");
        final String lowerCaseMnemonic = mnemonic.toLowerCase();
        EncodingEntry result = instructions.get( lowerCaseMnemonic );
        if ( result == null ) 
        {
            final String alias = alternativeMnemonics.get( lowerCaseMnemonic );
            if ( alias != null ) {
                result = instructions.get( alias );
            }
            if ( result == null ) {
                throw new RuntimeException("Unknown instruction: "+mnemonic);
            }
        }         
        return result;
    }
    
    protected final void aliasMnemonic(String alias,String realDeal) 
    {
        Validate.notBlank(alias, "alias must not be NULL or blank");
        Validate.notBlank(realDeal, "mnemonic must not be NULL or blank");
        if ( ! alias.toLowerCase().equals( alias ) ) {
            throw new RuntimeException("Mnemonics need to be all lower-case: "+alias);
        }
        if ( ! realDeal.toLowerCase().equals( realDeal ) ) {
            throw new RuntimeException("Mnemonics need to be all lower-case: "+realDeal);
        }        
        if ( instructions.get( realDeal ) == null ) {
            throw new RuntimeException("Refusing to register alias '"+alias+"' for non-existent instruction '"+realDeal+"'");
        }
        if ( alternativeMnemonics.containsKey( alias ) ) {
            throw new RuntimeException("Alias already exists: "+alias+" -> "+alternativeMnemonics.get(alias) );
        }
        alternativeMnemonics.put( alias , realDeal );
    }
}