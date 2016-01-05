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
package de.codesourcery.javr.assembler.arch;

import de.codesourcery.javr.assembler.ICompilationContext;
import de.codesourcery.javr.assembler.parser.ast.InstructionNode;

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
    
    /**
     * 
     * @return size of EEPROM in bytes
     */
    public int getEEPromSize();    
    
    // --
    
    public boolean isValidInstruction(String s);
    
    public boolean validate(InstructionNode node,ICompilationContext context);
    
    /**
     * Get/estimate instruction length.
     * 
     * <p>If the AST contains all required information this method will return the actual instruction length. If
     * the AST does not contain all information required to select the shortest encoding, this method will return the length
     * of the worst-case (=longest encoding).</p>
     * 
     * @param node
     * @param context
     * @param estimate
     * @return
     */
    public int getInstructionLengthInBytes(InstructionNode node,ICompilationContext context,boolean estimate);    
    
    public void compile(InstructionNode node,ICompilationContext context);
    
    public String disassemble(byte[] data,int len);
}
