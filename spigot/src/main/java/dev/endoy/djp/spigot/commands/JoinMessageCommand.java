package dev.endoy.djp.spigot.commands;

import dev.endoy.configuration.api.ISection;
import dev.endoy.djp.spigot.DonatorJoinPlus;
import dev.endoy.djp.spigot.data.EventData;
import dev.endoy.djp.spigot.data.RankData;
import dev.endoy.djp.spigot.utils.SpigotUtils;
import dev.endoy.djp.utils.Utils;
import dev.endoy.spigot.commandapi.command.MainSpigotCommand;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.TextComponent;
import net.milkbowl.vault.permission.Permission;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class JoinMessageCommand extends MainSpigotCommand implements Listener
{

    private static final String CONFIG_ROOT = "joinmessage-command";
    private static final String MENU_TITLE_PATH = CONFIG_ROOT + ".menu-title";
    private static final String NO_PERMISSION_MESSAGE_PATH = CONFIG_ROOT + ".no-permission-message";
    private static final String STORE_LINK_PATH = CONFIG_ROOT + ".store-link";
    private static final String DEFAULT_MENU_TITLE = "&8Join Messages";
    private static final String DEFAULT_NO_PERMISSION_MESSAGE = "You must have a Rank to use this command. Click on this message to open the store";
    private static final String DEFAULT_STORE_LINK = "https://store.biteforcemc.com";
    private static final String BYPASS_PERMISSION = "donatorjoinplus.joinmessage.bypass";
    private static final int MIN_MENU_SIZE = 9;
    private static final int MAX_MENU_SIZE = 54;
    private static final int RANK_SLOTS_PER_PAGE = 45;
    private static final int PREVIOUS_PAGE_SLOT = 45;
    private static final int PAGE_INFO_SLOT = 49;
    private static final int NEXT_PAGE_SLOT = 53;
    private static final int MAX_PREVIEW_LINES = 6;

    public JoinMessageCommand()
    {
        super( "joinmessage" );
    }

    @Override
    public void onExecute( final CommandSender sender, final String[] args )
    {
        if ( !( sender instanceof Player ) )
        {
            sender.sendMessage( Utils.getMessage( "not-for-console" ) );
            return;
        }

        final Player player = (Player) sender;
        if ( !hasAnyRankAccess( player ) )
        {
            sendNoPermissionMessage( player );
            return;
        }

        openMenu( player, 0 );
    }

    private void sendNoPermissionMessage( final Player player )
    {
        final String message = DonatorJoinPlus.i().getConfiguration()
                .getString( NO_PERMISSION_MESSAGE_PATH, DEFAULT_NO_PERMISSION_MESSAGE );
        final String link = DonatorJoinPlus.i().getConfiguration().getString( STORE_LINK_PATH, DEFAULT_STORE_LINK );

        final TextComponent component = new TextComponent( TextComponent.fromLegacyText( SpigotUtils.c( message ) ) );

        if ( link != null && !link.trim().isEmpty() )
        {
            component.setClickEvent( new ClickEvent( ClickEvent.Action.OPEN_URL, link.trim() ) );
        }

        player.spigot().sendMessage( component );
    }

    private boolean hasAnyRankAccess( final Player player )
    {
        if ( player.hasPermission( BYPASS_PERMISSION ) )
        {
            return true;
        }

        final String[] groups = getPlayerGroups( player );
        for ( RankData rankData : DonatorJoinPlus.i().getRankData() )
        {
            if ( hasRankAccess( player, rankData, groups ) )
            {
                return true;
            }
        }

        return false;
    }

    private boolean hasRankAccess( final Player player, final RankData rankData, final String[] groups )
    {
        if ( player.hasPermission( BYPASS_PERMISSION ) )
        {
            return true;
        }

        final DonatorJoinPlus plugin = DonatorJoinPlus.i();
        final String permissionNode = rankData.getPermission();

        if ( plugin.isUsePermissions() )
        {
            return hasPermissionNode( player, permissionNode );
        }

        if ( groups.length > 0 && SpigotUtils.contains( groups, rankData.getName() ) )
        {
            return true;
        }

        // Fallback for setups where rank groups cannot be queried.
        return hasPermissionNode( player, permissionNode );
    }

    private boolean hasPermissionNode( final Player player, final String permissionNode )
    {
        return permissionNode != null && !permissionNode.trim().isEmpty() && player.hasPermission( permissionNode );
    }

    private String[] getPlayerGroups( final Player player )
    {
        final Permission permission = DonatorJoinPlus.i().getPermission();
        if ( permission == null )
        {
            return new String[0];
        }

        try
        {
            final String[] groups = permission.getPlayerGroups( player );
            return groups == null ? new String[0] : groups;
        }
        catch ( Exception ignored )
        {
            return new String[0];
        }
    }

    private void openMenu( final Player player, final int requestedPage )
    {
        final List<RankData> rankData = DonatorJoinPlus.i().getRankData();
        final int totalPages = Math.max( 1, (int) Math.ceil( rankData.size() / (double) RANK_SLOTS_PER_PAGE ) );
        final int page = Math.max( 0, Math.min( requestedPage, totalPages - 1 ) );
        final boolean paginated = totalPages > 1;
        final int menuSize = paginated ? MAX_MENU_SIZE : getMenuSize( rankData.size() );
        final JoinMessageMenuHolder holder = new JoinMessageMenuHolder( page, totalPages, paginated );
        final Inventory inventory = Bukkit.createInventory(
                holder,
                menuSize,
                SpigotUtils.c(
                        DonatorJoinPlus.i().getConfiguration().getString( MENU_TITLE_PATH, DEFAULT_MENU_TITLE )
                )
        );
        holder.setInventory( inventory );

        final String[] groups = getPlayerGroups( player );
        final int startIndex = page * RANK_SLOTS_PER_PAGE;
        final int maxRanksInPage = paginated ? RANK_SLOTS_PER_PAGE : menuSize;
        final int endIndex = Math.min( rankData.size(), startIndex + maxRanksInPage );

        int slot = 0;
        for ( int i = startIndex; i < endIndex; i++ )
        {
            final RankData rank = rankData.get( i );
            final boolean hasAccess = hasRankAccess( player, rank, groups );
            inventory.setItem( slot, createRankItem( player, rank, hasAccess ) );
            slot++;
        }

        if ( paginated )
        {
            addNavigationItems( inventory, page, totalPages );
        }

        player.openInventory( inventory );
    }

    private void addNavigationItems( final Inventory inventory, final int page, final int totalPages )
    {
        if ( page > 0 )
        {
            inventory.setItem( PREVIOUS_PAGE_SLOT, createNavigationItem( "&ePrevious Page" ) );
        }

        inventory.setItem( PAGE_INFO_SLOT, createPageInfoItem( page, totalPages ) );

        if ( page + 1 < totalPages )
        {
            inventory.setItem( NEXT_PAGE_SLOT, createNavigationItem( "&eNext Page" ) );
        }
    }

    private int getMenuSize( final int ranks )
    {
        if ( ranks <= 0 )
        {
            return MIN_MENU_SIZE;
        }

        final int rows = (int) Math.ceil( ranks / 9D );
        return Math.max( MIN_MENU_SIZE, Math.min( MAX_MENU_SIZE, rows * 9 ) );
    }

    private ItemStack createNavigationItem( final String name )
    {
        final ItemStack item = new ItemStack( Material.ARROW );
        final ItemMeta itemMeta = item.getItemMeta();

        if ( itemMeta == null )
        {
            return item;
        }

        itemMeta.setDisplayName( SpigotUtils.c( name ) );
        item.setItemMeta( itemMeta );
        return item;
    }

    private ItemStack createPageInfoItem( final int page, final int totalPages )
    {
        final ItemStack item = new ItemStack( Material.BOOK );
        final ItemMeta itemMeta = item.getItemMeta();

        if ( itemMeta == null )
        {
            return item;
        }

        itemMeta.setDisplayName( SpigotUtils.c( "&fPage " + ( page + 1 ) + "&7/&f" + totalPages ) );
        item.setItemMeta( itemMeta );
        return item;
    }

    private ItemStack createRankItem( final Player player, final RankData rankData, final boolean hasAccess )
    {
        final ItemStack item = new ItemStack( hasAccess ? Material.PAPER : Material.GRAY_DYE );
        final ItemMeta itemMeta = item.getItemMeta();

        if ( itemMeta == null )
        {
            return item;
        }

        final String rankName = valueOrDefault( rankData.getName(), "Unknown Rank" );
        final String permissionNode = valueOrDefault( rankData.getPermission(), "none" );

        itemMeta.setDisplayName( SpigotUtils.c( ( hasAccess ? "&a" : "&7" ) + rankName ) );

        final List<String> lore = new ArrayList<>();
        lore.add( SpigotUtils.c( "&7Permission: &f" + permissionNode ) );
        lore.add( SpigotUtils.c( hasAccess ? "&aAvailable to you" : "&8Locked (no permission)" ) );
        lore.add( SpigotUtils.c( "&8Join Message:" ) );
        lore.addAll( getJoinMessagePreview( player, rankData ) );

        itemMeta.setLore( lore );
        item.setItemMeta( itemMeta );
        return item;
    }

    private List<String> getJoinMessagePreview( final Player player, final RankData rankData )
    {
        final EventData eventData = rankData.getEvents().get( EventData.EventType.JOIN );
        if ( eventData == null || eventData.getMessage() == null )
        {
            return Collections.singletonList( SpigotUtils.c( "&7No join message configured." ) );
        }

        final List<String> previewLines = extractMessageLines( player, eventData.getMessage() );
        if ( previewLines.isEmpty() )
        {
            return Collections.singletonList( SpigotUtils.c( "&7No join message configured." ) );
        }

        final List<String> trimmedLines = new ArrayList<>();
        for ( String line : previewLines )
        {
            if ( trimmedLines.size() >= MAX_PREVIEW_LINES )
            {
                trimmedLines.add( SpigotUtils.c( "&8..." ) );
                break;
            }

            trimmedLines.add( line );
        }

        return trimmedLines;
    }

    private List<String> extractMessageLines( final Player player, final Object message )
    {
        final List<String> lines = new ArrayList<>();
        if ( message == null )
        {
            return lines;
        }

        if ( message instanceof ISection )
        {
            lines.addAll( extractMessageLines( player, (ISection) message ) );
            return lines;
        }

        if ( message instanceof List<?> )
        {
            for ( Object value : (List<?>) message )
            {
                lines.addAll( extractMessageLines( player, value ) );
            }
            return lines;
        }

        lines.addAll( splitToLines( SpigotUtils.formatString( player, normalizeLines( String.valueOf( message ) ) ) ) );
        return lines;
    }

    private List<String> extractMessageLines( final Player player, final ISection section )
    {
        if ( section.isList( "text" ) )
        {
            final StringBuilder builder = new StringBuilder();
            for ( ISection textSection : section.getSectionList( "text" ) )
            {
                if ( textSection.exists( "text" ) )
                {
                    builder.append( textSection.getString( "text" ) );
                }
            }

            return splitToLines( SpigotUtils.formatString( player, normalizeLines( builder.toString() ) ) );
        }

        if ( section.exists( "text" ) )
        {
            return splitToLines( SpigotUtils.formatString( player, normalizeLines( section.getString( "text" ) ) ) );
        }

        return Collections.emptyList();
    }

    private String normalizeLines( final String text )
    {
        if ( text == null || text.isEmpty() )
        {
            return "";
        }

        final String newLine = System.lineSeparator();
        return text.replace( "<nl>", newLine )
                .replace( "%nl%", newLine )
                .replace( "%newline%", newLine )
                .replace( "{nl}", newLine )
                .replace( "{newline}", newLine )
                .replace( "\r\n", newLine )
                .replace( "\n", newLine );
    }

    private List<String> splitToLines( final String text )
    {
        if ( text == null || text.isEmpty() )
        {
            return Collections.emptyList();
        }

        final List<String> lines = new ArrayList<>();
        for ( String line : text.split( "\\R" ) )
        {
            if ( !line.isEmpty() )
            {
                lines.add( line );
            }
        }
        return lines;
    }

    private String valueOrDefault( final String value, final String fallback )
    {
        if ( value == null || value.trim().isEmpty() )
        {
            return fallback;
        }

        return value;
    }

    @EventHandler
    public void onMenuClick( final InventoryClickEvent event )
    {
        if ( !( event.getView().getTopInventory().getHolder() instanceof JoinMessageMenuHolder ) )
        {
            return;
        }

        event.setCancelled( true );

        if ( !( event.getWhoClicked() instanceof Player ) )
        {
            return;
        }

        final JoinMessageMenuHolder holder = (JoinMessageMenuHolder) event.getView().getTopInventory().getHolder();
        if ( !holder.isPaginated() )
        {
            return;
        }

        if ( event.getRawSlot() >= event.getView().getTopInventory().getSize() )
        {
            return;
        }

        final Player player = (Player) event.getWhoClicked();

        if ( event.getRawSlot() == PREVIOUS_PAGE_SLOT && holder.getPage() > 0 )
        {
            openMenu( player, holder.getPage() - 1 );
        }
        else if ( event.getRawSlot() == NEXT_PAGE_SLOT && holder.getPage() + 1 < holder.getTotalPages() )
        {
            openMenu( player, holder.getPage() + 1 );
        }
    }

    @EventHandler
    public void onMenuDrag( final InventoryDragEvent event )
    {
        if ( event.getView().getTopInventory().getHolder() instanceof JoinMessageMenuHolder )
        {
            event.setCancelled( true );
        }
    }

    private static class JoinMessageMenuHolder implements InventoryHolder
    {
        private final int page;
        private final int totalPages;
        private final boolean paginated;
        private Inventory inventory;

        public JoinMessageMenuHolder( final int page, final int totalPages, final boolean paginated )
        {
            this.page = page;
            this.totalPages = totalPages;
            this.paginated = paginated;
        }

        public int getPage()
        {
            return page;
        }

        public int getTotalPages()
        {
            return totalPages;
        }

        public boolean isPaginated()
        {
            return paginated;
        }

        public void setInventory( final Inventory inventory )
        {
            this.inventory = inventory;
        }

        @Override
        public Inventory getInventory()
        {
            return inventory;
        }
    }
}
