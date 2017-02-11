package de.codesourcery.javr.assembler.elf;

import java.io.IOException;

import de.codesourcery.javr.assembler.elf.ElfWriter.Endianess;
import de.codesourcery.javr.assembler.elf.SectionTableEntry.SpecialSection;
import de.codesourcery.javr.ui.config.ProjectConfiguration.OutputFormat;

public class ElfHeader 
{

    // ELF header file types
    private static final int FILE_TYPE_RELOCATABLE = 1;
    private static final int FILE_TYPE_EXECUTABLE = 2;
    
    public final ElfFile elfFile;
    
    public ElfHeader(ElfFile file) {
        this.elfFile = file;
    }
    
    public void write(ElfWriter writer) throws IOException {
        /*

 enum {
00050   EI_MAG0       = 0,          // File identification index.
00051   EI_MAG1       = 1,          // File identification index.
00052   EI_MAG2       = 2,          // File identification index.
00053   EI_MAG3       = 3,          // File identification index.
00054   EI_CLASS      = 4,          // File class.
00055   EI_DATA       = 5,          // Data encoding.
00056   EI_VERSION    = 6,          // File version.
00057   EI_OSABI      = 7,          // OS/ABI identification.
00058   EI_ABIVERSION = 8,          // ABI version.
00059   EI_PAD        = 9,          // Start of padding bytes.
00060   EI_NIDENT     = 16          // Number of bytes in e_ident.
00061 };
         
typedef struct 
{
unsigned char  e_ident [EI_NIDENT] ; 0   
  
Elf32_Half     e_type; // 16             
Elf32_Half     e_machine; // 18                 
Elf32_Word     e_version; // 20                   
Elf32_Addr     e_entry; // 22                      
Elf32_Off      e_phoff; // 26                
Elf32_Off      e_shoff; // 30                
Elf32_Word     e_flags; // 34                
Elf32_Half     e_ehsize;// 36                    

Elf32_Half     e_phentsize; // 38                  
Elf32_Half     e_phnum;  // 40    
                
Elf32_Half     e_shentsize; // 42                  
Elf32_Half     e_shnum; // 44                      

Elf32_Half     e_shstrndx; // 46
// 48                   
} El32_Ehdr ;
         */        
        
        writer.writeByte( 0x7f ); // eident[EI_MAG0]
        writer.writeByte( 'E' ); // eident[EI_MAG1]
        writer.writeByte( 'L' ); // eident[EI_MAG2]
        writer.writeByte( 'F' ); // eident[EI_MAG3]
        writer.writeByte( 0x01 ); // eident[EI_CLASS] => ELF32
        writer.writeByte( 0x01 ); // eident[EI_DATA] => LSB / little-endian
        writer.writeByte( 0x01 ); // eident[EI_VERSION] => ELF v1
        writer.pad( 9 );
        
        if ( elfFile.type == OutputFormat.ELF_EXECUTABLE ) {
            writer.writeHalf( FILE_TYPE_EXECUTABLE , Endianess.LITTLE ); // 16: e_type
        } else if ( elfFile.type == OutputFormat.ELF_RELOCATABLE) {
            writer.writeHalf( FILE_TYPE_RELOCATABLE, Endianess.LITTLE ); // 16: e_type 
        } else {
            throw new RuntimeException("Internal error,unhandled output type "+elfFile.type);
        }
        
        writer.writeHalf( 0x53 , Endianess.LITTLE ); // 18: e_machine => AVR
        writer.writeWord( 0x01 , Endianess.LITTLE);  // 20: e_version
        writer.writeWord( 0 , Endianess.LITTLE); // 24: e_entry => address of program entry point
        
        writer.deferredWriteWord( (w,file) -> w.getMarker( ElfFile.MarkerName.PROGRAM_HEADER_TABLE_START).offset , Endianess.LITTLE); // 28: e_phoff => program header table offset
        
        writer.deferredWriteWord( ElfFile.MarkerName.SECTION_TABLE_START , Endianess.LITTLE ); // 32: e_shoff => section header table offset
        writer.writeWord( 0x00 , Endianess.LITTLE ); // 36: e_flags
        writer.writeHalf( 52 , Endianess.LITTLE);  // 40: e_ehsize => ELF header size
        writer.writeHalf( ProgramTableEntry.SIZE_IN_BYTES , Endianess.LITTLE);  // 42: Elf32_Half     e_phentsize;  // size of ONE entry in the program header table
        writer.deferredWriteHalf( (w,file) -> file.getProgramHeaderCount() , Endianess.LITTLE );  // 44: Elf32_Half     e_phnum;  // number of entries in program header table
        
        writer.writeHalf( SectionTableEntry.SIZE_IN_BYTES , Endianess.LITTLE);  // 46: Elf32_Half     e_shentsize; // size of ONE entry in the section header table
        
        writer.deferredWriteHalf( (w,file) -> file.getSectionCount(), Endianess.LITTLE ); // 48: e_shnum => number of section header entries (=number of sections)

        writer.deferredWriteHalf(  (w,file) -> 
        file.getTableIndex( file.getSectionByName(  SpecialSection.SHSTRTAB.name ) ) , Endianess.LITTLE);  // 50: Elf32_Half     e_shstrndx; // section header table index of the entry associated with the section name string table
    }
}
