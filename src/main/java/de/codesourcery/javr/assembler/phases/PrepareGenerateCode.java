package de.codesourcery.javr.assembler.phases;

import org.apache.log4j.Logger;

import de.codesourcery.javr.assembler.Address;
import de.codesourcery.javr.assembler.ICompilationContext;
import de.codesourcery.javr.assembler.Segment;
import de.codesourcery.javr.assembler.parser.Identifier;
import de.codesourcery.javr.assembler.parser.ast.AST;
import de.codesourcery.javr.assembler.parser.ast.ASTNode;
import de.codesourcery.javr.assembler.parser.ast.ASTNode.IASTVisitor;
import de.codesourcery.javr.assembler.parser.ast.ASTNode.IIterationContext;
import de.codesourcery.javr.assembler.parser.ast.DirectiveNode;
import de.codesourcery.javr.assembler.parser.ast.DirectiveNode.Directive;
import de.codesourcery.javr.assembler.parser.ast.EquLabelNode;
import de.codesourcery.javr.assembler.parser.ast.IValueNode;
import de.codesourcery.javr.assembler.parser.ast.IdentifierDefNode;
import de.codesourcery.javr.assembler.parser.ast.LabelNode;
import de.codesourcery.javr.assembler.symbols.Symbol;
import de.codesourcery.javr.assembler.symbols.Symbol.Type;

public class PrepareGenerateCode extends GenerateCodePhase
{
    private static final Logger LOG = Logger.getLogger(PrepareGenerateCode.class);
    
    public PrepareGenerateCode() 
    {
        super(true);
    }
    
    @Override
    public void run(ICompilationContext context) throws Exception
    {
        final AST ast = context.currentCompilationUnit().getAST();
        
        // assign memory addresses to labels
        final IIterationContext fakeCtx = new IIterationContext() 
        {
            @Override
            public void stop() { };
            
            @Override
            public void dontGoDeeper() {};
        };
        
        final IASTVisitor visitor1 = new IASTVisitor() 
        {
            @Override
            public void visit(ASTNode node, IIterationContext ctx) 
            {
                final Segment currentSeg = context.currentSegment();
                final int adr = context.currentOffset();

                if ( ! visitNode( context , node , fakeCtx ) ) {
                    return;
                }
                
                if ( node instanceof LabelNode ) 
                {
                    final Identifier identifier = ((LabelNode) node).identifier;
                    final Symbol s = context.currentSymbolTable().get( identifier , Symbol.Type.LABEL );
                    switch( currentSeg ) 
                    {
                        case EEPROM:
                        case SRAM:
                            s.setValue( Address.byteAddress( currentSeg , adr ) );
                            break;
                        case FLASH:
                            s.setValue( Address.wordAddress( currentSeg , adr ) );                            
                            break;
                        default:
                            throw new RuntimeException("Unreachable code reached");
                    }
                }               
            }
        };
        
        ast.visitBreadthFirst( visitor1 );   
        
        // sanity checking now that all memory addresses have been resolved
        final IASTVisitor visitor = new IASTVisitor() 
        {
            @Override
            public void visit(ASTNode node, IIterationContext ctx) 
            {
               if ( node instanceof IValueNode ) 
               {
                   if ( ! ((IValueNode) node).resolveValue( context ) ) {
                       context.error( "Failed to resolve value for "+node, node );
                   } else if ( LOG.isDebugEnabled() ) {
                       LOG.debug("visit(): Resolved "+node);
                   }
               }
               
               if ( node instanceof DirectiveNode ) 
               {
                   final Directive directive = ((DirectiveNode) node).directive;
                   switch( directive ) 
                   {
                       case EQU:
                           // first child is EquLabelNode
                           final EquLabelNode labelNode = (EquLabelNode) node.firstChild();
                           final Symbol symbol = context.currentSymbolTable().get( labelNode.name );
                           
                           checkResolved( node.child(1) );
                           final Object v = ((IValueNode) node.child(1) ).getValue();
                           if ( v == null ) {
                               context.error("Symbol "+labelNode.name+" exists but failed to resolve value ? " , labelNode );
                           } else {
                               symbol.setValue( v , Type.EQU );
                           }
                           break;
                       case INIT_BYTES:
                       case INIT_WORDS:
                           for ( ASTNode child : node.children ) 
                           {
                               final Number value = getValue( child );
                               if ( value != null ) {
                                   int iValue = value.intValue();
                                   switch( directive ) 
                                   {
                                       case INIT_BYTES:
                                           if ( iValue < -127 || iValue > 255 ) {
                                               context.error("Value out of 8-bit range: "+iValue,child);
                                           }
                                           break;
                                       case INIT_WORDS:
                                           if ( iValue < -32767|| iValue > 65565 ) {
                                               context.error("Value out of 16-bit range: "+iValue,child);
                                           }
                                           break;
                                       default:
                                           throw new RuntimeException("Internal error, unhandled directive "+directive);
                                   }
                               }
                           }
                           break;
                       case RESERVE:
                           final Number toReserve = getValue( node.firstChild() ); 
                           if ( toReserve != null ) 
                           {
                               final int remainingBytes = context.getBytesRemainingInCurrentSegment();
                               if (toReserve.intValue() > remainingBytes ) 
                               {
                                   context.error("Value too large, only "+remainingBytes+" bytes remaining in segment "+context.currentSegment() , node.firstChild() );
                               } else if ( toReserve.intValue() < 0 ) 
                               {
                                   context.error("Only positive values allowed (was: "+toReserve+")", node.firstChild() );
                               }
                           }
                           break;
                       default:
                           // do nothing
                   }
               }               
            }
            
            private Number getValue(ASTNode node) 
            {
                Object value = ((IValueNode) node).getValue();
                if ( value == null ) {
                    context.error( "Failed to resolve value for ", node );
                } 
                else if ( value instanceof Number) 
                {
                    return ((Number) value).intValue();
                }  
                context.error("Expected a number but got "+value, node );                    
                return null;
            }
            
            private void checkResolved(ASTNode node) 
            {
                if ( ((IValueNode) node).getValue() == null ) {
                    context.error( "Failed to resolve value for "+node, node );
                }
            }            
        };
        ast.visitDepthFirst( visitor );        
    }
}