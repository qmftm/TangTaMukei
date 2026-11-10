package com.tangtamukei.game;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Random;

/**
 * 16x16 청크(256x256 블록) 게임 맵 배치. 맵 중심은 (0, 0).
 *
 * 2x2 청크 칸 8x8 로 나누고, 큰 구조물은 2x2 칸(4x4 청크), 야생 칸은 1청크씩 따로.
 * 4팀이 공평하도록 북서 사분면 배치를 90도씩 돌려서 나머지 사분면을 채움.
 *
 * <pre>
 *  (x →)   0       1       2       3      4       5       6       7
 *  0     [레드]  야생1   메사    ┌─── 마을 ───┐ 난파선  타이가  [옐로]
 *  1     타이가  야생2   사막    └────────────┘ 해저유적 야생2   야생1
 *  2     난파선  해저유적 전초기지 정글   늪    전초기지 사막    메사
 *  3     ┌ 해저 신전 ┐    늪     ┌─── 요새 ───┐ 정글   ┌ 맨션 ─┐
 *  4     └──────────┘   정글    └── (중앙) ──┘  늪    └───────┘
 *  5     메사     사막   전초기지  늪    정글  전초기지 해저유적 난파선
 *  6     야생1    야생2  해저유적 ┌─ 고대 도시 ─┐ 사막    야생2  타이가
 *  7     [블루]  타이가  난파선   └────────────┘ 메사    야생1  [그린]
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
    public static final int CHUNKS = SIZE >> 4;   // 16

    /** 북서 사분면(레드) 기준 배치. null = 큰 구조물/중앙 자리. [row(z)][col(x)] */
    private static final CellType[][] QUADRANT = {
            {CellType.SPAWN,     CellType.WILD_FOREST, CellType.BADLANDS, null},
            {CellType.TAIGA,     CellType.WILD_MIXED,  CellType.DESERT,   null},
            {CellType.SHIPWRECK, CellType.OCEAN_RUINS, CellType.OUTPOST,  CellType.JUNGLE},
            {null,               null,                 CellType.SWAMP,    null},
    };

    private final CellType[][] grid = new CellType[GRID][GRID]; // [col(x)][row(z)]
    private final CellType.Variant[][] variants = new CellType.Variant[GRID][GRID];
    private final CellType.Variant[][] wildChunks = new CellType.Variant[CHUNKS][CHUNKS];
    private final Random random;
    private final EnumMap<CellType, Integer> shifts = new EnumMap<>(CellType.class);

    /** 같은 시드면 같은 배치. 변형은 시드에 따라 섞임. */
    public MapLayout(long seed) {
        this.random = new Random(seed);
        big(3, 0, CellType.VILLAGE);      // 북 (레드·옐로)
        big(6, 3, CellType.MANSION);      // 동 (옐로·그린)
        big(3, 6, CellType.ANCIENT_CITY); // 남 (그린·블루)
        big(0, 3, CellType.MONUMENT);     // 서 (블루·레드)
        big(3, 3, CellType.STRONGHOLD);   // 중앙

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
                    int nRow = col, nCol = GRID - 1 - row; // 90도 회전
                    row = nRow;
                    col = nCol;
                }
            }
        }
        buildWildChunks();
    }

    /** 야생 칸: 북서 사분면에서 4청크 바이옴을 정하고 90도씩 회전 (4팀 같은 구성). */
    private void buildWildChunks() {
        for (int col = 0; col < GRID / 2; col++) {
            for (int row = 0; row < GRID / 2; row++) {
                CellType type = grid[col][row];
                if (!type.isWild()) continue;
                List<CellType.Variant> pool = new ArrayList<>(type.variants);
                List<CellType.Variant> picked = new ArrayList<>();
                if (type == CellType.WILD_MIXED) {
                    CellType.Variant cherry = pool.removeFirst(), bamboo = pool.removeFirst();
                    Collections.shuffle(pool, random);
                    boolean vertical = random.nextBoolean(); // 벚꽃 2청크는 나란히
                    picked.add(cherry);
                    picked.add(vertical ? bamboo : cherry);
                    picked.add(vertical ? cherry : bamboo);
                    picked.add(pool.removeFirst());
                } else {
                    Collections.shuffle(pool, random);
                    for (int i = 0; i < 4; i++) picked.add(pool.get(i));
                }
                for (int i = 0; i < 4; i++) {
                    int c = col * 2 + (i & 1), r = row * 2 + (i >> 1);
                    for (int rot = 0; rot < 4; rot++) {
                        wildChunks[c][r] = picked.get(i);
                        int nr = c, nc = CHUNKS - 1 - r;
                        r = nr;
                        c = nc;
                    }
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

    /** 북서 사분면 배치를 시계방향으로 90도씩 돌려 빈 칸을 채움 (네더용). grid[col][row], quadrant[row][col] */
    static <T> void fillRotated(T[][] grid, T[][] quadrant) {
        int n = grid.length;
        for (int r = 0; r < quadrant.length; r++) {
            for (int c = 0; c < quadrant[r].length; c++) {
                T type = quadrant[r][c];
                if (type == null) continue;
                int row = r, col = c;
                for (int rot = 0; rot < 4; rot++) {
                    if (grid[col][row] == null) grid[col][row] = type;
                    int nRow = col, nCol = n - 1 - row;
                    row = nRow;
                    col = nCol;
                }
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

    /** 복사해 올 영역 하나: 대상 영역(블록) + 찾을 대상. */
    public record Request(int targetX, int targetZ, int size, CellType type, CellType.Variant variant) {
    }

    /**
     * 맵을 채울 복사 요청 목록.
     * 구조물 칸만 한 곳에서 이어서 가져오고(큰 구조물 64블록, 작은 구조물 32블록),
     * 나머지는 청크(16블록)마다 각각 다른 곳에서 가져옴.
     */
    public List<Request> requests() {
        List<Request> list = new ArrayList<>();
        boolean[][] done = new boolean[GRID][GRID];
        for (int col = 0; col < GRID; col++) {
            for (int row = 0; row < GRID; row++) {
                if (done[col][row]) continue;
                CellType type = grid[col][row];
                int x = MIN + col * CELL, z = MIN + row * CELL;
                if (type.isBig()) {
                    for (int dc = 0; dc < 2; dc++)
                        for (int dr = 0; dr < 2; dr++)
                            done[col + dc][row + dr] = true;
                    list.add(new Request(x, z, CELL * 2, type, variants[col][row]));
                } else if (type.isWild()) {
                    for (int i = 0; i < 4; i++) {
                        int cc = col * 2 + (i & 1), cr = row * 2 + (i >> 1);
                        list.add(new Request(MIN + cc * 16, MIN + cr * 16, 16, type, wildChunks[cc][cr]));
                    }
                } else if (variants[col][row].structure() == null) {
                    // 구조물 없는 칸(스폰·메사)도 청크마다 다른 곳에서 → 청크마다 높낮이가 달라 끊겨 보임
                    for (int i = 0; i < 4; i++) {
                        list.add(new Request(x + (i & 1) * 16, z + (i >> 1) * 16, 16, type, variants[col][row]));
                    }
                } else {
                    list.add(new Request(x, z, CELL, type, variants[col][row]));
                }
            }
        }
        return list;
    }
}
