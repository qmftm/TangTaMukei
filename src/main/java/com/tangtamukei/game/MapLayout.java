package com.tangtamukei.game;

import org.bukkit.generator.structure.Structure;

import java.util.ArrayList;
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

    public MapLayout() {
        // 큰 구조물: 4변 가운데 (각각 이웃한 두 팀이 같은 거리)
        big(3, 0, CellType.VILLAGE);      // 북
        big(6, 3, CellType.MANSION);      // 동
        big(3, 6, CellType.ANCIENT_CITY); // 남
        big(0, 3, CellType.MONUMENT);     // 서
        big(3, 3, CellType.STRONGHOLD);   // 중앙

        // 사분면 배치를 시계방향으로 4번 회전
        for (int r = 0; r < 4; r++) {
            for (int c = 0; c < 4; c++) {
                CellType type = QUADRANT[r][c];
                if (type == null) continue;
                int row = r, col = c;
                for (int rot = 0; rot < 4; rot++) {
                    if (grid[col][row] == null) grid[col][row] = type;
                    int nRow = col, nCol = GRID - 1 - row; // 90도 회전
                    row = nRow;
                    col = nCol;
                }
            }
        }
    }

    private void big(int col, int row, CellType type) {
        for (int dc = 0; dc < 2; dc++)
            for (int dr = 0; dr < 2; dr++)
                grid[col + dc][row + dr] = type;
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
                if (done[col][row] || type.structure == null) continue;
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
                list.add(new Placement(type.structure, x, z));
            }
        }
        return list;
    }

    public record Placement(Structure structure, int x, int z) {
    }

    public static List<Placement> netherPlacements() {
        return List.of(
                new Placement(Structure.FORTRESS, 0, 0),           // 중앙
                new Placement(Structure.BASTION_REMNANT, 0, -80),  // 북 (레드·옐로 공용)
                new Placement(Structure.BASTION_REMNANT, 0, 80)    // 남 (그린·블루 공용)
        );
    }
}
