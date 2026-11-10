package com.tangtamukei.game;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

/**
 * [일반 땅] 도끼와 안 먹은 땅 처리.
 * - 발전과제를 깰 때마다 부서지지 않는 철 도끼 [일반 땅] 지급
 * - REACH 칸 안의 안 먹은 땅을 바라보고 버리면 그 청크를 우리 팀 땅으로
 * - REACH 칸 안에 안 먹은 땅이 있으면 그곳에 빨간 색유리 낙하 블록 3x3 표시 (본인에게만 보임)
 * - 안 먹은 땅에 들어가면 시듦 255 + 허기 255
 * - [특수 땅] 네더라이트 도끼: REACH 칸 안의 다른 팀 땅을 바라보고 버리면 우리 팀 땅으로 (주인 없는 땅은 불가)
 */
public final class LandToken {

    public static final double REACH = 3.0;
    private static final int PENALTY_LEVEL = 254; // 효과 레벨 255
    private static final BlockData MARKER = Material.RED_STAINED_GLASS.createBlockData();
    private static final int MARKER_LIFETIME = 1200; // 1분마다 새로 만듦 (사라지면 매 틱 확인해서 바로 다시)

    private final Plugin plugin;
    private final NamespacedKey key;
    private final NamespacedKey specialKey;
    private final Map<UUID, Marker> markers = new HashMap<>();
    private int tick;

    private record Marker(int x, int z, List<FallingBlock> blocks, int createdAt) {
    }

    public LandToken(Plugin plugin) {
        this.plugin = plugin;
        this.key = new NamespacedKey(plugin, "land_token");
        this.specialKey = new NamespacedKey(plugin, "special_land_token");
    }

    // ───────────── 아이템 ─────────────

