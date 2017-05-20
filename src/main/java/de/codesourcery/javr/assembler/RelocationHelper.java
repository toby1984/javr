package de.codesourcery.javr.assembler;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

import org.apache.commons.beanutils.converters.AbstractArrayConverter;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;

import de.codesourcery.javr.assembler.arch.AbstractArchitecture;
import de.codesourcery.javr.assembler.parser.OperatorType;
import de.codesourcery.javr.assembler.parser.ast.ASTNode;
import de.codesourcery.javr.assembler.parser.ast.ExpressionNode;
import de.codesourcery.javr.assembler.parser.ast.IValueNode;
import de.codesourcery.javr.assembler.parser.ast.IdentifierNode;
import de.codesourcery.javr.assembler.parser.ast.NumberLiteralNode;
import de.codesourcery.javr.assembler.parser.ast.OperatorNode;
import de.codesourcery.javr.assembler.symbols.Symbol;
import de.codesourcery.javr.assembler.symbols.SymbolTable;
import de.codesourcery.javr.assembler.symbols.Symbol.Type;
import jdk.internal.org.objectweb.asm.commons.GeneratorAdapter;

public class RelocationHelper 
{
    public interface BooleanFunction 
    {
        public boolean apply(Evaluated node);
    }

    public static final class RelocationInfo 
    {
        public final Symbol symbol;
        public final int s;
        public final int addent;

        public RelocationInfo(Symbol symbol, int addent,int s) {
            this.symbol = symbol;
            this.addent = addent;
            this.s = s;
        }

        public boolean isSameSymbol(Symbol other) 
        {
            return other.name().equals( symbol.name() ) &&
                    other.hasType( symbol.getType() ) &&
                    Objects.equals( other.getSegment() , symbol.getSegment() ) &&
                    other.getCompilationUnit().hasSameResourceAs( symbol.getCompilationUnit() );             
        }
    }   

    public static final class Evaluated 
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
        
