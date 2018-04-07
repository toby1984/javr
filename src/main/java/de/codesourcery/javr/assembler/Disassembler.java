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
package de.codesourcery.javr.assembler;

import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import de.codesourcery.javr.assembler.arch.IArchitecture;
import de.codesourcery.javr.assembler.arch.IArchitecture.DisassemblerSettings;
import de.codesourcery.javr.assembler.arch.impl.ATMega328p;

public class Disassembler {

    private IArchitecture architecture = new ATMega328p();
    
    private final DisassemblerSettings settings = defaultSettings( new DisassemblerSettings() );
    private boolean verboseMode;

    private static void printHelp() 
    {
        System.out.println("USAGE: [-h|--help] [-v|--verbose]  [--stdin] [--avr-as] <input file> [output file]");
    }
    
    public static void main(String[] arguments) 
    {
        if ( arguments.length == 0 ) 
        {            
            printHelp();
            System.exit(0);
        }
        
        final Disassembler disasm = new Disassembler();

        final List<String> args = Arrays.stream( arguments ).map( String::trim ).collect( Collectors.toList() );
        InputStream in = null;
        OutputStream out = System.out;
        int exitCode = 0;
        try 
        {
            for (int i = 0; i < args.size(); i++) 
            {
                final String arg = args.get(i);
                switch( arg ) 
                {
                    case "--stdin":
                        if ( in != null ) 
                        {
                            if ( in == System.in ) {
                                throw new RuntimeException("ERROR: Input already set to read from stdin");
                            } else {
                                throw new RuntimeException("ERROR: Cannot set input to stdin, already set to read from file");
                            }
                        }
                        if ( disasm.isVerboseMode() ) 
                        {
                            System.out.println("INFO: Reading input from stdin");
                        }
                        in = System.in;
                        break;
                    case "-h": case "--help":
                        printHelp();
                        return;
                    case "-v": case "--verbose":
                        disasm.setVerboseMode( true );
                        break;
                    case "--avr-as":
                        disasm.setAvrAsMode( true );
                        break;
                    default:
                        if ( arg.startsWith("-") ) {
                            throw new RuntimeException("ERROR: Unknown command-line option: '"+arg+"'");
                        }
                        final File file = new File( arg );                            
                        if ( in == null ) 
                        {
                            if ( ! file.exists() || ! file.canRead() || ! file.isFile() ) {
                                throw new RuntimeException("ERROR: Path does not exist or is no readable file: "+arg);
                            }
                            if ( disasm.isVerboseMode() ) {
                                System.out.println("INFO: Reading input from "+file);
                            }                                
                            in = new FileInputStream( file );
                        }
                        else if ( out == System.out ) 
                        {
                            final File parentFile = file.getParentFile();
                            if ( parentFile != null && ! parentFile.exists() ) 
                            {
                                throw new RuntimeException("ERROR: Parent directory does not exist: "+file.getParentFile());
                            }
                            if ( disasm.isVerboseMode() ) {
                                System.out.println("INFO: Writing output to file "+file);
                            }
                            out = new FileOutputStream( file );
                        } else {
                            throw new RuntimeException("ERROR: Unknown/super-fluous command-line argument: '"+arg+"'");                                
                        }
                }
            }

            if ( in == null ) 
            {
                throw new RuntimeException("No input file");
            }
            if ( disasm.isVerboseMode() ) {
                System.out.println("INFO: Now disassembling...");
            }
            disasm.disassemble( in , out );
            if ( disasm.isVerboseMode() && out != System.out ) 
            {
                System.out.println("INFO: Finished.");
            }            
        } 
        catch(Exception e) 
        {
            e.printStackTrace();
            exitCode = 1;
        }
        finally {
            System.exit( exitCode );
        }
    }

    public DisassemblerSettings getSettings() {
        return settings;
    }

    public void setVerboseMode(boolean verboseMode) {
        this.verboseMode = verboseMode;
    }

    public boolean isVerboseMode() {
        return verboseMode;
    }

    public void setAvrAsMode(boolean yesNo) 
    {
        if ( yesNo ) {
            avrAsMode( this.settings );
        } else {
            defaultSettings( this.settings );
        }
    }

    private DisassemblerSettings avrAsMode(DisassemblerSettings settings) 
    {
        settings.printBytes = true;
        settings.printAddresses = true;
        settings.resolveRelativeAddresses = false;
        settings.printCompoundRegistersAsLower=true;
        settings.byteOpcode = ".byte";
        return settings;
    }    

    private DisassemblerSettings defaultSettings(DisassemblerSettings settings) 
    {
        settings.printBytes = true;
        settings.printAddresses = true;
        settings.resolveRelativeAddresses = true;
        settings.printCompoundRegistersAsLower=false;
        return settings;
    }

    public void disassemble(InputStream in,OutputStream out) throws IOException 
    {
        final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        final byte[] tmpBuffer = new byte[1024];
        try   {
            int len = 0;
            while ( ( len = in.read( tmpBuffer ) ) > 0 ) 
            {
                buffer.write( tmpBuffer , 0 , len );
            }
        } finally {
            in.close();
        }

        final byte[] data = buffer.toByteArray();
        if ( verboseMode ) {
            System.out.println("Disassembling "+data.length+" bytes");
        }

        final String disassembly = architecture.disassemble( data , data.length , settings );
        try( BufferedWriter writer = new BufferedWriter( new OutputStreamWriter( out ) ) ) {
            writer.write( disassembly );
        }
    }    
}
