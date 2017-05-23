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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.ConsoleAppender;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.PatternLayout;

import de.codesourcery.javr.assembler.parser.Parser.CompilationMessage;
import de.codesourcery.javr.assembler.parser.Parser.Severity;
import de.codesourcery.javr.assembler.util.FileResource;
import de.codesourcery.javr.assembler.util.FileResourceFactory;
import de.codesourcery.javr.assembler.util.Resource;
import de.codesourcery.javr.ui.Project;
import de.codesourcery.javr.ui.config.ProjectConfiguration;
import de.codesourcery.javr.ui.config.ProjectConfiguration.OutputFormat;

public class CmdLine 
{
    @SuppressWarnings("unused")
    private static final Logger LOG = Logger.getLogger(CmdLine.class);
    
    private boolean verbose = false;
    private boolean hideWarnings = false;
    
    public static void main(String[] args) 
    {
        System.exit( new CmdLine().run(args) );
    }
    
    private static int printHelp() 
    {
        System.out.println();
        System.out.println("Usage:\n [-v] [-h] <source file>\n\n");
        System.out.println("Assembles the source file for an ATMega88.\n");
        System.out.println("-h/--help             => show help");
        System.out.println("-v                    => verbose output");
        System.out.println("--ignore-segment-size => compile even if output size exceeds the architecture limits");
        System.out.println("--max-errors <num>    => sets the maximum number of errors that is permitted before compilation is aborted");
        System.out.println("-f <intel|raw>        => output format (intel hex or raw binary)");
        System.out.println("--hide-warnings       => do not print warning messages");
        return 1;
    }

    private void setupConsoleAppender() 
    {
        final ConsoleAppender console = new ConsoleAppender(); 
        
        console.setLayout(new PatternLayout("%d{ISO8601} %p [%t] %c:%L - %m%n")); 
        console.setThreshold(Level.FATAL);
        console.activateOptions();
        
        Logger.getRootLogger().removeAllAppenders();
        Logger.getRootLogger().addAppender(console);
    }
    
    public int run(String[] arguments) 
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
        
        verbose = args.stream().anyMatch( a -> "-v".equals( a ) );
        
        final Set<String> argsSeen = new HashSet<>();
        OutputFormat outputFormat = null;
        
        final CompilerSettings compilerSettings = new CompilerSettings();
        for ( int i = 0 ; i < args.size() ; i++ ) 
        {
            final String arg = args.get(i);
            final String nextArg = (i+1) < args.size() ? args.get(i+1) : null;
            final boolean hasMoreArgs = StringUtils.isNotBlank( nextArg );
            int argsToRemove = 0;
            if ( "--ignore-segment-size".equals( arg ) ) {
                if ( argsSeen.contains( arg ) ) 
                {
                    return error("Duplicate command-line argument '"+arg+"'");
                }
                argsSeen.add( arg );
                compilerSettings.setFailOnAddressOutOfRange( false );
                argsToRemove = 1;
            } 
            else if ( "--hide-warnings".equals( arg ) ) 
            {
                if ( argsSeen.contains( arg ) ) 
                {
                    return error("Duplicate command-line argument '"+arg+"'");
                }
                argsSeen.add( arg );
                hideWarnings = true;
                argsToRemove = 1;
            }             
            else if ( "-f".equals( arg ) ) 
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
                    compilerSettings.setMaxErrors( Integer.parseInt( nextArg.trim() ) );
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
        } 
        catch (IOException e) 
        {
            return error( "Failed to open file",e );
        }

        final OutputFormat finalFormat = outputFormat;
        final ObjectCodeWriter writer;
        writer = new FileObjectCodeWriter() {
            
            @Override
            protected Optional<Resource> getOutputFile(CompilationUnit unit, Segment s) throws IOException 
            {
                final Optional<File> file = Project.getOutputFileName( finalFormat ,s ,  srcFile );
                if ( ! file.isPresent() ) {
                    return Optional.empty();
                }
                return Optional.of( new FileResource( file.get()  , "UTF-8" ) );
            }
        };

        System.out.println("Compiling "+srcFile.getAbsolutePath() );
        final ResourceFactory rf = FileResourceFactory.createInstance( srcFile.getParentFile() );

        try 
        {
            final ProjectConfiguration projectConfiguration = new ProjectConfiguration();
            projectConfiguration.setCompilerSettings( compilerSettings );

            final Project project = new Project( projectConfiguration );
            
            asm.compile( unit , project.getConfiguration().getCompilerSettings() , writer , rf );
            printMessages(unit);
        } 
        catch (IOException e) 
        {
            printMessages(unit);
            return error("compilation failed",e);
        }
        return 0;
    }
    
    private void printMessages(CompilationUnit unit) 
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
            
            if ( msg.severity == Severity.WARNING && hideWarnings ) {
                continue;
            }
            if ( severe ) {
                System.err.println( text );
            } else {
                System.out.println( text );
            }
        }
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
