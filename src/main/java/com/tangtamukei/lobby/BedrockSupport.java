package com.tangtamukei.lobby;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.List;
import java.util.function.IntConsumer;

/**
 * Floodgate(Geyser) 연동. Floodgate 가 없는 서버에서도 클래스 로딩 오류가 나지 않도록
 * 실제 API 호출은 {@link FloodgateHook} 안에만 둡니다.
 */
public interface BedrockSupport {

    boolean isAvailable();

    boolean isBedrock(Player player);

    /** 베드락 버튼 폼 전송. 성공하면 true. */
    boolean sendButtonForm(Player player, String title, String content,
                           List<Button> buttons, IntConsumer onClick);

    record Button(String text, String image) {
    }

    static BedrockSupport create(Plugin plugin) {
        if (plugin.getServer().getPluginManager().getPlugin("floodgate") != null) {
            try {
                return new FloodgateHook();
            } catch (Throwable t) {
                plugin.getLogger().warning("Floodgate 연동 실패: " + t.getMessage());
            }
        }
        return new None();
    }

    final class None implements BedrockSupport {
        @Override
        public boolean isAvailable() {
            return false;
        }

        @Override
        public boolean isBedrock(Player player) {
            // Floodgate 없이 Geyser 만 쓰는 경우 대략적인 판별 (기본 접두사 '.')
            return player.getName().startsWith(".");
        }

        @Override
        public boolean sendButtonForm(Player player, String title, String content,
                                      List<Button> buttons, IntConsumer onClick) {
            return false;
        }
    }
}
