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

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.commons.lang3.Validate;

import de.codesourcery.javr.assembler.CompilationUnit;
import de.codesourcery.javr.assembler.ICompilationContext;
import de.codesourcery.javr.assembler.Instruction;
import de.codesourcery.javr.assembler.Register;
import de.codesourcery.javr.assembler.Segment;
import de.codesourcery.javr.assembler.arch.IArchitecture;
import de.codesourcery.javr.assembler.exceptions.DuplicateSymbolException;
import de.codesourcery.javr.assembler.exceptions.ParseException;
import de.codesourcery.javr.assembler.parser.ExpressionToken.ExpressionTokenType;
import de.codesourcery.javr.assembler.parser.ast.AST;
import de.codesourcery.javr.assembler.parser.ast.ASTNode;
import de.codesourcery.javr.assembler.parser.ast.ArgumentNamesNode;
import de.codesourcery.javr.assembler.parser.ast.CharacterLiteralNode;
import de.codesourcery.javr.assembler.parser.ast.CommentNode;
import de.codesourcery.javr.assembler.parser.ast.CurrentAddressNode;
import de.codesourcery.javr.assembler.parser.ast.DirectiveNode;
import de.codesourcery.javr.assembler.parser.ast.DirectiveNode.Directive;
import de.codesourcery.javr.assembler.parser.ast.EquLabelNode;
import de.codesourcery.javr.assembler.parser.ast.FunctionCallNode;
import de.codesourcery.javr.assembler.parser.ast.FunctionDefinitionNode;
import de.codesourcery.javr.assembler.parser.ast.IdentifierDefNode;
import de.codesourcery.javr.assembler.parser.ast.IdentifierNode;
import de.codesourcery.javr.assembler.parser.ast.InstructionNode;
import de.codesourcery.javr.assembler.parser.ast.LabelNode;
import de.codesourcery.javr.assembler.parser.ast.NumberLiteralNode;
import de.codesourcery.javr.assembler.parser.ast.OperatorNode;
import de.codesourcery.javr.assembler.parser.ast.PreprocessorNode;
import de.codesourcery.javr.assembler.parser.ast.PreprocessorNode.Preprocessor;
import de.codesourcery.javr.assembler.parser.ast.RegisterNode;
import de.codesourcery.javr.assembler.parser.ast.StatementNode;
import de.codesourcery.javr.assembler.parser.ast.StringLiteral;
import de.codesourcery.javr.assembler.symbols.Symbol;
import de.codesourcery.javr.assembler.util.InMemoryResource;
import de.codesourcery.javr.assembler.util.Resource;

/**
 * Using a given lexer, turns a token stream into an AST.
 *
 * @author tobias.gierke@code-sourcery.de
 */
public class Parser
{
    private final IArchitecture arch;
    
    private ICompilationContext context;
    private Lexer lexer;
    private AST ast;
    
    private LabelNode previousGlobalLabel;

    public static enum Severity 
    {
        INFO(0),WARNING(1),ERROR(2);
        public final int level;
        
        private Severity(int level) {
            this.level = level;
        }
        
        public boolean equalOrGreater(Severity other) 
        {
            return this.level >= other.level;
        }
    }

    public static final class CompilationMessage 
    {
    	public final long timestamp = System.currentTimeMillis();
        public final Severity severity;
        public final String message;
        public final TextRegion region;
        public final ASTNode node;
        public final CompilationUnit unit;
        
        public static Comparator<CompilationMessage> compareSeverityDescending() 
        {
            return (a,b) -> Integer.compare(b.severity.level,a.severity.level);
        }
        
        public ZonedDateTime getTimestamp() {
        	return ZonedDateTime.ofInstant( Instant.ofEpochMilli( timestamp ) , ZoneId.systemDefault() );
        }
        
        @Override
        public String toString() 
        {
            final String offset;
            if ( region != null ) {
                offset = "offset "+region.start();
            } else if ( node != null && node.getTextRegion() != null ) {
                offset = "offset "+node.getTextRegion().start();
            } else {
                offset="<unknown location>";
            }
            return severity+" - "+offset+" - "+message;
        }

        public CompilationMessage(CompilationUnit unit,Severity severity, String message,TextRegion region) 
        {
            Validate.notNull(unit, "compilation unit must not be NULL");
            this.unit = unit;
            this.severity = severity;
            this.message = message;
            this.region = region;
            this.node = null;
        }

        public CompilationMessage(CompilationUnit unit,Severity severity, String message,ASTNode node) 
        {
            Validate.notNull(unit, "compilation unit must not be NULL");
            this.unit = unit;
            this.severity = severity;
            this.message = message;
            this.node = node;
            this.region = node == null ? null : node.getTextRegion();
        }        

