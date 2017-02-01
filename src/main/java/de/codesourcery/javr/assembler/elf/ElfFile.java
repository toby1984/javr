package de.codesourcery.javr.assembler.elf;

import java.io.*;
import java.util.*;

import org.apache.commons.lang3.Validate;

import de.codesourcery.javr.assembler.*;
import de.codesourcery.javr.assembler.arch.IArchitecture;
import de.codesourcery.javr.assembler.arch.impl.ATMega328p;
import de.codesourcery.javr.assembler.elf.ProgramTableEntry.SegmentFlag;
import de.codesourcery.javr.assembler.elf.ProgramTableEntry.SegmentType;
import de.codesourcery.javr.assembler.elf.SectionTableEntry.SectionType;
import de.codesourcery.javr.assembler.elf.SectionTableEntry.SpecialSection;
import de.codesourcery.javr.assembler.symbols.SymbolTable;
import de.codesourcery.javr.assembler.util.FileResourceFactory;
import de.codesourcery.javr.assembler.util.Resource;
import de.codesourcery.javr.ui.Project;

public class ElfFile 
{
    /* Magic section header index value: The symbol has an absolute value that will not change because of relocation. */
    public static final int SHN_ABS = 0xfff1;

    /* Magic section header index value: The symbol labels a common block that has not yet been allocated. 
     *  
     *  The symbol’s value gives alignment constraints, similar to a section’s sh_addralign member. That is, the
     *  link editor will allocate the storage for the symbol at an address that is a multiple of
     *  st_value. The symbol’s size tells how many bytes are required.
     */
    public static final int SHN_COMMON = 0xfff2;

    /* Magic section header index value: This section table index means the symbol is undefined.
     *  
     * When the link editor combines this object file with another that defines the indicated symbol, this file’s references to the
     * symbol will be linked to the actual definition.
     */
    public static final int SHN_UNDEF = 0;
    
    public static final String MARKER_HEADER_START = "fileheader_start";
    public static final String MARKER_HEADER_END = "fileheader_end";
    
    public static final String MARKER_SECTION_TABLE_START = "section_table";
    public static final String MARKER_SECTION_NAME_STRING_TABLE_START = "section_name_string_table_start";
    public static final String MARKER_SECTION_NAME_STRING_TABLE_END = "section_name_string_table_end";
    
    public static final String MARKER_PROGRAM_HEADER_TABLE_START = "program_header_start";
    public static final String MARKER_PROGRAM_HEADER_TABLE_END = "program_header_end";
    
    public static final String MARKER_TEXT_START = "text_start";
    public static final String MARKER_TEXT_END = "text_end";
    
    public static final String MARKER_DATA_START = "data_start";
    public static final String MARKER_DATA_END = "data_end";
    
    public static final String MARKER_SECTION_SYMBOL_NAMES_TABLE_START = "section_symbol_names_table_start";
    public static final String MARKER_SECTION_SYMBOL_NAMES_TABLE_END = "section_symbol_names_table_end";
    
    public static final String MARKER_SECTION_SYMBOL_TABLE_START = "section_symbol_table_start";
    public static final String MARKER_SECTION_SYMBOL_TABLE_END = "section_symbol_table_end";
    
    
    public final ElfHeader header = new ElfHeader(this);
    
    public final List<SectionTableEntry> sectionTableEntries = new ArrayList<>();
    
    public final List<ProgramTableEntry> programHeaders = new ArrayList<>();
    
    public final StringTable sectionNames = new StringTable();
    
    public final StringTable symbolNames = new StringTable();
    
    public SectionTableEntry sectionNamesEntry;
    
    public SectionTableEntry textSegmentEntry;
    
    public SectionTableEntry dataSegmentEntry;
    
    public SectionTableEntry symNamesEntry;
    
    public SectionTableEntry symTab;
    
    public ElfSymbolTable symbolTable = new ElfSymbolTable(this);
    
    private ProgramTableEntry textSegment;
    
    public ElfFile()
    {
    }
    
