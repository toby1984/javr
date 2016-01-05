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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;

public class InstructionEncoder 
{
    private static final Encoding NOP = new NOPEncoding();
    
    private static final Transform IDENTITY = x -> x;
    
    private static final char SRC_PATTERN = 's';
    private static final char DST_PATTERN = 'd';
    
    private final String pattern;
    private final String trimmedPattern;
    private final int binaryPattern;
    private final Encoding srcEncoding;
    private final Encoding dstEncoding;
    
    // number of '0' or '1' characters in pattern
    private final int opcodeBitCount;
    
    private Transform srcTransform = IDENTITY;
    private Transform dstTransform = IDENTITY;
    
    public interface Transform 
    {
        public int transform(int value);
    }
    
    public String getPattern() {
        return pattern;
    }
    
    public InstructionEncoder srcTransform(Transform t) 
    {
        
        Validate.notNull(t, "transform must not be NULL");
        this.srcTransform = t;
        return this;
    }
    
    public InstructionEncoder dstTransform(Transform t) 
    {
        
        Validate.notNull(t, "transform must not be NULL");
        this.dstTransform = t;
        return this;
    }    
    
    public String getTrimmedPattern() {
        return trimmedPattern;
    }
    
    public int getInstructionLengthInBytes() {
        return pattern.length()/8;
    }
    
    /**
     * 
     * @return the total number of '0' or '1' characters in the pattern 
     */
    public int getOpcodeBitCount() {
        return opcodeBitCount;
    }
    
    public boolean matches(int value) 
    {
        int mask = 0b10000000_00000000_00000000_00000000;
        for ( int i = 0,len=trimmedPattern.length() ; i < len ; i++ ) 
        {
            final boolean bitSet = (value&mask) != 0;
            final char c = trimmedPattern.charAt(i);
            if ( c == '0' ) {
                if ( bitSet ) {
                    return false;
                }
            } 
            else if ( c == '1' ) 
            {
                if ( ! bitSet ) {
                    return false;
                }
            } // else { /* the pattern contains a data bit at this position, both 0 and 1 are valid */ }
            mask >>>= 1;
        }
        return true;
    }
    
    public InstructionEncoder(String pat) 
    {
        Validate.notBlank(pat, "pattern must not be NULL or blank");
        String pattern = pat.toLowerCase().replace("_","") // support Java-style underscores in literals
                         .replace(" " , "" ) // support whitespace instead as well
                         .replace("k" , ""+SRC_PATTERN ) // support syntax used in Atmel spec
                         .replace("r" , ""+SRC_PATTERN ); // support syntax used in Atmel spec
        
        if ( ( pattern.length() % 8 ) != 0 || pattern.length() > 32 ) 
        {
            throw new IllegalArgumentException("Unsupported pattern length: "+pattern);
        }
        
        this.trimmedPattern = pattern;
        
        // hack to map pattern that only contain one unique non-digit character
        // to use DST_PATTERN instead (simplifies copy'n'paste from Atmel spec)
        final Set<Character> uniqueChars = new HashSet<>();
        for ( int i = 0 ; i < pattern.length() ; i++ ) 
        {
            switch ( pattern.charAt( i ) ) {
                case '0':
                case '1':
                    break;
                default:
                    uniqueChars.add( Character.valueOf( pattern.charAt(i) ) );
            }
        }
        final boolean hasMoreThanOneArgument = uniqueChars.size() > 1;
        
        final char dstPattern;
        if (uniqueChars.size() == 1) 
        {
            final char toReplace = uniqueChars.iterator().next();
            dstPattern = 'X';
            pattern = pattern.replace( toReplace , dstPattern );
        } else {
            dstPattern = DST_PATTERN;
        }
        
        // scan pattern
        int srcBitCount=0;
        int dstBitCount=0;
        int binaryPattern =0;
        int mask = 1;
        int opcodeBitCount = 0;
        for ( int i = pattern.length()-1 ; i >= 0 ; i-- ) 
        {
            final char c = pattern.charAt(i);
            if (c == '0') 
            {
                opcodeBitCount++;
            } 
            else if (c == '1') 
            {
                opcodeBitCount++;
                binaryPattern |= mask;
            } 
            else if ( hasMoreThanOneArgument && c == SRC_PATTERN) 
            {
                srcBitCount++;
            } 
            else if (c == dstPattern) 
            {
                dstBitCount++;
            } else {
                throw new RuntimeException("Invalid character '"+pattern.charAt(i)+"' in pattern: "+pattern);
            }
            mask <<= 1;
        }
        
        if ( dstBitCount == 0 && srcBitCount != 0 ) {
            throw new IllegalArgumentException("One-argument patterns must only use '"+DST_PATTERN+"'");
        }
        
        this.opcodeBitCount = opcodeBitCount;
        this.binaryPattern = binaryPattern;
        this.pattern = pattern;
        
        // dest bits mapping
        if ( dstBitCount > 0 ) 
        {
            dstEncoding = toEncoding( toBitMapping(pattern,dstPattern,dstBitCount) );
        } else {
            dstEncoding = NOP;
        }
        
        // source bits mapping
        if ( srcBitCount > 0 ) 
        {
            srcEncoding = toEncoding( toBitMapping(pattern,SRC_PATTERN,srcBitCount) );
        } else {
            srcEncoding = NOP;
        }
    }
    
