package com.mcdiscordbridge.listener;

import com.mcdiscordbridge.bridge.BridgeManager;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

/**
 * Forwards player chat to Discord. Does not cancel or alter the event, it
 * only observes what will be shown in-game.
 */
public final class ChatListener implements Listener {

    private final BridgeManager bridgeManager;

    public ChatListener(BridgeManager bridgeManager) {
        this.bridgeManager = bridgeManager;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        String message = PlainTextComponentSerializer.plainText().serialize(event.message());
        bridgeManager.sendChat(event.getPlayer().getName(), message);
    }
}
