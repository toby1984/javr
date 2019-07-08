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
package de.codesourcery.javr.assembler;

import java.util.HashMap;
import java.util.Map;
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
import de.codesourcery.javr.assembler.parser.ast.IntNumberLiteralNode;
import de.codesourcery.javr.assembler.parser.ast.OperatorNode;
import de.codesourcery.javr.assembler.parser.ast.PreprocessorNode;
import de.codesourcery.javr.assembler.parser.ast.RegisterNode;
import de.codesourcery.javr.assembler.parser.ast.StatementNode;
import de.codesourcery.javr.assembler.parser.ast.StringLiteral;
import de.codesourcery.javr.assembler.symbols.Symbol;

public class PrettyPrinter extends AbstractASTVisitor {

    private final StringBuilder buffer = new StringBuilder();

    private String charDelimiter = "'";
    private String stringDelimiter = "\"";
    private String newLineCharacter = "\n";
    private boolean printCompoundRegistersAsRange = true;
    private boolean forceCompoundRegisterNames= false;
    
    private String binaryPrefix = "%";
    private String hexadecimalPrefix = "0x";
    private String indentString = "    ";

    private String localLabelPrefix = ".";
    private String localLabelSuffix = "";

    private String globalLabelPrefix = "";
    private String globalLabelSuffix = ":";
    
    private String initByteLiteral = ".db";
    private String initWordLiteral = ".dw";
    
    private String equDelimiter = " = ";
    
    private String reserveBytesLiteral = ".byte";
    
    private String dataSegmentLiteral = ".dseg";
    
    private boolean gnuGlobals = false;
    
    private boolean gnuSyntax;
    
    private Map<String,String> localSymbolMapping = new HashMap<>(); 
    
    public void setGNUSyntax(boolean gnuSyntax) 
    {
        this.gnuSyntax = gnuSyntax;
        if ( gnuSyntax ) 
        {
            localLabelPrefix = "";
            localLabelSuffix = ":";
            binaryPrefix = "0b";
            printCompoundRegistersAsRange = false;
            initByteLiteral = ".byte";
            initWordLiteral = ".word";
            equDelimiter = ",";
            forceCompoundRegisterNames = true;
            reserveBytesLiteral = ".space";
            dataSegmentLiteral = ".data";
            gnuGlobals = true;
        } else {
            localLabelPrefix = ".";
            localLabelSuffix = "";
            binaryPrefix = "%";
            printCompoundRegistersAsRange = true;
            initByteLiteral = ".db";
            initWordLiteral = ".dw";       
            equDelimiter = " = ";
            forceCompoundRegisterNames = false;
            reserveBytesLiteral = ".byte";
            dataSegmentLiteral = ".dseg";
            gnuGlobals = false;
        }
    }
    
    private String getMappedSymbolName(Symbol s) 
    {
        if ( s.isLocalLabel() ) 
        {
            if ( gnuSyntax ) 
            {
                return getMappedSymbolName( s.getSegment() , s.getGlobalNamePart().value , s.getLocalNamePart().value );
            }
            return s.getLocalNamePart().value;
        } 
        return s.getGlobalNamePart().value;
    }
    
    private String getMappedSymbolName(Segment segment,String globalPart,String localPart) 
    {
        final String key = segment.name()+"."+globalPart+"."+localPart;
        final String mapped = localSymbolMapping.get( key );
        if ( mapped == null ) 
        {
            final String mappedName = "local"+localSymbolMapping.size();
            localSymbolMapping.put( key , mappedName );
            return mappedName;
        }
        return mapped;
    }
    
