package de.codesourcery.javr.assembler.arch;

import de.codesourcery.javr.assembler.Address;
import de.codesourcery.javr.assembler.CompilationSettings;
import de.codesourcery.javr.assembler.CompilationUnit;
import de.codesourcery.javr.assembler.ICompilationContext;
import de.codesourcery.javr.assembler.Instruction;
import de.codesourcery.javr.assembler.Register;
import de.codesourcery.javr.assembler.Segment;
import de.codesourcery.javr.assembler.arch.AbstractAchitecture.ArgumentType;
import de.codesourcery.javr.assembler.arch.AbstractAchitecture.EncodingEntry;
import de.codesourcery.javr.assembler.arch.AbstractAchitecture.InstructionEncoding;
import de.codesourcery.javr.assembler.arch.IArchitecture.DisassemblerSettings;
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
    
    private static final DisassemblerSettings settings = new DisassemblerSettings();
    
    // ; disassembled 8192 bytes from /home/tobi/atmel/asm/random.ra
    //  ld r17, z+                                                   | .db 0x11 , 0x91
    
    public void testDisassemble7() {
        
        final byte[] bytes = new byte[] { (byte) 0x11 ,(byte) 0x91 };
        
        final String output = arch.disassemble( bytes , bytes.length , settings );
        System.out.println("Got "+output);
        assertEquals("ld r17,z+" , output );
    }
    
    public void testDisassemble6() {
        
        final byte[] bytes = new byte[] { (byte) 0x44 ,(byte) 0x92 };
        
        final String output = arch.disassemble( bytes , bytes.length , settings );
        System.out.println("Got "+output);
        assertEquals("xch z,r4" , output );
    } 
    
    // 97 91           elpm    r25, Z+
    public void testDisassemble5() {
        
        final byte[] bytes = new byte[] { (byte) 0x97 ,(byte) 0x91 };
        
        final String output = arch.disassemble( bytes , bytes.length , settings );
        System.out.println("Got "+output);
        assertEquals("elpm r25,z+" , output );
    }    
    
    // sts 0x02,r20 ; ; 42 a8 ==>  ldd r4, z+50 
    public void testDisassemble4() {
        
        final byte[] bytes = new byte[] { (byte) 0x42  ,(byte) 0xa8 };
        
        final String output = arch.disassemble( bytes , bytes.length , settings );
        System.out.println("Got "+output);
        assertEquals("ldd r4,z+50" , output );
    }
    
    // call 0x2bdfac  ; 5f 95 ac df ==> needs to be  call 0x57bf58
    public void testDisassemble3() {
        
        final byte[] bytes = new byte[] { (byte) 0x5f ,(byte) 0x95 ,(byte) 0xac ,(byte) 0xdf};
        
        final String output = arch.disassemble( bytes , bytes.length , settings );
        assertEquals("call 0x57bf58" , output );
    }
    
    // 8f 83
    // st y+24,r7 ==> NEEDS TO BE  std y+7, r24
    public void testDisassemble2() {
        
        final byte[] bytes = new byte[] { (byte) 0x8f ,(byte) 0x83 };
        
        final String output = arch.disassemble( bytes , bytes.length , settings );
        assertEquals("std y+7,r24" , output );
    }
    
    /*
1c2c:    ST R24         ; 8d 93
1c2e:    ST R24         ; 8c 93

    */
    public void testDisassembleST() {
        
        final byte[] bytes = new byte[] { (byte) 0x8d, (byte) 0x93 , (byte) 0x8c, (byte) 0x93};
        
        final String output = arch.disassemble( bytes , bytes.length , settings );
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
                if ( enc.getArgumentCountFromPattern() > 0 ) 
                {
                    in.addChild( createFakeValue( enc.dstType , Kind.DST) );
                }
                if ( enc.getArgumentCountFromPattern() > 1 ) {
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
                final String output = arch.disassemble( ctx.buffer , ctx.ptr , settings );
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
            case SEVEN_BIT_SIGNED_COND_BRANCH_OFFSET:
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
            case Y_REGISTER_SIX_BIT_DISPLACEMENT:
                return new RegisterNode( new Register("Y",true,false) , new TextRegion(1,1) );   
            case Z_REGISTER:
                return new RegisterNode( new Register("Z",false,false) , new TextRegion(1,1) );
            case Z_REGISTER_SIX_BIT_DISPLACEMENT:
                return new RegisterNode( new Register("Z",true,false) , new TextRegion(1,1) );                
            default:
               throw new RuntimeException("Unreachable code reached");
        }
    }
    
    protected static final class FakeContext implements ICompilationContext {
        
        private final IArchitecture arch;
        private final ICompilationSettings compilationSettings = new CompilationSettings();
        
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

        @Override
        public ICompilationSettings getCompilationSettings() {
            return compilationSettings;
        }
    };
    
    // 1001 0100 KKKK 1011
    // 1001 0100 1001 1011
    // 1001 0100 1001 10110000000000000000
}
