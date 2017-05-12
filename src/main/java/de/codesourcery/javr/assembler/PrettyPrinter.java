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
package de.codesourcery.javr.assembler;

import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;

import de.codesourcery.javr.assembler.parser.AbstractASTVisitor;
import de.codesourcery.javr.assembler.parser.Identifier;
import de.codesourcery.javr.assembler.parser.OperatorType;
import de.codesourcery.javr.assembler.parser.ast.ASTNode;
import de.codesourcery.javr.assembler.parser.ast.ArgumentNamesNode;
import de.codesourcery.javr.assembler.parser.ast.CharacterLiteralNode;
import de.codesourcery.javr.assembler.parser.ast.CommentNode;
import de.codesourcery.javr.assembler.parser.ast.CurrentAddressNode;
import de.codesourcery.javr.assembler.parser.ast.DirectiveNode;
import de.codesourcery.javr.assembler.parser.ast.DirectiveNode.Directive;
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

public class PrettyPrinter extends AbstractASTVisitor {

    private final StringBuilder buffer = new StringBuilder();

    private String charDelimiter = "'";
    private String stringDelimiter = "\"";
    private String newLineCharacter = "\n";
    private boolean printCompoundRegistersAsRange = true;
    private String binaryPrefix = "%";
    private String hexadecimalPrefix = "0x";
    private String indentString = "    ";

    private String localLabelPrefix = ".";
    private String localLabelSuffix = "";

    private String globalLabelPrefix = "";
    private String globalLabelSuffix = ":";

    public String prettyPrint(ASTNode ast) 
    {
        buffer.setLength( 0 );
        visit( ast );
        return buffer.toString();
    }

    @Override
    protected void visitNode(StringLiteral node) {
        append( stringDelimiter ).append( node.value ).append( stringDelimiter );
    }

    @Override
    protected void visitNode(StatementNode node) 
    {
        if ( buffer.length() > 0 ) {
            append( newLineCharacter );
        }
        if ( !( node.hasLabel() || node.isCommentOnlyLine() || node.hasDirective() || node.hasPreprocessorNode() ) ) 
        {
            append( indentString );
        }
        super.visitChildren( node );
    }

    @Override
    protected void visitNode(RegisterNode node) 
    {
        int regNum = node.register.getRegisterNumber();
        
        if ( node.register.isPreDecrement() ) {
            append("-");
        }
        if ( printCompoundRegistersAsRange && node.register.isCompoundRegister() ) 
        {
            append( "r" ).append( regNum+1 ).append(":r").append( regNum );
        } else {
            append( "r" ).append( regNum );
        }
        if ( node.register.isPostIncrement() ) {
            append("+");
        }
    }

    private StringBuilder append(String s) {
        return buffer.append( s );
    }

    @Override
    protected void visitNode(PreprocessorNode node) {

        switch ( node.type ) 
        {
            case DEFINE:
                append("#define ");
                break;
            case ENDIF:
                append("#endif ");
                break;
            case ERROR:
                append("#error ");
                break;
            case IF_DEFINE:
                append("#ifdef ");
                break;
            case IF_NDEFINE:
                append("#ifndef ");
                break;
            case INCLUDE:
                append("#include ");
                break;
            case MESSAGE:
                append("#message ");
                break;
            case PRAGMA:
                append("#pragma");
                break;
            case WARNING:
                append("#warn ");
                break;
            default:
                throw new RuntimeException("Internal error,unhandled preprocessor node: "+node);
        }
        if ( node.arguments.size() > 0 || node.hasChildren() ) {
            append(" ");
        }
        if ( node.arguments.size() > 0 ) {
            append( node.arguments.stream().collect( Collectors.joining(" " ) ) );
            if ( node.hasChildren() ) {
                append(" ");
            }
        }        
        visitChildren( node );
    }

    @Override
    protected void visitNode(OperatorNode node) 
    {
        final OperatorType type = node.type;
        switch( type.getArgumentCount() ) {
            case 1:
                append( type.getSymbol() );
                visit( node.child(0) );
                break;
            case 2:
                visit( node.child(0) );
                append( type.getSymbol() );
                visit( node.child(1) );
                break;
            default:
                throw new RuntimeException("Operator has unhandled argument count: "+type);
        }
    }

