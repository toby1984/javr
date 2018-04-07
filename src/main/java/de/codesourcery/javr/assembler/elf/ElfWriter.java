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
package de.codesourcery.javr.assembler.elf;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.atomic.AtomicLong;

public class ElfWriter 
{
    public static final AtomicLong TMP_MARKER_ID = new AtomicLong(0);
    
    private static final boolean DEBUG_PADDING = false;
    
    private byte[] data = new byte[1024];
    private int currentOffset;

    private final Map<String,Marker> markers = new HashMap<>();
    
    private final List<IDeferredWrite> deferredWrites = new ArrayList<>();
    
    public interface IDeferredWrite 
    {
        public void perform(ElfWriter writer,ElfFile file);
    }
    
    public interface IValueSupplier 
    {
        public int getValue(ElfWriter writer, ElfFile file);
    }
    
    public final class Marker 
    {
        public final String name;
        public final int offset;
        
        public Marker(String name) 
        {
            this.name = name;
            this.offset = currentOffset;
        }
        
        public void writeByte(int value) 
        {
            final int tmp = currentOffset;
            try {
                currentOffset = offset;
                ElfWriter.this.writeByte(value);
            } finally {
                currentOffset = tmp;
            }
        }
        
        public void writeHalf(int value,Endianess endian) 
        {
            final int tmp = currentOffset;
            try {
                currentOffset = offset;
                ElfWriter.this.writeHalf(value,endian);
            } finally {
                currentOffset = tmp;
            }
        }   
        
        public void writeWord(int value,Endianess endian) 
        {
            final int tmp = currentOffset;
            try {
                currentOffset = offset;
                ElfWriter.this.writeWord(value,endian);
            } finally {
                currentOffset = tmp;
            }
        }         
    }
    
    public static enum Endianess 
    {
        BIG,LITTLE;
    }
    
    public final ElfFile file;
    
    public ElfWriter(ElfFile file) {
        this.file = file;
        
    }
    public Marker createTempMarker()
    {
        return createMarker( "tmp."+TMP_MARKER_ID.incrementAndGet());
    }
    
    public Marker createMarker(ElfFile.MarkerName marker) 
    {
        return createMarker( marker.name );
    }
    
    public Marker createMarker(String name) 
    {
        if ( markers.containsKey( name ) ) {
            throw new IllegalArgumentException("Duplicate marker '"+name+"'");
        }
        final Marker result = new Marker(name);
        markers.put( name , result );
        return result;
    }
    
    public void execDeferredWrites() 
    {
        for ( IDeferredWrite write : deferredWrites ) 
        {
            write.perform( this , file );
        }
    }
    
    public Marker getMarker(ElfFile.MarkerName marker)
    {
        return getMarker( marker.name );
    }
    
    public Marker getMarker(String name) 
    {
        final Marker result = markers.get(name);
        if ( result == null ) {
            throw new NoSuchElementException("Unknown marker '"+name+"'");
        }
        return result;
    }
    
    public void writeBytes(char data )
    {
        writeByte( data & 0xff );
    }    
    
    public void writeBytes(byte[] data )
    {
        writeBytes(data,0,data.length);
    }
    
    public void writeBytes(byte[] data ,int offset , int len)
    {
        for ( int i = 0 ; i < len ; i++ ) 
        {
            writeByte( data[offset+i] & 0xff );
        }
    }    
    
    public void deferredWriteByte(String markerName) 
    {
        deferredWriteByte( (writer,file) -> getMarker( markerName ).offset );
    }
    
    public void deferredWriteByte(IValueSupplier s) 
    {
        final Marker tmp = createTempMarker();
        writeByte(0);  // placeholder value        
        deferredWrites.add( (writer, file) -> tmp.writeByte( s.getValue( writer , file ) ) );
    }
    
    public void deferredWriteHalf(String markerName,Endianess endian) 
    {
        deferredWriteHalf( (writer,file) -> getMarker( markerName ).offset , endian );
    }    
    
    public void deferredWriteHalf(IValueSupplier s,Endianess endian) 
    {
        final Marker tmp = createTempMarker();        
        writeHalf(0,endian);  // placeholder value        
        deferredWrites.add( (writer, file) -> tmp.writeHalf( s.getValue( writer , file ) , endian) );
    }  
    
    public void deferredWriteWord(ElfFile.MarkerName markerName,Endianess endian) 
    {
        deferredWriteWord( markerName.name , endian );
    }      
    
    public void deferredWriteWord(String markerName,Endianess endian) 
    {
        deferredWriteWord( (writer,file) -> getMarker( markerName ).offset , endian );
    }    
    
    public void deferredWriteWord(IValueSupplier s,Endianess endian) 
    {
        final Marker tmp = createTempMarker();        
        writeWord(0,endian); // placeholder value        
        deferredWrites.add( (writer, file) -> tmp.writeWord( s.getValue( writer , file ), endian ) );
    }      
    
    public void writeByte(int value)
    {
        if ( currentOffset == data.length ) 
        {
            final byte[] tmp = new byte[ data.length + data.length/2 ];
            System.arraycopy( data , 0 , tmp , 0 , data.length );
            data = tmp;
        }
//        System.out.println("writeByte( "+value+")");
        data[currentOffset++] = (byte) value;
    }
    
    public void writeHalf(int value,Endianess endian) {
        
        align(2);
        
        switch( endian ) 
        {
            case BIG:
                writeByte( (value & 0xff00) >>> 8 );
                writeByte( (value & 0x00ff) );
                return;
            case LITTLE:
                writeByte( (value & 0x00ff) );
                writeByte( (value & 0xff00) >>> 8 );
                return;
            default:
                throw new IllegalArgumentException("Endian: "+endian);
        }
    }
    
    public void pad(int count)
    {
        for ( int i = 0 ; i < count ; i++) {
            pad();
        }
    }
    
    public void align(int alignment) 
    {
        while ( ( currentOffset % alignment ) != 0 ) 
        {
            pad();
        }
    }
    
    public void pad() 
    {
        if ( DEBUG_PADDING ) {
            System.out.println("** PAD 1 byte (offset before: "+currentOffset+" **");
        }
        writeByte(0);
    }
    
    public int currentOffset() {
        return currentOffset;
    }
    
    public void writeWord(int value,Endianess endian) {
        
        align(4);
        
        switch( endian ) 
        {
            case BIG:
                writeByte( (value & 0xff000000) >>> 24 );
                writeByte( (value & 0x00ff0000) >>> 16 );
                writeByte( (value & 0x0000ff00) >>> 8 );
                writeByte( (value & 0x000000ff) );
                return;
            case LITTLE:
                writeByte( (value & 0x000000ff) );
                writeByte( (value & 0x0000ff00) >>> 8 );
                writeByte( (value & 0x00ff0000) >>> 16 );
                writeByte( (value & 0xff000000) >>> 24 );
                return;
            default:
                throw new IllegalArgumentException("Endian: "+endian);
        }
    }    
    
    public byte[] getBytes() 
    {
        byte[] result = new byte[ currentOffset ];
        System.arraycopy( this.data , 0 , result , 0 , currentOffset );
        return result;
    }
}
