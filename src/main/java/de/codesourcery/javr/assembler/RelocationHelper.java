package de.codesourcery.javr.assembler;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.apache.commons.lang3.StringUtils;

import de.codesourcery.javr.assembler.arch.AbstractArchitecture;
import de.codesourcery.javr.assembler.elf.Relocation;
import de.codesourcery.javr.assembler.parser.Identifier;
import de.codesourcery.javr.assembler.parser.OperatorType;
import de.codesourcery.javr.assembler.parser.ast.ASTNode;
import de.codesourcery.javr.assembler.parser.ast.ExpressionNode;
import de.codesourcery.javr.assembler.parser.ast.FunctionCallNode;
import de.codesourcery.javr.assembler.parser.ast.IValueNode;
import de.codesourcery.javr.assembler.parser.ast.IdentifierNode;
import de.codesourcery.javr.assembler.parser.ast.NumberLiteralNode;
import de.codesourcery.javr.assembler.parser.ast.OperatorNode;
import de.codesourcery.javr.assembler.symbols.Symbol;
import de.codesourcery.javr.assembler.symbols.Symbol.Type;

public class RelocationHelper 
{
    interface BooleanFunction 
    {
        public boolean apply(Evaluated node);
    }

    /**
     * Relocation info.
     *
     * @author tobias.gierke@code-sourcery.de
     */
    public static final class RelocationInfo 
    {
       // bitmask that may contain hint bits Relocation.EXPR_FLAG_HI | Relocation.EXPR_FLAG_LO | Relocation.EXPR_FLAG_PM | Relocation.EXPR_FLAG_NEG 
        public int expressionFlags; 
        public final Symbol symbol;
        public final int addent;

        public RelocationInfo(Symbol symbol, int addent) {
            this.symbol = symbol;
            this.addent = addent;
        }

        public RelocationInfo withFlags(int expressionFlags ) {
            this.expressionFlags = expressionFlags ;
            return this;
        }
        public boolean isSameSymbol(Symbol other) 
        {
            return other.name().equals( symbol.name() ) &&
                    other.hasType( symbol.getType() ) &&
                    Objects.equals( other.getSegment() , symbol.getSegment() ) &&
                    other.getCompilationUnit().hasSameResourceAs( symbol.getCompilationUnit() );             
        }
    }   

    static final class Evaluated 
    {
        public ASTNode astNode;
        public Evaluated parent;
        public Object value;
        public final List<Evaluated> children=new ArrayList<>(2);

        private Evaluated(ASTNode astNode) 
        {
            this(astNode,null);
        }

        private Evaluated(ASTNode astNode,Object value) {
            this.value = value;
            this.astNode = astNode;
        }        

        public String printTree() 
        {
            StringBuilder buffer = new StringBuilder();
            printTree(0,buffer);
            return buffer.toString();
        }

        public String printExpression() {
            StringBuilder buffer = new StringBuilder();
            printExpression(buffer);
            return buffer.toString();
        }
        private void printExpression(StringBuilder buffer) {

            if ( value instanceof Number) {
                buffer.append( value );
                return;
            }
            if ( astNode instanceof IdentifierNode ) {
                buffer.append( ((IdentifierNode) astNode).name.value );
                return;
            } 
            if ( astNode instanceof NumberLiteralNode ) {
                buffer.append( ((NumberLiteralNode) astNode).getValue() );
                return;
            } 

            if ( isOperator() )
            {
                switch( getOperatorType().getArgumentCount() ) {
                    case 1:
                        buffer.append( getOperatorType().getSymbol() );
                        child(0).printExpression( buffer );
                        return;
                    case 2:
                        child(0).printExpression( buffer );
                        buffer.append( getOperatorType().getSymbol() );
                        child(1).printExpression( buffer );
                        return;
                    default:
                        throw new RuntimeException("Internal error, don't know how to handle operator with "+getOperatorType().getArgumentCount()+" arguments");
                }
            }
            buffer.append( astNode );
            for ( Evaluated c : children ) 
            {
                c.printExpression( buffer );
            }
        }

