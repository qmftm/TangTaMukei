package com.tangtamukei.game;

import com.tangtamukei.lobby.BedrockSupport;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * 도전 발전과제 보상: [일반 땅] 5개 또는 [특수 땅] 1개 중 선택.
 * 선택권(네더의 별)을 주고 바로 메뉴를 염. 그냥 닫으면 선택권을 우클릭해 다시 열 수 있음.
 * 자바는 상자 GUI, 베드락(Floodgate)은 버튼 폼.
 */
public final class RewardMenu {

    public static final int NORMAL_AMOUNT = 5;
    private static final int SLOT_NORMAL = 2, SLOT_SPECIAL = 6;

    private final Plugin plugin;
    private final LandToken tokens;
    private final BedrockSupport bedrock;
    private final NamespacedKey key;

    public RewardMenu(Plugin plugin, LandToken tokens, BedrockSupport bedrock) {
        this.plugin = plugin;
        this.tokens = tokens;
        this.bedrock = bedrock;
        this.key = new NamespacedKey(plugin, "challenge_ticket");
    }

    // ───────────── 선택권 ─────────────

    public ItemStack ticket() {
        ItemStack star = ItemStack.of(Material.NETHER_STAR);
        star.editMeta(meta -> {
            meta.displayName(Component.text("도전 보상 선택권", NamedTextColor.LIGHT_PURPLE).decoration(TextDecoration.ITALIC, false));
            meta.lore(List.of(Component.text("우클릭: 일반 땅 " + NORMAL_AMOUNT + "개 / 특수 땅 1개 중 선택", NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false)));
            meta.getPersistentDataContainer().set(key, PersistentDataType.BOOLEAN, true);
        });
        return star;
    }

    public boolean isTicket(ItemStack item) {
        return item != null && item.getType() == Material.NETHER_STAR
                && item.getPersistentDataContainer().has(key, PersistentDataType.BOOLEAN);
    }

    /** 도전 발전과제 달성 시: 선택권 지급 + 메뉴 열기. */
    public void grant(Player player) {
        give(player, ticket());
        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
        open(player);
    }

    // ───────────── 메뉴 ─────────────

    public void open(Player player) {
        if (bedrock.isAvailable() && bedrock.isBedrock(player)) {
            boolean sent = bedrock.sendButtonForm(player, "도전 보상", "보상을 하나 고르세요.",
                    List.of(new BedrockSupport.Button("일반 땅 " + NORMAL_AMOUNT + "개", "textures/items/iron_axe"),
                            new BedrockSupport.Button("특수 땅 1개", "textures/items/netherite_axe")),
                    index -> player.getScheduler().run(plugin, t -> choose(player, index == 1), null));
            if (sent) return;
        }
        player.openInventory(new Menu().inventory);
    }

    /** 선택: 선택권 1개를 쓰고 보상 지급. */
    void choose(Player player, boolean special) {
        if (!consumeTicket(player)) return;
        if (special) {
            give(player, tokens.specialItem());
            player.sendMessage(Component.text("[특수 땅]", NamedTextColor.DARK_PURPLE)
                    .append(Component.text(" 획득 — 다른 팀 땅을 바라보고 버리세요", NamedTextColor.GRAY)));
        } else {
            for (int i = 0; i < NORMAL_AMOUNT; i++) give(player, tokens.item());
            player.sendMessage(Component.text("[일반 땅] " + NORMAL_AMOUNT + "개", NamedTextColor.GREEN)
                    .append(Component.text(" 획득 — 안 먹은 땅을 바라보고 버리세요", NamedTextColor.GRAY)));
        }
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.2f);
    }

    private boolean consumeTicket(Player player) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (isTicket(item)) {
                item.subtract();
                return true;
            }
        }
        return false;
    }

    private static void give(Player player, ItemStack item) {
        player.getInventory().addItem(item).values()
                .forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
    }

    /** 상자 GUI. 클릭 처리는 GameListener 에서. */
    public final class Menu implements InventoryHolder {
        private final Inventory inventory = Bukkit.createInventory(this, 9, Component.text("도전 보상 고르기"));

        Menu() {
            ItemStack normal = tokens.item();
            normal.setAmount(NORMAL_AMOUNT);
            normal.editMeta(m -> m.displayName(Component.text("일반 땅 " + NORMAL_AMOUNT + "개", NamedTextColor.GREEN)
                    .decoration(TextDecoration.ITALIC, false)));
            ItemStack special = tokens.specialItem();
            special.editMeta(m -> m.displayName(Component.text("특수 땅 1개", NamedTextColor.DARK_PURPLE)
                    .decoration(TextDecoration.ITALIC, false)));
            inventory.setItem(SLOT_NORMAL, normal);
            inventory.setItem(SLOT_SPECIAL, special);
        }

        /** 슬롯 클릭. 고르면 true. */
        public boolean click(Player player, int slot) {
            if (slot != SLOT_NORMAL && slot != SLOT_SPECIAL) return false;
            player.closeInventory();
            choose(player, slot == SLOT_SPECIAL);
            return true;
        }

        @Override
        public @NotNull Inventory getInventory() {
            return inventory;
        }
    }
}
