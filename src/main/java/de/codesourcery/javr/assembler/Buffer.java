package de.codesourcery.javr.assembler;

import java.io.IOException;
import java.io.InputStream;

public interface Buffer 
{
    public boolean isEmpty();
    
    public Segment getSegment();

    public void writeByte(int byteToWrite);

    public void setStartAddress(Address adr);

    public Address getStartAddress();

    public void allocateBytes(int num);

    public void writeWord(int word);

    public int getCurrentByteAddress();
    
    public InputStream createInputStream() throws IOException;
}