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
package de.codesourcery.javr.assembler.parser.ast;

import de.codesourcery.javr.assembler.parser.TextRegion;

public class CommentNode extends AbstractASTNode {

    public final String value;
    
    public CommentNode(String string, TextRegion region) 
    {
        super(region);
        this.value = string;
    }
    
    @Override
    public String getAsString() {
        return "; "+value;
    }
    
    @Override
    protected CommentNode createCopy() 
    {
        return new CommentNode( this.value , getTextRegion().createCopy() );
    }    
}