package de.codesourcery.javr.assembler.elf;

import java.util.ArrayList;
import java.util.List;

public class StringTable {

    private final List<String> strings = new ArrayList<>();
    
    public StringTable() {
        strings.add(""); // every string table needs to hold at least the NULL string.
    }
    
    public int add(String s) 
    {
        int sizeInBytes = 0;
        for ( String existing : strings) 
        {
            sizeInBytes += (1+existing.length());
        }
        strings.add(s);
        return sizeInBytes;
    }
    
    public void write( ElfWriter writer) 
    {
        for ( String s : strings ) 
        {
            for ( char c : s.toCharArray() ) 
            {
                writer.writeByte( c );
            }
            writer.writeByte(0); // terminate string
        }
    }
    
    public String getStringAtByteOffset(int offset) 
    {
        int sizeInBytes = 0;
        for ( String existing : strings) 
        {
            if ( offset == sizeInBytes ) {
                return existing;
            }
            sizeInBytes += (1+existing.length());
        }        
        throw new RuntimeException("Invalid byte offset "+offset);
    }
}
