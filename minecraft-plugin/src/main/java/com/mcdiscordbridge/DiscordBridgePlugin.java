package com.mcdiscordbridge;

import com.mcdiscordbridge.bridge.BridgeManager;
import com.mcdiscordbridge.config.BridgeConfig;
import com.mcdiscordbridge.listener.ChatListener;
import org.bukkit.event.server.ServerLoadEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

public final class DiscordBridgePlugin extends JavaPlugin implements Listener {

    private BridgeManager bridgeManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        BridgeConfig config = BridgeConfig.fromConfiguration(getConfig());

        if (config.secret() == null || config.secret().isBlank() || config.secret().equals("CHANGE_ME_SHARED_SECRET")) {
            getLogger().warning("[MCDiscordBridge] bridge.secret has not been set in config.yml - "
                    + "set it to match the discord-bot's config before the bridge will authenticate.");
        }

        bridgeManager = new BridgeManager(this, config);
        getServer().getPluginManager().registerEvents(new ChatListener(bridgeManager), this);
        getServer().getPluginManager().registerEvents(this, this);

        bridgeManager.connect();
    }

    @EventHandler
    public void onServerLoad(ServerLoadEvent event) {
        if (event.getType() == ServerLoadEvent.LoadType.STARTUP) {
            bridgeManager.sendServerStart();
        }
    }

    @Override
    public void onDisable() {
        if (bridgeManager != null) {
            bridgeManager.shutdown();
        }
    }
}