        private void printTree(int depth , StringBuilder buffer) {
            buffer.append( StringUtils.repeat(' ' , depth*2 ) );
            if ( astNode.hasNoChildren() ) {
                buffer.append( "value=").append( value ).append(", node=").append( astNode ).append("\n");
            } else {
                if ( astNode instanceof OperatorNode) {
                    buffer.append( "value=").append( value ).append(", OPERATOR ").append( ((OperatorNode) astNode).getOperatorType().getSymbol() ).append("\n");
                } else {
                    buffer.append( "value=").append( value ).append(", node=").append( astNode.getClass().getSimpleName() ).append("\n");
                }
            }
            for ( Evaluated child : children ) 
            {
                child.printTree( depth+1 , buffer);
            }
        }

        @Override
        public String toString() 
        {
            if ( value != null ) {
                return "Evaluated[ value = "+value+" ]";
            }
            if ( astNode instanceof OperatorNode) {
                return "Evaluated [ op = "+((OperatorNode) astNode).getOperatorType().getSymbol()+" ]";
            }
            if ( astNode instanceof IdentifierNode ) {
                return "Evaluated [ identifier = "+((IdentifierNode) astNode).name.value+" ]";
            }
            return "Evaluated [ astNode = "+astNode.getClass().getSimpleName()+" ] ";
        }

        public boolean isBinaryOperator() {
            return isOperator() && getOperatorType().getArgumentCount() == 2;
        }

        public boolean isOperator() {
            return astNode.isOperatorNode();
        }

        public OperatorType getOperatorType() {
            return ((OperatorNode) astNode).type;
        }

        public boolean hasChildren() {
            return childCount() != 0 ;
        }

        public void replaceChild(Evaluated oldChild,Evaluated newChild) 
        {
            for ( int i = 0 , len = children.size() ; i< len ; i++ ) 
            {
                if ( children.get(i) == oldChild ) 
                {
                    children.set( i , newChild );
                    newChild.parent=this;
                    return;
                }
            }
            throw new IllegalArgumentException("Not a child of "+this+": "+oldChild);
        }

        public void swapWith(Evaluated other) 
        {
            Evaluated parent1 = parent;
            Evaluated parent2  = other.parent;
            parent1.replaceChild(this , other );
            parent2.replaceChild( other , this );
        }

        public void swapChildren() 
        {
            if ( childCount() == 2 ) {
                Evaluated tmp = child(0);
                children.set( 0 , child(1) );
                children.set( 1 , tmp);
                return;
            }
            throw new IllegalStateException("Trying to swap child nodes with only "+childCount()+" children ?");
        }

        public Evaluated child(int idx) {
            return children.get( idx );
        }

        public boolean isOperator(OperatorType t) {
            return astNode.isOperator( t );
        }

        public boolean isOperator(OperatorType t1,OperatorType t2) {
            return astNode.isOperator( t1 ) || astNode.isOperator( t2 );
        }        

        public boolean isLeftChild(Evaluated node) {
            return child(0) == node;
        }

        public Evaluated leftChild() {
            return child(0);
        }        

        public Evaluated rightChild() {
            return child(1);
        }

        public int childCount() 
        {
            return children.size();
        }

        public boolean isConstantValue() {
            return value instanceof Number;
        }

        public boolean visitDepthFirstWithCancel(BooleanFunction v) 
        {
            for (int i = 0 ; i < children.size() ; i++) 
            {
                final Evaluated c = children.get(i);
                if ( ! c.visitDepthFirstWithCancel( v ) ) {
                    return false;
                }
            }
            return v.apply( this );
        }        

        public void addChild(Evaluated child) {
            children.add( child );
            child.parent = this;
        }

        public void removeChildren() {
            this.children.clear();
        }

        public boolean hasParent() {
            return parent != null;
        }

        public boolean hasMixedChildTypes() {
            final boolean t1 = leftChild().isConstantValue();
            final boolean t2 = rightChild().isConstantValue();
            return t1 ^ t2;
        }

        public Evaluated getConstantChild()
        {
            final boolean t1 = leftChild().isConstantValue();
            final boolean t2 = rightChild().isConstantValue();
            if ( t1 ^ t2) 
            {
                return t1 ? leftChild() : rightChild();
            }
            throw new IllegalStateException("Illegal state, both children are constants, expected one of them not to be");
        }