    @Override
    protected void visitNode(NumberLiteralNode node) 
    {
        final boolean isNegative = node.getValue() < 0;
        final int tmpValue = isNegative ? ~node.getValue() : node.getValue();

        final int byteCount;
        if ( (tmpValue & 0xff) == tmpValue ) {
            byteCount = 1;
        } else if ( (tmpValue & 0xffff) == tmpValue ) {
            byteCount = 2;
        } else if ( (tmpValue & 0xffffffff) == tmpValue ) {
            byteCount = 4;
        } else {
            throw new RuntimeException("Value out of 32-bit range? "+node);
        }

        switch ( node.getType() ) 
        {
            case BINARY:
                String s = StringUtils.leftPad( Integer.toBinaryString( node.getValue() ) ,32 , isNegative ? '1' : '0' );
                s = s.substring( 32 - byteCount*8, 32 );
                append( binaryPrefix ).append( s ); 
                break;
            case DECIMAL:
                append( Integer.toString( node.getValue() ) );
                break;
            case HEXADECIMAL:
                append( hexadecimalPrefix ).append( Integer.toHexString( node.getValue() ) );
                break;
            default:
                throw new RuntimeException("Unhandled number literal type: "+node.getType());
        }
    }

    @Override
    protected void visitNode(LabelNode node) {
        if ( node.isLocal() ) {
            append( localLabelPrefix ).append( node.getSymbol().getLocalNamePart().value ).append( localLabelSuffix );
        } else {
            append( globalLabelPrefix ).append( node.getSymbol().getGlobalNamePart().value ).append( globalLabelSuffix );
        }
        if ( ! node.getStatement().isLabelLineOnly() ) {
            append(" ");
        }
    }

    @Override
    protected void visitNode(FunctionDefinitionNode node) {
        // TODO Auto-generated method stub
        throw new RuntimeException("method not implemented: visitNode");
    }
    
    @Override
    protected void visitNode(FunctionBodyNode node) {
        // TODO Auto-generated method stub
        throw new RuntimeException("method not implemented: visitNode");
    }

    @Override
    protected void visitNode(FunctionCallNode node) 
    {
        append( node.functionName.value );
        append("(");
        visitChildren( node );
        append(")");
    }

    @Override
    protected void visitNode(ExpressionNode node) {
        append("(");
        visitChildren( node );
        append(")");
    }

    @Override
    protected void visitNode(EquLabelNode node) 
    {
        append( node.name.value );
    }

    @Override
    protected void visitNode(DirectiveNode node) 
    {
        if ( node.directive == Directive.INIT_BYTES || node.directive == Directive.INIT_WORDS ) 
        {
            append( "." ).append( node.directive.literal );
            if ( node.hasChildren() ) {
                append(" ");
                for ( int i = 0 , len = node.childCount() ; i < len ; i++ ) {
                    visit( node.child(i) );
                    if ((i+1) < len ) {
                        append(",");
                    }
                }
            } 
        } 
        else if ( node.directive == Directive.EQU ) 
        {
            append( ".equ ");
            visit( node.child(0) );
            append(" = ");
            visitAnyRemainingChildren( node , 1 );
        } else {
            append( ".").append( node.directive.literal );
            if ( node.hasChildren() ) {
                append( " " );
            }
            visitChildren(node);
        }
    }

    @Override
    protected void visitNode(CurrentAddressNode node) {
        append(".");
    }

    @Override
    protected void visitNode(CommentNode node) {
        append( node.value );
    }

    @Override
    protected void visitNode(CharacterLiteralNode node) {
        append( charDelimiter ).append( node.value ).append( charDelimiter );
    }

    @Override
    protected void visitNode(ArgumentNamesNode node) 
    {
        append("(");
        visitChildren( node );
        append(")");
    }

    @Override
    protected void visitNode(InstructionNode node) {
        append( node.instruction.getMnemonic() );
        if ( node.hasChildren() ) 
        {
            append(" ");
            if ( node.childCount() > 1 ) {
                visit( node.child(0) );
                append(" , ");
                visitAnyRemainingChildren( node , 1 );
            } else {
                visitChildren( node );
            }
        }
    }

    @Override
    protected void visitNode(IdentifierNode node) {
        if ( Identifier.isLocalGlobalIdentifier( node.name ) ) {
            append( Identifier.getLocalIdentifierPart( node.name ).value );
        } else {
            append( node.name.value );
        }
    }
    
    protected void visitNode(IdentifierDefNode node) {
        append( node.name.value ).append(" = ");
        visitChildren( node );
    }    
}