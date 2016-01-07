package de.codesourcery.javr.assembler.parser;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import de.codesourcery.javr.assembler.Address;
import de.codesourcery.javr.assembler.parser.ast.ASTNode;
import de.codesourcery.javr.assembler.parser.ast.CharacterLiteralNode;
import de.codesourcery.javr.assembler.parser.ast.CurrentAddressNode;
import de.codesourcery.javr.assembler.parser.ast.ExpressionNode;
import de.codesourcery.javr.assembler.parser.ast.IdentifierNode;
import de.codesourcery.javr.assembler.parser.ast.NumberLiteralNode;
import de.codesourcery.javr.assembler.parser.ast.OperatorNode;
import de.codesourcery.javr.assembler.parser.ast.StringLiteral;
import de.codesourcery.javr.assembler.symbols.SymbolTable;

public enum OperatorType
{
    GT(">",2,6),
    LT("<",2,6),
    GTE(">=",2,6),
    LTE("<=",2,6),
    REF_EQ("==",2,5),
    REF_NEQ("!=",2,5),
    PLUS("+",2,7),
    BINARY_MINUS("-",2,7),
    TIMES("*",2,8),
    DIVIDE("/",2,8),
    LOGICAL_NOT("!",1,9),
    BITWISE_NEGATION("~",1,9),
    UNARY_MINUS("-",1,10),
    LOGICAL_AND("&&",2,2),
    LOGICAL_OR("||",2,1),
    BITWISE_AND("&",2,4),
    BITWISE_OR("|",2,3);

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
    
    public static Number evaluateToNumber(ASTNode node,SymbolTable symbolTable) 
    {
        Object result = evaluate(node,symbolTable);
        if ( result == null ) {
            return null;
        }
        if ( !(result instanceof Number) ) {
            throw new RuntimeException("Expected node "+node+" to evaluate to a number but got "+result);
        }
        return (Number) result;
    }
    
    public static Object evaluate(ASTNode node,SymbolTable symbolTable) 
    {
        if ( node instanceof ExpressionNode ) {
            return evaluate( ((ExpressionNode) node).child(0) , symbolTable );
        }
        if ( node instanceof IdentifierNode) {
            return symbolTable.get( ((IdentifierNode) node).name ).getValue();
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
            final Object value1 = node.childCount() >= 1 ? evaluate( node.child(0) , symbolTable ) : null;
            final Object value2 = node.childCount() >= 2 ? evaluate( node.child(1) , symbolTable ) : null;
            final int argCount = ( value1 == null ? 0 : 1 ) + ( value2 == null ? 0 : 1 );
            if ( argCount != type.getArgumentCount() ) {
                throw new RuntimeException("Operator '"+type.symbol+"' requires "+type.getArgumentCount()+" arguments , got "+argCount);
            }
            switch(type) 
            {
                case REF_EQ:
                case REF_NEQ:
                    if ( value1.getClass() != value2.getClass() ) {
                        throw new RuntimeException("'==' / '!=' operators require arguments of the same type, got "+value1.getClass().getSimpleName()+" and "+value2.getClass().getSimpleName());
                    }
                    return type == REF_EQ ? value1.equals(value2) : ! value1.equals(value2); 
                default:
                    // $FALL-THROUGH$$
            }
            switch( type ) {
                // logical operators
                case LOGICAL_NOT:
                    if ( !(value1 instanceof Boolean)) {
                        throw new RuntimeException("'!' operator requires a boolean argument");
                    }
                    return ! ((Boolean) value1).booleanValue();
                case LOGICAL_AND:
                case LOGICAL_OR:
                    if ( !(value1 instanceof Boolean)) {
                        throw new RuntimeException("'"+type.symbol+"' operator requires boolean arguments");
                    }
                    if ( !(value2 instanceof Boolean)) {
                        throw new RuntimeException("'"+type.symbol+"' operator requires boolean arguments");
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
                    throw new RuntimeException("Unreachable code reached");
                default:
                    // $$FALL-THROUGH
            }
            
            final int num1 = value1 == null ? 0 : toInt( value1 );
            final int num2 = value2 == null ? 0 : toInt( value2 );            
            switch( type ) 
            {
                case BINARY_MINUS:
                    return num1-num2;
                case BITWISE_AND:
                    return num1&num2;
                case BITWISE_NEGATION:
                    return ~num1;
                case BITWISE_OR:
                    return num1|num2;
                case DIVIDE:
                    return num1/num2;
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
                // logical operators
                case LOGICAL_AND:
                case LOGICAL_NOT:
                case LOGICAL_OR:
                case REF_EQ:
                case REF_NEQ:
                    
                default:
                    break;
            }
        }
        throw new RuntimeException("Don't know how to evaluate "+node.getClass().getName());
    }
    
    private static int toInt(Object o) 
    {
        if ( o instanceof Number ) {
            return ((Number) o).intValue();
        }
        if ( o instanceof Address ) {
            return ((Address) o).getByteAddress();
        }
        throw new RuntimeException("Don't know to create number from "+o);
    }
}