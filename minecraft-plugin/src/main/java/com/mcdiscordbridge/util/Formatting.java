package com.mcdiscordbridge.util;

import org.bukkit.ChatColor;

/**
 * Helpers for turning bridge format templates into display strings.
 */
public final class Formatting {

    private Formatting() {
    }

    /**
     * Applies "&"-based color codes to the template first, then substitutes
     * placeholders. Doing it in this order means untrusted placeholder values
     * (a Discord username or message) can never inject color codes into
     * in-game chat.
     */
    public static String discordToMinecraft(String template, String user, String message) {
        String colored = ChatColor.translateAlternateColorCodes('&', template);
        return colored.replace("{user}", user).replace("{message}", message);
    }

    public static String minecraftToDiscord(String template, String player, String message) {
        return template.replace("{player}", player).replace("{message}", message);
    }
}
