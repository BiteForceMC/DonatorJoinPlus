package dev.endoy.configuration.api;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class SimpleConfiguration implements IConfiguration
{

    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .create();
    private static final Type MAP_TYPE = new TypeToken<LinkedHashMap<String, Object>>()
    {
    }.getType();

    private final FileStorageType type;
    private final File file;
    private Map<String, Object> data;

    SimpleConfiguration( final FileStorageType type, final File file, final Map<String, Object> initialData )
    {
        this.type = type == null ? FileStorageType.YAML : type;
        this.file = file;
        this.data = initialData == null ? new LinkedHashMap<>() : initialData;
    }

    static IConfiguration load( final FileStorageType type, final File file )
    {
        if ( file == null )
        {
            return new SimpleConfiguration( type, null, new LinkedHashMap<>() );
        }
        try
        {
            final File parent = file.getParentFile();
            if ( parent != null && !parent.exists() )
            {
                parent.mkdirs();
            }
            if ( !file.exists() )
            {
                file.createNewFile();
            }
        }
        catch ( IOException ignored )
        {
            // Keep behavior soft-fail.
        }

        final Map<String, Object> values = readMapFromFile( type, file );
        return new SimpleConfiguration( type, file, values );
    }

    static IConfiguration load( final FileStorageType type, final InputStream inputStream )
    {
        if ( inputStream == null )
        {
            return new SimpleConfiguration( type, null, new LinkedHashMap<>() );
        }

        try ( Reader reader = new InputStreamReader( inputStream, StandardCharsets.UTF_8 ) )
        {
            final Map<String, Object> values = readMap( type, reader );
            return new SimpleConfiguration( type, null, values );
        }
        catch ( IOException ignored )
        {
            return new SimpleConfiguration( type, null, new LinkedHashMap<>() );
        }
    }

    @Override
    public void save() throws IOException
    {
        if ( file == null )
        {
            return;
        }

        final File parent = file.getParentFile();
        if ( parent != null && !parent.exists() )
        {
            parent.mkdirs();
        }

        if ( type == FileStorageType.JSON )
        {
            try ( Writer writer = new FileWriter( file, StandardCharsets.UTF_8 ) )
            {
                GSON.toJson( data, writer );
            }
            return;
        }

        final DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle( DumperOptions.FlowStyle.BLOCK );
        options.setPrettyFlow( true );
        options.setIndent( 2 );

        final Yaml yaml = new Yaml( options );
        try ( Writer writer = new FileWriter( file, StandardCharsets.UTF_8 ) )
        {
            yaml.dump( data, writer );
        }
    }

    @Override
    public void reload() throws IOException
    {
        if ( file == null )
        {
            return;
        }
        this.data = readMapFromFile( type, file );
    }

    @Override
    public void copyDefaults( final IConfiguration defaults ) throws IOException
    {
        if ( defaults == null )
        {
            return;
        }

        for ( String key : defaults.getKeys( true ) )
        {
            if ( !exists( key ) )
            {
                set( key, deepCopy( defaults.get( key ) ) );
            }
        }

        if ( file != null )
        {
            save();
        }
    }

    @Override
    public boolean exists( final String path )
    {
        return resolvePath( data, path ) != null;
    }

    @Override
    public boolean isList( final String path )
    {
        return resolvePath( data, path ) instanceof List<?>;
    }

    @Override
    public boolean isSection( final String path )
    {
        return resolvePath( data, path ) instanceof Map<?, ?>;
    }

    @Override
    public ISection getSection( final String path )
    {
        return new SimpleSection( data, path );
    }

    @Override
    public List<ISection> getSectionList( final String path )
    {
        final Object value = resolvePath( data, path );
        if ( !( value instanceof List<?> list ) )
        {
            return Collections.emptyList();
        }

        final List<ISection> sections = new ArrayList<>();
        for ( Object entry : list )
        {
            if ( entry instanceof Map<?, ?> map )
            {
                sections.add( new SimpleSection( castMap( map ), "" ) );
            }
        }
        return sections;
    }

    @Override
    public String getString( final String path )
    {
        final Object value = resolvePath( data, path );
        return value == null ? "" : String.valueOf( value );
    }

    @Override
    public List<String> getStringList( final String path )
    {
        final Object value = resolvePath( data, path );
        if ( !( value instanceof List<?> list ) )
        {
            return Collections.emptyList();
        }

        final List<String> output = new ArrayList<>();
        for ( Object entry : list )
        {
            if ( entry != null )
            {
                output.add( String.valueOf( entry ) );
            }
        }
        return output;
    }

    @Override
    public List<?> getList( final String path )
    {
        final Object value = resolvePath( data, path );
        if ( value instanceof List<?> list )
        {
            return list;
        }
        return Collections.emptyList();
    }

    @Override
    public boolean getBoolean( final String path )
    {
        final Object value = resolvePath( data, path );
        if ( value instanceof Boolean b )
        {
            return b;
        }
        if ( value instanceof Number number )
        {
            return number.intValue() != 0;
        }
        if ( value instanceof String stringValue )
        {
            return Boolean.parseBoolean( stringValue );
        }
        return false;
    }

    @Override
    public int getInteger( final String path )
    {
        final Object value = resolvePath( data, path );
        if ( value instanceof Number number )
        {
            return number.intValue();
        }
        if ( value instanceof String stringValue )
        {
            try
            {
                return Integer.parseInt( stringValue );
            }
            catch ( NumberFormatException ignored )
            {
                return 0;
            }
        }
        return 0;
    }

    @Override
    public long getLong( final String path )
    {
        final Object value = resolvePath( data, path );
        if ( value instanceof Number number )
        {
            return number.longValue();
        }
        if ( value instanceof String stringValue )
        {
            try
            {
                return Long.parseLong( stringValue );
            }
            catch ( NumberFormatException ignored )
            {
                return 0L;
            }
        }
        return 0L;
    }

    @Override
    @SuppressWarnings( "unchecked" )
    public <T> T get( final String path )
    {
        return (T) resolvePath( data, path );
    }

    @Override
    public void set( final String path, final Object value )
    {
        setPath( data, path, deepCopy( value ) );
    }

    @Override
    public ISection createSection( final String path )
    {
        Object value = resolvePath( data, path );
        if ( !( value instanceof Map<?, ?> ) )
        {
            setPath( data, path, new LinkedHashMap<String, Object>() );
        }
        return getSection( path );
    }

    @Override
    public Set<String> getKeys()
    {
        return getKeys( false );
    }

    @Override
    public Set<String> getKeys( final boolean deep )
    {
        return collectKeys( getCurrentSectionMap(), deep );
    }

    private Map<String, Object> getCurrentSectionMap()
    {
        return data;
    }

    private static Map<String, Object> readMapFromFile( final FileStorageType type, final File file )
    {
        if ( file == null || !file.exists() )
        {
            return new LinkedHashMap<>();
        }

        try ( Reader reader = Files.newBufferedReader( file.toPath(), StandardCharsets.UTF_8 ) )
        {
            return readMap( type, reader );
        }
        catch ( IOException ignored )
        {
            return new LinkedHashMap<>();
        }
    }

    private static Map<String, Object> readMap( final FileStorageType type, final Reader reader )
    {
        if ( type == FileStorageType.JSON )
        {
            final Map<String, Object> json = GSON.fromJson( reader, MAP_TYPE );
            return json == null ? new LinkedHashMap<>() : castMap( json );
        }

        final Yaml yaml = new Yaml();
        final Object loaded = yaml.load( reader );
        if ( loaded instanceof Map<?, ?> map )
        {
            return castMap( map );
        }
        return new LinkedHashMap<>();
    }

    private static Map<String, Object> castMap( final Map<?, ?> raw )
    {
        final Map<String, Object> cast = new LinkedHashMap<>();
        for ( Map.Entry<?, ?> entry : raw.entrySet() )
        {
            if ( entry.getKey() != null )
            {
                cast.put( String.valueOf( entry.getKey() ), entry.getValue() );
            }
        }
        return cast;
    }

    private static Object resolvePath( final Map<String, Object> map, final String rawPath )
    {
        final String path = sanitizePath( rawPath );
        if ( path.isEmpty() )
        {
            return map;
        }

        Object current = map;
        for ( String node : path.split( "\\." ) )
        {
            if ( !( current instanceof Map<?, ?> currentMap ) )
            {
                return null;
            }
            current = currentMap.get( node );
            if ( current == null )
            {
                return null;
            }
        }
        return current;
    }

    private static void setPath( final Map<String, Object> map, final String rawPath, final Object value )
    {
        final String path = sanitizePath( rawPath );
        if ( path.isEmpty() )
        {
            if ( value instanceof Map<?, ?> replacement )
            {
                map.clear();
                map.putAll( castMap( replacement ) );
            }
            return;
        }

        final String[] nodes = path.split( "\\." );
        Map<String, Object> current = map;

        for ( int i = 0; i < nodes.length - 1; i++ )
        {
            final String node = nodes[i];
            final Object next = current.get( node );

            if ( next instanceof Map<?, ?> nextMap )
            {
                try
                {
                    current = (Map<String, Object>) nextMap;
                }
                catch ( ClassCastException ignored )
                {
                    final Map<String, Object> replacement = castMap( nextMap );
                    current.put( node, replacement );
                    current = replacement;
                }
            }
            else
            {
                final Map<String, Object> created = new LinkedHashMap<>();
                current.put( node, created );
                current = created;
            }
        }

        current.put( nodes[nodes.length - 1], value );
    }

    private static Set<String> collectKeys( final Map<String, Object> source, final boolean deep )
    {
        if ( source == null || source.isEmpty() )
        {
            return Collections.emptySet();
        }

        final Set<String> keys = new LinkedHashSet<>();
        if ( !deep )
        {
            keys.addAll( source.keySet() );
            return keys;
        }

        for ( Map.Entry<String, Object> entry : source.entrySet() )
        {
            final String key = entry.getKey();
            keys.add( key );
            if ( entry.getValue() instanceof Map<?, ?> nestedMap )
            {
                for ( String child : collectKeys( castMap( nestedMap ), true ) )
                {
                    keys.add( key + "." + child );
                }
            }
        }
        return keys;
    }

    private static String sanitizePath( final String rawPath )
    {
        if ( rawPath == null )
        {
            return "";
        }
        return rawPath.trim();
    }

    private static Object deepCopy( final Object value )
    {
        if ( value instanceof Map<?, ?> map )
        {
            final Map<String, Object> copy = new LinkedHashMap<>();
            for ( Map.Entry<?, ?> entry : map.entrySet() )
            {
                if ( entry.getKey() != null )
                {
                    copy.put( String.valueOf( entry.getKey() ), deepCopy( entry.getValue() ) );
                }
            }
            return copy;
        }

        if ( value instanceof List<?> list )
        {
            final List<Object> copy = new ArrayList<>( list.size() );
            for ( Object item : list )
            {
                copy.add( deepCopy( item ) );
            }
            return copy;
        }

        return value;
    }

    private static final class SimpleSection implements ISection
    {
        private final Map<String, Object> source;
        private final String basePath;

        private SimpleSection( final Map<String, Object> source, final String basePath )
        {
            this.source = source == null ? new LinkedHashMap<>() : source;
            this.basePath = sanitizePath( basePath );
        }

        @Override
        public boolean exists( final String path )
        {
            return resolvePath( source, mergePath( path ) ) != null;
        }

        @Override
        public boolean isList( final String path )
        {
            return resolvePath( source, mergePath( path ) ) instanceof List<?>;
        }

        @Override
        public boolean isSection( final String path )
        {
            return resolvePath( source, mergePath( path ) ) instanceof Map<?, ?>;
        }

        @Override
        public ISection getSection( final String path )
        {
            return new SimpleSection( source, mergePath( path ) );
        }

        @Override
        public List<ISection> getSectionList( final String path )
        {
            final Object value = resolvePath( source, mergePath( path ) );
            if ( !( value instanceof List<?> list ) )
            {
                return Collections.emptyList();
            }

            final List<ISection> sections = new ArrayList<>();
            for ( Object entry : list )
            {
                if ( entry instanceof Map<?, ?> map )
                {
                    sections.add( new SimpleSection( castMap( map ), "" ) );
                }
            }
            return sections;
        }

        @Override
        public String getString( final String path )
        {
            final Object value = resolvePath( source, mergePath( path ) );
            return value == null ? "" : String.valueOf( value );
        }

        @Override
        public List<String> getStringList( final String path )
        {
            final Object value = resolvePath( source, mergePath( path ) );
            if ( !( value instanceof List<?> list ) )
            {
                return Collections.emptyList();
            }

            final List<String> output = new ArrayList<>();
            for ( Object entry : list )
            {
                if ( entry != null )
                {
                    output.add( String.valueOf( entry ) );
                }
            }
            return output;
        }

        @Override
        public List<?> getList( final String path )
        {
            final Object value = resolvePath( source, mergePath( path ) );
            if ( value instanceof List<?> list )
            {
                return list;
            }
            return Collections.emptyList();
        }

        @Override
        public boolean getBoolean( final String path )
        {
            final Object value = resolvePath( source, mergePath( path ) );
            if ( value instanceof Boolean b )
            {
                return b;
            }
            if ( value instanceof Number number )
            {
                return number.intValue() != 0;
            }
            if ( value instanceof String stringValue )
            {
                return Boolean.parseBoolean( stringValue );
            }
            return false;
        }

        @Override
        public int getInteger( final String path )
        {
            final Object value = resolvePath( source, mergePath( path ) );
            if ( value instanceof Number number )
            {
                return number.intValue();
            }
            if ( value instanceof String stringValue )
            {
                try
                {
                    return Integer.parseInt( stringValue );
                }
                catch ( NumberFormatException ignored )
                {
                    return 0;
                }
            }
            return 0;
        }

        @Override
        public long getLong( final String path )
        {
            final Object value = resolvePath( source, mergePath( path ) );
            if ( value instanceof Number number )
            {
                return number.longValue();
            }
            if ( value instanceof String stringValue )
            {
                try
                {
                    return Long.parseLong( stringValue );
                }
                catch ( NumberFormatException ignored )
                {
                    return 0L;
                }
            }
            return 0L;
        }

        @Override
        @SuppressWarnings( "unchecked" )
        public <T> T get( final String path )
        {
            return (T) resolvePath( source, mergePath( path ) );
        }

        @Override
        public void set( final String path, final Object value )
        {
            setPath( source, mergePath( path ), deepCopy( value ) );
        }

        @Override
        public ISection createSection( final String path )
        {
            final String merged = mergePath( path );
            Object value = resolvePath( source, merged );
            if ( !( value instanceof Map<?, ?> ) )
            {
                setPath( source, merged, new LinkedHashMap<String, Object>() );
            }
            return new SimpleSection( source, merged );
        }

        @Override
        public Set<String> getKeys()
        {
            return getKeys( false );
        }

        @Override
        public Set<String> getKeys( final boolean deep )
        {
            final Object current = resolvePath( source, basePath );
            if ( current instanceof Map<?, ?> map )
            {
                return collectKeys( castMap( map ), deep );
            }
            return Collections.emptySet();
        }

        private String mergePath( final String childPath )
        {
            final String cleanChild = sanitizePath( childPath );
            if ( basePath.isEmpty() )
            {
                return cleanChild;
            }
            if ( cleanChild.isEmpty() )
            {
                return basePath;
            }
            return basePath + "." + cleanChild;
        }
    }
}
