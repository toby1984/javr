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

import org.apache.commons.lang3.Validate;

import de.codesourcery.javr.assembler.ICompilationContext;
import de.codesourcery.javr.assembler.parser.Identifier;
import de.codesourcery.javr.assembler.parser.TextRegion;
import de.codesourcery.javr.assembler.symbols.Symbol;
import de.codesourcery.javr.assembler.symbols.Symbol.Type;
import de.codesourcery.javr.assembler.symbols.SymbolTable;

public class IdentifierNode extends AbstractASTNode implements IValueNode,Resolvable {

    public Identifier name;
    private Symbol symbol;
    
    public IdentifierNode(Identifier id,TextRegion region) {
        super(region);
        Validate.notNull(id, "id must not be NULL");
        this.name = id;
    }
    
    @Override
    public String getAsString() {
        return name.value;
    }
    
    public boolean refersToAddressSymbol(SymbolTable table) 
    {
        final Symbol symbol = table.get( name );
        return symbol.hasType( Type.ADDRESS_LABEL ); 
    }
    
    @Override
    public Object getValue()
    {
        return symbol != null ? symbol.getValue() : null;
    }
    
    @Override
    protected IdentifierNode createCopy() 
    {
        return new IdentifierNode(this.name , getTextRegion().createCopy() );
    }     
    
    public void setName(Identifier identifier) {
        Validate.notNull(identifier, "identifier must not be NULL");
        this.name = identifier;
    }
    
    @Override
    public String toString() {
        return "Identifier[ "+name+" ] = "+symbol;
    }
    
    public void setSymbol(Symbol symbol) {
        Validate.notNull(symbol, "symbol must not be NULL");
        this.symbol = symbol;
    }
    
    /**
     * Returns the symbol associated with this identifier node or
     * throws an exception if this node is not associated with any symbol yet.
     * 
     * @param in
     * @return
     */
    public Symbol safeGetSymbol() 
    {
        if ( symbol == null ) {
            throw new RuntimeException("Node "+this+" has NULL symbol ?");
        }
        return symbol;
    }
    
    public Symbol getSymbol() {
        return symbol;
    }
    
    @Override
    public boolean resolve(ICompilationContext context) 
    {
        // always resolve again, this is (ab-)used by the relocation 
        // addend calculation
        // if ( symbol == null ) {
            symbol = context.currentSymbolTable().maybeGet( name ).orElse( null );
            if ( symbol != null ) {
                symbol.markAsReferenced();
            }
        // }
        return symbol != null;
    }
}