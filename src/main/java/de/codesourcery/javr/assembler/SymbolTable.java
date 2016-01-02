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
package de.codesourcery.javr.assembler;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.apache.commons.lang3.Validate;

import de.codesourcery.javr.assembler.exceptions.DuplicateSymbolException;
import de.codesourcery.javr.assembler.exceptions.UnknownSymbolException;

public class SymbolTable 
{
    private SymbolTable parent;
    
    private final Map<Identifier,Symbol<?>> symbols = new HashMap<>();
    
    public SymbolTable() {
    }
    
    public SymbolTable(SymbolTable parent) {
        Validate.notNull(parent, "parent must not be NULL");
        this.parent = parent;
    }
    
    public Symbol<?> get(Identifier name) 
    {
        final Symbol<?> result = symbols.get( name );
        if ( result == null && parent != null ) {
            return parent.get( name );
        }
        if ( result == null ) {
            throw new UnknownSymbolException( name );
        }
        return result;
    }
    
    public Optional<Symbol<?>> maybeGet(Identifier name) 
    {
        final Symbol<?> result = symbols.get( name );
        if ( result == null && parent != null ) {
            return parent.maybeGet( name );
        }
        if ( result == null ) {
            return Optional.empty();
        }
        return Optional.of(result);
    }    
    
    public void declareSymbol(Identifier name) 
    {
        Validate.notNull(name, "name must not be NULL");
        Symbol<?> existing = symbols.get( name );
        if ( existing != null ) {
            this.symbols.put( name , null );
        }
    }
    
    public boolean isDefined(Identifier name) 
    {
        Validate.notNull(name, "name must not be NULL");
        return symbols.get(name) != null;
    }
    
    public void defineSymbol(Symbol<?> symbol) 
    {
        Validate.notNull(symbol, "symbol must not be NULL");
        Symbol<?> existing = symbols.get( symbol.name() );
        if ( existing != null ) {
            throw new DuplicateSymbolException( symbol , existing );
        }
        symbols.put( symbol.name() , symbol );
    }    
}
