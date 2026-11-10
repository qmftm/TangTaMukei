package com.tangtamukei.game;

import java.util.ArrayList;
import java.util.List;

import static com.tangtamukei.game.MapLayout.CELL;
import static com.tangtamukei.game.MapLayout.GRID;
import static com.tangtamukei.game.MapLayout.MIN;

/**
 * 네더 맵 배치 (오버월드와 같은 256x256, 2x2 청크 칸 8x8, 4팀 대칭).
 *
 * <pre>
 *  (x →)   0      1       2       3      4      5       6      7
 *  0     황무지  진홍숲  영혼골  ┌─── 보루 ───┐ 영혼골  진홍숲 황무지
 *  1     진홍숲  현무암 폐허포탈 └────────────┘ 폐허포탈 현무암 진홍숲
 *  2     영혼골 폐허포탈 진홍숲  현무암  영혼골 진홍숲 폐허포탈 영혼골
 *  3     ┌ 영혼골 ┐   영혼골  ┌ 뒤틀린 숲 ─┐ 현무암  ┌ 현무암 ┐
 *  4     └────────┘   현무암  └(네더 요새)─┘ 영혼골  └────────┘
 *  5     영혼골 폐허포탈 진홍숲  영혼골  현무암 진홍숲 폐허포탈 영혼골
 *  6     진홍숲  현무암 폐허포탈 ┌─── 보루 ───┐ 폐허포탈 현무암 진홍숲
 *  7     황무지  진홍숲  영혼골  └────────────┘ 영혼골  진홍숲 황무지
 * </pre>
 */
public final class NetherLayout {

    private static final NetherCell[][] QUADRANT = {
            {NetherCell.WASTES,    NetherCell.CRIMSON, NetherCell.SOUL_SAND, null},
            {NetherCell.CRIMSON,   NetherCell.BASALT,  NetherCell.RUINED,    null},
            {NetherCell.SOUL_SAND, NetherCell.RUINED,  NetherCell.CRIMSON,   NetherCell.BASALT},
            {null,                 null,               NetherCell.SOUL_SAND, null},
    };

    private final NetherCell[][] grid = new NetherCell[GRID][GRID]; // [col][row]

    public NetherLayout() {
        big(3, 0, NetherCell.BASTION);    // 북 (레드·옐로)
        big(3, 6, NetherCell.BASTION);    // 남 (그린·블루)
        big(6, 3, NetherCell.BIG_BASALT); // 동
        big(0, 3, NetherCell.BIG_SOUL);   // 서
        big(3, 3, NetherCell.CENTER);     // 중앙
        MapLayout.fillRotated(grid, QUADRANT);
    }

    private void big(int col, int row, NetherCell type) {
        for (int dc = 0; dc < 2; dc++)
            for (int dr = 0; dr < 2; dr++)
                grid[col + dc][row + dr] = type;
    }

    /** 맵 밖 좌표는 가장 가까운 칸. */
    public NetherCell nearest(int x, int z) {
        int col = Math.clamp(Math.floorDiv(x - MIN, CELL), 0, GRID - 1);
        int row = Math.clamp(Math.floorDiv(z - MIN, CELL), 0, GRID - 1);
        return grid[col][row];
    }

    public record Placement(org.bukkit.generator.structure.Structure structure, int x, int z) {
    }

    /** 요새는 항상 맵 중앙 (0, 0) 청크에서 생성 → GameManager 가 맵 안에 들어오는 시드를 고름. */
    public static final Placement FORTRESS = new Placement(org.bukkit.generator.structure.Structure.FORTRESS, 0, 0);

    public List<Placement> placements() {
        List<Placement> list = new ArrayList<>();
        boolean[][] done = new boolean[GRID][GRID];
        for (int col = 0; col < GRID; col++) {
            for (int row = 0; row < GRID; row++) {
                NetherCell type = grid[col][row];
                if (done[col][row] || type.structure == null) continue;
                if (type == NetherCell.CENTER) {
                    list.add(FORTRESS);
                    markBig(done, col, row);
                    continue;
                }
                int x = MIN + col * CELL + 4;
                int z = MIN + row * CELL + 4;
                if (type.big) {
                    markBig(done, col, row);
                    x = MIN + col * CELL + CELL;
                    z = MIN + row * CELL + CELL;
                    // 보루는 넓어서 맵 안쪽으로 당김
                    if (type == NetherCell.BASTION) z += z < 0 ? CELL / 2 : -CELL / 2;
                }
                list.add(new Placement(type.structure, x, z));
            }
        }
        return list;
    }

    private static void markBig(boolean[][] done, int col, int row) {
        for (int dc = 0; dc < 2; dc++)
            for (int dr = 0; dr < 2; dr++)
                done[col + dc][row + dr] = true;
    }
}