        public boolean isRelocatableSymbol() 
        {
            return value instanceof Symbol && needsRelocation((Symbol) value);
        }

        public Symbol getSymbol() 
        {
            if ( value instanceof Symbol == false ) {
                throw new UnsupportedOperationException("Called on node that is no symbol: "+this);
            }
            return (Symbol) value;
        }
        
        public int getAddress() {
            return RelocationHelper.getAddress( getSymbol() );
        }
    }
    
    private static boolean needsRelocation(Symbol s) 
    {
        return s.hasType( Type.ADDRESS_LABEL );
    }

    /**
     * Check whether an AST needs relocation.
     * 
     * @param node
     * @return relocation info or <code>null</code> if the input AST does not need relocation 
     */
    public static RelocationInfo getRelocationInfo(ASTNode node) 
    {
        if ( mayNeedRelocation(node ) ) 
        {
            final Evaluated tree = convert( node );
            while( true ) 
            {
                reduce( tree );
                if ( tree.isConstantValue() ) // trivial case: expression reduced to a single number 
                {
                    return null;
                }
                if ( ! simplify( tree ) ) { // try to push down constants that are below binary +,-,*,/ operators with the same precedence 
                    break;
                }
            }
            // FIXME: Analyze term pairs according to this spec

            // see for example: https://www.ibm.com/support/knowledgecenter/en/SSLTBW_2.1.0/com.ibm.zos.v2r1.asma400/prt.htm
            /*
             * An expression is absolute if its value is unaffected by program relocation. 
             * An expression is relocatable if its value depends upon program relocation. 
             * The two types of expressions, absolute and relocatable, take on these characteristics from the term or terms composing them.
             * 
             * An expression can be absolute even though it contains relocatable terms, if all the relocatable terms are paired. 
             * The pairing of relocatable terms cancels the effect of relocation.
             * 
             * The assembler reduces paired terms to single absolute terms in the intermediate stages of evaluation. 
             * The assembler considers relocatable terms as paired under the following conditions:
             *  - The paired terms must have the same relocatability attribute.
             *  - The paired terms must have opposite signs after all unary operators are resolved. 
             *    In an expression, the paired terms do not have to be contiguous (that is, other terms can come between the paired terms).
             * The following examples show absolute expressions. A is an absolute term; X and Y are relocatable terms with the same relocatability:
             * A-Y+X
             * A
             * A*A
             * X-Y+A
             * (*+*)-(*+*)
             * *-*
             * A reference to the location counter must be paired with another relocatable term from the same control section; that is, with the same relocatability. For example:
             * *-Y  
             * 
             * Relocatability: If two terms are defined in the same control section, they are characterized as having the same relocatability attribute.
             */
            Evaluated root = tree;
            if ( root.isConstantValue() ) {
                return null;
            }
            // not a number =>
            if ( ! root.hasChildren() ) 
            {
                // not a number and no children => must be a symbol that needs resolving
                return new RelocationInfo( root.getSymbol() , getAddress( root.getSymbol() ) );
            }
            // not a number and has children
            if ( root.astNode instanceof FunctionCallNode ) 
            {
                int expressionFlags = 0;                
                // hopefully either HIGH( ... ) or LOW( ... )
                final FunctionCallNode fn = (FunctionCallNode) root.astNode;
                if ( fn.functionName.equals( FunctionCallNode.BUILDIN_FUNCTION_HIGH ) ) {
                    expressionFlags |= Relocation.EXPR_FLAG_HI;
                } else if ( fn.functionName.equals( FunctionCallNode.BUILDIN_FUNCTION_LOW ) ) {
                    expressionFlags |= Relocation.EXPR_FLAG_LO;
                } else {
                    throw new RuntimeException("Unknown function in relocatable expression: "+fn.functionName);
                }
                Evaluated toInspect = root;
                while ( true ) {
                    if ( toInspect.childCount() == 1 ) 
                    {
                        // at this point can only be func( label ) or func( -label )
                        if ( toInspect.child(0).isRelocatableSymbol() ) {
                            return new RelocationInfo( toInspect.child(0).getSymbol() , toInspect.child(0).getAddress() ).withFlags( expressionFlags );
                        }
                        toInspect = toInspect.child(0);
                        if ( toInspect.isOperator( OperatorType.UNARY_MINUS ) ) {
                            expressionFlags |= Relocation.EXPR_FLAG_NEG;
                            continue;
                        }
                    } 
                    break;
                }
                if ( toInspect.childCount() == 2 ) {
                    // at this point can only be func( label + constant ) , func( constant + label ) _OR_func( PM ( label ) ) 
                    Evaluated n1 = toInspect.leftChild();
                    Evaluated n2 = toInspect.rightChild();
                    
                    if ( toInspect.isOperator( OperatorType.SHIFT_RIGHT ) ) 
                    {
                        if ( ! n2.isConstantValue() || ((Number) n2.value).intValue() != 1 ) 
                        {
                            throw new RuntimeException("Only right-shifts by 1 can be relocated");
                        }
                        if ( ! n1.isRelocatableSymbol() ) {
                            throw new RuntimeException("Expected a relocatable symbol as LHS of right-shift operator");
                        }
                        expressionFlags |= Relocation.EXPR_FLAG_PM;
                        return new RelocationInfo(  n1.getSymbol() , getAddress( n1.getSymbol() ) ).withFlags( expressionFlags );
                    }

                    if ( n1.isRelocatableSymbol() && n2.isConstantValue() ) {
                        return new RelocationInfo(  n1.getSymbol() , getAddress( n1.getSymbol() ) + ((Number) n2.value).intValue() ).withFlags( expressionFlags );
                    } 
                    if ( n1.isConstantValue() && n2.isRelocatableSymbol() ) {
                        return new RelocationInfo(  n2.getSymbol() , getAddress( n2.getSymbol() ) + ((Number) n1.value).intValue() ).withFlags( expressionFlags );
                    }                    
                }
            } 
            else 
            {
                /* either
                 * label <op> label (INVALID)
                 * label <op> constant
                 * constant <op> label   
                 * constant <op> constant (BUG, should've been reduced)
                 */
                if (  root.isOperator( OperatorType.PLUS , OperatorType.BINARY_MINUS ) ) 
                {
                    Evaluated n1 = root.leftChild();
                    Evaluated n2 = root.rightChild();

                    if ( n1.isRelocatableSymbol() && n2.isConstantValue() ) {
                        return new RelocationInfo(  n1.getSymbol() , getAddress( n1.getSymbol() ) + ((Number) n2.value).intValue() );
                    } 
                    if ( n1.isConstantValue() && n2.isRelocatableSymbol() ) {
                        return new RelocationInfo(  n2.getSymbol() , getAddress( n2.getSymbol() ) + ((Number) n1.value).intValue() );
                    }
                }
            }
            System.err.println("Tree:");
            System.err.println( tree.printTree() );
            System.err.println("Expression:");
            System.err.println( tree.printExpression() );
            throw new RuntimeException("Internal error, don't know how to relocate this expression: "+root);
        } 
        return null;
    }

