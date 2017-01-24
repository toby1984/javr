package de.codesourcery.javr.assembler.phases;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import de.codesourcery.javr.assembler.ICompilationContext;
import de.codesourcery.javr.assembler.Register;
import de.codesourcery.javr.assembler.parser.Identifier;
import de.codesourcery.javr.assembler.parser.ast.AST;
import de.codesourcery.javr.assembler.parser.ast.ASTNode;
import de.codesourcery.javr.assembler.parser.ast.ASTNode.IASTVisitor;
import de.codesourcery.javr.assembler.parser.ast.ASTNode.IIterationContext;
import de.codesourcery.javr.assembler.parser.ast.DirectiveNode;
import de.codesourcery.javr.assembler.parser.ast.DirectiveNode.Directive;
import de.codesourcery.javr.assembler.parser.ast.IdentifierDefNode;
import de.codesourcery.javr.assembler.parser.ast.IdentifierNode;
import de.codesourcery.javr.assembler.parser.ast.InstructionNode;
import de.codesourcery.javr.assembler.parser.ast.RegisterNode;
import de.codesourcery.javr.assembler.parser.ast.StatementNode;

/**
 * Walks through the AST and replaces all {@link IdentifierNode}s that
 * are below an {@link InstructionNode} with register from .def directives (if any).
 * 
 *
 * @author tobias.gierke@code-sourcery.de
 */
public class SubstituteRegisterAliases implements Phase 
{
    public static final String NAME = "substitute-register-identifiers";
    
    private final Map<Identifier,Register> substitutionMap = new HashMap<>();
    private final Set<Identifier> alreadyDefined= new HashSet<>();
    
    @Override
    public String getName() {
        return NAME;
    }
    
    @Override
    public void beforeRun(ICompilationContext ctx) 
    {
        substitutionMap.clear();
        alreadyDefined.clear();
    }

    @Override
    public void run(ICompilationContext context) throws Exception 
    {
        final AST ast = context.currentCompilationUnit().getAST();
        
        final IASTVisitor visitor = new IASTVisitor() 
        {
            @Override
            public void visit(ASTNode node, IIterationContext ctx) 
            {
                if ( node instanceof DirectiveNode) 
                {
                    final DirectiveNode def = (DirectiveNode) node;
                    
                    if ( def.hasType( Directive.UNDEF ) ) 
                    {
                        final IdentifierDefNode identifier = (IdentifierDefNode) node.child(0);
                        substitutionMap.remove( identifier.name );
                    } 
                    else if ( def.hasType(Directive.DEF ) ) 
                    {
                        final IdentifierDefNode alias = (IdentifierDefNode) def.child(0);
                        
                        if ( Register.isRegisterName( alias.name.value ) ) 
                        {
                            if ( ! context.error( "Identifier cannot be a register name" , alias ) ) 
                            {
                                ctx.stop();
                            }
                            return;
                        }
                        if ( ! context.globalSymbolTable().isDefined( alias.name ) ) 
                        {
                            final RegisterNode reg = (RegisterNode) def.child(1);
                            substitutionMap.put( alias.name , reg.register );
                        } 
                        else 
                        {
                            if ( ! alreadyDefined.contains( alias.name ) ) 
                            {
                                alreadyDefined.add( alias.name );
                                if ( ! context.error( "'"+alias.name+"' is already defined as a symbol" , alias ) ) 
                                {
                                    ctx.stop();

                                }
                            }
                        }
                    }
                }
                else if ( node instanceof IdentifierNode ) 
                {
                    final Register reg = substitutionMap.get( ((IdentifierNode) node).name );
                    if ( reg != null ) 
                    {
                        for ( ASTNode parent = node ; parent instanceof StatementNode == false ; parent = parent.getParent() ) 
                        {
                            if ( parent instanceof InstructionNode ) 
                            {
                                final Identifier identifier = ((IdentifierNode) node).name;
                                context.globalSymbolTable().removeDeclared( identifier );
                                System.out.println("=====> Substitute: "+identifier+" => "+reg);
                                node.replaceWith( new RegisterNode( reg , parent.getTextRegion() ) );
                                break;
                            }
                        }
                    }
                }
            }
        };
        ast.visitBreadthFirst( visitor );
    }
}