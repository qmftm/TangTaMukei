package com.tangtamukei.game;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.EnderSignal;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.entity.EntityPortalEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

public final class GameListener implements Listener {

    /** 1:1 포탈이라 탐색 범위를 작게 (바닐라 오버월드 기본 128). */
    private static final int PORTAL_RADIUS = 16;

    private final GameManager game;

    public GameListener(GameManager game) {
        this.game = game;
    }

    /** 네더 ↔ 오버월드를 같은 좌표로 연결 (바닐라 8:1 무시). */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerPortal(PlayerPortalEvent event) {
        if (event.getCause() != PlayerTeleportEvent.TeleportCause.NETHER_PORTAL) return;
        Location to = linked(event.getFrom());
        if (to == null) return;
        event.setTo(to);
        event.setSearchRadius(PORTAL_RADIUS);
        event.setCreationRadius(PORTAL_RADIUS);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityPortal(EntityPortalEvent event) {
        if (event.getPortalType() != org.bukkit.PortalType.NETHER) return;
        Location to = linked(event.getFrom());
        if (to == null) return;
        event.setTo(to);
        event.setSearchRadius(PORTAL_RADIUS);
        event.setCreationRadius(PORTAL_RADIUS);
    }

    private Location linked(Location from) {
        World world = from.getWorld();
        World target;
        if (world.equals(game.overworld())) target = game.nether();
        else if (world.equals(game.nether())) target = game.overworld();
        else return null;
        if (target == null) return null;

        // 월드보더 안쪽으로 제한
        double limit = MapLayout.MAX - 8;
        double x = Math.clamp(from.getX(), -limit, limit);
        double z = Math.clamp(from.getZ(), -limit, limit);
        double y = Math.clamp(from.getY(), target.getMinHeight() + 8, target.getEnvironment() == World.Environment.NETHER ? 118 : 250);
        return new Location(target, x, y, z, from.getYaw(), from.getPitch());
    }

    // ───────────── 엔더의 눈 → 맵 중앙 요새 ─────────────

    @EventHandler(priority = EventPriority.HIGH)
    public void onEyeOfEnder(PlayerInteractEvent event) {
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return;
        ItemStack item = event.getItem();
        if (item == null || item.getType() != Material.ENDER_EYE) return;
        Player player = event.getPlayer();
        if (!player.getWorld().equals(game.overworld())) return;
        // 엔드 포탈 틀에 끼우는 건 바닐라 그대로
        if (action == Action.RIGHT_CLICK_BLOCK && event.getClickedBlock() != null
                && event.getClickedBlock().getType() == Material.END_PORTAL_FRAME) return;

        event.setUseItemInHand(Event.Result.DENY);
        event.setCancelled(true);
        Location target = game.strongholdTarget();
        if (target == null) return;

        World world = player.getWorld();
        Location start = player.getEyeLocation();
        world.spawn(start, EnderSignal.class, signal -> {
            signal.setTargetLocation(target);
            signal.setDropItem(ThreadLocalRandom.current().nextInt(5) > 0); // 바닐라처럼 80% 확률로 떨어짐
        });
        world.playSound(start, Sound.ENTITY_ENDER_EYE_LAUNCH, 0.5f, 0.4f);
        player.swingHand(event.getHand());
        if (player.getGameMode() != GameMode.CREATIVE) item.subtract();
    }

    // ───────────── 구조물 몹 ─────────────
    // 직접 놓은 구조물은 바닐라가 "구조물 안"으로 인식하지 않아서
    // 요새(위더 스켈레톤·블레이즈)와 해저 신전(가디언) 몹을 대신 스폰

    private static final EntityType[] FORTRESS_MOBS = {
            EntityType.BLAZE, EntityType.BLAZE, EntityType.BLAZE, EntityType.BLAZE, EntityType.BLAZE,
            EntityType.WITHER_SKELETON, EntityType.WITHER_SKELETON, EntityType.WITHER_SKELETON, EntityType.WITHER_SKELETON,
            EntityType.ZOMBIFIED_PIGLIN, EntityType.ZOMBIFIED_PIGLIN, EntityType.MAGMA_CUBE, EntityType.SKELETON,
    };
    private static final Set<Material> MONUMENT_BLOCKS = Set.of(
            Material.PRISMARINE, Material.PRISMARINE_BRICKS, Material.DARK_PRISMARINE, Material.SEA_LANTERN);

    @EventHandler(ignoreCancelled = true)
    public void onNaturalSpawn(CreatureSpawnEvent event) {
        if (event.getSpawnReason() != CreatureSpawnEvent.SpawnReason.NATURAL) return;
        Location loc = event.getLocation();
        World world = loc.getWorld();
        Block below = loc.getBlock().getRelative(BlockFace.DOWN);

        if (world.equals(game.nether()) && below.getType() == Material.NETHER_BRICKS) {
            EntityType type = FORTRESS_MOBS[ThreadLocalRandom.current().nextInt(FORTRESS_MOBS.length)];
            if (event.getEntityType() == type) return;
            event.setCancelled(true);
            world.spawnEntity(loc, type, CreatureSpawnEvent.SpawnReason.CUSTOM);
        } else if (world.equals(game.overworld())
                && game.layout().at(loc.getBlockX(), loc.getBlockZ()) == CellType.MONUMENT
                && loc.getBlock().getType() == Material.WATER
                && event.getEntityType() != EntityType.GUARDIAN) {
            for (int dy = 1; dy <= 4; dy++) {
                if (MONUMENT_BLOCKS.contains(loc.getBlock().getRelative(0, -dy, 0).getType())) {
                    event.setCancelled(true);
                    world.spawnEntity(loc, EntityType.GUARDIAN, CreatureSpawnEvent.SpawnReason.CUSTOM);
                    return;
                }
            }
        }
    }

    /** 맵 가장자리에서 물/용암이 공허로 흘러나가지 않게. */
    @EventHandler(ignoreCancelled = true)
    public void onFlow(BlockFromToEvent event) {
        if (!game.isGameWorld(event.getBlock().getWorld())) return;
        var to = event.getToBlock();
        if (!MapLayout.inside(to.getX(), to.getZ())) event.setCancelled(true);
    }
}