    private Encoding toEncoding(int[] bitMapping) 
    {
        final List<BitRange> ranges = toBitRanges( bitMapping );
        if ( ranges.isEmpty() ) {
            throw new RuntimeException("Internal error, no range in bit mapping?");
        }
        if( ranges.size() > 2 ) {
            return new FreeFormEncoding(bitMapping);
        }
        final BitRange range1 = ranges.get(0);
        if ( ranges.size() == 1 ) {
            if ( range1.shiftOffset == 0 ) 
            {
                return new IdentityEncoding( range1.length );
            }
            return new ShiftedEncoding( range1.length , range1.shiftOffset );            
        }
        final BitRange range2 = ranges.get(1);
        return new ShiftedSingleSplitEncoding( range1, range2 );
    }
    
    public int getArgumentCount() 
    {
        if ( dstEncoding == NOP ) {
            return 0;
        }
        return srcEncoding == NOP ? 1 : 2;
    }
    
    private static int[] toBitMapping(String pattern,char p,int bitCount) 
    {
        final int[] mapping = new int[ bitCount ];
        final int len = pattern.length();
        for ( int j=0, i = pattern.length()-1 ; i >= 0 ; i-- ) {
            if ( pattern.charAt( i ) == p) {
                mapping[j++] = len-i-1;
            }
        }   
        return mapping;
    }
    
    private static List<BitRange> toBitRanges(int[] bitMapping) 
    {
        final List<BitRange> result = new ArrayList<>();
        
        int currentStart = 0;
        int maskShift = 0;
        for ( int i = 1 ; i < bitMapping.length ; i++ ) 
        {
            if ( bitMapping[i] != bitMapping[i-1]+1 ) 
            {
                final int len = i-currentStart;
                final int mask = ( (1<<len)-1 ) << maskShift;
                result.add( new BitRange( len , bitMapping[currentStart]-currentStart , mask ) );
                maskShift += len;
                currentStart = i;
            }
        }
        
        if ( result.isEmpty() ) 
        {
            final int mask = (1<<bitMapping.length)-1;
            result.add( new BitRange( bitMapping.length , bitMapping[0] , mask ) );
        } 
        else 
        {
            final int coveredLength = result.stream().mapToInt( s -> s.length ).sum();
            if ( coveredLength < bitMapping.length )
            {
                final int len = bitMapping.length - currentStart;
                final int mask = ( (1<<len)-1 ) << maskShift;
                result.add( new BitRange( len , bitMapping[currentStart] - maskShift , mask ) );        
            }
        }
        
        // sanity checks
        final int len = result.stream().mapToInt( r -> r.length ).sum();
        if ( len != bitMapping.length ) {
            throw new RuntimeException("Internal error, bit lengths don't match: "+len+" <-> "+bitMapping.length );
        }
        for ( BitRange r1 : result ) 
        {
            for ( BitRange r2 : result ) 
            {
                if ( r1 == r2) 
                {
                    continue;
                }
                if ( r1.overlaps( r2 ) ) {
                    throw new RuntimeException("Internal error, overlapping bit ranges: \n"+r1+"\n"+r2);
                }
            }
        }
        return result;
    }
    
