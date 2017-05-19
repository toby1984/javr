package de.codesourcery.javr.assembler;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

import org.apache.commons.beanutils.converters.AbstractArrayConverter;
import org.apache.commons.lang3.Validate;

import de.codesourcery.javr.assembler.arch.AbstractAchitecture;
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
        public final ASTNode astNode;
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

        @Override
        public String toString() 
        {
            return "Evaluated[ value="+value+",node: "+astNode+"]";
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
        
        public Evaluated child(int idx) {
            return children.get( idx );
        }

        public boolean isOperator(OperatorType t) {
            return astNode.isOperator( t );
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
            for (int i = 0 , l = children.size() ; i < l ; i++) 
            {
                final Evaluated c = children.get(i);
                c.visitDepthFirst( v );
            }
            v.accept( this );
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

        public boolean hasReloctableIdentifier(SymbolTable table) 
        {
            return isIdentifierInNeedOfRelocation( astNode , table );
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
                if ( ! pushDownConstants( tree ) ) { // try to push down constants that are below binary +,-,*,/ operators with the same precedence 
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
                throw new RuntimeException("Internal error,unreachable code reached");
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
            return p1.hasReloctableIdentifier( table );
        }
        
        public boolean p2IsSymbol(SymbolTable table) {
            return p2 != null && p2.hasReloctableIdentifier( table );
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
            return p1.hasReloctableIdentifier(table) || ( p2 != null && p2.hasReloctableIdentifier(table) );
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

    static boolean pushDownConstants(Evaluated input) 
    {
        final boolean[] nodesReordered = {false};
        final Consumer<Evaluated> visitor = new Consumer<Evaluated>() 
        {
            @Override
            public void accept(Evaluated node) 
            {
                if ( node.isOperator() && node.parent != null && node.parent.isOperator() )
                {
                    final Evaluated child = node;
                    final Evaluated parent = node.parent;
                    
                    final OperatorNode childOp = child.operator();
                    final OperatorNode parentOp = parent.operator();

                    if ( childOp.type.isPlusMinusTimesDivide() && parentOp.type.isPlusMinusTimesDivide() ) 
                    {
                        if ( childOp.type.hasSamePrecedenceAs( parentOp.type ) )
                        {
                            final Evaluated otherChild = parent.otherChild( child );
                            if ( otherChild.isNumber() ) 
                            {
                                final Evaluated child0 = child.child(0);
                                final Evaluated child1 = child.child(1);
                                if ( child0.isNumber() ) 
                                {
                                    otherChild.swapWith( child.otherChild( child0 ) );
                                    nodesReordered[0] = true;
                                } 
                                else if ( child1.isNumber() ) 
                                {
                                    otherChild.swapWith( child.otherChild( child1 ) );
                                    nodesReordered[0] = true;
                                }
                            }
                        }
                    }
                }
            }
        };
        input.visitDepthFirst(visitor);
        return nodesReordered[0];
    }
    
    static Evaluated convert(ASTNode subtree) 
    {
        if ( subtree instanceof ExpressionNode ) 
        {
            return convert( subtree.child(0) );
        }
        final Evaluated result = new Evaluated( subtree );
        for ( ASTNode child : subtree.children() ) 
        {
            result.addChild( convert( child ) );
        }
        return result;
    }

    static Evaluated reduce(Evaluated n,SymbolTable symbolTable)
    {
        if ( n.isNumber() ) {
            return n;
        }
        final ASTNode subtree = n.astNode;
        if ( subtree.hasNoChildren() )
        {
            final Object value;
            if ( subtree instanceof NumberLiteralNode ) {
                value = ((NumberLiteralNode) subtree).getValue();
            } else if ( subtree instanceof IdentifierNode) {
                final Symbol symbol = ((IdentifierNode) subtree).getSymbol();
                if ( symbol == null ) {
                    throw new RuntimeException("AST node "+subtree+" has NULL symbol ?");
                }
                if ( symbol.hasType( Type.ADDRESS_LABEL ) ) 
                {
                    value = symbol;
                } 
                else {
                    value = symbol.getValue();
                    if ( value == null ) {
                        throw new RuntimeException("Internal error, symbol "+symbol+" has no value?");
                    }
                }
            } 
            else 
            {
                final long tmp = AbstractAchitecture.toIntValue( ((IValueNode) subtree).getValue() );
                if ( tmp == AbstractAchitecture.VALUE_UNAVAILABLE ) {
                    throw new RuntimeException("Failed to evaluate "+subtree);
                }
                value = (int) tmp;
            }
            n.value = value;
            return n;
        }
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
                    if ( isIdentifierInNeedOfRelocation( v1.astNode , symbolTable ) && isIdentifierInNeedOfRelocation( v2.astNode , symbolTable ) ) 
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
        throw new IllegalArgumentException("Called with AST node that is not a valid part of a relocatable expression");
    }
 
    static int getAddress(Symbol s) 
    {
        long result = AbstractAchitecture.toIntValue( s.getValue() );
        if ( result == AbstractAchitecture.VALUE_UNAVAILABLE) {
            throw new RuntimeException("Internal error,failed to determine address of symbol "+s);
        }
        return (int) result;
    }
    
    static Symbol getSymbol(ASTNode node) {
        return ((IdentifierNode) node).getSymbol();
    }
}