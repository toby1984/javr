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
package de.codesourcery.javr.assembler.phases;

import de.codesourcery.javr.assembler.Address;
import de.codesourcery.javr.assembler.ICompilationContext;
import de.codesourcery.javr.assembler.RelocationHelper;
import de.codesourcery.javr.assembler.arch.AbstractArchitecture;
import de.codesourcery.javr.assembler.parser.Identifier;
import de.codesourcery.javr.assembler.parser.Parser.CompilationMessage;
import de.codesourcery.javr.assembler.parser.ast.AST;
import de.codesourcery.javr.assembler.parser.ast.ASTNode;
import de.codesourcery.javr.assembler.parser.ast.ASTNode.IASTVisitor;
import de.codesourcery.javr.assembler.parser.ast.ASTNode.IIterationContext;
import de.codesourcery.javr.assembler.parser.ast.DirectiveNode;
import de.codesourcery.javr.assembler.parser.ast.DirectiveNode.Directive;
import de.codesourcery.javr.assembler.parser.ast.IValueNode;
import de.codesourcery.javr.assembler.parser.ast.InstructionNode;
import de.codesourcery.javr.assembler.parser.ast.LabelNode;
import de.codesourcery.javr.assembler.parser.ast.IntNumberLiteralNode;
import de.codesourcery.javr.assembler.parser.ast.PreprocessorNode;
import de.codesourcery.javr.assembler.parser.ast.PreprocessorNode.Preprocessor;
import de.codesourcery.javr.assembler.symbols.Symbol;
import de.codesourcery.javr.assembler.symbols.Symbol.ObjectType;
import de.codesourcery.javr.assembler.symbols.SymbolTable;
import org.apache.log4j.Logger;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Performs the actual code generation.
 *
 * @author tobias.gierke@code-sourcery.de
 */
public class GenerateCodePhase extends AbstractPhase 
{
    private static final Logger LOG = Logger.getLogger(GenerateCodePhase.class);
    
    private final boolean isInResolvePhase;
    
    private boolean foundMemDirective;
    private final List<Symbol> previousDataSymbols = new ArrayList<>();
    
    public GenerateCodePhase() {
        this("generate_code",false);
    }
    
    protected GenerateCodePhase(String name,boolean onlyAllocation) {
        super(name,false);
        this.isInResolvePhase = onlyAllocation;
    }
    
    @Override
    public void run(ICompilationContext context) throws Exception {

        final AST ast = context.currentCompilationUnit().getAST();
        
        final IASTVisitor visitor = new IASTVisitor() 
        {
            @Override
            public void visit(ASTNode node, IIterationContext<?> ctx) 
            {
                generateCode(context, node, ctx); 
            }
        };
        ast.visitBreadthFirst( visitor );        
    }
    
