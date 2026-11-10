package com.tangtamukei.lobby;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Leaves;

import java.util.Random;

/**
 * 허공에 떠 있는 원형 섬 로비를 블록 단위로 생성.
 *
 * <pre>
 *   y+5   ┌─────┐            포탈 아치(4방향) / 벚나무(대각선 4곳)
 *   y     │ ░░░ │  ▒ 가로등   발판 + 가장자리 난간 + 투명 방벽
 *   y-1  ━━━━━━━━━━━━━━━━━━   바닥 (석영 무늬)
 *         ╲  돌 · 응회암  ╱     아래쪽은 원뿔형 바위 (떠 있는 섬)
 *           ╲  심층암   ╱
 * </pre>
 */
public final class LobbyStructure {

    /** 바닥 반지름. */
    public static final int RADIUS = 16;
    private static final int DEPTH = 14;

    private final World world;
    private final int y; // 플레이어가 서는 높이 (바닥은 y-1)
    private final Random random = new Random(20261110L);

    public LobbyStructure(World world, int y) {
        this.world = world;
        this.y = y;
    }

    public void build() {
        clear();
        floor();
        underside();
        edge();
        portals();
        trees();
        lamps();
    }

    /** 기존 구조물 영역 비우기. */
    private void clear() {
        int r = RADIUS + 4;
        for (int x = -r; x <= r; x++)
            for (int z = -r; z <= r; z++)
                for (int dy = -DEPTH - 2; dy <= 10; dy++)
                    set(x, y + dy, z, Material.AIR);
    }

    // ───────────── 바닥 ─────────────

    private void floor() {
        for (int x = -RADIUS; x <= RADIUS; x++) {
            for (int z = -RADIUS; z <= RADIUS; z++) {
                double d = Math.sqrt(x * x + z * z);
                if (d > RADIUS + 0.5) continue;
                set(x, y - 1, z, floorMaterial(x, z, d));
            }
        }
    }

    private Material floorMaterial(int x, int z, double d) {
        if (d <= 1.5) return Material.SEA_LANTERN;                  // 중앙 (스폰 지점)
        if (d <= 3.5) return Material.CHISELED_QUARTZ_BLOCK;
        if (d >= RADIUS - 0.5) return Material.POLISHED_DEEPSLATE; // 바깥 테두리
        if (d >= 7 && d < 8) return Material.QUARTZ_BRICKS;         // 안쪽 원 무늬
        if (x == 0 || z == 0) return Material.QUARTZ_BRICKS;        // 포탈로 가는 길
        if (Math.abs(x) == Math.abs(z)) return Material.POLISHED_DIORITE;
        return Material.SMOOTH_QUARTZ;
    }

    // ───────────── 아래쪽 바위 (떠 있는 섬 느낌) ─────────────

    private void underside() {
        for (int k = 1; k <= DEPTH; k++) {
            double t = (double) k / (DEPTH + 1);
            double radius = RADIUS * Math.pow(1 - t, 0.8);
            for (int x = -RADIUS; x <= RADIUS; x++) {
                for (int z = -RADIUS; z <= RADIUS; z++) {
                    double d = Math.sqrt(x * x + z * z) + random.nextDouble() * 1.6 - 0.8;
                    if (d > radius) continue;
                    set(x, y - 1 - k, z, rockMaterial(t));
                }
            }
        }
    }

    private Material rockMaterial(double depth) {
        double r = random.nextDouble();
        if (depth < 0.15) return r < 0.5 ? Material.STONE : Material.ANDESITE;
        if (depth < 0.6) {
            if (r < 0.4) return Material.STONE;
            if (r < 0.7) return Material.TUFF;
            if (r < 0.9) return Material.ANDESITE;
            return Material.COBBLESTONE;
        }
        return r < 0.6 ? Material.DEEPSLATE : Material.COBBLED_DEEPSLATE;
    }

    // ───────────── 가장자리 ─────────────

