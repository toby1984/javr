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
package de.codesourcery.javr.assembler;

import static org.junit.Assert.*;

import org.apache.commons.lang3.StringUtils;
import org.junit.Assert;
import org.junit.Test;

import de.codesourcery.javr.assembler.exceptions.ParseException;
import de.codesourcery.javr.assembler.parser.Scanner;
import de.codesourcery.javr.assembler.util.StringResource;

import java.util.function.Function;

public class ScannerTest
{
    private Scanner scanner;

    @Test(expected=NullPointerException.class)
    public void testNullIsRejected() {
        new Scanner( null );
    }

    @Test
    public void testRemoveCarriageReturn1()
    {
        this.scanner = new Scanner( new StringResource( "dummy", "\rx\r\nx\r\r\n\r") , 3 );
        // fetchSize == ( buffer size / 2 ) = 1
        Assert.assertEquals( "x\nx\n", readString() );
    }

    @Test
    public void testRemoveCarriageReturn2()
    {
        this.scanner = new Scanner( new StringResource( "dummy", "\rx\r\nx\r\r\n\r") , 4 );
        // fetchSize == ( buffer size / 2 ) = 2
        assertEquals( "x\nx\n".toCharArray(), readString().toCharArray() );
    }

    @Test
    public void testEmptyStringWorks()
    {
        this.scanner = new Scanner( new StringResource( "dummy", "") , 3 );
        assertEOF();
    }

    private String readString()
    {
        final StringBuilder buffer = new StringBuilder();
        while ( ! scanner.eof() ) {
            buffer.append( scanner.next() );
        }
        return buffer.toString();
    }

    @Test
    public void testPeekAndNext()
    {
        this.scanner = new Scanner( new StringResource( "dummy", "abc") , 3 );
        assertFalse( this.scanner.eof() );
        assertEquals( 'a' , this.scanner.peek() );
        assertEquals( 'a' , this.scanner.next() );
        assertEquals( 'b' , this.scanner.peek() );
        assertEquals( 'b' , this.scanner.next() );
        assertEquals( 'c' , this.scanner.peek() );
        assertEquals( 'c' , this.scanner.next() );
        assertEOF();
    }

    @Test
    public void testPushback()
    {
        this.scanner = new Scanner( new StringResource( "dummy", "a" ) , 3 );
        assertFalse( this.scanner.eof() );
        assertEquals( 'a' , this.scanner.next() );
        assertEquals( 1 , this.scanner.offset() );
        assertEOF();
        this.scanner.pushBack();
        assertFalse(this.scanner.eof() );
        assertEquals( 0 , this.scanner.offset() );
        assertEquals( 'a' , this.scanner.peek() );
    }
    
    @Test
    public void testReadSmallBuffer()
    {
        this.scanner = new Scanner( new StringResource( "dummy", "abc" ) , 3 );
        assertFalse( this.scanner.eof() );
        assertEquals( 'a' , this.scanner.next() );
        assertEquals( 1 , this.scanner.offset() );
        assertEquals( 'b' , this.scanner.next() );
        assertEquals( 2 , this.scanner.offset() );
        assertEquals( 'c' , this.scanner.next() );
        assertEquals( 3 , this.scanner.offset() );
        assertEOF();
    }    
    
    @Test
    public void testSetOffsetWithBackTrack()
    {
        this.scanner = new Scanner( new StringResource( "dummy", "0123456789abcdefgh" ) , 4 );
        assertFalse( this.scanner.eof() );
        assertEquals( '0' , this.scanner.next() );
        assertEquals( 1 , this.scanner.offset() );
        assertEquals( '1' , this.scanner.next() );
        assertEquals( 2 , this.scanner.offset() );
        assertEquals( '2' , this.scanner.next() );
        assertEquals( 3 , this.scanner.offset() );
        assertEquals( '3' , this.scanner.next() );
        assertEquals( 4 , this.scanner.offset() );
        assertEquals( '4' , this.scanner.next() );
        assertEquals( 5 , this.scanner.offset() );
        
        scanner.setOffset( 3 );
        
        assertEquals( 3 , this.scanner.offset() );
        assertEquals( '3' , this.scanner.next() );
    } 
    
