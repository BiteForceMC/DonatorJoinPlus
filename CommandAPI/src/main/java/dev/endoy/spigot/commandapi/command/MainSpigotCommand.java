package dev.endoy.spigot.commandapi.command;

import dev.endoy.spigot.commandapi.utils.MessageConfig;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.util.StringUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public abstract class MainSpigotCommand extends Command
{
    protected final List<SubCommand> subCommands = new ArrayList<>();
    private MessageConfig messageConfig = new MessageConfig();

    public MainSpigotCommand(final String name)
    {
        this(name, Collections.emptyList());
    }

    public MainSpigotCommand(final String name, final List<String> aliases)
    {
        super(name);
        setAliases(new ArrayList<>(aliases));
    }

    public MessageConfig getMessageConfig()
    {
        return messageConfig;
    }

    public void setMessageConfig(final MessageConfig messageConfig)
    {
        this.messageConfig = messageConfig;
    }

    public abstract void onExecute(CommandSender sender, String[] args);

    @Override
    public boolean execute(final CommandSender sender, final String commandLabel, final String[] args)
    {
        onExecute(sender, args);
        return true;
    }

    @Override
    public List<String> tabComplete(final CommandSender sender, final String alias, final String[] args)
    {
        if (args.length <= 1)
        {
            final String query = args.length == 0 ? "" : args[0];
            final List<String> names = new ArrayList<>();

            for (SubCommand subCommand : subCommands)
            {
                if (!subCommand.hasPermission(sender))
                {
                    continue;
                }

                if (StringUtil.startsWithIgnoreCase(subCommand.getName(), query))
                {
                    names.add(subCommand.getName());
                }
            }

            return names;
        }

        final String subCommandName = args[0];
        final String[] subArgs = Arrays.copyOfRange(args, 1, args.length);
        for (SubCommand subCommand : subCommands)
        {
            if (!subCommand.getName().equalsIgnoreCase(subCommandName))
            {
                continue;
            }
            if (!subCommand.hasPermission(sender))
            {
                return Collections.emptyList();
            }

            final List<String> completions = new ArrayList<>(subCommand.getSafeCompletions(sender, subArgs));
            if (completions.isEmpty())
            {
                return completions;
            }

            final String lastArg = subArgs.length == 0 ? "" : subArgs[subArgs.length - 1];
            final List<String> filtered = new ArrayList<>();
            for (String completion : completions)
            {
                if (completion != null && StringUtil.startsWithIgnoreCase(completion, lastArg))
                {
                    filtered.add(completion);
                }
            }

            return filtered;
        }

        return Collections.emptyList();
    }
}
