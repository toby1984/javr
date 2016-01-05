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
import de.codesourcery.javr.assembler.parser.Parser.CompilationMessage;
import de.codesourcery.javr.assembler.parser.TextRegion;
import de.codesourcery.javr.assembler.parser.ast.ASTNode;
import de.codesourcery.javr.assembler.parser.ast.InstructionNode;
import de.codesourcery.javr.assembler.parser.ast.NumberLiteralNode;
import de.codesourcery.javr.assembler.parser.ast.RegisterNode;
import de.codesourcery.javr.assembler.symbols.SymbolTable;
import junit.framework.TestCase;

public class DisassembleTest extends TestCase {

    public void testDisasm()
    {
        final ATMega88 arch = new ATMega88();
        
        FakeContext ctx = new FakeContext(arch);
        
        for ( EncodingEntry entry : arch.instructions.values() ) 
        {
            for ( InstructionEncoding enc : entry.encodings ) 
            {
                System.out.println("=====================\nASSEMBLE: "+enc.mnemonic.toUpperCase());
                final InstructionNode in = new InstructionNode( new Instruction( enc.mnemonic) , new TextRegion(1,1) );
                if ( enc.getArgumentCount() > 0 ) 
                {
                    in.addChild( createFakeValue( enc.dstType , Kind.DST) );
                }
                if ( enc.getArgumentCount() > 1 ) {
                    in.addChild( createFakeValue( enc.srcType , Kind.SRC ) );
                }
                ctx.reset();
                try 
                {
                    arch.compile( in , ctx  );
                } catch(Exception e) {
                    continue;
                }
                final String output = arch.disassemble( ctx.buffer , ctx.ptr );
                final String expected = enc.mnemonic.toLowerCase();
                final String actual = output.split("[ ]")[0].toLowerCase();
                if ( ! actual.equals( expected ) )
                {
                    if ( enc.aliasOf == null || ! actual.equals( enc.aliasOf.mnemonic.toLowerCase() ) ) { 
                        System.err.println( "DISASSEMBLE FAIL: "+output );
                    }
                    continue;
                }
                System.out.println("DISASM OK: "+output);
            }
        }
    }
    
    protected static enum Kind { SRC, DST };
    
    private ASTNode createFakeValue(ArgumentType type,Kind kind) 
    {
        final boolean isDst = kind == Kind.DST;
        switch(type) {
            case COMPOUND_REGISTER:
                
                return new RegisterNode( new Register( isDst ? "Y" : "Z" ,false,false) , new TextRegion(1,1) ); 
            case COMPOUND_REGISTERS_R24_TO_R30:
                return new RegisterNode( new Register( isDst ? "Y" : "Z",false,false) , new TextRegion(1,1) );
            case DATASPACE_16_BIT_ADDESS:
                return new NumberLiteralNode( isDst ? "0x1234" : "0x4322" , new TextRegion(1,1));
            case EIGHT_BIT_CONSTANT:
                return new NumberLiteralNode( isDst ? "0x12" : "0x24" , new TextRegion(1,1));
            case FIVE_BIT_IO_REGISTER_CONSTANT:
                return new NumberLiteralNode( isDst ? "%10101" : "%01010" , new TextRegion(1,1));
            case FLASH_MEM_ADDRESS:
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
            case SEVEN_BIT_SIGNED_BRANCH_OFFSET:
                return new NumberLiteralNode( isDst ? "%1101011" : "%1010111"  , new TextRegion(1,1));
            case SEVEN_BIT_SRAM_MEM_ADDRESS:
                return new NumberLiteralNode( isDst ? "%1110101" : "%1010111" , new TextRegion(1,1));
            case SINGLE_REGISTER:
                return new RegisterNode( new Register( isDst ? "r0" : "r1" ,false,false) , new TextRegion(1,1) );
            case SIX_BIT_CONSTANT:
                return new NumberLiteralNode( isDst ? "%101010" : "%010101" , new TextRegion(1,1));
            case SIX_BIT_IO_REGISTER_CONSTANT:
                return new NumberLiteralNode( isDst ? "%101010" : "%010101" , new TextRegion(1,1));
            case SIXTEEN_BIT_SRAM_MEM_ADDRESS:
                return new NumberLiteralNode( isDst ? "0x1234" : "0x3456" , new TextRegion(1,1));
            case THREE_BIT_CONSTANT:
                return new NumberLiteralNode( isDst ? "%101" : "%100" , new TextRegion(1,1));
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
            writeByte( value >> 8 );
            writeByte( value );
        }
        
        @Override
        public void writeByte(int value) {
            buffer[ptr++] = (byte) (value & 0xff);
        }
        
        @Override
        public void writeAsBytes(int value, int numberOfBytes) {
            switch( numberOfBytes ) {
                case 1:
                    writeByte(value);
                    break;
                case 2:
                    writeWord(value);
                    break;
                case 3:
                    writeByte(value >> 16 );
                    writeByte(value >> 8 );
                    writeByte(value );
                    break;
                case 4:
                    writeWord(value >> 16 );
                    writeWord(value );
                    break;                    
            }
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
        public int getBytesRemainingInCurrentSegment() {
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
