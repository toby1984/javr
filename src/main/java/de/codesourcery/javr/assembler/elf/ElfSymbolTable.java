/**
 * Copyright 2015-2018 Tobias Gierke <tobias.gierke@code-sourcery.de>
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

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;

import org.apache.commons.lang3.Validate;

import de.codesourcery.javr.assembler.Address;
import de.codesourcery.javr.assembler.Segment;
import de.codesourcery.javr.assembler.elf.ElfWriter.Endianess;
import de.codesourcery.javr.assembler.parser.Identifier;
import de.codesourcery.javr.assembler.parser.ast.IValueNode;
import de.codesourcery.javr.assembler.symbols.Symbol;
import de.codesourcery.javr.assembler.symbols.Symbol.Type;
import de.codesourcery.javr.assembler.symbols.SymbolTable;

public class ElfSymbolTable
{
    /* typedef struct 
     * {
     *  Elf32_Word    st_name;   4 bytes
     *  Elf32_Addr    st_value;  4 bytes
     *  Elf32_Word    st_size;   4 bytes
     *  unsigned char  st_info;   1 bytes
     *  unsigned char  st_other;  1 bytes
     *  Elf32_Half    st_shndx;  2 bytes
     * } Elf32_Sym ;
     */    
    public static final int SYMBOL_TABLE_ENTRY_SIZE = 16;
    
    private final List<ElfSymbol> symbols = new ArrayList<>();
    
    private final ElfFile file;
    
    public ElfSymbol textSectionSymbol;
    public ElfSymbol dataSectionSymbol;

    protected static enum BindingType 
    {
        STB_LOCAL(0),
        STB_GLOBAL(1),
        STB_WEAK(2),
        STB_LOPROC(13),
        STB_HIPROC(15);

        public int value;

        private BindingType(int value) {
            this.value = value;
        }
    }

    protected static enum SymbolType 
    {
        STT_NOTYPE(0),
        STT_OBJECT(1),
        STT_FUNC(2),
        STT_SECTION(3),
        STT_FILE(4),
        STT_LOPROC(13),
        STT_HIPROC(15);
        public int value;

        private SymbolType(int value) {
            this.value = value;
        }
    }

    protected final class ElfSymbol 
    {
        public int nameIdx;
        public int value;
        public int size;
        public SymbolType symbolType;
        public BindingType bindingType;
        public int sectionHeaderIndex;
        public final Symbol symbol;
        public Segment segment;

        public ElfSymbol(Symbol s) 
        {
            Validate.notNull(s, "symbol must not be NULL");
            Validate.notNull(s.getSegment(), "symbol must have non-null segment");
            this.symbol = s;
            this.nameIdx = file.symbolNames.add( name( s ) );
            this.value = valueOf( s );
            this.segment = s.getSegment();
            switch( s.getType() ) 
            {
                case ADDRESS_LABEL:
                    this.bindingType = BindingType.STB_GLOBAL;
                    switch( s.getObjectType() ) {
                        case FUNCTION:
                            this.symbolType = SymbolType.STT_FUNC;
                            break;
                        default:
                            this.symbolType = SymbolType.STT_OBJECT;
                    }
                    break;
                case EQU:
                    this.bindingType = BindingType.STB_LOCAL;
                    this.sectionHeaderIndex = ElfFile.SHN_ABS; // Flag as 'needs no relocation' TODO: This is not true if the expression involved addresses....
                    this.symbolType = SymbolType.STT_NOTYPE;                    
                    break;
                default:
                    throw new IllegalArgumentException("Don't know how to generate symbol table entry for "+s);
            }
            this.size = symbol.getObjectSize();
        }

        public ElfSymbol() 
        {
            /*
             * Name     |  Value    |   Note 
             * st_name  |    0      | No name
             * st_value |    0      | Zero value
             * st_size  |    0      | No size
             * st_info  |    0      | No type, local binding
             * st_other |    0      |
             * st_shndx | SHN_UNDEF | No section
             */
            this.nameIdx = 0;
            this.value = 0;
            this.size = 0;
            this.symbolType = SymbolType.STT_NOTYPE;
            this.bindingType = null;
            this.sectionHeaderIndex = ElfFile.SHN_UNDEF;
            this.symbol = null;
        }

        public int info() 
        {
            /*
            #define ELF32_ST_BIND(i) ((i)>>4)
            #define ELF32_ST_TYPE(i) ((i)&0xf)
            #define ELF32_ST_INFO(b,t) (((b)<<4)+((t)&0xf))
             */            
            final int b = bindingType == null ? 0 : bindingType.value;
            final int t = symbolType == null ? 0 : symbolType.value;
            return ((b<<4) | t & 0xf);
        }
    }

    private static String name(Symbol symbol) 
    {
        if ( ! symbol.hasType( Type.ADDRESS_LABEL ) || symbol.isGlobalLabel() ) {
            return symbol.name().value;
        }
        // local label
        return Identifier.getLocalIdentifierPart( symbol.name() ).value;
    }

    private static Object safeGetValue(Symbol symbol) 
    {
        Object result = symbol.getValue();
        if ( result == null ) {
            throw new IllegalStateException("Symbol '"+symbol+"' has no value ?");
        }
        return result;
    }

    private static int valueOf(Symbol symbol) {
        Object valueNode = safeGetValue(symbol);
        if ( valueNode instanceof IValueNode ) 
        {
            valueNode = ((IValueNode) valueNode).getValue();
        }
        if ( valueNode instanceof Number) {
            return ((Number) valueNode).intValue();
        } 
        else if ( valueNode instanceof Address) 
        {
            return ((Address) valueNode).getByteAddress();
        } 
        throw new IllegalStateException("Don't know how to get a value from "+symbol+" ( "+symbol.getValue()+" )");
    }

    public ElfSymbolTable(ElfFile file) 
    {
        Validate.notNull(file, "file must not be NULL");
        this.file = file;
        this.symbols.add( new ElfSymbol() ); // always add the index 0 entry
    }

    public void addSymbols(SymbolTable table) 
    {
        /* The symbols in a symbol table are written in the following order:
         * 
         * 1.) Index 0 in any symbol table is used to represent undefined symbols. This first entry in a symbol table is always completely zeroed. The symbol type is therefore STT_NOTYPE.
         * 
         * 2.) If the symbol table contains any local symbols, the second entry of the symbol table is an STT_FILE symbol giving the name of the file.
         * 
         * 3.) Section symbols of type STT_SECTION.
         * 
         * 4.) Register symbols of type STT_REGISTER.
         * 
         * 5.) Global symbols that have been reduced to local scope.
         * 
         * 6.) For each input file that supplies local symbols, a STT_FILE symbol giving the name of the input file, followed by the symbols in question.
         * 
         * The global symbols immediately follow the local symbols in the symbol table. 
         * The first global symbol is identified by the symbol table sh_info value. 
         * Local and global symbols are always kept separate in this manner, and cannot be mixed together.
         */
        final List<Symbol> globalSymbols = table.getAllSymbolsUnsorted().stream().filter( s -> s.hasType(Type.ADDRESS_LABEL) && ! s.isLocalLabel() ).collect( Collectors.toList() );
        
        // add local SECTION symbol for .text segment at index #1
        textSectionSymbol= new ElfSymbol();
        textSectionSymbol.bindingType = BindingType.STB_LOCAL;
        textSectionSymbol.nameIdx = 0;
        textSectionSymbol.sectionHeaderIndex = file.getTableIndex( file.textSegmentEntry );
        textSectionSymbol.symbolType = SymbolType.STT_SECTION;
        this.symbols.add( textSectionSymbol );            

        // add .data entry
        if ( file.dataSegmentEntry != null ) 
        {
            dataSectionSymbol= new ElfSymbol();
            dataSectionSymbol.bindingType = BindingType.STB_LOCAL;
            dataSectionSymbol.nameIdx = 0;
            dataSectionSymbol.sectionHeaderIndex = file.getTableIndex( file.dataSegmentEntry );
            dataSectionSymbol.symbolType = SymbolType.STT_SECTION;
            this.symbols.add( dataSectionSymbol );
        }
        
        // add global symbols
        globalSymbols.forEach( s ->
        {
            this.symbols.add( new ElfSymbol( s ) );
        });
    }
    
    public int getLastLocalSymbolIndex() 
    {
        int index = 0;
        for ( int i = 0 ; i < symbols.size() ; i++ ) 
        {
            if ( symbols.get(i).bindingType == BindingType.STB_LOCAL ) 
            {
                index = i;
            }
        }
        return index;
    }
    
    public void write(ElfWriter writer) throws IOException 
    {
        // set sectionheader into to index of .text section
        // EXCEPT for the first one which must always have value SHN_UNDEF
        final int textSectionIdx = file.getTableIndex( file.textSegmentEntry );
        
        final int dataSectionIdx;
        if ( file.dataSegmentEntry != null ) {
            dataSectionIdx = file.getTableIndex( file.dataSegmentEntry );
        } else {
            dataSectionIdx = -1;
        }
        for ( int i = 2 ; i < symbols.size() ; i++ ) {
            
            final ElfSymbol symbol = symbols.get(i);
            final Segment segment = symbol.segment;
            if ( segment != null )
            {
                if ( segment == Segment.FLASH ) {
                    symbol.sectionHeaderIndex = textSectionIdx;
                } 
                else if ( segment == Segment.SRAM ) 
                {
                    if ( file.dataSegmentEntry == null ) {
                        System.out.println("Symbol "+symbol.symbol+" is supposed to be in SRAM but SRAM section is empty?");
                    }
                    symbol.sectionHeaderIndex = dataSectionIdx;                    
                } else {
                    throw new RuntimeException("Internal error, there's no section for symbols in "+segment+". Offender:"+symbol.symbol);
                }
            }
        }

        for ( ElfSymbol s : symbols ) 
        {
            writer.writeWord( s.nameIdx , Endianess.LITTLE ); //    Elf32_Word    st_name;
            writer.writeWord( s.value , Endianess.LITTLE ); //    Elf32_Addr    st_value;
            writer.writeWord( s.size  , Endianess.LITTLE ); //    Elf32_Word    st_size;
            writer.writeByte( s.info() ); //     unsignedchar  st_info;
            writer.writeByte( 0 ); //     unsignedchar  st_other;
            writer.writeHalf( s.sectionHeaderIndex , Endianess.LITTLE ); // Elf32_Half    st_shndx; (index of section header entry this symbol belongs to) 
        }
    }
    
    /**
     * Returns the index of a given symbol in this table.
     * 
     * @param expected
     * @return
     * @throws NoSuchElementException if the symbol is not in this table. 
     */
    public int indexOf(Symbol expected) {
        
        Validate.notNull(expected, "expected must not be NULL");
        
        for ( int idx = 1 , len = symbols.size() ; idx < len ; idx++)
        {
            final ElfSymbol elfSymbol = symbols.get(idx);
            if ( elfSymbol.symbolType == SymbolType.STT_SECTION ) {
                continue;
            }
            final Symbol actual = elfSymbol.symbol;
            if ( actual == null ) {
                throw new RuntimeException("Internal error,ELF symbol at index "+idx+" has no symbol assigned ? Offender: "+elfSymbol);
            }
            if ( actual.name().equals( expected.name() ) ) {
                return idx;
            }
        }
        throw new NoSuchElementException("This symbol table does not contain symbol "+expected);
    }

    public int indexOf(ElfSymbol expected) 
    {
        Validate.notNull(expected, "expected must not be NULL");
        for ( int i = 0, len = symbols.size() ; i < len ; i++ ) 
        {
            if ( symbols.get(i) == expected ) {
                return i;
            }
        }
        throw new NoSuchElementException("This symbol table does not contain symbol "+expected);
    }
}