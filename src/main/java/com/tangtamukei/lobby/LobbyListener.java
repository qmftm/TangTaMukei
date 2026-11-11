package com.tangtamukei.lobby;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;

public final class LobbyListener implements Listener {

    private final LobbyManager lobby;

    public LobbyListener(LobbyManager lobby) {
        this.lobby = lobby;
    }

    // ───────────── 접속 / 퇴장 ─────────────

    @EventHandler(priority = EventPriority.HIGH)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        event.joinMessage(joinQuitMessage("join.join-message", player));
        lobby.onJoin(player);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        event.quitMessage(joinQuitMessage("join.quit-message", event.getPlayer()));
        lobby.onQuit(event.getPlayer());
    }

    private net.kyori.adventure.text.Component joinQuitMessage(String path, Player player) {
        String text = lobby.config().getString(path, "");
        return text.isEmpty() ? null : Text.parse(text, "player", player.getName());
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        if (event.getPlayer().getWorld().equals(lobby.spawn().getWorld())) {
            event.setRespawnLocation(lobby.spawn());
            event.getPlayer().getScheduler().run(lobby.plugin(), t -> lobby.giveItems(event.getPlayer()), null);
        }
    }

    // ───────────── 로비 아이템 사용 ─────────────

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        Action a = event.getAction();
        if (a != Action.RIGHT_CLICK_AIR && a != Action.RIGHT_CLICK_BLOCK) return;

        String id = lobby.lobbyItemId(event.getItem());
        if (id == null) return;
        event.setCancelled(true);
        lobby.useItem(event.getPlayer(), id);
    }

    // ───────────── 메뉴 GUI ─────────────

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        if (event.getInventory().getHolder(false) instanceof LobbyMenu menu) {
            event.setCancelled(true);
            if (event.getClickedInventory() == event.getView().getTopInventory()) {
                menu.click(lobby, player, event.getRawSlot());
            }
            return;
        }
        if (lobby.protects(player, "inventory-move")) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder(false) instanceof LobbyMenu
                || (event.getWhoClicked() instanceof Player p && lobby.protects(p, "inventory-move"))) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onSwapHands(PlayerSwapHandItemsEvent event) {
        if (lobby.protects(event.getPlayer(), "inventory-move")) event.setCancelled(true);
    }

    // ───────────── 로비 보호 ─────────────

    @EventHandler(priority = EventPriority.HIGH)
    public void onBreak(BlockBreakEvent event) {
        if (lobby.protects(event.getPlayer(), "block-break")) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlace(BlockPlaceEvent event) {
        if (lobby.protects(event.getPlayer(), "block-place")) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (event.getCause() == EntityDamageEvent.DamageCause.VOID && lobby.isInLobby(player)) {
            event.setCancelled(true);
            player.teleportAsync(lobby.spawn());
            return;
        }
        if (lobby.protects(player, "damage")) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onHunger(FoodLevelChangeEvent event) {
        if (event.getEntity() instanceof Player player && lobby.protects(player, "hunger")) {
            event.setCancelled(true);
            player.setFoodLevel(20);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDrop(PlayerDropItemEvent event) {
        if (lobby.protects(event.getPlayer(), "item-drop")
                || lobby.lobbyItemId(event.getItemDrop().getItemStack()) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player && lobby.protects(player, "item-pickup")) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (!event.hasChangedBlock()) return;
        Player player = event.getPlayer();
        // 로비에는 난간이 없어서, 떨어지면 바로 광장 가운데 스폰으로
        if (event.getTo().getY() < lobby.voidY() && lobby.isInLobby(player)) {
            player.setFallDistance(0);
            player.teleportAsync(lobby.spawn());
        }
    }
}
