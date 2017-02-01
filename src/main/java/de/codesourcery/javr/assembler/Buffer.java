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
import java.io.InputStream;

/**
 * A buffer for a specific {@link Segment} used by the {@link IObjectCodeWriter}.
 * 
 * @author tobias.gierke@code-sourcery.de
 */
public interface Buffer 
{
    public boolean isEmpty();
    
    public boolean isNotEmpty();
    
    public Segment getSegment();

    public void writeByte(int byteToWrite);

    public void setStartAddress(Address adr);

    public Address getStartAddress();

    public void allocateBytes(int num);

    public void writeWord(int word);

    public int getCurrentByteAddress();
    
    public InputStream createInputStream() throws IOException;
    
    public byte[] toByteArray() throws IOException;
}