package com.mcdiscordbridge.config;

import org.bukkit.configuration.file.FileConfiguration;

/**
 * Typed view over config.yml, resolved once at startup.
 */
public final class BridgeConfig {

    private final String host;
    private final int port;
    private final String secret;
    private final int reconnectInitialDelaySeconds;
    private final int reconnectMaxDelaySeconds;
    private final long shutdownFlushTimeoutMs;
    private final String serverName;
    private final String minecraftToDiscordFormat;
    private final String discordToMinecraftFormat;

    private BridgeConfig(String host, int port, String secret,
                          int reconnectInitialDelaySeconds, int reconnectMaxDelaySeconds,
                          long shutdownFlushTimeoutMs, String serverName,
                          String minecraftToDiscordFormat, String discordToMinecraftFormat) {
        this.host = host;
        this.port = port;
        this.secret = secret;
        this.reconnectInitialDelaySeconds = reconnectInitialDelaySeconds;
        this.reconnectMaxDelaySeconds = reconnectMaxDelaySeconds;
        this.shutdownFlushTimeoutMs = shutdownFlushTimeoutMs;
        this.serverName = serverName;
        this.minecraftToDiscordFormat = minecraftToDiscordFormat;
        this.discordToMinecraftFormat = discordToMinecraftFormat;
    }

    public static BridgeConfig fromConfiguration(FileConfiguration config) {
        return new BridgeConfig(
                config.getString("bridge.host", "127.0.0.1"),
                config.getInt("bridge.port", 8765),
                config.getString("bridge.secret", ""),
                config.getInt("bridge.reconnect.initial-delay", 5),
                config.getInt("bridge.reconnect.max-delay", 60),
                config.getLong("bridge.shutdown-flush-timeout-ms", 3000L),
                config.getString("server.name", "Minecraft Server"),
                config.getString("format.minecraft-to-discord", "**{player}**: {message}"),
                config.getString("format.discord-to-minecraft", "&9[Discord] &b{user}&f: {message}")
        );
    }

    public String host() {
        return host;
    }

    public int port() {
        return port;
    }

    public String secret() {
        return secret;
    }

    public int reconnectInitialDelaySeconds() {
        return reconnectInitialDelaySeconds;
    }

    public int reconnectMaxDelaySeconds() {
        return reconnectMaxDelaySeconds;
    }

    public long shutdownFlushTimeoutMs() {
        return shutdownFlushTimeoutMs;
    }

    public String serverName() {
        return serverName;
    }

    public String minecraftToDiscordFormat() {
        return minecraftToDiscordFormat;
    }

    public String discordToMinecraftFormat() {
        return discordToMinecraftFormat;
    }
}
