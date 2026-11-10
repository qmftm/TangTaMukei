package com.tangtamukei.game;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Chest;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.EnderSignal;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
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
    private final org.bukkit.plugin.Plugin plugin;

    public GameListener(GameManager game, org.bukkit.plugin.Plugin plugin) {
        this.game = game;
        this.plugin = plugin;
    }

    /** 네더 ↔ 오버월드를 같은 좌표로 연결 (바닐라 8:1 무시). */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerPortal(PlayerPortalEvent event) {
        if (event.getCause() == PlayerTeleportEvent.TeleportCause.END_PORTAL) {
            World from = event.getFrom().getWorld();
            if (from.equals(game.overworld())) event.setTo(game.endArrival());           // 게임 엔드로
            else if (from.equals(game.end())) event.setTo(game.respawnFor(event.getPlayer())); // 엔드 탈출
            return;
        }
        if (event.getCause() != PlayerTeleportEvent.TeleportCause.NETHER_PORTAL) return;
        Location to = linked(event.getFrom());
        if (to == null) return;
        event.setTo(to);
        event.setSearchRadius(PORTAL_RADIUS);
        event.setCreationRadius(PORTAL_RADIUS);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityPortal(EntityPortalEvent event) {
        if (event.getPortalType() == org.bukkit.PortalType.ENDER) {
            World from = event.getFrom().getWorld();
            if (from.equals(game.overworld())) event.setTo(game.endArrival());
            else if (from.equals(game.end())) event.setTo(game.overworld().getSpawnLocation());
            return;
        }
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

    // ───────────── 발전과제 책 ─────────────

    /** 책을 펼치기 직전에 최신 진척으로 고침. */
    @EventHandler(priority = EventPriority.LOW)
    public void onOpenBook(PlayerInteractEvent event) {
        Action a = event.getAction();
        if (a != Action.RIGHT_CLICK_AIR && a != Action.RIGHT_CLICK_BLOCK) return;
        if (game.book().isBook(event.getItem())) game.book().fill(event.getItem(), event.getPlayer());
    }

    /** 발전과제 달성: 책 갱신 + 게임 중이면 [일반 땅] 도끼 지급 (레시피 해금 같은 숨은 과제는 제외). */
    @EventHandler
    public void onAdvancement(org.bukkit.event.player.PlayerAdvancementDoneEvent event) {
        Player player = event.getPlayer();
        game.book().refresh(player);
        var adv = event.getAdvancement();
        if (game.teamOf(player) == null || adv.getDisplay() == null || adv.getKey().getKey().startsWith("recipes/")) return;
        // 도전 발전과제: 일반 땅 5개 / 특수 땅 1개 중 선택
        if (adv.getDisplay().frame() == io.papermc.paper.advancement.AdvancementDisplay.Frame.CHALLENGE) {
            game.rewards().grant(player);
            return;
        }
        player.getInventory().addItem(game.landToken().item()).values()
                .forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
        player.sendMessage(net.kyori.adventure.text.Component.text("발전과제 달성! ", net.kyori.adventure.text.format.NamedTextColor.GOLD)
                .append(net.kyori.adventure.text.Component.text("[일반 땅]", net.kyori.adventure.text.format.NamedTextColor.GREEN))
                .append(net.kyori.adventure.text.Component.text(" 획득 — 안 먹은 땅을 바라보고 버리세요", net.kyori.adventure.text.format.NamedTextColor.GRAY)));
    }

    /** 도전 보상 선택권 우클릭: 메뉴 다시 열기. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onTicket(PlayerInteractEvent event) {
        Action a = event.getAction();
        if (a != Action.RIGHT_CLICK_AIR && a != Action.RIGHT_CLICK_BLOCK) return;
        if (!game.rewards().isTicket(event.getItem())) return;
        event.setCancelled(true);
        game.rewards().open(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onRewardClick(org.bukkit.event.inventory.InventoryClickEvent event) {
        if (!(event.getInventory().getHolder(false) instanceof RewardMenu.Menu menu)) return;
        event.setCancelled(true);
        if (event.getWhoClicked() instanceof Player player && event.getClickedInventory() == event.getView().getTopInventory()) {
            menu.click(player, event.getRawSlot());
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onRewardDrag(org.bukkit.event.inventory.InventoryDragEvent event) {
        if (event.getInventory().getHolder(false) instanceof RewardMenu.Menu) event.setCancelled(true);
    }

    /** [특수 땅] 도끼를 버리면: 3칸 안 바라보는 다른 팀 땅을 우리 팀 땅으로. 주인 없는 땅은 불가. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDropSpecial(org.bukkit.event.player.PlayerDropItemEvent event) {
        if (!game.landToken().isSpecial(event.getItemDrop().getItemStack())) return;
        Player player = event.getPlayer();
        GameTeam team = game.teamOf(player);
        int[] target = team != null && player.getWorld().equals(game.overworld())
                ? LandToken.lookEnemy(player, game.territory(), team) : null;
        GameTeam previous = target == null ? null : game.territory().convert(target[0], target[1], team);
        if (previous == null) {
            event.setCancelled(true);
            player.sendActionBar(net.kyori.adventure.text.Component.text(
                    "3칸 안의 다른 팀 땅을 바라보고 버려야 해요 (주인 없는 땅은 안 돼요)", net.kyori.adventure.text.format.NamedTextColor.RED));
            return;
        }
        event.getItemDrop().remove();
        World world = player.getWorld();
        world.playSound(player.getLocation(), Sound.ENTITY_WITHER_SPAWN, 0.6f, 1.6f);
        int cx = Math.floorDiv(target[0], 16) * 16 + 8, cz = Math.floorDiv(target[1], 16) * 16 + 8;
        world.spawnParticle(org.bukkit.Particle.WITCH, cx, world.getHighestBlockYAt(cx, cz) + 1.5, cz, 80, 6, 1, 6);
        for (Player p : org.bukkit.Bukkit.getOnlinePlayers()) {
            GameTeam t = game.teamOf(p);
            if (t == team) p.sendMessage(net.kyori.adventure.text.Component.text(
                    player.getName() + " 님이 " + previous.displayName + " 팀 땅을 빼앗았어요! (" + game.territory().count(team) + "칸)", team.color));
            else if (t == previous) p.sendMessage(net.kyori.adventure.text.Component.text(
                    team.displayName + " 팀이 [특수 땅]으로 우리 땅을 빼앗았어요! (" + cx + ", " + cz + ")", net.kyori.adventure.text.format.NamedTextColor.RED));
        }
    }

    /** [일반 땅] 도끼를 버리면: 3칸 안 바라보는 안 먹은 땅을 우리 팀 땅으로. 대상이 없으면 버리기 취소. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDropToken(org.bukkit.event.player.PlayerDropItemEvent event) {
        if (!game.landToken().isToken(event.getItemDrop().getItemStack())) return;
        Player player = event.getPlayer();
        GameTeam team = game.teamOf(player);
        int[] target = team != null && player.getWorld().equals(game.overworld())
                ? LandToken.lookTarget(player, game.territory()) : null;
        if (target == null || !game.territory().claim(target[0], target[1], team)) {
            event.setCancelled(true);
            player.sendActionBar(net.kyori.adventure.text.Component.text(
                    "3칸 안의 안 먹은 땅을 바라보고 버려야 해요", net.kyori.adventure.text.format.NamedTextColor.RED));
            return;
        }
        event.getItemDrop().remove(); // 도끼 사용
        game.landToken().clear(player);
        int cx = Math.floorDiv(target[0], 16) * 16 + 8, cz = Math.floorDiv(target[1], 16) * 16 + 8;
        World world = player.getWorld();
        world.playSound(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 1f, 1.4f);
        world.spawnParticle(org.bukkit.Particle.HAPPY_VILLAGER, cx, world.getHighestBlockYAt(cx, cz) + 1.5, cz, 60, 6, 1, 6);
        net.kyori.adventure.text.Component msg = net.kyori.adventure.text.Component.text(
                player.getName() + " 님이 땅을 먹었어요! (" + game.territory().count(team) + "칸)", team.color);
        for (Player p : org.bukkit.Bukkit.getOnlinePlayers()) if (game.teamOf(p) == team) p.sendMessage(msg);
    }

    /** 침대가 부서졌거나 엔드에서 나올 때 게임 밖(메인 월드)으로 리스폰되지 않게. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        if (game.teamOf(player) == null) return;
        Location loc = event.getRespawnLocation();
        if (!game.isGameWorld(loc.getWorld()) || loc.getWorld().equals(game.end())) {
            event.setRespawnLocation(game.respawnFor(player));
        }
    }

    // ───────────── 엔더 드래곤 ─────────────

    /** 드래곤 처치: (0, 80, 0) 에 겉날개 상자 + 엔드 게이트웨이 생성 막기. */
    @EventHandler
    public void onDragonDeath(EntityDeathEvent event) {
        if (!(event.getEntity() instanceof EnderDragon) || !event.getEntity().getWorld().equals(game.end())) return;
        World end = game.end();

        Block block = end.getBlockAt(0, 80, 0);
        block.setType(Material.CHEST);
        if (block.getState() instanceof Chest chest) {
            chest.getBlockInventory().addItem(ItemStack.of(Material.ELYTRA));
        }
        end.playSound(block.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 2f, 1f);

        // 게이트웨이는 드래곤 사망 연출(약 10초)이 끝날 때 생김 → 30초 동안 감시해서 제거
        int[] ticks = {0};
        org.bukkit.Bukkit.getScheduler().runTaskTimer(plugin, task -> {
            ticks[0] += 10;
            if (end != game.end() || ticks[0] > 600) {
                task.cancel();
                return;
            }
            if (removeGateways(end)) task.cancel();
        }, 10L, 10L);
    }

    /** 바닐라 게이트웨이 위치 20곳 (반지름 96, y 75) 확인 후 제거. */
    private static boolean removeGateways(World end) {
        boolean removed = false;
        for (int i = 0; i < 20; i++) {
            double angle = 2.0 * (-Math.PI + 0.15707963267948966 * i);
            int x = (int) Math.floor(96.0 * Math.cos(angle));
            int z = (int) Math.floor(96.0 * Math.sin(angle));
            if (end.getBlockAt(x, 75, z).getType() != Material.END_GATEWAY) continue;
            for (int dx = -1; dx <= 1; dx++)
                for (int dy = -2; dy <= 2; dy++)
                    for (int dz = -1; dz <= 1; dz++)
                        end.getBlockAt(x + dx, 75 + dy, z + dz).setType(Material.AIR, false);
            removed = true;
        }
        return removed;
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

    /** 맵 가장자리에서 물/용암이 공허로 흘러나가지 않게, 붙여넣은 청크 경계를 넘지 않게. */
    @EventHandler(ignoreCancelled = true)
    public void onFlow(BlockFromToEvent event) {
        if (!game.isGameWorld(event.getBlock().getWorld())) return;
        var to = event.getToBlock();
        var from = event.getBlock();
        if (!MapLayout.inside(to.getX(), to.getZ())) {
            event.setCancelled(true);
        } else if (from.getWorld().equals(game.overworld())
                && game.differentSource(from.getX(), from.getZ(), to.getX(), to.getZ())) {
            // 다른 곳에서 가져온 청크 경계: 물벽이 그대로 남도록 흐름 차단
            event.setCancelled(true);
        }
    }
}
