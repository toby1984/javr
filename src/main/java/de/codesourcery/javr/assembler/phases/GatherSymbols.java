package de.codesourcery.javr.assembler.phases;

import de.codesourcery.javr.assembler.ICompilationContext;
import de.codesourcery.javr.assembler.exceptions.DuplicateSymbolException;
import de.codesourcery.javr.assembler.parser.Identifier;
import de.codesourcery.javr.assembler.parser.Parser.CompilationMessage;
import de.codesourcery.javr.assembler.parser.ast.AST;
import de.codesourcery.javr.assembler.parser.ast.ASTNode;
import de.codesourcery.javr.assembler.parser.ast.ASTNode.IASTVisitor;
import de.codesourcery.javr.assembler.parser.ast.ASTNode.IIterationContext;
import de.codesourcery.javr.assembler.parser.ast.DirectiveNode;
import de.codesourcery.javr.assembler.parser.ast.EquLabelNode;
import de.codesourcery.javr.assembler.parser.ast.DirectiveNode.Directive;
import de.codesourcery.javr.assembler.parser.ast.FunctionDefinitionNode;
import de.codesourcery.javr.assembler.parser.ast.IdentifierNode;
import de.codesourcery.javr.assembler.parser.ast.LabelNode;
import de.codesourcery.javr.assembler.symbols.Symbol;

public class GatherSymbols extends AbstractPhase
{
    public GatherSymbols() {
    }
    
    @Override
    public void run(ICompilationContext context) {

        final AST ast = context.currentCompilationUnit().getAST();
        
        final IASTVisitor visitor = new IASTVisitor() {
            
            @Override
            public void visit(ASTNode node, IIterationContext ctx) 
            {
                if ( ! visitNode( context , node , ctx ) ) {
                    return;
                }
                
                if ( node instanceof DirectiveNode ) 
                {
                    final DirectiveNode dn = (DirectiveNode) node;
                    if ( dn.is( Directive.EQU ) ) 
                    {
                        final EquLabelNode label = (EquLabelNode) dn.firstChild();
                        declareSymbol(context, label.name );
                    }
                } 
                else if ( node instanceof FunctionDefinitionNode ) 
                {
                    final FunctionDefinitionNode func =(FunctionDefinitionNode) node;
                    defineSymbol( func , new Symbol(func.name,Symbol.Type.MACRO , context.currentCompilationUnit() , func ) ); 
                } 
                else if ( node instanceof IdentifierNode) 
                {
                    final Identifier identifier = ((IdentifierNode) node).name;
                    declareSymbol(context, identifier);
                } 
                else if ( node instanceof LabelNode ) 
                {
                    final LabelNode label = (LabelNode) node;
                    defineSymbol( label , new Symbol( label.identifier , Symbol.Type.LABEL , context.currentCompilationUnit() , label ) );
                } 
            }

            private void declareSymbol(ICompilationContext context,final Identifier identifier) 
            {
                context.currentSymbolTable().declareSymbol( identifier , context.currentCompilationUnit() );
            }

            private void defineSymbol(ASTNode node,final Symbol symbol) 
            {
                try {
                    context.currentSymbolTable().defineSymbol( symbol );
                } 
                catch(DuplicateSymbolException e) 
                {
                    context.message( CompilationMessage.error("Duplicate symbol: "+symbol.name() ,node) );
                }
            }
        };
        ast.visitBreadthFirst( visitor );
    }
}
