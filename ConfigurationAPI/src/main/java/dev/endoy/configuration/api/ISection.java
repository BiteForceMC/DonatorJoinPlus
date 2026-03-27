package dev.endoy.configuration.api;

import java.util.List;
import java.util.Set;

public interface ISection
{

    boolean exists( String path );

    boolean isList( String path );

    boolean isSection( String path );

    ISection getSection( String path );

    List<ISection> getSectionList( String path );

    String getString( String path );

    default String getString( String path, String defaultValue )
    {
        if ( !exists( path ) )
        {
            return defaultValue;
        }
        return getString( path );
    }

    List<String> getStringList( String path );

    List<?> getList( String path );

    boolean getBoolean( String path );

    default boolean getBoolean( String path, boolean defaultValue )
    {
        if ( !exists( path ) )
        {
            return defaultValue;
        }
        return getBoolean( path );
    }

    int getInteger( String path );

    long getLong( String path );

    <T> T get( String path );

    void set( String path, Object value );

    ISection createSection( String path );

    Set<String> getKeys();

    Set<String> getKeys( boolean deep );
}