        public CompilationMessage(CompilationUnit unit,Severity severity, String message) 
        {
            this( unit,severity , message , (TextRegion) null );
        }        

        public static CompilationMessage error(CompilationUnit unit,String msg) {
            return new CompilationMessage(unit,Severity.ERROR , msg , (TextRegion) null ); 
        }        

        public static CompilationMessage error(CompilationUnit unit,String msg,ASTNode node) {
            return new CompilationMessage(unit,Severity.ERROR , msg , node ); 
        }

        public static CompilationMessage error(CompilationUnit unit,String msg,TextRegion region) {
            return new CompilationMessage(unit,Severity.ERROR , msg , region ); 
        }        

        public static CompilationMessage info(CompilationUnit unit,String msg) {
            return new CompilationMessage(unit,Severity.INFO , msg , (ASTNode) null ); 
        }
        
        public static CompilationMessage info(CompilationUnit unit,String msg,ASTNode node) {
            return new CompilationMessage(unit,Severity.INFO , msg , node ); 
        }

        public static CompilationMessage info(CompilationUnit unit,String msg,TextRegion region) {
            return new CompilationMessage(unit,Severity.INFO, msg , region ); 
        }    
        
        public static CompilationMessage warning(CompilationUnit unit,String msg,ASTNode node) {
            return new CompilationMessage(unit,Severity.WARNING , msg , node ); 
        }        

        public static CompilationMessage warning(CompilationUnit unit,String msg,TextRegion region) {
            return new CompilationMessage(unit,Severity.WARNING, msg , region ); 
        }
        
        public static CompilationMessage warning(CompilationUnit unit,String msg) {
            return new CompilationMessage(unit,Severity.WARNING, msg , (ASTNode) null ); 
        }         
    }

    public Parser(IArchitecture arch) 
    {
        Validate.notNull(arch, "arch must not be NULL");
        this.arch = arch;
    }
    
    protected void initialize(ICompilationContext context,CompilationUnit unit,Lexer lexer) 
    {
        Validate.notNull(context, "context must not be NULL");
        Validate.notNull(lexer, "lexer must not be NULL");
        if ( arch == null ) {
            throw new IllegalStateException("architecture must be set");
        }
        
        this.context = context;
        // this.lexer = new DebugLexer( lexer );
        this.lexer = lexer;
        this.ast = new AST();
        unit.setAst( ast );
        ast.setCompilationUnit( unit );        
    }
    
    public AST parse(ICompilationContext context,CompilationUnit unit,Lexer lexer) 
    {
        initialize(context,unit,lexer);
        
        while ( ! this.lexer.eof() ) 
        {
            try 
            {
                final StatementNode stmt = parseStatement();
                if ( stmt == null ) 
                {
                    throw new ParseException("Not a valid statement",lexer.peek());
                }
                if ( stmt.hasChildren() ) {
                    ast.addChild( stmt );
                }
            } 
            catch(Exception e) 
            {
                e.printStackTrace();
                unit.addMessage( new CompilationMessage(unit,Severity.ERROR,e.getMessage(),lexer.peek().region() ) );

                // advance to next line
                // TODO: implement proper parse error recovery
                while ( ! lexer.eof() ) 
                {
                    if ( lexer.peek().isEOL() ) 
                    {
                        lexer.next();
                        break;
                    }
                    lexer.next();
                }
            }
        }
        unit.setAst( ast );        
        return ast;
    }
    
    private void skipWhitespace() 
    {
        while ( lexer.peek().isWhitespace() )
        {
            lexer.next();
        }
    }
    
    private void skipNewlines() 
    {
        while ( lexer.peek().isEOL() ) 
        {
            lexer.next();
        }
    }

    private StatementNode parseStatement() 
    {
        skipWhitespace();

        skipNewlines();
        
        final StatementNode result = new StatementNode();

        final ASTNode preproc = parsePreprocessor();
        if ( preproc != null ) 
        {
            result.addChild(preproc);
        } 
        else 
        {
            // parse label
            final LabelNode label = parseLabel();
            if ( label != null ) {
                result.addChild( label );
            }

            // parse .XXXX directive
            final ASTNode directive = parseDirective();
            if ( directive != null ) {
                result.addChild( directive );
            } 
            else 
            {
                // parse instruction
                final InstructionNode insn = parseInstruction();
                if ( insn!= null ) {
                    result.addChild( insn );
                } 
                else 
                {
                    // parse macro
                    final IdentifierNode macroName = parseIdentifier( lexer );
                    if ( macroName!= null ) {
                        result.addChild( macroName );
                    } 
                }
            }
        }

        // parse comment
        final CommentNode comment = parseComment();
        if ( comment != null ) {
            result.addChild( comment );
        }
        
        skipWhitespace();
        
        if ( ! lexer.peek().isEOLorEOF() ) {
            return null;
        }
        return result;
    }

