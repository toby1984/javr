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

import java.util.Optional;

import org.apache.commons.lang3.Validate;

import de.codesourcery.javr.assembler.ICompilationContext;
import de.codesourcery.javr.assembler.parser.Identifier;
import de.codesourcery.javr.assembler.parser.TextRegion;
import de.codesourcery.javr.assembler.symbols.Symbol;

public class IdentifierNode extends ASTNode implements IValueNode {

    public final Identifier name;

    private Object resolvedValue;
    
    public IdentifierNode(Identifier id,TextRegion region) {
        super(region);
        Validate.notNull(id, "id must not be NULL");
        this.name = id;
    }
    
    @Override
    public String getAsString() {
        return name.value;
    }
    
    public boolean resolveValue(ICompilationContext context) 
    {
        final Optional<Symbol> symbol = context.currentSymbolTable().maybeGet( name );
        final Object value = symbol.isPresent() ? symbol.get().getValue() : null;
        this.resolvedValue = value;
        return value != null;
    }

    @Override
    public Object getValue()
    {
        return resolvedValue;
    }
    
    @Override
    protected IdentifierNode createCopy() 
    {
        return new IdentifierNode(this.name , getTextRegion().createCopy() );
    }     
    
    @Override
    public String toString() {
        return "Identifier[ "+name+" ] = "+resolvedValue;
    }
}