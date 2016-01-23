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

import de.codesourcery.javr.assembler.phases.GatherSymbolsPhase;
import de.codesourcery.javr.assembler.phases.GenerateCodePhase;
import de.codesourcery.javr.assembler.phases.ParseSourcePhase;
import de.codesourcery.javr.assembler.phases.Phase;
import de.codesourcery.javr.assembler.phases.PrepareGenerateCodePhase;
import de.codesourcery.javr.assembler.phases.SyntaxCheckPhase;
import de.codesourcery.javr.assembler.symbols.SymbolTable;
import de.codesourcery.javr.ui.config.IConfigProvider;

public class Assembler 
{
    private static final Logger LOG = Logger.getLogger(Assembler.class);

    private final CompilerSettings compilerSettings = new CompilerSettings();

    private CompilationContext compilationContext;

    public boolean compile(CompilationUnit unit,IObjectCodeWriter codeWriter,ResourceFactory rf, IConfigProvider config) throws IOException 
    {
        Validate.notNull(unit, "compilation unit must not be NULL");
        Validate.notNull(codeWriter, "codeWriter must not be NULL");
        Validate.notNull(rf, "resourceFactory must not be NULL");
        Validate.notNull(config, "provider must not be NULL");

        final SymbolTable globalSymbolTable = new SymbolTable(SymbolTable.GLOBAL);

        unit.beforeCompilationStarts(globalSymbolTable);

        this.compilationContext = new CompilationContext( unit , codeWriter , rf , globalSymbolTable , compilerSettings , config.getConfig() );

        final List<Phase> phases = new ArrayList<>();
        phases.add( new ParseSourcePhase(config) );
        phases.add( new SyntaxCheckPhase() );
        phases.add( new GatherSymbolsPhase() );
        phases.add( new PrepareGenerateCodePhase() );
        phases.add( new GenerateCodePhase() );

        LOG.info("assemble(): Now compiling "+unit);

        boolean success = false;
        try 
        {
            for ( Phase p : phases )
            {
                LOG.debug("Assembler phase: "+p);
                compilationContext.beforePhase();

                boolean hasErrors;
                try 
                {
                    p.beforeRun( compilationContext );

                    p.run( compilationContext );

                    hasErrors = unit.hasErrors(true);
                    if ( ! hasErrors ) {
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

                if ( hasErrors ) 
                {
                    LOG.error("compile(): Compilation failed with errors in phase '"+p.getName()+"'");
                    return false;
                }
            }

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