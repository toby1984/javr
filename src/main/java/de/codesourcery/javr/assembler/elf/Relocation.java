package de.codesourcery.javr.assembler.elf;

import org.apache.commons.lang3.Validate;

import de.codesourcery.javr.assembler.symbols.Symbol;

public class Relocation
{
    public final Kind kind;
    public final int addend;
    public final int offset;
    public final Symbol symbol;

    public Relocation(Symbol symbol,Kind kind, int offset, int addend) 
    {
        Validate.notNull(symbol, "symbol must not be NULL");
        Validate.notNull(kind, "kind must not be NULL");
        this.symbol = symbol;
        this.kind = kind;
        this.offset = offset;
        this.addend = addend;
    }

    public static enum Kind 
    {
        R_AVR_NONE(0),
        // absolute values (for example .byte directive in .data section)
        R_AVR_16(4),
        R_AVR_16_PM(5),      
        R_AVR_8(26),
        R_AVR_8_LO8(27),
        R_AVR_8_HI8(28),
        R_AVR_8_HLO8(29),        
        // conditional branches
        R_AVR_7_PCREL(2),
        R_AVR_13_PCREL(3),
        // unconditional branches
        R_AVR_CALL(18),        
        // LDI
        R_AVR_LDI(19),
        R_AVR_LO8_LDI(6),
        R_AVR_LO8_LDI_PM(12),
        R_AVR_HI8_LDI(7),
        R_AVR_HI8_LDI_PM(13),
        // negative addresses
        R_AVR_LO8_LDI_PM_NEG(15),
        R_AVR_LO8_LDI_NEG(9), // ldi r16,lo8(-(label))
        R_AVR_HI8_LDI_NEG(10),
        R_AVR_HI8_LDI_PM_NEG(16),
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

        private Kind(int elfId) {
            this.elfId = elfId;
        }
    }
}