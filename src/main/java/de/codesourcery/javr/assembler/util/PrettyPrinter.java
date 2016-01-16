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
package de.codesourcery.javr.assembler.util;

import de.codesourcery.javr.assembler.parser.ast.AST;
import de.codesourcery.javr.assembler.parser.ast.ASTNode;
import de.codesourcery.javr.assembler.parser.ast.ASTNode.IASTVisitor;
import de.codesourcery.javr.assembler.parser.ast.ASTNode.IIterationContext;
import de.codesourcery.javr.assembler.parser.ast.CharacterLiteralNode;
import de.codesourcery.javr.assembler.parser.ast.CommentNode;
import de.codesourcery.javr.assembler.parser.ast.CurrentAddressNode;
import de.codesourcery.javr.assembler.parser.ast.ExpressionNode;
import de.codesourcery.javr.assembler.parser.ast.FunctionCallNode;
import de.codesourcery.javr.assembler.parser.ast.IdentifierDefNode;
import de.codesourcery.javr.assembler.parser.ast.IdentifierNode;
import de.codesourcery.javr.assembler.parser.ast.InstructionNode;
import de.codesourcery.javr.assembler.parser.ast.LabelNode;
import de.codesourcery.javr.assembler.parser.ast.NumberLiteralNode;
import de.codesourcery.javr.assembler.parser.ast.OperatorNode;
import de.codesourcery.javr.assembler.parser.ast.RegisterNode;
import de.codesourcery.javr.assembler.parser.ast.StatementNode;
import de.codesourcery.javr.assembler.parser.ast.StringLiteral;

public class PrettyPrinter 
{
    private String newlineString = "\n";
    private String indentString = "    ";
    private String mnemonicToOperandSpacing = "    ";
    private String instructionOperandSeparator = ",";
    
    private String functionCallPrefix = "( ";
    private String functionCallSuffix = " )";
    
    private String operatorArgSpacing = " ";
    
    private String functionCallOperandSeparator = ",";
    private boolean lowercaseMnemonics = true;
    private boolean lowercaseRegisters = true;
    
    private boolean printAllNumberLiteralsAsHex = false;
    
    public synchronized String prettyPrint(ASTNode tree) 
    {
        final Printer p = new Printer();
        tree.visitBreadthFirst( p );
        return p.buffer.toString();
    }
    
    protected final class Printer implements IASTVisitor 
    {
        private final StringBuilder buffer = new StringBuilder();

        
        @Override
        public void visit(ASTNode node, IIterationContext ctx) 
        {
            //
            // TODO: Implement handling of preprocessor/directive nodes
            //
            if ( node instanceof AST ) { /* AST */
                // ok 
            } 
            else if ( node instanceof RegisterNode ) {
                String name = ((RegisterNode) node).getAsString();
                name = lowercaseRegisters ? name.toLowerCase() : name.toUpperCase();
                buffer.append( name );
            }
            else if ( node instanceof OperatorNode) { /* operator */
                final OperatorNode op = (OperatorNode) node;
                if ( op.type.getArgumentCount() == 1 ) 
                {
                    buffer.append( op.type.getSymbol() );
                    buffer.append( operatorArgSpacing );
                    op.child(0).visitBreadthFirst( this );
                } 
                else if ( op.type.getArgumentCount() == 2 ) 
                {
                    op.child(0).visitBreadthFirst( this );
                    buffer.append( operatorArgSpacing );
                    buffer.append( op.type.getSymbol() );
                    buffer.append( operatorArgSpacing );
                    op.child(1).visitBreadthFirst( this );
                }
                else 
                {
                    throw new RuntimeException("Unhandled operand count: "+op.type.getArgumentCount());
                }
                ctx.dontGoDeeper();
            }            
            else if ( node instanceof LabelNode) { /* '(' expr ')' */
                buffer.append( ((LabelNode) node).identifier.getValue() ).append(":");
            }
            else if ( node instanceof ExpressionNode) { /* '(' expr ')' */
                buffer.append("(");
                node.visitBreadthFirst( this );
                buffer.append(")");
                ctx.dontGoDeeper();
            }
            else if ( node instanceof FunctionCallNode) /* function call */
            {
                final FunctionCallNode fn = (FunctionCallNode) node;
                if ( fn.hasNoChildren() ) {
                    buffer.append(fn.functionName.getValue()).append("()");
                } else {
                    buffer.append( functionCallPrefix );
                    for ( int i = 0 ; i < node.childCount() ; i++ ) 
                    {
                        node.child(i).visitBreadthFirst( this );
                        if ( (i+1) < node.childCount() ) 
                        {
                            buffer.append( functionCallOperandSeparator );
                        }
                    }
                    buffer.append( functionCallSuffix );
                }
                ctx.dontGoDeeper();
            }            
            else if ( node instanceof StatementNode ) // statement
            {
                if ( buffer.length() > 0 ) {
                    buffer.append( newlineString );
                }
                if ( node.children().stream().noneMatch( n -> n instanceof LabelNode ) ) 
                {
                    buffer.append( indentString );
                }
            } 
            else if ( node instanceof InstructionNode ) // INSTRUCTION
            {
                // MNEMONIC_TO_OPERAND_SPACING
                String mnemonic = ((InstructionNode) node).instruction.getMnemonic();
                mnemonic = lowercaseMnemonics ? mnemonic.toLowerCase() : mnemonic.toUpperCase();

                buffer.append( mnemonic );
                if ( node.hasChildren() ) {
                    buffer.append( mnemonicToOperandSpacing );
                }
                for ( int i = 0 ; i < node.childCount() ; i++ ) 
                {
                    node.child(i).visitBreadthFirst( this );
                    if ( (i+1) < node.childCount() ) 
                    {
                        buffer.append( instructionOperandSeparator );
                    }
                }
                ctx.dontGoDeeper();
            } else if ( node instanceof IdentifierDefNode ) { 
                buffer.append( ((IdentifierDefNode) node).name.value );
            } else if ( node instanceof IdentifierNode ) { // IDENTIFIER
                buffer.append( ((IdentifierNode) node).name.value );
            } else if ( node instanceof CommentNode ) { // COMMENT
                buffer.append( ((CommentNode) node).value );
            } else if ( node instanceof CharacterLiteralNode ) { // CHAR LITERAL
                buffer.append("'").append( ((CharacterLiteralNode) node).value ).append("'");
            } else if ( node instanceof StringLiteral) { // STRING LITERAL
                buffer.append("\"").append( ((CharacterLiteralNode) node).value ).append("\"");
            } 
            else if ( node instanceof CurrentAddressNode) {
                buffer.append(".");
            }
            else if ( node instanceof NumberLiteralNode) // NUMBER
            {
                final NumberLiteralNode num = (NumberLiteralNode) node;
                if ( isPrintAllNumberLiteralsAsHex() ) 
                {
                    buffer.append("0x"+Integer.toHexString(num.getValue()));
                }
                else 
                {
                    switch(num.getType() ) 
                    {
                        case BINARY:
                            buffer.append( "%"+Integer.toBinaryString( num.getValue() ) );
                            break;
                        case DECIMAL:
                            buffer.append( "%"+Integer.toString( num.getValue() ) );
                            break;
                        case HEXADECIMAL:
                            buffer.append( "0x"+Integer.toHexString( num.getValue() ) );
                            break;
                        default:
                            throw new RuntimeException("Internal error, unhandled type: "+num.getType());
                    }
                }
            } else {
                throw new RuntimeException("Internal error, unhandled node: "+node.getClass().getName());
            }
        }
    }
    
