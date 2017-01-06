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
package de.codesourcery.javr.assembler.parser;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import de.codesourcery.javr.assembler.Address;
import de.codesourcery.javr.assembler.ICompilationContext;
import de.codesourcery.javr.assembler.parser.ast.ASTNode;
import de.codesourcery.javr.assembler.parser.ast.CharacterLiteralNode;
import de.codesourcery.javr.assembler.parser.ast.CurrentAddressNode;
import de.codesourcery.javr.assembler.parser.ast.ExpressionNode;
import de.codesourcery.javr.assembler.parser.ast.FunctionCallNode;
import de.codesourcery.javr.assembler.parser.ast.IValueNode;
import de.codesourcery.javr.assembler.parser.ast.IdentifierNode;
import de.codesourcery.javr.assembler.parser.ast.NumberLiteralNode;
import de.codesourcery.javr.assembler.parser.ast.OperatorNode;
import de.codesourcery.javr.assembler.parser.ast.Resolvable;
import de.codesourcery.javr.assembler.parser.ast.StringLiteral;
import de.codesourcery.javr.assembler.symbols.Symbol;
import de.codesourcery.javr.assembler.symbols.Symbol.Type;

/**
 * Enumeration of operators understood by the expression parser.
 *
 * @author tobias.gierke@code-sourcery.de
 */
public enum OperatorType
{
    UNARY_MINUS("-",1,11),
    LOGICAL_NOT("!",1,10),
    BITWISE_NEGATION("~",1,10),
    DIVIDE("/",2,9),
    TIMES("*",2,9),
    BINARY_MINUS("-",2,8),
    PLUS("+",2,7),
    SHIFT_LEFT("<<",2,6),
    SHIFT_RIGHT(">>",2,6),
    GT(">",2,5),
    LT("<",2,5),
    GTE(">=",2,5),
    LTE("<=",2,5),    
    REF_EQ("==",2,4),
    REF_NEQ("!=",2,4),    
    BITWISE_AND("&",2,3),
    BITWISE_OR("|",2,2),
    LOGICAL_AND("&&",2,1),
    LOGICAL_OR("||",2,0);
    
    /*
     *  1. ||
     *  2. &&
     *  3. |
     *  4. &
     *  5. == !=
     *  6. < > >= <=
     *  7. << >>
     *  8. - (binary ) +
     *  9. * /
     *  10. ~ !
     *  11. - (unary)
     */

    private final String symbol;
    private final int argumentCount;
    private final int precedence;

    private OperatorType(String op,int argumentCount,int precedence) {
        this.symbol=op;
        this.precedence = precedence;
        this.argumentCount = argumentCount;
    }

    public boolean isLeftAssociative() {
        return true;
    }

    public final boolean isRightAssociative() {
        return ! isLeftAssociative();
    }

    public int getPrecedence() {
        return this.precedence;
    }

    public boolean isArithmeticOperator() {
        return false;
    }

    public String toPrettyString() {
        return this.symbol;
    }

    public int getArgumentCount() {
        return this.argumentCount;
    }

    public String getSymbol() {
        return this.symbol;
    }

    public final boolean matchesSymbol(String s) {
        return s.equalsIgnoreCase( this.symbol );
    }

    public static OperatorType getExactMatch(String input)
    {
        final List<OperatorType> candidates = Arrays.stream( values() ).filter( operator -> operator.isHandledByLexer() && operator.matchesSymbol( input ) ).collect(Collectors.toList());
        if ( candidates.size() > 1 ) {
            throw new IllegalArgumentException("Found "+candidates.size()+" matching operators for symbol '"+input+"' , expected exactly one");
        }
        return candidates.isEmpty() ? null : candidates.get(0);
    }

    public static boolean mayBeOperator(String input) {
        final String s = input.toLowerCase();
        return Arrays.stream( values() ).anyMatch( operator -> operator.isHandledByLexer() && operator.symbol.startsWith(s) );
    }
    
    public static boolean mayBeOperator(char input) {
        return Arrays.stream( values() ).anyMatch( operator -> operator.isHandledByLexer() && operator.symbol.charAt(0) == input );
    }    

    public boolean isHandledByLexer() {
        return true;
    }

    public final boolean isComparisonOperator()
    {
        switch(this) {
            case GT:
            case GTE:
            case LT:
            case LTE:
            case REF_EQ:
            case REF_NEQ:
                return true;
            default:
                return false;
        }
    }

    public static OperatorType getNegatedComparisonOperator(OperatorType t)
    {
        switch(t)
        {
            case GT:  //   >
                return LTE;
            case GTE: //   >=
                return LT;
            case LT:  //   <
                return GTE;
            case LTE: //   <=
                return GT;
            case REF_EQ: // ==
                return REF_NEQ;
            case REF_NEQ: // !=
                return REF_EQ;
            default:
                throw new IllegalArgumentException("Cannot negate non-comparison operator: "+t);
        }
    }
    
    public static Number evaluateToNumber(ASTNode node,ICompilationContext context) 
    {
        Object result = evaluate(node,context);
        if ( result == null ) {
            return null;
        }
        if ( result != null && !(result instanceof Number) ) {
            context.error("Expected node "+node+" to evaluate to a number but got "+result,node);
            return null;
        }
        return (Number) result;
    }
    