    protected boolean generateCode(ICompilationContext context, ASTNode node,IIterationContext<?> ctx) 
    {
        visitNode( context , node , ctx );

        if ( ! isInResolvePhase ) 
        {
            if ( node instanceof LabelNode) 
            {
                final Symbol s = ((LabelNode) node).getSymbol();
                if ( s.hasObjectType( ObjectType.DATA ) ) 
                {
                    if ( foundMemDirective ) 
                    {
                        if ( ! previousDataSymbols.isEmpty() ) {
                            previousDataSymbols.clear();
                        }
                        foundMemDirective = false;
                    }
                    previousDataSymbols.add( s );
                }
            } 
        }
        
        if ( node instanceof InstructionNode ) 
        {
            try 
            {            
                if ( isInResolvePhase ) 
                {
                    context.getArchitecture().validate( (InstructionNode) node , context );
                    final int bytes = context.getArchitecture().getInstructionLengthInBytes( (InstructionNode) node , context , true );
                    if ( LOG.isDebugEnabled() ) {
                        LOG.debug("generateCode(): Allocating "+bytes+" at "+context.currentAddress()+" segment for "+node);
                    }
                    context.allocateBytes( bytes );
                }
                else 
                {
                    if ( LOG.isDebugEnabled() ) {
                        LOG.debug("generateCode(): Compiling instruction at "+context.currentAddress()+" segment for "+node);
                    }

                    if ( context.getCompilationSettings().isWarnIfInOutCanBeUsed() )
                    {
                        final String mnemonic = ((InstructionNode) node).instruction.getMnemonic();
                        int adrArgIdx = -1;
                        if ( mnemonic.equalsIgnoreCase( "sts" ) ) {
                            adrArgIdx = 0;
                        }
                        else if ( mnemonic.equalsIgnoreCase( "lds" ) )
                        {
                            adrArgIdx = 1;
                        }
                        if ( adrArgIdx != -1 )
                        {

                            final IValueNode child = (IValueNode) node.child(adrArgIdx);
                            final Object value = child.getValue();
                            final long addr = AbstractArchitecture.toIntValue( value );
                            if ( (addr & ~0b111111) == 0 )
                            {
                                final String alternative = "sts".equalsIgnoreCase( mnemonic ) ?
                                        "OUT" : "IN";
                                context.message( CompilationMessage.warning(
                                        context.currentCompilationUnit(),
                                        "Destination address fits in 6 bits, could use "+alternative+" instruction", node ) );
                            }
                        }
                    }

                    int ptr = context.currentOffset();
                    context.getArchitecture().compile( (InstructionNode) node , context );
                    final int delta = context.currentOffset() - ptr;
                    if ( delta != node.getSizeInBytes() ) 
                    {
                        // fail because following labels might be wrong ...
                        
                        // TODO: Properly handle the case where the size of an instruction changed because
                        // TODO: some expression that could previously not be calculated evaluated to something small enough 
                        // TODO: so that the compiler picked a smaller encoding
                        throw new RuntimeException("Internal error, size of instruction changed between compilation passes");
                    }
                }
            } catch(Exception e) {
                LOG.debug("visitNode(): "+e.getMessage(),e);
                context.error( e.getMessage() , node );
            }            
            ctx.dontGoDeeper();
        } 
        else if ( node instanceof DirectiveNode ) 
        {
            final Directive directive = ((DirectiveNode) node).directive;
            switch( directive ) 
            {
                case IRQ_ROUTINE:
                    if ( ! isInResolvePhase ) 
                    {
                        /* The AVR GNU linker checks for special function symbols named
                         * __vector_X
                         * and if present, will generate an IRQ vector entry for vector X that jumps to this function.
                         */
                        final int vectorIdx = ((IntNumberLiteralNode) node.child( 0)).getValue();
                        final Identifier symName = new Identifier("__vector_"+vectorIdx);
                        final Symbol nextGlobalFunc = ((DirectiveNode) node).findNextGlobalFunctionSymbol( context );
                        final SymbolTable symTable = context.currentSymbolTable().getTopLevelTable();
                        final Symbol copy = nextGlobalFunc.withName( symName );
                        symTable.defineSymbol( copy , nextGlobalFunc.getSegment() );
                    }
                    break;
                case INIT_BYTES:
                case INIT_WORDS:
                    if ( ! isInResolvePhase ) 
                    {
                        foundMemDirective = true;
                        if ( ! previousDataSymbols.isEmpty() )
                        {
                            final int size = ((DirectiveNode) node).getSizeInBytes();
                            previousDataSymbols.forEach( s -> s.incObjectSize( size ) );
                        }
                        
                        if ( context.isGenerateRelocations() ) {
                            ((DirectiveNode) node).addRelocations( context );
                        }
                    }

                    final boolean checkForRelocation =  directive == Directive.INIT_WORDS && ! isInResolvePhase && context.isGenerateRelocations();
                    for ( ASTNode child : node.children() ) 
                    {
                        final boolean relocated = checkForRelocation && RelocationHelper.getRelocationInfo( child ) != null;
                        Object value = ((IValueNode) child).getValue();
                        final int[] data;
                        if ( isInResolvePhase ) 
                        {
                            final int len = value == null ? 1 : value instanceof String ? ((String) value).length() : 1;
                            data = new int[len];
                        } 
                        else 
                        {
                            if ( value instanceof Number) 
                            {
                                data = new int[] { relocated ? 0 : ((Number) value).intValue() };
                            } 
                            else if ( value instanceof Address ) 
                            {
                                if ( directive == Directive.INIT_BYTES ) {
                                    context.message( CompilationMessage.error( context.currentCompilationUnit() , "Storing 16-bit address as byte value would truncate it",node ) );
                                }
                                data = new int[] { relocated ? 0 : ((Address) value).getByteAddress() };         
                            } 
                            else if ( value instanceof String ) 
                            {
                                final String s = (String) value;
                                data = new int[ s.length() ];
                                for ( int i = 0  ; i < data.length ; i++ ) {
                                    data[i] = s.charAt(i);
                                }
                            } else {
                                throw new RuntimeException("Internal error,unhandled IValueNode: "+value);
                            }
                        }
                        switch( directive ) 
                        {
                            case INIT_BYTES:
                                for ( int i = 0 ; i < data.length ; i++ ) 
                                {
                                    if ( isInResolvePhase ) {
                                        context.allocateByte();
                                    } else {
                                        context.writeByte( data[i] );
                                    }
                                }
                                break;
                            case INIT_WORDS:
                                for ( int i = 0 ; i < data.length ; i++ ) 
                                {
                                    if ( isInResolvePhase ) {
                                        context.allocateWord();
                                    } else {
                                        context.writeWord( data[i] );
                                    }
                                }
                                break;
                            default:
                                throw new RuntimeException("Unreachable code reached");
                        }
                    }
                    break;
                case RESERVE:
                    switch( context.currentSegment() ) 
                    {
                        case EEPROM:
                        case SRAM:
                            foundMemDirective = true;
                            if ( ! isInResolvePhase && ! previousDataSymbols.isEmpty() )
                            {
                                final int size = node.getSizeInBytes();
                                previousDataSymbols.forEach( s -> s.incObjectSize( size ) );
                            }                        
                            final Number value = (Number) ((IValueNode) node.child(0)).getValue();
                            if ( value.intValue() < 1 ) {
                                context.error("Refusing to allocate less than 1 byte", node.child(0));
                            }
                            context.allocateBytes( value.intValue() );
                            break;
                        default:
                            context.message( CompilationMessage.error( context.currentCompilationUnit() , "Cannot reserve bytes in "+context.currentSegment()+" segment, only SRAM and EEPROM are supported",node ) );
                    }
                    ctx.dontGoDeeper();                            
                    break;
                default:
                    break;
            }
            ctx.dontGoDeeper();
        } 
        else if ( node instanceof PreprocessorNode ) 
        {
            final PreprocessorNode pn = (PreprocessorNode) node;
            if ( pn.hasType( Preprocessor.INCLUDE_BINARY ) ) 
            {
                if ( isInResolvePhase ) {
                    context.allocateBytes( pn.getFile().size() ); 
                } 
                else 
                {
                    try ( InputStream in = new BufferedInputStream( pn.getFile().createInputStream() ) ) 
                    {
                        int data=0;
                        while ( ( data = in.read() ) != -1 ) 
                        {
                            context.writeByte( data );
                        }
                    } catch (IOException e) {
                        context.message( CompilationMessage.error( context.currentCompilationUnit() , "Failed to read "+pn.getFile(),node ) );
                    }
                }
            }
        }
        return true;
    }    
}