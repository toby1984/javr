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

import de.codesourcery.javr.assembler.arch.IArchitecture;
import de.codesourcery.javr.assembler.arch.impl.ATMega88;
import de.codesourcery.javr.assembler.parser.Lexer;
import de.codesourcery.javr.assembler.parser.LexerImpl;
import de.codesourcery.javr.assembler.parser.Parser;
import de.codesourcery.javr.assembler.parser.PreprocessingLexer;
import de.codesourcery.javr.assembler.parser.Scanner;
import de.codesourcery.javr.assembler.parser.ast.AST;
import de.codesourcery.javr.assembler.util.StringResource;
import de.codesourcery.javr.ui.Project;
import de.codesourcery.javr.ui.config.ProjectConfiguration;
import junit.framework.TestCase;

public class ParseTestHelper extends TestCase 
{
    private final IArchitecture arch = new ATMega88();
    
    private CompilationContext compilationContext;
    
    
    // helper functions
    protected AST parse(String s) 
    {
        return parse(s,false);
    }
    
    protected AST parseWithPreprocessor(String s) 
    {
        return parse(s,true);
    }
    
    protected AST parse(String s,boolean usePreProcessingLexer) 
    {
        if ( usePreProcessingLexer && ! s.endsWith("\n")) {
            s += "\n";
        }
        final Parser p = new Parser(arch);
        final StringResource resource = new StringResource("dummy", s);
        final CompilationUnit unit = new CompilationUnit( resource );
        
        final Project project = new Project(new ProjectConfiguration() );
        project.addCompilationUnit( unit );
        
        final ObjectCodeWriter writer = new ObjectCodeWriter();
        
        compilationContext = new CompilationContext( unit , writer , project ,new CompilerSettings() );
        
        final Lexer lexer;
        if ( usePreProcessingLexer ) 
        {
            lexer = new PreprocessingLexer( new LexerImpl(new Scanner(resource) ) , compilationContext );
        } else {
            lexer = new LexerImpl(new Scanner(resource) );
        }
        return p.parse( compilationContext, unit , lexer );
    }
}
