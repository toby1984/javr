package de.codesourcery.javr.assembler.parser.ast;

import java.util.Optional;

import org.apache.commons.lang3.Validate;

import de.codesourcery.javr.assembler.ICompilationContext;
import de.codesourcery.javr.assembler.parser.Identifier;
import de.codesourcery.javr.assembler.parser.TextRegion;
import de.codesourcery.javr.assembler.symbols.Symbol;
import de.codesourcery.javr.assembler.symbols.Symbol.Type;

public class FunctionCallNode extends AbstractASTNode implements IValueNode, Resolvable {

    public static final Identifier BUILDIN_FUNCTION_DEFINED = new Identifier("defined");
    
    public final Identifier functionName;
    
    private Object value;

    public FunctionCallNode(Identifier functionName,TextRegion region) {
        super(region);
        Validate.notNull(functionName, "functionName must not be NULL");
        this.functionName = functionName;
    }

    @Override
    protected FunctionCallNode createCopy() {
        return new FunctionCallNode(this.functionName , getTextRegion().createCopy() );
    }
    
    @Override
    public boolean resolve(ICompilationContext context) 
    {
        if ( childCount() == 1 ) 
        {
            final IValueNode child = (IValueNode) child(0);
            final String name = functionName.value;
            if ( BUILDIN_FUNCTION_DEFINED.equals( functionName ) )
            {
                if ( !(child instanceof IdentifierNode )) {
                    context.error("Expected an identifier", child);
                    return false;
                }
                final Identifier identifier = ((IdentifierNode) child).name;
                final Optional<Symbol> result = context.currentSymbolTable().maybeGet( identifier );
                this.value = result.isPresent() && result.get().getType() == Type.PREPROCESSOR_MACRO;
                return true;
            } 
            if ( name.equals("HIGH") || name.equals("LOW" ) ) 
            {
                if ( child instanceof Resolvable) {
                    ((Resolvable) child).resolve( context );
                }
                Number v = (Number) child.getValue();
                if ( v == null ) {
                    return false;
                }
                
                switch ( name ) {
                    case "HIGH": v = ( v.intValue() >>> 8 ) & 0xff; break;
                    case "LOW": v = v.intValue() & 0xff; break;
                    default: throw new RuntimeException("Unreachable code reached");
                }
                this.value = v;
                return true;
            }
            context.error("Unknown built-in function "+name,this);
        }
        return false;
    }    

    @Override
    public Object getValue() {
        return value;
    }
}