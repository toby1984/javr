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

import java.io.IOException;
import java.util.List;

import de.codesourcery.javr.assembler.elf.Relocation;

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
    public void finish(ICompilationContext context,boolean success) throws IOException;
    
    /**
     * Add relocation info to the current segment.
     * 
     * @param reloc
     */
    public void addRelocation(Relocation reloc);
    
    /**
     * Returns all relocation infos related to the current segment.
     * 
     * @return
     */
    public List<Relocation> getRelocations(Segment segment);
    
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
    
    /**
     * Returns the buffer for a given segment.
     * 
     * @param segment
     * @return
     */
    public Buffer getBuffer(Segment segment);
}
