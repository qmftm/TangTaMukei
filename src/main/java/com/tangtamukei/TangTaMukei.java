package com.tangtamukei;

import com.tangtamukei.lobby.BedrockSupport;
import com.tangtamukei.lobby.LobbyCommands;
import com.tangtamukei.lobby.LobbyListener;
import com.tangtamukei.lobby.LobbyManager;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.plugin.java.JavaPlugin;

public final class TangTaMukei extends JavaPlugin {

    private LobbyManager lobby;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        BedrockSupport bedrock = BedrockSupport.create(this);
        lobby = new LobbyManager(this, bedrock);

        // BungeeCord / Velocity 서버 이동용 채널
        getServer().getMessenger().registerOutgoingPluginChannel(this, "BungeeCord");

        getServer().getPluginManager().registerEvents(new LobbyListener(lobby), this);
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS,
                event -> LobbyCommands.register(event.registrar(), this, lobby));

        getLogger().info("로비 활성화 완료 (Floodgate " + (bedrock.isAvailable() ? "연동됨" : "없음 - 베드락은 상자 GUI 사용") + ")");
    }

    @Override
    public void onDisable() {
        getServer().getMessenger().unregisterOutgoingPluginChannel(this);
    }

    public LobbyManager lobby() {
        return lobby;
    }
}
