package de.codesourcery.javr.assembler.elf;

import java.util.AbstractList;
import java.util.ArrayList;
import java.util.List;

public class Relocations extends AbstractList<Relocation>
{
    private final List<Relocation> data = new ArrayList<>();
    
    @Override
    public boolean add(Relocation e) {
        return data.add(e);
    }
    
    @Override
    public Relocation get(int index) {
        return data.get(index);
    }

    @Override
    public int size() {
        return data.size();
    }
}