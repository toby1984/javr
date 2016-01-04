package de.codesourcery.javr.assembler.parser.ast;

import de.codesourcery.javr.assembler.ICompilationContext;
import de.codesourcery.javr.assembler.ICompilationContext.Phase;
import de.codesourcery.javr.assembler.parser.Identifier;
import de.codesourcery.javr.assembler.symbols.EquSymbol;

public class EquNode extends ASTNode 
{
    public Identifier getName() {
        return ((EquLabelNode) child(0)).name;
    }
    
    @Override
    public void compile(ICompilationContext ctx) 
    {
        if ( ctx.isInPhase( Phase.GATHER_SYMBOLS ) ) 
        {
            ctx.getSymbolTable().defineSymbol( new EquSymbol( getName() , this ) );
        } 
        else if ( ctx.isInPhase( Phase.RESOLVE_SYMBOLS ) ) 
        {
            final EquSymbol symbol = (EquSymbol) ctx.getSymbolTable().get( getName() );
            final IValueNode child = (IValueNode) child(1);
            symbol.setValue( child.getValue() );
        }
    }
}