    @Test
    public void testBackTrackingToMuchFails()
    {
    	// buffer size is 2
    	// backtracking is possible -2 bytes (bufferSize/2) past start of current region
        this.scanner = new Scanner( new StringResource( "dummy", "0123456" ) , 4 );
        assertFalse( this.scanner.eof() );
        assertEquals( '0' , this.scanner.next() );
        assertEquals( 1 , this.scanner.offset() );
        assertEquals( '1' , this.scanner.next() );
        assertEquals( 2 , this.scanner.offset() );
        assertEquals( '2' , this.scanner.next() );
        assertEquals( 3 , this.scanner.offset() );
        assertEquals( '3' , this.scanner.next() );
        assertEquals( 4 , this.scanner.offset() );
        
        assertEquals( '4' , this.scanner.next() );
        assertEquals( 5 , this.scanner.offset() );
        
        assertEquals( '5' , this.scanner.next() );
        assertEquals( 6 , this.scanner.offset() );
        
        scanner.setOffset( 2 );
        assertEquals( '2' , this.scanner.peek() );

        try {
        	scanner.setOffset( 1 );
        	fail("Should've failed");
        } catch(IllegalStateException e) {
        	// ok
        }
        
        scanner.setOffset( 5 );
        assertEquals( '5' , this.scanner.peek() );
        
        try {
        	scanner.setOffset( 7 );
        	fail("Should've failed");
        } catch(IllegalStateException e) {
        	// ok
        }      
        assertEquals( '5' , this.scanner.next() );
        assertEquals( '6' , this.scanner.next() );
        assertEOF();
    }    
    
    private void assertEquals(int expected,int actual) {
        if ( expected != actual ) {
            fail("Expected "+expected+" but got "+actual);
        }
    }    
    
    private void assertEquals(char expected,char actual) {
        if ( expected != actual ) {
            fail("Expected '"+expected+"' but got '"+actual+"'");
        }
    }

    private void assertEquals(char[] expected,char[] actual)
    {
        if ( expected == null || actual == null && expected != actual ) {
            fail("Expected: "+expected+" , actual: "+actual);
        }
        boolean doMatch = expected.length == actual.length;

        final int len = Math.max(expected.length, actual.length );
        final int padLen = Integer.toString(len).length();
        for ( int i = 0  ; i < len ; i++ )
        {
            final String idx = StringUtils.leftPad( Integer.toString(i), padLen );

            if ( i < expected.length )
            {
                if ( i < actual.length )
                {
                    boolean matching = expected[i] == actual[i];
                    if ( matching ) {
                        System.out.println( "["+idx+"] expected: "+toString(expected[i]) +" <-> "+toString(actual[i]));
                    } else {
                        System.out.println( "["+idx+"] expected: "+toString(expected[i]) +" <-> "+toString(actual[i])+" MISMATCH");
                        doMatch = false;
                    }
                } else {
                    // i < expected.length
                    // i > actual.length
                    System.out.println( "["+idx+"] expected: "+toString(expected[i]) +" <-> n/a MISMATCH");
                    doMatch = false;
                }
            } else {
                // i > expected.length
                // i < actual.length
                System.out.println( "["+idx+"] expected: n/a <-> "+toString(actual[i])+" MISMATCH");
                doMatch = false;
            }
        }
        if ( ! doMatch ) {
            fail("Arrays do not match.");
        }
    }

    private String toString(char c) {

        if ( c == '\r' ) {
            return "<CR>";
        }
        if ( c == '\n' ) {
            return "<NL>";
        }
        if ( c == '\t' ) {
            return "<TAB>";
        }
        if ( c == ' ' ) {
            return "<SP>";
        }
        return Character.toString(c);
    }

    private void assertEOF()
    {
        assertTrue( this.scanner.eof() );
        try {
            this.scanner.peek();
            fail("Should've failed");
        } catch(final ParseException e) {
            // ok
        }
        try {
            this.scanner.next();
            fail("Should've failed");
        } catch(final ParseException e) {
            // ok
        }
    }

}
