package de.codesourcery.javr.assembler.arch;

import de.codesourcery.javr.assembler.Address;
import de.codesourcery.javr.assembler.CompilationUnit;
import de.codesourcery.javr.assembler.ICompilationContext;
import de.codesourcery.javr.assembler.Instruction;
import de.codesourcery.javr.assembler.Register;
import de.codesourcery.javr.assembler.Segment;
import de.codesourcery.javr.assembler.arch.AbstractAchitecture.ArgumentType;
import de.codesourcery.javr.assembler.arch.AbstractAchitecture.EncodingEntry;
import de.codesourcery.javr.assembler.arch.AbstractAchitecture.InstructionEncoding;
import de.codesourcery.javr.assembler.arch.impl.ATMega88;
import de.codesourcery.javr.assembler.parser.Lexer;
import de.codesourcery.javr.assembler.parser.Parser;
import de.codesourcery.javr.assembler.parser.Parser.CompilationMessage;
import de.codesourcery.javr.assembler.parser.Scanner;
import de.codesourcery.javr.assembler.parser.TextRegion;
import de.codesourcery.javr.assembler.parser.ast.AST;
import de.codesourcery.javr.assembler.parser.ast.ASTNode;
import de.codesourcery.javr.assembler.parser.ast.InstructionNode;
import de.codesourcery.javr.assembler.parser.ast.NumberLiteralNode;
import de.codesourcery.javr.assembler.parser.ast.RegisterNode;
import de.codesourcery.javr.assembler.symbols.SymbolTable;
import de.codesourcery.javr.assembler.util.PrettyPrinter;
import junit.framework.TestCase;

public class DisassembleTest extends TestCase {

    private final ATMega88 arch = new ATMega88();
    
    public void testDisassemble2() {
        
        final byte[] bytes = new byte[] { (byte) 0xff, (byte) 0xff , (byte) 0x8f, (byte) 0xef , (byte) 0x8d ,(byte) 0xbf  , (byte) 0xc8 ,(byte) 0xed };
        
        final String output = arch.disassemble( bytes , bytes.length , false , 0 , false );
        System.out.println( ">>>>>> "+output );
    }
    
    /*
1c2c:    ST R24         ; 8d 93
1c2e:    ST R24         ; 8c 93

    */
    public void testDisassembleST() {
        
        final byte[] bytes = new byte[] { (byte) 0x8d, (byte) 0x93 , (byte) 0x8c, (byte) 0x93};
        
        final String output = arch.disassemble( bytes , bytes.length , false , 0 , false );
        System.out.println( ">>>>>> "+output );
    }
    
    public void testDisassembl3()
    {
        FakeContext ctx = new FakeContext(arch);
        
        final PrettyPrinter printer = new PrettyPrinter();
        printer.setIndentString("");
        printer.setPrintAllNumberLiteralsAsHex( true );
        
        for ( EncodingEntry entry : arch.instructions.values() ) 
        {
            for ( InstructionEncoding enc : entry.encodings ) 
            {
                final InstructionNode in = new InstructionNode( new Instruction( enc.mnemonic ) , new TextRegion(1,1) );
                if ( enc.getArgumentCount() > 0 ) 
                {
                    in.addChild( createFakeValue( enc.dstType , Kind.DST) );
                }
                if ( enc.getArgumentCount() > 1 ) {
                    in.addChild( createFakeValue( enc.srcType , Kind.SRC ) );
                }
                final String expected = printer.prettyPrint( in );
                System.out.println("=====================\nASSEMBLE: "+expected );
                ctx.reset();
                try 
                {
                    arch.compile( in , ctx  );
                } catch(Exception e) {
                    continue;
                }
                final String output = arch.disassemble( ctx.buffer , ctx.ptr , false , 0 , false );
                final String actual = printer.prettyPrint( parse( output ) );
                if ( ! actual.equalsIgnoreCase( expected ) )
                {
                    // handle equivalent operations
                    // EXPECTED:brbs    0x1,0x57
                    // GOT     : breq    0x57
                    if ( ("brbs    0x1,0x57".equals( expected ) && "breq    0x57".equals( actual ) ) ||
                         ("brcc    0x6b".equals( expected ) && "brsh    0x6b".equals( actual ) ) ||
                         ("brbc    0x1,0x57".equals( expected ) && "brne    0x57".equals( actual ) ) ||                         
                         ("ori    r0,0x24".equals( expected ) && "sbr    r16,0x24".equals( actual ) ) ||                         
                         ("bset    0x1".equals( expected ) && "sez".equals( actual ) ) ||
                         ("cbr    r16,0x24".equals( expected ) && "andi    r16,0xdb".equals( actual ) ) ||                             
                         ("brlo    0x6b".equals( expected ) && "brcs    0x6b".equals( actual ) ) ||                             
                         ("bclr    0x1".equals( expected ) && "clz".equals( actual ) )
                    ){
                        // ok
                    } else {
                        System.err.println( "DISASSEMBLE FAIL: \nEXPECTED: >"+expected+"<"+
                                                          "\nGOT     : >"+actual+"<" );
                        fail();
                    }
                }
                System.out.println("DISASM OK: "+actual);
            }
        }
    }
    
    private AST parse(String text) 
    {
        final Parser p = new Parser();
        p.setArchitecture( arch );
        return p.parse( new Lexer( new Scanner(text ) ) );
    }
    
    protected static enum Kind { SRC, DST };
    
