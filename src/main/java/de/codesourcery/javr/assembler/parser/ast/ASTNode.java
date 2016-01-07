package de.codesourcery.javr.assembler.parser.ast;

import java.util.Collection;
import java.util.List;

import de.codesourcery.javr.assembler.Address;
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
     */
    void markAsSkip();

    List<ASTNode> children();

    /**
     * Check whether conditional compilation marked this subtree as to be skipped
     * @return
     */
    boolean isSkip();

    ASTNode createCopy(boolean deep);

    void setRegion(TextRegion region);

    ASTNode getParent();

    boolean hasParent();

    boolean hasNoParent();

    void setParent(ASTNode parent);

    int childCount();

    void visitBreadthFirst(IASTVisitor n);
    
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

    TextRegion getTextRegion();

    void insertChild(int index, ASTNode child);

    void addChild(ASTNode child);

    void addChildren(Collection<? extends ASTNode> children);

    ASTNode firstChild();

    ASTNode child(int index);

    boolean hasChildren();

    boolean hasNoChildren();

    int indexOf(ASTNode child);

    String getAsString();

    String toString();

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