    public String getNewlineString() {
        return newlineString;
    }

    public void setNewlineString(String newlineString) {
        this.newlineString = newlineString;
    }

    public String getIndentString() {
        return indentString;
    }

    public void setIndentString(String indentString) {
        this.indentString = indentString;
    }

    public String getMnemonicToOperandSpacing() {
        return mnemonicToOperandSpacing;
    }

    public void setMnemonicToOperandSpacing(String mnemonicToOperandSpacing) {
        this.mnemonicToOperandSpacing = mnemonicToOperandSpacing;
    }

    public String getInstructionOperandSeparator() {
        return instructionOperandSeparator;
    }

    public void setInstructionOperandSeparator(String instructionOperandSeparator) {
        this.instructionOperandSeparator = instructionOperandSeparator;
    }

    public String getFunctionCallPrefix() {
        return functionCallPrefix;
    }

    public void setFunctionCallPrefix(String functionCallPrefix) {
        this.functionCallPrefix = functionCallPrefix;
    }

    public String getFunctionCallSuffix() {
        return functionCallSuffix;
    }

    public void setFunctionCallSuffix(String functionCallSuffix) {
        this.functionCallSuffix = functionCallSuffix;
    }

    public String getOperatorArgSpacing() {
        return operatorArgSpacing;
    }

    public void setOperatorArgSpacing(String operatorArgSpacing) {
        this.operatorArgSpacing = operatorArgSpacing;
    }

    public String getFunctionCallOperandSeparator() {
        return functionCallOperandSeparator;
    }

    public void setFunctionCallOperandSeparator(
            String functionCallOperandSeparator) {
        this.functionCallOperandSeparator = functionCallOperandSeparator;
    }

    public boolean isLowercaseMnemonics() {
        return lowercaseMnemonics;
    }

    public void setLowercaseMnemonics(boolean lowercaseMnemonics) {
        this.lowercaseMnemonics = lowercaseMnemonics;
    }

    public boolean isLowercaseRegisters() {
        return lowercaseRegisters;
    }

    public void setLowercaseRegisters(boolean lowercaseRegisters) {
        this.lowercaseRegisters = lowercaseRegisters;
    }
    
    public void setPrintAllNumberLiteralsAsHex(
            boolean printAllNumberLiteralsAsHex) {
        this.printAllNumberLiteralsAsHex = printAllNumberLiteralsAsHex;
    }
    
    public boolean isPrintAllNumberLiteralsAsHex() {
        return printAllNumberLiteralsAsHex;
    }
}
