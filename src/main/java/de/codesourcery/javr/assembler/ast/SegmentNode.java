package de.codesourcery.javr.assembler.ast;

import org.apache.commons.lang3.Validate;

import de.codesourcery.javr.assembler.ICompilationContext;
import de.codesourcery.javr.assembler.TextRegion;

public class SegmentNode extends ASTNode 
{
    public final Segment segment;
    
    public static enum Segment 
    {
        FLASH,
        SRAM,
        EEPROM
    }

    public SegmentNode(Segment segment,TextRegion region) {
        super(region);
        
        Validate.notNull(segment, "segment must not be NULL");
        this.segment = segment;
    }
    
    @Override
    public void compile(ICompilationContext ctx) 
    {
        ctx.setSegment( this.segment );
        super.compile(ctx);
    }
}