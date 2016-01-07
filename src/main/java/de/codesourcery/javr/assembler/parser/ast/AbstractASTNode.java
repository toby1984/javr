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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.apache.commons.lang3.Validate;

import de.codesourcery.javr.assembler.Address;
import de.codesourcery.javr.assembler.parser.TextRegion;

public abstract class AbstractASTNode implements ASTNode 
{
    private final List<ASTNode> children=new ArrayList<>();
    
    private boolean skip;
    private TextRegion region;
    private ASTNode parent;
    
    public AbstractASTNode() {
    }
    
    /* (non-Javadoc)
     * @see de.codesourcery.javr.assembler.parser.ast.ASTNodeIf#markAsSkip()
     */
    @Override
    public final void markAsSkip() 
    {
        this.skip = true;
        for ( ASTNode child : children ) 
        {
            child.markAsSkip();
        }
    }
    
    @Override
    public final StatementNode getStatement() 
    {
        if ( this instanceof StatementNode) {
            return (StatementNode) this;
        }
        ASTNode current = getParent();
        while ( current != null && !(current instanceof StatementNode) ) {
            current = current.getParent();
        }
        return (StatementNode) current;
    }
    
    /* (non-Javadoc)
     * @see de.codesourcery.javr.assembler.parser.ast.ASTNodeIf#children()
     */
    @Override
    public final List<ASTNode> children() {
        return children;
    }
    
    /* (non-Javadoc)
     * @see de.codesourcery.javr.assembler.parser.ast.ASTNodeIf#isSkip()
     */
    @Override
    public final boolean isSkip() {
        return skip;
    }
    
    /* (non-Javadoc)
     * @see de.codesourcery.javr.assembler.parser.ast.ASTNodeIf#createCopy(boolean)
     */
    @Override
    public final ASTNode createCopy(boolean deep) 
    {
        final ASTNode result = createCopy();
        if ( this.skip ) {
            result.markAsSkip();
        }
        
        if ( deep ) 
        {
            for ( ASTNode child : children ) 
            {
                result.addChild( child.createCopy( true ) );
            }
        }
        return result;
    }
    
    protected abstract ASTNode createCopy();
    
    public AbstractASTNode(TextRegion region) 
    {
        Validate.notNull(region, "region must not be NULL");
        this.region = region;
    }
    
    /* (non-Javadoc)
     * @see de.codesourcery.javr.assembler.parser.ast.ASTNodeIf#setRegion(de.codesourcery.javr.assembler.parser.TextRegion)
     */
    @Override
    public final void setRegion(TextRegion region) {
        Validate.notNull(region, "region must not be NULL");
        this.region = region;
    }
    
    /* (non-Javadoc)
     * @see de.codesourcery.javr.assembler.parser.ast.ASTNodeIf#getParent()
     */
    @Override
    public final ASTNode getParent() {
        return parent;
    }
    
    /* (non-Javadoc)
     * @see de.codesourcery.javr.assembler.parser.ast.ASTNodeIf#hasParent()
     */
    @Override
    public final boolean hasParent() {
        return parent != null;
    }
    
    /* (non-Javadoc)
     * @see de.codesourcery.javr.assembler.parser.ast.ASTNodeIf#hasNoParent()
     */
    @Override
    public final boolean hasNoParent() {
        return parent == null;
    }
    
    /* (non-Javadoc)
     * @see de.codesourcery.javr.assembler.parser.ast.ASTNodeIf#setParent(de.codesourcery.javr.assembler.parser.ast.ASTNode)
     */
    @Override
    public final void setParent(ASTNode parent) 
    {
        Validate.notNull(parent, "parent must not be NULL");
        if ( this.parent != null && this.parent != parent) {
            throw new IllegalStateException("refusing to re-assign node parent");
        }
        this.parent = parent;
    }
    
    /* (non-Javadoc)
     * @see de.codesourcery.javr.assembler.parser.ast.ASTNodeIf#childCount()
     */
    @Override
    public final int childCount() {
        return children.size();
    }    
    
    protected static class IterationContext implements IIterationContext {

        public boolean stop;
        public boolean dontGoDeeper;
        
        public void reset() {
            dontGoDeeper = false;
        }
        @Override
        public void stop() {
            stop = true;
        }

        @Override
        public void dontGoDeeper() {
            dontGoDeeper = true;
        }
    }
    
    /* (non-Javadoc)
     * @see de.codesourcery.javr.assembler.parser.ast.ASTNodeIf#visitBreadthFirst(de.codesourcery.javr.assembler.parser.ast.ASTNode.IASTVisitor)
     */
    @Override
    public final void visitBreadthFirst(IASTVisitor n) 
    {
        visitBreadthFirst( n , new IterationContext() );
    }
    
    @SuppressWarnings("deprecation")
    @Override
    public final void visitBreadthFirst(IASTVisitor n,IterationContext ctx)  
    {
        n.visit( this , ctx );
        if ( ctx.stop ) {
            return;
        }
        if ( ctx.dontGoDeeper ) {
            ctx.reset();
            return;
        }
        
        for ( ASTNode child : children ) 
        {
            child.visitBreadthFirst( n , ctx );
            if ( ctx.stop ) 
            {
                return;
            }
        }
    }    
    
