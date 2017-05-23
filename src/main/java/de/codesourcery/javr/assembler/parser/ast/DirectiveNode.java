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

import org.apache.commons.lang3.Validate;

import de.codesourcery.javr.assembler.ICompilationContext;
import de.codesourcery.javr.assembler.RelocationHelper;
import de.codesourcery.javr.assembler.RelocationHelper.RelocationInfo;
import de.codesourcery.javr.assembler.Segment;
import de.codesourcery.javr.assembler.elf.Relocation;
import de.codesourcery.javr.assembler.parser.Identifier;
import de.codesourcery.javr.assembler.parser.TextRegion;
import de.codesourcery.javr.assembler.symbols.Symbol;
import de.codesourcery.javr.assembler.symbols.Symbol.ObjectType;

public class DirectiveNode extends NodeWithMemoryLocation implements Resolvable
{
    private static final int SIZE_NOT_RESOLVED = -1;

    public final Directive directive;
    private int sizeInBytes = SIZE_NOT_RESOLVED;
    
    public static enum Directive 
    {
        ORG("org",1,1),
        CSEG("cseg",0,0),
        DSEG("dseg",0,0),
        ESEG("eseg",0,0),
        UNDEF("undef",1,1),
        DEF("DEF",2,2), // register alias
        DEVICE("device",1,1),
        RESERVE("byte",1,1),
        INIT_BYTES("db",1,Integer.MAX_VALUE),
        INIT_WORDS("dw",1,Integer.MAX_VALUE),
        // generates an entry in the IRQ vector table that points the given IRQ vector entry to this method
        // (currently relies on linker to generate the actual IRQ vector data so only works with ELF relocatable output format)
        IRQ_ROUTINE("irq",1,1),
        EQU("equ" , 1 , 1 );

        public final String literal;
        public final int minOperandCount;
        public final int maxOperandCount;

        private Directive(String literal,int minOperandCount,int maxOperandCount) {
            this.literal = literal.toLowerCase();
            this.minOperandCount = minOperandCount;
            this.maxOperandCount = maxOperandCount;
        }

        public boolean mayRequireRelocation() {
            return false;
        }

        public boolean isValidOperandCount(int actual) 
        {
            return minOperandCount <= actual && actual <= maxOperandCount;
        }

        public static boolean isValidDirective(String s) 
        {
            if ( s != null ) {
                for ( Directive d : values() ) 
                {
                    if ( d.literal.equalsIgnoreCase( s ) ) 
                    {
                        return true;
                    }
                }
            }
            return false;
        }

        public static Directive parse(String s) 
        {
            Directive[] v = values();
            for ( int i = 0, len = v.length ; i < len ; i++ ) {
                if ( s.equalsIgnoreCase( v[i].literal ) ) {
                    return v[i];
                }
            }
            return null;
        }
    }

    public boolean is(Directive d) {
        return d.equals( this.directive );
    }

    private DirectiveNode(Directive directive) 
    {
        Validate.notNull(directive, "directive must not be NULL");
        this.directive = directive;
    }    

    public DirectiveNode(Directive directive,TextRegion region) 
    {
        super(region);
        Validate.notNull(directive, "directive must not be NULL");
        this.directive = directive;
    }

    @Override
    protected DirectiveNode createCopy() {
        if ( getTextRegion() != null ) {
            return new DirectiveNode(this.directive , getTextRegion().createCopy() );
        }
        return new DirectiveNode(this.directive );
    }

    /**
     * Check whether this node has a given {@link Directive type}.
     * @param type
     * @return
     */
    public boolean hasType(Directive type) 
    {
        return this.directive == type;
    }

    @Override
    public boolean hasMemoryLocation() 
    {
        switch( this.directive ) 
        {
            case INIT_BYTES:
            case INIT_WORDS:
            case RESERVE:
                return true;
            default:
                return false;
        }
    }

    private boolean resolveSize(ICompilationContext context)
    {
        switch( this.directive ) 
        {
            case INIT_BYTES: sizeInBytes = childCount(); break;
            case INIT_WORDS: sizeInBytes = childCount()*2; break;
            case RESERVE:
                if ( context.currentSegment() == Segment.FLASH ) {
                    context.error(".byte cannot be used in .text segment",this);
                    return false;
                }
                final IValueNode child = (IValueNode) child(0); 
                if ( child instanceof Resolvable) {
                    if ( ! ((Resolvable) child).resolve( context ) ) {
                        return false;
                    }
                }
                Number value = (Number) child.getValue();
                if ( value != null ) 
                {
                    final int size = value.intValue();
                    if ( size >= 0 ) {
                        sizeInBytes = size;
                        return true;
                    }
                    context.error("Expected a positive number but got "+size,child);
                    return false;
                }
                return value != null;
            default:
                throw new RuntimeException("Unreachable code reached");
        }  
        return sizeInBytes != SIZE_NOT_RESOLVED;
    }

