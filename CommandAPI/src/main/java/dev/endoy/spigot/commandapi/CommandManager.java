package dev.endoy.spigot.commandapi;

import dev.endoy.spigot.commandapi.command.MainSpigotCommand;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandMap;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Field;
import java.util.Locale;

public final class CommandManager
{
    private static final CommandManager INSTANCE = new CommandManager();

    private CommandManager()
    {
    }

    public static CommandManager getInstance()
    {
        return INSTANCE;
    }

    public void registerCommand(final MainSpigotCommand command)
    {
        final JavaPlugin plugin = JavaPlugin.getProvidingPlugin(command.getClass());
        final CommandMap commandMap = getCommandMap();

        if (commandMap == null)
        {
            throw new IllegalStateException("Could not access Bukkit command map");
        }

        commandMap.register(plugin.getName().toLowerCase(Locale.ROOT), command);
    }

    private CommandMap getCommandMap()
    {
        try
        {
            final Field field = Bukkit.getServer().getClass().getDeclaredField("commandMap");
            field.setAccessible(true);
            return (CommandMap) field.get(Bukkit.getServer());
        }
        catch (NoSuchFieldException | IllegalAccessException ex)
        {
            return null;
        }
    }
}
