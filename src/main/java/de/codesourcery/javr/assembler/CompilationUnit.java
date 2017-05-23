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

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.Validate;
import org.apache.log4j.Logger;

import de.codesourcery.javr.assembler.parser.Parser.CompilationMessage;
import de.codesourcery.javr.assembler.parser.Parser.Severity;
import de.codesourcery.javr.assembler.parser.ast.AST;
import de.codesourcery.javr.assembler.symbols.SymbolTable;
import de.codesourcery.javr.assembler.util.Resource;

/**
 * Value object that holds a resource returning the source got compiled, the source's AST (if available) along
 * with any symbols that got discovered during parsing, other compilation units this unit depends on
 * and any compiler messages that occurred during processing of this unit.  
 *
 * @author tobias.gierke@code-sourcery.de
 */
public class CompilationUnit 
{
    private static final Logger LOG = Logger.getLogger(CompilationUnit.class);

    private final Resource resource;
    private String contentHash;

    private AST ast = new AST();
    private final List<CompilationUnit> dependencies = new ArrayList<>();
    private SymbolTable symbolTable;

    private final List<CompilationMessage> messages = new ArrayList<>();

    public CompilationUnit(Resource resource) 
    {
        this( resource , new SymbolTable( resource.toString() ) );
    }
    
    /**
     * 
     * @param resource
     * @param symbolTable
     */
    public CompilationUnit(Resource resource,SymbolTable symbolTable) 
    {
        Validate.notNull(resource, "resource must not be NULL");
        Validate.notNull(symbolTable, "symbolTablemust not be NULL");
        this.resource = resource;
        this.symbolTable = symbolTable;
    }    
    
    public void beforeCompilationStarts() 
    {
        clearMessages();
        this.symbolTable.clear();
        this.dependencies.clear();
        this.ast = new AST();
    }
    
    public void clearMessages() {
        messages.clear();
    }
    
    public boolean hasSameResourceAs(CompilationUnit other) 
    {
        return this.resource == other.resource || this.resource.pointsToSameData( other.resource );
    }

    public boolean isDirty() 
    {
        return ! resource.contentHash().equals( this.contentHash );
    }

    public void clearIsDirty() 
    {
        this.contentHash = resource.contentHash();
    }

    @Override
    public String toString() {
        return "Unit: "+super.toString()+" - "+resource;
    }

    public Resource getResource() {
        return resource;
    }

    public AST getAST() {
        return ast;
    }

    public void setAST(AST ast) {
        Validate.notNull(ast, "AST must not be NULL");
        this.ast = ast;
    }

    public void addDependency(CompilationUnit other) 
    {
        Validate.notNull(other, "compilation unit must not be NULL");
        this.dependencies.add( other );
    }

    public List<CompilationUnit> getDependencies() {
        return dependencies;
    }

    public SymbolTable getSymbolTable() {
        return symbolTable;
    }

    public void clearMessages(boolean clearDependentUnits) {
        this.messages.clear();
        if ( clearDependentUnits ) 
        {
            this.dependencies.forEach( unit -> unit.clearMessages(true) );
        }
    }

    /**
     * 
     * @param msg
     */
    public void addMessage(CompilationMessage msg) 
    {
        Validate.notNull(msg, "msg must not be NULL");

        switch( msg.severity ) 
        {
            case ERROR:
                LOG.error( msg.toString() );
                break;
            case INFO:
                LOG.info( msg.toString() );
                break;
            case WARNING:
                LOG.warn( msg.toString() );
                break;
            default:

        }
        if ( LOG.isTraceEnabled() ) 
        { 
            LOG.trace("addMessage() "+msg.message , new Exception() );
        }

        this.messages.add(msg);
    }

    public List<CompilationMessage> getMessages(boolean includeDependencies) 
    {
        final List<CompilationMessage>  result = new ArrayList<>( this.messages );
        if ( includeDependencies ) 
        {
            for ( CompilationUnit child : dependencies ) 
            {
                result.addAll( child.getMessages( true ) );
            }
        }
        return result;
    }

    public boolean hasErrors(boolean checkDependencies) 
    {
        return checkMessagesBySeverity(Severity.ERROR,checkDependencies);
    }

    public boolean checkMessagesBySeverity(Severity severity, boolean checkDependencies) 
    {
        if ( messages.stream().anyMatch( msg -> msg.severity == severity ) ) {
            return true;
        }
        if ( checkDependencies ) {
            for ( int i = 0 , len = dependencies.size() ; i < len ; i++ ) {
                if ( dependencies.get(i).checkMessagesBySeverity( severity , true ) ) {
                    return true;
                }
            }
        }
        return false;
    }    

    public boolean hasWarning(boolean checkDependencies) {
        return checkMessagesBySeverity(Severity.WARNING,checkDependencies);
    }

    public boolean hasInfo(boolean checkDependencies) {
        return checkMessagesBySeverity(Severity.INFO,checkDependencies);
    }    
}