package com.tangtamukei.lobby;

import org.bukkit.Bukkit;
import org.bukkit.Difficulty;
import org.bukkit.GameRules;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

/** 허공 로비 월드 생성/로드 + 구조물 자동 생성. */
public final class LobbyWorld {

    private static final int STRUCTURE_VERSION = 3; // 2: 광장 + 게임별 섬 4개, 3: 디테일 추가 + 난간 제거

    private final JavaPlugin plugin;
    private final NamespacedKey builtKey;
    private World world;
    private int height;

    public LobbyWorld(JavaPlugin plugin) {
        this.plugin = plugin;
        this.builtKey = new NamespacedKey(plugin, "structure_version");
    }

    /** config 의 lobby-world.enabled 가 true 면 월드를 만들고 구조물을 지음. */
    public void load() {
        ConfigurationSection cfg = plugin.getConfig().getConfigurationSection("lobby-world");
        if (cfg == null || !cfg.getBoolean("enabled", true)) return;

        String name = cfg.getString("name", "lobby");
        height = cfg.getInt("height", 100);

        world = Bukkit.getWorld(name);
        if (world == null) {
            world = new WorldCreator(name)
                    .environment(World.Environment.NORMAL)
                    .generator(new VoidGenerator(height))
                    .generateStructures(false)
                    .createWorld();
        }
        if (world == null) {
            plugin.getLogger().severe("로비 월드를 만들 수 없습니다: " + name);
            return;
        }

        // 항상 낮, 날씨 없음, 몹 없음
        world.setDifficulty(Difficulty.PEACEFUL);
        world.setTime(6000);
        world.setStorm(false);
        world.setGameRule(GameRules.ADVANCE_TIME, false);
        world.setGameRule(GameRules.ADVANCE_WEATHER, false);
        world.setGameRule(GameRules.SPAWN_MOBS, false);
        world.setGameRule(GameRules.SPAWN_MONSTERS, false);
        world.setGameRule(GameRules.SPAWN_PHANTOMS, false);
        world.setGameRule(GameRules.SPAWN_WANDERING_TRADERS, false);
        world.setGameRule(GameRules.SHOW_ADVANCEMENT_MESSAGES, false);
        world.setGameRule(GameRules.IMMEDIATE_RESPAWN, true);
        world.setSpawnLocation(0, height, 0);

        Integer built = world.getPersistentDataContainer().get(builtKey, PersistentDataType.INTEGER);
        if (built == null || built < STRUCTURE_VERSION) {
            rebuild();
        }
    }

    /** 구조물 다시 짓기 (/lobbyrebuild). */
    public boolean rebuild() {
        if (world == null) return false;
        long start = System.currentTimeMillis();
        new LobbyStructure(world, height).build();
        world.getPersistentDataContainer().set(builtKey, PersistentDataType.INTEGER, STRUCTURE_VERSION);
        plugin.getLogger().info("로비 구조물 생성 완료 (" + (System.currentTimeMillis() - start) + "ms)");
        return true;
    }

    public World world() {
        return world;
    }

    public int height() {
        return height;
    }

    /** 구조물 중앙 (북쪽 포탈을 바라봄). */
    public Location defaultSpawn() {
        return world == null ? null : new Location(world, 0.5, height, 0.5, 180f, 0f);
    }
}
