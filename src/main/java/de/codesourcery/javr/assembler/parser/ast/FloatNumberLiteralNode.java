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

public class FloatNumberLiteralNode extends AbstractASTNode implements IValueNode
{
    private final double value;

    public FloatNumberLiteralNode(double value, TextRegion region) {
        super(region);
        this.value = value;
    }

    @Override
    protected FloatNumberLiteralNode createCopy()
    {
        return new FloatNumberLiteralNode( this.value , getTextRegion().createCopy() );
    }

    @Override
    public Double getValue()
    {
        return value;
    }

    @Override
    public String getAsString()
    {
        return Double.toString( this.value );
    }
}