        public boolean isRelocatableSymbol() {
            return value instanceof Symbol;
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

        public boolean hasNoChildren() {
            return childCount() == 0 ;
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

        public void replaceWith(Evaluated other) 
        {
            parent.replaceChild(this,other);
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

        public boolean isLeftChild(Evaluated node) {
            return child(0) == node;
        }

        public Evaluated leftChild() {
            return child(0);
        }        

        public Evaluated rightChild() {
            return child(1);
        }

        public boolean isRightChild(Evaluated node) {
            return child(1) == node;
        }        

        public Evaluated otherChild(Evaluated reference) {
            if ( children.size() == 2 ) {
                if ( reference == child(0) ) {
                    return child(1);
                }
                return child(0);
            }
            throw new UnsupportedOperationException("Must not be invoked on a node that doesn't have 2 children");
        }

        public OperatorNode operator() {
            return astNode.asOperator();
        }

        public int childCount() 
        {
            return children.size();
        }

        public boolean isNumber() {
            return value instanceof Number;
        }

        public void visitDepthFirst(Consumer<Evaluated> v) 
        {
            for (int i = 0 ; i < children.size() ; i++) 
            {
                final Evaluated c = children.get(i);
                c.visitDepthFirst( v );
            }
            v.accept( this );
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

        public Evaluated addChildren(Evaluated v1,Evaluated... more) 
        {
            Validate.notNull(v1, "v1 must not be NULL");
            this.children.add( v1 );
            v1.parent = this;
            if ( more != null && more.length > 0 ) 
            {
                for (int i = 0; i < more.length; i++) 
                {
                    final Evaluated c = more[i];
                    c.parent = this;
                    this.children.add( c );
                }
            }
            return this;
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

        public boolean isNoReloctableIdentifier(SymbolTable table) 
        {
            return ! isReloctableIdentifier( table );
        }        

        public boolean hasMixedChildTypes() {
            final boolean t1 = leftChild().isNumber();
            final boolean t2 = rightChild().isNumber();
            return t1 ^ t2;
        }
        
        public Evaluated getNumberChild() 
        {
            for ( int i = 0 , len = children.size() ; i < len ; i++ ) {
                Evaluated child = children.get(i);
                if ( child.isNumber() ) {
                    return children.get(i);
                }
            }
            return null;
        }
        
        public boolean hasNumericChild() 
        {
            return getNumberChild() != null;
        }

        public Evaluated getConstantChild()
        {
            final boolean t1 = leftChild().isNumber();
            final boolean t2 = rightChild().isNumber();
            if ( t1 ^ t2) 
            {
                return t1 ? leftChild() : rightChild();
            }
            throw new IllegalStateException("Illegal state, both children are constants, expected one of them not to be");
        }

        public Evaluated getRelocatableChild(SymbolTable table) 
        {
            final boolean t1 = leftChild().isReloctableIdentifier( table );
            final boolean t2 = rightChild().isReloctableIdentifier( table );
            if ( t1 ^ t2) 
            {
                return t1 ? leftChild() : rightChild();
            }
            throw new IllegalStateException("Illegal state, both children require (no) relocation, expected one of them to do"); 
        }

        public boolean isReloctableIdentifier(SymbolTable table) 
        {
            return value instanceof Symbol && ((Symbol) value).hasType( Type.ADDRESS_LABEL );
        }

        public Symbol getSymbol() 
        {
            return ((IdentifierNode) astNode).getSymbol();
        }
    }

    public static RelocationInfo getRelocationInfo(ASTNode node,SymbolTable symbolTable) 
    {
        if ( mayNeedRelocation(node,symbolTable ) ) 
        {
            final Evaluated tree = convert( node );
            while( true ) 
            {
                reduce( tree , symbolTable );
                if ( tree.isNumber() ) // trivial case: expression reduced to a single number 
                {
                    return null;
                }
                if ( ! simplify( tree , symbolTable ) ) { // try to push down constants that are below binary +,-,*,/ operators with the same precedence 
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
            final List<Pair> pairs = toPairs( tree );
            if ( pairs.size() == 1) 
            {
                final Pair pair = pairs.get(0);
                final boolean hasConstantValue = pair.hasConstantValue();
                final boolean hasRelocatableSymbol = pair.hasRelocatableIdentifiers( symbolTable );
                if ( hasConstantValue && ! hasRelocatableSymbol )
                {
                    return null;
                }
                if ( hasConstantValue && hasRelocatableSymbol ) 
                {
                    final Number value = pair.getConstantValue();
                    final Symbol symbol = pair.getRelocatableSymbol( symbolTable );
                    final int address = getAddress( symbol );
                    return new RelocationInfo(symbol,address , value.intValue() );
                }
                if ( ! hasConstantValue && hasRelocatableSymbol ) {
                    final Symbol symbol = pair.getRelocatableSymbol( symbolTable );
                    final int address = getAddress( symbol );
                    return new RelocationInfo(symbol,address , 0 );
                }
                // no constant and no symbol ??
                throw new RuntimeException("Internal error,pair evaluated to neither a constant value nor a symbol ?");
            } 
            else {
                throw new RuntimeException("Internal error,unexpected pair count "+pairs.size());
            }
        } 
        return null;
    }

    static List<Pair> toPairs(Evaluated tree) 
    {
        final List<Pair> result = new ArrayList<>();
        toPairs(tree,result);
        return result;
    }

    private static void toPairs(Evaluated tree,List<Pair> result) 
    {
        if ( tree.hasNoChildren() ) 
        {
            result.add( new Pair(tree) );
            return;
        }
        tree.visitDepthFirst( node -> 
        {
            if ( node.isOperator() ) 
            {
                if ( node.childCount() == 0 ) {
                    result.add( new Pair( node ) );
                    return;
                }

                for ( int i = 0 , len = node.childCount() ; i < len ; i++ ) {
                    if ( node.child(i).hasChildren() ) {
                        return;
                    }
                }
                if ( node.childCount() == 1 ) {
                    result.add( new Pair( node ) );
                } else if ( node.childCount() == 2 ) {
                    result.add( new Pair( node.child(0) , node.child(1) ) );
                } else {
                    throw new RuntimeException("Internal error, unhandled Evaluated with "+node.childCount()+" children ?");
                }
            }
        });
    }

    static final class Pair 
    {
        public final Evaluated p1;
        public final Evaluated p2;

        public Pair(Evaluated p1) 
        {
            this(p1,null);
        }

        public boolean hasConstantValue() 
        {
            return p1IsNumber() || p2IsNumber();
        }

        public Symbol getRelocatableSymbol(SymbolTable table) 
        {
            Symbol s1 = null;
            Symbol s2 = null;
            if ( p1IsSymbol( table ) ) {
                s1 = p1.getSymbol();
            }
            if ( p2IsSymbol( table ) ) {
                s2 = p2.getSymbol();
            }
            if ( s1 != null && s2 != null ) 
            {
                throw new UnsupportedOperationException("Invoked on pair "+this+" that has two relocatable symbols");
            }
            return s1 != null ? s1 : s2;
        }

        public boolean p1IsNumber() {
            return p1.isNumber();
        }

        public boolean p2IsNumber() {
            return p2 != null && p2.isNumber();
        }

        public boolean p1IsSymbol(SymbolTable table) {
            return p1.isReloctableIdentifier( table );
        }

        public boolean p2IsSymbol(SymbolTable table) {
            return p2 != null && p2.isReloctableIdentifier( table );
        }        

        public Number getConstantValue() 
        {
            Number n1 = p1IsNumber()  ? (Number) p1.value : null;
            Number n2 = p2IsNumber()  ? (Number) p2.value : null;
            if ( n1 != null && n2 != null ) 
            {
                // hm, we failed to reduce this term...
                throw new IllegalStateException("Each pair is expected to contain at most one number after reduce(), something went wrong");
            }
            final Number result = n1 != null ? n1 : n2;
            if ( result == null ) {
                throw new UnsupportedOperationException("getConstantValue() called on instance that has no constant value?");
            }
            return result;
        }

        public boolean hasRelocatableIdentifiers(SymbolTable table) {
            return p1.isReloctableIdentifier(table) || ( p2 != null && p2.isReloctableIdentifier(table) );
        }

        public Pair(Evaluated p1,Evaluated p2) 
        {
            if ( p1 == null ) {
                throw new IllegalArgumentException("p1 cannot be NULL");
            }
            this.p1 = p1;
            this.p2 = p2;
        }

        public boolean isComplete() 
        {
            return p1 != null && p2 != null;
        }

        @Override
        public String toString() {
            if ( p1 != null && p2 != null ) {
                return "Pair[ p1="+p1+" , p2="+p2+"]";
            }
            return "Pair[ "+p1+" ]";
        }
    }

    static boolean mayNeedRelocation(ASTNode node,SymbolTable symbolTable) 
    {
        return node.visitDepthFirstWithResult( false , (n,ctx) -> 
        {
            if ( isIdentifierInNeedOfRelocation( n , symbolTable ) ) {
                ctx.stop( true );
            }
        });
    }     

    static boolean isIdentifierInNeedOfRelocation(ASTNode node,SymbolTable symbolTable) 
    {
        if ( node instanceof IdentifierNode) 
        {
            return ((IdentifierNode) node).refersToAddressSymbol( symbolTable );
        }
        return false;
    }    

    static boolean simplify(Evaluated input,SymbolTable table) 
    {
        moveConstantsRight( input , table );

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

                        final OperatorNode childOp = child.operator();
                        final OperatorNode parentOp = parent.operator();

                        if ( childOp.type == OperatorType.PLUS && parentOp.type == OperatorType.BINARY_MINUS )
                        {
                            if ( parent.isLeftChild( child ) && 
                                 child.hasMixedChildTypes() &&
                                 parent.hasNumericChild() ) 
                            {
                                Evaluated c = child.getConstantChild();
                                System.out.println("PUSH-UP: "+c+" -> "+parent.rightChild());
                                c.swapWith( parent.rightChild() );
                                final ASTNode tmp = parent.astNode;
                                parent.astNode = child.astNode;
                                child.astNode = tmp;
                                nodesReordered[0] = true;
                                astMutated[0] = true;
                                return false;
                            }
                        }
                    }
                    return true;
                }
            };
            input.visitDepthFirstWithCancel(visitor);
        } while ( astMutated[0] );
        return nodesReordered[0];
    }

    static void moveConstantsRight(Evaluated tree,SymbolTable table) 
    {
        final boolean[] moved = {false};

        do {
            moved[0] = false;
            final BooleanFunction visitor = node -> 
            {
                if ( node.isBinaryOperator() && node.getOperatorType().isCommutative() ) 
                {
                    if ( node.hasMixedChildTypes() && node.rightChild().isReloctableIdentifier( table ) )
                    {
                        System.out.println("SWAP: "+node.child(0)+" <-> "+node.child(1));
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

    static Evaluated convert(ASTNode node) 
    {
        if ( node instanceof NumberLiteralNode ) 
        {
            return new Evaluated( node , ((NumberLiteralNode) node).getValue() );
        }
        if ( node instanceof IdentifierNode) {
            IdentifierNode id = (IdentifierNode) node;
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

    static void reduceFully(Evaluated tree,SymbolTable symbolTable) 
    {
        while( true ) 
        {
            reduce( tree , symbolTable );
            if ( tree.isNumber() ) // trivial case: expression reduced to a single number 
            {
                return;
            }
            if ( ! simplify( tree , symbolTable ) ) {
                return;
            }
        }        
    }

    private static Evaluated reduce(Evaluated n,SymbolTable symbolTable)
    {
        if ( n.isNumber() || n.isRelocatableSymbol() ) {
            return n;
        }
        final ASTNode subtree = n.astNode;
        if ( subtree.isOperatorNode() ) 
        {
            final OperatorNode op= subtree.asOperator();
            if ( op.type.getArgumentCount() == 2 ) 
            {
                Evaluated v1 = reduce( n.child(0) , symbolTable );
                Evaluated v2 = reduce( n.child(1) , symbolTable );
                if ( !(v1.isNumber() && v2.isNumber() ) ) 
                {
                    // special case where we do symA - symB / symA + symB
                    if ( v1.isReloctableIdentifier( symbolTable ) && v2.isReloctableIdentifier( symbolTable ) ) 
                    {
                        if ( op.type == OperatorType.BINARY_MINUS || op.type == OperatorType.PLUS ) 
                        {
                            Symbol sym1 = getSymbol( v1.astNode );
                            Symbol sym2 = getSymbol( v2.astNode );

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
                System.out.println("REDUCE: "+value1+" "+op.type.getSymbol()+" "+value2+" = "+result);
                n.value = result;
                n.removeChildren();
                return n;
            }
            if ( op.type.getArgumentCount() == 1 ) 
            {
                switch( op.type ) 
                {
                    case BITWISE_NEGATION:
                    case UNARY_MINUS:
                        Evaluated v1 = reduce( n.child(0) , symbolTable  );
                        if ( v1.isNumber() ) 
                        {
                            final int num = ((Number) v1.value).intValue();
                            if ( op.type == OperatorType.BITWISE_NEGATION) 
                            {
                                n.value = ~num;
                            } else {
                                n.value = -num;
                            }
                            return n;
                        }
                        return n;
                    default:
                        throw new RuntimeException("Operator not allowed in relocatable expression: "+op.type);
                }
            }
            throw new RuntimeException("Internal error,unhandled operator node in expression: "+subtree);
        }
        throw new IllegalArgumentException("Called with node "+subtree+" that is not a valid part of a relocatable expression");
    }

    static int getAddress(Symbol s) 
    {
        long result = AbstractArchitecture.toIntValue( s.getValue() );
        if ( result == AbstractArchitecture.VALUE_UNAVAILABLE) {
            throw new RuntimeException("Internal error,failed to determine address of symbol "+s);
        }
        return (int) result;
    }

    static Symbol getSymbol(ASTNode node) {
        return ((IdentifierNode) node).getSymbol();
    }
}