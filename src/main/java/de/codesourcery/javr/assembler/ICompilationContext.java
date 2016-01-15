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
import de.codesourcery.javr.assembler.util.Resource;

/**
 * Provides access to the state of the current compilation process.
 * 
 * This interface is used by all compilation phases to perform the
 * actual compilation process.
 * 
 * @author tobias.gierke@code-sourcery.de
 */
public interface ICompilationContext
{
    public interface ICompilationSettings 
    {
        public boolean isFailOnAddressOutOfRange();
        
        public ICompilationSettings setFailOnAddressOutOfRange(boolean failOnAddressOutOfRange);
    }
    
    // symbol tables
    public SymbolTable globalSymbolTable();
    
    public SymbolTable currentSymbolTable();
    
    // code generation
    public int currentOffset();
    
    public Address currentAddress();
    
    public Segment currentSegment();
    
    public void setSegment(Segment s);
    
    public void writeByte(int value);
    
    public void writeWord(int value);
    
    public void allocateByte();
    
    public void allocateWord();
    
    public void allocateBytes(int numberOfBytes);    
    
    // error handling
    public void error(String message,ASTNode node);
    
    public void message(CompilationMessage msg);
    
    // #include handling
    public void pushCompilationUnit(CompilationUnit unit);
    
    public void popCompilationUnit();
    
    public CompilationUnit getOrCreateCompilationUnit(Resource res);
    
    // misc
    public ResourceFactory getResourceFactory();
    
    public ICompilationSettings getCompilationSettings();
    
    public CompilationUnit currentCompilationUnit();
    
    public IArchitecture getArchitecture();    
}