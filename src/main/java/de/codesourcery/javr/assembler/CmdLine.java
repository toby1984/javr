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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.ConsoleAppender;
import org.apache.log4j.FileAppender;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.PatternLayout;

import de.codesourcery.hex2raw.IntelHex;
import de.codesourcery.javr.assembler.arch.IArchitecture;
import de.codesourcery.javr.assembler.arch.impl.ATMega88;
import de.codesourcery.javr.assembler.parser.Lexer;
import de.codesourcery.javr.assembler.parser.LexerImpl;
import de.codesourcery.javr.assembler.parser.Parser;
import de.codesourcery.javr.assembler.parser.Parser.CompilationMessage;
import de.codesourcery.javr.assembler.parser.Scanner;
import de.codesourcery.javr.assembler.util.FileResource;
import de.codesourcery.javr.assembler.util.FileResourceFactory;
import de.codesourcery.javr.assembler.util.Resource;
import de.codesourcery.javr.ui.config.IConfig;
import de.codesourcery.javr.ui.config.ProjectConfiguration.OutputFormat;
import de.codesourcery.javr.ui.config.ProjectConfiguration.OutputSpec;

public class CmdLine 
{
    private static final Logger LOG = Logger.getLogger(CmdLine.class);
    
    public static void main(String[] args) 
    {
        System.exit( run(args) );
    }
    
    private static int printHelp() 
    {
        System.out.println();
        System.out.println("Usage:\n [-v] [-h] <source file>\n\n");
        System.out.println("Assembles the source file for an ATMega88.\n");
        System.out.println("-h/--help          => show help");
        System.out.println("-v                 => verbose output");
        System.out.println("--max-errors <num> => sets the maximum number of errors that is permitted before compilation is aborted");
        System.out.println("-f <intel|raw>     => output format (intel hex or raw binary)");
        return 1;
    }

    private static void setupConsoleAppender() 
    {
        final ConsoleAppender console = new ConsoleAppender(); 
        
        console.setLayout(new PatternLayout("%d{ISO8601} %p [%t] %c:%L - %m%n")); 
        console.setThreshold(Level.FATAL);
        console.activateOptions();
        
        Logger.getRootLogger().removeAllAppenders();
        Logger.getRootLogger().addAppender(console);
    }
    
    public static int run(String[] arguments) 
    {
        setupConsoleAppender();
        
        final Assembler asm = new Assembler();    
        
        final Set<String> switches = new HashSet<>( Arrays.asList( new String[] {"-v","-h", "--help" , "--verbose"} ) );

        final List<String> args = new ArrayList<>( Arrays.asList( arguments ) );
        final boolean help = args.stream().anyMatch( a -> "-h".equals( a ) || "--help".equals( a ) );

        if ( help ) 
        {
            return printHelp();
        }
        
        final boolean verbose = args.stream().anyMatch( a -> "-v".equals( a ) );
        
        final Set<String> argsSeen = new HashSet<>();
        OutputFormat outputFormat = null;
        for ( int i = 0 ; i < args.size() ; i++ ) 
        {
            final String arg = args.get(i);
            final String nextArg = (i+1) < args.size() ? args.get(i+1) : null;
            final boolean hasMoreArgs = StringUtils.isNotBlank( nextArg );
            int argsToRemove = 0;
            if ( "-f".equals( arg ) ) 
            {
                if ( ! hasMoreArgs ) {
                    return error("-f option needs an argument");
                }
                if ( argsSeen.contains( arg ) ) 
                {
                    return error("Duplicate command-line argument '"+arg+"'");
                }
                argsSeen.add( arg );
                switch( nextArg ) {
                    case "intel": outputFormat = OutputFormat.INTEL_HEX; break;
                    case "raw": outputFormat = OutputFormat.RAW; break;
                    default:
                        return error("Unknown output format '"+nextArg+"'");
                }
                argsToRemove = 2;
            } else if ( "--max-errors".equals(arg ) )
            {
                if ( ! hasMoreArgs ) {
                    return error("-f option needs an argument");
                }
                if ( argsSeen.contains( arg ) ) 
                {
                    return error("Duplicate command-line argument '"+arg+"'");
                }
                argsSeen.add( arg );
                try {
                    asm.getCompilerSettings().setMaxErrors( Integer.parseInt( nextArg.trim() ) );
                } catch(NumberFormatException e) {
                    return error("Invalid command-line, expected a number but got '"+nextArg.trim()+"'");
                }
                argsToRemove = 2;                
            }
             
            if ( argsToRemove != 0 ) 
            {
                for ( int j = argsToRemove ; j > 0 ; j--) {
                    args.remove(i);
                }
                i--;                
            }
        }
        
        if ( outputFormat == null ) {
            outputFormat = OutputFormat.INTEL_HEX;
        }

        args.removeIf( arg -> switches.contains( arg ) );

        if ( args.size() != 1 ) {
            return error("Invalid command line, you need to provide exactly one file to compile (got "+args.size()+")");
        }

        final File srcFile;
        final CompilationUnit unit; 
        try {
            srcFile = new File(args.get(0) ).getCanonicalFile();
            unit = new CompilationUnit( new FileResource( srcFile , Resource.ENCODING_UTF ) );
        } catch (IOException e) {
            return error( "Failed to open file",e );
        }

        final IArchitecture arch = new ATMega88();

        final ObjectCodeWriter writer;
        try {
            writer = createOutputWriter(srcFile, outputFormat, unit, arch);
        } 
        catch (IOException e1) 
        {
            return error("Failed to create output files",e1);
        }

        System.out.println("Compiling "+srcFile.getAbsolutePath() );
        final ResourceFactory rf = FileResourceFactory.createInstance( srcFile.getParentFile() );

        final IConfig config  = new IConfig() 
        {
            @Override
            public String getEditorIndentString() { return "  "; }

            @Override
            public IArchitecture getArchitecture() {
                return arch;
            }

            @Override
            public Parser createParser() {
                return new Parser( arch );
            }

            @Override
            public Lexer createLexer(Scanner s) {
                return new LexerImpl( s );
            }
        };

        try 
        {
            asm.compile( unit , writer , rf , ()-> config );
            printMessages(unit);
        } 
        catch (IOException e) 
        {
            printMessages(unit);
            return error("compilation failed",e);
        }
        return 0;
    }
    
