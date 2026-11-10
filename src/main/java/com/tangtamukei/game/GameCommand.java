package com.tangtamukei.game;

import com.tangtamukei.lobby.Text;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.command.CommandSender;

import java.util.Collection;
import java.util.List;

/** /game start [팀수] | stop | status */
public final class GameCommand implements BasicCommand {

    private final GameManager game;

    public GameCommand(GameManager game) {
        this.game = game;
    }

    @Override
    public void execute(CommandSourceStack source, String[] args) {
        CommandSender sender = source.getSender();
        String sub = args.length == 0 ? "status" : args[0].toLowerCase();
        switch (sub) {
            case "start" -> {
                int teams = 0;
                if (args.length > 1) {
                    try {
                        teams = Integer.parseInt(args[1]);
                    } catch (NumberFormatException e) {
                        send(sender, "<red>팀 수는 1~4 숫자로 입력하세요.");
                        return;
                    }
                }
                if (!game.start(teams)) send(sender, "<red>이미 게임이 진행 중이에요.");
            }
            case "stop" -> {
                if (!game.stop()) send(sender, "<red>진행 중인 게임이 없습니다.");
            }
            default -> send(sender, "<gray>게임 상태: <white>" + switch (game.state()) {
                case IDLE -> "맵 없음";
                case PREPARING -> "다음 맵 준비 중";
                case READY -> "맵 준비 완료 (바로 시작 가능)";
                case RUNNING -> "게임 중";
            } + " <dark_gray>| /game start [팀수 1~4], /game stop");
        }
    }

    @Override
    public Collection<String> suggest(CommandSourceStack source, String[] args) {
        if (args.length <= 1) return List.of("start", "stop", "status");
        if (args.length == 2 && args[0].equalsIgnoreCase("start")) return List.of("2", "3", "4");
        return List.of();
    }

    @Override
    public String permission() {
        return "tangtamukei.admin";
    }

    private static void send(CommandSender sender, String mini) {
        sender.sendMessage(Text.parse("<gold>[게임]</gold> " + mini));
    }
}
