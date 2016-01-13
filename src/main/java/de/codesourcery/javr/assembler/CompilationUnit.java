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

public class CompilationUnit 
{
    private static final Logger LOG = Logger.getLogger(CompilationUnit.class);
    
    private final Resource resource;
    private String contentHash;
    
    private AST ast = new AST();
    private List<CompilationUnit> dependencies = new ArrayList<>();
    private SymbolTable symbolTable;

   private final List<CompilationMessage> messages = new ArrayList<>();
    
    public CompilationUnit(Resource resource) 
    {
        this( resource , new SymbolTable( resource.toString() ) );
    }
    
    public CompilationUnit(Resource resource,SymbolTable symbolTable) 
    {
        Validate.notNull(resource, "resource must not be NULL");
        Validate.notNull(symbolTable, "symbolTablemust not be NULL");
        this.resource = resource;
        this.symbolTable = symbolTable;
    }    
    
    public void beforeCompilationStarts(SymbolTable parentSymbolTable) 
    {
    	this.messages.clear();
    	this.symbolTable.clear();
    	this.symbolTable.setParent( parentSymbolTable );
    	this.dependencies.clear();
    	this.messages.clear();
    }
    
    public boolean hasSameResourceAs(CompilationUnit other) 
    {
        return this.resource.pointsToSameData( other.resource );
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
        return "Unit: "+resource;
    }
    
    public Resource getResource() {
        return resource;
    }
    
    public AST getAST() {
        return ast;
    }
    
    public void setAst(AST ast) {
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
    	if ( includeDependencies ) {
    		
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