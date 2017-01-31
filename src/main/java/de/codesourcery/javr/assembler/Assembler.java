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

import de.codesourcery.javr.assembler.phases.ExpandMacrosPhase;
import de.codesourcery.javr.assembler.phases.GatherSymbolsPhase;
import de.codesourcery.javr.assembler.phases.GenerateCodePhase;
import de.codesourcery.javr.assembler.phases.ParseSourcePhase;
import de.codesourcery.javr.assembler.phases.Phase;
import de.codesourcery.javr.assembler.phases.PrepareGenerateCodePhase;
import de.codesourcery.javr.assembler.phases.SubstituteRegisterAliases;
import de.codesourcery.javr.assembler.phases.SyntaxCheckPhase;
import de.codesourcery.javr.ui.IProject;
import de.codesourcery.javr.ui.config.IConfigProvider;

public class Assembler 
{
    private static final Logger LOG = Logger.getLogger(Assembler.class);

    private final CompilerSettings compilerSettings = new CompilerSettings();

    private CompilationContext compilationContext;

    public boolean compile(IProject project,IObjectCodeWriter codeWriter,ResourceFactory rf, IConfigProvider config) throws IOException 
    {
        Validate.notNull(project, "project must not be NULL");
        Validate.notNull(codeWriter, "codeWriter must not be NULL");
        Validate.notNull(rf, "resourceFactory must not be NULL");
        Validate.notNull(config, "provider must not be NULL");

        final CompilationUnit unit = project.getCompileRoot();
        Validate.notNull(unit, "project's compile root must not be NULL");
        
        project.getGlobalSymbolTable().clear();
        unit.beforeCompilationStarts( project.getGlobalSymbolTable() );

        this.compilationContext = new CompilationContext( unit , project.getGlobalSymbolTable() , codeWriter , rf , compilerSettings , config.getConfig() );
        
        final List<Phase> phases = new ArrayList<>();
        phases.add( new ParseSourcePhase(config) );
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
            codeWriter.finish( compilationContext , success );
        }
        return true;
    }

    public CompilerSettings getCompilerSettings() {
        return compilerSettings;
    }
}