    @Override
    public int getSizeInBytes() throws IllegalStateException 
    {
        if ( ! hasMemoryLocation() ) {
            throw new IllegalStateException( "This statement is not yet associated with a memory location" );
        }
        return sizeInBytes;
    }

    @Override
    public boolean resolve(ICompilationContext context) 
    {
        switch( directive ) 
        {
            case IRQ_ROUTINE:
                handleIRQ( context );
                return true;
            case EQU:
                // child 0 is EquLabelNode
                final Identifier identifier = ((EquLabelNode) child(0)).name;
                final ASTNode child1 = child(1);
                // chidl 1 is expression
                final boolean valueResolved;
                if ( child1 instanceof Resolvable) {
                    valueResolved = ((Resolvable) child1).resolve( context );
                } else {
                    valueResolved = true;
                }
                if ( valueResolved )
                {
                    context.currentSymbolTable().get( identifier ).setValue( ((IValueNode) child(1)).getValue() , Symbol.Type.EQU ); 
                } else {
                    context.error("Failed to resolve value",child1);
                }
                break;
            case INIT_BYTES:
            case INIT_WORDS:
            case RESERVE:
                return resolveSize( context );
            default:
        }
        return false;
    }   

    private void handleIRQ(ICompilationContext context) {

        if ( context.currentSegment() != Segment.FLASH ) {
            context.error(".irq can only be used within the .text segment",this);
            return;
        }

        final Symbol symbol = findNextGlobalFunctionSymbol( context );
        if ( symbol == null ) {
            return;
        }
        if ( symbol.getSegment() != Segment.FLASH ) {
            context.error(".irq directive requires a global function label within FLASH but "+symbol.name()+" isn't",this);
            return;
        }
        if ( symbol.getObjectType() != ObjectType.FUNCTION ) {
            context.error(".irq directive needs to be followed by a global FUNCTION label",this);
            return;
        }

        final int vectorIdx = ((NumberLiteralNode) child(0)).getValue();
        if ( vectorIdx < 0 || vectorIdx >= context.getArchitecture().getIRQVectorCount() ) 
        {
            context.error("IRQ vector out of range, "+context.getArchitecture().getType()+" architecture only supports IRQ vectors 0-"+(context.getArchitecture().getIRQVectorCount()-1), child(0));
            return;
        }
        
        symbol.markAsReferenced(); // ...so that the check for unreferenced symbols doesn't complain about this function
    }
    
    /**
     * Returns the next global function symbol following this node.
     * 
     * @param context
     * @return
     */
    public Symbol findNextGlobalFunctionSymbol(ICompilationContext context) 
    {
        LabelNode label = null;
        StatementNode stmt = statement();
        final ASTNode ast = stmt.getParent();
        final int nextChild = ast.indexOf( stmt )+1;
        final int lastChild = ast.childCount();
        for ( int i = nextChild ; label == null && i < lastChild ; i++ ) 
        {
            label = ast.child(i).visitBreadthFirstWithResult( (LabelNode) null, (node,ctx) -> 
            {
                if ( node instanceof LabelNode && ((LabelNode) node).isGlobal() ) {
                    ctx.stop( (LabelNode) node);
                }
            });
        }
        if ( label == null ) {
            context.error(".irq directive needs to be followed by a global label",this);
            return null;
        }
        final Symbol symbol = label.getSymbol();
        if ( symbol.getSegment() != Segment.FLASH ) {
            context.error(".irq directive requires a global function label within FLASH but "+symbol.name()+" isn't",this);
            return null;
        }
        if ( symbol.getObjectType() != ObjectType.FUNCTION ) {
            context.error(".irq directive needs to be followed by a global FUNCTION label",this);
            return null;
        }        
        return symbol;
    }

    public void addRelocations(ICompilationContext context) 
    {
        if ( is( Directive.INIT_WORDS )  && context.getCompilationSettings().isGenerateRelocations() )
        { 
            int offset = context.currentOffset();
            for ( ASTNode child : children() ) 
            {
                final RelocationInfo info = RelocationHelper.getRelocationInfo( child );
                if ( info != null )
                {
                    final Relocation reloc = new Relocation( info.symbol );
                    reloc.addend = info.addent;
                    reloc.locationOffset = offset;
                    reloc.kind = Relocation.Kind.R_AVR_16;
                    context.addRelocation( reloc );
                }
                offset += 2;
            } 
        }
    }
}