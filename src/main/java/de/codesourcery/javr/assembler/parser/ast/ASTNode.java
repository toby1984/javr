package de.codesourcery.javr.assembler.parser.ast;

import java.util.Collection;
import java.util.List;
import java.util.function.Predicate;

import de.codesourcery.javr.assembler.Address;
import de.codesourcery.javr.assembler.CompilationUnit;
import de.codesourcery.javr.assembler.parser.TextRegion;
import de.codesourcery.javr.assembler.parser.ast.AbstractASTNode.IterationContext;

public interface ASTNode {

    @FunctionalInterface
    public interface IASTVisitor {
        
        public void visit(ASTNode node,IIterationContext ctx);
    }
    
    public interface IIterationContext 
    {
        public void stop();
        public void dontGoDeeper();
    }    
    /**
     * Marks this subtree as to be skipped because of conditional compilation.
     * This is also used to make all later compilation phases ignore preprocessor directives so these
     * don't get evaluated more than once.
     */
    public void markAsSkip();

    public List<ASTNode> children();
    
    public boolean anyMatchingParent(Predicate<ASTNode> predicate);
    
    public ASTNode findMatchingParent(Predicate<ASTNode> predicate);
    
    public void replaceWith(ASTNode other);
    
    public void replaceChild(ASTNode child,  ASTNode newNode);
    
    /**
     * Returns the compilation unit this node belongs to.
     *  
     * @return
     */
    public CompilationUnit getCompilationUnit();

    /**
     * Check whether conditional compilation marked this subtree as to be skipped
     * @return
     */
    public boolean isSkip();

    public ASTNode createCopy(boolean deep);

    public void setRegion(TextRegion region);

    public ASTNode getParent();

    public boolean hasParent();

    public boolean hasNoParent();

    public void setParent(ASTNode parent);

    public int childCount();

    public void visitBreadthFirst(IASTVisitor n);
    
    public StatementNode getStatement();

    /**
     * DO NOT USE - PART OF INTERNAL API.     
     * @param n
     * @param ctx
     * @Deprecated
     */
    @Deprecated
    public void visitBreadthFirst(IASTVisitor n,IterationContext ctx);      

    void visitDepthFirst(IASTVisitor n);
    
    /**
     * DO NOT USE - PART OF INTERNAL API.
     * @param n
     * @param ctx
     * @Deprecated
     */
    @Deprecated
    public void visitDepthFirst(IASTVisitor n,IterationContext ctx);    

    public TextRegion getTextRegion();

    public void insertChild(int index, ASTNode child);

    public void addChild(ASTNode child);

    public void addChildren(Collection<? extends ASTNode> children);

    public ASTNode firstChild();

    public ASTNode child(int index);

    public boolean hasChildren();

    public boolean hasNoChildren();

    public int indexOf(ASTNode child);

    public String getAsString();

    public String toString();

    /**
     * Returns whether this AST node is associated with a location in memory.
     * 
     * @return
     */
    public boolean hasMemoryLocation();
    
    /**
     * Returns the memory address this AST node is associated with.
     * @return
     * @throws IllegalStateException if this AST node cannot be associated with a memory location.
     * @see #hasMemoryLocation()       
     */
    public Address getMemoryLocation() throws IllegalStateException;
    
    /**
     * Assigns a memory location to this AST node.
     * 
     * @param address
     * @return
     * @throws IllegalStateException if this AST node cannot be associated with a memory location.
     * @see #hasMemoryLocation()     
     */
    public boolean assignMemoryLocation(Address address);
    
    /**
     * Returns the number of bytes in memory that are associated with this AST node and all of its children.
     * 
     * @return
     * @throws IllegalStateException
     */
    public int getSizeInBytes() throws IllegalStateException;
}