    private ASTNode createFakeValue(ArgumentType type,Kind kind) 
    {
        final boolean isDst = kind == Kind.DST;
        switch(type) {
            case COMPOUND_REGISTER_FOUR_BITS:
                
                return new RegisterNode( new Register( isDst ? "Y" : "Z" ,false,false) , new TextRegion(1,1) ); 
            case COMPOUND_REGISTERS_R24_TO_R30:
                return new RegisterNode( new Register( isDst ? "Y" : "Z",false,false) , new TextRegion(1,1) );
            case EIGHT_BIT_CONSTANT:
                return new NumberLiteralNode( isDst ? "0x12" : "0x24" , new TextRegion(1,1));
            case FIVE_BIT_IO_REGISTER_CONSTANT:
                return new NumberLiteralNode( isDst ? "%10101" : "%01010" , new TextRegion(1,1));
            case TWENTYTWO_BIT_FLASH_MEM_ADDRESS:
                return new NumberLiteralNode( isDst ? "0x1234" : "00x0136" , new TextRegion(1,1));
            case FOUR_BIT_CONSTANT:
                return new NumberLiteralNode( isDst ? "%1001" : "%1111" , new TextRegion(1,1));
            case NONE:
                return null;
            case R0_TO_R15:
                return new RegisterNode( new Register( isDst ? "r0" : "r1" ,false,false) , new TextRegion(1,1) );
            case R16_TO_R23:
                return new RegisterNode( new Register( isDst ? "r16" : "r17" ,false,false) , new TextRegion(1,1) );
            case R16_TO_R31:
                return new RegisterNode( new Register( isDst ? "r16" : "r31" ,false,false) , new TextRegion(1,1) );
            case SEVEN_BIT_SIGNED_JUMP_OFFSET:
                return new NumberLiteralNode( isDst ? "%1101011" : "%1010111"  , new TextRegion(1,1));
            case SINGLE_REGISTER:
                return new RegisterNode( new Register( isDst ? "r0" : "r1" ,false,false) , new TextRegion(1,1) );
            case SIX_BIT_CONSTANT:
                return new NumberLiteralNode( isDst ? "%101010" : "%010101" , new TextRegion(1,1));
            case SIX_BIT_IO_REGISTER_CONSTANT:
                return new NumberLiteralNode( isDst ? "%101010" : "%010101" , new TextRegion(1,1));
            case SEVEN_BIT_SRAM_MEM_ADDRESS:
                return new NumberLiteralNode( isDst ? "%1110100" : "%1010110" , new TextRegion(1,1));                
            case SIXTEEN_BIT_SRAM_MEM_ADDRESS:
                return new NumberLiteralNode( isDst ? "0x34" : "0x100" , new TextRegion(1,1));
            case THREE_BIT_CONSTANT:
                return new NumberLiteralNode( isDst ? "%001" : "%100" , new TextRegion(1,1));
            case TWELVE_BIT_SIGNED_JUMP_OFFSET:
                return new NumberLiteralNode(  isDst ? "%101010101010" : "%010101010101" , new TextRegion(1,1));
            case X_REGISTER:
                return new RegisterNode( new Register("X",false,false) , new TextRegion(1,1) );
            case Y_REGISTER:
                return new RegisterNode( new Register("Y",false,false) , new TextRegion(1,1) );
            case Y_REGISTER_DISPLACEMENT:
                return new RegisterNode( new Register("Y",true,false) , new TextRegion(1,1) );   
            case Z_REGISTER:
                return new RegisterNode( new Register("Z",false,false) , new TextRegion(1,1) );
            case Z_REGISTER_DISPLACEMENT:
                return new RegisterNode( new Register("Z",true,false) , new TextRegion(1,1) );                
            default:
               throw new RuntimeException("Unreachable code reached");
        }
    }
    
    protected static final class FakeContext implements ICompilationContext {
        
        private final IArchitecture arch;
        
        public final byte[] buffer = new byte[1024];
        public int ptr = 0;
        
        public FakeContext(IArchitecture arch) {
            this.arch = arch;
        }
        
        public void reset() {
            ptr = 0;
        }

        @Override
        public void writeWord(int value) {
            writeByte( value );
            writeByte( value >> 8 );
        }
        
        @Override
        public void writeByte(int value) {
            buffer[ptr++] = (byte) (value & 0xff);
        }
        
        @Override
        public void setSegment(Segment s) {
            throw new UnsupportedOperationException();
        }
        
        @Override
        public void message(CompilationMessage msg) {
            System.err.println( msg.message );
        }
        
        @Override
        public SymbolTable globalSymbolTable() {
            throw new UnsupportedOperationException();
        }
        
        @Override
        public IArchitecture getArchitecture() {
            return arch;
        }
        
        @Override
        public void error(String message, ASTNode node) {
            System.out.println( message );            
        }
        
        @Override
        public SymbolTable currentSymbolTable() {
            throw new UnsupportedOperationException();
        }
        
        @Override
        public Segment currentSegment() {
            return Segment.FLASH;
        }
        
        @Override
        public int currentOffset() {
            return 0;
        }
        
        @Override
        public CompilationUnit currentCompilationUnit() {
            throw new UnsupportedOperationException();
        }
        
        @Override
        public Address currentAddress() {
            return Address.wordAddress(Segment.FLASH, currentOffset() );
        }
        
        @Override
        public void allocateWord() {
            throw new UnsupportedOperationException();            
        }
        
        @Override
        public void allocateBytes(int numberOfBytes) {
            throw new UnsupportedOperationException();            
        }
        
        @Override
        public void allocateByte() {
            throw new UnsupportedOperationException();            
        }
    };
    
    // 1001 0100 KKKK 1011
    // 1001 0100 1001 1011
    // 1001 0100 1001 10110000000000000000
}
