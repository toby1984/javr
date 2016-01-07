package de.codesourcery.javr.assembler.phases;

import org.apache.log4j.Logger;

import de.codesourcery.javr.assembler.ICompilationContext;
import de.codesourcery.javr.assembler.parser.Parser.CompilationMessage;
import de.codesourcery.javr.assembler.parser.ast.AST;
import de.codesourcery.javr.assembler.parser.ast.ASTNode;
import de.codesourcery.javr.assembler.parser.ast.ASTNode.IASTVisitor;
import de.codesourcery.javr.assembler.parser.ast.ASTNode.IIterationContext;
import de.codesourcery.javr.assembler.parser.ast.DirectiveNode;
import de.codesourcery.javr.assembler.parser.ast.DirectiveNode.Directive;
import de.codesourcery.javr.assembler.parser.ast.IValueNode;
import de.codesourcery.javr.assembler.parser.ast.InstructionNode;

public class GenerateCodePhase extends AbstractPhase 
{
    private static final Logger LOG = Logger.getLogger(GenerateCodePhase.class);
    
    private final boolean isInResolvePhase;
    
    public GenerateCodePhase() {
        this("generate_code",false);
    }
    
    protected GenerateCodePhase(String name,boolean onlyAllocation) {
        super(name);
        this.isInResolvePhase = onlyAllocation;
    }
    
    @Override
    public void run(ICompilationContext context) throws Exception {

        final AST ast = context.currentCompilationUnit().getAST();
        
        final IASTVisitor visitor = new IASTVisitor() 
        {
            @Override
            public void visit(ASTNode node, IIterationContext ctx) 
            {
                generateCode(context, node, ctx); 
            }
        };
        ast.visitBreadthFirst( visitor );        
    }
    
    protected boolean generateCode(ICompilationContext context, ASTNode node,IIterationContext ctx) 
    {
        if ( ! super.visitNode( context , node , ctx ) ) {
            return false;
        }

        if ( node instanceof InstructionNode ) 
        {
            try {            
                if ( isInResolvePhase ) 
                {
                    context.getArchitecture().validate( (InstructionNode) node , context );
                    final int bytes = context.getArchitecture().getInstructionLengthInBytes( (InstructionNode) node , context , true );
                    if ( LOG.isDebugEnabled() ) {
                        LOG.debug("generateCode(): Allocating "+bytes+" at "+context.currentAddress()+" segment for "+node);
                    }
                    context.allocateBytes( bytes );
                } else {
                    if ( LOG.isDebugEnabled() ) {
                        LOG.debug("generateCode(): Compiling instruction at "+context.currentAddress()+" segment for "+node);
                    }                    
                    context.getArchitecture().compile( (InstructionNode) node , context );
                }
            } catch(Exception e) {
                LOG.debug("visitNode(): "+e.getMessage(),e);
                context.error( e.getMessage() , node );
            }            
            ctx.dontGoDeeper();
        } 
        else if ( node instanceof DirectiveNode ) 
        {
            final Directive directive = ((DirectiveNode) node).directive;
            switch( directive ) 
            {
                case INIT_BYTES:
                case INIT_WORDS:
                    for ( ASTNode child : node.children() ) 
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
        return true;
    }    
}