    private ASTNode parsePreprocessor() 
    {
        final int offset = lexer.peek().offset;
        if ( lexer.peek(TokenType.HASH ) ) 
        {
            lexer.next();
            if ( lexer.peek(TokenType.TEXT ) ) 
            {
                final Token keywordToken = lexer.next();
                final String keyword = keywordToken.value;
                final PreprocessorNode.Preprocessor proc = Preprocessor.parse( keyword );
                if ( proc == null ) {
                    throw new ParseException("Unknown preprocessor instruction: "+keywordToken,keywordToken);
                }
                
                if ( proc == Preprocessor.PRAGMA ) // #pragma is currently ignored 
                {
                    final TextRegion r = new TextRegion( offset , lexer.peek().offset - offset, keywordToken.line , keywordToken.column );
                    final List<String> values = new ArrayList<>();
                    while ( ! lexer.peek().isEOLorEOF() ) 
                    {
                        values.add( lexer.peek().value );
                        r.merge( lexer.next().region() );
                    }
                    return new PreprocessorNode( proc , values , r );
                }
                if ( proc == Preprocessor.IF_DEFINE || proc == Preprocessor.IF_NDEFINE ) 
                {
                    final TextRegion r = new TextRegion( offset , lexer.peek().offset - offset , keywordToken.line , keywordToken.column);
                    ASTNode expr = parseExpression(lexer);
                    if ( expr == null ) {
                        throw new ParseException("Expected an identifier/expression", lexer.peek() );
                    }
                    if ( expr.hasNoChildren() && expr instanceof IdentifierNode) { // shorthand syntax for "#ifdef defined(identifier)"
                        final IdentifierNode idNode = (IdentifierNode) expr;
                        expr = new FunctionCallNode( new Identifier("defined") , idNode.getTextRegion().createCopy() );
                        expr.addChild( idNode );
                        
                        if ( proc == Preprocessor.IF_NDEFINE ) // => negate condition
                        {
                            final OperatorNode op = new OperatorNode( OperatorType.LOGICAL_NOT , idNode.getTextRegion().createCopy() );
                            op.addChild( expr );
                            expr = op;
                        }
                    }
                    final PreprocessorNode preproc = new PreprocessorNode( proc , r );
                    preproc.addChild( expr );
                    return preproc;
                } 
                if ( proc == Preprocessor.DEFINE ) 
                {
                    final Token nameToken = lexer.next();
                    if ( ! Identifier.isValidIdentifier( nameToken.value ) ) {
                        throw new ParseException("Expected an identifier ",nameToken);
                    }
                    final Identifier name = new Identifier(nameToken.value);

                    // check for whitespace
                    boolean gotWhitespace = false;
                    lexer.setIgnoreWhitespace( false );
                    try {
                        while ( lexer.peek(TokenType.WHITESPACE ) ) 
                        {
                            lexer.next();
                            gotWhitespace = true;
                        }
                    } finally {
                        lexer.setIgnoreWhitespace( true );
                    }                     

                    /*
                     * #define a
                     * #define a (1+2)
                     * #define a(x) x*x
                     * #define a sbi 0x3f , 5
                     */
                    final FunctionDefinitionNode funcDef = new FunctionDefinitionNode( name , Symbol.Type.PREPROCESSOR_MACRO , nameToken.region() );

                    boolean expectingFunctionBody= false;
                    if ( ! gotWhitespace &&  lexer.peek(TokenType.PARENS_OPEN ) ) 
                    {
                        expectingFunctionBody = true;
                        funcDef.addChild( parseArgumentNamesList() );
                    } else {
                        funcDef.addChild( new ArgumentNamesNode() );
                    }

                    final ASTNode macroBody = parseSingleLineMacro();
                    if ( macroBody != null ) 
                    {
                        funcDef.addChild( macroBody );
                    } 
                    else if ( ! expectingFunctionBody ) 
                    {
                    	funcDef.addChild( new NumberLiteralNode("1", nameToken.region() ) );
                    }
                    else 
                    { 
                        throw new ParseException("Expected an expression",lexer.peek());
                    } 
                    
                    final TextRegion r = new TextRegion( offset , keywordToken.endOffset() - offset , keywordToken.line , keywordToken.column);
                    final PreprocessorNode preproc= new PreprocessorNode(Preprocessor.DEFINE , r);                        
                    preproc.addChild( funcDef );
                    
                    defineSymbol( funcDef , new Symbol(funcDef.name,Symbol.Type.PREPROCESSOR_MACRO , context.currentCompilationUnit() , funcDef ) ); 
                    return preproc;
                }
                final List<String> args = parseText();
                final TextRegion r = new TextRegion( offset , lexer.peek().offset - offset , keywordToken.line , keywordToken.column);
                return new PreprocessorNode( proc , args , r );
            }
            throw new ParseException("Expected a keyword",lexer.peek());
        }
        return null;
    }
    