    public static void main(String[] args) throws FileNotFoundException, IOException 
    {
        final String src = "main:  ldi r16,0xff\n";
        final File baseDir = new File(".");
        
        final Assembler asm = new Assembler();
        final Resource res = Resource.forString("dummy", src );
        final CompilationUnit root = new CompilationUnit( res );
        
        final Project project = new Project( root );
        project.setCompileRoot( root );
        project.setArchitecture( new ATMega328p() );
        
        final ObjectCodeWriter writer = new ObjectCodeWriter();
        
        final ResourceFactory resFactory = FileResourceFactory.createInstance( baseDir );
        
        if ( ! asm.compile( project , writer , resFactory , project ) ) {
            throw new IllegalStateException("Compilation failed");
        }
        
        final byte[] text = writer.getBuffer( Segment.FLASH ).toByteArray();
        System.out.println("Object code size: "+text.length+" bytes");
        
        final String outputFile = "/home/tobi/tmp/my.elf";
        try ( FileOutputStream out = new FileOutputStream( outputFile ) ) {
            new ElfFile().write( project.getArchitecture() , writer , project.getGlobalSymbolTable(), out );
        }
        System.out.println("Wrote to "+outputFile);
    }
    
    public int write(IArchitecture arch, IObjectCodeWriter objWriter , SymbolTable globalSymbolTable, OutputStream out) throws IOException 
    {
        Validate.notNull(objWriter, "objWriter must not be NULL");
        Validate.notNull(globalSymbolTable , "globalSymbolTable  must not be NULL");
        Validate.notNull(out, "out must not be NULL");
        
        setupSections( arch , objWriter );
        symbolTable.addSymbols( globalSymbolTable );
        
        final ElfWriter writer = new ElfWriter(this);
        
        /**********************
         * Write file header
         *********************/
        writer.createMarker( MARKER_HEADER_START );
        header.write( writer );
        writer.createMarker( MARKER_HEADER_END );

        /************************
         * Write program header table
         ***********************/
        
        writer.createMarker( MARKER_PROGRAM_HEADER_TABLE_START );
        for ( ProgramTableEntry entry : programHeaders ) 
        {
            entry.write( writer );
        }
        writer.createMarker( MARKER_PROGRAM_HEADER_TABLE_END);
        
        /*****************
         * Write section header
         ****************/

        writer.createMarker(MARKER_SECTION_TABLE_START);
        
        for ( SectionTableEntry entry : sectionTableEntries ) 
        {
            entry.write( writer );
        }
        
        /******************
         * Write section name string table
         *****************/
        
        writer.createMarker( MARKER_SECTION_NAME_STRING_TABLE_START );
        sectionNames.write( writer );
        writer.createMarker( MARKER_SECTION_NAME_STRING_TABLE_END );
        
        
        /**********************
         * Write .text payload
         *********************/
        
        writer.align(2);
        writer.createMarker( MARKER_TEXT_START );
        writer.writeBytes( objWriter.getBuffer(Segment.FLASH).toByteArray() );
        writer.createMarker( MARKER_TEXT_END );
        
        /**********************
         * Write .data section
         *********************/
        
        if ( objWriter.getBuffer( Segment.SRAM ).isNotEmpty() ) 
        {
            writer.align(2);
            writer.createMarker( MARKER_DATA_START );
            writer.writeBytes( objWriter.getBuffer(Segment.SRAM).toByteArray() );
            writer.createMarker( MARKER_DATA_END );
        }
        
        /**********************
         * Write symbol table
         *********************/     
        
        writer.align( 4 );
        writer.createMarker( MARKER_SECTION_SYMBOL_TABLE_START );        
        symbolTable.write( writer );
        writer.createMarker( MARKER_SECTION_SYMBOL_TABLE_END );
        
        /**********************
         * Write symbol names table
         *********************/
        
        writer.createMarker( MARKER_SECTION_SYMBOL_NAMES_TABLE_START );        
        symbolNames.write( writer );
        writer.createMarker( MARKER_SECTION_SYMBOL_NAMES_TABLE_END );         
        
        /********************
         * Execute deferred writes
         *******************/        
        writer.execDeferredWrites();
        
        /********************
         * Write data to output stream
         *******************/
        final byte[] fileData = writer.getBytes();
        out.write( fileData );
        return fileData.length;
    }

