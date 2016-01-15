package de.codesourcery.javr.assembler;

import java.io.IOException;
import java.util.IdentityHashMap;
import java.util.Stack;

import org.apache.commons.lang3.Validate;

import de.codesourcery.javr.assembler.arch.IArchitecture;
import de.codesourcery.javr.assembler.parser.Parser.CompilationMessage;
import de.codesourcery.javr.assembler.parser.ast.ASTNode;
import de.codesourcery.javr.assembler.symbols.SymbolTable;
import de.codesourcery.javr.assembler.util.Resource;
import de.codesourcery.javr.ui.IConfig;

public final class CompilationContext implements ICompilationContext 
{
    private final SymbolTable globalSymbolTable;
    
    private final CompilerSettings compilerSettings;
    
    private final IObjectCodeWriter objectCodeWriter;
    
    private final ResourceFactory resourceFactory;
    
    private final IConfig config;        
    
    // stack to keep track of the current compilation unit while processing #include directives
    private final Stack<CompilationUnit> compilationUnits = new Stack<>();
    
    // the compilation unit that was passed to start the compilation process
    private final CompilationUnit rootCompilationUnit;
    
    // performance optimization to avoid calling Stack#peek() all the time,
    // updated each time a compilation unit is pushed to the stack/popped from the stack
    private CompilationUnit currentCompilationUnit;
    
    public CompilationContext(CompilationUnit unit,
            IObjectCodeWriter objectCodeWriter, 
            ResourceFactory resourceFactory,
            SymbolTable globalSymbolTable,
            CompilerSettings compilerSettings,
            IConfig config) 
    {
        Validate.notNull(unit, "unit must not be NULL");
        Validate.notNull(objectCodeWriter, "objectCodeWriter must not be NULL");
        Validate.notNull(resourceFactory, "resourceFactory must not be NULL");
        Validate.notNull(globalSymbolTable , "globalSymbolTable must not be NULL");
        Validate.notNull(compilerSettings,"compilerSettings must not be NULL");
        Validate.notNull(config,"config must not be NULL");
        this.resourceFactory = resourceFactory;
        this.rootCompilationUnit = unit;
        this.objectCodeWriter = objectCodeWriter;
        this.globalSymbolTable = globalSymbolTable;
        this.compilerSettings = compilerSettings;
        this.config = config;
        pushCompilationUnit( unit );
    }
    
    @Override
    public CompilationUnit getOrCreateCompilationUnit(Resource res) 
    {
        if ( currentCompilationUnit().getResource().equals( res ) ) {
            return currentCompilationUnit();
        }
        final Stack<CompilationUnit> units = new Stack<>();
        units.push( currentCompilationUnit() );
        while ( ! units.isEmpty() ) 
        {
            CompilationUnit u = units.pop();
            if ( res.equals( u.getResource() ) ) {
                return u;
            }
            units.addAll( u.getDependencies() );
        }
        return new CompilationUnit( res );
    }
    
    @Override
    public boolean pushCompilationUnit(CompilationUnit newUnit) 
    {
        Validate.notNull(newUnit, "unit must not be NULL");
        if ( newUnit == currentCompilationUnit() ) {
            throw new IllegalArgumentException("Cannot push current compilation unit");
        }
        
        /*
         * Check for circular dependencies BEFORE pushing the new unit to the stack
         */
        final Stack<CompilationUnit> stack = new Stack<>();
        if ( currentCompilationUnit != null ) { // NULL when this method is called by the constructor
            stack.push( currentCompilationUnit() );
        }
        
        final IdentityHashMap<CompilationUnit, Boolean> unique = new IdentityHashMap<>();
        
        while ( ! stack.isEmpty() ) 
        {
            final CompilationUnit current = stack.pop();
            
            if ( unique.containsKey( current ) ) 
            {
                error("Circular includes detected: "+unique.keySet(),newUnit.getAST());
                return false;
            }
            unique.put( current, Boolean.TRUE );
            stack.addAll( current.getDependencies() );
            if ( current == currentCompilationUnit() ) // fake adding the new compilation unit
            {
                stack.add( newUnit );
            }
        }
        
        if ( currentCompilationUnit != null ) { // NULL when this method is called by the constructor
            currentCompilationUnit().addDependency( newUnit );
        }
        compilationUnits.add( newUnit );
        this.currentCompilationUnit = newUnit;
        return true;
    }
    
    @Override
    public ResourceFactory getResourceFactory() {
        return resourceFactory;
    }
    
    @Override
    public void popCompilationUnit() 
    {
        compilationUnits.pop();
        this.currentCompilationUnit = compilationUnits.isEmpty() ? null : compilationUnits.peek();
    }        
    
    @Override
    public ICompilationSettings getCompilationSettings() {
        return compilerSettings;
    }

    public void beforePhase() throws IOException
    {
        compilationUnits.clear();
        currentCompilationUnit = null;
        pushCompilationUnit( rootCompilationUnit );
        objectCodeWriter.reset();
        objectCodeWriter.setCurrentSegment( Segment.FLASH );
    }

    @Override
    public SymbolTable currentSymbolTable() {
        return currentCompilationUnit().getSymbolTable();
    }

    @Override
    public SymbolTable globalSymbolTable() {
        return globalSymbolTable;
    }

    @Override
    public Address currentAddress() 
    {
        return Address.byteAddress( currentSegment() , currentOffset() );
    }

    @Override
    public int currentOffset() 
    {
        return objectCodeWriter.getCurrentByteAddress();
    }

    @Override
    public Segment currentSegment() {
        return objectCodeWriter.getCurrentSegment();
    }

    @Override
    public void setSegment(Segment s) {
        objectCodeWriter.setCurrentSegment( s );
    }

    @Override
    public void writeByte(int value) 
    {
        objectCodeWriter.writeByte(value);
    }

    @Override
    public void writeWord(int value) 
    {
        objectCodeWriter.writeWord( value );
    }

    @Override
    public void error(String message, ASTNode node) {
        message( CompilationMessage.error(message,node ) );
    }

    @Override
    public void message(CompilationMessage msg) {
        currentCompilationUnit().addMessage( msg );
    }

    @Override
    public IArchitecture getArchitecture() {
        return config.getArchitecture();
    }

    @Override
    public void allocateByte() 
    {
        allocateBytes(1);
    }

    public void allocateBytes(int bytes)
    {
        objectCodeWriter.allocateBytes( bytes );
    }

    @Override
    public void allocateWord() {
        allocateBytes(2);
    }

    @Override
    public CompilationUnit currentCompilationUnit() {
        return currentCompilationUnit;
    }
}