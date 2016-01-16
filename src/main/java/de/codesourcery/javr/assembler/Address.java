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

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;

public final class Address {

    private final Segment segment;
    private final int byteOffset;
    
    private Address(Segment s,int byteOffset) 
    {
        Validate.notNull(byteOffset, "byteOffset must not be NULL");
        if ( byteOffset < 0 ) {
            throw new IllegalArgumentException("Byte offset needs to be >= 0,was: "+byteOffset);
        }
        this.segment = s;
        this.byteOffset = byteOffset;
    }
    
    public int getByteAddress() {
        return byteOffset;
    }
    
    @Override
    public int hashCode() {
        return 31*(31 + byteOffset)+segment.hashCode();
    }

    public int getWordAddress() 
    {
        if ( ( byteOffset & 1) != 0 ) {
            throw new IllegalStateException("Refusing to convert odd byte-address "+this+" into word address");
        }
        return byteOffset >>> 1;
    }
    
    public Segment getSegment() {
        return segment;
    }
    
    public boolean hasSegment(Segment s) {
        return s.equals( this.segment );
    }
    
    @Override
    public String toString() {
        return "0x"+StringUtils.leftPad( Integer.toHexString( byteOffset ) , 4 , '0' )+" ("+segment+")";
    }
    
    public static Address byteAddress(Segment s,int byteOffset) 
    {
        return new Address(s,byteOffset);
    }
    
    public static Address wordAddress(Segment s,int byteOffset) 
    {
        if ( (byteOffset&1) != 0 ) {
            throw new IllegalArgumentException("Word-address must be even, was: "+byteOffset);
        }
        return new Address(s,byteOffset);
    }
}
