/**
 * Copyright 2015-2018 Tobias Gierke <tobias.gierke@code-sourcery.de>
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
import de.codesourcery.javr.assembler.Segment;
import de.codesourcery.javr.assembler.parser.ast.ASTNode;
import de.codesourcery.javr.assembler.parser.ast.InstructionNode;
import de.codesourcery.javr.ui.config.ProjectConfiguration.OutputFormat;

/**
 * Microcontroller architecture.
 *
 * <p>Implementations of this interface know about the features
 * of a specific uC architecture and most importantly, know how to turn
 * {@link InstructionNode}s into actual object code.</p>
 * 
 * @author tobias.gierke@code-sourcery.de
 */
public interface IArchitecture 
{
    /**
     * Disassembler settings.
     *
     * @author tobias.gierke@code-sourcery.de
     */
    public static final class DisassemblerSettings 
    {
        /**
         * Whether to include instruction addresses 
         * into the disassembly output. 
         */
        public boolean printAddresses;
        
        /**
         * The start address that should be assumed.
         */
        public int startAddress;
        
        /**
         * Whether to write the raw bytes as hex numbers
         * to the disassembly output. 
         */        
        public boolean printBytes;
        
        /**
         * Whether references to compound registers should
         * be printed using only the lower register number.
         */
        public boolean printCompoundRegistersAsLower;
        
        /**
         * Opcode used to output raw byte values 
         * when encountering bytes that cannot be disassembled
         * into valid instructions.
         */
        public String byteOpcode = ".db";
        
        /**
         * Whether relative branch/jump offsets should
         * be resolved to their true destination address
         * relative to the disassembly start address.
         */
        public boolean resolveRelativeAddresses=true;
    }
    
    /**
     * Returns the type of this architecture.
     * 
     * @return
     */
    public Architecture getType();
    
    /**
     * Check whether this architecture matches a given type.
     * 
     * @param t
     * @return
     */
    public boolean hasType(Architecture t);
    
    /**
     * Returns the size of a given memory segment size in bytes.
     * 
     * @param seg
     * @return
     */
    public int getSegmentSize(Segment seg);

    /**
     * Returns whether a string resembles a valid mnemonic for this architecture.
     * @param s
     * @return
     */
    public boolean isValidMnemonic(String s);
    
    /**
     * Returns the number of explicit arguments a given instruction AST node requires.
     *
     * Depending on the instruction this method may fail if the AST node has 
     * not had all of it's symbols etc. resolved.
     * 
     * Some mnemonics have implicit arguments, these are <b>not</b> counted
     * by this method.
     * 
     * @param node
     * @return argument count 
     * @see #isValidMnemonic(String)
     */
    public int getExplicitArgumentCount(InstructionNode node);
    
    /**
     * Returns the 'real' start address of the SRAM (register file + I/O register + etc.)
     * @return
     */
    public int getSRAMStartAddress();
    
    /**
     * Returns the number of IRQ vectors this architecture has.
     * 
     * @return
     */
    public int getIRQVectorCount();
    
    /**
     * Checks that code generation is possible for a given {@link ASTNode}.
     * 
     * @param node
     * @param context
     * @return
     */
    public boolean validate(InstructionNode node,ICompilationContext context);
    
    /**
     * Get/estimate instruction length.
     * 
     * <p>If the AST contains all required information this method will return the actual instruction length. If
     * the AST does not contain all information required to select the shortest possible encoding, this method will return the length
     * of the worst-case (=longest encoding).</p>
     * 
     * @param node
     * @param context
     * @param estimate
     * @return
     */
    public int getInstructionLengthInBytes(InstructionNode node,ICompilationContext context,boolean estimate);    
    
    /**
     * Turns a given {@link InstructionNode} into object code.
     * 
     * @param node
     * @param context
     * 
     * @see ICompilationContext#writeByte(int)
     * @see ICompilationContext#writeWord(int)
     * 
     * @return Relocation info or <code>null</code> if this instruction does not need relocation <b>or</b>
     * the currently active {@link OutputFormat} does not support relocation 
     */
    public void compile(InstructionNode node,ICompilationContext context);
 
    /**
     * Disassembles a raw object file.
     * 
     * @param data
     * @param len
     * @param settings
     * @return
     */
    public String disassemble(byte[] data,int len,DisassemblerSettings settings);
}