    private static void printMessages(CompilationUnit unit) 
    {
        final List<CompilationMessage> messages = unit.getMessages( true );
        messages.sort(  CompilationMessage.compareSeverityDescending() );
        
        for ( final CompilationMessage msg : messages ) {
            final String prefix;
            boolean severe = true;
            switch( msg.severity ) 
            {
                case ERROR: prefix = "ERROR"; break;
                case INFO: prefix = "INFO"; severe = false; break;
                case WARNING: prefix = "WARNING"; break;
                default:
                    prefix = msg.severity.toString();
            }
            final String position = msg.region != null ? "offset "+msg.region.start() : "<unknown location>"; 
            final String text = prefix+" - "+position+" - "+msg.message;
            if ( severe ) {
                System.err.println( text );
            } else {
                System.out.println( text );
            }
        }
    }
    
    private static ObjectCodeWriter createOutputWriter(final File srcFile,OutputFormat format,final CompilationUnit unit, final IArchitecture arch)throws IOException 
    {
        final boolean[] compilationSuccess={false};

        final String fileSuffix;
        switch(format) 
        {
            case INTEL_HEX: fileSuffix = ".hex"; break;
            case RAW: fileSuffix = ".raw"; break;
            default: throw new RuntimeException("Internal error,unhandled output format "+format);
        }

        final Resource flashOut = new FileResource( new File( srcFile.getParentFile() , srcFile.getName()+".flash"+fileSuffix ) , Resource.ENCODING_UTF);
        final Resource epromOut = new FileResource( new File( srcFile.getParentFile() , srcFile.getName()+".eeprom"+fileSuffix ) , Resource.ENCODING_UTF);
        final Resource sramOut  = new FileResource( new File( srcFile.getParentFile() , srcFile.getName()+".sram"+fileSuffix ) , Resource.ENCODING_UTF);

        final Map<Segment,OutputSpec> outputSpec = new HashMap<>();
        outputSpec.put( Segment.FLASH , new OutputSpec(flashOut,Segment.FLASH, format ) ); 
        outputSpec.put( Segment.EEPROM, new OutputSpec(epromOut,Segment.EEPROM, format ) );
        outputSpec.put( Segment.SRAM , new OutputSpec(sramOut,Segment.SRAM,format ) );

        return new ObjectCodeWriter() 
        {
            @Override
            public void finish(ICompilationContext context,boolean success) throws IOException 
            {
                compilationSuccess[0] = success;
                super.finish(context,success);

                if ( ! success ) 
                {
                    return;
                }

                for ( Segment s : Segment.values() ) 
                {
                    final OutputSpec spec = outputSpec.get( s );
                    if ( spec == null ) 
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
                    int bytesWritten;
                    try ( InputStream in = buffer.createInputStream() ; OutputStream out = spec.resource.createOutputStream() ) 
                    {
                        if ( spec.format == OutputFormat.INTEL_HEX ) 
                        {
                            bytesWritten = new IntelHex().rawToHex( in , out , buffer.getStartAddress().getByteAddress() ); 
                        } 
                        else if ( spec.format == OutputFormat.RAW ) 
                        {
                            bytesWritten = IOUtils.copy( in , out );
                        } else {
                            throw new RuntimeException("Unhandled output format: "+spec.format);
                        }
                    }
                    if ( bytesWritten > 0 )
                    {
                        final int segSize = arch.getSegmentSize( s );
                        final float percentage = 100.0f*(bytesWritten/(float) segSize);
                        final DecimalFormat DF = new DecimalFormat("#####0.00");
                        final String msg = s+": Wrote "+bytesWritten+" bytes ("+DF.format(percentage)+" %) to "+spec.resource;
                        unit.addMessage( CompilationMessage.info( context.currentCompilationUnit() , msg ) );                    
                    }
                    LOG.info("finish(): Wrote "+bytesWritten+" bytes to "+spec.resource+" in format "+spec.format);
                }      
            }
        };
    }

    private static int error(String msg) {
        return error(msg,null);
    }

    private static int error(String msg,Throwable t) 
    {
        System.err.println("ERROR: "+msg+"\n");
        if ( t != null ) 
        {
            System.err.println("\n");
            t.printStackTrace( System.err );
            System.err.println("\n\n");
        }
        return 1;
    }
}