    /* (non-Javadoc)
     * @see de.codesourcery.javr.assembler.parser.ast.ASTNodeIf#visitDepthFirst(de.codesourcery.javr.assembler.parser.ast.ASTNode.IASTVisitor)
     */
    @Override
    public final void visitDepthFirst(IASTVisitor n) 
    {
        visitDepthFirst( n , new IterationContext() 
        {
            public void dontGoDeeper() {
                throw new UnsupportedOperationException("dontGoDeeper() doesn't make sense with depth-first traversal");
            }
        } );
    }
    
    @SuppressWarnings("deprecation")
    @Override
    public final void visitDepthFirst(IASTVisitor n,IterationContext ctx)  
    {
        for ( ASTNode child : children ) 
        {
            child.visitDepthFirst( n , ctx );
            if ( ctx.stop ) 
            {
                return;
            }
        }
        
        n.visit( this , ctx );
        if ( ctx.stop ) {
            return;
        }
    }      
    
    /* (non-Javadoc)
     * @see de.codesourcery.javr.assembler.parser.ast.ASTNodeIf#getTextRegion()
     */
    @Override
    public final TextRegion getTextRegion() {
        return region;
    }
    
    /* (non-Javadoc)
     * @see de.codesourcery.javr.assembler.parser.ast.ASTNodeIf#insertChild(int, de.codesourcery.javr.assembler.parser.ast.ASTNode)
     */
    @Override
    public final void insertChild(int index,ASTNode child) 
    {
        Validate.notNull(child, "child must not be NULL");
        this.children.add( index , child );
        child.setParent( this );
    }
    
    /* (non-Javadoc)
     * @see de.codesourcery.javr.assembler.parser.ast.ASTNodeIf#addChild(de.codesourcery.javr.assembler.parser.ast.ASTNode)
     */
    @Override
    public final void addChild(ASTNode child) 
    {
        Validate.notNull(child, "child must not be NULL");
        this.children.add( child );
        child.setParent( this );
    }
    
    /* (non-Javadoc)
     * @see de.codesourcery.javr.assembler.parser.ast.ASTNodeIf#addChildren(java.util.Collection)
     */
    @Override
    public final void addChildren(Collection<? extends ASTNode> children) 
    {
        Validate.notNull(children, "child must not be NULL");
        for ( ASTNode child : children ) {
            addChild( child );
        }
    }
    
    /* (non-Javadoc)
     * @see de.codesourcery.javr.assembler.parser.ast.ASTNodeIf#firstChild()
     */
    @Override
    public final ASTNode firstChild() {
        return children.get(0);
    }
    
    /* (non-Javadoc)
     * @see de.codesourcery.javr.assembler.parser.ast.ASTNodeIf#child(int)
     */
    @Override
    public final ASTNode child(int index) {
        return children.get(index);
    }
    
    /* (non-Javadoc)
     * @see de.codesourcery.javr.assembler.parser.ast.ASTNodeIf#hasChildren()
     */
    @Override
    public final boolean hasChildren() {
        return ! children.isEmpty();
    }
    
    /* (non-Javadoc)
     * @see de.codesourcery.javr.assembler.parser.ast.ASTNodeIf#hasNoChildren()
     */
    @Override
    public final boolean hasNoChildren() {
        return children.isEmpty();
    }

    /* (non-Javadoc)
     * @see de.codesourcery.javr.assembler.parser.ast.ASTNodeIf#indexOf(de.codesourcery.javr.assembler.parser.ast.ASTNode)
     */
    @Override
    public final int indexOf(ASTNode child) {
        return children.indexOf( child );
    }
    
    /* (non-Javadoc)
     * @see de.codesourcery.javr.assembler.parser.ast.ASTNodeIf#getAsString()
     */
    @Override
    public String getAsString() {
        return null;
    }
    
    /* (non-Javadoc)
     * @see de.codesourcery.javr.assembler.parser.ast.ASTNodeIf#toString()
     */
    @Override
    public String toString() 
    {
        final StringBuilder buffer = new StringBuilder();
        String s = getAsString();
        if ( s != null ) {
            buffer.append( s );
        }
        for ( ASTNode child : children ) {
            s = child.toString();
            if ( s.length() > 0 ) 
            {
                if ( buffer.length() > 0 ) {
                    buffer.append(",");
                }
                buffer.append( s );
            }
        }
        return buffer.toString();
    }
    
    @Override
    public boolean hasMemoryLocation() {
        return false;
    }
    
    @Override
    public Address getMemoryLocation() throws IllegalStateException {
        throw new IllegalStateException( getClass().getName()+" can never be be associated with a memory location");
    }
    
    @Override
    public boolean assignMemoryLocation(Address address) {
        throw new IllegalStateException( getClass().getName()+" can never be be associated with a memory location");
    }
    
    @Override
    public int getSizeInBytes() throws IllegalStateException {
        throw new IllegalStateException( getClass().getName()+" can never be be associated with a memory location");
    }
}