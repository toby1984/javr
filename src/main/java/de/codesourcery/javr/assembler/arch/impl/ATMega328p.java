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
