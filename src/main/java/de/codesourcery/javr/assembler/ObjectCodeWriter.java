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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import org.apache.commons.lang3.Validate;

/**
 * Default object code writer.
 *
 * @author tobias.gierke@code-sourcery.de
 */
public class ObjectCodeWriter implements IObjectCodeWriter 
{
    private Buffer codeBuffer;
    private Buffer sramBuffer;
    private Buffer eepromBuffer;
    
    private Buffer currentBuffer;
    
    protected static final class ByteArrayBuffer implements Buffer 
    {
        public final Segment segment;
        public Address startAddress;
        public byte[] data = new byte[1024];
        public int dataPtr = 0;
        
        public ByteArrayBuffer(Segment segment) 
        {
            Validate.notNull(segment,"segment must not be NULL");
            this.segment = segment;
        }
        
        @Override
        public boolean isEmpty() {
            return dataPtr == 0;
        }
        
        @Override
        public InputStream createInputStream() throws IOException 
        {
            return new ByteArrayInputStream( data , 0 , dataPtr );
        }
        
        public Segment getSegment() {
            return segment;
        }
        
        @Override
        public void writeByte(int byteToWrite) {
            if ( dataPtr+1 >= data.length ) {
                growDataArray();
            }
            data[dataPtr++] = (byte) byteToWrite;
        }
        
        private void growDataArray() 
        {
            byte[] tmp = new byte[ data.length*2 ];
            System.arraycopy( data, 0 , tmp , 0 , data.length);
            data = tmp;
        }        
        
        @Override
        public void setStartAddress(Address adr) 
        {
            Validate.notNull(adr, "address must not be NULL");
            if ( ! adr.getSegment().equals( this.segment ) ) {
                throw new IllegalArgumentException("Segment type mismatch: "+this.segment+" <-> "+adr.getSegment());
            }
            if ( startAddress != null && ! startAddress.equals( adr ) ) 
            {
                throw new IllegalStateException( segment+" start address already set");
            }
            if ( this.dataPtr != 0 ) {
                throw new IllegalStateException("Cannot change start address of segment "+segment+" after bytes have already been written/allocated to this segment");
            }
            this.startAddress = adr;   
        }
        
        @Override
        public Address getStartAddress() 
        {
            return startAddress == null ? Address.byteAddress( segment , 0 ) : startAddress;
        }

        @Override
        public void allocateBytes(int num) 
        {
            if ( num < 0 ) {
                throw new IllegalArgumentException("need a non-negative byte count,got : "+num);
            }
            if ( dataPtr+num >= data.length ) {
                growDataArray();
            }
            dataPtr += num;
        }

        @Override
        public void writeWord(int word) 
        {
            if ( dataPtr+2 >= this.data.length ) {
                growDataArray();
            }
            this.data[ dataPtr++ ] = (byte) word;
            this.data[ dataPtr++ ] = (byte) (word >> 8 );
        }

        @Override
        public int getCurrentByteAddress() 
        {
            if ( startAddress == null ) {
                return dataPtr;
            }
            return startAddress.getByteAddress()+dataPtr;
        }
    }
    
    public ObjectCodeWriter() {
        currentBuffer = codeBuffer;
    }
    
    @Override
    public void reset()
    {
        codeBuffer = createBuffer( Segment.FLASH );
        sramBuffer = createBuffer( Segment.SRAM );
        eepromBuffer = createBuffer( Segment.EEPROM);
        currentBuffer = codeBuffer;
    }
    
    protected Buffer createBuffer(Segment s) {
        return new ByteArrayBuffer(s);
    }
    
    public final Address getStartAddress(Segment segment) 
    {
        return getBuffer(segment).getStartAddress();
    }
    
    public Buffer getBuffer(Segment segment) 
    {
        Validate.notNull(segment, "segment must not be NULL");
        switch( segment ) 
        {
            case EEPROM:
                return eepromBuffer;
            case FLASH:
                return codeBuffer;
            case SRAM:
                return sramBuffer;
            default:
                throw new RuntimeException("Unhandled segment type: "+segment);
        }
    }

    @Override
    public final void setStartAddress(Address address) 
    {
        getBuffer(address.getSegment()).setStartAddress(address);
    }

    @Override
    public final void allocateBytes(int num)
    {
        currentBuffer.allocateBytes( num );
    }
    
    @Override
    public final void writeByte(int data) 
    {
        currentBuffer.writeByte(data);
    }

    @Override
    public void writeWord(int data) 
    {
        currentBuffer.writeWord( data );
    }
    
    @Override
    public void finish(ICompilationContext context,boolean success) throws IOException 
    {
    }

    @Override
    public int getCurrentByteAddress() 
    {
        return currentBuffer.getCurrentByteAddress();
    }
    
    @Override
    public Address getCurrentAddress() 
    {
        return Address.byteAddress( currentBuffer.getSegment() , currentBuffer.getCurrentByteAddress() );
    }

    @Override
    public void setCurrentSegment(Segment segment) 
    {
        currentBuffer = getBuffer(segment);
    }

    @Override
    public Segment getCurrentSegment() {
        return currentBuffer.getSegment();
    }
}