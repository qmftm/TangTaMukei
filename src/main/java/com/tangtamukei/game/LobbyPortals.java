package com.tangtamukei.game;

import com.tangtamukei.lobby.LobbyManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

/**
 * 로비 포탈 4개: 북쪽(정면) = 땅따먹기, 동쪽(빨강) = 하늘에서 용암이 내린다면, 남쪽(하늘색) = 대홍수 (둘 다 개발중), 서쪽 = 개발중.
 * 글씨는 보는 사람을 따라 돌지 않고 광장 쪽을 향해 고정.
 * 포탈 위에 큰 글씨를 띄우고, OP 가 땅따먹기 포탈을 우클릭하면 팀 설정 GUI → 시작.
 */
public final class LobbyPortals implements Listener {

    private static final String TAG = "tt_portal_label";
    private static final int DISTANCE = com.tangtamukei.lobby.LobbyStructure.PORTAL_DISTANCE;
    /** 북, 동, 남, 서 포탈 가운데 (x, z). 0번이 땅따먹기. */
    private static final int[][] PORTALS = {{0, -DISTANCE}, {DISTANCE, 0}, {0, DISTANCE}, {-DISTANCE, 0}};
    /** 개발중인 게임 이름 (포탈 번호 → 이름, 색). 없으면 그냥 "개발중". */
    private static final String[] UPCOMING = {null, "하늘에서 용암이\n내린다면", "대홍수", null};
    private static final NamedTextColor[] UPCOMING_COLOR = {null, NamedTextColor.RED, NamedTextColor.AQUA, null};
    /** 제목 크기 (긴 이름은 두 줄 + 작게 해서 포탈 폭에 맞춤). */
    private static final float[] UPCOMING_SCALE = {0, 3.4f, 6.0f, 0};
    /** 포탈별 글씨 방향: 광장(가운데)을 바라보게. 북 → 남쪽을 봄(0), 동 → 서쪽(90), 남 → 북쪽(180), 서 → 동쪽(-90) */
    private static final float[] FACING = {0f, 90f, 180f, -90f};

    private final Plugin plugin;
    private final LobbyManager lobby;
    private final GameManager game;

    public LobbyPortals(Plugin plugin, LobbyManager lobby, GameManager game) {
        this.plugin = plugin;
        this.lobby = lobby;
        this.game = game;
    }

    private int height() {
        return plugin.getConfig().getInt("lobby-world.height", 100);
    }

    /** 포탈 위 글씨 띄우기 (기존 것은 지우고 새로). */
    public void spawnLabels() {
        World world = lobby.lobbyWorld().world();
        if (world == null) return;
        world.getEntitiesByClass(TextDisplay.class).stream().filter(e -> e.getScoreboardTags().contains(TAG)).forEach(org.bukkit.entity.Entity::remove);
        int h = height();
        for (int i = 0; i < PORTALS.length; i++) {
            Location top = new Location(world, PORTALS[i][0] + 0.5, h + 5.7, PORTALS[i][1] + 0.5, FACING[i], 0f); // 아치 바로 위
            if (i == 0) {
                // 아치 바로 위: 최소 인원 / 예상시간, 그 위에 큰 제목 (포탈 폭 5블록보다 넓게)
                int min = plugin.getConfig().getInt("game.min-players", 2);
                int minutes = plugin.getConfig().getInt("game.expected-minutes", 60);
                label(top, Component.text("최소 " + min + "명\n예상시간: " + minutes + "분", NamedTextColor.WHITE), 1.6f);
                label(top.clone().add(0, 0.95, 0), Component.text("땅따먹기", NamedTextColor.GOLD, TextDecoration.BOLD), 6.0f);
            } else if (UPCOMING[i] != null) {
                // 다음에 만들 게임: 제목 + 아래 작게 "개발중"
                label(top, Component.text("개발중", NamedTextColor.GRAY), 1.8f);
                label(top.clone().add(0, 0.6, 0), Component.text(UPCOMING[i], UPCOMING_COLOR[i], TextDecoration.BOLD), UPCOMING_SCALE[i]);
            } else {
                label(top, Component.text("개발중", NamedTextColor.GRAY, TextDecoration.BOLD), 4.0f);
            }
        }
    }

    private void label(Location loc, Component text, float scale) {
        loc.getWorld().spawn(loc, TextDisplay.class, d -> {
            d.text(text);
            d.setBillboard(Display.Billboard.FIXED); // 시선을 따라 돌지 않음 (위치의 방향으로 고정)
            d.setShadowed(true);
            d.setBackgroundColor(Color.fromARGB(110, 0, 0, 0));
            d.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(),
                    new Vector3f(scale, scale, scale), new AxisAngle4f()));
            d.setPersistent(false);
            d.addScoreboardTag(TAG);
        });
    }

    public void removeLabels() {
        World world = lobby.lobbyWorld().world();
        if (world == null) return;
        world.getEntitiesByClass(TextDisplay.class).stream().filter(e -> e.getScoreboardTags().contains(TAG)).forEach(org.bukkit.entity.Entity::remove);
    }

    /** 클릭한 블록이 어느 포탈 아치인지 (아니면 -1). */
    private int portalAt(Block b) {
        if (!b.getWorld().equals(lobby.lobbyWorld().world())) return -1;
        int h = height();
        if (b.getY() < h - 1 || b.getY() > h + 5) return -1;
        for (int i = 0; i < PORTALS.length; i++) {
            int px = PORTALS[i][0], pz = PORTALS[i][1];
            boolean alongX = pz != 0; // 북·남 포탈은 x축으로 펼쳐짐
            if (alongX ? (b.getZ() == pz && Math.abs(b.getX() - px) <= 2) : (b.getX() == px && Math.abs(b.getZ() - pz) <= 2)) return i;
        }
        return -1;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onClickPortal(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getHand() != EquipmentSlot.HAND || event.getClickedBlock() == null) return;
        int portal = portalAt(event.getClickedBlock());
        if (portal < 0) return;
        event.setCancelled(true);
        Player player = event.getPlayer();
        if (portal != 0) {
            String name = UPCOMING[portal] == null ? null : UPCOMING[portal].replace('\n', ' ');
            player.sendMessage(Component.text(name != null ? "'" + name + "'는 개발중이에요." : "개발중인 게임이에요.", NamedTextColor.GRAY));
            return;
        }
        if (!player.isOp()) {
            player.sendMessage(Component.text("관리자(OP)가 우클릭하면 팀을 정하고 시작해요.", NamedTextColor.YELLOW));
            return;
        }
        if (game.state() == GameManager.State.RUNNING) {
            player.sendMessage(Component.text("이미 게임 중이에요. 끝내려면 /game stop", NamedTextColor.RED));
            return;
        }
        new TeamSetupMenu(game).open(player);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onMenuClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder(false) instanceof TeamSetupMenu menu)) return;
        event.setCancelled(true);
        if (event.getWhoClicked() instanceof Player player && event.getClickedInventory() == event.getView().getTopInventory()) {
            menu.click(player, event.getRawSlot(), event.getClick());
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onMenuDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder(false) instanceof TeamSetupMenu) event.setCancelled(true);
    }
}
