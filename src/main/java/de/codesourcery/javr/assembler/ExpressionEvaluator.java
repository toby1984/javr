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

import java.util.Objects;

import de.codesourcery.javr.assembler.parser.OperatorType;
import de.codesourcery.javr.assembler.parser.ast.ASTNode;
import de.codesourcery.javr.assembler.parser.ast.CharacterLiteralNode;
import de.codesourcery.javr.assembler.parser.ast.CurrentAddressNode;
import de.codesourcery.javr.assembler.parser.ast.ExpressionNode;
import de.codesourcery.javr.assembler.parser.ast.FunctionCallNode;
import de.codesourcery.javr.assembler.parser.ast.IValueNode;
import de.codesourcery.javr.assembler.parser.ast.IdentifierNode;
import de.codesourcery.javr.assembler.parser.ast.NumberLiteralNode;
import de.codesourcery.javr.assembler.parser.ast.OperatorNode;
import de.codesourcery.javr.assembler.parser.ast.StringLiteral;
import de.codesourcery.javr.assembler.symbols.Symbol;

public class ExpressionEvaluator 
{
    protected final ICompilationContext context;
    
    public ExpressionEvaluator(ICompilationContext context) 
    {
        this.context = context;
    }
    
    private Object evaluate(ASTNode node) 
    {
        if ( node instanceof IValueNode) {
            return evaluate( (IValueNode) node);
        }
        throw new RuntimeException("Internal error, not a value node: "+node);
    }
    
    public Object evaluate(IValueNode node) 
    {
        if ( node instanceof CharacterLiteralNode || node instanceof NumberLiteralNode || node instanceof StringLiteral ) 
        {
            return node.getValue();
        } 
        if ( node instanceof CurrentAddressNode ) 
        {
            return context.currentAddress();
        } 
        if ( node instanceof ExpressionNode ) 
        {
            return evaluate( (IValueNode) node.child(0) );
        } 
        if ( node instanceof FunctionCallNode ) 
        {
            final FunctionCallNode fn = (FunctionCallNode) node;
            if ( fn.functionName.equals( FunctionCallNode.BUILDIN_FUNCTION_HIGH ) ) 
            {
                final Object value = evaluate( fn.child(0) );
                if ( value instanceof Number == false ) {
                    throw new RuntimeException("Internal error, node did not evaluate to a number but "+value);
                }
                return (((Number) value).intValue() >>> 8) & 0xff; 
            } 
            else if ( fn.functionName.equals( FunctionCallNode.BUILDIN_FUNCTION_LOW ) ) 
            {
                final Object value = evaluate( fn.child(0) );
                if ( value instanceof Number == false ) {
                    throw new RuntimeException("Internal error, node did not evaluate to a number but "+value);
                }
                return ((Number) value).intValue() & 0xff;
            } 
            throw new RuntimeException("Internal error, don't know how to evaluate function call "+fn);
        } 
        if ( node instanceof IdentifierNode ) 
        {
            final IdentifierNode in = (IdentifierNode) node;
            final Symbol s = resolveSymbol( in );
            final Object value = s.getValue();
            if ( value == null ) {
                throw new RuntimeException("Symbol "+s+" has NULL value?");
            }
            return value;
        }
        if ( node instanceof OperatorNode) 
        {
            final OperatorNode op = (OperatorNode) node;
            switch ( op.type ) 
            {
                case BINARY_MINUS:
                    return -intValue( op.child(0) );
                case BITWISE_AND:
                    return intValue( op.lhs() ) & intValue( op.rhs() );
                case BITWISE_NEGATION:
                    return ~intValue( op.child(0) );
                case BITWISE_OR:
                    return intValue( op.lhs() ) | intValue( op.rhs() );
                case DIVIDE:
                    return intValue( op.lhs() ) / intValue( op.rhs() );
                case GT:
                    return intValue( op.lhs() ) > intValue( op.rhs() );
                case GTE:
                    return intValue( op.lhs() ) >= intValue( op.rhs() );
                case LOGICAL_AND:
                    return booleanValue( op.lhs() ) && booleanValue( op.rhs() );                    
                case LOGICAL_NOT:
                    return ! booleanValue( op.lhs() );
                case LOGICAL_OR:
                    return booleanValue( op.lhs() ) || booleanValue( op.rhs() );                   
                case LT:
                    return intValue( op.lhs() ) < intValue( op.rhs() );
                case LTE:
                    return intValue( op.lhs() ) <= intValue( op.rhs() );
                case PLUS:
                    return intValue( op.lhs() ) + intValue( op.rhs() );
                case REF_EQ:
                case REF_NEQ:                    
                    Object lhs = evaluate( op.lhs() );
                    if ( lhs == null ) {
                        final String opName = op.type == OperatorType.REF_EQ ? "==" : "!=";
                        throw new RuntimeException("Internal error, LHS of "+opName+" operation evaluated to NULL: "+op.lhs());
                    }
                    Object rhs = evaluate( op.rhs() );
                    if ( rhs == null ) {
                        final String opName = op.type == OperatorType.REF_EQ ? "==" : "!=";
                        throw new RuntimeException("Internal error, RHS of "+opName+" operation evaluated to NULL: "+op.rhs());
                    }         
                    if ( lhs.getClass() != rhs.getClass() ) {
                        final String opName = op.type == OperatorType.REF_EQ ? "==" : "!=";
                        throw new RuntimeException( opName+" operator requires expressions to be of same type but got lhs: "+lhs.getClass().getName()+" , rhs: "+rhs.getClass().getName());
                    }
                    if ( op.type == OperatorType.REF_EQ ) {
                        return Objects.equals( lhs , rhs );
                    }
                    return ! Objects.equals( lhs , rhs );
                case SHIFT_LEFT:
                    return intValue( op.lhs() ) << intValue( op.rhs() );
                case SHIFT_RIGHT:
                    return intValue( op.lhs() ) >>> intValue( op.rhs() );                    
                case TIMES:
                    return intValue( op.lhs() ) * intValue( op.rhs() );
                case UNARY_MINUS:
                    return -intValue( op.lhs() );
                default:
                    throw new RuntimeException("Internal error, don't know how to evaluate operator "+op.type);
            }
        }
        throw new RuntimeException("Internal error, unhandled value node: "+node);
    }
    
    private int intValue(ASTNode node) 
    {
        Object value = evaluate(node);
        if ( value instanceof Number == false ) {
            throw new RuntimeException("Internal error, node did not evaluate to a number but "+value);
        }
        return ((Number) value).intValue();
    }
    
    private boolean booleanValue(ASTNode node) 
    {
        Object value = evaluate(node);
        if ( value instanceof Boolean == false ) {
            throw new RuntimeException("Internal error, node did not evaluate to a boolean but "+value);
        }
        return ((Boolean) value).booleanValue();
    }    
    
    protected Symbol resolveSymbol(IdentifierNode in) {
        final Symbol result = in.getSymbol();
        if ( result == null ) {
            throw new RuntimeException("Internal error, failed to get value for symbol of "+in);
        }
        return result;
    }
}
