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
package de.codesourcery.javr.assembler.parser.ast;

import java.util.ArrayList;
import java.util.List;

import de.codesourcery.javr.assembler.Address;
import de.codesourcery.javr.assembler.ICompilationContext;

public class StatementNode extends AbstractASTNode implements Resolvable {

    private LabelNode getLabelNode() 
    {
        for ( ASTNode child : children() ) {
            if ( child instanceof LabelNode) {
                return (LabelNode) child;
            }
        }
        return null;
    }
    
    @Override
    protected StatementNode createCopy() {
        return new StatementNode();
    }    
    
    private static boolean continueLabelSearch(StatementNode node) 
    {
        return node.hasNoChildren() || ( node.childCount() == 1 && node.child(0) instanceof CommentNode); 
    }
    
    public boolean hasLabel() 
    {
        return ! findLabels().isEmpty();
    }
    
    public boolean isLabelLineOnly() 
    {
        return hasOnlyNode( LabelNode.class );
    }
    
    private <T extends ASTNode> boolean hasOnlyNode(Class<T> clazz) {
        if ( hasNoChildren() ) {
            return false;
        }
        for ( int i = 0 , len = childCount() ; i < len ; i++ ) 
        {
            if ( ! clazz.isAssignableFrom( child(i).getClass() ) ) 
            {
                return false;
            }
        }
        return true;
    }
    
    public boolean isCommentOnlyLine() 
    {
        return hasOnlyNode( CommentNode.class );
    }
    
    public boolean hasPreprocessorNode() 
    {
        for ( int i = 0 , len = childCount() ; i < len ; i++ ) 
        {
            if ( child(i) instanceof PreprocessorNode ) {
                return true;
            }
        }
        return false;
    }    
    
    public boolean hasDirective() 
    {
        for ( int i = 0 , len = childCount() ; i < len ; i++ ) 
        {
            if ( child(i) instanceof DirectiveNode ) {
                return true;
            }
        }
        return false;
    }
    
    public List<LabelNode> findLabels() 
    {
        final List<LabelNode> results = new ArrayList<>();
        LabelNode result = getLabelNode();
        if ( result != null ) {
            results.add( result );
        }
        if ( hasParent() ) 
        {
            int previous = getParent().indexOf( this )-1;
            while ( result == null && previous >= 0 ) 
            {
                final StatementNode previousStatement = (StatementNode) getParent().child( previous );
                if ( ! continueLabelSearch(previousStatement) ) {
                    break;
                }
                result = previousStatement.getLabelNode();
                if ( result != null ) {
                    results.add( result ); 
                }
                previous--;
            }
        }
        return results;
    }
    
    private ASTNode findInstruction() 
    {
        ASTNode result = null;
        for ( ASTNode child : children() ) 
        {
            if ( child.hasMemoryLocation() ) 
            {
                if ( result != null ) 
                {
                    // currently this cannot happen
                    throw new IllegalStateException("Statement is associated with more than one memory location ??");
                }
                result = child;
            }
        }
        return result;
    }
    
    @Override
    public boolean hasMemoryLocation() {
        return findInstruction() != null;
    }
    
    @Override
    public Address getMemoryLocation() throws IllegalStateException 
    {
        final ASTNode ins = findInstruction();
        if ( ins == null ) {
            throw new IllegalStateException( "This statement is not associated with a memory location" );
        }
        return ins.getMemoryLocation();
    }
    
    @Override
    public boolean assignMemoryLocation(Address address) 
    {
        final ASTNode ins = findInstruction();
        if ( ins == null ) {
            throw new IllegalStateException( "This statement is not associated with a memory location" );
        }
        return ins.assignMemoryLocation( address );
    }

    @Override
    public boolean resolve(ICompilationContext context) 
    {
        boolean result = true;
        for ( ASTNode child : children() ) 
        {
            if ( child instanceof Resolvable) 
            {
                result &= ((Resolvable) child).resolve(context);
            }
        }
        return result;
    }    
}