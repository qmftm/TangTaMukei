package com.tangtamukei.game;

import org.bukkit.generator.structure.Structure;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.Random;
import java.util.List;

/**
 * 16x16 청크(256x256 블록) 게임 맵 배치. 맵 중심은 (0, 0).
 *
 * 맵은 32블록(2x2 청크) 칸 8x8 로 나뉘고, 큰 구조물은 2x2 칸(4x4 청크)을 차지합니다.
 * 4팀이 공평하도록 1사분면 배치를 90도씩 돌려서 나머지 사분면을 채웁니다.
 *
 * <pre>
 *  (x →)   0       1       2       3      4       5       6       7
 *  0     [레드]   숲      메사    ┌─── 마을 ───┐ 난파선  타이가  [옐로]
 *  1     타이가   벚꽃    사막    └────────────┘ 해저유적 벚꽃    숲
 *  2     난파선  해저유적 전초기지 대나무   늪   전초기지 사막    메사
 *  3     ┌ 해저 신전 ┐    늪     ┌─── 요새 ───┐ 대나무  ┌ 맨션 ─┐
 *  4     └──────────┘   대나무   └── (중앙) ──┘  늪    └───────┘
 *  5     메사     사막   전초기지  늪   대나무  전초기지 해저유적 난파선
 *  6     숲       벚꽃   해저유적 ┌─ 고대 도시 ─┐ 사막    벚꽃   타이가
 *  7     [블루]  타이가  난파선   └────────────┘ 메사     숲    [그린]
 * </pre>
 */
public final class MapLayout {

    public static final int CELL = 32;            // 칸 크기 (블록)
    public static final int GRID = 8;             // 8x8 칸
    public static final int SIZE = CELL * GRID;   // 256 블록 = 16 청크
    public static final int MIN = -SIZE / 2;      // -128
    public static final int MAX = SIZE / 2;       // 128 (미포함)
    public static final int MIN_CHUNK = MIN >> 4; // -8
    public static final int MAX_CHUNK = (MAX >> 4) - 1; // 7

    /** 북서 사분면(레드) 기준 배치. null = 큰 구조물/중앙 자리. [row(z)][col(x)] */
    private static final CellType[][] QUADRANT = {
            {CellType.SPAWN,     CellType.FOREST,       CellType.BADLANDS,    null},
            {CellType.TAIGA,     CellType.CHERRY_GROVE, CellType.DESERT,      null},
            {CellType.SHIPWRECK, CellType.OCEAN_RUINS,  CellType.OUTPOST,     CellType.BAMBOO},
            {null,               null,                  CellType.SWAMP,       null},
    };

    private final CellType[][] grid = new CellType[GRID][GRID]; // [col(x)][row(z)]
    private final CellType.Variant[][] variants = new CellType.Variant[GRID][GRID];
    private final Random random;
    private final EnumMap<CellType, Integer> shifts = new EnumMap<>(CellType.class);

    /** 같은 시드면 같은 배치. 바이옴 변형은 시드에 따라 섞임. */
    public MapLayout(long seed) {
        this.random = new Random(seed);
        // 큰 구조물: 4변 가운데 (각각 이웃한 두 팀이 같은 거리)
        big(3, 0, CellType.VILLAGE);      // 북
        big(6, 3, CellType.MANSION);      // 동
        big(3, 6, CellType.ANCIENT_CITY); // 남
        big(0, 3, CellType.MONUMENT);     // 서
        big(3, 3, CellType.STRONGHOLD);   // 중앙

        // 사분면 배치를 90도씩 돌려 채우고, 사분면마다 다른 바이옴 변형 지정
        for (int r = 0; r < 4; r++) {
            for (int c = 0; c < 4; c++) {
                CellType type = QUADRANT[r][c];
                if (type == null) continue;
                int shift = shifts.computeIfAbsent(type, t -> random.nextInt(t.variants.size()));
                int row = r, col = c;
                for (int rot = 0; rot < 4; rot++) {
                    if (grid[col][row] == null) {
                        grid[col][row] = type;
                        variants[col][row] = type.variants.get((rot + shift) % type.variants.size());
                    }
                    int nRow = col, nCol = GRID - 1 - row;
                    row = nRow;
                    col = nCol;
                }
            }
        }
    }

