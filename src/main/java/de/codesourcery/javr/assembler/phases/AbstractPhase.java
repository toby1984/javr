package de.codesourcery.javr.assembler.phases;

import org.apache.commons.lang3.Validate;

import de.codesourcery.javr.assembler.ICompilationContext;
import de.codesourcery.javr.assembler.Segment;
import de.codesourcery.javr.assembler.parser.ast.ASTNode;
import de.codesourcery.javr.assembler.parser.ast.ASTNode.IIterationContext;
import de.codesourcery.javr.assembler.parser.ast.DirectiveNode;
import de.codesourcery.javr.assembler.parser.ast.DirectiveNode.Directive;

/**
 * Abstract base-class for compiler phases that handles common AST nodes.
 *
 * @author tobias.gierke@code-sourcery.de
 */
public abstract class AbstractPhase implements Phase 
{
    private final String name;
    
    public AbstractPhase(String name) 
    {
        Validate.notBlank(name, "name must not be NULL or blank");
        this.name = name;
    }
    
    protected boolean visitNode(ICompilationContext context, ASTNode node,IIterationContext ctx) 
    {
        if ( node instanceof DirectiveNode )
        {
            final Directive directive = ((DirectiveNode) node).directive;
            switch( directive ) 
            {
                case CSEG: context.setSegment( Segment.FLASH ); break;
                case DSEG: context.setSegment( Segment.SRAM ) ; break;
                case ESEG: context.setSegment( Segment.EEPROM ); break;
                default:
                    // $$FALL-THROUGH$$
            }
        }
        return true;
    }

    @Override
    public final String getName() {
        return name;
    }
}