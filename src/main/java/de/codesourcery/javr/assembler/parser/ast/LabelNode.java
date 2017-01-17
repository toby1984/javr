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

import org.apache.commons.lang3.Validate;

import de.codesourcery.javr.assembler.ICompilationContext;
import de.codesourcery.javr.assembler.parser.Identifier;
import de.codesourcery.javr.assembler.parser.TextRegion;
import de.codesourcery.javr.assembler.symbols.Symbol;

public class LabelNode extends AbstractASTNode implements Resolvable
{
    public final Identifier identifier;
    private Symbol symbol;
    private final boolean isLocal;
    
    public LabelNode(Identifier id,boolean isLocal,TextRegion region) {
        super(region);
        Validate.notNull(id, "id must not be NULL");
        this.identifier= id;
        this.isLocal = isLocal;
    }
    
    @Override
    protected LabelNode createCopy() 
    {
        final LabelNode result = new LabelNode( this.identifier , this.isLocal , getTextRegion().createCopy() );
        result.symbol = symbol;
        return result;
    }    
    
    public boolean isLocal() {
        return isLocal;
    }
    
    public boolean isGlobal() {
        return ! isLocal;
    }
    
    @Override
    public String getAsString() {
        return isGlobal() ? identifier.value+":" : "."+identifier.value;
    }
    
    public void setSymbol(Symbol symbol) 
    {
        Validate.notNull(symbol, "symbol must not be NULL");
        this.symbol = symbol;
    }

    @Override
    public boolean resolve(ICompilationContext context) 
    {
        symbol.setValue( context.currentAddress() );
        return true;
    }
}