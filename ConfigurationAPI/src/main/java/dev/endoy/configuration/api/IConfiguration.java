package dev.endoy.configuration.api;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;

public interface IConfiguration extends ISection
{

    void save() throws IOException;

    void reload() throws IOException;

    void copyDefaults( IConfiguration defaults ) throws IOException;

    static void createDefaultFile( InputStream inputStream, File file )
    {
        if ( inputStream == null || file == null )
        {
            return;
        }
        try
        {
            final File parentFile = file.getParentFile();
            if ( parentFile != null && !parentFile.exists() )
            {
                parentFile.mkdirs();
            }
            Files.copy( inputStream, file.toPath(), StandardCopyOption.REPLACE_EXISTING );
        }
        catch ( IOException ignored )
        {
            // Ignore to preserve historical behavior.
        }
        finally
        {
            try
            {
                inputStream.close();
            }
            catch ( IOException ignored )
            {
                // Ignore.
            }
        }
    }

    static IConfiguration loadYamlConfiguration( File file )
    {
        return SimpleConfiguration.load( FileStorageType.YAML, file );
    }

    static IConfiguration loadYamlConfiguration( InputStream inputStream )
    {
        return SimpleConfiguration.load( FileStorageType.YAML, inputStream );
    }

    static IConfiguration loadConfiguration( FileStorageType type, File file )
    {
        return SimpleConfiguration.load( type, file );
    }

    static IConfiguration empty()
    {
        return new SimpleConfiguration( FileStorageType.YAML, null, new LinkedHashMap<String, Object>() );
    }
}
