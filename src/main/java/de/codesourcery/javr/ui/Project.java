package de.codesourcery.javr.ui;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Paths;
import java.text.DecimalFormat;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;
import org.apache.log4j.Logger;

import de.codesourcery.hex2raw.IntelHex;
import de.codesourcery.javr.assembler.Assembler;
import de.codesourcery.javr.assembler.Buffer;
import de.codesourcery.javr.assembler.CompilationUnit;
import de.codesourcery.javr.assembler.IObjectCodeWriter;
import de.codesourcery.javr.assembler.ObjectCodeWriter;
import de.codesourcery.javr.assembler.ObjectCodeWriterWrapper;
import de.codesourcery.javr.assembler.Segment;
import de.codesourcery.javr.assembler.arch.IArchitecture;
import de.codesourcery.javr.assembler.arch.impl.ATMega88;
import de.codesourcery.javr.assembler.parser.Lexer;
import de.codesourcery.javr.assembler.parser.LexerImpl;
import de.codesourcery.javr.assembler.parser.Parser;
import de.codesourcery.javr.assembler.parser.Parser.CompilationMessage;
import de.codesourcery.javr.assembler.parser.Scanner;
import de.codesourcery.javr.assembler.util.Misc;
import de.codesourcery.javr.assembler.util.Resource;
import de.codesourcery.javr.ui.ProjectConfiguration.OutputFormat;
import de.codesourcery.javr.ui.ProjectConfiguration.OutputSpec;

public class Project implements IProject
{
    private static final Logger LOG = Logger.getLogger(Project.class);

    private IArchitecture architecture;

    private CompilationUnit compileRoot;    

    private ProjectConfiguration projectConfig = new ProjectConfiguration();