    public int encode(int dstArgument,int srcArgument) 
    {
        return binaryPattern | dstEncoding.encode( dstTransform.transform( dstArgument ) ) | srcEncoding.encode( srcTransform.transform( srcArgument ) );
    }    
    
    public List<Integer> decode(int value) 
    {
        final List<Integer> result = new ArrayList<>();
        if ( dstEncoding != NOP ) {
            result.add( dstEncoding.decode( value ) );
        } else {
            result.add( null );
        }
        if ( srcEncoding != NOP ) {
            result.add( srcEncoding.decode( value ) );
        } else {
            result.add( null );
        }        
        return result;
    }
    
    protected interface Encoding 
    {
        public int encode(int value);
        
        public int decode(int value);
        
        public int getBitCount();
        
        public String getDescription();
    }
    
    public static final class NOPEncoding implements Encoding 
    {
        @Override
        public int encode(int value) {
            return 0;
        }

        @Override
        public int getBitCount() {
            return 0;
        }
        
        @Override
        public String getDescription() {
            return "NOP";
        }

        @Override
        public int decode(int value) {
            throw new UnsupportedOperationException();
        }
    }
    
    protected static abstract class AbstractEncoding implements Encoding 
    {
        private final int bitCount;
        private final int mask;
        
        public AbstractEncoding(int bitCount) 
        {
            if ( bitCount <= 0 ) {
                throw new IllegalArgumentException("Bit count must be > 0,was: "+bitCount);
            }
            this.bitCount = bitCount;
            this.mask = ~( (1<<(bitCount+1))-1);
        }
        
        @Override
        public final int getBitCount() {
            return bitCount;
        }

        @Override
        public int encode(int value) 
        {
            if ( ( value & mask ) != 0 ) {
                throw new RuntimeException("Value out of range, expected 0.."+((1<<(bitCount+1))-1)+" but got "+value);
            }
            return doEncode(value);
        }
        
        protected abstract int doEncode(int value);
    }
    
    protected final class IdentityEncoding extends AbstractEncoding 
    {
        public IdentityEncoding(int bitCount) {
            super(bitCount);
        }
        
        @Override
        protected int doEncode(int value) {
            return value;
        }
        
        @Override
        public String getDescription() {
            return "Identity( "+getBitCount()+" bits)";
        }        
        
        public int decode(int value) 
        {
            final int shiftedValue = value >> (4-getInstructionLengthInBytes())*8;
            final int mask = (1<<getBitCount() )-1;
            return shiftedValue & mask;
        }
    }
    
    protected final class ShiftedEncoding extends AbstractEncoding {

        private final int bitsToShift;
        
        public ShiftedEncoding(int bitCount,int bitsToShift) {
            super(bitCount);
            if ( bitsToShift < 0 || bitsToShift > 30) {
                throw new IllegalArgumentException("Bit shift count must be >= 0 and <=30,was: "+bitsToShift);
            }
            this.bitsToShift = bitsToShift;
        }

        @Override
        protected int doEncode(int value) {
            return value << bitsToShift;
        }
        
        @Override
        public String getDescription() {
            return "Shifted( "+getBitCount()+" bits, shifted by "+bitsToShift+")";
        }          
        
        public int decode(int value) 
        {
            final int shiftedValue = value >>> (4-getInstructionLengthInBytes())*8;
            int mask = (1<<getBitCount() )-1;
            mask <<= bitsToShift;
            return (shiftedValue & mask) >> bitsToShift;
        }        
    }
    
    protected final class FreeFormEncoding extends AbstractEncoding {

        private final int[] bitMapping;
        
        public FreeFormEncoding(int[] bitMapping) {
            super(bitMapping.length);
            this.bitMapping = new int[ bitMapping.length ];
            System.arraycopy( bitMapping , 0 , this.bitMapping , 0 , bitMapping.length);
        }

