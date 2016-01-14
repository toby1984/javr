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
package de.codesourcery.javr.assembler;

import static org.junit.Assert.*;

import org.junit.Test;

import de.codesourcery.javr.assembler.exceptions.ParseException;
import de.codesourcery.javr.assembler.parser.Scanner;
import de.codesourcery.javr.assembler.util.StringResource;

public class ScannerTest
{

    private Scanner s;

    @Test(expected=NullPointerException.class)
    public void testNullIsRejected() {
        new Scanner( null );
    }

    @Test
    public void testEmptyStringWorks()
    {
        this.s = new Scanner( new StringResource("dummy","") , 3 );
        assertEOF();
    }

    @Test
    public void testPeekAndNext()
    {
        this.s = new Scanner( new StringResource("dummy","abc") , 3 );
        assertFalse( this.s.eof() );
        assertEquals( 'a' , this.s.peek() );
        assertEquals( 'a' , this.s.next() );
        assertEquals( 'b' , this.s.peek() );
        assertEquals( 'b' , this.s.next() );
        assertEquals( 'c' , this.s.peek() );
        assertEquals( 'c' , this.s.next() );
        assertEOF();
    }

    @Test
    public void testPushback()
    {
        this.s = new Scanner( new StringResource("dummy","a" ) , 3 );
        assertFalse( this.s.eof() );
        assertEquals( 'a' , this.s.next() );
        assertEquals( 1 , this.s.offset() );
        assertEOF();
        this.s.pushBack();
        assertFalse(this.s.eof() );
        assertEquals( 0 , this.s.offset() );
        assertEquals( 'a' , this.s.peek() );
    }
    
    @Test
    public void testReadSmallBuffer()
    {
        this.s = new Scanner( new StringResource("dummy","abc" ) , 3 );
        assertFalse( this.s.eof() );
        assertEquals( 'a' , this.s.next() );
        assertEquals( 1 , this.s.offset() );
        assertEquals( 'b' , this.s.next() );
        assertEquals( 2 , this.s.offset() );
        assertEquals( 'c' , this.s.next() );
        assertEquals( 3 , this.s.offset() );        
        assertEOF();
    }    
    
    @Test
    public void testSetOffsetWithBackTrack()
    {
        this.s = new Scanner( new StringResource("dummy","0123456789abcdefgh" ) , 4 );
        assertFalse( this.s.eof() );
        assertEquals( '0' , this.s.next() );
        assertEquals( 1 , this.s.offset() );
        assertEquals( '1' , this.s.next() );
        assertEquals( 2 , this.s.offset() );
        assertEquals( '2' , this.s.next() );
        assertEquals( 3 , this.s.offset() );    
        assertEquals( '3' , this.s.next() );
        assertEquals( 4 , this.s.offset() );
        assertEquals( '4' , this.s.next() );
        assertEquals( 5 , this.s.offset() );
        
        s.setOffset( 3 );
        
        assertEquals( 3 , this.s.offset() );
        assertEquals( '3' , this.s.next() );
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

    private void assertEOF()
    {
        assertTrue( this.s.eof() );
        try {
            this.s.peek();
            fail("Should've failed");
        } catch(final ParseException e) {
            // ok
        }
        try {
            this.s.next();
            fail("Should've failed");
        } catch(final ParseException e) {
            // ok
        }
    }

}