    public int getTableIndex(SectionTableEntry entry) 
    {
        if ( entry == null ) {
            throw new IllegalArgumentException("entry must not be NULL");
        }
        int result = sectionTableEntries.indexOf( entry );
        if ( result == -1 ) {
            throw new NoSuchElementException("Unknown section "+entry);
        }
        return result;
    }
    
    public String getSectionName(SectionTableEntry header) 
    {
        return sectionNames.getStringAtByteOffset( header.sh_name );
    }
    
    public SectionTableEntry getSectionByName(String name) 
    {
        for ( SectionTableEntry hdr : sectionTableEntries ) 
        {
            if ( getSectionName( hdr ).equals( name ) ) {
                return hdr;
            }
        }
        return null;
    }
    
    public int getPayloadOffset(ProgramTableEntry entry,ElfWriter writer) {
        if ( entry == textSegment ) 
        {
            return writer.getMarker( MARKER_TEXT_START ).offset;
        }
        throw new RuntimeException("Internal error, don't know how to handle program header "+entry);
    }
    
    public int getPayloadSize(ProgramTableEntry entry,ElfWriter writer) 
    {
        if ( entry == textSegment ) 
        {
            final int start = writer.getMarker( MARKER_TEXT_START ).offset;
            final int end = writer.getMarker( MARKER_TEXT_END ).offset;
            return end - start;
        }
        throw new RuntimeException("Internal error, don't know how to handle program header "+entry);  
    }
    
    public int getSectionDataOffset(SectionTableEntry section ,ElfWriter writer) 
    {
        if ( section == sectionNamesEntry ) {
            return writer.getMarker( MARKER_SECTION_NAME_STRING_TABLE_START ).offset;
        }
        if ( section.sh_type == SectionType.SHT_NULL ) {
            return 0;
        }
        if ( section == textSegmentEntry ) {
            return getPayloadOffset( textSegment , writer );
        }
        if ( section == dataSegmentEntry ) {
            return writer.getMarker( MARKER_DATA_START ).offset;
        }        
        if ( section == symTab ) {
            return writer.getMarker( MARKER_SECTION_SYMBOL_TABLE_START ).offset;
        }
        if ( section == symNamesEntry) {
            return writer.getMarker( MARKER_SECTION_SYMBOL_NAMES_TABLE_START ).offset;
        }        
        throw new RuntimeException("Internal error, don't know how to handle section '"+getSectionName(section)+"'");
    }
    
    public int getSectionDataSize(SectionTableEntry section,ElfWriter writer) 
    {
        if ( section == null ) {
            throw new NoSuchElementException("Unknown section '"+getSectionName(section)+"'");
        }
        if ( section == sectionNamesEntry )
        {
            int start = writer.getMarker( MARKER_SECTION_NAME_STRING_TABLE_START ).offset;
            int end = writer.getMarker( MARKER_SECTION_NAME_STRING_TABLE_END ).offset;
            return end-start;
        }
        if ( section == textSegmentEntry ) {
            return getPayloadSize( textSegment , writer );
        }        
        if ( section == dataSegmentEntry ) {
            final int start = writer.getMarker( MARKER_DATA_START ).offset;
            final int end = writer.getMarker( MARKER_DATA_END ).offset;
            return end-start;
        }        
        if ( section == symTab ) 
        {
            final int start = writer.getMarker( MARKER_SECTION_SYMBOL_TABLE_START ).offset;
            final int end = writer.getMarker( MARKER_SECTION_SYMBOL_TABLE_END ).offset;
            return end-start;
        }
        if ( section == symNamesEntry ) 
        {
            final int start = writer.getMarker( MARKER_SECTION_SYMBOL_NAMES_TABLE_START ).offset;
            final int end = writer.getMarker( MARKER_SECTION_SYMBOL_NAMES_TABLE_END ).offset;
            return end-start;
        }        
        if ( section.sh_type == SectionType.SHT_NULL ) {
            return 0;
        }        
        throw new RuntimeException("Internal error, don't know how to handle section '"+getSectionName(section)+"'");
    }
    