    public ASTNode parseSingleLineMacro() 
    {
        final Parser p = new Parser( this.context.getArchitecture() );
        
        final InMemoryResource nopResource = new InMemoryResource("nop" , Resource.ENCODING_UTF);
        final Lexer subLexer = new LexerImpl( new Scanner( nopResource ) );
        final List<Token> tokens = new ArrayList<>();
        while ( ! lexer.eof() && ! lexer.peek(TokenType.EOL) ) {
            tokens.add( 0 , lexer.next() );
        }
        tokens.forEach( subLexer::pushBack );
        p.initialize( this.context , this.context.currentCompilationUnit() , subLexer );
        ASTNode node = p.parseStatement();
        if ( node == null ) 
        {
            node = parseExpression( p.lexer );
            if ( node == null ) {
                throw new ParseException("Neither a valid statement nor a valid expression",p.lexer.peek());
            }
        } 
        else if ( node.childCount() == 1 ) 
        {
            node = node.child(0);
        }
        return node;
    }

    private ArgumentNamesNode parseArgumentNamesList() {

        if ( ! lexer.peek( TokenType.PARENS_OPEN ) ) 
        {
            throw new ParseException("Expected an argument names list",lexer.peek());
        }
        lexer.next();

        final ArgumentNamesNode result = new ArgumentNamesNode();
        while ( true ) 
        {
            final Token tok = lexer.peek();
            if ( Identifier.isValidIdentifier( tok.value ) ) 
            {
                lexer.next();
                result.addChild( new IdentifierDefNode( new Identifier( tok.value) , tok.region() ) );
            }
            if ( ! lexer.peek( TokenType.COMMA ) ) {
                break;
            }
            lexer.next(); // consume comma
        }
        if ( ! lexer.peek(TokenType.PARENS_CLOSE ) ) 
        {
            throw new ParseException("Missing closing parens" , lexer.peek() );
        }
        return result;
    }

    private List<String> parseText() 
    {
        final List<String> result = new ArrayList<>();
        while ( ! lexer.peek().is( TokenType.SEMICOLON, TokenType.EOF, TokenType.EOL) ) 
        {
            if ( lexer.peek( TokenType.SINGLE_QUOTE ) ) {
                final CharacterLiteralNode n = parseCharLiteral(lexer);
                result.add("'"+n.value+"'");
            } else if ( lexer.peek( TokenType.DOUBLE_QUOTE ) ) {
                final StringLiteral n = parseStringLiteral(lexer);
                result.add("\""+n.value+"\"");
            } else {
                result.add( lexer.next().value );
            }
        }
        return result;
    }

    private DirectiveNode parseDirective() // parse .XXXX commands
    {
        Token tok = lexer.peek();
        if ( tok.is( TokenType.DOT ) ) 
        {
            final TextRegion region = lexer.next().region(); // consume '.'

            final Token tok2 = lexer.peek();
            if ( ! tok2.is(TokenType.TEXT ) ) {
                throw new ParseException("Expected a keyword", tok2 );
            }
            lexer.next(); // consume keyword
            final DirectiveNode result = parseDirective2( tok2 );
            if ( result != null ) 
            {
                region.merge( tok2 );
                result.setRegion( region );
                return result;                    
            }
        }
        return null;
    }

