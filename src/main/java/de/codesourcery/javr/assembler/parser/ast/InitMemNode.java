package de.codesourcery.javr.assembler.parser.ast;

import org.apache.commons.lang3.Validate;

import de.codesourcery.javr.assembler.Address;
import de.codesourcery.javr.assembler.ICompilationContext;
import de.codesourcery.javr.assembler.ICompilationContext.Phase;
import de.codesourcery.javr.assembler.parser.Parser.CompilationMessage;

public class InitMemNode extends ASTNode 
{
    public final ElementSize elementSize;
    
    public static enum ElementSize {
        BYTE,WORD
    }
    
    public InitMemNode(ElementSize elementSize) {
        Validate.notNull(elementSize, "elementSize must not be NULL");
        this.elementSize = elementSize;
    }
    
    @Override
    public void compile(ICompilationContext ctx) 
    {
        if ( ctx.isInPhase( Phase.GENERATE_CODE ) || ctx.isInPhase( Phase.RESOLVE_SYMBOLS ) ) 
        {
            for ( ASTNode child : children ) 
            {
                final IValueNode vn = (IValueNode) child;
                final Object value = vn.getValue();
                final int number;
                if ( value instanceof Address) {
                    number = ((Address) value).getByteAddress();
                } else if ( value instanceof Number) {
                    number = ((Number) value).intValue();
                } else {
                    ctx.message( CompilationMessage.error( "Expression needs to evaluate to a number bbut was: "+value , child ) );
                    number = 0;
                }
                final boolean outputCode = ctx.isInPhase( Phase.GENERATE_CODE );
                switch( elementSize ) 
                {
                    case BYTE:
                        if ( outputCode ) {
                            if ( number > 255 || number < -128 ) {
                                ctx.message( CompilationMessage.error( "value does not fit into 8 bit: "+number , child ) );
                            }
                            ctx.writeByte( number );
                        } else {
                            ctx.allocateByte(); 
                        }
                        break;
                    case WORD:
                        if ( outputCode ) {
                            if ( number > 65535|| number < -32768 ) {
                                ctx.message( CompilationMessage.error( "value does not fit into 16 bit: "+number , child ) );
                            }                        
                            ctx.writeWord( number );
                        } else {
                            ctx.allocateBytes(2); 
                        }
                        break;
                    default:
                        throw new RuntimeException("Unreachable code reached: "+elementSize);
                }
            }
        }
    }
}
