package com.tangtamukei.game;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.MapCanvas;
import org.bukkit.map.MapCursor;
import org.bukkit.map.MapCursorCollection;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;

import java.awt.Color;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

/**
 * 게임 지도: 맵 전체(256x256 = 지도 128x128, 2블록당 1픽셀)를 청크마다 바둑판으로 구분해서 그리고,
 * 점령한 청크는 그 팀 색으로 칠함. 팀원 위치는 팀 색 깃발로 표시 (자기 위치는 기본 화살표).
 */
public final class GameMap {

    private final int[] terrain = new int[128 * 128]; // 지형 색 (RGB)
    private Territory territory;
    private Function<Player, GameTeam> teamOf;
    private MapView view;

    /** 오버월드가 만들어진 직후(청크가 로드돼 있을 때) 지형 색을 떠 둠. */
    public void captureTerrain(World world) {
        for (int px = 0; px < 128; px++) {
            for (int pz = 0; pz < 128; pz++) {
                int x = MapLayout.MIN + px * 2, z = MapLayout.MIN + pz * 2;
                Block b = world.getHighestBlockAt(x, z);
                int rgb;
                try {
                    rgb = b.getType().isAir() ? 0x101018 : b.getBlockData().getMapColor().asRGB();
                } catch (RuntimeException e) {
                    rgb = 0x808080;
                }
                // 높이에 따라 살짝 음영 (북쪽보다 높으면 밝게)
                int north = world.getHighestBlockYAt(x, z - 2);
                double f = b.getY() > north ? 1.08 : b.getY() < north ? 0.88 : 1.0;
                terrain[pz * 128 + px] = scale(rgb, f);
            }
        }
    }

    /** 게임 시작 시 지도 생성. */
    public void start(World world, Territory territory, Function<Player, GameTeam> teamOf) {
        this.territory = territory;
        this.teamOf = teamOf;
        view = Bukkit.createMap(world);
        view.setCenterX(0);
        view.setCenterZ(0);
        view.setScale(MapView.Scale.NORMAL);
        view.setTrackingPosition(true);
        view.setUnlimitedTracking(false);
        view.addRenderer(new Renderer());
    }

    public ItemStack item() {
        ItemStack stack = ItemStack.of(Material.FILLED_MAP);
        stack.editMeta(MapMeta.class, meta -> {
            meta.setMapView(view);
            meta.displayName(Component.text("땅따먹기 지도").decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
        });
        return stack;
    }

    public boolean ready() {
        return view != null;
    }

    private final class Renderer extends MapRenderer {
        private final Map<UUID, Integer> drawn = new HashMap<>();

        Renderer() {
            super(true); // 플레이어마다 다른 화면 (팀원 표시)
        }

        @Override
        public void render(MapView map, MapCanvas canvas, Player player) {
            int version = territory.version();
            Integer last = drawn.get(player.getUniqueId());
            if (last == null || last != version) {
                draw(canvas);
                drawn.put(player.getUniqueId(), version);
            }
            canvas.setCursors(teammates(player));
        }

        private void draw(MapCanvas canvas) {
            for (int px = 0; px < 128; px++) {
                for (int pz = 0; pz < 128; pz++) {
                    int c = px >> 3, r = pz >> 3;
                    int rgb = terrain[pz * 128 + px];
                    GameTeam owner = territory.owner(c, r);
                    boolean edge = (px & 7) == 0 || (pz & 7) == 0 || (px & 7) == 7 || (pz & 7) == 7;
                    // 바둑판: 청크마다 번갈아 밝기
                    if (((c + r) & 1) == 0) rgb = scale(rgb, 0.9);
                    if (owner != null) {
                        // 점령한 청크: 팀 색으로 칠하고 테두리는 진한 팀 색
                        rgb = edge ? scale(teamRgb(owner), 0.8) : mix(rgb, teamRgb(owner), 0.6);
                    } else if ((px & 7) == 0 || (pz & 7) == 0) {
                        rgb = scale(rgb, 0.72); // 청크 경계선
                    }
                    canvas.setPixelColor(px, pz, new Color(rgb));
                }
            }
        }

        private MapCursorCollection teammates(Player viewer) {
            MapCursorCollection cursors = new MapCursorCollection();
            GameTeam team = teamOf.apply(viewer);
            if (team == null) return cursors;
            for (Player p : viewer.getWorld().getPlayers()) {
                if (p.equals(viewer) || teamOf.apply(p) != team) continue;
                Location l = p.getLocation();
                byte x = (byte) Math.clamp(l.getBlockX(), -128, 127), z = (byte) Math.clamp(l.getBlockZ(), -128, 127);
                byte dir = (byte) (Math.round(((l.getYaw() % 360 + 360) % 360) / 22.5) & 15);
                cursors.addCursor(new MapCursor(x, z, dir, banner(team), true, Component.text(p.getName())));
            }
            return cursors;
        }
    }

    private static MapCursor.Type banner(GameTeam t) {
        return switch (t) {
            case RED -> MapCursor.Type.BANNER_RED;
            case YELLOW -> MapCursor.Type.BANNER_YELLOW;
            case GREEN -> MapCursor.Type.BANNER_GREEN;
            case BLUE -> MapCursor.Type.BANNER_BLUE;
        };
    }

    static int teamRgb(GameTeam t) {
        return switch (t) {
            case RED -> 0xE0322E;
            case YELLOW -> 0xF2CB28;
            case GREEN -> 0x3CB44B;
            case BLUE -> 0x3264DC;
        };
    }

    private static int mix(int a, int b, double t) {
        int r = (int) (((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
        int g = (int) (((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
        int bl = (int) ((a & 255) * (1 - t) + (b & 255) * t);
        return (r << 16) | (g << 8) | bl;
    }

    private static int scale(int rgb, double f) {
        int r = Math.min(255, (int) (((rgb >> 16) & 255) * f));
        int g = Math.min(255, (int) (((rgb >> 8) & 255) * f));
        int b = Math.min(255, (int) ((rgb & 255) * f));
        return (r << 16) | (g << 8) | b;
    }
}
