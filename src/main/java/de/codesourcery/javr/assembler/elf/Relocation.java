package de.codesourcery.javr.assembler.elf;

import org.apache.commons.lang3.Validate;

import de.codesourcery.javr.assembler.symbols.Symbol;
import de.codesourcery.javr.assembler.symbols.Symbol.Type;

public class Relocation
{
    /**
     * Kind of relocation to perform.
     */
    public Kind kind;
    /**
     * The value to be used in the calculation when
     * calculating the final value.
     */
    public int addend;
    /**
     * Offset within the section where to apply this relocation
     */
    public int locationOffset;
    /**
     * The symbol this relocation refers to
     */
    public Symbol symbol;

    /**
     * LDI relocation type (bitmask value): HIGH(x)
     */
    public static final int EXPR_FLAG_HI = 1;
    
    /**
     * LDI relocation type (bitmask value): LOW(x)
     */
    public static final int EXPR_FLAG_LO = 2;
    
    /**
     * LDI relocation type (bitmask value): x>>1
     */
    public static final int EXPR_FLAG_PM = 4;
    
    /**
     * LDI relocation type (bitmask value): -x
     */
    public static final int EXPR_FLAG_NEG = 8;
    
    public static enum Kind 
    {
        R_AVR_NONE(0),
        // absolute values (for example .byte directive in .data section)
        R_AVR_16(4),
        R_AVR_16_PM(5),      
        R_AVR_8(26),
        R_AVR_8_LO8(27),
        R_AVR_8_HI8(28),
        // conditional branches
        R_AVR_7_PCREL(2),
        R_AVR_13_PCREL(3),
        // unconditional branches
        R_AVR_CALL(18),        
        // LDI
        R_AVR_LDI(19),
        R_AVR_LO8_LDI(6,EXPR_FLAG_LO),
        R_AVR_LO8_LDI_PM(12,EXPR_FLAG_LO|EXPR_FLAG_PM),
        R_AVR_HI8_LDI(7, EXPR_FLAG_HI),
        R_AVR_HI8_LDI_PM(13,EXPR_FLAG_HI|EXPR_FLAG_PM),
        // negative addresses
        R_AVR_LO8_LDI_PM_NEG(15 , EXPR_FLAG_LO | EXPR_FLAG_PM  | EXPR_FLAG_NEG ),
        R_AVR_LO8_LDI_NEG(9,EXPR_FLAG_LO | EXPR_FLAG_NEG ), // ldi r16,lo8(-(label))
        R_AVR_HI8_LDI_NEG(10 , EXPR_FLAG_HI | EXPR_FLAG_NEG ),
        R_AVR_HI8_LDI_PM_NEG(16 , EXPR_FLAG_HI | EXPR_FLAG_PM | EXPR_FLAG_NEG ),
        // LDS / STS
        R_AVR_LDS_STS_16(33),        
        // ldd/sdd command 
        R_AVR_6(20),
        // For sbiw/adiw command
        R_AVR_6_ADIW(21), // ok
        // in,out
        R_AVR_PORT6(34),
        // sbi, sbic , etc.
        R_AVR_PORT5(35),
        // ===================================================================
        // TODO: The following relocations are currently not being generated 
        // ===================================================================
        // 32 bit AVRs
        R_AVR_HH8_LDI_NEG(11), // ldi r16,hhi8(-(label))
        R_AVR_HH8_LDI(8),
        R_AVR_HH8_LDI_PM(14),
        R_AVR_HH8_LDI_PM_NEG(17),           
        R_AVR_32(1),
        R_AVR_8_HLO8(29),               
        // linker stub generation
        // https://lists.gnu.org/archive/html/bug-binutils/2011-08/msg00111.html
        R_AVR_LO8_LDI_GS(24),
        R_AVR_HI8_LDI_GS(25),            
        // linker relaxation stuff, see https://lists.nongnu.org/archive/html/avr-libc-dev/2005-10/msg00042.html
        R_AVR_MS8_LDI(22),
        R_AVR_MS8_LDI_NEG(23),       
        // diff
        R_AVR_DIFF8(30),
        R_AVR_DIFF16(31),
        R_AVR_DIFF32(32);        

        public final int elfId;
        public final int typeFlags;
        
        private Kind(int elfId,int typeFlags) {
            this.elfId = elfId;
            this.typeFlags = typeFlags;
        }
        
        private Kind(int elfId) {
            this.elfId = elfId;
            this.typeFlags = 0;
        }
        
        public static Kind get8BitLDIRelocation(int expressionTypeBitMask) 
        {
            switch( expressionTypeBitMask ) 
            {
                case EXPR_FLAG_LO | EXPR_FLAG_HI:
                    throw new IllegalArgumentException("Relocation cannot use HIGH() and LOW() at the same time");
                case EXPR_FLAG_LO:
                    return R_AVR_LO8_LDI;
                case EXPR_FLAG_LO | EXPR_FLAG_PM :
                    return R_AVR_LO8_LDI_PM;
                case EXPR_FLAG_HI:
                    return R_AVR_HI8_LDI;         
                case EXPR_FLAG_HI | EXPR_FLAG_PM:
                    return R_AVR_HI8_LDI_PM;      
                case EXPR_FLAG_LO | EXPR_FLAG_PM | EXPR_FLAG_NEG:
                    return R_AVR_LO8_LDI_PM_NEG;     
                case EXPR_FLAG_LO | EXPR_FLAG_NEG:
                    return R_AVR_LO8_LDI_NEG;   
                case EXPR_FLAG_HI | EXPR_FLAG_NEG:
                    return R_AVR_HI8_LDI_NEG;        
                case EXPR_FLAG_HI | EXPR_FLAG_NEG | EXPR_FLAG_PM:
                    return R_AVR_HI8_LDI_PM_NEG;                      
                default:
                    return R_AVR_LDI;
            }
        }
    }

    public Relocation(Symbol s) 
    {
        Validate.notNull(s, "symbol must not be NULL");
        if ( ! s.hasType( Type.ADDRESS_LABEL ) ) {
            throw new IllegalArgumentException("Symbol must be an address label");
        }
        this.symbol = s;
    }
    @Override
    public String toString() {
        return "Relocation [kind=" + kind + ", addend=" + addend + ", locationOffset=" + locationOffset + ", symbol="+ symbol + "]";
    }
}