package com.tangtamukei.lobby;

import org.bukkit.Axis;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.MultipleFacing;
import org.bukkit.block.data.Orientable;
import org.bukkit.block.data.type.Lantern;
import org.bukkit.block.data.type.Leaves;
import org.bukkit.block.data.type.Slab;
import org.bukkit.block.data.type.Stairs;
import org.bukkit.block.data.type.Wall;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;

/**
 * 허공에 떠 있는 로비: 가운데 광장 + 다리로 이어진 게임별 섬 4개.
 *
 * <pre>
 *                  [땅따먹기 섬]  ← 잔디·꽃·참나무 + 4팀 색 바닥과 깃발
 *                       ║
 *   [개발중 섬] ═══ ( 광 장 ) ═══ [용암 섬]
 *   비계 크레인·자재     ║          현무암 기둥·용암 웅덩이·떨어지는 용암 줄기
 *                  [대홍수 섬]  ← 프리즈머린·물웅덩이 + 포탈 뒤로 솟는 파도
 * </pre>
 * 광장 정중앙이 스폰. 각 섬의 바깥쪽 끝에 광장을 바라보는 포탈 아치.
 * 가장자리에 난간이 없어서 떨어질 수 있고, 떨어지면 스폰으로 되돌아감 (LobbyListener).
 */
public final class LobbyStructure {

    public static final int PLAZA_RADIUS = 9;
    public static final int ISLAND_DISTANCE = 22; // 섬 중심까지 거리
    public static final int ISLAND_RADIUS = 6;
    /** 포탈 아치 중심까지 거리 (섬 바깥쪽 끝). 북·동·남·서 순서로 (0,-D) (D,0) (0,D) (-D,0). */
    public static final int PORTAL_DISTANCE = ISLAND_DISTANCE + 3;
    private static final int CLEAR_RADIUS = 36;
    private static final BlockFace[] SIDES = {BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST};

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

        portal(0, -PORTAL_DISTANCE, true, Material.QUARTZ_BRICKS, Material.QUARTZ_STAIRS, Material.LIME_STAINED_GLASS);
        portal(PORTAL_DISTANCE, 0, false, Material.POLISHED_BLACKSTONE_BRICKS, Material.POLISHED_BLACKSTONE_BRICK_STAIRS, Material.RED_STAINED_GLASS);
        portal(0, PORTAL_DISTANCE, true, Material.DARK_PRISMARINE, Material.DARK_PRISMARINE_STAIRS, Material.LIGHT_BLUE_STAINED_GLASS);
        portal(-PORTAL_DISTANCE, 0, false, Material.SMOOTH_STONE, Material.STONE_BRICK_STAIRS, Material.YELLOW_STAINED_GLASS);