    /**
     * Returns whether a given AST contains nodes that refer to relocatable symbols.
     * 
     * @param node
     * @return
     */
    static boolean mayNeedRelocation(ASTNode node) 
    {
        return node.visitDepthFirstWithResult( false , (n,ctx) -> 
        {
            if ( n instanceof IdentifierNode && needsRelocation( ((IdentifierNode) n).getSymbol() ) ) 
            {
                ctx.stop( true );
            }
        });
    }     

    /**
     * Simplifies a given expression as much as possible.
     * 
     * - add symbols that do not need relocation will be replaced by their values
     * - operator nodes that can be reduced will be replaced by their values
     * - function nodes that can be reduced will be replaced by their values
     * - constant values will be moved to the very end of the expression (in hope that we can can reduce them with other constants that also get moved there)  
     * 
     * @param input
     * @return
     */
    static boolean simplify(Evaluated input) 
    {
        moveConstantsRight( input );

        final boolean[] nodesReordered = {false};

        final boolean[] astMutated = { false };
        do 
        {
            astMutated[0] = false;
            final BooleanFunction visitor = new BooleanFunction() 
            {
                @Override
                public boolean apply(Evaluated node) 
                {
                    if ( node.isOperator() && node.hasParent() && node.parent.isOperator() )
                    {
                        final Evaluated child = node;
                        final Evaluated parent = node.parent;

                        if ( child.isOperator( OperatorType.PLUS , OperatorType.BINARY_MINUS) &&
                                parent.isOperator( OperatorType.PLUS , OperatorType.BINARY_MINUS ) &&
                                parent.isLeftChild( child ) &&
                                child.rightChild().isConstantValue() &&
                                ! parent.rightChild().isConstantValue() ) 
                        {
                            final Evaluated c = child.getConstantChild();
                            // System.out.println("PUSH-UP: "+c+" -> "+parent.rightChild());
                            c.swapWith( parent.rightChild() );
                            final ASTNode tmp = parent.astNode;
                            parent.astNode = child.astNode;
                            child.astNode = tmp;
                            nodesReordered[0] = true;
                            astMutated[0] = true;
                            return false;
                        }
                    }
                    return true;
                }
            };
            input.visitDepthFirstWithCancel(visitor);
        } while ( astMutated[0] );
        return nodesReordered[0];
    }

