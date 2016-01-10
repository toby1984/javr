package de.codesourcery.javr.assembler.phases;

import de.codesourcery.javr.assembler.ICompilationContext;

public class PreprocessPhase implements Phase 
{
    @Override
    public String getName() {
        return "preprocess";
    }

    @Override
    public void run(ICompilationContext context) throws Exception 
    {
        new PreProcessor().preprocess(context.currentCompilationUnit().getAST() , context );        
    }
}