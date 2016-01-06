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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.Validate;
import org.apache.log4j.Logger;

import de.codesourcery.javr.assembler.arch.IArchitecture;
import de.codesourcery.javr.assembler.parser.Parser.CompilationMessage;
import de.codesourcery.javr.assembler.parser.Parser.Severity;
import de.codesourcery.javr.assembler.parser.ast.ASTNode;
import de.codesourcery.javr.assembler.phases.GatherSymbols;
import de.codesourcery.javr.assembler.phases.GenerateCodePhase;
import de.codesourcery.javr.assembler.phases.ParseSource;
import de.codesourcery.javr.assembler.phases.Phase;
import de.codesourcery.javr.assembler.phases.PrepareGenerateCode;
import de.codesourcery.javr.assembler.phases.SyntaxCheck;
import de.codesourcery.javr.assembler.symbols.SymbolTable;
import de.codesourcery.javr.assembler.util.Resource;
import de.codesourcery.javr.ui.IConfig;
import de.codesourcery.javr.ui.IConfigProvider;

public class Assembler 
{
    private static final Logger LOG = Logger.getLogger(Assembler.class);
    
    private IConfig config;
    private CompilationContext compilationContext;
    private SymbolTable globalSymbolTable = new SymbolTable( SymbolTable.GLOBAL );
    
    private final class CompilationContext implements ICompilationContext 
    {
        private Segment segment = Segment.FLASH;
        
        private int codeOffset = 0;
        private int initDataOffset = 0;
        private int uninitDataOffset = 0;
        
        private byte[] codeSegment = new byte[0];
        private byte[] initDataSegment = new byte[0];
        
        private final CompilationUnit compilationUnit;
        
        public CompilationContext(CompilationUnit unit) 
        {
            Validate.notNull(unit, "unit must not be NULL");
            this.compilationUnit = unit;
            unit.setSymbolTable( new SymbolTable( compilationUnit.getResource().toString() , globalSymbolTable) );
        }
        
        public void save(Binary b , ResourceFactory rf) throws IOException 
        {
            for ( Segment seg : Segment.values() ) 
            {
                final Resource resource = rf.getResource( seg );
                if ( writeSegment( resource , seg ) != 0 ) 
                {
                    message( new CompilationMessage(Severity.INFO,"Wrote "+resource) );
                    b.setResource( seg , resource );
                }
            }
        }
        
        private int writeSegment(Resource r,Segment segment) throws IOException 
        {
            final int len;
            final byte[] data;
            switch( segment ) 
            {
                case EEPROM:
                    data = new byte[ uninitDataOffset ];
                    len = uninitDataOffset;
                    break;
                case FLASH:
                    len = codeOffset;
                    data = codeSegment;
                    break;
                case SRAM:
                    len = initDataOffset;
                    data = initDataSegment;
                    break;
                default:
                    throw new RuntimeException("Unhandled switch/case: "+segment);
            }
            if ( len > 0 ) {
                writeResource( r , data , len );
            } else {
                r.delete();
            }
            return len;
        }
        
        private void writeResource(Resource flash,byte[] data,int len) throws IOException 
        {
            try ( OutputStream out = flash.createOutputStream() ; InputStream in = new ByteArrayInputStream( data , 0 , len ) ) 
            {
                IOUtils.copy( in , out );
            }        
        }
        
        public void beforePhase() 
        {
            segment = Segment.FLASH;
            codeOffset = 0;
            initDataOffset = 0;
            uninitDataOffset = 0;
            codeSegment = new byte[0];
            initDataSegment = new byte[0];
        }
        
        @Override
        public SymbolTable currentSymbolTable() {
            return compilationUnit.getSymbolTable();
        }
        
        @Override
        public SymbolTable globalSymbolTable() {
            return globalSymbolTable;
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
            // AVR is little endian
            writeByte( value );
            writeByte( value >> 8 );
        }
        
        @Override
        public void error(String message, ASTNode node) {
            message( CompilationMessage.error(message,node ) );
        }

        @Override
        public void message(CompilationMessage msg) {
            currentCompilationUnit().getAST().addMessage( msg );
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
        public CompilationUnit currentCompilationUnit() {
            return compilationUnit;
        }

        @Override
        public int getBytesRemainingInCurrentSegment() 
        {
            final IArchitecture architecture = config.getArchitecture();
            final int available;
            switch( currentSegment() ) 
            {
             case EEPROM:
                 available = architecture.getEEPromSize();
                 break;
             case FLASH:
                 available = architecture.getFlashMemorySize();
                 break;
             case SRAM:
                 available = architecture.getSRAMMemorySize();
                 break;
             default:
                 throw new RuntimeException("Unreachable code reached");
            }
            final int remainingBytes = available - currentAddress().getByteAddress();
            if ( remainingBytes < 0 ) {
                LOG.warn("getBytesRemainingInCurrentSegment(): Segment overrun ("+currentSegment()+")");
                return 0;
            }
            return remainingBytes;
        }
    }
    
    public Binary compile(CompilationUnit unit,ResourceFactory rf, IConfigProvider config) throws IOException 
    {
        Validate.notNull(unit, "unit must not be NULL");
        Validate.notNull(rf, "rf must not be NULL");
        Validate.notNull(config, "provider must not be NULL");
        
        this.globalSymbolTable = new SymbolTable(SymbolTable.GLOBAL);
        this.compilationContext = new CompilationContext( unit );
        this.config = config.getConfig();
        
        final List<Phase> phases = new ArrayList<>();
        phases.add( new ParseSource(config) );
        phases.add( new SyntaxCheck() );
        phases.add( new GatherSymbols() );
        phases.add( new PrepareGenerateCode() );
        phases.add( new GenerateCodePhase() );
        
        LOG.info("assemble(): Now compiling "+unit);
        
        for ( Phase p : phases )
        {
            compilationContext.beforePhase();
            
            LOG.debug("Assembler phase: "+p);
            
            try 
            {
                p.beforeRun( compilationContext );
                
                p.run( compilationContext );
                
                if ( ! unit.getAST().hasErrors() ) {
                    p.afterSuccessfulRun( compilationContext );
                } 
            } 
            catch (Exception e) 
            {
                LOG.error("assemble(): ",e);
                if ( e instanceof RuntimeException) {
                    throw (RuntimeException) e;
                }
                throw new RuntimeException(e);
            }
            
            if ( p.stopOnErrors() && unit.getAST().hasErrors() ) 
            {
                System.err.println("Stopping with errors");
                return null;
            }
        }
        
        // write output
        final Binary b = new Binary();
        compilationContext.save( b , rf );
        return b;
    }
    
    public SymbolTable getGlobalSymbolTable() 
    {
        return globalSymbolTable;
    }
}