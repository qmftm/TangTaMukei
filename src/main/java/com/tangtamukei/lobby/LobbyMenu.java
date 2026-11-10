package com.tangtamukei.lobby;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 게임 선택 메뉴.
 * Java 유저 → 상자 GUI, 베드락 유저(Floodgate) → 베드락 버튼 폼.
 */
public final class LobbyMenu implements InventoryHolder {

    private final Inventory inventory;
    private final Map<Integer, String> actions = new HashMap<>();

    private LobbyMenu(LobbyManager lobby) {
        ConfigurationSection menu = lobby.config().getConfigurationSection("menu");
        int rows = Math.clamp(menu == null ? 3 : menu.getInt("rows", 3), 1, 6);
        String title = menu == null ? "Menu" : menu.getString("title", "Menu");
        this.inventory = Bukkit.createInventory(this, rows * 9, Text.parse(title));

        for (String id : lobby.menuEntryIds()) {
            ConfigurationSection entry = menu.getConfigurationSection("entries." + id);
            if (entry == null) continue;
            int slot = entry.getInt("slot");
            if (slot < 0 || slot >= inventory.getSize()) continue;

            ItemStack stack = ItemStack.of(LobbyManager.material(entry.getString("material"), Material.PAPER));
            stack.editMeta(meta -> {
                meta.displayName(Text.item(entry.getString("name", id)));
                meta.lore(Text.items(entry.getStringList("lore")));
            });
            inventory.setItem(slot, stack);
            actions.put(slot, entry.getString("action", ""));
        }
    }

    public static void open(LobbyManager lobby, Player player) {
        if (openBedrockForm(lobby, player)) return;
        player.openInventory(new LobbyMenu(lobby).inventory);
    }

    private static boolean openBedrockForm(LobbyManager lobby, Player player) {
        BedrockSupport bedrock = lobby.bedrock();
        if (!bedrock.isAvailable() || !bedrock.isBedrock(player)) return false;

        ConfigurationSection menu = lobby.config().getConfigurationSection("menu");
        if (menu == null) return false;

        List<BedrockSupport.Button> buttons = new ArrayList<>();
        List<String> buttonActions = new ArrayList<>();
        for (String id : lobby.menuEntryIds()) {
            ConfigurationSection entry = menu.getConfigurationSection("entries." + id);
            if (entry == null) continue;
            buttons.add(new BedrockSupport.Button(Text.legacy(entry.getString("name", id)), entry.getString("bedrock-image", "")));
            buttonActions.add(entry.getString("action", ""));
        }

        return bedrock.sendButtonForm(player, Text.legacy(menu.getString("title", "Menu")), "", buttons,
                // 폼 응답은 Geyser 네트워크 스레드에서 오므로 메인 스레드로 넘김
                index -> player.getScheduler().run(lobby.plugin(),
                        task -> lobby.runAction(player, buttonActions.get(index)), null));
    }

    /** 상자 GUI 클릭 처리. */
    public void click(LobbyManager lobby, Player player, int slot) {
        String action = actions.get(slot);
        if (action == null) return;
        player.closeInventory();
        lobby.runAction(player, action);
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }
}
