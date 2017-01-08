package de.codesourcery.javr.assembler.elf;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import de.codesourcery.javr.assembler.elf.ElfWriter.Endianess;

public class ProgramTableEntry {

    public static final int SIZE_IN_BYTES = 32;
    
    public final ElfFile elfFile;

    public SegmentType p_type;
    public int p_offset;
    public int p_vaddr;
    public int p_paddr;
    public int p_filesz;
    public int p_memsz;
    public final Set<SegmentFlag> p_flags = new HashSet<>();
    public int p_align;

    public enum SegmentType 
    {        
        PT_NULL(0),
        PT_LOAD(1),
        PT_DYNAMIC(2),
        PT_INTERP(3),
        PT_NOTE(4),
        PT_SHLIB(5),
        PT_PHDR(6),
        PT_LOPROC(0x70000000),
        PT_HIPROC(0x7fffffff);

        public final int value;

        private SegmentType(int value) {
            this.value = value;
        }
    }
    
    public enum SegmentFlag  {
        PF_X(1),
        PF_W(2),
        PF_R(4),
        PF_MASKPROC(0xf0000000);
        
        public final int value;

        private SegmentFlag(int value) {
            this.value = value;
        }
        
    }

    public ProgramTableEntry(ElfFile file) {
        this.elfFile = file;
    }

    public void addFlags(SegmentFlag f1,SegmentFlag... flags) 
    {
        p_flags.add(f1);
        if ( flags != null ) {
            Arrays.stream( flags ).forEach( p_flags::add );

        }
    }
    public void write(ElfWriter writer) 
    {

        System.out.println("Writing program header entry "+p_type+" ("+p_type.value+") at offset "+writer.currentOffset());
        /*
typedef struct {
    Elf32_Word p_type;   // 0    
    Elf32_Off  p_offset; // 4
    Elf32_Addr p_vaddr;  // 8
    Elf32_Addr p_paddr;  // 12
    Elf32_Word p_filesz; // 16
    Elf32_Word p_memsz;  // 20
    Elf32_Word p_flags;  // 24
    Elf32_Word p_align;  // 28
} Elf32_Phdr;
         */

        writer.writeWord( p_type.value , Endianess.LITTLE ); // 0
        
        writer.deferredWriteWord( (w,f) -> f.getPayloadOffset(this,w) ,  Endianess.LITTLE );     // 4: p_offset
        
        writer.writeWord( p_vaddr, Endianess.LITTLE );       // 8
        writer.writeWord( p_paddr, Endianess.LITTLE );       // 12
        
        writer.deferredWriteWord( (w,f) -> f.getPayloadSize(this,w) ,  Endianess.LITTLE );     // 16: p_filesz
        writer.deferredWriteWord( (w,f) -> f.getPayloadSize(this,w) ,  Endianess.LITTLE );     // 20: p_memsz
        
        writer.writeWord( p_flags.stream().mapToInt( f -> f.value).sum() , Endianess.LITTLE );       // 24
        writer.writeWord( p_align, Endianess.LITTLE );       // 28
    }
}