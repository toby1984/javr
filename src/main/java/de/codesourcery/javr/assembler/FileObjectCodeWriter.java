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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.DecimalFormat;
import java.util.Optional;

import org.apache.commons.io.IOUtils;
import org.apache.log4j.Logger;

import de.codesourcery.hex2raw.IntelHex;
import de.codesourcery.javr.assembler.elf.ElfFile;
import de.codesourcery.javr.assembler.parser.Parser.CompilationMessage;
import de.codesourcery.javr.assembler.util.Resource;
import de.codesourcery.javr.ui.config.ProjectConfiguration.OutputFormat;

public abstract class FileObjectCodeWriter extends ObjectCodeWriter
{
    private static final Logger LOG = Logger.getLogger( FileObjectCodeWriter.class );

    private boolean artifactsGenerated = false;

    protected abstract Optional<Resource> getOutputFile(CompilationUnit unit,Segment s) throws IOException;
    
    public boolean isArtifactsGenerated() {
        return artifactsGenerated;
    }
    
    @Override
    public void reset(ICompilationContext context)
    {
        super.reset(context);
        artifactsGenerated = false;
        
        if ( context != null ) 
        {
            for ( Segment s : Segment.values() ) 
            {
                Optional<Resource> file;
                try 
                {
                    file = getOutputFile( context.currentCompilationUnit() , s );
                    file.ifPresent( v -> 
                    {
                        try 
                        {
                            if ( v.exists() ) {
                                v.delete();
                            }
                        } catch (IOException e) {
                            LOG.error("reset(): Failed to delete "+v,e);
                        }
                    } );                    
                } catch (IOException e1) {
                    LOG.error("reset(): "+e1);
                }
            }
        }
    }

    @Override
    public void finish(CompilationUnit unit,ICompilationContext context,boolean success) throws IOException 
    {
        super.finish(unit,context,success);

        if ( ! success ) 
        {
            return;
        }

        for ( Segment s : Segment.values() ) 
        {
            final Optional<Resource> outputResource = getOutputFile( unit , s );
            if ( ! outputResource.isPresent() ) 
            {
                LOG.info("finish(): Not writing file, project configuration has no output spec for segment "+s);
                continue;
            }
            final Buffer buffer = super.getBuffer( s );
            if ( buffer.isEmpty() ) 
            {
                LOG.info("finish(): Not writing file, compilation produced no data for segment "+s);
                continue;
            }
            final OutputFormat spec = context.getCompilationSettings().getOutputFormat();
            int bytesWritten;
            try ( InputStream in = buffer.createInputStream() ; OutputStream out = outputResource.get().createOutputStream() ) 
            {
                if ( spec == OutputFormat.INTEL_HEX ) 
                {
                    bytesWritten = new IntelHex().rawToHex( in , out , buffer.getStartAddress().getByteAddress() ); 
                } 
                else if ( spec == OutputFormat.RAW ) 
                {
                    bytesWritten = IOUtils.copy( in , out );
                } 
                else if ( spec == OutputFormat.ELF_EXECUTABLE || spec == OutputFormat.ELF_RELOCATABLE ) 
                {
                    if ( s == Segment.FLASH ) 
                    {
                        final ByteArrayOutputStream program = new ByteArrayOutputStream();
                        bytesWritten = IOUtils.copy( in , program );
                        if ( s == Segment.FLASH ) 
                        {
                            new ElfFile( spec ).write( context.getArchitecture() , this , unit.getSymbolTable() , out );
                        }
                    } else {
                        bytesWritten = 0;
                    }
                } else {
                    throw new RuntimeException("Unhandled output format: "+spec);
                }
            }
            if ( bytesWritten > 0 )
            {
                artifactsGenerated = true;

                final int segSize = context.getArchitecture().getSegmentSize( s );
                final float percentage = 100.0f*(bytesWritten/(float) segSize);
                final DecimalFormat DF = new DecimalFormat("#####0.00");
                final String msg;
                if ( spec == OutputFormat.ELF_EXECUTABLE && s == Segment.SRAM ) {
                    msg = s+": "+bytesWritten+" bytes used ("+DF.format(percentage)+" %)";
                } else {
                    msg = s+": Wrote "+bytesWritten+" bytes ("+DF.format(percentage)+" %) to "+outputResource.get();
                }
                context.message( CompilationMessage.info( context.currentCompilationUnit() , msg ) );                    
            }
            LOG.info("finish(): Wrote "+bytesWritten+" bytes to "+outputResource.get()+" in format "+spec);
        } 
    }
}