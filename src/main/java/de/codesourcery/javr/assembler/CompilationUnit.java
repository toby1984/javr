package de.codesourcery.javr.assembler;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.Validate;
import org.apache.log4j.Logger;

import de.codesourcery.javr.assembler.parser.ast.AST;
import de.codesourcery.javr.assembler.symbols.SymbolTable;
import de.codesourcery.javr.assembler.util.Resource;

public class CompilationUnit 
{
    @SuppressWarnings("unused")
    private static final Logger LOG = Logger.getLogger(CompilationUnit.class);
    
    private final Resource resource;
    private String contentHash;
    
    private AST ast = new AST();
    private List<CompilationUnit> dependencies = new ArrayList<>();
    private SymbolTable symbolTable;

    public CompilationUnit(Resource resource) 
    {
        Validate.notNull(resource, "resource must not be NULL");
        this.resource = resource;
        symbolTable = new SymbolTable( resource.toString() );
    }
    
    public void setSymbolTable(SymbolTable symbolTable) 
    {
        Validate.notNull(symbolTable, "symbolTable must not be NULL");
        this.symbolTable = symbolTable;
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
}