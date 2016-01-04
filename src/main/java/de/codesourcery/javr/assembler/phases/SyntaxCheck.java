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

public class SyntaxCheck implements Phase 
{
    @Override
    public void run(ICompilationContext context) throws Exception
    {
        final AST ast = context.getCompilationUnit().getAST();
        
        final IASTVisitor visitor = new IASTVisitor() 
        {
            @Override
            public void visit(ASTNode node, IIterationContext ctx) 
            {
                if ( node instanceof InstructionNode) 
                {
                    if ( context.currentSegment() != Segment.FLASH ) 
                    {
                        context.error("Instructions need to be placed in CODE segment",node);
                    }
                }
               else if ( node instanceof DirectiveNode ) 
               {
                   final Directive directive = ((DirectiveNode) node).directive;
                   int operandCount = node.childCount();
                   if ( directive == Directive.EQU ) {
                       operandCount = operandCount > 0 ? operandCount-1 : operandCount;
                   } 
                   if ( ! directive.isValidOperandCount( node.childCount() ) ) {
                       context.error("Invalid operand count (expected at least "+directive.minOperandCount+" and at most "+directive.maxOperandCount, node );
                   }
                   
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
                           }
                           break;
                       case RESERVE:
                           switch( context.currentSegment() ) 
                           {
                               case EEPROM:
                               case SRAM:
                                   break;
                               default:
                                   context.message( CompilationMessage.error("Cannot reserve bytes in "+context.currentSegment()+" segment, only SRAM and EEPROM are supported",node ) );
                           }
                           ctx.dontGoDeeper();                            
                           break;
                       default:
                           throw new RuntimeException("Internal error, unhandled directive "+directive);
                   }
                   ctx.dontGoDeeper();
               } 
            }
        };
        ast.visitBreadthFirst( visitor );        
    }
}