    private final ObjectCodeWriter writerDelegate = new ObjectCodeWriter();
    private final IObjectCodeWriter objWriter = new ObjectCodeWriterWrapper( writerDelegate ) 
    {
        public void reset() throws IOException 
        {
            super.reset();
            artifactsGenerated = false;
            for ( OutputSpec spec : projectConfig.getOutputFormats().values() ) 
            {
                spec.deleteFile();
            }
        }

        public void finish(boolean success) throws IOException 
        {
            super.finish(success);
            
            if ( ! success ) 
            {
                compilationSuccess = false;
                return;
            }
            
            for ( Segment s : Segment.values() ) 
            {
                final OutputSpec spec = projectConfig.getOutputFormats().get( s );
                if ( spec == null ) 
                {
                    LOG.info("finish(): Not writing file, project configuration has no output spec for segment "+s);
                    continue;
                }
                final Buffer buffer = ((ObjectCodeWriter) delegate).getBuffer( s );
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
                    artifactsGenerated = true;
                    
                    final int segSize = getArchitecture().getSegmentSize( s );
                    final float percentage = 100.0f*(bytesWritten/(float) segSize);
                    final DecimalFormat DF = new DecimalFormat("#####0.00");
                    final String msg = s+": Wrote "+bytesWritten+" bytes ("+DF.format(percentage)+" %) to "+spec.resource;
                    getCompileRoot().addMessage( CompilationMessage.info( msg ) );                    
                }
                LOG.info("finish(): Wrote "+bytesWritten+" bytes to "+spec.resource+" in format "+spec.format);
            } 
        }
    };

    private boolean compilationSuccess = false;
    private boolean artifactsGenerated = false;

    public Project(CompilationUnit compilationRoot) 
    {
        this( compilationRoot , new ProjectConfiguration() ); 
    }
    
    public Project( CompilationUnit compilationRoot, ProjectConfiguration config) 
    {
        Validate.notNull(compilationRoot, "compilationUnit must not be NULL");
        Validate.notNull(config, "config must not be NULL");
        
        this.architecture = new ATMega88();
        this.compileRoot = compilationRoot;
        this.projectConfig = config;
    }
    
    @Override
    public IObjectCodeWriter getObjectCodeWriter() {
        return objWriter;
    }

    public void setArchitecture(IArchitecture architecture) {
        Validate.notNull(architecture, "architecture must not be NULL");
        this.architecture = architecture;
    }

    @Override
    public IArchitecture getArchitecture() {
        return architecture;
    }

    public void setCompileRoot(CompilationUnit compileRoot) 
    {
        Validate.notNull(compileRoot, "compileRoot must not be NULL");

        this.compileRoot = compileRoot;
    }

    @Override
    public CompilationUnit getCompileRoot() {
        return compileRoot;
    }

    @Override
    public boolean canUploadToController() 
    {
        final Collection<OutputSpec> values = projectConfig.getOutputFormats().values();
        return compilationSuccess && 
                artifactsGenerated && 
                StringUtils.isNotBlank( projectConfig.getUploadCommand() ) &&
               ! values.isEmpty() && 
                values.stream().anyMatch( s -> s != null );
    }

    @Override
    public void uploadToController(OutputStream stdOut,OutputStream stdErr) throws IOException 
    {
        if ( ! canUploadToController() ) {
            throw new IllegalStateException("No upload command configured on this project");
        }

        /*
         * Expand commandline arguments.
         * 
         * %f => expands to file holding the flash data
         * %fa => expands to byte address where flash data should be uploaded to
         * 
         * %e => expands to file holding the EEPROM data
         * %ea => expands to byte address where EEPROM data should be uploaded to
         */
        final Map<String,String> params = new HashMap<>();
        for ( Segment s : new Segment[]{Segment.FLASH,Segment.EEPROM} ) 
        {
            final Buffer buffer = writerDelegate.getBuffer( s );
            final OutputSpec spec = projectConfig.getOutputFormats().get( s );
            if ( spec != null ) 
            {
                switch( s ) 
                {
                    case FLASH:
                        params.put("f", spec.resource.toString() );
                        params.put("fa", Integer.toString( buffer.getStartAddress().getByteAddress() ) );                        
                        break;
                    case EEPROM:
                        params.put("e", spec.resource.toString() );
                        params.put("ea", Integer.toString( buffer.getStartAddress().getByteAddress() ) );                       
                        break;
                    default:
                        throw new RuntimeException("Unhandled switch/case: "+s);
                }
            }
        }
        
        if ( params.isEmpty() ) {
            throw new RuntimeException("Internal error, no parameters ?");
        }

        final List<String> command = Misc.expand( projectConfig.getUploadCommand() , params );

        // invoke command
        LOG.info("uploadToController(): "+command.stream().collect(Collectors.joining()));

        final ProcessBuilder builder = new ProcessBuilder( command );
        final Process process = builder.start();
        int exitCode = -1;
        try {
            exitCode = process.waitFor();
        } 
        catch (InterruptedException e) 
        {
            Thread.currentThread().interrupt();
        }
        IOUtils.copy( process.getInputStream() , stdOut );
        IOUtils.copy( process.getErrorStream() , stdErr );
        
        if ( exitCode != 0 ) 
        {
            throw new IOException("Upload to microcontroller failed");
        }
    }

    @Override
    public boolean compile() throws IOException {
        
        compilationSuccess  = false;
        artifactsGenerated = false;
        
        final Assembler asm = new Assembler();
        compilationSuccess = asm.compile( getCompileRoot() , getObjectCodeWriter() , projectConfig , this );
        return compilationSuccess;
    }

    @Override
    public IConfig getConfig()
    {
        return new IConfig() {

            @Override
            public IArchitecture getArchitecture() {
                return Project.this.getArchitecture();
            }

            @Override
            public Lexer createLexer(Scanner s) {
                return new LexerImpl(s);
            }

            @Override
            public Parser createParser() 
            {
                final Parser p = new Parser();
                p.setArchitecture( Project.this.getArchitecture() );
                return p;
            }

            @Override
            public String getEditorIndentString() {
                return "  ";
            }
        };
    }

    public static File getCurrentWorkingDirectory() {
        return new File( Paths.get(".").toAbsolutePath().normalize().toString() );
    }

    @Override
    public ProjectConfiguration getConfiguration() {
        return this.projectConfig.createCopy();
    }

    @Override
    public void setConfiguration(ProjectConfiguration other) 
    {
        this.compilationSuccess = false;
        this.artifactsGenerated = false;
        this.projectConfig = other.createCopy();
    }

    @Override
    public Resource resolveResource(String child) throws IOException 
    {
        return projectConfig.resolveResource(child);
    }

    @Override
    public Resource resolveResource(Resource parent, String child) throws IOException {
        return projectConfig.resolveResource(parent,child);
    }
}