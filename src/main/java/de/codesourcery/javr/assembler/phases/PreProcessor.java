package de.codesourcery.javr.assembler.phases;

import java.util.Stack;
import java.util.stream.Collectors;

import de.codesourcery.javr.assembler.ICompilationContext;
import de.codesourcery.javr.assembler.parser.Parser.CompilationMessage;
import de.codesourcery.javr.assembler.parser.Parser.Severity;
import de.codesourcery.javr.assembler.parser.ast.AST;
import de.codesourcery.javr.assembler.parser.ast.ASTNode;
import de.codesourcery.javr.assembler.parser.ast.ASTNode.IASTVisitor;
import de.codesourcery.javr.assembler.parser.ast.ASTNode.IIterationContext;
import de.codesourcery.javr.assembler.parser.ast.FunctionDefinitionNode;
import de.codesourcery.javr.assembler.parser.ast.IValueNode;
import de.codesourcery.javr.assembler.parser.ast.PreprocessorNode;
import de.codesourcery.javr.assembler.parser.ast.Resolvable;
import de.codesourcery.javr.assembler.parser.ast.StatementNode;
import de.codesourcery.javr.assembler.symbols.Symbol;
import de.codesourcery.javr.assembler.symbols.Symbol.Type;

public class PreProcessor 
{
    private int resumeDepth = -1;
    private boolean skipToEndIf = false;

    private final Stack<ASTNode> ifDefStack = new Stack<>();

    public void preprocess(AST ast,ICompilationContext ctx) 
    {
        ast.visitBreadthFirst( createVisitor(ctx) );
        
        if ( ! ifDefStack.isEmpty() ) {
            ctx.message( CompilationMessage.error( "Missing #endif" , ifDefStack.peek() ) );
        }           
    }

    private IASTVisitor createVisitor(ICompilationContext ctx) 
    {
        final IASTVisitor n = (node,itContext) -> visitNode( ctx , node , itContext );
        return n;
    }

    protected void visitNode(ICompilationContext context, ASTNode node,IIterationContext ctx) 
    {
        if ( node.isSkip() ) 
        {
            return;
        }
        
        if ( skipToEndIf ) 
        {
            if ( node instanceof StatementNode ) 
            {
                final IASTVisitor createVisitor = createVisitor( context );
                for (ASTNode child : node.children() ) 
                {
                    child.visitBreadthFirst( createVisitor );
                }
                node.markAsSkip();
            } 
            else if ( node instanceof PreprocessorNode ) 
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
                        return;
                    case IF_DEFINE:
                    case IF_NDEFINE:
                        ifDefStack.push( node );
                        return;
                    default:
                        // $$FALL-THROUGH$$
                }
            }
            node.markAsSkip();
            return;
        }

        if ( node instanceof PreprocessorNode ) {

            node.getParent().markAsSkip();
            final PreprocessorNode preproc = (PreprocessorNode) node;
            switch( preproc.type ) 
            {
                case DEFINE:
                    final FunctionDefinitionNode fn = (FunctionDefinitionNode) preproc.child(0);
                    final Symbol s = new Symbol(fn.name,Type.PREPROCESSOR_MACRO,context.currentCompilationUnit(),fn);
                    context.currentSymbolTable().defineSymbol(s);
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
                    if ( ! ((Resolvable) preproc.child(0)).resolve( context ) ) 
                    {
                        return;
                    }
                    Object value = ((IValueNode) preproc.child(0)).getValue();
                    if ( !(value instanceof Boolean ) ) {
                        context.error("Expression did not resolve to a boolean, got "+value,preproc.child(0));
                        return;
                    }
                    final boolean isDefined = ((Boolean) value).booleanValue();
                    ifDefStack.push(node);
                    if ( ! isDefined ) 
                    {
                        resumeDepth = ifDefStack.size()-1;
                        skipToEndIf=true;
                        return;
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
                    System.err.println( compMsg );
                    context.message( compMsg  );
                    break;
                default:
                    // $$FALL-THROUGH$$
            }
        }
        return;
    }

    private String asText(PreprocessorNode node) 
    {
        if ( node.hasChildren() ) {
            throw new RuntimeException("Only supported for nodes without children");
        }
        return node.arguments.stream().collect(Collectors.joining(" "));
    }
}