    private DirectiveNode parseDirective2(Token tok2) 
    {
        final String value = tok2.value;
        switch( value.toLowerCase() ) // AVR documentation states that directives are matched ignoring case
        {
            case "org":
                {
                    final ASTNode expr = parseExpression(lexer);
                    if ( expr != null ) 
                    {
                        final DirectiveNode result = new DirectiveNode( Directive.ORG , tok2.region() );
                        result.addChild( expr );
                        return result;
                    }
                }
                throw new ParseException(".byte requires at least one value",tok2);                
            case "device":
                final int start = lexer.peek().offset;
                final List<String> values2 = parseText();
                if ( values2.isEmpty() ) {
                    throw new ParseException("Missing value", lexer.peek() );
                }
                final String device= values2.stream().collect(Collectors.joining());
                final DirectiveNode dResult = new DirectiveNode(Directive.DEVICE , tok2.region() );
                dResult.addChild( new StringLiteral( device , new TextRegion(start, lexer.peek().offset - start , tok2.line , tok2.column ) ) ); 
                return dResult;
            case "byte":
                final ASTNode expr = parseExpression(lexer);
                if ( expr != null ) 
                {
                    final DirectiveNode result = new DirectiveNode( Directive.RESERVE , tok2.region() );
                    result.addChild( expr );
                    return result;
                } 
                throw new ParseException(".byte requires at least one value",tok2);
            case "def":
	            {
	            	if ( lexer.peek().isValidIdentifier() ) 
	            	{
	            		final Token tok = lexer.next();
	            		final IdentifierDefNode dn = new IdentifierDefNode( Identifier.of( tok.value.toLowerCase() ) , tok.region() );
	            		
	            		if ( ! lexer.peek(TokenType.EQUALS ) ) {
	            		    throw new ParseException("Expected an equals sign", lexer.peek() );
	            		}
	            		// consume '=' equals
	            		lexer.next();
	                     
	            		final DirectiveNode result = new DirectiveNode(Directive.DEF , tok2.region());
	            		result.addChild( dn );
	            		final TextRegion regRegion = lexer.peek().region();
	            		String registerName = parseRegisterName( regRegion );
	            		if ( registerName == null ) {
	                		throw new ParseException("Expected a valid register name",lexer.peek());
	            		}
	            		result.addChild( new RegisterNode( new Register( registerName , false ,false ) , regRegion ) );
	            		return result;
	            	} 
	            }
	            throw new ParseException("Expected an identifier",lexer.peek());
            case "undef":
                if ( lexer.peek().isValidIdentifier() ) 
                {
                    final Token tok = lexer.next();
                    final IdentifierDefNode dn = new IdentifierDefNode( Identifier.of( tok.value.toLowerCase() ) , tok.region() );     
                    final DirectiveNode result = new DirectiveNode(Directive.UNDEF , tok2.region());
                    result.addChild(dn);
                    return result;
                }
                throw new ParseException("Expected an identifier",lexer.peek());
            case "dseg":
                context.setSegment( Segment.SRAM );
                previousGlobalLabel = null;
                return new DirectiveNode(Directive.DSEG , tok2.region() );
            case "eseg":
                context.setSegment( Segment.EEPROM );
                previousGlobalLabel = null;
                return new DirectiveNode(Directive.ESEG , tok2.region() );
            case "cseg": 
                context.setSegment( Segment.FLASH );
                previousGlobalLabel = null;
                return new DirectiveNode(Directive.CSEG , tok2.region() );
            case "db":
            case "dw":
            case "word":
                final Directive dirType = value.toLowerCase().equals("db") ? Directive.INIT_BYTES : Directive.INIT_WORDS;
                final DirectiveNode result =  new DirectiveNode( dirType , tok2.region() ); 
                final List<ASTNode> values = parseExpressionList();                
                if ( values.isEmpty() ) {
                    throw new ParseException( "Missing expression" , lexer.peek() );
                }
                result.addChildren( values );
                return result;
            case "equ":
                if ( lexer.peek(TokenType.TEXT ) && Identifier.isValidIdentifier( lexer.peek().value ) ) 
                {
                    final Token tok = lexer.next();
                    final Identifier name = new Identifier( tok.value );
                    if ( lexer.peek( TokenType.EQUALS ) ) 
                    {
                        lexer.next();
                        final ASTNode expr2 = parseExpression(lexer);
                        if ( expr2 != null ) {
                            final DirectiveNode result2 = new DirectiveNode(Directive.EQU , tok2.region() );
                            final EquLabelNode equLabel = new EquLabelNode( name , tok.region() );
                            
                            result2.addChild( equLabel );
                            result2.addChild( expr2 );
                            
                            defineSymbol( equLabel , new Symbol( equLabel.name , Symbol.Type.EQU , context.currentCompilationUnit() , equLabel ) );
                            return result2;
                        }
                        throw new ParseException("Expected an expression",lexer.peek());
                    } 
                    throw new ParseException("Expected '='",lexer.peek());
                }
                break;
            default:
                throw new RuntimeException("Unknown directive: ."+tok2.value);
        }
        return null;
    }

    private void defineSymbol(ASTNode node,final Symbol symbol) throws DuplicateSymbolException 
    {
        try {
            context.currentSymbolTable().defineSymbol( symbol , context.currentSegment() );
        } 
        catch(DuplicateSymbolException e) 
        {
            if ( ! context.message( CompilationMessage.error( context.currentCompilationUnit() , "Duplicate symbol: "+symbol.name() ,node) ) ) 
            {
                throw new ParseException("Too many errors",lexer.peek().offset );
            }
        }
    }
    
