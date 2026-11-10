package com.tangtamukei.game;

import io.papermc.paper.advancement.AdvancementDisplay;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.advancement.Advancement;
import org.bukkit.advancement.AdvancementProgress;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 발전과제 진척 책. 카테고리별 달성 수와 목록(✔ 달성 / ✘ 미달성)을 보여줌.
 * 책을 펼칠 때와 발전과제를 달성할 때마다 내용이 새로 고쳐짐. 베드락(Geyser)에서도 그대로 읽힘.
 */
public final class AdvancementBook {

    private static final Map<String, String> CATEGORIES = new LinkedHashMap<>();
    private static final int PER_PAGE = 10;

    static {
        CATEGORIES.put("story", "마인크래프트");
        CATEGORIES.put("nether", "네더");
        CATEGORIES.put("end", "엔드");
        CATEGORIES.put("adventure", "모험");
        CATEGORIES.put("husbandry", "농사");
    }

    private final NamespacedKey key;

    public AdvancementBook(Plugin plugin) {
        this.key = new NamespacedKey(plugin, "advancement_book");
    }

    public ItemStack create(Player player) {
        ItemStack book = ItemStack.of(Material.WRITTEN_BOOK);
        book.editPersistentDataContainer(pdc -> pdc.set(key, PersistentDataType.BOOLEAN, true));
        fill(book, player);
        return book;
    }

    public boolean isBook(ItemStack item) {
        return item != null && item.getType() == Material.WRITTEN_BOOK
                && item.getPersistentDataContainer().has(key, PersistentDataType.BOOLEAN);
    }

    /** 인벤토리에 있는 진척 책을 모두 최신 내용으로. */
    public void refresh(Player player) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (isBook(item)) fill(item, player);
        }
    }

    public void fill(ItemStack book, Player player) {
        List<Component> pages = pages(player);
        book.editMeta(BookMeta.class, meta -> {
            meta.title(Component.text("발전과제 진척"));
            meta.author(Component.text("TangTaMukei"));
            meta.pages(pages);
        });
    }

    /** 게임 시작 시 이전 판 진척 초기화 (레시피 해금 발전과제는 제외). */
    public static void reset(Player player) {
        for (Iterator<Advancement> it = Bukkit.advancementIterator(); it.hasNext(); ) {
            Advancement adv = it.next();
            if (adv.getKey().getKey().startsWith("recipes/")) continue;
            AdvancementProgress progress = player.getAdvancementProgress(adv);
            for (String c : progress.getAwardedCriteria()) progress.revokeCriteria(c);
        }
    }

    private record Entry(Component title, boolean done, boolean hidden) {
    }

    private static List<Component> pages(Player player) {
        Map<String, List<Entry>> byCategory = new LinkedHashMap<>();
        CATEGORIES.keySet().forEach(c -> byCategory.put(c, new ArrayList<>()));
        for (Iterator<Advancement> it = Bukkit.advancementIterator(); it.hasNext(); ) {
            Advancement adv = it.next();
            if (!adv.getKey().getNamespace().equals(NamespacedKey.MINECRAFT)) continue;
            String path = adv.getKey().getKey();
            int slash = path.indexOf('/');
            if (slash < 0) continue;
            List<Entry> list = byCategory.get(path.substring(0, slash));
            AdvancementDisplay display = adv.getDisplay();
            if (list == null || display == null) continue;
            list.add(new Entry(display.title(), player.getAdvancementProgress(adv).isDone(), display.isHidden()));
        }

        int total = 0, done = 0;
        for (List<Entry> l : byCategory.values()) {
            total += l.size();
            done += (int) l.stream().filter(Entry::done).count();
        }

        List<Component> pages = new ArrayList<>();
        // 첫 장: 요약
        TextComponent.Builder summary = Component.text()
                .append(Component.text("발전과제 진척\n", NamedTextColor.DARK_PURPLE, TextDecoration.BOLD))
                .append(Component.text(player.getName() + "\n\n", NamedTextColor.DARK_GRAY))
                .append(Component.text("전체 ", NamedTextColor.BLACK))
                .append(Component.text(done + " / " + total + "\n", NamedTextColor.DARK_GREEN, TextDecoration.BOLD))
                .append(bar(done, total)).append(Component.newline()).append(Component.newline());
        for (var e : byCategory.entrySet()) {
            int d = (int) e.getValue().stream().filter(Entry::done).count();
            summary.append(Component.text(CATEGORIES.get(e.getKey()) + " ", NamedTextColor.BLACK))
                    .append(Component.text(d + "/" + e.getValue().size() + "\n", d == e.getValue().size() ? NamedTextColor.DARK_GREEN : NamedTextColor.DARK_GRAY));
        }
        pages.add(summary.build());

        // 카테고리별 목록
        for (var e : byCategory.entrySet()) {
            List<Entry> list = e.getValue();
            for (int i = 0; i < list.size(); i += PER_PAGE) {
                TextComponent.Builder page = Component.text()
                        .append(Component.text(CATEGORIES.get(e.getKey()) + (i > 0 ? " (계속)" : "") + "\n\n", NamedTextColor.DARK_PURPLE, TextDecoration.BOLD));
                for (Entry entry : list.subList(i, Math.min(list.size(), i + PER_PAGE))) {
                    if (entry.done()) {
                        page.append(Component.text("✔ ", NamedTextColor.DARK_GREEN)).append(entry.title().color(NamedTextColor.BLACK));
                    } else if (entry.hidden()) {
                        page.append(Component.text("✘ ???", NamedTextColor.GRAY));
                    } else {
                        page.append(Component.text("✘ ", NamedTextColor.GRAY)).append(entry.title().color(NamedTextColor.DARK_GRAY));
                    }
                    page.append(Component.newline());
                }
                pages.add(page.build());
            }
        }
        return pages;
    }

    private static Component bar(int done, int total) {
        int filled = total == 0 ? 0 : Math.round(done * 14f / total);
        return Component.text("|".repeat(filled), NamedTextColor.DARK_GREEN)
                .append(Component.text("|".repeat(14 - filled), NamedTextColor.GRAY));
    }
}