    /** 북서 사분면 배치를 시계방향으로 90도씩 돌려 빈 칸을 채움. grid[col][row], quadrant[row][col] */
    static <T> void fillRotated(T[][] grid, T[][] quadrant) {
        int n = grid.length;
        for (int r = 0; r < quadrant.length; r++) {
            for (int c = 0; c < quadrant[r].length; c++) {
                T type = quadrant[r][c];
                if (type == null) continue;
                int row = r, col = c;
                for (int rot = 0; rot < 4; rot++) {
                    if (grid[col][row] == null) grid[col][row] = type;
                    int nRow = col, nCol = n - 1 - row; // 90도 회전
                    row = nRow;
                    col = nCol;
                }
            }
        }
    }

    private void big(int col, int row, CellType type) {
        CellType.Variant variant = type.variants.get(random.nextInt(type.variants.size()));
        for (int dc = 0; dc < 2; dc++) {
            for (int dr = 0; dr < 2; dr++) {
                grid[col + dc][row + dr] = type;
                variants[col + dc][row + dr] = variant;
            }
        }
    }

    public static boolean inside(int x, int z) {
        return x >= MIN && x < MAX && z >= MIN && z < MAX;
    }

    public static boolean insideChunk(int cx, int cz) {
        return cx >= MIN_CHUNK && cx <= MAX_CHUNK && cz >= MIN_CHUNK && cz <= MAX_CHUNK;
    }

    /** 블록 좌표의 칸 종류 (맵 밖이면 null). */
    public CellType at(int x, int z) {
        if (!inside(x, z)) return null;
        return grid[(x - MIN) / CELL][(z - MIN) / CELL];
    }

    /** 맵 밖 좌표는 가장 가까운 칸으로 (바이옴 검사용). */
    public CellType nearest(int x, int z) {
        return cell(Math.floorDiv(x - MIN, CELL), Math.floorDiv(z - MIN, CELL));
    }

    public CellType.Variant nearestVariant(int x, int z) {
        return variant(Math.floorDiv(x - MIN, CELL), Math.floorDiv(z - MIN, CELL));
    }

    public CellType.Variant variant(int col, int row) {
        return variants[Math.clamp(col, 0, GRID - 1)][Math.clamp(row, 0, GRID - 1)];
    }

    /** 칸 높이 (자리 기본값 + 변형 보정). */
    public int height(int col, int row) {
        return cell(col, row).height + variant(col, row).heightOffset();
    }

    public int roughness(int col, int row) {
        return cell(col, row).roughness + variant(col, row).roughnessOffset();
    }

    /** 칸 인덱스로 조회 (범위 밖은 가장 가까운 칸). */
    public CellType cell(int col, int row) {
        col = Math.clamp(col, 0, GRID - 1);
        row = Math.clamp(row, 0, GRID - 1);
        return grid[col][row];
    }

    /** 놓아야 할 구조물 목록. 큰 구조물은 2x2 칸의 중앙에 하나만. */
    public List<Placement> placements() {
        List<Placement> list = new ArrayList<>();
        boolean[][] done = new boolean[GRID][GRID];
        for (int col = 0; col < GRID; col++) {
            for (int row = 0; row < GRID; row++) {
                CellType type = grid[col][row];
                Structure structure = variants[col][row].structure();
                if (done[col][row] || structure == null) continue;
                // 작은 구조물은 칸의 첫 청크에 (대부분 청크 모서리부터 ~20블록 크기라 칸 안에 들어감)
                int x = MIN + col * CELL + 4;
                int z = MIN + row * CELL + 4;
                if (type.isBig()) {
                    for (int dc = 0; dc < 2; dc++)
                        for (int dr = 0; dr < 2; dr++)
                            done[col + dc][row + dr] = true;
                    x = MIN + col * CELL + CELL;  // 2x2 칸 중앙
                    z = MIN + row * CELL + CELL;
                    switch (type) {
                        // 고대 도시는 매우 넓어서 맵 안쪽으로 당김
                        case ANCIENT_CITY -> z -= CELL / 2;
                        // 해저 신전은 주변 29블록이 전부 바다여야 함 → 맵 바깥/위쪽 바다 칸 방향으로
                        case MONUMENT -> {
                            x -= CELL / 2;
                            z -= CELL / 2;
                        }
                        default -> {
                        }
                    }
                }
                list.add(new Placement(structure, x, z));
            }
        }
        return list;
    }

    public record Placement(Structure structure, int x, int z) {
    }
}
