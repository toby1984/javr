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
package de.codesourcery.javr.assembler.arch;

import static org.junit.Assert.*;

import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.junit.Before;
import org.junit.Test;

public class InstructionEncoderTest 
{
    private InstructionEncoder enc;
    
    @Before
    public void setup() {
        enc=null;
    }
    
    @Test(expected=IllegalArgumentException.class)
    public void testInvalidPatternLength() 
    {
        enc = new InstructionEncoder("" );
    }
    
    @Test(expected=NullPointerException.class)
    public void testInvalidPatternLength2() 
    {
        enc = new InstructionEncoder(null);
    }   
    
    @Test(expected=IllegalArgumentException.class)
    public void testInvalidPatternLength3() 
    {
        enc = new InstructionEncoder("0101_0ss01d");
    }    
    
    @Test(expected=IllegalArgumentException.class)
    public void testInvalidPatternLength4() 
    {
        enc = new InstructionEncoder("01010");
    }
    
    @Test(expected=IllegalArgumentException.class)
    public void testInvalidPatternLength5() 
    {
        enc = new InstructionEncoder("010101010101010");
    }    
    
    @Test
    public void testEncodeDecode1() {
        
        enc = new InstructionEncoder("101010101010dddd");
        int value = enc.encode( 0b1111 , 0 );
        assertBinary( 0b1010101010101111 , value );
        value <<= (enc.getInstructionLengthInBytes()*8);
        final List<Integer> decoded = enc.decode( value );
        assertEquals(2,decoded.size());
        assertEquals( 0b1111 , decoded.get(0).intValue() );
        assertNull(decoded.get(1) );
    }
    
    @Test
    public void testEncodeDecode2() {
        
        enc = new InstructionEncoder("1010101010dddd00");
        int value = enc.encode( 0b1111 , 0 );
        assertBinary( 0b1010101010111100 , value );
        value <<= (enc.getInstructionLengthInBytes()*8);
        final List<Integer> decoded = enc.decode( value );
        assertEquals(2,decoded.size());
        assertEquals( 0b1111 , decoded.get(0).intValue() );
        assertNull(decoded.get(1) );
    }    
    
    @Test
    public void testEncodeDecode3() {
        
        enc = new InstructionEncoder("1010101010ddddss");
        int value = enc.encode( 0b1111 , 0b01 );
        assertBinary( 0b1010101010111101 , value );
        value <<= (enc.getInstructionLengthInBytes()*8);
        final List<Integer> decoded = enc.decode( value );
        assertEquals(2,decoded.size());
        assertEquals( 0b1111 , decoded.get(0).intValue() );
        assertEquals( 0b01 , decoded.get(1).intValue() );
    }     
    
    @Test
    public void testEncodeDecode27() {
        
        enc = new InstructionEncoder("1111 01kk kkkk k001");
        int value = enc.encode( -3 , 0 );
        assertBinary( 0b1111_0111_1110_1001 , value & 0xffff );
        value <<= (enc.getInstructionLengthInBytes()*8);
        final List<Integer> decoded = enc.decode( value );
        assertEquals(2,decoded.size());
        assertEquals( 125 , decoded.get(0).intValue() );
        assertNull(decoded.get(1) );
    }    
    
    @Test
    public void testEncodeDecode4() {
        
        enc = new InstructionEncoder("101010101d101ddd");
        int value = enc.encode( 0b1111 , 0 );
        assertBinary( 0b1010101011101111 , value );
        value <<= (enc.getInstructionLengthInBytes()*8);
        final List<Integer> decoded = enc.decode( value );
        assertEquals(2,decoded.size());
        assertEquals( 0b1111 , decoded.get(0).intValue() );
        assertNull(decoded.get(1) );
    }    
    
    @Test
    public void testEncodeDecode5() {
        
        enc = new InstructionEncoder("0101010dd001dd10");
        int value = enc.encode( 0b1111 , 0 );
        assertBinary( 0b0101010110011110 , value );
        value <<= (enc.getInstructionLengthInBytes()*8);
        final List<Integer> decoded = enc.decode( value );
        assertEquals(2,decoded.size());
        assertEquals( 0b1111 , decoded.get(0).intValue() );
        assertNull(decoded.get(1) );
    }      
    
    @Test
    public void testMatching() {
        enc = new InstructionEncoder("0101010dd001dd10");
        assertTrue( enc.matches( 0b0101_0100_0001_0010 << 16) );
        assertTrue( enc.matches( 0b0101_0101_1001_1110 << 16) );
        assertTrue( enc.matches( 0b0101_0100_1001_0110 << 16) );
        assertTrue( enc.matches( 0b0101_0101_0001_1010 << 16) );
    }
    
