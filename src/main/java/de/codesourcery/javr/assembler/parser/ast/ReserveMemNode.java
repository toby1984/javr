package de.codesourcery.javr.assembler.parser.ast;

import de.codesourcery.javr.assembler.ICompilationContext;
import de.codesourcery.javr.assembler.ICompilationContext.Phase;
import de.codesourcery.javr.assembler.parser.Parser.CompilationMessage;
import de.codesourcery.javr.assembler.parser.Parser.Severity;

public class ReserveMemNode extends ASTNode {

    @Override
    public void compile(ICompilationContext ctx) 
    {
        if ( ctx.isInPhase( Phase.VALIDATE1) ) 
        {
            if ( childCount() != 1 ) {
                ctx.message( new CompilationMessage(Severity.ERROR,"Expected exactly one operand",this) );
            } 
            ASTNode child = child(0);
            if ( ! (child instanceof IValueNode) ) {
                ctx.message( new CompilationMessage(Severity.ERROR,"Syntax error (operand needs to evaluate to an integer)",child) );
            }
        } 
        else if ( ctx.isInPhase( Phase.RESOLVE_SYMBOLS) ) 
        {
            Object value = ((IValueNode) child(0)).getValue();
            if ( value == null ) {
                ctx.message( new CompilationMessage(Severity.ERROR,"Failed to resolve value",child(0)) );
            } else if ( !( value instanceof Number ) ) {
                ctx.message( new CompilationMessage(Severity.ERROR,"Not a number",child(0)) );
            }
        }
        else if ( ctx.isInPhase( Phase.GENERATE_CODE ) ) 
        {
            switch( ctx.currentSegment() ) 
            {
                case EEPROM:
                case SRAM:
                    final Number value = (Number) ((IValueNode) child(0)).getValue();
                    ctx.allocateBytes( value.intValue() );
                    break;
                default:
                    ctx.message( new CompilationMessage(Severity.ERROR,"Cannot reserve bytes in "+ctx.currentSegment()+" segment, only SRAM and EEPROM are supported",this));
            }
        }
    }    
}
