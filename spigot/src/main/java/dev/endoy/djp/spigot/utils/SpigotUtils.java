package dev.endoy.djp.spigot.utils;

/*
 * Created by DBSoftwares on 13 mei 2018
 * Developer: Dieter Blancke
 * Project: DonatorJoinPlus
 */

import dev.endoy.djp.spigot.DonatorJoinPlus;
import dev.endoy.djp.spigot.integrations.BiteLeaderboardsBridge;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.TextComponent;
import net.milkbowl.vault.permission.Permission;
import org.bukkit.*;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.metadata.MetadataValue;

import java.lang.reflect.Method;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SpigotUtils
{

    public static final String USER_KEY = "DJP_USER";
    private static final Pattern HEX_PATTERN = Pattern.compile( "<#([A-Fa-f0-9]{6})>" );
    private static final Pattern HEX_COLOR_PATTERN = Pattern.compile( "#?[A-Fa-f0-9]{6}" );
    private static final Pattern GRADIENT_PATTERN = Pattern.compile(
            "<gradient:([^>]+)>(.*?)</gradient>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );
    private static final Pattern LEGACY_COLOR_OR_STYLE_PATTERN = Pattern.compile(
            "(?i)[&§][0-9A-FK-OR]"
    );
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern(
            "dd MMM yyyy",
            Locale.ENGLISH
    );

    private SpigotUtils()
    {
    }

    public static void setMetaData( final Player player, final String key, final Object value )
    {
        // Removing first to be sure
        player.removeMetadata( key, DonatorJoinPlus.i() );

        // Setting meta data
        player.setMetadata( key, new FixedMetadataValue( DonatorJoinPlus.i(), value ) );
    }

    public static <T> T getMetaData( final Player player, final String key )
    {
        return (T) getMetaData( player, key, null );
    }

    public static <T> T getMetaData( final Player player, final String key, T defaultValue )
    {
        if ( player == null )
        {
            return null;
        }
        for ( MetadataValue meta : player.getMetadata( key ) )
        {
            if ( meta.getOwningPlugin().getName().equalsIgnoreCase( DonatorJoinPlus.i().getName() ) )
            {
                return (T) meta.value();
            }
        }
        return defaultValue;
    }

    public static void spawnFirework( Location loc )
    {
        Firework firework = loc.getWorld().spawn( loc, Firework.class );

        FireworkMeta fireworkmeta = firework.getFireworkMeta();
        FireworkEffect.Builder builder = FireworkEffect.builder()
                .withTrail().withFlicker()
                .withFade( Color.GREEN )
                .withColor( Color.WHITE ).withColor( Color.YELLOW )
                .withColor( Color.BLUE ).withColor( Color.FUCHSIA )
                .withColor( Color.PURPLE ).withColor( Color.MAROON )
                .withColor( Color.LIME ).withColor( Color.ORANGE )
                .with( FireworkEffect.Type.BALL_LARGE );

        fireworkmeta.addEffect( builder.build() );
        fireworkmeta.setPower( 1 );
        firework.setFireworkMeta( fireworkmeta );
    }

    public static boolean contains( String[] groups, String group )
    {
        for ( String g : groups )
        {
            if ( g.equalsIgnoreCase( group ) )
            {
                return true;
            }
        }
        return false;
    }

    @SuppressWarnings( "deprecation" )
    public static UUID getUuid( final String name )
    {
        if ( name == null )
        {
            return null;
        }
        final CompletableFuture<UUID> future = CompletableFuture.supplyAsync( () ->
        {
            OfflinePlayer player = Bukkit.getPlayer( name );

            if ( player == null )
            {
                player = Bukkit.getOfflinePlayer( name );
            }

            return player == null || !player.hasPlayedBefore() ? null : player.getUniqueId();
        } );

        try
        {
            return future.get();
        }
        catch ( InterruptedException | ExecutionException e )
        {
            return null;
        }
    }

    public static void prefetchProfilePlaceholders( final Player player )
    {
        BiteLeaderboardsBridge.prefetch( player );
    }

    public static String c( String message )
    {
        if ( message == null || message.isEmpty() )
        {
            return "";
        }

        message = applyGradientColors( message );

        Matcher matcher = HEX_PATTERN.matcher( message );
        while ( matcher.find() )
        {
            final ChatColor hexColor = ChatColor.of( matcher.group().substring( 1, matcher.group().length() - 1 ) );
            final String before = message.substring( 0, matcher.start() );
            final String after = message.substring( matcher.end() );

            message = before + hexColor + after;
            matcher = HEX_PATTERN.matcher( message );
        }
        return ChatColor.translateAlternateColorCodes( '&', message );
    }

    public static String formatString( final Player p, String str )
    {
        if ( str == null || str.isEmpty() )
        {
            return "";
        }

        prefetchProfilePlaceholders( p );

        final String playerName = p.getName();
        final String playerPrefix = getPlayerPrefix( p );
        final String playerSuffix = getPlayerSuffix( p );
        final String playerDisplay = getPlayerDisplayName( playerPrefix, playerName, playerSuffix );
        final long playtimeSeconds = Math.max( 0L, p.getStatistic( Statistic.PLAY_ONE_MINUTE ) / 20L );

        str = str.replace( "%player%", p.getName() );
        str = str.replace( "{player}", p.getName() );
        str = str.replace( "{player_name}", playerName );
        str = str.replace( "{player_prefix}", playerPrefix );
        str = str.replace( "{player_suffix}", playerSuffix );
        str = str.replace( "{player_display}", playerDisplay );
        str = str.replace( "{player_balance}", getBalance( p ) );
        str = str.replace( "{player_ping}", String.valueOf( getPing( p ) ) );
        str = str.replace( "{player_first_joined}", getFirstJoinDate( p ) );
        str = str.replace( "{player_playtime}", formatPlaytime( playtimeSeconds ) );
        str = str.replace( "{bite_playtime}", BiteLeaderboardsBridge.getPlaytime( p ) );
        str = str.replace( "{bite_playtime_rank}", BiteLeaderboardsBridge.getPlaytimeRank( p ) );
        str = str.replace( "{bite_playtime_total}", BiteLeaderboardsBridge.getPlaytimeTotalTracked( p ) );

        if ( Bukkit.getPluginManager().isPluginEnabled( "PlaceholderAPI" ) )
        {
            str = me.clip.placeholderapi.PlaceholderAPI.setPlaceholders( (OfflinePlayer) p, str );
        }
        str = c( str );

        return str;
    }

    public static BaseComponent[] format( final Player player, final List<String> messages )
    {
        final AtomicInteger count = new AtomicInteger();
        return messages
                .stream()
                .map( message ->
                {
                    if ( count.incrementAndGet() >= messages.size() )
                    {
                        return formatString( player, message );
                    }
                    return formatString( player, message + "\n" );
                } )
                .map( TextComponent::fromLegacyText )
                .flatMap( Arrays::stream )
                .toArray( BaseComponent[]::new );
    }

    private static String getPlayerPrefix( final Player player )
    {
        return normalizeMetaValue( resolvePermissionMeta( player, true ) );
    }

    private static String getPlayerSuffix( final Player player )
    {
        return normalizeMetaValue( resolvePermissionMeta( player, false ) );
    }

    private static String resolvePermissionMeta( final Player player, final boolean prefix )
    {
        final Permission permission = DonatorJoinPlus.i().getPermission();
        if ( permission == null || player == null )
        {
            return "";
        }

        final String methodName = prefix ? "getPlayerPrefix" : "getPlayerSuffix";

        try
        {
            final Method method = permission.getClass().getMethod( methodName, Player.class );
            return (String) method.invoke( permission, player );
        }
        catch ( Exception ignored )
        {
            // Fallback to older Vault APIs that accept world/player names.
        }

        final String playerName = player.getName();
        final String worldName = player.getWorld() == null ? null : player.getWorld().getName();

        try
        {
            final Method method = permission.getClass().getMethod( methodName, String.class, String.class );
            return (String) method.invoke( permission, worldName, playerName );
        }
        catch ( Exception ignored )
        {
            return "";
        }
    }

    private static String normalizeMetaValue( final String value )
    {
        if ( value == null )
        {
            return "";
        }

        final String normalized = value.trim();
        if ( normalized.equalsIgnoreCase( "null" ) )
        {
            return "";
        }
        if ( normalized.isEmpty() )
        {
            return "";
        }
        return normalized;
    }

    private static String getPlayerDisplayName( final String prefix, final String name, final String suffix )
    {
        final StringBuilder display = new StringBuilder();

        if ( !prefix.isEmpty() )
        {
            display.append( prefix );
            if ( !prefix.endsWith( " " ) )
            {
                display.append( ' ' );
            }
        }

        display.append( name );

        if ( !suffix.isEmpty() )
        {
            if ( !suffix.startsWith( " " ) )
            {
                display.append( ' ' );
            }
            display.append( suffix );
        }

        return display.toString();
    }

    private static String getBalance( final Player player )
    {
        if ( DonatorJoinPlus.i().getEconomy() == null )
        {
            return "N/A";
        }

        try
        {
            final double balance = DonatorJoinPlus.i().getEconomy().getBalance( (OfflinePlayer) player );
            return DonatorJoinPlus.i().getEconomy().format( balance );
        }
        catch ( Exception ignored )
        {
            return "N/A";
        }
    }

    private static int getPing( final Player player )
    {
        try
        {
            final Method getPingMethod = player.getClass().getMethod( "getPing" );
            return ( (Number) getPingMethod.invoke( player ) ).intValue();
        }
        catch ( Exception ignored )
        {
            try
            {
                final Method spigotMethod = player.getClass().getMethod( "spigot" );
                final Object spigot = spigotMethod.invoke( player );
                final Method spigotPingMethod = spigot.getClass().getMethod( "getPing" );
                return ( (Number) spigotPingMethod.invoke( spigot ) ).intValue();
            }
            catch ( Exception ex )
            {
                return -1;
            }
        }
    }

    private static String getFirstJoinDate( final Player player )
    {
        final long firstPlayed = player.getFirstPlayed();
        if ( firstPlayed <= 0L )
        {
            return "Unknown";
        }

        return DATE_FORMATTER.format(
                Instant.ofEpochMilli( firstPlayed )
                        .atZone( ZoneId.systemDefault() )
        );
    }

    private static String formatPlaytime( final long seconds )
    {
        final long totalMinutes = Math.max( 0L, seconds ) / 60L;
        final long days = totalMinutes / 1440L;
        final long hours = ( totalMinutes % 1440L ) / 60L;
        final long minutes = totalMinutes % 60L;

        if ( days > 0L )
        {
            return String.format( Locale.US, "%dd %dh %dm", days, hours, minutes );
        }

        if ( hours > 0L )
        {
            return String.format( Locale.US, "%dh %dm", hours, minutes );
        }
        return String.format( Locale.US, "%dm", minutes );
    }

    private static String applyGradientColors( final String message )
    {
        final Matcher matcher = GRADIENT_PATTERN.matcher( message );
        final StringBuffer output = new StringBuffer();

        while ( matcher.find() )
        {
            final String gradientContent = matcher.group( 2 );
            final List<String> colors = parseGradientColors( matcher.group( 1 ) );

            if ( colors.size() < 2 )
            {
                matcher.appendReplacement( output, Matcher.quoteReplacement( gradientContent ) );
                continue;
            }

            matcher.appendReplacement(
                    output,
                    Matcher.quoteReplacement( applyGradient( gradientContent, colors ) )
            );
        }
        matcher.appendTail( output );
        return output.toString();
    }

    private static List<String> parseGradientColors( final String colorSpec )
    {
        final List<String> colors = new java.util.ArrayList<>();

        for ( String token : colorSpec.split( ":" ) )
        {
            final String color = token.trim();
            if ( HEX_COLOR_PATTERN.matcher( color ).matches() )
            {
                colors.add( color.startsWith( "#" ) ? color : "#" + color );
            }
        }
        return colors;
    }

    private static String applyGradient( final String text, final List<String> colors )
    {
        final String cleanText = stripLegacyColorAndStyleCodes( text );
        if ( cleanText.isEmpty() )
        {
            return "";
        }

        final StringBuilder builder = new StringBuilder();
        final int length = cleanText.length();

        for ( int i = 0; i < length; i++ )
        {
            final double ratio = length == 1 ? 0D : (double) i / ( length - 1 );
            builder.append( ChatColor.of( pickGradientColor( colors, ratio ) ) )
                    .append( cleanText.charAt( i ) );
        }

        return builder.toString();
    }

    private static String stripLegacyColorAndStyleCodes( final String input )
    {
        if ( input == null || input.isEmpty() )
        {
            return "";
        }

        return LEGACY_COLOR_OR_STYLE_PATTERN.matcher( input ).replaceAll( "" );
    }

    private static String pickGradientColor( final List<String> colors, final double ratio )
    {
        if ( colors.size() == 2 )
        {
            return interpolateColor( colors.get( 0 ), colors.get( 1 ), ratio );
        }

        final int segments = colors.size() - 1;
        final double clampedRatio = Math.max( 0D, Math.min( 1D, ratio ) );
        final double scaled = clampedRatio * segments;
        final int index = Math.min( segments - 1, (int) Math.floor( scaled ) );
        final double localRatio = scaled - index;

        return interpolateColor(
                colors.get( index ),
                colors.get( index + 1 ),
                localRatio
        );
    }

    private static String interpolateColor( final String startHex, final String endHex, final double ratio )
    {
        final int start = Integer.parseInt( startHex.substring( 1 ), 16 );
        final int end = Integer.parseInt( endHex.substring( 1 ), 16 );
        final double clampedRatio = Math.max( 0D, Math.min( 1D, ratio ) );

        final int red = interpolateChannel( start >> 16 & 0xFF, end >> 16 & 0xFF, clampedRatio );
        final int green = interpolateChannel( start >> 8 & 0xFF, end >> 8 & 0xFF, clampedRatio );
        final int blue = interpolateChannel( start & 0xFF, end & 0xFF, clampedRatio );

        return String.format( Locale.US, "#%02X%02X%02X", red, green, blue );
    }

    private static int interpolateChannel( final int start, final int end, final double ratio )
    {
        return (int) Math.round( start + ( end - start ) * ratio );
    }
}
