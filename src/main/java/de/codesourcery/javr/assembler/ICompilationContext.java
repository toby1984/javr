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
import de.codesourcery.javr.assembler.parser.ast.ASTNode;
import de.codesourcery.javr.assembler.symbols.SymbolTable;

public interface ICompilationContext 
{
    public SymbolTable globalSymbolTable();
    
    public SymbolTable currentSymbolTable();
    
    public CompilationUnit currentCompilationUnit();
    
    public int currentOffset();
    
    public Address currentAddress();
    
    public Segment currentSegment();
    
    public void setSegment(Segment s);
    
    public void writeByte(int value);
    
    public void writeWord(int value);
    
    public void allocateByte();
    
    public void allocateWord();
    
    public void allocateBytes(int numberOfBytes);    
    
    public void error(String message,ASTNode node);
    
    public void message(CompilationMessage msg);
    
    public IArchitecture getArchitecture();
    
    public int getBytesRemainingInCurrentSegment();
}
