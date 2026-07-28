package com.mcdiscordbridge.listener;

import com.mcdiscordbridge.bridge.BridgeManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Forwards player join/leave events to Discord. Does not alter the event, it
 * only observes what will be shown in-game.
 */
public final class PlayerConnectionListener implements Listener {

    private final BridgeManager bridgeManager;

    public PlayerConnectionListener(BridgeManager bridgeManager) {
        this.bridgeManager = bridgeManager;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        bridgeManager.sendPlayerJoin(event.getPlayer().getName());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        bridgeManager.sendPlayerLeave(event.getPlayer().getName());
    }
}
