package com.mcdiscordbridge.bridge;

import com.mcdiscordbridge.config.BridgeConfig;
import com.mcdiscordbridge.util.Formatting;
import com.mcdiscordbridge.util.Json;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.net.URI;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

/**
 * Owns the single connection to the Discord bot bridge server: connecting,
 * reconnecting with backoff, and translating between bridge protocol
 * messages and in-game chat. Every method here is safe to call from any
 * thread and never blocks the caller for a meaningful amount of time,
 * except {@link #shutdown()} which bounds its wait explicitly.
 */
public final class BridgeManager {

    private final JavaPlugin plugin;
    private final BridgeConfig config;
    private final Logger logger;

    private final AtomicReference<BridgeWebSocketClient> client = new AtomicReference<>();
    private final AtomicInteger reconnectDelaySeconds;
    private volatile boolean shuttingDown = false;
    private volatile int reconnectTaskId = -1;

    public BridgeManager(JavaPlugin plugin, BridgeConfig config) {
        this.plugin = plugin;
        this.config = config;
        this.logger = plugin.getLogger();
        this.reconnectDelaySeconds = new AtomicInteger(config.reconnectInitialDelaySeconds());
    }

    public void connect() {
        if (shuttingDown) {
            return;
        }
        try {
            URI uri = new URI("ws://" + config.host() + ":" + config.port());
            BridgeWebSocketClient newClient = new BridgeWebSocketClient(uri, this, config.secret(), logger);
            client.set(newClient);
            newClient.connect();
        } catch (Exception e) {
            logger.warning("[MCDiscordBridge] Failed to start connection to Discord bot: " + e.getMessage());
            scheduleReconnect();
        }
    }

    void onConnected() {
        reconnectDelaySeconds.set(config.reconnectInitialDelaySeconds());
    }

    void onDisconnected() {
        if (!shuttingDown) {
            logger.warning("[MCDiscordBridge] Disconnected from Discord bot, will retry.");
            scheduleReconnect();
        }
    }

    private void scheduleReconnect() {
        if (shuttingDown || !plugin.isEnabled()) {
            return;
        }
        int delay = reconnectDelaySeconds.get();
        reconnectTaskId = Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, () -> {
            if (!shuttingDown) {
                connect();
            }
        }, delay * 20L).getTaskId();
        int next = Math.min(delay * 2, config.reconnectMaxDelaySeconds());
        reconnectDelaySeconds.set(next);
    }

    /**
     * Called when the Discord bot relays a chat message. Runs on the
     * WebSocket I/O thread, so the actual broadcast is handed to the main
     * thread as Bukkit API calls are not thread-safe.
     */
    void onDiscordChat(String user, String message) {
        String formatted = Formatting.discordToMinecraft(config.discordToMinecraftFormat(), user, message);
        Component component = LegacyComponentSerializer.legacyAmpersand().deserialize(formatted);
        Bukkit.getScheduler().runTask(plugin, () -> Bukkit.broadcast(component));
    }

    /** Forwards an in-game chat message to Discord. Fire-and-forget. */
    public void sendChat(String player, String message) {
        sendBestEffort(Json.object("type", "chat", "player", player, "message", message));
    }

    /** Notifies Discord that this server has finished starting up. */
    public void sendServerStart() {
        sendBestEffort(Json.object("type", "server_start", "server_name", config.serverName()));
    }

    private void sendBestEffort(String json) {
        BridgeWebSocketClient current = client.get();
        if (current != null && current.isOpen() && current.isAuthenticated()) {
            try {
                current.send(json);
            } catch (Exception e) {
                logger.fine("[MCDiscordBridge] Failed to send bridge message: " + e.getMessage());
            }
        }
    }

    /**
     * Sends the server_stop notice and closes the connection, bounded by
     * {@code bridge.shutdown-flush-timeout-ms} so a stuck or unreachable
     * bot never delays server shutdown.
     */
    public void shutdown() {
        shuttingDown = true;
        if (reconnectTaskId != -1) {
            Bukkit.getScheduler().cancelTask(reconnectTaskId);
        }
        BridgeWebSocketClient current = client.get();
        if (current == null) {
            return;
        }
        Thread flushThread = new Thread(() -> {
            try {
                if (current.isOpen() && current.isAuthenticated()) {
                    current.send(Json.object("type", "server_stop", "server_name", config.serverName()));
                }
                current.closeBlocking();
            } catch (Exception e) {
                logger.fine("[MCDiscordBridge] Error while closing bridge connection: " + e.getMessage());
            }
        }, "MCDiscordBridge-Shutdown");
        flushThread.setDaemon(true);
        flushThread.start();
        try {
            flushThread.join(config.shutdownFlushTimeoutMs());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
