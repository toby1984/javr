package de.codesourcery.javr.assembler.elf;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
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
    
    // flag indicating whether this table contains local symbols ; if yes,
    // the second entry in this table always points to a symbol that has
    // the compilation unit's name as its name
    private boolean hasLocalSymbols;
    
    private final ElfFile file;

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

        public ElfSymbol(Symbol s) 
        {
            this.symbol = s;
            this.nameIdx = file.symbolNames.add( name( s ) );
            this.value = valueOf( s );
            this.symbolType = SymbolType.STT_FUNC; // TODO: Maybe try to distinguish between functions and objects here ???
            switch( s.getType() ) 
            {
                case ADDRESS_LABEL:
                    this.bindingType = BindingType.STB_GLOBAL;                    
                    break;
                case EQU:
                    this.bindingType = BindingType.STB_LOCAL;
                    this.sectionHeaderIndex = ElfFile.SHN_ABS; // Flag as 'needs no relocation' TODO: This is not true if the expression involved addresses....
                    this.symbolType = SymbolType.STT_NOTYPE;                    
                    break;
                default:
                    throw new IllegalArgumentException("Don't know how to generate symbol table entry for "+s);
            }
            this.size = 0; // TODO: Size is currently always set to zero.... change Symbol class so it can store the size of labelled object AND the type ( function or data structure / variable) 
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
        /*
The symbols in a symbol table are written in the following order.

    Index 0 in any symbol table is used to represent undefined symbols. This first entry in a symbol table is always completely zeroed. The symbol type is therefore STT_NOTYPE.

    If the symbol table contains any local symbols, the second entry of the symbol table is an STT_FILE symbol giving the name of the file.

    Section symbols of type STT_SECTION.

    Register symbols of type STT_REGISTER.

    Global symbols that have been reduced to local scope.

    For each input file that supplies local symbols, a STT_FILE symbol giving the name of the input file, followed by the symbols in question.

    The global symbols immediately follow the local symbols in the symbol table. The first global symbol is identified by the symbol table sh_info value. Local and global symbols are always kept separate in this manner, and cannot be mixed together.
         */
        final List<Symbol> symbols = table.getAllSymbolsUnsorted().stream().filter( s -> s.hasType(Type.ADDRESS_LABEL) || s.hasType(Type.EQU ) ).collect( Collectors.toList() );
        hasLocalSymbols = symbols.stream().anyMatch( s -> s.isLocalLabel() );
        
        if ( hasLocalSymbols ) 
        {
            ElfSymbol sym = new ElfSymbol();
            sym.bindingType = BindingType.STB_LOCAL;
            sym.nameIdx = file.symbolNames.add( "dummy.c" ); // TODO: Add the real filename here
            sym.sectionHeaderIndex = file.getTableIndex( file.textSegmentEntry );
            sym.symbolType = SymbolType.STT_FILE;
            this.symbols.add( sym );
        }
        
        // add global symbols before local symbols
        symbols.stream().filter( sym -> sym.hasType( Symbol.Type.EQU ) || sym.isGlobalLabel() ).forEach( s ->
        {
            this.symbols.add( new ElfSymbol( s ) );
        });
        
        // add global symbols before local symbols
        symbols.stream().filter( sym -> sym.isLocalLabel() ).forEach( s ->
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
        
        final int startIdx = hasLocalSymbols ? 2 : 1; // if we have local symbols , symbol table entry #1 is occupied by STT_FILE symbol
        for ( int i = startIdx ; i < symbols.size() ; i++ ) {
            
            final ElfSymbol symbol = symbols.get(i);
            final Segment segment = symbol.symbol.getSegment();
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
}