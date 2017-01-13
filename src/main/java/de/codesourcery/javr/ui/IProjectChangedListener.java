package de.codesourcery.javr.ui;

import de.codesourcery.javr.assembler.CompilationUnit;

public interface IProjectChangedListener 
{
	public void unitAdded(Project project,CompilationUnit newUnit);
	
	public void unitRemoved(Project project,CompilationUnit newUnit);
}