        connectWallsAndFences();
    }

    private void clear() {
        for (int x = -CLEAR_RADIUS; x <= CLEAR_RADIUS; x++)
            for (int z = -CLEAR_RADIUS; z <= CLEAR_RADIUS; z++)
                for (int dy = -20; dy <= 16; dy++)
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
        int from = PLAZA_RADIUS - 1, to = ISLAND_DISTANCE - ISLAND_RADIUS + 1;
        for (int t = from; t <= to; t++) {
            for (int w = -1; w <= 1; w++) {
                int x = dx * t + (dz != 0 ? w : 0), z = dz * t + (dx != 0 ? w : 0);
                if (!floor.contains(key(x, z))) setFloor(x, z, w == 0 ? Material.POLISHED_TUFF : Material.TUFF_BRICKS);
                // 다리 밑면: 양 끝은 거꾸로 된 계단으로 받치고 가운데는 얇게
                if (get(x, y - 2, z).isAir()) {
                    if (t == from + 2 || t == to - 2) {
                        BlockFace toward = t == from + 2 ? face(-dx, -dz) : face(dx, dz);
                        stairs(x, y - 2, z, Material.TUFF_BRICK_STAIRS, toward, true);
                    } else {
                        slab(x, y - 2, z, Material.TUFF_BRICK_SLAB, true);
                    }
                }
            }
        }
    }

    private Material floorMaterial(Theme theme, int x, int z, double d, int radius) {
        double r = random.nextDouble();
        return switch (theme) {
            case PLAZA -> {
                if (d <= 0.5) yield Material.CHISELED_TUFF_BRICKS;          // 스폰
                // 가운데 방향 표시: 각 섬의 색
                if (d <= 2.5 && (x == 0 || z == 0)) {
                    if (z < 0) yield Material.LIME_GLAZED_TERRACOTTA;
                    if (x > 0) yield Material.RED_GLAZED_TERRACOTTA;
                    if (z > 0) yield Material.LIGHT_BLUE_GLAZED_TERRACOTTA;
                    yield Material.YELLOW_GLAZED_TERRACOTTA;
                }
                if (d <= 2.5) yield Material.POLISHED_TUFF;
                if (d >= 4.5 && d < 5.5) yield Material.TUFF_BRICKS;        // 안쪽 원
                if (d >= radius - 0.5) yield Material.POLISHED_TUFF;         // 가장자리 띠
                if (Math.abs(x) <= 1 || Math.abs(z) <= 1) yield x == 0 || z == 0 ? Material.POLISHED_TUFF : Material.TUFF_BRICKS; // 다리로 가는 길
                if (Math.abs(x) == Math.abs(z)) yield Material.POLISHED_DIORITE; // 대각선 무늬
                yield r < 0.12 ? Material.DIORITE : Material.CALCITE;
            }
            case LAND -> {
                if (d <= 3.5) {
                    // 가운데: 4팀 색 바닥 (레드 북서, 옐로우 북동, 그린 남동, 블루 남서), 십자 경계선
                    if (x == 0 || z == 0) yield Material.WHITE_CONCRETE;
                    if (x < 0 && z < 0) yield Material.RED_CONCRETE;
                    if (x > 0 && z < 0) yield Material.YELLOW_CONCRETE;
                    if (x > 0) yield Material.LIME_CONCRETE;
                    yield Material.BLUE_CONCRETE;
                }
                if (x == 0 && z > 0) yield Material.DIRT_PATH;               // 다리에서 들어오는 흙길
                yield r < 0.1 ? Material.MOSS_BLOCK : Material.GRASS_BLOCK;
            }
            case LAVA -> {
                if (r < 0.12) yield Material.MAGMA_BLOCK;
                if (r < 0.2) yield Material.GILDED_BLACKSTONE;
                if (r < 0.45) yield Material.POLISHED_BLACKSTONE_BRICKS;
                yield r < 0.75 ? Material.POLISHED_BLACKSTONE : Material.BLACKSTONE;
            }
            case FLOOD -> {
                if (d >= radius - 0.5) yield Material.DARK_PRISMARINE;
                yield r < 0.55 ? Material.PRISMARINE_BRICKS : Material.PRISMARINE;
            }
            case DEV -> {
                if (d >= radius - 1.2) yield ((x + z) & 1) == 0 ? Material.YELLOW_CONCRETE : Material.BLACK_CONCRETE; // 경고 줄무늬
                yield r < 0.25 ? Material.ANDESITE : (r < 0.4 ? Material.GRAVEL : Material.SMOOTH_STONE);
            }
        };
    }

    private void underside(int cx, int cz, int radius, Theme theme) {
        int depth = radius + 5;
        for (int k = 1; k <= depth; k++) {
            double t = (double) k / (depth + 1);
            double rr = radius * Math.pow(1 - t, 0.75);
            for (int x = -radius; x <= radius; x++) {
                for (int z = -radius; z <= radius; z++) {
                    double d = Math.sqrt(x * x + z * z) + random.nextDouble() * 1.6 - 0.8;
                    if (d > rr) continue;
                    set(cx + x, y - 1 - k, cz + z, rock(theme, t));
                }
            }
        }
        // 밑면에 매달린 사슬 랜턴 몇 개
        for (int i = 0; i < 3; i++) {
            int x = cx + random.nextInt(radius) - radius / 2, z = cz + random.nextInt(radius) - radius / 2;
            int bottom = y - 2;
            while (bottom > y - 20 && !get(x, bottom - 1, z).isAir()) bottom--;
            if (bottom >= y - 3) continue;
            data(x, bottom - 1, z, Material.IRON_CHAIN.createBlockData());
            data(x, bottom - 2, z, Material.LANTERN.createBlockData(d -> ((Lantern) d).setHanging(true)));
        }
    }

    private Material rock(Theme theme, double depth) {
        double r = random.nextDouble();
        return switch (theme) {
            case PLAZA -> depth < 0.5 ? (r < 0.5 ? Material.TUFF : Material.STONE) : (r < 0.6 ? Material.DEEPSLATE : Material.TUFF);
            case LAND -> depth < 0.2 ? (r < 0.8 ? Material.DIRT : Material.ROOTED_DIRT) : (r < 0.7 ? Material.STONE : Material.ANDESITE);
            case LAVA -> r < 0.12 ? Material.MAGMA_BLOCK : (r < 0.6 ? Material.BLACKSTONE : Material.BASALT);
            case FLOOD -> depth < 0.3 ? Material.PRISMARINE : (r < 0.5 ? Material.STONE : Material.GRAVEL);
            case DEV -> r < 0.5 ? Material.STONE : Material.COBBLESTONE;
        };
    }

    // ───────────── 광장 ─────────────

    private void plazaDecor() {
        // 대각선 4곳: 꽃 화단 + 벚나무 + 벤치
        Material[] flowers = {Material.PINK_TULIP, Material.ALLIUM, Material.AZURE_BLUET, Material.OXEYE_DAISY};
        int p = 6;
        int[][] spots = {{p, p}, {-p, p}, {p, -p}, {-p, -p}};
        for (int[] s : spots) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (!floor.contains(key(s[0] + dx, s[1] + dz))) continue;
                    set(s[0] + dx, y - 1, s[1] + dz, Material.MOSS_BLOCK);
                    if (dx == 0 && dz == 0) continue;
                    set(s[0] + dx, y, s[1] + dz, random.nextInt(3) == 0 ? Material.PINK_PETALS : flowers[random.nextInt(flowers.length)]);
                }
            }
            cherry(s[0], s[1]);
            // 벤치: 화단 안쪽(광장 가운데 방향)에 등받이 계단
            int sx = Integer.signum(s[0]), sz = Integer.signum(s[1]);
            stairs(s[0] - 2 * sx, y, s[1], Material.CHERRY_STAIRS, face(sx, 0), false);
            stairs(s[0], y, s[1] - 2 * sz, Material.CHERRY_STAIRS, face(0, sz), false);
        }
        // 다리 입구 아치문: 기둥 2개 + 가로대 + 매단 랜턴 2개
        // (스폰에서 포탈 제목이 가로대 아래로 보이도록 높게)
        for (int[] d : new int[][]{{0, -1}, {1, 0}, {0, 1}, {-1, 0}}) {
            int t = PLAZA_RADIUS - 1;
            for (int side : new int[]{-2, 2}) {
                int x = d[0] * t + (d[1] != 0 ? side : 0), z = d[1] * t + (d[0] != 0 ? side : 0);
                set(x, y, z, Material.POLISHED_TUFF);
                for (int k = 1; k <= 3; k++) set(x, y + k, z, Material.TUFF_BRICKS);
                set(x, y + 4, z, Material.CHISELED_TUFF_BRICKS);
            }
            for (int w = -1; w <= 1; w++) {
                int x = d[0] * t + (d[1] != 0 ? w : 0), z = d[1] * t + (d[0] != 0 ? w : 0);
                slab(x, y + 4, z, Material.TUFF_BRICK_SLAB, true);
                if (w != 0) data(x, y + 3, z, Material.LANTERN.createBlockData(l -> ((Lantern) l).setHanging(true)));
            }
        }
    }

    // ───────────── 땅따먹기 섬 ─────────────

    private void landDecor() {
        int cz = -ISLAND_DISTANCE;
        // 팀 색 깃대 4개 (울타리 + 양털 깃발)
        Material[] wool = {Material.RED_WOOL, Material.YELLOW_WOOL, Material.LIME_WOOL, Material.BLUE_WOOL};
        int[][] spots = {{-4, -2}, {4, -2}, {4, 3}, {-4, 3}};
        for (int i = 0; i < 4; i++) {
            int x = spots[i][0], z = cz + spots[i][1];
            for (int dy = 0; dy < 4; dy++) set(x, y + dy, z, Material.OAK_FENCE);
            int dir = x < 0 ? 1 : -1;
            set(x + dir, y + 3, z, wool[i]);
            set(x + 2 * dir, y + 3, z, wool[i]);
            set(x + dir, y + 2, z, wool[i]);
        }
        oak(-3, cz + 5);
        // 건초·퇴비통
        data(3, y, cz + 5, Material.HAY_BLOCK.createBlockData());
        data(4, y, cz + 4, Material.HAY_BLOCK.createBlockData(d -> ((Orientable) d).setAxis(Axis.X)));
        set(3, y + 1, cz + 5, Material.HAY_BLOCK);
        set(2, y, cz + 5, Material.COMPOSTER);
        // 잔디 위 꽃과 풀
        Material[] plants = {Material.POPPY, Material.DANDELION, Material.CORNFLOWER, Material.SHORT_GRASS, Material.SHORT_GRASS};
        for (int x = -ISLAND_RADIUS; x <= ISLAND_RADIUS; x++) {
            for (int z = -ISLAND_RADIUS; z <= ISLAND_RADIUS; z++) {
                if (!floor.contains(key(x, cz + z)) || get(x, y - 1, cz + z) != Material.GRASS_BLOCK) continue;
                if (!get(x, y, cz + z).isAir() || random.nextInt(5) != 0) continue;
                set(x, y, cz + z, plants[random.nextInt(plants.length)]);
            }
        }
    }

    // ───────────── 용암 섬 ─────────────

    private void lavaDecor() {
        int cx = ISLAND_DISTANCE;
        // 양옆 용암 웅덩이 (바닥보다 한 칸 낮고 사방이 막혀 흐르지 않음)
        for (int side : new int[]{-3, 3}) {
            for (int x = -1; x <= 1; x++) {
                for (int z = -1; z <= 1; z++) {
                    boolean corner = Math.abs(x) == 1 && Math.abs(z) == 1;
                    set(cx + x - 1, y - 2, side + z, Material.MAGMA_BLOCK);
                    setFloor(cx + x - 1, side + z, corner ? Material.CHISELED_POLISHED_BLACKSTONE : Material.LAVA);
                }
            }
        }
        // 현무암 기둥
        for (int[] c : new int[][]{{-4, -1, 3}, {-4, 1, 2}, {2, -5, 4}, {3, -4, 2}, {2, 5, 5}, {1, 5, 2}, {4, 3, 3}}) {
            for (int k = 0; k < c[2]; k++) set(cx + c[0], y + k, c[1], Material.POLISHED_BASALT);
        }
        // 섬 위에 떠 있는 바위에서 용암 줄기가 웅덩이로 (주황 유리, 실제 용암 아님)
        for (int side : new int[]{-3, 3}) {
            int bx = cx - 1, by = y + 9;
            for (int x = -1; x <= 1; x++)
                for (int z = -1; z <= 1; z++)
                    for (int dy = 0; dy <= 1; dy++) {
                        if (Math.abs(x) + Math.abs(z) + dy > 2) continue;
                        set(bx + x, by + dy, side + z, random.nextInt(4) == 0 ? Material.MAGMA_BLOCK : Material.BLACKSTONE);
                    }
            for (int yy = y; yy < by; yy++) set(bx, yy, side, Material.ORANGE_STAINED_GLASS);
            set(bx, by, side, Material.SHROOMLIGHT);
        }
        set(cx + 4, y, -2, Material.CRYING_OBSIDIAN);
        set(cx - 5, y, 3, Material.CRYING_OBSIDIAN);
    }

    // ───────────── 대홍수 섬 ─────────────

    private void floodDecor() {
        int cz = ISLAND_DISTANCE;
        // 가운데 얕은 물웅덩이 (바닥보다 한 칸 낮고 사방이 막혀 넘치지 않음)
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                boolean edge = Math.abs(x) == 2 || Math.abs(z) == 2;
                set(x, y - 2, cz + z, Math.floorMod(x + z, 2) == 0 ? Material.SEA_LANTERN : Material.PRISMARINE_BRICKS);
                if (!edge) set(x, y - 1, cz + z, Material.WATER);
            }
        }
        // 포탈 뒤로 솟아 안쪽으로 말리는 파도 (파랑 → 하늘색 → 흰 유리)
        for (int x = -ISLAND_RADIUS; x <= ISLAND_RADIUS; x++) {
            for (int z = 1; z <= ISLAND_RADIUS; z++) {
                double d = Math.sqrt(x * x + z * z);
                if (d < ISLAND_RADIUS - 1.2 || d > ISLAND_RADIUS + 0.5 || !floor.contains(key(x, cz + z))) continue;
                if (z == 3 && Math.abs(x) <= 2) continue; // 포탈 자리
                int height = (int) Math.round(2 + 5.0 * z / ISLAND_RADIUS - Math.abs(x) * 0.25);
                for (int k = 0; k < height; k++) {
                    Material m = k >= height - 1 ? Material.WHITE_STAINED_GLASS
                            : k >= height - 3 ? Material.LIGHT_BLUE_STAINED_GLASS : Material.BLUE_STAINED_GLASS;
                    set(x, y + k, cz + z, m);
                }
                // 물마루: 한 칸 안쪽으로 말림
                if (height >= 4 && get(x, y + height - 1, cz + z - 1).isAir()) set(x, y + height - 1, cz + z - 1, Material.WHITE_STAINED_GLASS);
            }
        }
        for (int[] s : new int[][]{{-4, -3}, {4, -3}}) {
            set(s[0], y, cz + s[1], Material.PRISMARINE_WALL);
            set(s[0], y + 1, cz + s[1], Material.SEA_LANTERN);
        }
    }

    // ───────────── 개발중 섬 ─────────────

    private void devDecor() {
        int cx = -ISLAND_DISTANCE;
        // 비계 크레인: 탑 + 팔 + 사슬에 매달린 자재
        int tx = cx + 2, tz = -3;
        for (int k = 0; k < 7; k++) set(tx, y + k, tz, Material.SCAFFOLDING);
        for (int k = 1; k <= 4; k++) slab(tx, y + 6, tz + k, Material.SMOOTH_STONE_SLAB, true);
        data(tx, y + 5, tz + 4, Material.IRON_CHAIN.createBlockData());
        data(tx, y + 4, tz + 4, Material.IRON_CHAIN.createBlockData());
        set(tx, y + 3, tz + 4, Material.STONE_BRICKS);
        for (int k = 0; k < 3; k++) set(tx + 1, y + k, tz, Material.SCAFFOLDING);
        // 자재 더미
        set(cx - 2, y, 3, Material.STONE_BRICKS);
        set(cx - 3, y, 3, Material.STONE_BRICKS);
        set(cx - 2, y + 1, 3, Material.STONE_BRICKS);
        set(cx - 2, y, 4, Material.BRICKS);
        slab(cx - 3, y, 4, Material.BRICK_SLAB, false);
        set(cx - 3, y, -3, Material.OAK_PLANKS);
        set(cx - 3, y + 1, -3, Material.OAK_PLANKS);
        slab(cx - 4, y, -2, Material.OAK_SLAB, false);
        set(cx - 2, y, -4, Material.BARREL);
        set(cx + 4, y, 3, Material.CRAFTING_TABLE);
        set(cx + 3, y, 4, Material.BARREL);
        lamp(cx + 4, -1, Material.COBBLESTONE_WALL);
    }

    // ───────────── 공용 장식 ─────────────

    private void lamp(int x, int z, Material post) {
        set(x, y, z, post);
        set(x, y + 1, z, post);
        set(x, y + 2, z, Material.LANTERN);
    }

    private void cherry(int cx, int cz) {
        int height = 4;
        for (int dy = 0; dy < height; dy++) set(cx, y + dy, cz, Material.CHERRY_LOG);
        leaves(cx, y + height, cz, Material.CHERRY_LEAVES);
    }

    private void oak(int cx, int cz) {
        set(cx, y - 1, cz, Material.DIRT);
        for (int dy = 0; dy < 4; dy++) set(cx, y + dy, cz, Material.OAK_LOG);
        leaves(cx, y + 4, cz, Material.OAK_LEAVES);
    }

    private void leaves(int cx, int top, int cz, Material type) {
        BlockData leaves = type.createBlockData(d -> ((Leaves) d).setPersistent(true));
        for (int x = -2; x <= 2; x++)
            for (int z = -2; z <= 2; z++)
                for (int dy = -1; dy <= 1; dy++) {
                    double d = Math.sqrt(x * x + z * z + dy * dy * 2.0);
                    if (d > 2.4 || (x == 0 && z == 0 && dy < 1)) continue;
                    Block b = world.getBlockAt(cx + x, top + dy, cz + z);
                    if (b.getType().isAir()) b.setBlockData(leaves, false);
                }
    }

    /**
     * 포탈 아치 (폭 5, 높이 5): 기둥 2개 + 가로대, 안쪽 위 모서리는 거꾸로 된 계단으로 둥글림.
     * alongX = x축으로 펼쳐짐.
     */
    private void portal(int cx, int cz, boolean alongX, Material frame, Material stair, Material glass) {
        for (int i = -2; i <= 2; i++) {
            int x = alongX ? cx + i : cx, z = alongX ? cz : cz + i;
            for (int dy = 0; dy <= 4; dy++) {
                boolean isFrame = Math.abs(i) == 2 || dy == 4;
                set(x, y + dy, z, isFrame ? frame : glass);
            }
            if (Math.abs(i) == 1) {
                // 아치 모서리: 기둥 쪽을 향한 거꾸로 된 계단
                BlockFace toward = alongX ? face(i, 0) : face(0, i);
                stairs(x, y + 3, z, stair, toward, true);
            }
            if (Math.abs(i) < 2) set(x, y + 5, z, frame);
            set(x, y - 1, z, Material.GOLD_BLOCK);
        }
    }

    /** 가로등 기둥·울타리가 이웃과 이어지도록 연결 상태를 직접 계산 (블록 갱신 없이 지었으므로). */
    private void connectWallsAndFences() {
        for (int x = -CLEAR_RADIUS; x <= CLEAR_RADIUS; x++) {
            for (int z = -CLEAR_RADIUS; z <= CLEAR_RADIUS; z++) {
                for (int dy = -20; dy <= 16; dy++) {
                    Block b = world.getBlockAt(x, y + dy, z);
                    BlockData data = b.getBlockData();
                    if (data instanceof Wall wall) {
                        for (BlockFace f : SIDES) wall.setHeight(f, connects(b.getRelative(f)) ? Wall.Height.LOW : Wall.Height.NONE);
                        wall.setUp(true);
                        b.setBlockData(wall, false);
                    } else if (data instanceof MultipleFacing fence && b.getType().name().endsWith("_FENCE")) {
                        for (BlockFace f : SIDES) fence.setFace(f, connects(b.getRelative(f)));
                        b.setBlockData(fence, false);
                    }
                }
            }
        }
    }

    private static boolean connects(Block neighbor) {
        Material m = neighbor.getType();
        String n = m.name();
        if (n.endsWith("_WALL") || n.endsWith("_FENCE")) return true;
        return m.isOccluding(); // 꽉 찬 불투명 블록
    }

    // ───────────── 블록 놓기 ─────────────

    private static BlockFace face(int dx, int dz) {
        if (dx > 0) return BlockFace.EAST;
        if (dx < 0) return BlockFace.WEST;
        return dz > 0 ? BlockFace.SOUTH : BlockFace.NORTH;
    }

    private void stairs(int x, int yy, int z, Material m, BlockFace facing, boolean upsideDown) {
        data(x, yy, z, m.createBlockData(d -> {
            ((Stairs) d).setFacing(facing);
            ((Stairs) d).setHalf(upsideDown ? Bisected.Half.TOP : Bisected.Half.BOTTOM);
        }));
    }

    private void slab(int x, int yy, int z, Material m, boolean top) {
        data(x, yy, z, m.createBlockData(d -> ((Slab) d).setType(top ? Slab.Type.TOP : Slab.Type.BOTTOM)));
    }

    private void setFloor(int x, int z, Material m) {
        set(x, y - 1, z, m);
        floor.add(key(x, z));
    }

    private static long key(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
    }

    private Material get(int x, int yy, int z) {
        return world.getBlockAt(x, yy, z).getType();
    }

    private void data(int x, int yy, int z, BlockData data) {
        world.getBlockAt(x, yy, z).setBlockData(data, false);
    }

    private void set(int x, int yy, int z, Material material) {
        world.getBlockAt(x, yy, z).setType(material, false);
    }
}
