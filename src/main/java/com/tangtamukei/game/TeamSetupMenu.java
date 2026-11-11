package com.tangtamukei.game;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 땅따먹기 팀 설정 GUI (OP 전용).
 * 위: 팀 수(2·3·4), 모두 자동으로 / 가운데: 접속자 머리 (좌클릭 다음 팀, 우클릭 이전 팀) / 아래: 닫기, 게임 시작.
 * "자동"인 사람은 시작할 때 인원이 적은 팀부터 채움.
 */
public final class TeamSetupMenu implements InventoryHolder {

    private static final int[] COUNT_SLOTS = {2, 3, 4}; // 2팀, 3팀, 4팀
    private static final int SLOT_AUTO = 6, SLOT_STATUS = 8, SLOT_CLOSE = 45, SLOT_START = 49;
    private static final int HEAD_FIRST = 9, HEAD_LAST = 44;

    private final GameManager game;
    private final Inventory inventory;
    private final Map<UUID, GameTeam> assign = new HashMap<>();
    private final List<UUID> shown = new ArrayList<>();
    private int teamCount;

    public TeamSetupMenu(GameManager game) {
        this.game = game;
        this.inventory = Bukkit.createInventory(this, 54, Component.text("땅따먹기 팀 설정"));
        this.teamCount = Math.clamp(Bukkit.getOnlinePlayers().size(), 2, 4);
        render();
    }

    public void open(Player player) {
        player.openInventory(inventory);
    }

    private void render() {
        inventory.clear();
        ItemStack filler = item(Material.GRAY_STAINED_GLASS_PANE, Component.text(" "), List.of());
        for (int i = 0; i < 9; i++) inventory.setItem(i, filler);
        for (int i = 45; i < 54; i++) inventory.setItem(i, filler);

        for (int i = 0; i < COUNT_SLOTS.length; i++) {
            int n = i + 2;
            boolean selected = n == teamCount;
            ItemStack it = item(selected ? Material.LIME_DYE : Material.GRAY_DYE,
                    Component.text(n + "팀", selected ? NamedTextColor.GREEN : NamedTextColor.GRAY, TextDecoration.BOLD),
                    List.of(Component.text(teams(n), NamedTextColor.GRAY)));
            it.setAmount(n);
            inventory.setItem(COUNT_SLOTS[i], it);
        }
        inventory.setItem(SLOT_AUTO, item(Material.HOPPER, Component.text("모두 자동으로", NamedTextColor.YELLOW),
                List.of(Component.text("정해둔 팀을 모두 지우고 자동 배정", NamedTextColor.GRAY))));
        inventory.setItem(SLOT_STATUS, item(Material.FILLED_MAP, Component.text("맵: " + status(), NamedTextColor.AQUA),
                List.of(Component.text("준비 중이면 끝나는 대로 시작해요", NamedTextColor.GRAY))));

        shown.clear();
        int slot = HEAD_FIRST;
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (slot > HEAD_LAST) break;
            shown.add(p.getUniqueId());
            inventory.setItem(slot++, head(p));
        }

        inventory.setItem(SLOT_CLOSE, item(Material.BARRIER, Component.text("닫기", NamedTextColor.RED), List.of()));
        inventory.setItem(SLOT_START, item(Material.LIME_CONCRETE, Component.text("게임 시작", NamedTextColor.GREEN, TextDecoration.BOLD),
                List.of(Component.text(teamCount + "팀, " + Bukkit.getOnlinePlayers().size() + "명", NamedTextColor.GRAY))));
    }

    private ItemStack head(Player p) {
        GameTeam t = assign.get(p.getUniqueId());
        ItemStack skull = ItemStack.of(Material.PLAYER_HEAD);
        skull.editMeta(SkullMeta.class, meta -> {
            meta.setOwningPlayer(p);
            meta.displayName(Component.text(p.getName(), t == null ? NamedTextColor.WHITE : t.color)
                    .decoration(TextDecoration.ITALIC, false));
            meta.lore(List.of(
                    Component.text("팀: ", NamedTextColor.GRAY).append(t == null
                            ? Component.text("자동", NamedTextColor.WHITE)
                            : Component.text(t.displayName, t.color)).decoration(TextDecoration.ITALIC, false),
                    Component.text("좌클릭: 다음 팀 / 우클릭: 이전 팀", NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false)));
        });
        return skull;
    }

    /** 클릭 처리: 팀 수, 모두 자동, 머리(팀 바꾸기), 닫기, 시작. */
    public void click(Player op, int slot, ClickType type) {
        for (int i = 0; i < COUNT_SLOTS.length; i++) {
            if (slot == COUNT_SLOTS[i]) {
                teamCount = i + 2;
                List<GameTeam> usable = List.of(GameTeam.forCount(teamCount));
                assign.values().removeIf(t -> !usable.contains(t)); // 없어진 팀은 자동으로
                render();
                return;
            }
        }
        if (slot == SLOT_AUTO) {
            assign.clear();
            render();
        } else if (slot == SLOT_CLOSE) {
            op.closeInventory();
        } else if (slot == SLOT_START) {
            op.closeInventory();
            if (!game.start(teamCount, assign)) op.sendMessage(Component.text("이미 게임 중이에요.", NamedTextColor.RED));
        } else if (slot >= HEAD_FIRST && slot <= HEAD_LAST && slot - HEAD_FIRST < shown.size()) {
            UUID id = shown.get(slot - HEAD_FIRST);
            List<GameTeam> cycle = new ArrayList<>();
            cycle.add(null); // 자동
            cycle.addAll(List.of(GameTeam.forCount(teamCount)));
            int i = cycle.indexOf(assign.get(id));
            int next = Math.floorMod(i + (type.isRightClick() ? -1 : 1), cycle.size());
            if (cycle.get(next) == null) assign.remove(id);
            else assign.put(id, cycle.get(next));
            render();
        }
    }

    private String status() {
        return switch (game.state()) {
            case READY -> "준비 완료";
            case PREPARING -> "준비 중";
            case IDLE -> "없음 (시작하면 만들어요)";
            case RUNNING -> "게임 중";
        };
    }

    private static String teams(int n) {
        StringBuilder sb = new StringBuilder();
        for (GameTeam t : GameTeam.forCount(n)) sb.append(sb.isEmpty() ? "" : " · ").append(t.displayName);
        return sb.toString();
    }

    private static ItemStack item(Material m, Component name, List<Component> lore) {
        ItemStack it = ItemStack.of(m);
        it.editMeta(meta -> {
            meta.displayName(name.decoration(TextDecoration.ITALIC, false));
            meta.lore(lore.stream().map(c -> c.decoration(TextDecoration.ITALIC, false)).toList());
        });
        return it;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }
}