    public ItemStack item() {
        ItemStack axe = ItemStack.of(Material.IRON_AXE);
        axe.editMeta(meta -> {
            meta.displayName(Component.text("[일반 땅]", NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false));
            meta.lore(List.of(
                    Component.text("3칸 안의 안 먹은 땅을 바라보고", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                    Component.text("버리면(Q) 우리 팀 땅이 돼요", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)));
            meta.setUnbreakable(true);
            meta.getPersistentDataContainer().set(key, PersistentDataType.BOOLEAN, true);
        });
        return axe;
    }

    public boolean isToken(ItemStack item) {
        return item != null && item.getType() == Material.IRON_AXE
                && item.getPersistentDataContainer().has(key, PersistentDataType.BOOLEAN);
    }

    public ItemStack specialItem() {
        ItemStack axe = ItemStack.of(Material.NETHERITE_AXE);
        axe.editMeta(meta -> {
            meta.displayName(Component.text("[특수 땅]", NamedTextColor.DARK_PURPLE).decoration(TextDecoration.ITALIC, false));
            meta.lore(List.of(
                    Component.text("3칸 안의 다른 팀 땅을 바라보고", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                    Component.text("버리면(Q) 우리 팀 땅으로 바꿔요", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                    Component.text("주인 없는 땅에는 못 써요", NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false)));
            meta.setUnbreakable(true);
            meta.getPersistentDataContainer().set(specialKey, PersistentDataType.BOOLEAN, true);
        });
        return axe;
    }

    public boolean isSpecial(ItemStack item) {
        return item != null && item.getType() == Material.NETHERITE_AXE
                && item.getPersistentDataContainer().has(specialKey, PersistentDataType.BOOLEAN);
    }

    // ───────────── 바라보는 땅 ─────────────

    /** 시선 방향으로 REACH 칸 안에서 처음 만나는 다른 팀 땅 위치 {x, z}. 없으면 null. */
    public static int[] lookEnemy(Player player, Territory territory, GameTeam team) {
        return look(player, (x, z) -> {
            GameTeam owner = territory.ownerAt(x, z);
            return owner != null && owner != team;
        });
    }

    /** 시선 방향으로 REACH 칸 안에서 처음 만나는 안 먹은 땅 위치 {x, z}. 없으면 null. */
    public static int[] lookTarget(Player player, Territory territory) {
        return look(player, territory::isUnclaimed);
    }

    private static int[] look(Player player, java.util.function.BiPredicate<Integer, Integer> test) {
        Location eye = player.getEyeLocation();
        Vector dir = eye.getDirection();
        Vector flat = new Vector(dir.getX(), 0, dir.getZ());
        if (flat.lengthSquared() < 1e-4) return null; // 바로 위/아래를 보면 없음
        flat.normalize();
        for (double d = 0.5; d <= REACH; d += 0.25) {
            int x = (int) Math.floor(eye.getX() + flat.getX() * d), z = (int) Math.floor(eye.getZ() + flat.getZ() * d);
            if (test.test(x, z)) return new int[]{x, z};
        }
        return null;
    }

    /** REACH 칸 안에서 가장 가까운 안 먹은 땅 위치 {x, z}. 없으면 null. */
    private static int[] nearest(Player player, Territory territory) {
        Location l = player.getLocation();
        int[] best = null;
        double bestD = Double.MAX_VALUE;
        int r = (int) Math.ceil(REACH);
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                int x = l.getBlockX() + dx, z = l.getBlockZ() + dz;
                double d = dx * dx + dz * dz;
                if (d > REACH * REACH || d >= bestD || !territory.isUnclaimed(x, z)) continue;
                bestD = d;
                best = new int[]{x, z};
            }
        }
        return best;
    }

    // ───────────── 매 틱: 표시 (가까이 있는 동안 계속), 10틱마다: 페널티 ─────────────

    public void tick(World world, Territory territory, Function<Player, GameTeam> teamOf) {
        tick++;
        boolean penaltyTick = tick % 10 == 0;
        for (Player p : world.getPlayers()) {
            if (teamOf.apply(p) == null) {
                clear(p);
                continue;
            }
            Location l = p.getLocation();
            boolean inUnclaimed = territory.isUnclaimed(l.getBlockX(), l.getBlockZ());
            if (penaltyTick && inUnclaimed) {
                p.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, 40, PENALTY_LEVEL, false, true, true));
                p.addPotionEffect(new PotionEffect(PotionEffectType.HUNGER, 40, PENALTY_LEVEL, false, true, true));
                p.sendActionBar(Component.text("안 먹은 땅이에요! 빨리 나가세요", NamedTextColor.RED));
            } else if (penaltyTick) {
                removePenalty(p);
            }

            int[] target = inUnclaimed ? null : lookTarget(p, territory);
            if (target == null && !inUnclaimed) target = nearest(p, territory);
            if (target == null) clear(p);
            else show(p, world, territory, target[0], target[1]);
        }
        // 게임 월드를 떠난 플레이어 표시 정리
        markers.keySet().removeIf(id -> {
            Player p = plugin.getServer().getPlayer(id);
            if (p != null && p.getWorld().equals(world)) return false;
            Marker m = markers.get(id);
            m.blocks().forEach(org.bukkit.entity.Entity::remove);
            return true;
        });
    }

    private static void removePenalty(Player p) {
        for (PotionEffectType type : new PotionEffectType[]{PotionEffectType.WITHER, PotionEffectType.HUNGER}) {
            PotionEffect e = p.getPotionEffect(type);
            if (e != null && e.getAmplifier() == PENALTY_LEVEL) p.removePotionEffect(type);
        }
    }

    /** 안 먹은 땅 (x, z) 둘레 3x3 에 빨간 색유리 낙하 블록 (본인에게만). */
    private void show(Player player, World world, Territory territory, int x, int z) {
        Marker old = markers.get(player.getUniqueId());
        if (old != null && old.x() == x && old.z() == z && tick - old.createdAt() < MARKER_LIFETIME
                && old.blocks().stream().allMatch(org.bukkit.entity.Entity::isValid)) return;
        clear(player);
        List<FallingBlock> blocks = new ArrayList<>();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                int bx = x + dx, bz = z + dz;
                if (!territory.isUnclaimed(bx, bz)) continue; // 안 먹은 땅 칸에만
                // 지면에서 살짝 띄워 땅과 구분되게
                Location loc = new Location(world, bx + 0.5, world.getHighestBlockYAt(bx, bz) + 1.15, bz + 0.5);
                FallingBlock fb = world.spawn(loc, FallingBlock.class, f -> {
                    f.setBlockData(MARKER);
                    f.setGravity(false);
                    f.shouldAutoExpire(false);
                    f.setDropItem(false);
                    f.setCancelDrop(true);
                    f.setHurtEntities(false);
                    f.setInvulnerable(true);
                    f.setPersistent(false);
                    f.setSilent(true);
                    f.setVisibleByDefault(false);
                });
                player.showEntity(plugin, fb);
                blocks.add(fb);
            }
        }
        markers.put(player.getUniqueId(), new Marker(x, z, blocks, tick));
    }

    public void clear(Player player) {
        Marker m = markers.remove(player.getUniqueId());
        if (m != null) m.blocks().forEach(org.bukkit.entity.Entity::remove);
    }

    public void clearAll() {
        markers.values().forEach(m -> m.blocks().forEach(org.bukkit.entity.Entity::remove));
        markers.clear();
        for (Player p : plugin.getServer().getOnlinePlayers()) removePenalty(p);
    }
}
