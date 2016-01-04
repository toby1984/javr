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

import de.codesourcery.javr.assembler.ICompilationContext;
import de.codesourcery.javr.assembler.parser.TextRegion;
import de.codesourcery.javr.assembler.parser.Parser.CompilationMessage;
import de.codesourcery.javr.assembler.parser.Parser.Severity;

public abstract class ASTNode 
{
    public final List<ASTNode> children=new ArrayList<>();
    
    private TextRegion region;
    private ASTNode parent;
    
    public ASTNode() {
    }
    
    public final ASTNode createCopy(boolean deep) 
    {
        final ASTNode result = createCopy();
        if ( deep ) 
        {
            for ( ASTNode child : children ) 
            {
                result.add( child.createCopy( true ) );
            }
        }
        return result;
    }
    
    protected abstract ASTNode createCopy();
    
    public ASTNode(TextRegion region) 
    {
        Validate.notNull(region, "region must not be NULL");
        this.region = region;
    }
    
    public void setRegion(TextRegion region) {
        Validate.notNull(region, "region must not be NULL");
        this.region = region;
    }
    
    public ASTNode getParent() {
        return parent;
    }
    
    public boolean hasParent() {
        return parent != null;
    }
    
    public boolean hasNoParent() {
        return parent == null;
    }
    
    public void setParent(ASTNode parent) 
    {
        Validate.notNull(parent, "parent must not be NULL");
        if ( this.parent != null && this.parent != parent) {
            throw new IllegalStateException("refusing to re-assign node parent");
        }
        this.parent = parent;
    }
    
    public int childCount() {
        return children.size();
    }    
    
    public interface IIterationContext 
    {
        public void stop();
        public void dontGoDeeper();
    }
    
    protected final class IterationContext implements IIterationContext {

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
    
    public interface IASTVisitor {
        
        public void visit(ASTNode node,IIterationContext ctx);
    }
    
    public void visitBreadthFirst(IASTVisitor n) 
    {
        visitBreadthFirst( n , new IterationContext() );
    }
    
    private void visitBreadthFirst(IASTVisitor n,IterationContext ctx)  
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
    
    public TextRegion getTextRegion() {
        return region;
    }
    
    public void add(ASTNode child) 
    {
        Validate.notNull(child, "child must not be NULL");
        this.children.add( child );
        child.setParent( this );
    }
    
    public void add(Collection<ASTNode> children) 
    {
        Validate.notNull(children, "child must not be NULL");
        for ( ASTNode child : children ) {
            add( child );
        }
    }
    
    public ASTNode firstChild() {
        return children.get(0);
    }
    
    public ASTNode child(int index) {
        return children.get(index);
    }
    
    public boolean hasChildren() {
        return ! children.isEmpty();
    }
    
    public boolean hasNoChildren() {
        return children.isEmpty();
    }

    public int indexOf(ASTNode child) {
        return children.indexOf( child );
    }
    
    public void compile(ICompilationContext ctx) 
    {
        for ( ASTNode child : children ) 
        {
            try {
                child.compile(ctx);
            } catch(Exception e) {
                ctx.message( new CompilationMessage( Severity.ERROR , e.getMessage() , child ) );
            }
        }
    }
    
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
}