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
package de.codesourcery.javr.assembler.symbols;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;

import org.apache.commons.lang3.Validate;
import org.apache.log4j.Logger;

import de.codesourcery.javr.assembler.CompilationUnit;
import de.codesourcery.javr.assembler.exceptions.DuplicateSymbolException;
import de.codesourcery.javr.assembler.exceptions.UnknownSymbolException;
import de.codesourcery.javr.assembler.parser.Identifier;
import de.codesourcery.javr.assembler.symbols.Symbol.Type;

/**
 * Symbol table that may be chained with another one through a parent-child relationship. 
 *
 * <p>Any symbols added to a child symbol table are recursively added to all parents as well.</p>
 * 
 * @author tobias.gierke@code-sourcery.de
 */
public class SymbolTable 
{
    private static final Logger LOG = Logger.getLogger(SymbolTable.class);
    
    public static final String GLOBAL = "<global>";
    
    private final String name;
    private SymbolTable parent;
    
    private final List<SymbolTable> children = new ArrayList<>();
    
    private final Map<Identifier,Symbol> symbols = new HashMap<>();
    
    public SymbolTable(String name) {
        Validate.notBlank(name, "name must not be NULL or blank");
        this.name = name;
    }
    
    public SymbolTable(String name,SymbolTable parent) 
    {
        this(name);
        Validate.notNull(parent, "parent must not be NULL");
        setParent( parent );
    }
    
    public SymbolTable getParent() {
    	return parent;
    }
    
    public boolean hasParent() {
        return parent != null;
    }
    
    public boolean isEmpty() {
    	return symbols.isEmpty();
    }
    
    public int size() {
    	return symbols.size();
    }
    
    /**
     * Walks the symbol table hierarchy upwards, returning the top-level symbol table.
     * 
     * @return
     */
    public SymbolTable getTopLevelTable() 
    {
    	SymbolTable result = this;
    	while ( result.hasParent() ) {
    		result = result.getParent();
    	}
    	return result;
    }
    
    public void clear() {
    	this.symbols.clear();
    }
    
    public Symbol get(Identifier name,Symbol.Type type) 
    {
        Validate.notNull(type, "type must not be NULL");
        final Symbol result = internalGet(name);
        if ( result != null ) 
        {
            if ( result.getType() != type ) {
                throw new RuntimeException("Expected symbol '"+name+"' to have type "+type+" but was "+result);
            }
            return result;
        }
        throw new UnknownSymbolException( name );
    }    
    
    public Symbol get(Identifier name) 
    {
        final Symbol result = internalGet(name);
        if ( result != null ) {
            return result;
        }
        throw new UnknownSymbolException( name );
    }

    public List<Symbol> getAllSymbolsUnsorted() 
    {
        final List<Symbol> result = new ArrayList<>();
        result.addAll( symbols.values() );
        if ( parent != null ) 
        {
            final List<Symbol> parentSymbols = parent.getAllSymbolsUnsorted();
            for ( Symbol p : parentSymbols ) 
            {
            	if ( ! symbols.keySet().contains( p.name() ) ) {
            		result.add( p );
            	}
            }
        }
        return result;
    }    
    
    public List<Symbol> getAllSymbolsSorted() 
    {
        final List<Symbol> result = getAllSymbolsUnsorted();
        Collections.sort( result , (a,b) -> a.name().value.compareTo( b.name().value ) );
        return result;
    }
    
    public Optional<Symbol> maybeGet(Identifier name) 
    {
        return Optional.ofNullable( internalGet( name ) );
    }   
    
    public Optional<Symbol> maybeGet(Identifier name,Symbol.Type type) 
    {
        final Symbol result = internalGet( name );
        if ( result != null && ! result.hasType( type ) ) {
            throw new RuntimeException("Expected symbol '"+name+"' with type "+type+" but found "+type);
        }
        return Optional.ofNullable( result );
    }        
    
    private Symbol internalGet(Identifier name) 
    {
        final Symbol result = symbols.get( name );
        if ( result == null && parent != null ) {
            return parent.internalGet( name );
        }
        return result;
    }
    