    private static void declareSymbol(ICompilationContext context,final Identifier identifier) 
    {
        context.currentSymbolTable().declareSymbol( identifier , context.currentCompilationUnit() );
    }
    
    private List<ASTNode> parseExpressionList() 
    {
        final List<ASTNode> result = new ArrayList<>();
        while(true)
        {
            ASTNode expr = parseExpression(lexer);
            if ( expr == null ) {
                return result;
            }
            result.add( expr );
            if ( lexer.peek( TokenType.COMMA ) ) {
                lexer.next();
            }
        } 
    }

    private LabelNode parseLabel() 
    {
        Token tok = lexer.peek();
        boolean isLocal = false;
        
        final TextRegion region;
        if ( tok.is(TokenType.DOT ) ) 
        {
            Token dot = lexer.next();
            region = dot.region();
            if ( lexer.eof() || Directive.isValidDirective( lexer.peek().value ) || ! lexer.peek().isValidIdentifier() ) 
            {
                lexer.pushBack( dot );
                return null;
            }
            tok = lexer.peek();
            region.merge( tok.region() );
            isLocal = true;
        } else {
            region = tok.region().incLength();
        }
        
        if ( tok.isValidIdentifier() && ! Register.isRegisterName( tok.value ) )
        {
            LabelNode label = null;
            final Identifier id = new Identifier( lexer.next().value );
            if ( isLocal ) 
            {
                label = new LabelNode( id , true , region );
            } 
            else if ( lexer.peek( TokenType.COLON ) ) 
            {
                lexer.next();
                label = new LabelNode( id , false , region );
                previousGlobalLabel = label;
            }
            if ( label != null ) 
            {
                if ( ! isLocal || (isLocal && previousGlobalLabel != null ) ) 
                {
                    // define symbol
                    final Identifier identifier;
                    if ( ! isLocal ) {
                        identifier = label.identifier;
                    } else {
                        identifier = Identifier.newLocalGlobalIdentifier( previousGlobalLabel.identifier , label.identifier );
                    }
                    final Symbol symbol = new Symbol( identifier , Symbol.Type.ADDRESS_LABEL , context.currentCompilationUnit() , label );
                    label.setSymbol( symbol );
                    defineSymbol( label , symbol ); 
                    return label;
                } else {
                    context.message( CompilationMessage.error(context.currentCompilationUnit(),"Local label has no preceding global label",region ) );
                }
            }
            lexer.pushBack( tok );
        }
        return null;
    }

    private InstructionNode parseInstruction() 
    {
        Token tok = lexer.peek();
        if ( tok.is( TokenType.TEXT ) && arch.isValidMnemonic( tok.value ) ) 
        {
            lexer.next();
            final Instruction instruction = new Instruction( tok.value );
            final InstructionNode result = new InstructionNode(instruction, tok.region());
            result.addChildren( parseOperands() );
            return result;
        }
        return null;
    }

    private List<ASTNode> parseOperands() {
        ASTNode n1 = parseOperand();
        if ( n1 == null ) {
            return Collections.emptyList();
        }
        if ( lexer.peek( TokenType.COMMA ) ) 
        {
            final Token tok = lexer.next();
            ASTNode n2 = parseOperand();
            if ( n2 == null ) {
                throw new ParseException("Expected an operand", tok.offset );
            }
            return Arrays.asList(n1,n2);
        } 
        return Arrays.asList(n1);
    }

    private ASTNode parseOperand() 
    {
        // register ref
        ASTNode result = parseRegisterRef();
        if ( result != null ) {
            return result;
        } 
        return parseExpression(lexer);
    }    

    private static CharacterLiteralNode parseCharLiteral(Lexer lexer) 
    {
        final Token tok = lexer.peek();
        if ( tok.is( TokenType.SINGLE_QUOTE ) ) 
        {
            lexer.setIgnoreWhitespace( false );
            try 
            {            
                lexer.next();
                final Token tok2 = lexer.next();
                if ( tok2.value.length() != 1 ) 
                {
                    throw new ParseException("Expected a single character", tok2.offset);
                }
                final Token tok3 = lexer.next();
                if ( ! tok3.is( TokenType.SINGLE_QUOTE ) ) {
                    throw new ParseException("Expected closing single-quote" , tok3.offset);
                }
                return new CharacterLiteralNode( tok2.value.charAt(0) , tok.region().merge( tok2 ).merge( tok3 ) );
            } finally {
                lexer.setIgnoreWhitespace(true);
            }
        }
        return null;
    }

