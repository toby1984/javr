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
package de.codesourcery.javr.assembler.arch.impl;

import org.apache.commons.lang3.Validate;

import de.codesourcery.javr.assembler.Segment;
import de.codesourcery.javr.assembler.arch.Architecture;

public class ATMega328p extends ATMega88 {

    @Override
    public int getSegmentSize(Segment seg) 
    {
        Validate.notNull(seg, "segment must not be NULL");
        switch(seg) {
            case EEPROM: return 1024;
            case FLASH: return 32*1024;
            case SRAM: return 2048;
            default:
                throw new RuntimeException("Unhandled segment type: "+seg);
        }
    }
    
    @Override
    public int getSRAMStartAddress() 
    {
        return 0x100;
    }    
    
    public Architecture getType() {
        return Architecture.ATMEGA328P;
    }
}