    public String prettyPrint(ASTNode ast) 
    {
        localSymbolMapping.clear();
        
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
        
        if ( node.register.isCompoundRegister() ) 
        {
            String regName=null;
            switch ( node.register.getRegisterNumber() ) {
                case Register.REG_X: regName = "X"; break;
                case Register.REG_Y: regName = "Y"; break;
                case Register.REG_Z: regName = "Z"; break;
                default:
            }
            if ( forceCompoundRegisterNames && regName != null ) {
                append( regName );
            } 
            else if ( printCompoundRegistersAsRange ) 
            {
                append( "r" ).append( regNum+1 ).append(":r").append( regNum );
            } else {
                append( "r" ).append( regNum );
            }            
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
            case INCLUDE_BINARY:
                append("#incbin ");
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
    protected void visitNode(IntNumberLiteralNode node)
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
    protected void visitNode(LabelNode node) 
    {
        if ( node.isLocal() ) {
            append( localLabelPrefix ).append( getMappedSymbolName( node.getSymbol() ) ).append( localLabelSuffix );
        } else {
            if ( gnuGlobals ) {
                append(".global").append( " " ).append( getMappedSymbolName( node.getSymbol() ) ).append( newLineCharacter );
            }
            append( globalLabelPrefix ).append( getMappedSymbolName( node.getSymbol() ) ).append( globalLabelSuffix );
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
        String funcName = node.functionName.value;
        if ( gnuSyntax ) 
        {
            if ( FunctionCallNode.BUILDIN_FUNCTION_HIGH.equals( node.functionName ) ) {
                funcName = "hi8";
            } else if ( FunctionCallNode.BUILDIN_FUNCTION_LOW.equals( node.functionName ) ) {
                funcName = "lo8";
            }
        } 
        append( funcName );
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
        if ( node.directive == Directive.DEF && gnuSyntax ) {
            append("#define ");
            IdentifierDefNode child = (IdentifierDefNode) node.child(0);
            append( child.name.value );
            append(" ");
            visit( node.child(1) );
        } 
        else if ( node.directive == Directive.DSEG ) {
            append( dataSegmentLiteral );
        } 
        else if ( node.directive == Directive.RESERVE ) {
            append( reserveBytesLiteral );
            if ( node.hasChildren() ) {
                append( " " );
            }
            visitChildren(node);
        } 
        else if ( node.directive == Directive.INIT_BYTES || node.directive == Directive.INIT_WORDS ) 
        {
            final String literal;
            switch( node.directive ) {
                case INIT_BYTES:
                    literal = initByteLiteral;
                    break;
                case INIT_WORDS:
                    literal = initWordLiteral;
                    break;                    
                default:
                    throw new RuntimeException("Internal error,unreachable code reached");
            }

            if ( node.hasChildren() ) 
            {
                if ( ! (gnuSyntax && node.directive == Directive.INIT_BYTES && needsConversion( node.child(0) ) ) ) {
                    append( literal ).append(" ");
                }
                for ( int i = 0 , len = node.childCount() ; i < len ; i++ ) 
                {
                    final boolean hasMore = (i+1) < len;
                    final ASTNode child = node.child(i);
                    if ( gnuSyntax && node.directive == Directive.INIT_BYTES && needsConversion( child ) ) {
                        append( newLineCharacter ).append( ".asciz ");
                        visit( child );
                        if ( hasMore ) 
                        {
                            if ( !( gnuSyntax && node.directive == Directive.INIT_BYTES && needsConversion( node.child(i+1) ) ) ) {
                                append( newLineCharacter );
                                append( initByteLiteral ).append(" ");
                            }
                        }
                    } else {
                        visit( child );
                        if ( hasMore && ! ( gnuSyntax && needsConversion( node.child(i+1) ) ) ) {
                            append(",");
                        }                        
                    }
                }
            } 
        } 
        else if ( node.directive == Directive.EQU ) 
        {
            append( ".equ ");
            visit( node.child(0) );
            append( equDelimiter );
            visitAnyRemainingChildren( node , 1 );
        } else {
            append( ".").append( node.directive.literal );
            if ( node.hasChildren() ) {
                append( " " );
            }
            visitChildren(node);
        }
    }
    
    private boolean needsConversion(ASTNode node) {
        return node instanceof StringLiteral; 
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
    protected void visitNode(IdentifierNode node) 
    {
        if ( gnuSyntax && Identifier.isLocalGlobalIdentifier( node.name ) ) 
        {
            final String name = getMappedSymbolName( node.getSymbol().getSegment() , Identifier.getGlobalIdentifierPart( node.name ).value , Identifier.getLocalIdentifierPart( node.name ).value );
             append( name  );
             return;
        }
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