        @Override
        protected int doEncode(int value) 
        {
            int result = 0;
            for ( int i = 0 ; i < bitMapping.length ; i++ ) 
            {
                int readMask = 1<<i;
                if ( (value & readMask) != 0 ) 
                {
                    int writeMask = 1<<bitMapping[i];
                    result |= writeMask;
                }
            }
            return result;
        }
        
        public int decode(int value) 
        {
            final int shiftedValue = value >> (4-getInstructionLengthInBytes())*8;
            int result = 0;
            for ( int i = 0 ; i < bitMapping.length ; i++ ) 
            {
                if ( ( shiftedValue & 1 << bitMapping[i] ) != 0 ) {
                    result |= 1<<i;
                }
            }
            return result;
        }         
        
        @Override
        public String getDescription() {
            return "FreeForm( "+getBitCount()+" bits: "+Stream.of( bitMapping ).map( s -> ""+s ).collect( Collectors.joining(","));
        }          
    }    
    
    protected static final class BitRange {

        public final int length;
        public final int shiftOffset;
        public final int mask;
        
        public BitRange(int length, int shiftOffset,int mask) 
        {
            if ( length < 1 ) {
                throw new IllegalArgumentException("length must be > 0, was "+length);
            }
            if ( shiftOffset < 0 ) {
                throw new IllegalArgumentException("shift offset must be >= 0, was "+shiftOffset);
            }            
            this.length = length;
            this.shiftOffset = shiftOffset;
            this.mask = mask;
        }
        
        public boolean overlaps(BitRange other) 
        {
            int v1 = (0xffffffff & mask) << shiftOffset;
            int v2 = (0xffffffff & other.mask) << other.shiftOffset;
            return (v1 ^ v2 ) != (v1|v2);
        }
        
        public int getMask() {
            return mask;
        }
        
        @Override
        public String toString() {
            return "Range[len="+length+" , shift_offset="+shiftOffset+" , mask="+toBinary(mask)+",shifted_mask="+
                    toBinary(mask<<shiftOffset)+"]";
        }
    }
    
    // encoding where the value is split at some arbitrary bit position
    // and both halves of the value go to different bit offsets in the
    // destination
    //
    // e.g. 0010ddd10101011ddd1
    
    protected final class ShiftedSingleSplitEncoding extends AbstractEncoding {

        private final int lowerBitsShift;
        private final int upperBitsShift;
        private final int lowerBitMask;
        private final int upperBitMask;
        
        public ShiftedSingleSplitEncoding(BitRange lower,BitRange upper) 
        {
            super( lower.length + upper.length );
            
            this.lowerBitMask = lower.getMask();
            this.upperBitMask = upper.getMask();
            this.lowerBitsShift = lower.shiftOffset;
            this.upperBitsShift = upper.shiftOffset;
        }

        @Override
        protected int doEncode(int value) 
        {
            final int lower = (value & lowerBitMask) << lowerBitsShift;
            final int upper = (value & upperBitMask) << upperBitsShift;
            return upper | lower;
        }
        
        @Override
        public String getDescription() 
        {
            return "ShiftedSplit( "+getBitCount()+" bits total, lower_shift="+lowerBitsShift+", lower_mask="+toBinary( lowerBitMask)+
                    " , upper_shift="+upperBitsShift+",upper_mask="+toBinary( upperBitMask );
        }

        @Override
        public int decode(int value) 
        {
            final int shiftedValue = value >>> (4-getInstructionLengthInBytes())*8;
            final int hiValue = (shiftedValue & (upperBitMask << upperBitsShift)) >>> upperBitsShift;
            final int loValue = (shiftedValue & (lowerBitMask << lowerBitsShift)) >>> lowerBitsShift;
            return hiValue | loValue;
        }         
    }
    
    protected static final String toBinary(int i) {
        return "%"+StringUtils.leftPad( Integer.toBinaryString( i ) , 16 , '0' );
    }
    
    @Override
    public String toString() 
    {
        return "pattern='"+pattern+"',srcEnc="+srcEncoding.getDescription()+",dstEnc="+dstEncoding.getDescription();
    }
}