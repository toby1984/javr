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

import de.codesourcery.javr.assembler.arch.IArchitecture;
import de.codesourcery.javr.assembler.parser.Parser.CompilationMessage;
import de.codesourcery.javr.assembler.symbols.SymbolTable;

public interface ICompilationContext 
{
    public static enum Phase 
    {
        VALIDATE1,
        GATHER_SYMBOLS,
        RESOLVE_SYMBOLS,
        VALIDATE2,
        GENERATE_CODE;
    }
    
    public SymbolTable getSymbolTable();
    
    public int currentOffset();
    
    public Address currentAddress();
    
    public Segment currentSegment();
    
    public Phase currentPhase();
    
    public boolean isInPhase(Phase p);    
    
    public void setSegment(Segment s);
    
    public void writeByte(int value);
    
    public void writeWord(int value);
    
    public void writeAsBytes(int value,int numberOfBytes);    
    
    public void allocateByte();
    
    public void allocateWord();
    
    public void allocateBytes(int numberOfBytes);    
    
    public void message(CompilationMessage msg);
    
    public IArchitecture getArchitecture();
}
