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
 * 로비 포탈 4개: 북쪽(정면) = 땅따먹기, 나머지 = 개발중.
 * 포탈 위에 큰 글씨를 띄우고, OP 가 땅따먹기 포탈을 우클릭하면 팀 설정 GUI → 시작.
 */
public final class LobbyPortals implements Listener {

    private static final String TAG = "tt_portal_label";
    private static final int DISTANCE = 13; // 로비 구조물 반지름 16 - 3
    /** 북, 동, 남, 서 포탈 가운데 (x, z). 0번이 땅따먹기. */
    private static final int[][] PORTALS = {{0, -DISTANCE}, {DISTANCE, 0}, {0, DISTANCE}, {-DISTANCE, 0}};

    private static final Component DESCRIPTION = Component.text()
            .append(Component.text("팀끼리 땅을 넓히는 서바이벌\n", NamedTextColor.WHITE, TextDecoration.BOLD))
            .append(Component.text("발전과제 → ", NamedTextColor.GRAY)).append(Component.text("[일반 땅]", NamedTextColor.GREEN))
            .append(Component.text(" 도끼로 땅 먹기\n", NamedTextColor.GRAY))
            .append(Component.text("도전 과제 → ", NamedTextColor.GRAY)).append(Component.text("[특수 땅]", NamedTextColor.LIGHT_PURPLE))
            .append(Component.text("으로 남의 땅 뺏기\n", NamedTextColor.GRAY))
            .append(Component.text("내 땅으로 둘러싸면 안쪽도 내 땅!\n", NamedTextColor.GRAY))
            .append(Component.text("관리자가 우클릭하면 시작", NamedTextColor.YELLOW))
            .build();

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
            Location top = new Location(world, PORTALS[i][0] + 0.5, h + 5.7, PORTALS[i][1] + 0.5); // 아치 바로 위
            if (i == 0) {
                // 포탈(폭 5블록)보다 넓게 보이도록 크게
                label(top, Component.text("땅따먹기", NamedTextColor.GOLD, TextDecoration.BOLD), 6.0f);
                // 게임 설명: 포탈 앞쪽(로비 가운데 방향), 포탈 높이쯤 눈높이에
                Location front = new Location(world, PORTALS[i][0] + 0.5, h + 1.9, PORTALS[i][1] + 0.5 + 1.6);
                label(front, DESCRIPTION, 1.1f);
            } else {
                label(top, Component.text("개발중", NamedTextColor.GRAY, TextDecoration.BOLD), 4.0f);
            }
        }
    }

    private void label(Location loc, Component text, float scale) {
        loc.getWorld().spawn(loc, TextDisplay.class, d -> {
            d.text(text);
            d.setBillboard(Display.Billboard.CENTER);
            d.setShadowed(true);
            d.setLineWidth(400);
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
            player.sendMessage(Component.text("개발중인 게임이에요.", NamedTextColor.GRAY));
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
