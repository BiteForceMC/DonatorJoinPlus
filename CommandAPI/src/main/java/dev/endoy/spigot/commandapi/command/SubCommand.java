package dev.endoy.spigot.commandapi.command;

import dev.endoy.spigot.commandapi.utils.MessageConfig;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public abstract class SubCommand
{
    private final String name;
    private final int minimumArgs;
    private final int maximumArgs;

    public SubCommand(final String name)
    {
        this(name, 0, Integer.MAX_VALUE);
    }

    public SubCommand(final String name, final int minimumArgs)
    {
        this(name, minimumArgs, Integer.MAX_VALUE);
    }

    public SubCommand(final String name, final int minimumArgs, final int maximumArgs)
    {
        this.name = name;
        this.minimumArgs = Math.max(0, minimumArgs);
        this.maximumArgs = maximumArgs < 0 ? Integer.MAX_VALUE : maximumArgs;
    }

    public String getName()
    {
        return name;
    }

    public int getMinimumArgs()
    {
        return minimumArgs;
    }

    public int getMaximumArgs()
    {
        return maximumArgs;
    }

    public boolean hasPermission(final CommandSender sender)
    {
        final String permission = getPermission();
        return permission == null || permission.isEmpty() || sender.hasPermission(permission);
    }

    public boolean execute(final CommandSender sender, final String[] args, final MessageConfig messageConfig)
    {
        if (args.length == 0 || !name.equalsIgnoreCase(args[0]))
        {
            return false;
        }

        final String[] subArgs = Arrays.copyOfRange(args, 1, args.length);
        if (!hasPermission(sender))
        {
            if (messageConfig != null && messageConfig.getNoPermissionMessage() != null)
            {
                sender.sendMessage(messageConfig.getNoPermissionMessage());
            }
            return true;
        }

        if (subArgs.length < minimumArgs || subArgs.length > maximumArgs)
        {
            if (messageConfig != null && messageConfig.getUsageMessage() != null)
            {
                sender.sendMessage(messageConfig.getUsageMessage().replace("{usage}", getUsage()));
            }
            return true;
        }

        if (sender instanceof Player)
        {
            onExecute((Player) sender, subArgs);
        }
        else
        {
            onExecute(sender, subArgs);
        }

        return true;
    }

    public List<String> getSafeCompletions(final CommandSender sender, final String[] args)
    {
        final List<String> completions;
        if (sender instanceof Player)
        {
            completions = getCompletions((Player) sender, args);
        }
        else
        {
            completions = getCompletions(sender, args);
        }

        return completions == null ? Collections.emptyList() : completions;
    }

    public abstract String getUsage();

    public abstract String getPermission();

    public abstract void onExecute(Player player, String[] args);

    public abstract void onExecute(CommandSender sender, String[] args);

    public abstract List<String> getCompletions(Player player, String[] args);

    public abstract List<String> getCompletions(CommandSender sender, String[] args);
}
