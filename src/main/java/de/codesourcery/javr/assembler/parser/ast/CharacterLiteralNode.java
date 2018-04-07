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

public class CharacterLiteralNode extends AbstractASTNode implements IValueNode {

    public final char value;

    public CharacterLiteralNode(char value, TextRegion region) {
        super(region);
        this.value = value;
    }

    @Override
    public Integer getValue() 
    {
        return Integer.valueOf( value );
    }

    @Override
    protected CharacterLiteralNode createCopy() 
    {
        return new CharacterLiteralNode( this.value , getTextRegion().createCopy() );
    }      
}
