package com.tangtamukei.lobby;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Leaves;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;

/**
 * 허공에 떠 있는 로비: 가운데 광장 + 다리로 이어진 게임별 섬 4개.
 *
 * <pre>
 *                  [땅따먹기 섬]  ← 잔디 + 4팀 색 바닥
 *                       ║
 *   [개발중 섬] ═══ ( 광 장 ) ═══ [용암비 섬]
 *   경고 줄무늬         ║          흑암·마그마·떨어지는 용암 방울
 *                  [대홍수 섬]  ← 프리즈머린 + 물웅덩이
 * </pre>
 * 광장 정중앙이 스폰. 각 섬의 바깥쪽 끝에 광장을 바라보는 포탈 아치.
 * 모든 가장자리에 난간 + 투명 벽(배리어).
 */
public final class LobbyStructure {

    public static final int PLAZA_RADIUS = 9;
    public static final int ISLAND_DISTANCE = 22; // 섬 중심까지 거리
    public static final int ISLAND_RADIUS = 6;
    /** 포탈 아치 중심까지 거리 (섬 바깥쪽 끝). 북·동·남·서 순서로 (0,-D) (D,0) (0,D) (-D,0). */
    public static final int PORTAL_DISTANCE = ISLAND_DISTANCE + 3;
    private static final int CLEAR_RADIUS = 36;

    private enum Theme { PLAZA, LAND, LAVA, FLOOD, DEV }

    private final World world;
    private final int y;          // 플레이어가 서는 높이 (바닥은 y-1)
    private final Random random = new Random(20261111L);
    private final Set<Long> floor = new HashSet<>();

    public LobbyStructure(World world, int y) {
        this.world = world;
        this.y = y;
    }

    public void build() {
        clear();
        // 바닥
        disk(0, 0, PLAZA_RADIUS, Theme.PLAZA);
        bridge(0, -1);
        bridge(1, 0);
        bridge(0, 1);
        bridge(-1, 0);
        disk(0, -ISLAND_DISTANCE, ISLAND_RADIUS, Theme.LAND);
        disk(ISLAND_DISTANCE, 0, ISLAND_RADIUS, Theme.LAVA);
        disk(0, ISLAND_DISTANCE, ISLAND_RADIUS, Theme.FLOOD);
        disk(-ISLAND_DISTANCE, 0, ISLAND_RADIUS, Theme.DEV);

        plazaDecor();
        landDecor();
        lavaDecor();
        floodDecor();
        devDecor();

        portal(0, -PORTAL_DISTANCE, true, Material.QUARTZ_PILLAR, Material.LIME_STAINED_GLASS);
        portal(PORTAL_DISTANCE, 0, false, Material.POLISHED_BLACKSTONE_BRICKS, Material.RED_STAINED_GLASS);
        portal(0, PORTAL_DISTANCE, true, Material.DARK_PRISMARINE, Material.LIGHT_BLUE_STAINED_GLASS);
        portal(-PORTAL_DISTANCE, 0, false, Material.YELLOW_CONCRETE, Material.YELLOW_STAINED_GLASS);

        rails();
    }

    private void clear() {
        for (int x = -CLEAR_RADIUS; x <= CLEAR_RADIUS; x++)
            for (int z = -CLEAR_RADIUS; z <= CLEAR_RADIUS; z++)
                for (int dy = -18; dy <= 14; dy++)
                    set(x, y + dy, z, Material.AIR);
    }

    // ───────────── 바닥 + 아래쪽 바위 ─────────────

