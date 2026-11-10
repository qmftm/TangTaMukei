package com.tangtamukei.lobby;

import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import net.kyori.adventure.title.Title;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

public final class LobbyManager {

    private final JavaPlugin plugin;
    private final BedrockSupport bedrock;
    private final NamespacedKey itemKey;
    private final Set<UUID> hidingOthers = new HashSet<>();

    public LobbyManager(JavaPlugin plugin, BedrockSupport bedrock) {
        this.plugin = plugin;
        this.bedrock = bedrock;
        this.itemKey = new NamespacedKey(plugin, "lobby_item");
    }

    public JavaPlugin plugin() {
        return plugin;
    }

    public FileConfiguration config() {
        return plugin.getConfig();
    }

    public BedrockSupport bedrock() {
        return bedrock;
    }

    // ───────────── 스폰 ─────────────

    public Location spawn() {
        ConfigurationSection s = config().getConfigurationSection("spawn");
        String worldName = s == null ? "" : s.getString("world", "");
        World world = worldName.isEmpty() ? null : Bukkit.getWorld(worldName);
        if (world == null) {
            return Bukkit.getWorlds().getFirst().getSpawnLocation().toCenterLocation();
        }
        return new Location(world, s.getDouble("x"), s.getDouble("y"), s.getDouble("z"),
                (float) s.getDouble("yaw"), (float) s.getDouble("pitch"));
    }

    public void setSpawn(Location loc) {
        config().set("spawn.world", loc.getWorld().getName());
        config().set("spawn.x", loc.getX());
        config().set("spawn.y", loc.getY());
        config().set("spawn.z", loc.getZ());
        config().set("spawn.yaw", (double) loc.getYaw());
        config().set("spawn.pitch", (double) loc.getPitch());
        plugin.saveConfig();
    }

    public boolean isInLobby(Player player) {
        return player.getWorld().equals(spawn().getWorld());
    }

    /** 크리에이티브 + 권한이 있으면 보호 무시 (로비 꾸미기용). */
    public boolean canBypass(Player player) {
        return player.getGameMode() == GameMode.CREATIVE && player.hasPermission("tangtamukei.build");
    }

    public boolean protects(Player player, String rule) {
        return config().getBoolean("protection." + rule, true) && isInLobby(player) && !canBypass(player);
    }

    // ───────────── 접속 처리 ─────────────

    public void onJoin(Player player) {
        ConfigurationSection join = config().getConfigurationSection("join");
        if (join == null) return;

        if (join.getBoolean("teleport-to-spawn", true)) {
            player.teleportAsync(spawn());
        }
        preparePlayer(player);

        String name = player.getName();
        String title = join.getString("welcome-title", "");
        String subtitle = join.getString("welcome-subtitle", "");
        if (!title.isEmpty() || !subtitle.isEmpty()) {
            player.showTitle(Title.title(Text.parse(title, "player", name), Text.parse(subtitle, "player", name)));
        }
        for (String line : join.getStringList("welcome-lines")) {
            player.sendMessage(Text.parse(line, "player", name));
        }
        playSound(player, join.getString("sound", ""));

        // 이미 "플레이어 숨김"을 켠 사람에게는 새로 들어온 사람도 숨김
        for (UUID id : hidingOthers) {
            Player viewer = Bukkit.getPlayer(id);
            if (viewer != null && !viewer.equals(player)) viewer.hidePlayer(plugin, player);
        }
    }

    public void onQuit(Player player) {
        hidingOthers.remove(player.getUniqueId());
    }

