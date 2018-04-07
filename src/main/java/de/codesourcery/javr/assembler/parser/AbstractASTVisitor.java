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
package de.codesourcery.javr.assembler.parser;

import de.codesourcery.javr.assembler.parser.ast.AST;
import de.codesourcery.javr.assembler.parser.ast.ASTNode;
import de.codesourcery.javr.assembler.parser.ast.ArgumentNamesNode;
import de.codesourcery.javr.assembler.parser.ast.CharacterLiteralNode;
import de.codesourcery.javr.assembler.parser.ast.CommentNode;
import de.codesourcery.javr.assembler.parser.ast.CurrentAddressNode;
import de.codesourcery.javr.assembler.parser.ast.DirectiveNode;
import de.codesourcery.javr.assembler.parser.ast.EquLabelNode;
import de.codesourcery.javr.assembler.parser.ast.ExpressionNode;
import de.codesourcery.javr.assembler.parser.ast.FunctionBodyNode;
import de.codesourcery.javr.assembler.parser.ast.FunctionCallNode;
import de.codesourcery.javr.assembler.parser.ast.FunctionDefinitionNode;
import de.codesourcery.javr.assembler.parser.ast.IdentifierDefNode;
import de.codesourcery.javr.assembler.parser.ast.IdentifierNode;
import de.codesourcery.javr.assembler.parser.ast.InstructionNode;
import de.codesourcery.javr.assembler.parser.ast.LabelNode;
import de.codesourcery.javr.assembler.parser.ast.NumberLiteralNode;
import de.codesourcery.javr.assembler.parser.ast.OperatorNode;
import de.codesourcery.javr.assembler.parser.ast.PreprocessorNode;
import de.codesourcery.javr.assembler.parser.ast.RegisterNode;
import de.codesourcery.javr.assembler.parser.ast.StatementNode;
import de.codesourcery.javr.assembler.parser.ast.StringLiteral;

public class AbstractASTVisitor {

    protected final void visit(ASTNode node) 
    {
        if ( node instanceof ArgumentNamesNode ) {
            visitNode( (ArgumentNamesNode) node );
        }
        else if ( node instanceof AST ) {
            visitNode( (AST) node );
        }
        else if ( node instanceof InstructionNode ) {
            visitNode( (InstructionNode) node );
        }
        else if ( node instanceof CharacterLiteralNode ) {
            visitNode( (CharacterLiteralNode) node );
        }
        else if ( node instanceof CommentNode ) {
            visitNode( (CommentNode) node );
        }
        else if ( node instanceof CurrentAddressNode ) {
            visitNode( (CurrentAddressNode) node );
        }
        else if ( node instanceof DirectiveNode ) {
            visitNode( (DirectiveNode) node );
        }
        else if ( node instanceof EquLabelNode ) {
            visitNode( (EquLabelNode) node );
        }
        else if ( node instanceof ExpressionNode ) {
            visitNode( (ExpressionNode) node );
        }
        else if ( node instanceof FunctionBodyNode ) {
            visitNode( (FunctionBodyNode) node );
        }
        else if ( node instanceof FunctionCallNode ) {
            visitNode( (FunctionCallNode) node );
        }
        else if ( node instanceof FunctionDefinitionNode ) {
            visitNode( (FunctionDefinitionNode) node );
        }
        else if ( node instanceof LabelNode ) {
            visitNode( (LabelNode) node );
        }
        else if ( node instanceof NumberLiteralNode ) {
            visitNode( (NumberLiteralNode) node );
        }
        else if ( node instanceof OperatorNode ) {
            visitNode( (OperatorNode) node );
        }
        else if ( node instanceof PreprocessorNode ) {
            visitNode( (PreprocessorNode) node );
        }
        else if ( node instanceof RegisterNode ) {
            visitNode( (RegisterNode) node );
        }
        else if ( node instanceof StatementNode ) {
            visitNode( (StatementNode) node );
        }
        else if ( node instanceof IdentifierNode ) {
            visitNode( (IdentifierNode) node );
        }        
        else if ( node instanceof StringLiteral ) {
            visitNode( (StringLiteral) node );
        }        
        else if ( node instanceof IdentifierDefNode) {
            visitNode( (IdentifierDefNode) node );            
        } else {
            throw new RuntimeException("Internal error, unhandled AST node: "+node);
        }
    }

    protected void visitNode(IdentifierDefNode node) {
        visitChildren( node );
    }

    protected void visitNode(InstructionNode node) {
        visitChildren( node );
    }

    protected final void visitChildren( ASTNode node ) 
    {
        for ( ASTNode n : node.children() ) {
            visit(n);
        }
    }
    
    protected final void visitAnyRemainingChildren(ASTNode node,int firstChildToVisit) 
    {
            for ( int i = firstChildToVisit , len = node.childCount()  ; i < len ; i++ ) {
                visit( node.child(i) );
            }
    }
    
    protected void visitNode(IdentifierNode node) {
        visitChildren(node);
    }

    protected void visitNode(StringLiteral node) {
        visitChildren(node);
    }

    protected void visitNode(StatementNode node) {
        visitChildren(node);
    }

    protected void visitNode(RegisterNode node) {
        visitChildren(node);
    }

    protected void visitNode(PreprocessorNode node) {
        visitChildren(node);
    }

    protected void visitNode(OperatorNode node) {
        visitChildren(node);
    }

    protected void visitNode(NumberLiteralNode node) {
        visitChildren(node);
    }

    protected void visitNode(LabelNode node) {
        visitChildren(node);
    }

    protected void visitNode(FunctionDefinitionNode node) {
        visitChildren(node);
    }

    protected void visitNode(FunctionCallNode node) {
        visitChildren(node);
    }

    protected void visitNode(FunctionBodyNode node) {
        visitChildren(node);
    }

    protected void visitNode(ExpressionNode node) {
        visitChildren(node);
    }

    protected void visitNode(EquLabelNode node) {
        visitChildren(node);
    }

    protected void visitNode(DirectiveNode node) {
        visitChildren(node);
    }

    protected void visitNode(CurrentAddressNode node) {
        visitChildren(node);
    }

    protected void visitNode(CommentNode node) {
        visitChildren(node);
    }

    protected void visitNode(CharacterLiteralNode node) {
        visitChildren(node);
    }

    protected void visitNode(AST node) {
        visitChildren(node);
    }

    protected void visitNode(ArgumentNamesNode node) {
        visitChildren(node);
    }
}