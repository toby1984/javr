package de.codesourcery.javr.assembler.phases;

import de.codesourcery.javr.assembler.ICompilationContext;
import de.codesourcery.javr.assembler.Segment;
import de.codesourcery.javr.assembler.exceptions.DuplicateSymbolException;
import de.codesourcery.javr.assembler.parser.Identifier;
import de.codesourcery.javr.assembler.parser.Parser.CompilationMessage;
import de.codesourcery.javr.assembler.parser.ast.AST;
import de.codesourcery.javr.assembler.parser.ast.ASTNode;
import de.codesourcery.javr.assembler.parser.ast.ASTNode.IASTVisitor;
import de.codesourcery.javr.assembler.parser.ast.ASTNode.IIterationContext;
import de.codesourcery.javr.assembler.parser.ast.DirectiveNode;
import de.codesourcery.javr.assembler.parser.ast.DirectiveNode.Directive;
import de.codesourcery.javr.assembler.parser.ast.EquLabelNode;
import de.codesourcery.javr.assembler.parser.ast.IdentifierNode;
import de.codesourcery.javr.assembler.parser.ast.LabelNode;
import de.codesourcery.javr.assembler.symbols.EquSymbol;
import de.codesourcery.javr.assembler.symbols.LabelSymbol;
import de.codesourcery.javr.assembler.symbols.Symbol;

public class GatherSymbols implements Phase 
{
    @Override
    public void run(ICompilationContext context) {

        final AST ast = context.getCompilationUnit().getAST();
        
        final IASTVisitor visitor = new IASTVisitor() {
            
            @Override
            public void visit(ASTNode node, IIterationContext ctx) 
            {
                if ( node instanceof IdentifierNode) {
                    final Identifier identifier = ((IdentifierNode) node).value;
                    context.getSymbolTable().declareSymbol( identifier );
                } 
                else if ( node instanceof LabelNode ) 
                {
                    final LabelNode label = (LabelNode) node;
                    defineSymbol( label , new LabelSymbol( label ) );
                } 
                else if ( node instanceof DirectiveNode ) 
                {
                    final Directive type = ((DirectiveNode) node).directive;
                    switch( type ) 
                    {
                        case CSEG: context.setSegment( Segment.FLASH ); break;
                        case DSEG: context.setSegment( Segment.SRAM ); break;
                        case ESEG: context.setSegment( Segment.EEPROM ); break;
                        case EQU:
                            final Identifier identifier = ((EquLabelNode) node.child(0) ).name;
                            defineSymbol( node.child(0) , new EquSymbol( identifier , (DirectiveNode) node ) );
                            ctx.dontGoDeeper();
                            break;
                        default:
                    }
                }
            }

            private void defineSymbol(ASTNode node,final Symbol<?> symbol) 
            {
                try {
                    context.getSymbolTable().defineSymbol( symbol );
                } catch(DuplicateSymbolException e) {
                    context.message( CompilationMessage.error("Duplicate symbol: "+symbol.name() ,node) );
                }
            }
        };
        ast.visitBreadthFirst( visitor );
    }
}