    public int getProgramHeaderCount() {
        return programHeaders.size();
    }
    
    public int getSectionCount() {
        return sectionTableEntries.size();
    }
    
    
    private void setupSections(IArchitecture arch, IObjectCodeWriter objWriter) 
    {
        /*
    [ 1] .text             PROGBITS        00000000 000074 00000e 00  AX  0   0  2
  [ 2] .data             PROGBITS        00800060 000082 000000 00  WA  0   0  1
  [ 3] .comment          PROGBITS        00000000 000082 000011 01  MS  0   0  1
  [ 4] .shstrtab         STRTAB          00000000 000093 000030 00      0   0  1
  [ 5] .symtab           SYMTAB          00000000 0000c4 000170 10      6  10  4
  [ 6] .strtab           STRTAB          00000000 000234 0000dd 00      0   0  1       
         */
        // setup sections
        final SectionTableEntry nullEntry = new SectionTableEntry(this);
        nullEntry.sh_type = SectionType.SHT_NULL;
        sectionTableEntries.add( nullEntry );
        
        // .text segment
        textSegmentEntry = new SectionTableEntry(this);
        textSegmentEntry.setType( SpecialSection.TEXT );
        textSegmentEntry.sh_addralign = 2;
        textSegmentEntry.sh_name = sectionNames.add( SpecialSection.TEXT.name );

        sectionTableEntries.add( textSegmentEntry );
        
        // .data segment        
        if ( objWriter.getBuffer( Segment.SRAM).isNotEmpty() ) {
            dataSegmentEntry = new SectionTableEntry(this);
            dataSegmentEntry.setType( SpecialSection.DATA );
            dataSegmentEntry.sh_addralign = 2;
            dataSegmentEntry.sh_addr = 0x800000 + arch.getSRAMStartAddress();
            dataSegmentEntry.sh_name = sectionNames.add( SpecialSection.DATA.name );
            sectionTableEntries.add( dataSegmentEntry );
        }
        
        // .shrstrtab names
        sectionNamesEntry = new SectionTableEntry(this);
        sectionNamesEntry.setType( SpecialSection.SHSTRTAB );
        sectionNamesEntry.sh_addralign = 1;
        sectionNamesEntry.sh_name = sectionNames.add( SpecialSection.SHSTRTAB.name );
        
        sectionTableEntries.add( sectionNamesEntry );
                
        // .symtab segment
        symNamesEntry = new SectionTableEntry(this);
        
        symTab = new SectionTableEntry(this);
        symTab.setType( SpecialSection.SYMTAB );
        symTab.sh_name = sectionNames.add( SpecialSection.SYMTAB.name );
        symTab.sh_addralign = 4;
        symTab.linkedEntry = symNamesEntry;
        symTab.sh_entsize = ElfSymbolTable.SYMBOL_TABLE_ENTRY_SIZE;
        
        sectionTableEntries.add( symTab );
        
        // symbol name table
        symNamesEntry.setType( SpecialSection.STRTAB );
        symNamesEntry.sh_addralign = 1;
        symNamesEntry.sh_name = sectionNames.add( SpecialSection.STRTAB.name );
        sectionTableEntries.add( symNamesEntry );
        
        // setup program headers
        textSegment = new ProgramTableEntry(this);
        textSegment.p_type = SegmentType.PT_LOAD;
        textSegment.addFlags(SegmentFlag.PF_X , SegmentFlag.PF_R );
        textSegment.p_align = 2;
        
        programHeaders.add( textSegment );
    }
    
}