    public static Object evaluate(ASTNode node,ICompilationContext context) 
    {
        if ( node instanceof ExpressionNode ) {
            return evaluate( ((ExpressionNode) node).child(0) , context);
        }
        if ( node instanceof FunctionCallNode ) {
            return evaluateBuiltInFunction( (FunctionCallNode) node , context );
        }
        if ( node instanceof IdentifierNode) {
            return context.currentSymbolTable().get( ((IdentifierNode) node).name ).getValue();
        }
        if ( node instanceof CurrentAddressNode ) {
            return ((CurrentAddressNode ) node).getValue();
        }
        if ( node instanceof StringLiteral) {
            return ((StringLiteral) node).value;
        }
        if ( node instanceof CharacterLiteralNode) {
            return (int) ((CharacterLiteralNode) node).value;
        }        
        if ( node instanceof NumberLiteralNode) {
            return ((NumberLiteralNode) node).getValue();
        }          
        if( node instanceof OperatorNode ) 
        {
            final OperatorType type = ((OperatorNode) node).type;
            final Object value1 = node.childCount() >= 1 ? evaluate( node.child(0) , context ) : null;
            final Object value2 = node.childCount() >= 2 ? evaluate( node.child(1) , context ) : null;
            final int argCount = ( value1 == null ? 0 : 1 ) + ( value2 == null ? 0 : 1 );
            if ( argCount != type.getArgumentCount() ) 
            {
                context.error("Operator '"+type.symbol+"' requires "+type.getArgumentCount()+" arguments , got "+argCount,node);
                return null;
            }
            switch(type) 
            {
                case REF_EQ:
                case REF_NEQ:
                    if ( value1.getClass() != value2.getClass() ) {
                        context.error("'==' / '!=' operators require arguments of the same type, got "+value1.getClass().getSimpleName()+" and "+value2.getClass().getSimpleName(),node);
                        return null;
                    }
                    return type == REF_EQ ? value1.equals(value2) : ! value1.equals(value2); 
                default:
                    // $FALL-THROUGH$$
            }
            switch( type ) {
                // logical operators
                case LOGICAL_NOT:
                    if ( !(value1 instanceof Boolean)) {
                        context.error("'!' operator requires a boolean argument",node);
                        return null;
                    }
                    return ! ((Boolean) value1).booleanValue();
                case LOGICAL_AND:
                case LOGICAL_OR:
                    if ( !(value1 instanceof Boolean)) {
                        context.error("'"+type.symbol+"' operator requires boolean arguments",node);
                        return null;
                    }
                    if ( !(value2 instanceof Boolean)) {
                        context.error("'"+type.symbol+"' operator requires boolean arguments",node);
                        return null;
                    }              
                    final boolean b1 = ((Boolean) value1).booleanValue();
                    final boolean b2 = ((Boolean) value2).booleanValue();
                    switch( type ) 
                    {
                        case LOGICAL_AND:
                            return b1 & b2;
                        case LOGICAL_OR:
                            return b1 | b2;
                        case REF_EQ:
                            return b1 == b2;
                        case REF_NEQ:
                            return b1 != b2;
                    }
                    throw new RuntimeException("Internal error - unreachable code reached");
                default:
                    // $$FALL-THROUGH
            }
            
            final long num1 = value1 == null ? 0 : toLong( value1 );
            final long num2 = value2 == null ? 0 : toLong( value2 );            
            switch( type ) 
            {
                case SHIFT_LEFT:
                    return num1 << num2;
                case SHIFT_RIGHT:
                    return num1 >>> num2;                    
                case BINARY_MINUS:
                    return num1 - num2;
                case BITWISE_AND:
                    return num1 & num2;
                case BITWISE_NEGATION:
                    return ~num1;
                case BITWISE_OR:
                    return num1 | num2;
                case DIVIDE:
                    return num1 / num2;
                case GT:
                    return num1 > num2;
                case GTE:
                    return num1 >= num2;
                case LT:
                    return num1 < num2;
                case LTE:
                    return num1 <= num2;
                case PLUS:
                    return num1+num2;                    
                case TIMES:
                    return num1*num2;
                case UNARY_MINUS:
                    return -num1;
                default:
            }
        }
        throw new RuntimeException("Internal error - don't know how to evaluate "+node.getClass().getName());
    }
    
    private static long toLong(Object o) 
    {
        if ( o instanceof Number ) {
            return ((Number) o).longValue();
        }
        if ( o instanceof Address ) {
            return ((Address) o).getByteAddress();
        }
        throw new RuntimeException("Don't know to create number from "+o);
    }
    
    private static Object evaluateBuiltInFunction(FunctionCallNode fn,ICompilationContext context) 
    {
        if ( fn.childCount() == 1 ) 
        {
            final IValueNode child = (IValueNode) fn.child(0);
            final String name = fn.functionName.value;
            if ( FunctionCallNode.BUILDIN_FUNCTION_DEFINED.equals( fn.functionName ) )
            {
                if ( ! (child instanceof IdentifierNode ) ) {
                    context.error("Expected an identifier but got "+child, child);
                    return false;
                }
                final Identifier identifier = ((IdentifierNode) child).name;
                final Optional<Symbol> result = context.currentSymbolTable().maybeGet( identifier );
                return result.isPresent() && result.get().getType() == Type.PREPROCESSOR_MACRO;
            } 
            if ( name.equals("HIGH") || name.equals("LOW" ) ) 
            {
                if ( child instanceof Resolvable) {
                    ((Resolvable) child).resolve( context );
                }
                Number v = (Number) child.getValue();
                if ( v == null ) {
                    return false;
                }
                
                switch ( name ) {
                    case "HIGH": v = ( v.intValue() >>> 8 ) & 0xff; break;
                    case "LOW ": v = v.intValue() & 0xff; break;
                    default: throw new RuntimeException("Unreachable code reached");
                }
                return v;
            }
        }
        context.error("Not a built-in function: "+fn.functionName,fn);
        return null;
    }
}