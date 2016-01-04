package de.codesourcery.javr.assembler;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.Validate;

import de.codesourcery.javr.assembler.parser.ast.AST;
import de.codesourcery.javr.assembler.symbols.SymbolTable;
import de.codesourcery.javr.assembler.util.Resource;

public class CompilationUnit 
{
    private final Resource resource;
    private String contentHash;
    
    private AST ast;
    private List<CompilationUnit> dependencies = new ArrayList<>();
    private final SymbolTable symbolTable = new SymbolTable();

    public CompilationUnit(Resource resource) 
    {
        Validate.notNull(resource, "resource must not be NULL");
        this.resource = resource;
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