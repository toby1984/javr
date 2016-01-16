/**
 * Copyright 2015 Tobias Gierke <tobias.gierke@code-sourcery.de>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.codesourcery.javr.assembler.phases;

import de.codesourcery.javr.assembler.ICompilationContext;
import de.codesourcery.javr.assembler.Segment;
import de.codesourcery.javr.assembler.parser.ast.AST;
import de.codesourcery.javr.assembler.parser.ast.ASTNode;
import de.codesourcery.javr.assembler.parser.ast.ASTNode.IASTVisitor;
import de.codesourcery.javr.assembler.parser.ast.ASTNode.IIterationContext;
import de.codesourcery.javr.assembler.parser.ast.DirectiveNode;
import de.codesourcery.javr.assembler.parser.ast.DirectiveNode.Directive;
import de.codesourcery.javr.assembler.parser.ast.InstructionNode;

/**
 * Performs semantic checks on the AST.
 *
 * @author tobias.gierke@code-sourcery.de
 */
public class SyntaxCheckPhase implements Phase 
{
    @Override
    public String getName() {
        return "syntax_check";
    }
    
    @Override
    public void run(ICompilationContext context) throws Exception
    {
        final AST ast = context.currentCompilationUnit().getAST();
        
        final IASTVisitor visitor = new IASTVisitor() 
        {
            @Override
            public void visit(ASTNode node, IIterationContext ctx) 
            {
                if ( node instanceof InstructionNode) 
                {
                    if ( context.currentSegment() != Segment.FLASH ) 
                    {
                        if ( ! context.error("Instructions need to be placed in CODE segment",node) ) {
                            ctx.stop();
                        }
                    }
                }
               else if ( node instanceof DirectiveNode ) 
               {
                   final Directive directive = ((DirectiveNode) node).directive;
                   int operandCount = node.childCount();
                   if ( directive == Directive.EQU ) { // .equ is special since the first child node is the label, not an operand
                       operandCount = operandCount > 0 ? operandCount-1 : operandCount;
                   } 
                   if ( ! directive.isValidOperandCount( operandCount ) ) {
                       if ( ! context.error( directive.name().toUpperCase()+" directive has invalid operand count "+node.childCount()+" , (expected at least "+directive.minOperandCount+" and at most "+directive.maxOperandCount, node ) ) {
                           ctx.stop();
                       }
                   }
                   
                   switch( directive ) 
                   {
                       case CSEG: context.setSegment( Segment.FLASH ); break;
                       case DSEG: context.setSegment( Segment.SRAM ) ; break;
                       case ESEG: context.setSegment( Segment.EEPROM ); break;                       
                       case INIT_BYTES:
                       case INIT_WORDS:
                           break;
                       case RESERVE:
                           if( context.currentSegment() != Segment.SRAM && context.currentSegment() != Segment.EEPROM ) 
                           {
                               if ( ! context.error( "Cannot reserve bytes in "+context.currentSegment()+" segment, only SRAM and EEPROM are supported",node ) ) {
                                   ctx.stop();
                               }
                           }
                           break;
                       case EQU: // currently ignored
                       case DEVICE: // currently ignored
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