    private void internalDeclareSymbol(Symbol s) 
    {
        Validate.notNull(s, "symbol must not be NULL");
        Symbol existing = internalGet(s.name());
        if ( existing == null ) 
        {
            if ( parent != null ) {
                parent.internalDeclareSymbol( s );
            }
            putSymbol(s);
        }
    }
    
    private void putSymbol(Symbol s) 
    {
        if ( LOG.isTraceEnabled() ) 
        {
            LOG.trace("putSymbol( "+this.name+" ):  "+s+"@"+Integer.toHexString( s.hashCode() ) );
        }        
        this.symbols.put( s.name() , s );
    }
    
    public void declareSymbol(Identifier name,CompilationUnit unit) 
    {
        Validate.notNull(name, "name must not be NULL");
        Validate.notNull(unit, "compilation unit must not be NULL");
        Symbol existing = internalGet(name);
        if ( existing == null ) 
        {
            internalDeclareSymbol( new Symbol(name,Type.UNDEFINED,unit,null ) );
        }
    }
    
    public boolean removeIf(Predicate<Symbol> predicate) 
    {
        boolean result = false;
        for (Iterator<Symbol> it = this.symbols.values().iterator(); it.hasNext();) 
        {
            final Symbol entry = it.next();
            if ( predicate.test( entry ) ) {
                it.remove();
                result = true;
            }
        }
        if ( parent != null ) {
            result |= parent.removeIf( predicate );
        }
        return result;
    }
    
    public boolean isDefined(Identifier name) 
    {
        Validate.notNull(name, "name must not be NULL");
        final Symbol existing = internalGet( name );
        return existing != null && existing.isDefined();
    }
    
    public boolean isDeclared(Identifier name) 
    {
        Validate.notNull(name, "name must not be NULL");
        return internalGet( name ) != null;
    }    
    
    public void defineSymbol(Symbol symbol) throws DuplicateSymbolException
    {
        Validate.notNull(symbol, "symbol must not be NULL");
        if ( ! symbol.hasType(Type.PREPROCESSOR_MACRO ) ) {
            Validate.notNull(symbol.getNode(), "symbol must not have a NULL node");
        }
        Symbol existing = internalGet( symbol.name() );
        if ( existing != null && ! existing.hasType( Type.UNDEFINED ) ) {
            throw new DuplicateSymbolException( symbol , existing );
        }
        if ( parent != null ) {
            parent.defineSymbol( symbol );
        }
        putSymbol(symbol);
    }
    
    @Override
    public String toString() 
    {
        return name+" ["+super.toString()+","+this.symbols.size()+" symbols]";
    }

	public void setParent(SymbolTable symbolTable) {
		Validate.notNull(symbolTable, "symbolTable must not be NULL");
		
		this.parent = symbolTable;
		this.parent.children.add( this );
		
        IdentityHashMap<SymbolTable,Integer> chain = new IdentityHashMap<>();
        SymbolTable current = this;
        while ( current != null ) {
        	if ( chain.containsKey( current ) ) {
        		throw new IllegalArgumentException("Symbol table parents form a cycle");
        	}
        	chain.put( current , Integer.valueOf(0) );
        	current = current.getParent();
        }

	}
	
	/**
	 * DEBUGGING ONLY.
	 * @return
	 * @deprecated DEBUGGING ONLY
	 */
	@Deprecated
    public Map<Identifier,Symbol> symbols() {
        return this.symbols;
    }
	
	/**
	 * Removes a symbol from this table and 
	 * all its children.
	 * 
	 * @param s
	 */
	public void removeSymbol(Symbol s) 
	{
	    this.symbols.remove( s.name() );
	    children.forEach( child -> child.removeSymbol( s ) );
	}
	
	/**
	 * Visit all symbols in this table.
	 * 
	 * @param visitor visitor that accepts a symbol and returns either <code>true</code> to continue iteration or <code>false</code> to stop iteration and return
	 */
	public void visitSymbols(Function<Symbol,Boolean> visitor) 
	{
	    Collection<Symbol> values = this.symbols.values();
	    for (Iterator<Symbol>  it = values.iterator(); it.hasNext();) 
	    {
            final Symbol symbol = (Symbol) it.next();
            if ( ! Boolean.TRUE.equals( visitor.apply( symbol ) ) ) {
                return;
            }
        }
	}
}