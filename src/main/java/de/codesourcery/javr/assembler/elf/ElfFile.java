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
package de.codesourcery.javr.assembler.elf;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

import org.apache.commons.lang3.Validate;

import de.codesourcery.javr.assembler.Assembler;
import de.codesourcery.javr.assembler.CompilationUnit;
import de.codesourcery.javr.assembler.IObjectCodeWriter;
import de.codesourcery.javr.assembler.ObjectCodeWriter;
import de.codesourcery.javr.assembler.ResourceFactory;
import de.codesourcery.javr.assembler.Segment;
import de.codesourcery.javr.assembler.arch.IArchitecture;
import de.codesourcery.javr.assembler.arch.impl.ATMega328p;
import de.codesourcery.javr.assembler.elf.ElfWriter.Endianess;
import de.codesourcery.javr.assembler.elf.ProgramTableEntry.SegmentFlag;
import de.codesourcery.javr.assembler.elf.ProgramTableEntry.SegmentType;
import de.codesourcery.javr.assembler.elf.SectionTableEntry.SectionHeaderInfoAndLink;
import de.codesourcery.javr.assembler.elf.SectionTableEntry.SectionType;
import de.codesourcery.javr.assembler.elf.SectionTableEntry.SpecialSection;
import de.codesourcery.javr.assembler.symbols.SymbolTable;
import de.codesourcery.javr.assembler.util.FileResourceFactory;
import de.codesourcery.javr.assembler.util.Resource;
import de.codesourcery.javr.ui.Project;
import de.codesourcery.javr.ui.config.ProjectConfiguration;
import de.codesourcery.javr.ui.config.ProjectConfiguration.OutputFormat;

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
    
    public static final int RELOCATION_SECTION_ENTRY_SIZE = 0x0c;
    
    public static enum MarkerName 
    {
        HEADER_START("fileheader_start"),
        HEADER_END("fileheader_end"),
        SECTION_TABLE_START("section_table"),
        SECTION_NAME_STRING_TABLE_START("section_name_string_table_start"),
        SECTION_NAME_STRING_TABLE_END("section_name_string_table_end"),
        PROGRAM_HEADER_TABLE_START("program_header_start"),
        PROGRAM_HEADER_TABLE_END("program_header_end"),
        DATA_RELOCATIONS_START("data_relocations_start"),
        TEXT_RELOCATIONS_START("text_relocations_start"),
        TEXT_START("text_start"),
        TEXT_END("text_end"),
        DATA_START("data_start"),
        DATA_END("data_end"),
        SECTION_SYMBOL_NAMES_TABLE_START("section_symbol_names_table_start"),
        SECTION_SYMBOL_NAMES_TABLE_END("section_symbol_names_table_end"),
        SECTION_SYMBOL_TABLE_START("section_symbol_table_start"),
        SECTION_SYMBOL_TABLE_END("section_symbol_table_end");
        
        public final String name;
        
        private MarkerName(String name) {
            this.name= name;
        }
    }
    
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
    
    private final Map<Segment,SectionTableEntry> relocationEntries = new HashMap<>();
    
    public ElfSymbolTable symbolTable = new ElfSymbolTable(this);
    
    private ProgramTableEntry textSegment;
    
    public final OutputFormat type;
    
    private IObjectCodeWriter objectCodeWriter;
    
    public ElfFile(OutputFormat type)
    {
        Validate.notNull(type, "type must not be NULL");
        this.type = type;
    }
    
    public static void main(String[] args) throws FileNotFoundException, IOException 
    {
        final String src = ""
                + "mydelay: ldi r16,0xff\n"
                + ".loop  dec r16\n"
                + "brne loop\n"
                + "ret\n";
        final File baseDir = new File(".");
        final OutputFormat outputFormat = OutputFormat.ELF_RELOCATABLE;
        
        final Assembler asm = new Assembler();
        final Resource res = Resource.forString("dummy", src );
        final CompilationUnit root = new CompilationUnit( res );
        
        final Project project = new Project( root );
        project.setArchitecture( new ATMega328p() );
        
        /*
         * TODO: Hacky... Project class assumes that sources are in local filesystem so Project#setConfiguration(IProjectConfiguration)
         * TODO: will actually try to resolve the root compilation unit from the filesystem , overwriting
         * TODO: the compilation root we previously set when calling new Project( compilationRoot) 
         */
        ProjectConfiguration copy = project.getConfiguration(); 
        copy.setOutputFormat( outputFormat );
        project.setConfiguration( copy ); // compilationRoot get
        
        // discard compilation root from filesystem and try again... 
        final CompilationUnit oldRoot = project.getCompileRoot();
        project.setCompileRoot( root );
        project.removeCompilationUnit( oldRoot );
        
        final ObjectCodeWriter writer = new ObjectCodeWriter();
        
        final ResourceFactory resFactory = FileResourceFactory.createInstance( baseDir );
        
        if ( ! asm.compile( project , writer , resFactory , project ) ) {
            throw new IllegalStateException("Compilation failed");
        }
        
        final byte[] text = writer.getBuffer( Segment.FLASH ).toByteArray();
        System.out.println("Object code size: "+text.length+" bytes");
        
        final String outputFile = "/home/tobi/tmp/my.elf";
        try ( FileOutputStream out = new FileOutputStream( outputFile ) ) {
            new ElfFile(outputFormat).write( project.getArchitecture() , writer , project.getGlobalSymbolTable(), out );
        }
        System.out.println("Wrote to "+outputFile);
    }
    
    /**
     * Write ELF file.
     * 
     * @param arch
     * @param objWriter
     * @param symbols
     * @param out
     * @return
     * @throws IOException
     */
    public int write(IArchitecture arch, IObjectCodeWriter objWriter , SymbolTable symbols, OutputStream out) throws IOException 
    {
        Validate.notNull(objWriter, "objWriter must not be NULL");
        Validate.notNull(symbols , "symbols must not be NULL");
        Validate.notNull(out, "out must not be NULL");
        
        this.objectCodeWriter = objWriter;
        
        setupSections( arch , objWriter );
        
        symbolTable.addSymbols( symbols );
        
        final ElfWriter writer = new ElfWriter(this);
        
        /**********************
         * Write file header
         *********************/
        writer.createMarker( MarkerName.HEADER_START );
        header.write( writer );
        writer.createMarker( MarkerName.HEADER_END );

        /************************
         * Write program header table
         ***********************/
        
        if ( type == OutputFormat.ELF_EXECUTABLE) 
        {         
            writer.createMarker( MarkerName.PROGRAM_HEADER_TABLE_START );
            for ( ProgramTableEntry entry : programHeaders ) 
            {
                entry.write( writer );
            }
            writer.createMarker( MarkerName.PROGRAM_HEADER_TABLE_END);
        }
        
        /*****************
         * Write section header
         ****************/

        writer.createMarker(MarkerName.SECTION_TABLE_START);
        
        for ( SectionTableEntry entry : sectionTableEntries ) 
        {
            entry.write( writer );
        }
        
        /******************
         * Write section name string table
         *****************/
        
        writer.createMarker( MarkerName.SECTION_NAME_STRING_TABLE_START );
        sectionNames.write( writer );
        writer.createMarker( MarkerName.SECTION_NAME_STRING_TABLE_END );
        
        /**********************
         * Write .text payload
         *********************/
        
        writer.align(2);
        writer.createMarker( MarkerName.TEXT_START );
        writer.writeBytes( objWriter.getBuffer(Segment.FLASH).toByteArray() );
        writer.createMarker( MarkerName.TEXT_END );
        
        /**********************
         * Write relocation section headers
         *********************/
        
        if ( type == OutputFormat.ELF_RELOCATABLE) 
        {
            for ( Segment s : Segment.values() ) 
            {
                final List<Relocation> relocs = objWriter.getRelocations( s ); 
                if ( ! relocs.isEmpty() ) 
                {
                    final MarkerName marker;
                    if ( s == Segment.FLASH ) {
                        marker = MarkerName.TEXT_RELOCATIONS_START;
                    } else if ( s == Segment.SRAM ) {
                        marker = MarkerName.DATA_RELOCATIONS_START;
                    } else {
                        throw new RuntimeException("Internal error,unhandled segment: "+s);
                    }
                    
                    writer.align(4);                    
                    writer.createMarker( marker );
                    for ( Relocation r : relocs ) 
                    {
                        /* typedef struct 
                         * {
                         *     Elf32_Addr  r_offset;
                         *     Elf32_Word  r_info;
                         *     Elf32_Sword r_addend;
                         * } Elf32_Rela
                         */
                        writer.writeWord( r.locationOffset , Endianess.LITTLE );
                        final int symbolTableIdx;
                        if ( r.relocateRelativeToStartOf != null ) 
                        {
                            if ( r.relocateRelativeToStartOf == Segment.FLASH ) {
                                symbolTableIdx = symbolTable.indexOf( symbolTable.textSectionSymbol );
                            } else if ( r.relocateRelativeToStartOf == Segment.SRAM ) {
                                symbolTableIdx = symbolTable.indexOf( symbolTable.dataSectionSymbol );
                            } else {
                                throw new RuntimeException("Not implemented - relocation relative to start of segment "+r.relocateRelativeToStartOf);
                            }
                        } 
                        else if ( r.symbol.isLocalLabel() ) { // avr-gcc does relocations of local labels always relative to their segment 
                            if ( r.symbol.getSegment() != Segment.FLASH ) { // we currently only support local labels within the text segment
                                throw new RuntimeException("Internal error, relocation of symbol "+r.symbol+" that is not in .text segment ?");
                            }
                            symbolTableIdx = symbolTable.indexOf( symbolTable.textSectionSymbol );
                        } else {
                            symbolTableIdx = symbolTable.indexOf( symbolTable.textSectionSymbol );
                        }
                        final int typeAndSymbolIdx = symbolTableIdx<<8 | r.kind.elfId;
                        writer.writeWord( typeAndSymbolIdx , Endianess.LITTLE );
                        writer.writeWord( r.addend , Endianess.LITTLE );
                    }
                }
            }
        }
        
        /**********************
         * Write .data section
         *********************/
        
        if ( objWriter.getBuffer( Segment.SRAM ).isNotEmpty() ) 
        {
            writer.align(2);
            writer.createMarker( MarkerName.DATA_START );
            writer.writeBytes( objWriter.getBuffer(Segment.SRAM).toByteArray() );
            writer.createMarker( MarkerName.DATA_END );
        }
        
        /**********************
         * Write symbol table
         *********************/     
        
        writer.align( 4 );
        writer.createMarker( MarkerName.SECTION_SYMBOL_TABLE_START );        
        symbolTable.write( writer );
        writer.createMarker( MarkerName.SECTION_SYMBOL_TABLE_END );
        
        /**********************
         * Write symbol names table
         *********************/
        
        writer.createMarker( MarkerName.SECTION_SYMBOL_NAMES_TABLE_START );        
        symbolNames.write( writer );
        writer.createMarker( MarkerName.SECTION_SYMBOL_NAMES_TABLE_END );         
        
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

    int getTableIndex(SectionTableEntry entry) 
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
    
    String getSectionName(SectionTableEntry header) 
    {
        return sectionNames.getStringAtByteOffset( header.sh_name );
    }
    
    SectionTableEntry getSectionByName(String name) 
    {
        for ( SectionTableEntry hdr : sectionTableEntries ) 
        {
            if ( getSectionName( hdr ).equals( name ) ) {
                return hdr;
            }
        }
        return null;
    }
    
    int getPayloadOffset(ProgramTableEntry entry,ElfWriter writer) {
        if ( entry == textSegment ) 
        {
            return writer.getMarker( MarkerName.TEXT_START ).offset;
        }
        throw new RuntimeException("Internal error, don't know how to handle program header "+entry);
    }
    
    int getPayloadSize(ProgramTableEntry entry,ElfWriter writer) 
    {
        if ( entry == textSegment ) 
        {
            final int start = writer.getMarker( MarkerName.TEXT_START ).offset;
            final int end = writer.getMarker( MarkerName.TEXT_END ).offset;
            return end - start;
        }
        throw new RuntimeException("Internal error, don't know how to handle program header "+entry);  
    }
    
    int getSectionDataOffset(SectionTableEntry section ,ElfWriter writer) 
    {
        if ( section == sectionNamesEntry ) {
            return writer.getMarker( MarkerName.SECTION_NAME_STRING_TABLE_START ).offset;
        }
        if ( section.sh_type == SectionType.SHT_NULL ) {
            return 0;
        }
        if ( section == textSegmentEntry ) {
            return getPayloadOffset( textSegment , writer );
        }
        if ( section == dataSegmentEntry ) {
            return writer.getMarker( MarkerName.DATA_START ).offset;
        }        
        if ( section == symTab ) {
            return writer.getMarker( MarkerName.SECTION_SYMBOL_TABLE_START ).offset;
        }
        if ( section == symNamesEntry) {
            return writer.getMarker( MarkerName.SECTION_SYMBOL_NAMES_TABLE_START ).offset;
        }     
        if ( section.hasType( SectionType.SHT_RELA ) ) 
        {
            final MarkerName marker;
            final Segment s = getSegmentForRelocation( section );
            switch( s ) {
                case FLASH:
                    marker = MarkerName.TEXT_RELOCATIONS_START;
                    break;
                case SRAM:
                    marker = MarkerName.DATA_RELOCATIONS_START;
                    break;
                default:
                    throw new RuntimeException("Unhandled segment: "+s);
            }
            return writer.getMarker( marker ).offset;
        }
        throw new RuntimeException("Internal error, don't know how to handle section '"+getSectionName(section)+"'");
    }
    
    private int getDistance(ElfWriter writer,MarkerName start,MarkerName end) 
    {
        final int startAdr = writer.getMarker( start ).offset;
        final int endAdr = writer.getMarker( end ).offset;
        return endAdr-startAdr;
    }
    
    int getSectionDataSize(SectionTableEntry section,ElfWriter writer) 
    {
        if ( section == null ) {
            throw new NoSuchElementException("Unknown section '"+getSectionName(section)+"'");
        }
        if ( section == sectionNamesEntry )
        {
            return getDistance(writer, MarkerName.SECTION_NAME_STRING_TABLE_START , MarkerName.SECTION_NAME_STRING_TABLE_END );
        }
        if ( section == textSegmentEntry ) {
            return getPayloadSize( textSegment , writer );
        }        
        if ( section == dataSegmentEntry ) {
            return getDistance(writer,MarkerName.DATA_START , MarkerName.DATA_END );
        }        
        if ( section == symTab ) 
        {
            return getDistance(writer, MarkerName.SECTION_SYMBOL_TABLE_START , MarkerName.SECTION_SYMBOL_TABLE_END );
        }
        if ( section == symNamesEntry ) 
        {
            return getDistance(writer, MarkerName.SECTION_SYMBOL_NAMES_TABLE_START , MarkerName.SECTION_SYMBOL_NAMES_TABLE_END );
        }        
        if ( section.sh_type == SectionType.SHT_NULL ) {
            return 0;
        }        
        if ( section.hasType( SectionType.SHT_RELA ) ) 
        {
            final Segment segment = getSegmentForRelocation( section );
            final List<Relocation> relocs = objectCodeWriter.getRelocations( segment );
            if ( relocs.isEmpty() ) {
                throw new RuntimeException("Internal error, called for segment with no relocations ?");
            }
            return relocs.size() * RELOCATION_SECTION_ENTRY_SIZE;
        }
        throw new RuntimeException("Internal error, don't know how to handle section '"+getSectionName(section)+"'");
    }
    
    int getProgramHeaderCount() {
        return programHeaders.size();
    }
    
    int getSectionCount() {
        return sectionTableEntries.size();
    }
    
    SectionHeaderInfoAndLink getSectionHeaderInfoAndLink(SectionTableEntry entry) 
    {
        Validate.notNull(entry, "entry must not be NULL");
        final int link;
        final int info;
        if ( entry == symTab ) 
        {
            link = getTableIndex( symNamesEntry );
            info = symbolTable.getLastLocalSymbolIndex()+1;   
        } 
        else if ( entry.hasType( SectionType.SHT_RELA ) ) 
        {
            link = getTableIndex( symTab );
            
            final Segment segment = getSegmentForRelocation( entry );
            if ( segment == Segment.FLASH ) {
                info = getTableIndex( textSegmentEntry );
            } else if ( segment == Segment.SRAM ) {
                info = getTableIndex( dataSegmentEntry );
            } else {
                throw new RuntimeException("Internal error,unhandled segment: "+segment);
            }
        } else {
            link = entry.sh_link;
            info = entry.sh_info;
        }        
        return new SectionHeaderInfoAndLink(info,link);
    }
    
    Segment getSegmentForRelocation(SectionTableEntry entry) 
    {
        if ( ! entry.hasType( SectionType.SHT_RELA ) ) {
            throw new IllegalArgumentException("Method must only be called for relocation section table entries");
        }
        return relocationEntries.entrySet().stream().filter( e -> e.getValue() == entry ).map( e -> e.getKey() ).findFirst().get();
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
        textSegmentEntry.sh_addralign = 0; // <<< power-of-two = 2^0 = 1
        textSegmentEntry.sh_name = sectionNames.add( SpecialSection.TEXT.name );

        sectionTableEntries.add( textSegmentEntry );
        
        if ( type == OutputFormat.ELF_RELOCATABLE ) 
        {
            for ( Segment s : Segment.values() ) 
            {
                if ( objWriter.getRelocations( s ).isEmpty() ) 
                {
                    continue;
                }
                final SectionTableEntry relocationEntry  = new SectionTableEntry(this);
                relocationEntries.put( s , relocationEntry );
                
                relocationEntry.setType( SpecialSection.RELANAME );
                relocationEntry.sh_addralign = 2; // <<< power-of-two = 2^2 = 4
                final String segName;
                switch ( s ) {
                    case FLASH:
                        segName = ".rela.text";
                        break;
                    case SRAM:
                        segName = ".rela.data";
                        break;
                    default:
                        throw new RuntimeException("Internal error, relocation entries for segment "+s+" ?");
                }
                relocationEntry.sh_name = sectionNames.add( segName );
                relocationEntry.sh_entsize = RELOCATION_SECTION_ENTRY_SIZE;
                // TODO: readelf output shows flag 'INFO' but I couldn't find the binary value for this flag (not in regular ELF spec)
                // relocationEntry.sh_flags.add( Flag.INFO );
                sectionTableEntries.add( relocationEntry );
            }
        }
        
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
        symTab.sh_entsize = ElfSymbolTable.SYMBOL_TABLE_ENTRY_SIZE;
        
        sectionTableEntries.add( symTab );
        
        // symbol name table
        symNamesEntry.setType( SpecialSection.STRTAB );
        symNamesEntry.sh_addralign = 1;
        symNamesEntry.sh_name = sectionNames.add( SpecialSection.STRTAB.name );
        sectionTableEntries.add( symNamesEntry );
        
        // setup program headers
        if ( type == OutputFormat.ELF_EXECUTABLE ) {        
            textSegment = new ProgramTableEntry(this);
            textSegment.p_type = SegmentType.PT_LOAD;
            textSegment.addFlags(SegmentFlag.PF_X , SegmentFlag.PF_R );
            textSegment.p_align = 2;
            
            programHeaders.add( textSegment );
        }
    }
}