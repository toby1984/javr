/**
 * Copyright 2015-2018 Tobias Gierke <tobias.gierke@code-sourcery.de>
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
import java.util.Objects;
import java.util.function.Predicate;

import org.apache.commons.lang3.Validate;

import de.codesourcery.javr.assembler.Address;
import de.codesourcery.javr.assembler.CompilationUnit;
import de.codesourcery.javr.assembler.parser.OperatorType;
import de.codesourcery.javr.assembler.parser.TextRegion;

/**
 * Abstract base class for implementing AST nodes.
 *
 * @author tobias.gierke@code-sourcery.de
 */
public abstract class AbstractASTNode implements ASTNode 
{
    private final List<ASTNode> children=new ArrayList<>();
    
    private TextRegion region;
    private TextRegion mergedRegion;
    
    private ASTNode parent;
    
    public AbstractASTNode() {
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
    public final TextRegion recalculateMergedRegion() 
    {
        TextRegion region = this.region != null ? this.region.createCopy() : null;
        for ( ASTNode child : children ) 
        {
            final TextRegion m = child.getMergedTextRegion();
            if ( m != null ) {
                if ( region == null ) {
                    region = m.createCopy();
                } else {
                    region.merge( m );
                }
            }
        }
        final boolean regionChanged = ! Objects.equals( this.mergedRegion , region );
        mergedRegion = region;
        if ( regionChanged && hasParent() ) 
        {
            parent.recalculateMergedRegion();
        }
        return mergedRegion;
    }
    
    @Override
    public final TextRegion getMergedTextRegion()
    {
        if ( mergedRegion == null ) 
        {
            return recalculateMergedRegion();
        }
        return mergedRegion;
    }
    
    @Override
    public final ASTNode getNodeAtOffset(int offset) 
    {
        final TextRegion m = getMergedTextRegion();
        if ( m == null || ! m.contains( offset ) )
        {
            return null;
        }
        
        for ( int i = 0 , len = children.size() ; i < len ; i++ ) 
        {
            final ASTNode tmp = children.get(i).getNodeAtOffset( offset );
            if ( tmp != null ) 
            {
                return tmp;
            }
        }
        
        if ( getTextRegion() != null && getTextRegion().contains( offset ) ) {
            return this;
        }
        return null;
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
    
    @Override
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
    
    @Override
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
        recalculateMergedRegion();
    }
    
    @Override
    public final ASTNode searchBackwards(Predicate<ASTNode> pred) 
    {
        if ( hasNoParent() ) {
            return null;
        }
        
        final ASTNode[] result = new ASTNode[]{null};
        
        final IASTVisitor visitor = new IASTVisitor() 
        {
            @Override
            public void visit(ASTNode node, IIterationContext<?> ctx) 
            {
                if ( pred.test( node ) ) 
                {
                    result[0] = node;
                    ctx.stop();
                }
            }
        };
        
        final int idx = getParent().indexOf( this );
        for ( int i = idx - 1 ; result[0] == null && i >= 0 ; i-- ) 
        {
            final ASTNode child = getParent().child( i );
            child.visitDepthFirst( visitor );
            if ( result[0] != null ) {
                return result[0];
            }
        }
        return getParent().searchBackwards( pred );
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
    public final ASTNode createCopy(boolean deep) 
    {
        /*
         * 'skip' (ignore subtree) flag is INTENTIONALLY
         * not cloned here since preprocessor macro expansion
         * would otherwise have to always undo this
         */
        final AbstractASTNode result = createCopy();
        result.region = this.region != null ? this.region.createCopy() : null;
        result.mergedRegion = this.mergedRegion != null ? this.mergedRegion.createCopy() : null;
        if ( deep ) 
        {
            for ( ASTNode child : children ) 
            {
                result.addChild( child.createCopy( true ) );
            }
        }
        return result;
    }
    
    protected abstract AbstractASTNode createCopy();
    
    public AbstractASTNode(TextRegion region) 
    {
        Validate.notNull(region, "region must not be NULL");
        this.region = region;
    }
    
    @Override
    public final void setRegion(TextRegion region) {
        Validate.notNull(region, "region must not be NULL");
        this.region = region;
        recalculateMergedRegion();
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
    
    protected static class IterationContext<T> implements IIterationContext<T> {

        public T result;
        public boolean stop;
        public boolean dontGoDeeper;
        
        public IterationContext() {
        }
        
        public IterationContext(T value) {
            this.result = value;
        }
        
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
        @Override
        public void stop(T value) {
            this.result = value;
            this.stop = true;
        }
        
        @Override
        public T getResult() {
            return result;
        }
    }
    
    @Override
    public final <T> T visitBreadthFirstWithResult(T initialValue,IASTVisitor2<T> n) 
    {
        final IterationContext<T> ctx = new IterationContext<T>(initialValue);
        visitBreadthFirstWithResult( n , ctx );
        return ctx.getResult();
    }
    
    @SuppressWarnings("deprecation")
    public final <T> void visitBreadthFirstWithResult(IASTVisitor2<T> n,IterationContext<T> ctx)  
    {
        n.visit( this , ctx );
        if ( ctx.stop ) {
            return;
        }
        if ( ctx.dontGoDeeper ) {
            ctx.reset();
            return;
        }
        
        for (int i = 0 , l = children.size() ; i < l ; i++) {
            final ASTNode child = children.get(i);
            child.visitBreadthFirstWithResult( n , ctx );
            if ( ctx.stop ) 
            {
                return;
            }
        }
    }     
    
    @Override
    public final void visitBreadthFirst(IASTVisitor n) 
    {
        visitBreadthFirst( n , new IterationContext<Object>() );
    }
    
    @SuppressWarnings("deprecation")
    @Override
    public final void visitBreadthFirst(IASTVisitor n,IterationContext<?> ctx)  
    {
        n.visit( this , ctx );
        if ( ctx.stop ) {
            return;
        }
        if ( ctx.dontGoDeeper ) {
            ctx.reset();
            return;
        }
        
        for (int i = 0 , len = children.size() ; i < len ; i++) 
        {
            final ASTNode child = children.get(i);
            child.visitBreadthFirst( n , ctx );
            if ( ctx.stop ) 
            {
                return;
            }
        }
    }    
    
    @Override
    public final <T> T visitDepthFirstWithResult(T initialValue,IASTVisitor2<T> n) 
    {
        final IterationContext<T> ctx = new IterationContext<T>(initialValue) 
        {
            public void dontGoDeeper() {
                throw new UnsupportedOperationException("dontGoDeeper() doesn't make sense with depth-first traversal");
            }
        };
        visitDepthFirstWithResult( n , ctx );
        return ctx.getResult();
    }
    
    @Override
    public final <T> void visitDepthFirstWithResult(IASTVisitor2<T> n, IterationContext<T> ctx) 
    {
        for (int i = 0 , l = children.size() ; i < l ; i++) 
        {
            final ASTNode child = children.get(i);
            child.visitDepthFirstWithResult( n , ctx );
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
    public final void visitDepthFirst(IASTVisitor n) 
    {
        visitDepthFirst( n , new IterationContext<Object>() 
        {
            public void dontGoDeeper() {
                throw new UnsupportedOperationException("dontGoDeeper() doesn't make sense with depth-first traversal");
            }
        } );
    }
    
    @SuppressWarnings("deprecation")
    @Override
    public final void visitDepthFirst(IASTVisitor n,IterationContext<?> ctx)  
    {
        for (int i = 0 , l = children.size() ; i < l ; i++) {
            final ASTNode child = children.get(i);
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
        childAdded( child );
    }
    
    private void childAdded(ASTNode child) 
    {
        TextRegion tmp = child.getMergedTextRegion();
        if ( tmp != null ) {
            if ( this.mergedRegion == null ) {
                this.mergedRegion = tmp.createCopy();
            } else {
                this.mergedRegion.merge( tmp );
            }
        }
    }
    
    @Override
    public final void addChild(ASTNode child) 
    {
        Validate.notNull(child, "child must not be NULL");
        this.children.add( child );
        child.setParent( this );
        childAdded( child );
    }
    
    @Override
    public final void addChildren(Collection<? extends ASTNode> toAdd) 
    {
        Validate.notNull(toAdd, "child must not be NULL");
        for ( ASTNode child : toAdd ) 
        {
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
    
    @Override
    public final OperatorNode asOperator() {
        return (OperatorNode) this;
    }
    
    @Override
    public boolean isOperatorNode() {
        return false;
    }
    
    @Override
    public boolean isOperator(OperatorType type) 
    {
        return false;
    }
    
    protected StatementNode statement() 
    {
        ASTNode current = this;
        while ( current != null && !(current instanceof StatementNode) ) {
            current = current.getParent();
        }
        return (StatementNode) current;
    }
}
