package de.codesourcery.javr.assembler.phases;

import de.codesourcery.javr.assembler.ICompilationContext;
import de.codesourcery.javr.assembler.Segment;
import de.codesourcery.javr.assembler.parser.Parser.CompilationMessage;
import de.codesourcery.javr.assembler.parser.ast.AST;
import de.codesourcery.javr.assembler.parser.ast.ASTNode;
import de.codesourcery.javr.assembler.parser.ast.ASTNode.IASTVisitor;
import de.codesourcery.javr.assembler.parser.ast.ASTNode.IIterationContext;
import de.codesourcery.javr.assembler.parser.ast.DirectiveNode;
import de.codesourcery.javr.assembler.parser.ast.DirectiveNode.Directive;
import de.codesourcery.javr.assembler.parser.ast.IValueNode;
import de.codesourcery.javr.assembler.parser.ast.InstructionNode;

public class GenerateCodePhase implements Phase 
{
    private final boolean onlyAllocation;
    
    public GenerateCodePhase() {
        this(false);
    }
    
    protected GenerateCodePhase(boolean onlyAllocation) {
        this.onlyAllocation = onlyAllocation;
    }
    
    @Override
    public void run(ICompilationContext context) throws Exception {

        final AST ast = context.getCompilationUnit().getAST();
        
        final IASTVisitor visitor = new IASTVisitor() 
        {
            @Override
            public void visit(ASTNode node, IIterationContext ctx) 
            {
                visitNode(context, node, ctx); 
            }
        };
        ast.visitBreadthFirst( visitor );        
    }
    
    protected final void visitNode(ICompilationContext context, ASTNode node,IIterationContext ctx) 
    {
        if ( node instanceof InstructionNode ) 
        {
            if ( onlyAllocation ) 
            {
                final int bytes = context.getArchitecture().getInstructionLengthInBytes( (InstructionNode) node , context , true );
                context.allocateBytes( bytes );
            } else {
                context.getArchitecture().compile( (InstructionNode) node , context );
            }
            ctx.dontGoDeeper();
        } 
        else if ( node instanceof DirectiveNode ) 
        {
            final Directive directive = ((DirectiveNode) node).directive;
            switch( directive ) 
            {
                case CSEG: context.setSegment( Segment.FLASH ); break;
                case DSEG: context.setSegment( Segment.SRAM ) ; break;
                case ESEG: context.setSegment( Segment.EEPROM ); break;                
                case INIT_BYTES:
                case INIT_WORDS:
                    for ( ASTNode child : node.children ) 
                    {
                        Object value = ((IValueNode) child).getValue();
                        final int iValue = ((Number) value).intValue();
                        switch( directive ) 
                        {
                            case INIT_BYTES:
                                context.writeByte( iValue );
                                break;
                            case INIT_WORDS:
                                context.writeWord( iValue );
                                break;
                            default:
                                throw new RuntimeException("Unreachable code reached");
                        }
                    }
                    break;
                case RESERVE:
                    switch( context.currentSegment() ) 
                    {
                        case EEPROM:
                        case SRAM:
                            final Number value = (Number) ((IValueNode) node.child(0)).getValue();
                            context.allocateBytes( value.intValue() );
                            break;
                        default:
                            context.message( CompilationMessage.error("Cannot reserve bytes in "+context.currentSegment()+" segment, only SRAM and EEPROM are supported",node ) );
                    }
                    ctx.dontGoDeeper();                            
                    break;
                default:
                    break;
                
            }
            ctx.dontGoDeeper();
        }
    }    
}