package com.tangtamukei.game;

/**
 * 청크 땅 주인.
 * - 각 팀의 시작 꼭짓점 2x2 청크만 처음부터 그 팀 땅, 나머지는 주인 없음
 * - 주인 없는 땅은 [일반 땅] 도끼로만, 다른 팀 땅은 [특수 땅] 도끼로만 가져올 수 있음 ({@link LandToken})
 */
public final class Territory {

    private static final int N = MapLayout.CHUNKS;

    private final GameTeam[][] owner = new GameTeam[N][N];
    /** 땅 주인이 바뀔 때마다 증가 (지도 다시 그리기용). */
    private volatile int version;

    /** 게임 시작: 모든 땅 초기화 후 팀 시작 꼭짓점 2x2 청크 지급. */
    public void start(GameTeam[] teams) {
        for (GameTeam[] row : owner) java.util.Arrays.fill(row, null);
        for (GameTeam t : teams) {
            int c0 = t.spawnX() < 0 ? 0 : N - 2, r0 = t.spawnZ() < 0 ? 0 : N - 2;
            for (int dc = 0; dc < 2; dc++)
                for (int dr = 0; dr < 2; dr++)
                    owner[c0 + dc][r0 + dr] = t;
        }
        version++;
    }

    public GameTeam owner(int col, int row) {
        return owner[col][row];
    }

    public int version() {
        return version;
    }

    /** 블록 좌표의 땅 주인 (맵 밖이면 null). */
    public GameTeam ownerAt(int x, int z) {
        if (!MapLayout.inside(x, z)) return null;
        return owner[(x - MapLayout.MIN) >> 4][(z - MapLayout.MIN) >> 4];
    }

    /** 맵 안이고 아무 팀도 안 먹은 땅이면 true. */
    public boolean isUnclaimed(int x, int z) {
        return MapLayout.inside(x, z) && ownerAt(x, z) == null;
    }

    /** [일반 땅] 도끼로 주인 없는 땅을 먹음. 성공하면 true. */
    public boolean claim(int x, int z, GameTeam team) {
        if (!isUnclaimed(x, z)) return false;
        owner[(x - MapLayout.MIN) >> 4][(z - MapLayout.MIN) >> 4] = team;
        version++;
        return true;
    }

    /** [특수 땅] 도끼로 다른 팀 땅을 빼앗음. 빼앗긴 팀을 반환 (실패하면 null). */
    public GameTeam convert(int x, int z, GameTeam team) {
        GameTeam previous = ownerAt(x, z);
        if (previous == null || previous == team) return null;
        owner[(x - MapLayout.MIN) >> 4][(z - MapLayout.MIN) >> 4] = team;
        version++;
        return previous;
    }

    public int count(GameTeam team) {
        int n = 0;
        for (GameTeam[] row : owner) for (GameTeam t : row) if (t == team) n++;
        return n;
    }
}
