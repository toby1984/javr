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
        
        while ( ( currentOffset % 2 ) != 0 ) {
            pad();
        }
        
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
    
    public void pad() 
    {
        writeByte(0);
    }
    
    public int currentOffset() {
        return currentOffset;
    }
    
    public void writeWord(int value,Endianess endian) {
        
        while ( ( currentOffset % 4 ) != 0 ) {
            pad();
        }
        
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