    /**
     * Moves all constants below commutative binary operators to the right.
     * 
     * @param tree
     */
    static void moveConstantsRight(Evaluated tree) 
    {
        final boolean[] moved = {false};

        do {
            moved[0] = false;
            final BooleanFunction visitor = node -> 
            {
                if ( node.isBinaryOperator() && node.childCount() == 2 && node.getOperatorType().isCommutative() ) 
                {
                    if ( node.hasMixedChildTypes() && node.rightChild().isRelocatableSymbol() )
                    {
                        // System.out.println("SWAP: "+node.child(0)+" <-> "+node.child(1));
                        node.swapChildren();
                        moved[0] = true;
                        return false;
                    }
                }
                return true;
            };
            tree.visitDepthFirstWithCancel( visitor );
        } while ( moved[0]);
    }

    /**
     * Converts a pre-processed AST (where AST nodes have symbols assigned and all symbols in turn have had their addresses assigned)
     * into a new tree structure that can be used to derive relocation information.
     * 
     * @param node
     * @return
     */
    static Evaluated convert(ASTNode node) 
    {
        if ( node instanceof NumberLiteralNode ) 
        {
            return new Evaluated( node , ((NumberLiteralNode) node).getValue() );
        }
        if ( node instanceof IdentifierNode) {
            final IdentifierNode id = (IdentifierNode) node;
            if ( ! isRelocatableSymbol( id.getSymbol() ) )
            {
                final long tmp = AbstractArchitecture.toIntValue( ((IValueNode) node).getValue() );
                if ( tmp == AbstractArchitecture.VALUE_UNAVAILABLE ) {
                    throw new RuntimeException("Failed to evaluate "+node);
                }
                final int value = (int) tmp;
                return new Evaluated( node , value );
            } 
            final Symbol symbol = id.getSymbol();
            return new Evaluated( node , symbol );
        }
        if ( node instanceof ExpressionNode ) 
        {
            return convert( node.child(0) );
        }
        final Evaluated result = new Evaluated( node );
        for ( ASTNode child : node.children() ) 
        {
            result.addChild( convert( child ) );
        }
        return result;
    }

    private static boolean isRelocatableSymbol(Symbol s) 
    {
        return s.hasType( Type.ADDRESS_LABEL );
    }

    static void reduceFully(Evaluated tree) 
    {
        while( true ) 
        {
            reduce( tree );
            if ( tree.isConstantValue() ) // trivial case: expression reduced to a single number 
            {
                return;
            }
            if ( ! simplify( tree ) ) {
                return;
            }
        }        
    }
    
