/**
 * Copyright 2015-2018 Tobias Gierke <tobias.gierke@code-sourcery.de>
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

import org.apache.commons.lang3.Validate;

import de.codesourcery.javr.assembler.ICompilationContext;
import de.codesourcery.javr.assembler.Segment;
import de.codesourcery.javr.assembler.parser.Parser.CompilationMessage;
import de.codesourcery.javr.assembler.parser.ast.ASTNode;
import de.codesourcery.javr.assembler.parser.ast.ASTNode.IIterationContext;
import de.codesourcery.javr.assembler.parser.ast.DirectiveNode;
import de.codesourcery.javr.assembler.parser.ast.DirectiveNode.Directive;
import de.codesourcery.javr.assembler.parser.ast.IValueNode;
import de.codesourcery.javr.assembler.parser.ast.LabelNode;

/**
 * Abstract base-class for compiler phases that handles common AST nodes.
 *
 * @author tobias.gierke@code-sourcery.de
 */
public abstract class AbstractPhase implements Phase 
{
    private final String name;
    private final boolean generateMessages;
    
    protected LabelNode previousGlobalLabel;
    
    public AbstractPhase(String name,boolean generateMessages) 
    {
        Validate.notBlank(name, "name must not be NULL or blank");
        this.name = name;
        this.generateMessages = generateMessages;
    }
    
    @Override
    public void beforeRun(ICompilationContext ctx) {
        previousGlobalLabel = null;
    }
    
    protected void visitNode(ICompilationContext context, ASTNode node,IIterationContext<?> ctx) 
    {
        if ( node instanceof LabelNode) 
        {
            final LabelNode label = (LabelNode) node;
            if ( label.isGlobal() ) {
                previousGlobalLabel = label;
            }
        } 
        else if ( node instanceof DirectiveNode )
        {
            final DirectiveNode dnNode = (DirectiveNode) node;
			final Directive directive = dnNode.directive;
            switch( directive ) 
            {
                case ORG:
                    final IValueNode expr = (IValueNode) node.child(0);
                    final Object value = expr.getValue();
                    if ( !( value instanceof Number) ) 
                    {
                        if ( generateMessages ) 
                        {
                            if ( value != null ) {
                                context.message( CompilationMessage.warning( context.currentCompilationUnit() , "Expected a number" , expr) );
                            } else {
                                context.message( CompilationMessage.warning( context.currentCompilationUnit() , "Failed to resolve expression" , expr) );
                            }
                        }
                        return;
                    }
                    final int address = ((Number) value).intValue();
                    context.setStartAddress( address );
                    break;            		
                case CSEG: 
                    previousGlobalLabel = null; // local labels cannot belong to a global label from a different segment
                	context.setSegment( Segment.FLASH ); 
                	break;
                case DSEG: 
                    previousGlobalLabel = null; // local labels cannot belong to a global label from a different segment
                	context.setSegment( Segment.SRAM ) ; 
                	break;
                case ESEG: 
                    previousGlobalLabel = null; // local labels cannot belong to a global label from a different segment
                	context.setSegment( Segment.EEPROM ); 
                	break;
                default:
                    // $$FALL-THROUGH$$
            }
        }
    }

    @Override
    public final String getName() {
        return name;
    }
}