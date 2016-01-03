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

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.Validate;

import de.codesourcery.javr.assembler.ICompilationContext.Phase;
import de.codesourcery.javr.assembler.Parser.CompilationMessage;
import de.codesourcery.javr.assembler.ast.AST;
import de.codesourcery.javr.assembler.ast.SegmentNode.Segment;
import de.codesourcery.javr.ui.IConfig;

public class Assembler 
{
    private IConfig config;
    private CompilationContext compilationContext;
    
    private boolean debug;
    
    private final List<Phase> phases = new ArrayList<>();
    
    private final class CompilationContext implements ICompilationContext 
    {
        private Segment segment = Segment.FLASH;
        private final SymbolTable symbolTable = new SymbolTable();
        
        private int codeOffset = 0;
        private int initDataOffset = 0;
        private int uninitDataOffset = 0;
        
        private byte[] codeSegment = new byte[0];
        private byte[] initDataSegment = new byte[0];
        private final AST ast;
        private Phase phase;
        
        public CompilationContext(AST ast) {
            this.ast = ast;
        }
        
        @Override
        public SymbolTable getSymbolTable() {
            return symbolTable;
        }
        
        @Override
        public Address currentAddress() 
        {
            return Address.byteAddress( currentSegment() , currentOffset() );
        }

        @Override
        public int currentOffset() 
        {
            switch( segment ) 
            {
                case FLASH:
                    return codeOffset;
                case SRAM:
                    return initDataOffset;
                case EEPROM:
                    return uninitDataOffset;
                default:
                    throw new RuntimeException("Unhandled segment type:"+segment);
            }
        }

        @Override
        public Segment currentSegment() {
            return segment;
        }

        @Override
        public void setSegment(Segment s) {
            Validate.notNull(s, "segment must not be NULL");
            this.segment = s;
        }
        
        @Override
        public void writeAsBytes(int value, int numberOfBytes) 
        {
            switch( numberOfBytes ) 
            {
                case 1:
                    writeByte(value);
                    break;
                case 2:
                    writeWord( value );
                    break;
                case 3:
                    writeWord( value >> 8);
                    writeByte( value );
                    break;
                case 4:
                    writeWord( value >> 16);
                    writeWord( value );
                    break;
                default:
                    throw new RuntimeException("Invalid byte count: "+numberOfBytes);
            }
        }        
        
        @Override
        public void writeByte(int value) 
        {
            switch( segment ) 
            {
                case FLASH:
                    if ( codeOffset+1 >= codeSegment.length ) {
                        codeSegment = realloc( codeSegment , codeOffset+1);
                    }
                    codeSegment[ codeOffset++ ] = (byte) value;
                    break;
                case SRAM:
                    if ( initDataOffset+1 >= initDataSegment.length ) {
                        initDataSegment = realloc( initDataSegment , initDataOffset+1 );
                    }
                    initDataSegment[ initDataOffset++ ] = (byte) value;
                    break;
                case EEPROM:
                    uninitDataOffset += 1;
                    break;
                default:
                    throw new RuntimeException("Unhandled segment type:"+segment);
            }
        }
        
        private byte[] realloc(byte[] input,int minLength) 
        {
            final int len = Math.max( input.length*2 , minLength );
            byte[] newArray = new byte[ len ];
            System.arraycopy( input , 0 , newArray , 0 , input.length );
            return newArray;
        }
        
        @Override
        public void writeWord(int value) {
            writeByte( value >> 8 );
            writeByte( value );
        }

        @Override
        public void message(CompilationMessage msg) {
            ast.addMessage( msg );
        }

        public void setPhase(Phase phase) 
        {
            this.phase = phase;
        }

        @Override
        public IArchitecture getArchitecture() {
            return config.getArchitecture();
        }

        @Override
        public void allocateByte() 
        {
            allocateBytes(1);
        }
        
        public void allocateBytes(int bytes)
        {
            switch( segment ) 
            {
                case FLASH:
                    codeOffset+=bytes;
                    break;
                case SRAM:
                    initDataOffset+=bytes;
                    break;
                case EEPROM:
                    uninitDataOffset += bytes;
                    break;
                default:
                    throw new RuntimeException("Unhandled segment type:"+segment);
            }               
        }

        @Override
        public void allocateWord() {
            allocateBytes(2);
        }

        @Override
        public boolean isInPhase(Phase p) {
            return p.equals( this.phase );
        }

        @Override
        public Phase currentPhase() {
            return phase;
        }
    }
    
    public Assembler() 
    {
        phases.add( Phase.VALIDATE1 );
        phases.add( Phase.GATHER_SYMBOLS );
        phases.add( Phase.RESOLVE_SYMBOLS );
        phases.add( Phase.VALIDATE2 );
        phases.add( Phase.GENERATE_CODE );
    }
    
    public void assemble(AST ast,IConfig config) 
    {
        this.compilationContext = new CompilationContext( ast );
        this.config = config;
        
        if ( ast.hasErrors() ) 
        {
            return;
        }
        
        for ( Phase p : phases )
        {
            logDebug("Assembler phase: "+compilationContext.currentPhase());
            
            this.compilationContext.setPhase( p );
            
            ast.compile( compilationContext );
            
            if ( ast.hasErrors() ) {
                return;
            }
        } 
    }
    
    private void logDebug(String msg) 
    {
        if ( debug ) {
            System.out.println(msg);
        }
    }
    
    public SymbolTable getSymbolTable() 
    {
        return compilationContext.getSymbolTable();
    }
    
    public void setDebug(boolean debug) {
        this.debug = debug;
    }
}