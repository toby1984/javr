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
import java.util.function.Predicate;

import org.apache.commons.lang3.Validate;

import de.codesourcery.javr.assembler.Address;
import de.codesourcery.javr.assembler.CompilationUnit;
import de.codesourcery.javr.assembler.parser.TextRegion;

public abstract class AbstractASTNode implements ASTNode 
{
    private final List<ASTNode> children=new ArrayList<>();
    
    private boolean skip;
    private TextRegion region;
    private ASTNode parent;
    
    public AbstractASTNode() {
    }
    
    public final TextRegion getMergedTextRegion() {
        
        final TextRegion[] result = {null};
        visitBreadthFirst( (node,ctx) -> 
        {
            final TextRegion r = node.getTextRegion();
            if ( r != null ) {
                if ( result[0] == null ) {
                    result[0] = r.createCopy();
                } else {
                    result[0].merge( r );
                }
            }
        }); 
        return result[0];
    }
    
    @Override
    public final void replaceWith(ASTNode other) 
    {
        if ( hasNoParent() ) {
            throw new IllegalStateException("Cannot replace node "+this+" that has no parent");
        }
        parent.replaceChild( this , other );
    }
    
    @Override
    public CompilationUnit getCompilationUnit() 
    {
    	return parent == null ? null : parent.getCompilationUnit();
    }
    
    @Override
    public final boolean anyMatchingParent(Predicate<ASTNode> predicate) 
    {
    	return findMatchingParent(predicate) != null;
    }
    
    public final ASTNode findMatchingParent(Predicate<ASTNode> predicate) {
    	ASTNode current = getParent();
    	while ( current != null ) {
    		if ( predicate.test( current ) ) {
    			return current;
    		}
    		current = current.getParent();
    	}
    	return null;
    }
    
    public final void replaceChild(ASTNode child,  ASTNode newNode) 
    {
        Validate.notNull(child, "child must not be NULL");
        Validate.notNull(newNode,"newNode must not be NULL");
        final int idx = children.indexOf( child );
        if ( idx == -1 ) {
            throw new IllegalArgumentException( child+" is no child of "+this);
        }
        children.set( idx , newNode );
        newNode.setParent( this );
        child.setParent( null );
    }
    
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
    
    @Override
    public final List<ASTNode> children() {
        return children;
    }
    
    @Override
    public final boolean isSkip() {
        return skip;
    }
    
    @Override
    public final ASTNode createCopy(boolean deep) 
    {
        /*
         * 'skip' (ignore subtree) flag is INTENTIONALLY
         * not cloned here since preprocessor macro expansion
         * would otherwise have to always undo this
         */
        final ASTNode result = createCopy();
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
    
    @Override
    public final void setRegion(TextRegion region) {
        Validate.notNull(region, "region must not be NULL");
        this.region = region;
    }
    
    @Override
    public final ASTNode getParent() {
        return parent;
    }
    
    @Override
    public final boolean hasParent() {
        return parent != null;
    }
    
    @Override
    public final boolean hasNoParent() {
        return parent == null;
    }
    
    @Override
    public final void setParent(ASTNode parent) 
    {
        this.parent = parent;
    }
    
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
    
    @Override
    public final TextRegion getTextRegion() {
        return region;
    }
    
    @Override
    public final void insertChild(int index,ASTNode child) 
    {
        Validate.notNull(child, "child must not be NULL");
        this.children.add( index , child );
        child.setParent( this );
    }
    
    @Override
    public final void addChild(ASTNode child) 
    {
        Validate.notNull(child, "child must not be NULL");
        this.children.add( child );
        child.setParent( this );
    }
    
    @Override
    public final void addChildren(Collection<? extends ASTNode> children) 
    {
        Validate.notNull(children, "child must not be NULL");
        for ( ASTNode child : children ) {
            addChild( child );
        }
    }
    
    @Override
    public final ASTNode firstChild() {
        return children.get(0);
    }
    
    @Override
    public final ASTNode child(int index) {
        return children.get(index);
    }
    
    @Override
    public final boolean hasChildren() {
        return ! children.isEmpty();
    }
    
    @Override
    public final boolean hasNoChildren() {
        return children.isEmpty();
    }

    @Override
    public final int indexOf(ASTNode child) {
        return children.indexOf( child );
    }
    
    @Override
    public String getAsString() {
        return null;
    }
    
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
