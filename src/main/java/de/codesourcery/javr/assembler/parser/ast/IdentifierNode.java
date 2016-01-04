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

import de.codesourcery.javr.assembler.Address;
import de.codesourcery.javr.assembler.ICompilationContext;
import de.codesourcery.javr.assembler.ICompilationContext.Phase;
import de.codesourcery.javr.assembler.parser.Identifier;
import de.codesourcery.javr.assembler.parser.TextRegion;
import de.codesourcery.javr.assembler.parser.Parser.CompilationMessage;
import de.codesourcery.javr.assembler.parser.Parser.Severity;
import de.codesourcery.javr.assembler.symbols.LabelSymbol;
import de.codesourcery.javr.assembler.symbols.Symbol;
import de.codesourcery.javr.assembler.symbols.Symbol.Type;

public class IdentifierNode extends ASTNode implements IValueNode {

    public final Identifier value;

    private Address resolvedValue;
    
    public IdentifierNode(Identifier id,TextRegion region) {
        super(region);
        Validate.notNull(id, "id must not be NULL");
        this.value = id;
    }
    
    @Override
    public void compile(ICompilationContext ctx) 
    {
        if ( ctx.isInPhase( Phase.RESOLVE_SYMBOLS ) ) 
        {
            if ( ! ctx.getSymbolTable().isDefined( value ) ) 
            {
                ctx.message( new CompilationMessage( Severity.ERROR , "Unknown symbol: '"+value.value+"'" , this ) );
            } 
            resolveValue( ctx );
        } 
    }
    
    @Override
    public String getAsString() {
        return value.value;
    }

    @Override
    public void resolveValue(ICompilationContext context) 
    {
        final Optional<Symbol<?>> symbol = context.getSymbolTable().maybeGet( this.value );
        if ( symbol.isPresent() && symbol.get().hasType( Type.LABEL ) ) 
        {
            final LabelSymbol label = (LabelSymbol) symbol.get();
            resolvedValue = label.getAddress();
        } else {
            resolvedValue = null;
        }
    }    
    @Override
    public Address getValue()
    {
        return resolvedValue;
    }
}