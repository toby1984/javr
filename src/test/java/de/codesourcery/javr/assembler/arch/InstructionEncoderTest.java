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

import org.apache.commons.lang3.StringUtils;
import org.junit.Assert;
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
    public void testPatternWithRandomArbitraryCharacterWorks() 
    {
        enc = new InstructionEncoder("1100_0100_kkkk_0000");
        Assert.assertEquals(1,  enc.getArgumentCount() );
        final int value = enc.encode( 0b1111 , 0 );
        assertBinary( 0b1100_0100_1111_0000 , value );
    } 
    
    @Test
    public void testADD() 
    {
        enc = new InstructionEncoder("000011sdddddssss");
        Assert.assertEquals(2,  enc.getArgumentCount() );
        final int value = enc.encode( 1 , 2 );
        assertBinary( 0b0000_1100_0001_0010 , value );
    }     
    
    @Test
    public void testEncodeNoArgumentsPattern() 
    {
        enc = new InstructionEncoder("1100_0100_1010_0000");
        final int value = enc.encode( -1 , -1 );
        assertBinary( 0b1100_0100_1010_0000 , value );
        Assert.assertEquals(0, enc.getArgumentCount() );
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
        Assert.assertEquals(1, enc.getArgumentCount() );
    }     
    
    @Test
    public void testEncodeOneArgumentShiftedSplitMSBPattern2() 
    {
        enc = new InstructionEncoder("1001 011K K0dd KKKK");
        final int value = enc.encode( 0b01 , 0b111010);
        assertBinary( 0b1001_0111_1001_1010 , value );
        Assert.assertEquals(2, enc.getArgumentCount() );
    }     
    
    @Test
    public void testEncodeTwoArgumentsPattern1() 
    {
        enc = new InstructionEncoder("1100_010d_ddd0_1sss");
        final int value = enc.encode( 15 , 7);
                     // 1100_010d_ddd0_1010
        assertBinary( 0b1100_0101_1110_1111 , value );
        Assert.assertEquals( 2 , enc.getArgumentCount() );
    }     
    
    // helpers
    private void assertBinary(int expected,int actual) 
    {
        final String exp = "0b"+StringUtils.leftPad( Integer.toBinaryString( expected ) , 16 , '0' ); 
        final String act = "0b"+StringUtils.leftPad( Integer.toBinaryString( actual ) , 16 , '0' ); 
        Assert.assertEquals( "Expected \n"+exp+" but got \n"+act, expected , actual );
    }
    
}