    private void edge() {
        for (int x = -RADIUS - 1; x <= RADIUS + 1; x++) {
            for (int z = -RADIUS - 1; z <= RADIUS + 1; z++) {
                double d = Math.sqrt(x * x + z * z);
                if (d < RADIUS - 0.5 || d > RADIUS + 0.5) continue;
                set(x, y, z, Material.POLISHED_DEEPSLATE_WALL);
                // 떨어지지 않게 보이지 않는 벽
                for (int dy = 1; dy <= 3; dy++) set(x, y + dy, z, Material.BARRIER);
            }
        }
    }

    private void lamps() {
        int r = RADIUS - 2;
        int[][] spots = {{r, 0}, {-r, 0}, {0, r}, {0, -r}};
        // 포탈 옆 양쪽
        for (int[] s : spots) {
            for (int side : new int[]{-4, 4}) {
                int x = s[0] == 0 ? side : s[0];
                int z = s[1] == 0 ? side : s[1];
                set(x, y, z, Material.DEEPSLATE_BRICK_WALL);
                set(x, y + 1, z, Material.DEEPSLATE_BRICK_WALL);
                set(x, y + 2, z, Material.LANTERN);
            }
        }
    }

    // ───────────── 게임 포탈 (4방향 아치) ─────────────

    private void portals() {
        int r = RADIUS - 3;
        portal(0, -r, true, Material.LIME_STAINED_GLASS);       // 북쪽
        portal(r, 0, false, Material.RED_STAINED_GLASS);        // 동쪽
        portal(0, r, true, Material.LIGHT_BLUE_STAINED_GLASS);  // 남쪽
        portal(-r, 0, false, Material.YELLOW_STAINED_GLASS);    // 서쪽
    }

    /** (cx, cz) 중심, alongX 면 x축 방향으로 펼쳐진 아치. */
    private void portal(int cx, int cz, boolean alongX, Material glass) {
        for (int i = -2; i <= 2; i++) {
            int x = alongX ? cx + i : cx;
            int z = alongX ? cz : cz + i;
            for (int dy = 0; dy <= 4; dy++) {
                boolean frame = Math.abs(i) == 2 || dy == 4;
                set(x, y + dy, z, frame ? Material.QUARTZ_PILLAR : glass);
            }
            set(x, y + 5, z, Math.abs(i) == 2 ? Material.AIR : Material.QUARTZ_SLAB);
            set(x, y - 1, z, Material.GOLD_BLOCK);
        }
    }

    // ───────────── 벚나무 (대각선 4곳) ─────────────

    private void trees() {
        int p = 8;
        int[][] spots = {{p, p}, {-p, p}, {p, -p}, {-p, -p}};
        for (int[] s : spots) tree(s[0], s[1]);
    }

    private void tree(int cx, int cz) {
        // 화단
        for (int x = -1; x <= 1; x++)
            for (int z = -1; z <= 1; z++)
                set(cx + x, y - 1, cz + z, Material.MOSS_BLOCK);
        set(cx + 1, y, cz, Material.PINK_PETALS);
        set(cx - 1, y, cz + 1, Material.PINK_PETALS);

        int height = 4;
        for (int dy = 0; dy < height; dy++) set(cx, y + dy, cz, Material.CHERRY_LOG);

        BlockData leaves = Material.CHERRY_LEAVES.createBlockData(data -> ((Leaves) data).setPersistent(true));
        int top = y + height;
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                for (int dy = -1; dy <= 1; dy++) {
                    double d = Math.sqrt(x * x + z * z + dy * dy * 2.0);
                    if (d > 2.4 || (x == 0 && z == 0 && dy < 1)) continue;
                    Block b = world.getBlockAt(cx + x, top + dy, cz + z);
                    if (b.getType().isAir()) b.setBlockData(leaves, false);
                }
            }
        }
    }

    private void set(int x, int y, int z, Material material) {
        world.getBlockAt(x, y, z).setType(material, false);
    }
}
