package de.codesourcery.javr.assembler.parser.ast;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.Validate;

import de.codesourcery.javr.assembler.parser.TextRegion;

public class PreprocessorNode extends ASTNode {

    public final Preprocessor type;
    public final List<String> arguments;
    
    public static enum Preprocessor 
    {
        IF_DEFINE("ifdef"),
        IF_NDEFINE("ifndef"),
        PRAGMA("pragma"),
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
            for ( int i = 0, len = v.length ; i < len ; i++ ) {
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
}
