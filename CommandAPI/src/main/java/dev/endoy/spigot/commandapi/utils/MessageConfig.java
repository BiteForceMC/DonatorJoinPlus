package dev.endoy.spigot.commandapi.utils;

public class MessageConfig
{
    private String noPermissionMessage = "&cYou do not have permission.";
    private String usageMessage = "&eUsage: &f{usage}";

    public String getNoPermissionMessage()
    {
        return noPermissionMessage;
    }

    public void setNoPermissionMessage(final String noPermissionMessage)
    {
        this.noPermissionMessage = noPermissionMessage;
    }

    public String getUsageMessage()
    {
        return usageMessage;
    }

    public void setUsageMessage(final String usageMessage)
    {
        this.usageMessage = usageMessage;
    }
}
