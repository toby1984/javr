package de.codesourcery.javr.assembler;

import java.io.IOException;

/**
 * Responsible for writing object code.
 *
 * <p>Implementations keep track of the memory segment for 
 * which data should currently be written.</p>
 * 
 * @author tobias.gierke@code-sourcery.de
 */
public interface IObjectCodeWriter 
{
    /**
     * Resets this writer to it's initial state.
     * 
     * Any data written already through this writer is discarded.
     *  
     * @throws IOException
     * @see #finish(boolean)
     */
    public void reset() throws IOException;
    
    /**
     * Returns the currently active segment.
     * 
     * Any data written by {@link #allocateBytes(int)} ,
     * {@link #writeByte(int)} or {@link #writeWord(int)}
     * will be written to this segment.     
     * @return
     */
    public Segment getCurrentSegment();
    
    /**
     * Sets the currently active segment.
     * 
     * Any data written by {@link #allocateBytes(int)} ,
     * {@link #writeByte(int)} or {@link #writeWord(int)}
     * will be written to this segment.
     * 
     * @param segment
     */
    public void setCurrentSegment(Segment segment);
    
    /**
     * Set the start address of the current segment.
     * 
     * This method may only be called once for each segment 
     * and must not be called after any data has been written to/allocated
     * in the current segment.
     * 
     * @param address
     */
    public void setStartAddress(Address address);
    
    /**
     * Allocates a certain number of uninitialized bytes.
     * 
     * @param num
     */
    public void allocateBytes(int num);
    
    /**
     * Write a single byte.
     * 
     * @param data
     */
    public void writeByte(int data);
    
    /**
     * Write a 16-bit word in MSB format.
     * 
     * @param data word in <b>big-endian</b> format
     */
    public void writeWord(int data);
    
    /**
     * Invoked when no more data will be written to this writer.
     * 
     * @param success <code>true</code> if the generated code is considered to be valid, <code>false</code>
     * if code generation got aborted for some reason
     * @throws IOException
     */
    public void finish(boolean success) throws IOException;
    
    /**
     * Returns the start address for a given segment.
     * 
     * @param segment
     * @return
     */
    public Address getStartAddress(Segment segment);
    
    /**
     * Returns the current offset of this writer in bytes.
     * 
     * @return
     */
    public int getCurrentByteAddress();
    
    /**
     * Returns the current offset of this writer.
     * 
     * @return
     */
    public Address getCurrentAddress();
}
