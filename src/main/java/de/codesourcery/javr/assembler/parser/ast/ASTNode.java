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
package de.codesourcery.javr.assembler.parser.ast;

import java.util.Collection;
import java.util.List;
import java.util.function.Predicate;

import de.codesourcery.javr.assembler.Address;
import de.codesourcery.javr.assembler.CompilationUnit;
import de.codesourcery.javr.assembler.parser.TextRegion;
import de.codesourcery.javr.assembler.parser.ast.AbstractASTNode.IterationContext;

/**
 * AST tree node.
 *
 * @author tobias.gierke@code-sourcery.de
 */
public interface ASTNode 
{
    @FunctionalInterface
    public interface IASTVisitor {
        
        public void visit(ASTNode node,IIterationContext ctx);
    }
    
    /**
     * Iteration context used to control depth-first/breadth-first traversal.
     *
     * @author tobias.gierke@code-sourcery.de
     */
    public interface IIterationContext 
    {
        public void stop();
        
        public void dontGoDeeper();
    }    
    
    /**
     * Returns all children of this node.
     * @return
     */
    public List<ASTNode> children();
    
    /**
     * Traverses the AST upwards, looking for any parent node that 
     * satisfies a given predicate.
     * 
     * Note that the node this method is invoked on is <b>not</b>
     * considered.
     * 
     * @param predicate
     * @return
     * 
     * @see #findMatchingParent(Predicate)
     */
    public boolean anyMatchingParent(Predicate<ASTNode> predicate);
    
    /**
     * Traverses the AST upwards and returns the first node 
     * that satisfies a given predicate.
     * 
     * Note that the node this method is invoked on is <b>not</b>
     * considered.
     *  
     * @param predicate
     * @return
     */
    public ASTNode findMatchingParent(Predicate<ASTNode> predicate);
    
    /**
     * Replaces this node with another.
     * 
     * @param other
     * @throws IllegalStateException when invoked on a node that has no parent
     */
    public void replaceWith(ASTNode other);
    
    /**
     * Replace a direct child of this node with another instance.
     * 
     * @param child
     * @param newNode
     * @throws IllegalArgumentException if <code>child</code> is no child node of this instance
     */
    public void replaceChild(ASTNode child,  ASTNode newNode);
    
    /**
     * Returns the compilation unit this node belongs to.
     *  
     * @return compilation unit, may be <code>null</code>
     */
    public CompilationUnit getCompilationUnit();

    /**
     * Returns a shallow or deep copy of the subtree starting at this node.
     *  
     * @param deep
     * @return
     */
    public ASTNode createCopy(boolean deep);

    /**
     * Sets the text region of the source code that is covered by this node (disregarding
     * the text regions of any children this node may have).
     * 
     * @param region
     */
    public void setRegion(TextRegion region);

    /**
     * Returns the parent node.
     * 
     * @return
     */
    public ASTNode getParent();

    /**
     * Returns whether this node has a parent node.
     * 
     * @return
     */
    public boolean hasParent();

    /**
     * Returns whether this node has no parent node.
     * 
     * @return
     */
    public boolean hasNoParent();

    /**
     * Sets this node's parent.
     * 
     * @param parent
     * @see #hasNoParent()
     * @see #hasParent()
     */
    public void setParent(ASTNode parent);

    /**
     * Returns the number of direct children this node has.
     *  
     * @return
     * @see #hasChildren()
     * @see #hasNoChildren()
     */
    public int childCount();

    /**
     * Performs a breadth-first traversal of the AST starting with this node.
     * 
     * @param n
     */
    public void visitBreadthFirst(IASTVisitor n);
    
    /**
     * Returns the statement this node belongs to.
     * 
     * @return
     */
    public StatementNode getStatement();

    /**
     * DO NOT USE - PART OF INTERNAL API.     
     * @param n
     * @param ctx
     * @Deprecated
     */
    @Deprecated
    public void visitBreadthFirst(IASTVisitor n,IterationContext ctx);      

    /**
     * Performs a depth-first traversal of the subtree starting at this node.
     * 
     * @param n
     */
    void visitDepthFirst(IASTVisitor n);
    
    /**
     * DO NOT USE - PART OF INTERNAL API.
     * @param n
     * @param ctx
     * @Deprecated
     */
    @Deprecated
    public void visitDepthFirst(IASTVisitor n,IterationContext ctx);    

    /**
     * Returns the region of the source code covered by this node (disregarding
     * the text region covered by any of this node's children).
     * 
     * @return
     */
    public TextRegion getTextRegion();

    /**
     * Insert a direct child node at a specific position.
     * 
     * @param index
     * @param child
     */
    public void insertChild(int index, ASTNode child);

    /**
     * Add a child node.
     * 
     * @param child
     */
    public void addChild(ASTNode child);

    /**
     * Add child nodes.
     * 
     * @param child
     */
    public void addChildren(Collection<? extends ASTNode> children);

    /**
     * Returns this node's first direct child.
     * @return
     */
    public ASTNode firstChild();

    /**
     * Returns the direct child for a given index.
     * @param index
     * @return
     */
    public ASTNode child(int index);

    /**
     * Returns whether this node has at least one direct child.
     * 
     * @return
     */
    public boolean hasChildren();

    /**
     * Returns whether this node has at no direct children.
     * 
     * @return
     */    
    public boolean hasNoChildren();

    /**
     * Returns the index of a given direct child.
     * 
     * @param child
     * @return
     */
    public int indexOf(ASTNode child);

    /**
     * Debug.
     * 
     * @return
     * @deprecated debug only.
     */
    @Deprecated
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