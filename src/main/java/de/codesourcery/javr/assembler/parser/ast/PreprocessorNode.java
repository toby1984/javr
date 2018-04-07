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
package de.codesourcery.javr.assembler.parser.ast;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.Validate;

import de.codesourcery.javr.assembler.ICompilationContext;
import de.codesourcery.javr.assembler.parser.TextRegion;

public class PreprocessorNode extends AbstractASTNode implements Resolvable {

    public final Preprocessor type;
    public final List<String> arguments;
    
    public static enum Preprocessor 
    {
        IF_DEFINE("ifdef"),
        IF_NDEFINE("ifndef"),
        PRAGMA("pragma"),
        INCLUDE("include"),
        ENDIF("endif"),
        ERROR("error"),
        WARNING("warning"),
        MESSAGE("message"),
        DEFINE("define");
        
        public final String literal;
        
        private Preprocessor(String literal) {
            this.literal = literal;
        }
        
        public static Preprocessor parse(String s) 
        {
            Preprocessor[] v = values();
            for ( int i = 0, len = v.length ; i < len ; i++ ) 
            {
                if ( s.equalsIgnoreCase( v[i].literal ) ) {
                    return v[i];
                }
            }
            return null;
        }        
    }
    
    public PreprocessorNode(Preprocessor p , TextRegion r) {
        this(p, new ArrayList<String>() , r );
    }
    
    public PreprocessorNode(Preprocessor p ,List<String> arguments,TextRegion r) {
        super(r);
        Validate.notNull(p, "type must not be NULL");
        Validate.notNull(arguments, "arguments must not be NULL");
        this.type = p;
        this.arguments = new ArrayList<>(arguments);
    }
    
    @Override
    protected PreprocessorNode createCopy() 
    {
        return new PreprocessorNode( this.type , this.arguments , getTextRegion().createCopy() );
    }

    @Override
    public String toString() {
        return this.type+" ("+this.arguments+")";
    }
    
    public List<String> getArguments() {
		return arguments;
	}

    @Override
    public boolean resolve(ICompilationContext context) 
    {
        if ( type == Preprocessor.DEFINE ) 
        {
            children().forEach( child -> child.visitDepthFirst( (n,ctx) -> 
            { 
                if ( n instanceof Resolvable) { 
                    ((Resolvable)n).resolve( context ); 
                }
            }));
        }
        return true;
    }
}