    private void disk(int cx, int cz, int radius, Theme theme) {
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                double d = Math.sqrt(x * x + z * z);
                if (d > radius + 0.5) continue;
                setFloor(cx + x, cz + z, floorMaterial(theme, x, z, d, radius));
            }
        }
        underside(cx, cz, radius, theme);
    }

    /** 광장 가장자리 ~ 섬 가장자리, 폭 3 다리. (dx, dz) = 방향 */
    private void bridge(int dx, int dz) {
        for (int t = PLAZA_RADIUS - 1; t <= ISLAND_DISTANCE - ISLAND_RADIUS + 1; t++) {
            for (int w = -1; w <= 1; w++) {
                int x = dx * t + (dz != 0 ? w : 0), z = dz * t + (dx != 0 ? w : 0);
                if (!floor.contains(key(x, z))) setFloor(x, z, w == 0 ? Material.POLISHED_TUFF : Material.TUFF_BRICKS);
                set(x, y - 2, z, Material.TUFF_BRICK_SLAB); // 얇은 다리 밑면
            }
        }
    }

    private Material floorMaterial(Theme theme, int x, int z, double d, int radius) {
        double r = random.nextDouble();
        return switch (theme) {
            case PLAZA -> {
                if (d <= 1.5) yield Material.CHISELED_TUFF_BRICKS;          // 스폰
                if (d >= 4.5 && d < 5.5) yield Material.TUFF_BRICKS;        // 안쪽 원
                if (Math.abs(x) <= 1 || Math.abs(z) <= 1) yield Material.POLISHED_TUFF; // 다리로 가는 길
                if (Math.abs(Math.abs(x) - Math.abs(z)) == 0) yield Material.POLISHED_DIORITE; // 대각선 무늬
                yield Material.CALCITE;
            }
            case LAND -> {
                if (d <= 3.5) {
                    // 가운데: 4팀 색 바닥 (레드 북서, 옐로우 북동, 그린 남동, 블루 남서)
                    if (x < 0 && z < 0) yield Material.RED_CONCRETE;
                    if (x >= 0 && z < 0) yield Material.YELLOW_CONCRETE;
                    if (x >= 0) yield Material.LIME_CONCRETE;
                    yield Material.BLUE_CONCRETE;
                }
                yield r < 0.12 ? Material.MOSS_BLOCK : Material.GRASS_BLOCK;
            }
            case LAVA -> {
                if (r < 0.16) yield Material.MAGMA_BLOCK;
                yield r < 0.6 ? Material.POLISHED_BLACKSTONE : Material.BLACKSTONE;
            }
            case FLOOD -> {
                if (d >= radius - 0.5) yield Material.DARK_PRISMARINE;
                yield r < 0.55 ? Material.PRISMARINE_BRICKS : Material.PRISMARINE;
            }
            case DEV -> {
                if (d >= radius - 1.2) yield ((x + z) & 1) == 0 ? Material.YELLOW_CONCRETE : Material.BLACK_CONCRETE; // 경고 줄무늬
                yield r < 0.3 ? Material.ANDESITE : Material.SMOOTH_STONE;
            }
        };
    }

    private void underside(int cx, int cz, int radius, Theme theme) {
        int depth = radius + 4;
        for (int k = 1; k <= depth; k++) {
            double t = (double) k / (depth + 1);
            double rr = radius * Math.pow(1 - t, 0.8);
            for (int x = -radius; x <= radius; x++) {
                for (int z = -radius; z <= radius; z++) {
                    double d = Math.sqrt(x * x + z * z) + random.nextDouble() * 1.4 - 0.7;
                    if (d > rr) continue;
                    set(cx + x, y - 1 - k, cz + z, rock(theme, t));
                }
            }
        }
    }

    private Material rock(Theme theme, double depth) {
        double r = random.nextDouble();
        return switch (theme) {
            case PLAZA -> depth < 0.5 ? (r < 0.5 ? Material.TUFF : Material.STONE) : (r < 0.6 ? Material.DEEPSLATE : Material.TUFF);
            case LAND -> depth < 0.2 ? Material.DIRT : (r < 0.7 ? Material.STONE : Material.ANDESITE);
            case LAVA -> r < 0.15 ? Material.MAGMA_BLOCK : (r < 0.6 ? Material.BLACKSTONE : Material.BASALT);
            case FLOOD -> depth < 0.3 ? Material.PRISMARINE : (r < 0.5 ? Material.STONE : Material.GRAVEL);
            case DEV -> r < 0.5 ? Material.STONE : Material.COBBLESTONE;
        };
    }

    // ───────────── 장식 ─────────────

    private void plazaDecor() {
        int p = 6;
        for (int[] s : new int[][]{{p, p}, {-p, p}, {p, -p}, {-p, -p}}) cherry(s[0], s[1]);
        // 다리 입구 양쪽 가로등
        for (int[] d : new int[][]{{0, -1}, {1, 0}, {0, 1}, {-1, 0}}) {
            for (int side : new int[]{-2, 2}) {
                int x = d[0] * (PLAZA_RADIUS - 1) + (d[1] != 0 ? side : 0);
                int z = d[1] * (PLAZA_RADIUS - 1) + (d[0] != 0 ? side : 0);
                lamp(x, z, Material.TUFF_BRICK_WALL);
            }
        }
    }

    private void landDecor() {
        int cz = -ISLAND_DISTANCE;
        // 네 귀퉁이에 팀 색 깃대 (울타리 + 양털)
        Material[] wool = {Material.RED_WOOL, Material.YELLOW_WOOL, Material.LIME_WOOL, Material.BLUE_WOOL};
        int[][] spots = {{-4, -3}, {4, -3}, {4, 3}, {-4, 3}};
        for (int i = 0; i < 4; i++) {
            int x = spots[i][0], z = cz + spots[i][1];
            for (int dy = 0; dy < 3; dy++) set(x, y + dy, z, Material.OAK_FENCE);
            set(x, y + 3, z, wool[i]);
            set(x + (x < 0 ? 1 : -1), y + 3, z, wool[i]);
        }
        cherry(-3, cz + 4);
        cherry(3, cz + 4);
    }

    private void lavaDecor() {
        int cx = ISLAND_DISTANCE;
        // 하늘에서 떨어지는 용암 방울 (주황 유리, 실제 용암 아님)
        // 섬 양옆에만 (광장에서 포탈을 볼 때 제목을 가리지 않게)
        int[][] drops = {{-3, -4, 2}, {0, -5, 3}, {2, -4, 2}, {-3, 4, 3}, {0, 5, 2}, {2, 4, 2}};
        for (int[] d : drops) {
            int top = y + 5 + random.nextInt(2);
            for (int k = 0; k < d[2]; k++) set(cx + d[0], top - k, d[1], k == d[2] - 1 ? Material.SHROOMLIGHT : Material.ORANGE_STAINED_GLASS);
        }
        // 굳은 용암 웅덩이
        for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) setFloor(cx + x, z, Material.MAGMA_BLOCK);
        lamp(cx - 4, -4, Material.POLISHED_BLACKSTONE_WALL);
        lamp(cx - 4, 4, Material.POLISHED_BLACKSTONE_WALL);
    }

    private void floodDecor() {
        int cz = ISLAND_DISTANCE;
        // 가운데 물웅덩이 (바닥보다 한 칸 낮고 사방이 막혀 넘치지 않음)
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                boolean edge = Math.abs(x) == 2 || Math.abs(z) == 2;
                set(x, y - 2, cz + z, Material.SEA_LANTERN);
                set(x, y - 1, cz + z, edge ? Material.DARK_PRISMARINE : Material.WATER);
            }
        }
        for (int[] s : new int[][]{{-4, -3}, {4, -3}, {-4, 3}, {4, 3}}) {
            set(s[0], y, cz + s[1], Material.PRISMARINE_WALL);
            set(s[0], y + 1, cz + s[1], Material.SEA_LANTERN);
        }
    }

    private void devDecor() {
        int cx = -ISLAND_DISTANCE;
        // 비계와 나무통 (공사 중)
        for (int[] s : new int[][]{{2, -3, 4}, {3, -3, 3}, {2, -2, 2}, {-2, 3, 3}, {-3, 3, 2}}) {
            for (int k = 0; k < s[2]; k++) set(cx + s[0], y + k, s[1], Material.SCAFFOLDING);
        }
        set(cx - 2, y, -3, Material.BARREL);
        set(cx - 3, y, -3, Material.BARREL);
        set(cx - 2, y + 1, -3, Material.BARREL);
        set(cx + 3, y, 3, Material.CRAFTING_TABLE);
        lamp(cx + 4, -1, Material.COBBLESTONE_WALL);
        lamp(cx + 4, 1, Material.COBBLESTONE_WALL);
    }

    private void lamp(int x, int z, Material post) {
        set(x, y, z, post);
        set(x, y + 1, z, post);
        set(x, y + 2, z, Material.LANTERN);
    }

    private void cherry(int cx, int cz) {
        set(cx, y - 1, cz, Material.MOSS_BLOCK);
        set(cx + 1, y, cz, Material.PINK_PETALS);
        set(cx - 1, y, cz + 1, Material.PINK_PETALS);
        int height = 4;
        for (int dy = 0; dy < height; dy++) set(cx, y + dy, cz, Material.CHERRY_LOG);
        BlockData leaves = Material.CHERRY_LEAVES.createBlockData(d -> ((Leaves) d).setPersistent(true));
        int top = y + height;
        for (int x = -2; x <= 2; x++)
            for (int z = -2; z <= 2; z++)
                for (int dy = -1; dy <= 1; dy++) {
                    double d = Math.sqrt(x * x + z * z + dy * dy * 2.0);
                    if (d > 2.4 || (x == 0 && z == 0 && dy < 1)) continue;
                    Block b = world.getBlockAt(cx + x, top + dy, cz + z);
                    if (b.getType().isAir()) b.setBlockData(leaves, false);
                }
    }

    /** 포탈 아치 (폭 5, 높이 5) + 계단 받침. alongX = x축으로 펼쳐짐. */
    private void portal(int cx, int cz, boolean alongX, Material frame, Material glass) {
        for (int i = -2; i <= 2; i++) {
            int x = alongX ? cx + i : cx, z = alongX ? cz : cz + i;
            for (int dy = 0; dy <= 4; dy++) {
                boolean isFrame = Math.abs(i) == 2 || dy == 4;
                set(x, y + dy, z, isFrame ? frame : glass);
            }
            if (Math.abs(i) < 2) set(x, y + 5, z, frame);
            set(x, y - 1, z, Material.GOLD_BLOCK);
        }
    }

    // ───────────── 가장자리 난간 + 투명 벽 ─────────────

    private void rails() {
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        Set<Long> done = new HashSet<>();
        for (long k : floor) {
            int x = (int) (k >> 32), z = (int) k;
            for (int[] d : dirs) {
                int nx = x + d[0], nz = z + d[1];
                long nk = key(nx, nz);
                if (floor.contains(nk) || !done.add(nk)) continue;
                set(nx, y, nz, Material.POLISHED_TUFF_WALL);
                for (int dy = 1; dy <= 3; dy++) set(nx, y + dy, nz, Material.BARRIER);
            }
        }
    }

    private void setFloor(int x, int z, Material m) {
        set(x, y - 1, z, m);
        floor.add(key(x, z));
    }

    private static long key(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
    }

    private void set(int x, int y, int z, Material material) {
        world.getBlockAt(x, y, z).setType(material, false);
    }
}
