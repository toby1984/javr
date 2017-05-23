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

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.Validate;
import org.apache.log4j.Logger;

import de.codesourcery.javr.assembler.parser.Parser.CompilationMessage;
import de.codesourcery.javr.assembler.phases.ExpandMacrosPhase;
import de.codesourcery.javr.assembler.phases.GatherSymbolsPhase;
import de.codesourcery.javr.assembler.phases.GenerateCodePhase;
import de.codesourcery.javr.assembler.phases.ParseSourcePhase;
import de.codesourcery.javr.assembler.phases.Phase;
import de.codesourcery.javr.assembler.phases.PrepareGenerateCodePhase;
import de.codesourcery.javr.assembler.phases.SubstituteRegisterAliases;
import de.codesourcery.javr.assembler.phases.SyntaxCheckPhase;

public class Assembler 
{
    private static final Logger LOG = Logger.getLogger(Assembler.class);

    private final CompilerSettings compilerSettings = new CompilerSettings();

    private CompilationContext compilationContext;

    public boolean compile(CompilationUnit unit,
            CompilerSettings compilerSettings,
            IObjectCodeWriter codeWriter,
            ResourceFactory rf) 
                    throws IOException 
    {
        Validate.notNull(compilerSettings, "compilerSettings must not be NULL");
        Validate.notNull(codeWriter, "codeWriter must not be NULL");
        Validate.notNull(rf, "resourceFactory must not be NULL");
        Validate.notNull(unit,"unit must not be NULL");
        
        this.compilerSettings.populateFrom( compilerSettings );
        
        unit.beforeCompilationStarts();

        this.compilationContext = new CompilationContext( unit , codeWriter , rf , compilerSettings );
        
        final List<Phase> phases = new ArrayList<>();
        phases.add( new ParseSourcePhase() );
        phases.add( new SyntaxCheckPhase() );
        phases.add( new GatherSymbolsPhase() );
        phases.add( new SubstituteRegisterAliases() );
        phases.add( new ExpandMacrosPhase() );
        phases.add( new PrepareGenerateCodePhase() );
        phases.add( new GenerateCodePhase() );

        LOG.info("assemble(): Now compiling "+unit);

        final StringBuilder debugOutput = new StringBuilder(); 
        boolean success = false;
        try 
        {
            final long startAllPhases = System.currentTimeMillis();
            for ( Phase phase : phases )
            {
                LOG.debug("Assembler phase: "+phase);
                compilationContext.beforePhase();

                final long start = System.currentTimeMillis();
                boolean hasErrors;
                try 
                {
                    phase.beforeRun( compilationContext );

                    final long timeAtPhaseStart = System.currentTimeMillis();
                    phase.run( compilationContext );
                    final long timeAfterPhaseStart = System.currentTimeMillis();

                    hasErrors = unit.hasErrors(true);
                    if ( ! hasErrors ) {
                        phase.afterSuccessfulRun( compilationContext );
                    } 
                    final long timeAfterRun = System.currentTimeMillis();
                    
                    final String msg = "**** Phase "+phase+" ****\n"+
                    		     "Preparation   : "+(timeAtPhaseStart-start)+" ms\n"+
                    		     "Runtime       : "+(timeAfterPhaseStart-timeAtPhaseStart)+" ms\n"+
                    		     "Postprocessing: "+(timeAfterRun-timeAfterPhaseStart)+" ms";
                    if ( debugOutput.length() > 0 ) {
                    	debugOutput.append("\n");
                    }
                    debugOutput.append(msg);
                } 
                catch (Exception e) 
                {
                    LOG.error("assemble(): ",e);
                    if ( e instanceof RuntimeException) {
                        throw (RuntimeException) e;
                    }
                    throw new RuntimeException(e);
                }

                if ( hasErrors ) 
                {
                    LOG.error("compile(): Compilation failed with errors in phase '"+phase.getName()+"'");
                    return false;
                }                
            }
            
            final long end = System.currentTimeMillis();
            unit.addMessage( CompilationMessage.info( unit , "Finished compiling "+unit.getResource()+" after "+(end-startAllPhases)+" ms") );

            System.err.flush();
            System.out.flush();
            System.out.println( "=================================");
            System.out.println( debugOutput );
            System.out.println( "=================================");
            System.out.flush();
            success = true;
        } 
        finally 
        {
            codeWriter.finish( unit , compilationContext , success );
        }
        return true;
    }
}