    private static StringLiteral parseStringLiteral(Lexer lexer) 
    {
        final Token tok = lexer.peek();
        if ( tok.is( TokenType.DOUBLE_QUOTE ) ) 
        {
            lexer.setIgnoreWhitespace( false );
            try 
            {
                lexer.next();
                final StringBuilder buffer = new StringBuilder();
                do 
                {
                    final Token tok2 = lexer.peek();
                    if ( tok2.is( TokenType.EOL ,TokenType.EOF , TokenType.DOUBLE_QUOTE ) ) 
                    {
                        break;
                    }
                    lexer.next();
                    buffer.append( tok2.value );
                } while ( ! lexer.eof() );

                if ( ! lexer.peek( TokenType.DOUBLE_QUOTE ) ) {
                    throw new ParseException("Unterminated string literal", lexer.peek().offset );
                }
                final Token tok3 = lexer.next();
                return new StringLiteral( buffer.toString() , tok.region().merge( tok3) );
            } finally {
                lexer.setIgnoreWhitespace( true );
            }
        }
        return null;
    }    

    private RegisterNode parseRegisterRef() 
    {
        final Token tok = lexer.peek();
        TextRegion region = tok.region();
        boolean preDecrement = tok.is( TokenType.OPERATOR ) && tok.value.equals("-");
        if ( preDecrement) {
            lexer.next();
        }
        String name = parseRegisterName(region);
        if ( name != null ) 
        {
            if ( ! isCompoundRegister( name ) ) 
            {
                if ( lexer.peek(TokenType.COLON ) ) {
                    region.merge( lexer.next() );
                    final String otherName = parseRegisterName( region );
                    if ( otherName == null ) {
                        throw new ParseException("Expected another register name",lexer.peek());
                    }
                    name = name +":" + otherName;
                }
            }
            final boolean postIncrement = lexer.peek(TokenType.OPERATOR) && lexer.peek().value.equals("+");
            ASTNode displacement = null;
            if ( postIncrement ) 
            {
                if ( preDecrement ) {
                    throw new ParseException("Either pre-decrement or post-increment are possible but not both",lexer.peek());
                }
                region.merge( lexer.next() );
                displacement = parseExpression(lexer);
            }            
            final Register reg = new Register( name , postIncrement && displacement == null , preDecrement );
            final RegisterNode result = new RegisterNode( reg , region );
            if ( displacement != null ) {
                result.addChild( displacement );
            }
            return result;
        }
        if ( preDecrement ) {
            lexer.pushBack( tok );
        }
        return null;
    }


    private ASTNode parseExpression(Lexer lexer) 
    {
        return parseExpression(lexer,context);
    }
    
    public static ASTNode parseExpression(Lexer lexer,ICompilationContext context) 
    {
        final ShuntingYard yard = new ShuntingYard();
        while ( ! lexer.eof() ) 
        {
            final Token tok = lexer.peek();
            if ( tok.is(TokenType.PARENS_OPEN ) ) {
                yard.pushOperator( new ExpressionToken(ExpressionTokenType.PARENS_OPEN , lexer.next() ) );
            } 
            else if ( tok.is(TokenType.PARENS_CLOSE ) ) {
                yard.pushOperator( new ExpressionToken(ExpressionTokenType.PARENS_CLOSE , lexer.next() ) );
            }
            else if ( tok.is(TokenType.OPERATOR) ) 
            {
                lexer.next();
                final OperatorType type;
                if ( tok.value.equals("-" ) ) {
                    type = OperatorType.BINARY_MINUS; // will be turned into UNARY_MINUS by pushOperator() call
                } else {
                    type = OperatorType.getExactMatch( tok.value );
                }
                OperatorNode op = new OperatorNode( type , tok.region() );

                yard.pushOperator( new ExpressionToken(ExpressionTokenType.OPERATOR , op ) );
            }
            else if ( yard.isFunctionOnStack() && tok.is(TokenType.COMMA ) ) {
                yard.pushOperator( new ExpressionToken(ExpressionTokenType.ARGUMENT_DELIMITER, lexer.next() ) );
            } 
            else 
            {
                final ASTNode node ;
                if ( lexer.peek(TokenType.DOT ) ) {
                    node = new CurrentAddressNode( lexer.next().region() );
                } else {
                    node = parseAtom(lexer,context);
                }
                if ( node != null ) 
                {
                    if ( node instanceof IdentifierNode && lexer.peek(TokenType.PARENS_OPEN ) ) 
                    {
                        final Identifier id = ((IdentifierNode) node).name;
                        yard.pushOperator( new ExpressionToken(ExpressionTokenType.FUNCTION , new FunctionCallNode( id , node.getTextRegion() ) ) );
                    } else {
                        yard.pushValue( node );
                    }
                } else {
                    break;
                }
            }
        }
        if ( yard.isEmpty() ) {
            return null;
        }
        return yard.getResult( lexer.peek().region() );
    }

