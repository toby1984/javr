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

import org.apache.commons.lang3.Validate;

import de.codesourcery.javr.assembler.ICompilationContext;
import de.codesourcery.javr.assembler.Segment;
import de.codesourcery.javr.assembler.parser.ast.ASTNode;
import de.codesourcery.javr.assembler.parser.ast.ASTNode.IIterationContext;
import de.codesourcery.javr.assembler.parser.ast.DirectiveNode;
import de.codesourcery.javr.assembler.parser.ast.DirectiveNode.Directive;
import de.codesourcery.javr.assembler.parser.ast.IdentifierDefNode;
import de.codesourcery.javr.assembler.parser.ast.RegisterNode;

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
    
    protected void visitNode(ICompilationContext context, ASTNode node,IIterationContext ctx) 
    {
        if ( node instanceof DirectiveNode )
        {
            final DirectiveNode dnNode = (DirectiveNode) node;
			final Directive directive = dnNode.directive;
            switch( directive ) 
            {
            	case DEF:
            		final IdentifierDefNode identifier = (IdentifierDefNode) dnNode.child(0);
            		final RegisterNode register = (RegisterNode) dnNode.child(1);
            		context.setRegisterAlias( identifier.name , register.register ); 
            		break;
                case CSEG: 
                	context.setSegment( Segment.FLASH ); 
                	break;
                case DSEG: 
                	context.setSegment( Segment.SRAM ) ; 
                	break;
                case ESEG: 
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