    @Test
    public void testEncodeDecode6() {
        
        enc = new InstructionEncoder("00ss010dd001dd1s");
        int value = enc.encode( 0b1111 , 0b111 );
        assertBinary( 0b0011010110011111 , value );
        value <<= (enc.getInstructionLengthInBytes()*8);
        final List<Integer> decoded = enc.decode( value );
        assertEquals(2,decoded.size());
        assertEquals( 0b1111 , decoded.get(0).intValue() );
        assertEquals( 0b111 , decoded.get(1).intValue() );
    }     
    
    @Test
    public void testEncodeDecode8() {
        
        enc = new InstructionEncoder("01d101d101d101d1");
        int value = enc.encode( 0b1111 , 0 );
        assertBinary( 0b0111011101110111 , value );
        value <<= (enc.getInstructionLengthInBytes()*8);
        final List<Integer> decoded = enc.decode( value );
        assertEquals(2,decoded.size());
        assertEquals( 0b1111 , decoded.get(0).intValue() );
        assertNull( decoded.get(1) );
    } 
    
    @Test
    public void testPatternWithRandomArbitraryCharacterWorks() 
    {
        enc = new InstructionEncoder("1100_0100_kkkk_0000");
        assertEquals(1,  enc.getArgumentCount() );
        final int value = enc.encode( 0b1111 , 0 );
        assertBinary( 0b1100_0100_1111_0000 , value );
    } 
    
    @Test
    public void testFreeFormMapping() {
        enc = new InstructionEncoder("10s0 ss0d dddd 1sss");
        assertEquals(2,  enc.getArgumentCount() );
        final int value = enc.encode( 0b1010 , 0b111001 );
        assertBinary( 0b1010_1100_1010_1001 , value );
    }
    @Test
    public void testADD() 
    {
        enc = new InstructionEncoder("000011sdddddssss");
        assertEquals(2,  enc.getArgumentCount() );
        final int value = enc.encode( 1 , 2 );
        assertBinary( 0b0000_1100_0001_0010 , value );
    }     
    
    @Test
    public void testEncodeNoArgumentsPattern() 
    {
        enc = new InstructionEncoder("1100_0100_1010_0000");
        final int value = enc.encode( -1 , -1 );
        assertBinary( 0b1100_0100_1010_0000 , value );
        assertEquals(0, enc.getArgumentCount() );
    }     
    
    @Test
    public void testEncodeOneArgumentNotShiftedPattern() 
    {
        enc = new InstructionEncoder("1100_0100_1010_dddd");
        final int value = enc.encode( 15 , -1 );
        assertBinary( 0b1100_0100_1010_1111 , value );
    }    
    
    @Test
    public void testEncodeOneArgumentShiftedPattern() 
    {
        enc = new InstructionEncoder("1100_010d_ddd0_1010");
        final int value = enc.encode( 15 , -1 );
                     // 1100_010d_ddd0_1010
        assertBinary( 0b1100_0101_1110_1010 , value );
    }  
    
    @Test
    public void testEncodeOneArgumentShiftedSplitMSBPattern() 
    {
        enc = new InstructionEncoder("1100_d100_0ddd_1010");
        final int value = enc.encode( 15 , -1 );
        assertBinary( 0b1100_1100_0111_1010 , value );
        assertEquals(1, enc.getArgumentCount() );
    }     
    
    @Test
    public void testEncodeOneArgumentShiftedSplitMSBPattern2() 
    {
        enc = new InstructionEncoder("1001 011K K0dd KKKK");
        final int value = enc.encode( 0b01 , 0b111010);
        assertBinary( 0b1001_0111_1001_1010 , value );
        assertEquals(2, enc.getArgumentCount() );
    }     
    
    @Test
    public void testEncodeTwoArgumentsPattern1() 
    {
        enc = new InstructionEncoder("1100_010d_ddd0_1sss");
        final int value = enc.encode( 15 , 7);
                     // 1100_010d_ddd0_1010
        assertBinary( 0b1100_0101_1110_1111 , value );
        assertEquals( 2 , enc.getArgumentCount() );
    }     
    
    // helpers
    private void assertBinary(int expected,int actual) 
    {
        final String exp = "0b"+StringUtils.leftPad( Integer.toBinaryString( expected ) , 16 , '0' ); 
        final String act = "0b"+StringUtils.leftPad( Integer.toBinaryString( actual ) , 16 , '0' ); 
        assertEquals( "Expected \n"+exp+" (0x"+Integer.toHexString(expected)+") but got \n"+act+" (0x"+Integer.toHexString(actual)+")", expected , actual );
    }
    
}