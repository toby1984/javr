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

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.apache.commons.lang3.Validate;
import org.apache.log4j.Logger;

import de.codesourcery.javr.assembler.ICompilationContext;
import de.codesourcery.javr.assembler.parser.TextRegion;
import de.codesourcery.javr.assembler.util.FileResource;
import de.codesourcery.javr.assembler.util.Resource;

public class PreprocessorNode extends NodeWithMemoryLocation implements Resolvable {

    private static final Logger LOG = Logger.getLogger( PreprocessorNode.class );

    public final Preprocessor type;
    public final List<String> arguments;
    
    private Resource file;
    
    public static enum Preprocessor 
    {
        IF_DEFINE("ifdef"),
        IF_NDEFINE("ifndef"),
        PRAGMA("pragma"),
        INCLUDE("include"),
        INCLUDE_BINARY("incbin"),
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
        final PreprocessorNode result = new PreprocessorNode( this.type , this.arguments , getTextRegion().createCopy() );
        result.file = this.file;
        return result;
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
        if ( hasType( Preprocessor.DEFINE ) ) 
        {
            children().forEach( child -> child.visitDepthFirst( (n,ctx) -> 
            { 
                if ( n instanceof Resolvable) { 
                    ((Resolvable)n).resolve( context ); 
                }
            }));
        } 
        else if ( hasType( Preprocessor.INCLUDE_BINARY ) ) 
        {
            try {
                this.file = getResource(context).orElse( null );
            } 
            catch (IOException e) 
            {
                context.error("Failed to open file: "+e.getMessage(),this);
                this.file = null;
            }
        }
        return true;
    }

    @Override
    public boolean hasMemoryLocation() {
        return type == Preprocessor.INCLUDE_BINARY;
    }
    
    public boolean hasType(Preprocessor includeBinary) 
    {
        if ( this.type == null ) {
            throw new IllegalStateException("No type set");
        }
        return includeBinary.equals( this.type );
    }    
    
    public Resource getFile() 
    {
        return file;
    }
    
    public Optional<Resource> getResource(ICompilationContext context) throws IOException 
    {
        if ( getArguments().size() != 1 ) {
            return Optional.empty();
        }        
        
        String path = getArguments().get(0);
        if ( path.startsWith("\"") &&  path.endsWith("\"") ) {
            path = path.substring(1,path.length()-1 );
        } else {
            throw new RuntimeException("Internal error, filename is not in quotes");
        }
        return Optional.of( context.getResourceFactory().resolveResource( context.currentCompilationUnit().getResource(), path ) );
    }
    
    public boolean isValidFile(Resource file) 
    {
        if ( file instanceof FileResource) 
        {
            final File f = ((FileResource) file).getFile();
            final boolean exists = f.exists();
            final boolean isFile = f.isFile();
            final boolean canRead = f.canRead();
            final boolean isOk = exists && isFile && canRead;
            if ( ! isOk ) {
                LOG.error("isValidFile(): #incbin failed to read "+f.getAbsolutePath()+" (exists: "+exists+" | is_file: "+isFile+" | can_read: "+canRead+")");
            }
            return isOk;
        }
        return true;
    }
    
    @Override
    public int getSizeInBytes() throws IllegalStateException {
        if ( file == null ) {
            throw new IllegalStateException("Called although file has not been resolved?");
        }
        return file.size();
    }
}