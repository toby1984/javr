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

import de.codesourcery.javr.assembler.ast.InstructionNode;

public interface IArchitecture {

    public Architecture getType();
    
    public boolean hasType(Architecture t);
    
    /**
     * 
     * @return size of program memory in bytes
     */
    public int getFlashMemorySize();
    
    /**
     * 
     * @return size of SRAM memory in bytes
     */
    public int getSRAMMemorySize();
    
    // --
    public boolean isValidRegister(String s);
    
    public Register parseRegister(String s);
    
    // --
    
    public boolean isValidInstruction(String s);
    
    public Instruction parseInstruction(String s);    
    
    public void compile(InstructionNode node,ICompilationContext context);
}
