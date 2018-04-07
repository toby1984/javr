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
