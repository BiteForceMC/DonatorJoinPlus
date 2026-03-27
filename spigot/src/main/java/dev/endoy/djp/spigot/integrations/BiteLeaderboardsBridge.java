package dev.endoy.djp.spigot.integrations;

import dev.endoy.djp.spigot.DonatorJoinPlus;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public final class BiteLeaderboardsBridge
{

    private static final Map<UUID, CachedRankInfo> CACHE = new ConcurrentHashMap<>();
    private static final Map<UUID, CompletableFuture<Void>> IN_FLIGHT = new ConcurrentHashMap<>();
    private static final long DEFAULT_LOOKUP_TIMEOUT_MILLIS = 250L;
    private static final String DEFAULT_CATEGORY = "time_played";
    private static final String DEFAULT_PLUGIN = "BiteLeaderboards";

    private BiteLeaderboardsBridge()
    {
    }

    public static void prefetch( final Player player )
    {
        if ( player == null || !isEnabled() )
        {
            return;
        }
        triggerLoad( player.getUniqueId() );
    }

    public static String getPlaytime( final Player player )
    {
        return resolve( player ).formattedScore;
    }

    public static String getPlaytimeRank( final Player player )
    {
        final CachedRankInfo info = resolve( player );

        if ( info.rank < 1 )
        {
            return "-";
        }
        return "#" + info.rank;
    }

    public static String getPlaytimeTotalTracked( final Player player )
    {
        final CachedRankInfo info = resolve( player );

        if ( info.totalTracked < 1 )
        {
            return "-";
        }
        return formatNumber( info.totalTracked );
    }

    private static CachedRankInfo resolve( final Player player )
    {
        if ( player == null || !isEnabled() )
        {
            return fallbackInfo();
        }

        final UUID uuid = player.getUniqueId();
        final CachedRankInfo cached = CACHE.get( uuid );

        if ( cached != null && System.currentTimeMillis() - cached.loadedAt <= getCacheMillis() )
        {
            return cached;
        }

        if ( cached == null )
        {
            final CachedRankInfo immediate = loadFromPlugin( uuid );
            if ( immediate != null )
            {
                CACHE.put( uuid, immediate );
                return immediate;
            }
        }

        triggerLoad( uuid );

        if ( cached != null )
        {
            return cached;
        }
        return fallbackInfo();
    }

    private static void triggerLoad( final UUID uuid )
    {
        IN_FLIGHT.computeIfAbsent( uuid, ignored -> CompletableFuture
                .supplyAsync( () -> loadFromPlugin( uuid ) )
                .thenAccept( info ->
                {
                    if ( info != null )
                    {
                        CACHE.put( uuid, info );
                    }
                } )
                .exceptionally( throwable ->
                {
                    DonatorJoinPlus.i().debug( "Could not load BiteLeaderboards data for " + uuid + ": " + throwable.getMessage() );
                    return null;
                } )
                .whenComplete( ( ignoredResult, ignoredThrowable ) -> IN_FLIGHT.remove( uuid ) ) );
    }

    private static CachedRankInfo loadFromPlugin( final UUID uuid )
    {
        final Plugin plugin = resolvePlugin();

        if ( plugin == null )
        {
            return null;
        }

        try
        {
            final Object service = plugin.getClass().getMethod( "getLeaderboardService" ).invoke( plugin );
            final Method getRankInfoMethod = service.getClass()
                    .getMethod( "getRankInfoForPlayerAsync", String.class, UUID.class );
            final Object futureObject = getRankInfoMethod.invoke( service, getCategoryId(), uuid );

            if ( !( futureObject instanceof CompletableFuture<?> future ) )
            {
                return null;
            }

            final Object rankInfo = future.get( getLookupTimeoutMillis(), TimeUnit.MILLISECONDS );
            if ( rankInfo == null )
            {
                return null;
            }

            final int rank = ( (Number) rankInfo.getClass().getMethod( "getRank" ).invoke( rankInfo ) ).intValue();
            final int totalTracked = ( (Number) rankInfo.getClass().getMethod( "getTotalTracked" ).invoke( rankInfo ) ).intValue();
            final long score = ( (Number) rankInfo.getClass().getMethod( "getScore" ).invoke( rankInfo ) ).longValue();

            return new CachedRankInfo(
                    System.currentTimeMillis(),
                    rank,
                    totalTracked,
                    score,
                    formatCategoryScore( plugin, getCategoryId(), score )
            );
        }
        catch ( Exception e )
        {
            DonatorJoinPlus.i().debug( "BiteLeaderboards reflection lookup failed: " + e.getMessage() );
            return null;
        }
    }

    private static String formatCategoryScore( final Plugin plugin, final String categoryId, final long score )
    {
        try
        {
            final ClassLoader classLoader = plugin.getClass().getClassLoader();
            final Class<?> formatterClass = Class.forName(
                    "biteforce.biteleaderboard.util.ScoreFormatUtil",
                    true,
                    classLoader
            );
            final Method formatter = formatterClass.getMethod(
                    "formatCategoryScore",
                    String.class,
                    long.class
            );

            return formatter.invoke( null, categoryId, score ).toString();
        }
        catch ( Exception ignored )
        {
            if ( DEFAULT_CATEGORY.equalsIgnoreCase( categoryId ) || "playtime".equalsIgnoreCase( categoryId ) )
            {
                return formatDurationSeconds( score );
            }
            return formatNumber( score );
        }
    }

    private static CachedRankInfo fallbackInfo()
    {
        return new CachedRankInfo(
                System.currentTimeMillis(),
                -1,
                -1,
                0L,
                getFallbackText()
        );
    }

    private static Plugin resolvePlugin()
    {
        final String pluginName = getPluginName();
        final Plugin plugin = DonatorJoinPlus.i().getServer().getPluginManager().getPlugin( pluginName );

        if ( plugin != null && plugin.isEnabled() )
        {
            return plugin;
        }

        final Plugin fallback = DonatorJoinPlus.i().getServer().getPluginManager().getPlugin( "BiteLeaderboard" );
        if ( fallback != null && fallback.isEnabled() )
        {
            return fallback;
        }

        return null;
    }

    private static String getPluginName()
    {
        final String path = "integrations.biteleaderboards.plugin-name";

        if ( DonatorJoinPlus.i().getConfiguration().exists( path ) )
        {
            return DonatorJoinPlus.i().getConfiguration().getString( path );
        }
        return DEFAULT_PLUGIN;
    }

    private static String getCategoryId()
    {
        final String path = "integrations.biteleaderboards.playtime-category";

        if ( DonatorJoinPlus.i().getConfiguration().exists( path ) )
        {
            return DonatorJoinPlus.i().getConfiguration().getString( path );
        }
        return DEFAULT_CATEGORY;
    }

    private static boolean isEnabled()
    {
        return DonatorJoinPlus.i().getConfiguration().getBoolean( "integrations.biteleaderboards.enabled", true );
    }

    private static long getCacheMillis()
    {
        final String path = "integrations.biteleaderboards.cache-seconds";
        long cacheSeconds = 60L;

        if ( DonatorJoinPlus.i().getConfiguration().exists( path ) )
        {
            cacheSeconds = DonatorJoinPlus.i().getConfiguration().getLong( path );
        }

        return Math.max(
                1L,
                cacheSeconds
        ) * 1000L;
    }

    private static long getLookupTimeoutMillis()
    {
        final String path = "integrations.biteleaderboards.lookup-timeout-ms";
        long timeoutMillis = DEFAULT_LOOKUP_TIMEOUT_MILLIS;

        if ( DonatorJoinPlus.i().getConfiguration().exists( path ) )
        {
            timeoutMillis = DonatorJoinPlus.i().getConfiguration().getLong( path );
        }

        return Math.max(
                25L,
                timeoutMillis
        );
    }

    private static String getFallbackText()
    {
        final String path = "integrations.biteleaderboards.fallback-text";

        if ( DonatorJoinPlus.i().getConfiguration().exists( path ) )
        {
            return DonatorJoinPlus.i().getConfiguration().getString( path );
        }
        return "N/A";
    }

    private static String formatDurationSeconds( final long rawSeconds )
    {
        final Duration duration = Duration.ofSeconds( Math.max( 0L, rawSeconds ) );

        final long days = duration.toDays();
        final long hours = duration.minusDays( days ).toHours();
        final long minutes = duration.minusDays( days ).minusHours( hours ).toMinutes();

        if ( days > 0 )
        {
            return String.format( Locale.US, "%dd %dh %dm", days, hours, minutes );
        }

        if ( hours > 0 )
        {
            return String.format( Locale.US, "%dh %dm", hours, minutes );
        }
        return String.format( Locale.US, "%dm", minutes );
    }

    private static String formatNumber( final long value )
    {
        return String.format( Locale.US, "%,d", Math.max( 0L, value ) );
    }

    private static final class CachedRankInfo
    {
        private final long loadedAt;
        private final int rank;
        private final int totalTracked;
        @SuppressWarnings( "unused" )
        private final long score;
        private final String formattedScore;

        private CachedRankInfo( final long loadedAt,
                                final int rank,
                                final int totalTracked,
                                final long score,
                                final String formattedScore )
        {
            this.loadedAt = loadedAt;
            this.rank = rank;
            this.totalTracked = totalTracked;
            this.score = score;
            this.formattedScore = formattedScore;
        }
    }
}
