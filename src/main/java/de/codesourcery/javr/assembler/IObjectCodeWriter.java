package de.codesourcery.javr.assembler;

import java.io.IOException;

public interface IObjectCodeWriter 
{
    public void reset();
    
    public Segment getCurrentSegment();
    
    public void setCurrentSegment(Segment segment);
    
    public void setStartAddress(Address address);
    
    public void allocateBytes(int num);
    
    public void writeByte(int data);
    
    public void writeWord(int data);
    
    public void finish(boolean success) throws IOException;
    
    public Address getStartAddress(Segment segment);
    
    public int getCurrentByteAddress();
    
    public Address getCurrentAddress();
}
