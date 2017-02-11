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

import java.util.HashSet;
import java.util.Set;

import org.apache.commons.lang3.Validate;

import de.codesourcery.javr.assembler.ICompilationContext;
import de.codesourcery.javr.assembler.Instruction;
import de.codesourcery.javr.assembler.Segment;
import de.codesourcery.javr.assembler.parser.Identifier;
import de.codesourcery.javr.assembler.parser.OperatorType;
import de.codesourcery.javr.assembler.parser.TextRegion;
import de.codesourcery.javr.assembler.symbols.Symbol;
import de.codesourcery.javr.assembler.symbols.Symbol.Type;
import de.codesourcery.javr.assembler.symbols.SymbolTable;

public class InstructionNode extends NodeWithMemoryLocation implements Resolvable
{
    public final Instruction instruction;
    private int sizeInBytes;

    public InstructionNode(Instruction insn,TextRegion region) 
    {
        super(region);
        Validate.notNull(insn, "insn must not be NULL");
        this.instruction = insn;
    }
    
    @Override
    protected InstructionNode createCopy() {
        return new InstructionNode( this.instruction.createCopy() , getTextRegion().createCopy() );
    }
    
    public boolean srcNeedsRelocation(ICompilationContext context) {
        return needsRelocation( src() , context );
    }
    
    public boolean dstNeedsRelocation(ICompilationContext context) {
        return needsRelocation( dst() , context );
    }    
    
    private ASTNode unwrapExpression(ASTNode node) 
    {
        ASTNode result = node;
        while( result instanceof ExpressionNode) 
        {
            result = result.child(0);
        }
        return result;
    }
    
    private boolean isAddressIdentifierNode(ASTNode node,SymbolTable symbolTable) 
    {
        if ( node instanceof IdentifierNode) 
        {
            return ((IdentifierNode) node).refersToAddressSymbol( symbolTable );
        }
        return false;
    }

    private boolean needsRelocation(ASTNode subTree,ICompilationContext context) 
    {
        final SymbolTable symbolTable = context.currentSymbolTable();
        final Set<Segment> segments = new HashSet<>();
        final IASTVisitor visitor = new IASTVisitor() 
        {
            @Override
            public void visit(ASTNode node, IIterationContext ctx) 
            {
                // deal with special cases 
                if ( node instanceof OperatorNode) 
                {
                    final OperatorNode op = (OperatorNode) node;
                    // subtraction between addresses in same segment doesn't need relocation
                    if ( op.getOperatorType() == OperatorType.BINARY_MINUS ) 
                    {
                        final ASTNode child0 = unwrapExpression( op.child(0) );
                        final ASTNode child1 = unwrapExpression( op.child(1) );
                        if ( isAddressIdentifierNode(child0 , symbolTable) || isAddressIdentifierNode( child1 , symbolTable) ) 
                        {
                            // (label1-label2) expressions don't need relocation
                            ctx.dontGoDeeper();
                        }
                    } 
                    else if ( op.getOperatorType() == OperatorType.UNARY_MINUS ) // 0 - address doesn't need special case 
                    {
                        final ASTNode child0 = unwrapExpression( op.child(0) );
                        if ( isAddressIdentifierNode( child0 , symbolTable ) ) 
                        {
                            final IdentifierNode id = (IdentifierNode) child0;
                            if ( ! id.safeGetSymbol().getSegment().equals( context.currentSegment() ) ) {
                                throw new RuntimeException("Not relocatable, symbol needs to be in same section as instruction");
                            }
                            ctx.dontGoDeeper();
                        }
                    }
                } 
                else if ( isAddressIdentifierNode( node , symbolTable ) )
                {
                    final Symbol symbol = ((IdentifierNode) node).safeGetSymbol();
                    final Segment segment = symbol.getSegment();
                    if ( segment == null ) {
                        throw new RuntimeException("Symbol "+symbol+" has NULL segment ?");
                    }
                    segments.add( segment ); 
                    if ( segments.size() > 1 ) {
                        throw new RuntimeException("Expression is not relocatable: Involves symbols from different sections");
                    }
                }
            }
        };
        subTree.visitBreadthFirst( visitor );
        return ! segments.isEmpty();
    }
    
    public ASTNode src() {
        return child(1);
    }
    
    public ASTNode dst() {
        return child(0);
    }    
    
    @Override
    public String getAsString() {
        return instruction.getMnemonic().toUpperCase();
    }
    
    @Override
    public boolean hasMemoryLocation() {
        return true;
    }
    
    @Override
    public int getSizeInBytes() throws IllegalStateException 
    {
        if ( this.sizeInBytes <= 0 ) {
            throw new IllegalStateException("Size not resolved yet");
        }
        return sizeInBytes;
    }    
    
    @Override
    public boolean resolve(ICompilationContext context) 
    {
        assignMemoryLocation( context.currentAddress() );
        for ( ASTNode child : children() ) 
        {
            child.visitDepthFirst( (node,ctx) -> 
            {
                if ( node instanceof Resolvable) 
                {
                    ((Resolvable) node).resolve( context );
                }
            });
        }
        try {
            this.sizeInBytes = context.getArchitecture().getInstructionLengthInBytes( this, context , true );
        } catch(Exception e) {
            context.error( e.getMessage() , this );
        }
        return true;
    }
}