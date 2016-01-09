package de.codesourcery.javr.assembler.util;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;

import org.apache.commons.lang3.Validate;

import de.codesourcery.javr.assembler.ResourceFactory;
import de.codesourcery.javr.assembler.Segment;

public class FileResourceFactory implements ResourceFactory {

    private final String fileBaseName; 
    private final String pwd;
    private final File pwdFile;
    
    private FileResourceFactory(File parentPath , String fileBaseName) 
    {
        Validate.notNull(parentPath, "parentPath must not be NULL or blank");
        Validate.notBlank(fileBaseName, "fileBaseName must not be NULL or blank");
        
        String path = parentPath.getAbsolutePath();
        while ( path.length() > File.pathSeparator.length() && path.endsWith( File.pathSeparator ) ) {
            path = path.substring(0 , path.length()- File.pathSeparator.length() );
        }
        this.fileBaseName = fileBaseName;
        this.pwd = path;
        this.pwdFile = new File( path );
    }
    
    public static ResourceFactory createInstance(String fileBaseName) 
    {
        return createInstance( new File( Paths.get(".").toAbsolutePath().normalize().toString()) , fileBaseName  );
    }
    
    public static ResourceFactory createInstance(File parentPath,String fileBaseName) 
    {
        return new FileResourceFactory( parentPath , fileBaseName );
    }    

    @Override
    public Resource resolveResource(Resource parent, String child) throws IOException {
        return new FileResource( new File(pwdFile,child) ,  Resource.ENCODING_UTF );
    }
    
    @Override
    public Resource getResource(Segment segment) throws IOException 
    {
        final String suffix;
        switch( segment ) 
        {
            case EEPROM: suffix = ".eeprom.hex"; break;
            case FLASH:  suffix = ".flash.hex"; break;
            case SRAM:   suffix = ".sram.hex"; break;
            default: throw new RuntimeException("Unhandled segment type: "+segment);
        }
        return new FileResource( new File( pwd + File.separatorChar + fileBaseName + suffix )  , Resource.ENCODING_UTF );
    }
}