package de.codesourcery.javr.assembler.elf;

public class Relocation
{
    public Kind kind;
    public int addend;
    public int offset;

    public Relocation(Kind kind, int offset, int addend) {
        this.kind = kind;
        this.offset = offset;
        this.addend = addend;
    }

    public static enum Kind 
    {
        R_AVR_NONE(0),
        R_AVR_32(1),
        R_AVR_7_PCREL(2),
        R_AVR_13_PCREL(3),
        R_AVR_16(4),
        R_AVR_16_PM(5),
        // LDI
        R_AVR_LO8_LDI(6),
        R_AVR_HI8_LDI(7),
        R_AVR_HH8_LDI(8),
        R_AVR_LO8_LDI_NEG(9),
        R_AVR_HI8_LDI_NEG(10),
        R_AVR_HH8_LDI_NEG(11),
        R_AVR_LO8_LDI_PM(12),
        R_AVR_HI8_LDI_PM(13),
        R_AVR_HH8_LDI_PM(14),
        R_AVR_LO8_LDI_PM_NEG(15),
        R_AVR_HI8_LDI_PM_NEG(16),
        R_AVR_HH8_LDI_PM_NEG(17),
        R_AVR_MS8_LDI(22),
        R_AVR_MS8_LDI_NEG(23),
        R_AVR_LO8_LDI_GS(24),
        R_AVR_HI8_LDI_GS(25),        
        R_AVR_LDI(19),
        // other
        R_AVR_CALL(18),
        R_AVR_6(20),
        R_AVR_6_ADIW(21), // ok
        R_AVR_8(26),
        R_AVR_8_LO8(27),
        R_AVR_8_HI8(28),
        R_AVR_8_HLO8(29),
        R_AVR_LDS_STS_16(33),
        R_AVR_PORT6(34),
        R_AVR_PORT5(35),
        R_AVR_DIFF8(30),
        R_AVR_DIFF16(31),
        R_AVR_DIFF32(32);        

        public final int elfId;

        private Kind(int elfId) {
            this.elfId = elfId;
        }
    }