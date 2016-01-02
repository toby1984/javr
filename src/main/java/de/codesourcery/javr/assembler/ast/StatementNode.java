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
package de.codesourcery.javr.assembler.ast;

import java.util.ArrayList;
import java.util.List;

public class StatementNode extends ASTNode {

    private LabelNode getLabelNode() 
    {
        for ( ASTNode child : children ) {
            if ( child instanceof LabelNode) {
                return (LabelNode) child;
            }
        }
        return null;
    }
    
    private static boolean continueLabelSearch(StatementNode node) 
    {
        return node.hasNoChildren() || ( node.childCount() == 1 && node.child(0) instanceof CommentNode); 
    }
    
    public boolean hasLabel() 
    {
        return ! findLabels().isEmpty();
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
}