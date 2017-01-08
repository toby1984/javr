package de.codesourcery.javr.assembler.elf;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.NoSuchElementException;

import de.codesourcery.javr.assembler.elf.ProgramTableEntry.SegmentFlag;
import de.codesourcery.javr.assembler.elf.ProgramTableEntry.SegmentType;
import de.codesourcery.javr.assembler.elf.SectionTableEntry.SectionType;
import de.codesourcery.javr.assembler.elf.SectionTableEntry.SpecialSection;

public class ElfFile 
{
    public static final String MARKER_HEADER_START = "fileheader_start";
    public static final String MARKER_HEADER_END = "fileheader_end";
    
    public static final String MARKER_SECTION_TABLE_START = "section_table";
    public static final String MARKER_SECTION_NAME_STRING_TABLE_START = "section_name_string_table_start";
    public static final String MARKER_SECTION_NAME_STRING_TABLE_END = "section_name_string_table_end";
    
    public static final String MARKER_PROGRAM_HEADER_TABLE_START = "program_header_start";
    public static final String MARKER_PROGRAM_HEADER_TABLE_END = "program_header_end";
    
    public static final String MARKER_TEXT_START = "text_start";
    public static final String MARKER_TEXT_END = "text_end";
    
    
    public final ElfHeader header = new ElfHeader(this);
    public final List<SectionTableEntry> sectionTableEntries = new ArrayList<>();
    
    public final List<ProgramTableEntry> programHeaders = new ArrayList<>();
    
    public final StringTable sectionNames = new StringTable();
    
    public final SectionTableEntry sectionNamesEntry;
    
    public final SectionTableEntry textSegmentEntry;
    
    
    private ProgramTableEntry textSegment;
    
    private byte[] program;
    
    public ElfFile() 
    {
        // setup sections
        final SectionTableEntry nullEntry = new SectionTableEntry(this);
        nullEntry.sh_type = SectionType.SHT_NULL;
        sectionTableEntries.add( nullEntry );
        
        sectionNamesEntry = new SectionTableEntry(this);
        sectionNamesEntry.setType( SpecialSection.SHSTRTAB );
        sectionNamesEntry.sh_name = sectionNames.add( SpecialSection.SHSTRTAB.name );
        
        // .text segment
        textSegmentEntry = new SectionTableEntry(this);
        textSegmentEntry.setType( SpecialSection.TEXT );
        textSegmentEntry.sh_name = sectionNames.add( SpecialSection.TEXT.name );

        sectionTableEntries.addAll( Arrays.asList( textSegmentEntry , sectionNamesEntry ) );
        
        // setup program headers
        textSegment = new ProgramTableEntry(this);
        textSegment.p_type = SegmentType.PT_LOAD;
        textSegment.addFlags(SegmentFlag.PF_X , SegmentFlag.PF_R );
        textSegment.p_align = 2;
        programHeaders.add( textSegment );
    }
    
    public static void main(String[] args) throws FileNotFoundException, IOException {
        
        final byte[] program = { 01,02};
        try ( FileOutputStream out = new FileOutputStream("/home/tobi/tmp/my.elf" ) ) {
            new ElfFile().write( program , out );
        }
    }
    
    public int write(byte[] program , OutputStream out) throws IOException {
        
        this.program = program;
        
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
        
        writer.createMarker( MARKER_TEXT_START );
        writer.writeBytes( program );
        writer.createMarker( MARKER_TEXT_END );

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
}