    private static Evaluated reduce(Evaluated n)
    {
        if ( n.isConstantValue() || n.isRelocatableSymbol() ) {
            return n;
        }
        final ASTNode astNode = n.astNode;
        
        if ( astNode instanceof FunctionCallNode ) 
        {
            if ( astNode.childCount() > 1 ) 
            {
                for ( Evaluated child : n.children ) {
                    reduce( child );
                }
            }
            if ( n.childCount() == 1 ) 
            {
                if ( n.child(0).isConstantValue() ) {
                    final Identifier fn = ((FunctionCallNode) astNode).functionName;
                    if ( fn.equals( FunctionCallNode.BUILDIN_FUNCTION_HIGH ) ) {
                        n.value = (((Number) n.child(0).value ).intValue() >> 8 ) & 0xff; 
                        n.removeChildren();
                    } else if ( fn.equals( FunctionCallNode.BUILDIN_FUNCTION_LOW ) ) {
                        n.value = ((Number) n.child(0).value ).intValue() & 0xff; 
                        n.removeChildren();
                    }
                } else {
                    return reduce( n.child(0) );
                }
            }
            return n;
        }
        if ( astNode.isOperatorNode() ) 
        {
            final OperatorNode op= astNode.asOperator();
            if ( op.type.getArgumentCount() == 2 ) 
            {
                Evaluated v1 = reduce( n.child(0) );
                Evaluated v2 = reduce( n.child(1) );
                if ( v1.isConstantValue() && v2.isConstantValue() ) {
                    int value1 = ((Number) v1.value).intValue();
                    int value2 = ((Number) v2.value).intValue();
                    final int result;
                    switch( op.type ) 
                    {
                        case BINARY_MINUS: result = value1 - value2; break;
                        case BITWISE_AND: result = value1 & value2 ; break;
                        case BITWISE_OR: result = value1 | value2 ; break;
                        case DIVIDE: result = value1 / value2; break;
                        // FIXME: No overflow checking here...
                        case PLUS: result = value1 + value2; break;
                        case SHIFT_LEFT: result = value1 << value2; break;
                        case SHIFT_RIGHT: result = value1 >> value2; break;
                        case TIMES: result = value1 * value2; break;
                        default:
                            throw new RuntimeException("Operator not allowed in relocatable expression: "+op.type);
                    }
                    // System.out.println("REDUCED: "+value1+" "+op.type.getSymbol()+" "+value2+" => "+result);
                    n.value = result;
                    n.removeChildren();
                    return n;                    
                }
                // special case where we do symA - symB / symA + symB
                if ( v1.isRelocatableSymbol() && v2.isRelocatableSymbol() )
                {
                    if ( op.type == OperatorType.BINARY_MINUS || op.type == OperatorType.PLUS ) 
                    {
                        Symbol sym1 = v1.getSymbol();
                        Symbol sym2 = v2.getSymbol();

                        if ( sym1.getSegment() != sym2.getSegment() ) {
                            throw new RuntimeException("Expression must not refer to symbols in different segments");
                        }
                        if ( op.type == OperatorType.BINARY_MINUS ) {
                            n.value = getAddress( sym1 ) - getAddress( sym2 );
                        } else {
                            n.value = getAddress( sym1 ) + getAddress( sym2 );
                        }
                        n.removeChildren();
                        return n;
                    }
                }
                return n;
            }
            if ( op.type.getArgumentCount() == 1 ) 
            {
                switch( op.type ) 
                {
                    case BITWISE_NEGATION:
                    case UNARY_MINUS:
                        Evaluated v1 = reduce( n.child(0)  );
                        if ( v1.isConstantValue() ) 
                        {
                            final int num = ((Number) v1.value).intValue();
                            if ( op.type == OperatorType.BITWISE_NEGATION) 
                            {
                                n.value = ~num;
                            } else {
                                n.value = -num;
                            }
                            n.removeChildren();
                            return n;
                        }
                        return n;
                    default:
                        throw new RuntimeException("Operator not allowed in relocatable expression: "+op.type);
                }
            }
            throw new RuntimeException("Internal error,unhandled operator node in expression: "+astNode);
        }
        throw new IllegalArgumentException("Called with node "+astNode+" that is not a valid part of a relocatable expression");
    }

    /**
     * Returns the memory address for a given symbol.
     * 
     * @param s
     * @return
     */
    private static int getAddress(Symbol s) 
    {
        long result = AbstractArchitecture.toIntValue( s.getValue() );
        if ( result == AbstractArchitecture.VALUE_UNAVAILABLE) {
            throw new RuntimeException("Internal error,failed to determine address of symbol "+s);
        }
        return (int) result;
    }
}