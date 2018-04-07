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
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.apache.commons.lang3.Validate;

import de.codesourcery.javr.assembler.elf.ElfWriter.Endianess;

public class SectionTableEntry {

    public static final int SIZE_IN_BYTES = 40;
    
    public enum Flag 
    {
        SHF_WRITE(0x1),
        SHF_ALLOC( 0x2 ),
        SHF_EXECINSTR(0x4),
        SHF_MASKPROC( 0xf0000000 );

        public final int value;

        private Flag(int value) {
            this.value = value;
        }
    }
    
    public static final class SectionHeaderInfoAndLink 
    {
        public int sh_info;
        public int sh_link;
        
        public SectionHeaderInfoAndLink(int sh_info, int sh_link) {
            this.sh_info = sh_info;
            this.sh_link = sh_link;
        }
    }

    public enum SectionType 
    {
        SHT_NULL(0),
        SHT_PROGBITS(1),
        SHT_SYMTAB(2),
        SHT_STRTAB(3),
        SHT_RELA(4),
        SHT_HASH(5),
        SHT_DYNAMIC(6),
        SHT_NOTE(7),
        SHT_NOBITS(8),
        SHT_REL(9),
        SHT_SHLIB(10),
        SHT_DYNSYM(11),
        SHT_LOPROC(0x70000000),
        SHT_HIPROC(0x7fffffff),
        SHT_LOUSER(0x80000000),
        SHT_HIUSER( 0xffffffff);

        public int value;

        private SectionType(int value) {
            this.value = value;
        }
    }

    public final ElfFile elfFile;
    
    public int sh_name;
    public SectionType sh_type;
    public Set<Flag> sh_flags = new HashSet<>();
    public int sh_addr;
    public int sh_offset;
    public int sh_size;
    public int sh_link;
    public int sh_info;
    public int sh_addralign;
    public int sh_entsize;
    
    @Override
    public String toString()
    {
        return sh_type == null ? "<type is NULL?>" : sh_type.toString();
    }

    public void write(ElfWriter writer) throws IOException 
    {
        /*
        typedef struct {
        Elf32_Word sh_name; // 0
        Elf32_Word sh_type; // 4
        Elf32_Word sh_flags; // 8
        Elf32_Addr sh_addr;  // 12 , if section appears in program image , this is its starting address
        Elf32_Off  sh_offset; // 16 offset of this section's actual data
        Elf32_Word sh_size; // 20 // size of the section in bytes 
        Elf32_Word sh_link; // 24
        Elf32_Word sh_info; // 28
        Elf32_Word sh_addralign; // 32
        Elf32_Word sh_entsize; // 36
        }Elf32_Shdr; ==> 24 bytes
             */
        
        writer.writeWord( sh_name , Endianess.LITTLE );
        writer.writeWord( sh_type.value , Endianess.LITTLE );
        writer.writeWord( sh_flags.stream().mapToInt( f -> f.value ).sum() , Endianess.LITTLE );
        writer.writeWord(sh_addr , Endianess.LITTLE ); // load address
        
        writer.deferredWriteWord( (w,file) -> 
        file.getSectionDataOffset(this, w) , Endianess.LITTLE );  // offset of this section's actual data
        
        writer.deferredWriteWord( (w,file) -> 
        file.getSectionDataSize(this, w) , Endianess.LITTLE );  //  size of the section data in bytes 

        final SectionHeaderInfoAndLink infoAndLink = elfFile.getSectionHeaderInfoAndLink( this );
            
        writer.writeWord( infoAndLink.sh_link , Endianess.LITTLE ); // sh_link
        writer.writeWord( infoAndLink.sh_info , Endianess.LITTLE ); // sh_info
        writer.writeWord(sh_addralign , Endianess.LITTLE );
        writer.writeWord(sh_entsize , Endianess.LITTLE );
    }
    
    public enum SpecialSection 
    {
        BSS("bss", SectionType.SHT_NOBITS, Flag.SHF_ALLOC , Flag.SHF_WRITE),
        COMMENT("comment", SectionType.SHT_PROGBITS ), 
        DATA("data", SectionType.SHT_PROGBITS , Flag.SHF_ALLOC , Flag.SHF_WRITE),
        DEBUG("debug", SectionType.SHT_PROGBITS ),
        DYNAMIC("dynamic", SectionType.SHT_DYNAMIC),
        DYNSTR("dynstr", SectionType.SHT_STRTAB, Flag.SHF_ALLOC), 
        DYNSYM("dynsym", SectionType.SHT_DYNSYM , Flag.SHF_ALLOC),
        FINI("fini",  SectionType.SHT_PROGBITS, Flag.SHF_ALLOC , Flag.SHF_EXECINSTR),
        GOT("got", SectionType.SHT_PROGBITS),
        HASH("hash", SectionType.SHT_HASH, Flag.SHF_ALLOC),
        INIT("init", SectionType.SHT_PROGBITS, Flag.SHF_ALLOC , Flag.SHF_EXECINSTR),
        INTERP("interp",  SectionType.SHT_PROGBITS),
        LINE("line", SectionType.SHT_PROGBITS ),
        NOTE("note", SectionType.SHT_NOTE ),
        PLT("plt", SectionType.SHT_PROGBITS),
        RELNAME("relname", SectionType.SHT_REL),
        RELANAME("rela.text", SectionType.SHT_RELA),
        RODATA("rodata", SectionType.SHT_PROGBITS, Flag.SHF_ALLOC),
        SHSTRTAB("shstrtab", SectionType.SHT_STRTAB  ),
        STRTAB("strtab",  SectionType.SHT_STRTAB),
        SYMTAB("symtab",  SectionType.SHT_SYMTAB),
        TEXT("text", SectionType.SHT_PROGBITS, Flag.SHF_ALLOC , Flag.SHF_EXECINSTR);
        
        public final String name;
        public final SectionType type;
        public final Set<Flag> flags = new HashSet<>();
        
        private SpecialSection(String name,SectionType type,Flag...flags) 
        {
            this.name= "."+name;
            this.type = type;
            if ( flags != null ) {
                this.flags.addAll( Arrays.asList(flags) );
            }
        }
    }

    public void setType(SpecialSection section) 
    {
        this.sh_type = section.type;
        this.sh_flags.clear();
        this.sh_flags.addAll(section.flags);
    }

    public SectionTableEntry(ElfFile elfFile) {
        this.elfFile = elfFile;
    }
    
    public boolean hasType( SectionType t) 
    {
        Validate.notNull(t, "t must not be NULL");
        return this.sh_type.equals( t );
    }
}