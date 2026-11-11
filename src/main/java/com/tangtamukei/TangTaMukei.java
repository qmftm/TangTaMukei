package com.tangtamukei;

import com.tangtamukei.game.GameCommand;
import com.tangtamukei.game.GameListener;
import com.tangtamukei.game.GameManager;
import com.tangtamukei.lobby.BedrockSupport;
import com.tangtamukei.lobby.LobbyCommands;
import com.tangtamukei.lobby.LobbyListener;
import com.tangtamukei.lobby.LobbyManager;
import com.tangtamukei.lobby.LobbyWorld;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.plugin.java.JavaPlugin;

public final class TangTaMukei extends JavaPlugin {

    private LobbyManager lobby;
    private GameManager game;
    private com.tangtamukei.game.LobbyPortals portals;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        BedrockSupport bedrock = BedrockSupport.create(this);
        LobbyWorld lobbyWorld = new LobbyWorld(this);
        lobbyWorld.load();
        lobby = new LobbyManager(this, bedrock, lobbyWorld);

        // BungeeCord / Velocity 서버 이동용 채널
        getServer().getMessenger().registerOutgoingPluginChannel(this, "BungeeCord");

        game = new GameManager(this, lobby);
        lobby.setInGameCheck(p -> game.teamOf(p) != null);

        getServer().getPluginManager().registerEvents(new LobbyListener(lobby), this);
        getServer().getPluginManager().registerEvents(new GameListener(game, this), this);
        portals = new com.tangtamukei.game.LobbyPortals(this, lobby, game);
        getServer().getPluginManager().registerEvents(portals, this);
        getServer().getScheduler().runTask(this, portals::spawnLabels);
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            LobbyCommands.register(event.registrar(), this, lobby);
            event.registrar().register("game", "팀 게임 시작/종료", java.util.List.of("게임"), new GameCommand(game));
            event.registrar().register("map", "땅따먹기 지도 받기", java.util.List.of("지도"), (source, args) -> {
                if (!(source.getExecutor() instanceof org.bukkit.entity.Player player)) return;
                if (!game.giveMap(player)) player.sendMessage(com.tangtamukei.lobby.Text.parse("<red>게임 중일 때만 지도를 받을 수 있어요."));
            });
            event.registrar().register("book", "발전과제 진척 책 받기", java.util.List.of("책"), (source, args) -> {
                if (source.getExecutor() instanceof org.bukkit.entity.Player player) {
                    player.getInventory().addItem(game.book().create(player));
                }
            });
        });

        getLogger().info("로비 활성화 완료 (Floodgate " + (bedrock.isAvailable() ? "연동됨" : "없음 - 베드락은 상자 GUI 사용") + ")");
    }

    @Override
    public void onDisable() {
        if (portals != null) portals.removeLabels();
        if (game != null) game.shutdown();
        getServer().getMessenger().unregisterOutgoingPluginChannel(this);
    }

    public LobbyManager lobby() {
        return lobby;
    }

    public GameManager game() {
        return game;
    }
}