    private static ASTNode parseAtom(Lexer lexer,ICompilationContext context) 
    {
        // character literal
        ASTNode result = parseCharLiteral(lexer);
        if ( result != null ) {
            return result;
        }

        // string literal
        result = parseStringLiteral(lexer);
        if ( result != null ) {
            return result;
        }

        // number literal
        result = parseNumber(lexer);
        if ( result != null ) {
            return result;
        }

        // identifier
        return parseIdentifier(lexer,context);         
    }

    private boolean isCompoundRegister(String name) {
        switch( name.toLowerCase() ) {
            case "x":
            case "y":
            case "z":
                return true;
            default:
                return false;
        }
    }

    private String parseRegisterName(TextRegion region) 
    {
        final Token tok = lexer.peek();
        if ( tok.is(TokenType.TEXT) )
        {
            final String value = tok.value;
            final char firstChar = value.charAt(0);
            switch( firstChar ) 
            {
                case 'r':
                case 'R':
                    boolean isNumber = false;
                    for ( int i = 1, len = value.length() ; i < len ; i++ ) 
                    {
                        if ( ! Character.isDigit( value.charAt( i ) ) ) {
                            isNumber = false;
                            break;
                        }
                        isNumber = true;
                    }
                    if ( isNumber ) 
                    {
                        lexer.next();
                        region.merge( tok );
                        return tok.value;
                    }                    
                    break;
                case 'X':
                case 'Y':
                case 'Z':
                case 'x':
                case 'y':
                case 'z':        
                    if ( value.length() == 1 ) 
                    {
                        lexer.next();
                        region.merge( tok );
                        return tok.value;
                    }
            }
        }
        return null;
    }

    private static NumberLiteralNode parseNumber(Lexer lexer) 
    {
        Token tok = lexer.peek();
        if ( tok.is( TokenType.DIGITS ) || tok.is( TokenType.TEXT ) && NumberLiteralNode.isValidNumberLiteral( tok.value ) ) 
        {
            lexer.next();
            return new NumberLiteralNode( tok.value , tok.region() );
        }
        return null;
    }
    
    private IdentifierNode parseIdentifier(Lexer lexer) 
    {
        return parseIdentifier(lexer,context);
    }

    private static IdentifierNode parseIdentifier(Lexer lexer,ICompilationContext context) 
    {
        Token tok = lexer.peek();
        if ( Identifier.isValidIdentifier( tok.value ) ) 
        {
            lexer.next();
            IdentifierNode result = new IdentifierNode( new Identifier( tok.value ) , tok.region() );
            declareSymbol( context , result.name );
            return result;
        }
        return null;
    }

    private CommentNode parseComment() 
    {
        final Token tok = lexer.peek();
        if ( tok.is( TokenType.SEMICOLON ) || tok.isOperator("/" ) ) 
        {
            final StringBuilder buffer = new StringBuilder();
            final TextRegion region = lexer.peek().region();     
            buffer.append( lexer.next().value );
            boolean isMultiLineComment = false;
            if ( tok.isOperator("/" ) ) 
            {
                final Token tok2 = lexer.peek();
                if ( tok2.isOperator("/") ) { // single-line comment
                    // ok
                    buffer.append( "/" );
                    region.merge( lexer.next().region() );
                } 
                else if ( tok2.isOperator("*") ) { // multi-line comment
                    buffer.append( "*" );
                    region.merge( lexer.next().region() );
                    isMultiLineComment = true;
                }
                else 
                {
                    throw new ParseException( "C-style comment needs //" , tok);
                }
            } 
       
            lexer.setIgnoreWhitespace( false );
            try 
            {
                while ( ! lexer.eof() ) 
                {
                    if ( lexer.peek(TokenType.EOL ) ) 
                    {
                        if ( ! isMultiLineComment ) {
                            break;
                        }
                    }
                    final Token tok2 = lexer.next();
                    region.merge( tok2.region() );
                    buffer.append( tok2.value );
                    if ( isMultiLineComment && tok2.isOperator("*") && lexer.peek().isOperator("/" ) ) 
                    {
                        buffer.append( lexer.peek().value );                        
                        region.merge( lexer.next().region() );
                        break;
                    }                    
                }
                return new CommentNode( buffer.toString(), region );
            } 
            finally 
            {
                lexer.setIgnoreWhitespace( true );
            }
        }
        return null;
    }
}
