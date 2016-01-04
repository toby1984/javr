package de.codesourcery.javr.assembler.parser.ast;

import org.apache.commons.lang3.Validate;

import de.codesourcery.javr.assembler.parser.TextRegion;

public class DirectiveNode extends ASTNode 
{
    public final Directive directive;
    
    public static enum Directive 
    {
        CSEG("cseg",0,0),
        DSEG("dseg",0,0),
        ESEG("eseg",0,0),
        RESERVE("byte",1,1),
        INIT_BYTES("db",1,Integer.MAX_VALUE),
        INIT_WORDS("dw",1,Integer.MAX_VALUE),
        EQU("equ" , 1 , 1 );
        
        public final String literal;
        public final int minOperandCount;
        public final int maxOperandCount;
        
        private Directive(String literal,int minOperandCount,int maxOperandCount) 
        {
            this.literal = literal;
            this.minOperandCount = minOperandCount;
            this.maxOperandCount = maxOperandCount;
        }
        
        public boolean isValidOperandCount(int actual) 
        {
            return minOperandCount <= actual && actual <= maxOperandCount;
        }
        
        public static Directive parse(String s) 
        {
            Directive[] v = values();
            for ( int i = 0, len = v.length ; i < len ; i++ ) {
                if ( s.equalsIgnoreCase( v[i].literal ) ) {
                    return v[i];
                }
            }
            return null;
        }
    }
    
    public boolean is(Directive d) {
        return d.equals( this.directive );
    }
    
    public DirectiveNode(Directive directive) 
    {
        Validate.notNull(directive, "directive must not be NULL");
        this.directive = directive;
    }    
    
    public DirectiveNode(Directive directive,TextRegion region) 
    {
        super(region);
        Validate.notNull(directive, "directive must not be NULL");
        this.directive = directive;
    }
    
    @Override
    protected DirectiveNode createCopy() {
        if ( getTextRegion() != null ) {
            return new DirectiveNode(this.directive , getTextRegion().createCopy() );
        }
        return new DirectiveNode(this.directive );
    }
}