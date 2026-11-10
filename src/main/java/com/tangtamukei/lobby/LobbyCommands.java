package com.tangtamukei.lobby;

import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

public final class LobbyCommands {

    private LobbyCommands() {
    }

    public static void register(Commands commands, JavaPlugin plugin, LobbyManager lobby) {
        commands.register("lobby", "로비로 이동", List.of("hub", "spawn", "로비"), new BasicCommand() {
            @Override
            public void execute(CommandSourceStack source, String[] args) {
                if (!(source.getExecutor() instanceof Player player)) {
                    lobby.message(source.getSender(), "players-only");
                    return;
                }
                lobby.teleportToLobby(player);
            }
        });

        commands.register("setlobby", "현재 위치를 로비 스폰으로 설정", new BasicCommand() {
            @Override
            public void execute(CommandSourceStack source, String[] args) {
                if (!(source.getExecutor() instanceof Player player)) {
                    lobby.message(source.getSender(), "players-only");
                    return;
                }
                lobby.setSpawn(player.getLocation());
                lobby.message(player, "spawn-set");
            }

            @Override
            public String permission() {
                return "tangtamukei.admin";
            }
        });

        commands.register("lobbyrebuild", "로비 구조물 다시 짓기", new BasicCommand() {
            @Override
            public void execute(CommandSourceStack source, String[] args) {
                if (lobby.lobbyWorld().rebuild()) lobby.message(source.getSender(), "rebuilt");
                else lobby.message(source.getSender(), "no-lobby-world");
            }

            @Override
            public String permission() {
                return "tangtamukei.admin";
            }
        });

        commands.register("lobbyreload", "로비 설정 다시 불러오기", new BasicCommand() {
            @Override
            public void execute(CommandSourceStack source, String[] args) {
                plugin.reloadConfig();
                lobby.message(source.getSender(), "reloaded");
            }

            @Override
            public String permission() {
                return "tangtamukei.admin";
            }
        });
    }
}