    /** 로비 상태로 초기화: 게임모드, 체력, 인벤토리, 로비 아이템. */
    public void preparePlayer(Player player) {
        ConfigurationSection join = config().getConfigurationSection("join");
        if (join == null) return;

        try {
            player.setGameMode(GameMode.valueOf(join.getString("gamemode", "ADVENTURE").toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("잘못된 gamemode: " + join.getString("gamemode"));
        }
        if (join.getBoolean("heal", true)) {
            var maxHealth = player.getAttribute(Attribute.MAX_HEALTH);
            player.setHealth(maxHealth != null ? maxHealth.getValue() : 20.0);
            player.setFoodLevel(20);
            player.setSaturation(20f);
            player.setFireTicks(0);
            player.clearActivePotionEffects();
        }
        if (join.getBoolean("clear-inventory", true)) {
            player.getInventory().clear();
        }
        giveItems(player);
    }

    // ───────────── 로비 아이템 ─────────────

    public void giveItems(Player player) {
        ConfigurationSection items = config().getConfigurationSection("items");
        if (items == null) return;
        for (String id : items.getKeys(false)) {
            ConfigurationSection item = items.getConfigurationSection(id);
            if (item == null) continue;
            player.getInventory().setItem(item.getInt("slot"), buildItem(id, item, hidingOthers.contains(player.getUniqueId())));
        }
    }

    private ItemStack buildItem(String id, ConfigurationSection item, boolean hiddenState) {
        boolean visibilityItem = "visibility".equals(item.getString("action"));
        String materialName = visibilityItem && hiddenState ? item.getString("hidden-material", item.getString("material")) : item.getString("material");
        String name = visibilityItem && hiddenState ? item.getString("hidden-name", item.getString("name")) : item.getString("name");

        ItemStack stack = ItemStack.of(material(materialName, Material.PAPER));
        stack.editMeta(meta -> {
            meta.displayName(Text.item(name));
            meta.lore(Text.items(item.getStringList("lore")));
            meta.getPersistentDataContainer().set(itemKey, PersistentDataType.STRING, id);
        });
        return stack;
    }

    /** 로비 아이템이면 그 아이템 id, 아니면 null. */
    public String lobbyItemId(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        return stack.getPersistentDataContainer().get(itemKey, PersistentDataType.STRING);
    }

    public void useItem(Player player, String id) {
        String action = config().getString("items." + id + ".action", "");
        switch (action) {
            case "menu" -> LobbyMenu.open(this, player);
            case "visibility" -> toggleVisibility(player);
            default -> runAction(player, action);
        }
    }

    // ───────────── 플레이어 숨기기 ─────────────

    public void toggleVisibility(Player player) {
        boolean hide = hidingOthers.add(player.getUniqueId());
        if (!hide) hidingOthers.remove(player.getUniqueId());

        for (Player other : Bukkit.getOnlinePlayers()) {
            if (other.equals(player)) continue;
            if (hide) player.hidePlayer(plugin, other);
            else player.showPlayer(plugin, other);
        }
        giveItems(player);
        message(player, hide ? "players-hidden" : "players-shown");
        playSound(player, "UI_BUTTON_CLICK");
    }

    // ───────────── 액션 ─────────────

    /** server:이름 / command:명령어 / message:텍스트 */
    public void runAction(Player player, String action) {
        if (action == null || action.isBlank()) return;
        int colon = action.indexOf(':');
        String type = colon < 0 ? action : action.substring(0, colon);
        String value = colon < 0 ? "" : action.substring(colon + 1).trim();

        switch (type.toLowerCase(Locale.ROOT)) {
            case "server" -> connect(player, value);
            case "command" -> player.performCommand(value.startsWith("/") ? value.substring(1) : value);
            case "message" -> player.sendMessage(Text.parse(value, "player", player.getName()));
            default -> plugin.getLogger().warning("알 수 없는 action: " + action);
        }
    }

    public void connect(Player player, String server) {
        message(player, "connecting", "server", server);
        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        out.writeUTF("Connect");
        out.writeUTF(server);
        player.sendPluginMessage(plugin, "BungeeCord", out.toByteArray());
    }

    public void teleportToLobby(Player player) {
        player.teleportAsync(spawn()).thenAccept(ok -> {
            if (ok) {
                preparePlayer(player);
                message(player, "teleported");
            }
        });
    }

    // ───────────── 유틸 ─────────────

    public void message(Player player, String key, String... placeholders) {
        String text = config().getString("messages." + key, "");
        if (text.isEmpty()) return;
        player.sendMessage(Text.parse(config().getString("messages.prefix", "") + text, placeholders));
    }

    public void message(org.bukkit.command.CommandSender sender, String key) {
        String text = config().getString("messages." + key, "");
        if (!text.isEmpty()) sender.sendMessage(Text.parse(config().getString("messages.prefix", "") + text));
    }

    public void playSound(Player player, String soundName) {
        if (soundName == null || soundName.isBlank()) return;
        Sound sound = null;
        try {
            // ENTITY_PLAYER_LEVELUP 같은 상수 이름
            sound = (Sound) Sound.class.getField(soundName.toUpperCase(Locale.ROOT)).get(null);
        } catch (ReflectiveOperationException | ClassCastException ignored) {
            // minecraft:entity.player.levelup 같은 키
            NamespacedKey key = NamespacedKey.fromString(soundName.toLowerCase(Locale.ROOT));
            if (key != null) sound = Registry.SOUNDS.get(key);
        }
        if (sound != null) player.playSound(player.getLocation(), sound, 1f, 1f);
    }

    public static Material material(String name, Material fallback) {
        if (name == null) return fallback;
        Material m = Material.matchMaterial(name);
        return m != null && m.isItem() ? m : fallback;
    }

    public List<String> menuEntryIds() {
        ConfigurationSection entries = config().getConfigurationSection("menu.entries");
        return entries == null ? List.of() : List.copyOf(entries.getKeys(false));
    }
}
