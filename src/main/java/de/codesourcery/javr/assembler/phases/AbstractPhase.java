package de.codesourcery.javr.assembler.phases;

import java.util.Stack;
import java.util.stream.Collectors;

import org.apache.commons.lang3.Validate;

import de.codesourcery.javr.assembler.ICompilationContext;
import de.codesourcery.javr.assembler.Segment;
import de.codesourcery.javr.assembler.parser.Parser.CompilationMessage;
import de.codesourcery.javr.assembler.parser.Parser.Severity;
import de.codesourcery.javr.assembler.parser.ast.ASTNode;
import de.codesourcery.javr.assembler.parser.ast.ASTNode.IIterationContext;
import de.codesourcery.javr.assembler.parser.ast.DirectiveNode;
import de.codesourcery.javr.assembler.parser.ast.DirectiveNode.Directive;
import de.codesourcery.javr.assembler.parser.ast.FunctionDefinitionNode;
import de.codesourcery.javr.assembler.parser.ast.IValueNode;
import de.codesourcery.javr.assembler.parser.ast.PreprocessorNode;
import de.codesourcery.javr.assembler.parser.ast.Resolvable;
import de.codesourcery.javr.assembler.parser.ast.StatementNode;

public abstract class AbstractPhase implements Phase 
{
    private int resumeDepth = -1;
    private boolean skipToEndIf = false;

    private final Stack<ASTNode> ifDefStack = new Stack<>();

    private final String name;
    
    public AbstractPhase(String name) {
        Validate.notBlank(name, "name must not be NULL or blank");
        this.name = name;
    }
    
    @Override
    public final void beforeRun(ICompilationContext ctx) 
    {
        ifDefStack.clear();
        resumeDepth = -1;
        skipToEndIf = false;
    }

    public void afterSuccessfulRun(ICompilationContext ctx) 
    {
        if ( ! ifDefStack.isEmpty() ) {
            ctx.message( CompilationMessage.error( "Missing #endif" , ifDefStack.peek() ) );
        }
    }

    protected boolean visitNode(ICompilationContext context, ASTNode node,IIterationContext ctx) 
    {
        if ( node.isSkip() ) {
            return false;
        }
        if ( skipToEndIf ) 
        {
            System.out.println("SKIPPED: "+node);

            if ( node instanceof PreprocessorNode ) 
            {
                final PreprocessorNode preproc = (PreprocessorNode) node;
                switch( preproc.type ) 
                {
                    case ENDIF:
                        if ( ifDefStack.isEmpty() ) 
                        {
                            context.error("#endif without #ifdef or #ifndef",node);
                        } 
                        else 
                        {
                            ifDefStack.pop();
                            if ( ifDefStack.size() == resumeDepth ) 
                            {
                                skipToEndIf = false;
                                resumeDepth = -1;
                            }
                        }
                        return false;
                    case IF_DEFINE:
                    case IF_NDEFINE:
                        ifDefStack.push( node );
                        return false;
                    default:
                        // $$FALL-THROUGH$$
                }
            }
            if ( ! (node instanceof StatementNode ) ) {
                node.markAsSkip();
            }
            return false;
        }

        if ( node instanceof DirectiveNode )
        {
            if ( ! node.isSkip() ) 
            {
                final Directive directive = ((DirectiveNode) node).directive;
                switch( directive ) 
                {
                    case CSEG: context.setSegment( Segment.FLASH ); break;
                    case DSEG: context.setSegment( Segment.SRAM ) ; break;
                    case ESEG: context.setSegment( Segment.EEPROM ); break;
                    default:
                        // $$FALL-THROUGH$$
                }
            }
        } else if ( node instanceof PreprocessorNode ) {

            final PreprocessorNode preproc = (PreprocessorNode) node;
            switch( preproc.type ) 
            {
                case DEFINE:
                    final FunctionDefinitionNode fn = (FunctionDefinitionNode) preproc.child(0);
                    context.currentSymbolTable().declareSymbol( fn.name , context.currentCompilationUnit() );
                    break;
                case ENDIF:
                    if ( ifDefStack.isEmpty() ) 
                    {
                        context.error("#endif without #ifdef or #ifndef",node);
                    } 
                    else 
                    {
                        ifDefStack.pop();
                    }
                    break;
                case IF_DEFINE:
                case IF_NDEFINE:
                    if ( ! ((Resolvable) preproc.child(0)).resolve( context ) ) {
                        return false;                        
                    }
                    Object value = ((IValueNode) preproc.child(0)).getValue();
                    if ( !(value instanceof Boolean ) ) {
                        context.error("Expression did not resolve to a boolean, got "+value,preproc.child(0));
                        return false;
                    }
                    final boolean isDefined = ((Boolean) value).booleanValue();
                    ifDefStack.push(node);
                    if ( ! isDefined ) 
                    {
                        resumeDepth = ifDefStack.size()-1;
                        skipToEndIf=true;
                        return false;
                    }
                    break;
                case MESSAGE:
                case WARNING:
                case ERROR:
                    final String msg = asText(preproc);
                    final Severity severity;
                    switch( preproc.type) {
                        case MESSAGE: severity = Severity.INFO; break;
                        case WARNING: severity = Severity.WARNING; break;
                        case ERROR:   severity = Severity.ERROR; break;
                        default:
                            throw new RuntimeException("Unhandled switch/case: "+preproc.type);
                    }
                    final CompilationMessage compMsg = new CompilationMessage( severity , msg , node );
                    node.markAsSkip(); // only print/add this message once

                    System.err.println( compMsg );
                    context.message( compMsg  );
                    break;
                default:
                    // $$FALL-THROUGH$$
            }
        }
        return true;
    }

    private String asText(PreprocessorNode node) 
    {
        if ( node.hasChildren() ) {
            throw new RuntimeException("Only supported for nodes without children");
        }
        return "(@ "+node.getTextRegion()+") "+node.arguments.stream().collect(Collectors.joining(" "));
    }
    
    @Override
    public final String getName() {
        return name;
    }
}