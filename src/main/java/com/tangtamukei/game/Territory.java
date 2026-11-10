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

    /** 게임 시작: 모든 땅 초기화 후 팀마다 꼭짓점부터 startChunks 개 지급. */
    public void start(GameTeam[] teams, int startChunks) {
        for (GameTeam[] row : owner) java.util.Arrays.fill(row, null);
        for (GameTeam t : teams) {
            for (int[] cr : startChunks(t, startChunks)) owner[cr[0]][cr[1]] = t;
        }
        version++;
    }

    /**
     * 꼭짓점부터 정사각형으로 채워나가는 순서의 청크 n 개 {col, row}.
     * 1 = 꼭짓점 1칸, 4 = 2x2, 9 = 3x3, 그 사이 값은 다음 줄을 한 칸씩 채움.
     */
    public static java.util.List<int[]> startChunks(GameTeam t, int n) {
        int half = N / 2;
        java.util.List<int[]> local = new java.util.ArrayList<>();
        for (int a = 0; a < half; a++) for (int b = 0; b < half; b++) local.add(new int[]{a, b});
        local.sort(java.util.Comparator.<int[]>comparingInt(p -> Math.max(p[0], p[1]))
                .thenComparingInt(p -> Math.min(p[0], p[1]))
                .thenComparingInt(p -> p[0] < p[1] ? 1 : 0));
        java.util.List<int[]> out = new java.util.ArrayList<>();
        for (int[] p : local.subList(0, Math.clamp(n, 1, half * half))) {
            out.add(new int[]{t.signX() < 0 ? p[0] : N - 1 - p[0], t.signZ() < 0 ? p[1] : N - 1 - p[1]});
        }
        return out;
    }

    /** 시작 땅 안의 스폰 블록 {x, z}: 꽉 찬 k x k 정사각형의 가운데. */
    public static int[] spawnBlock(GameTeam t, int n) {
        int k = Math.max(1, (int) Math.sqrt(Math.clamp(n, 1, (N / 2) * (N / 2))));
        int x = t.signX() < 0 ? MapLayout.MIN + k * 8 : MapLayout.MAX - k * 8;
        int z = t.signZ() < 0 ? MapLayout.MIN + k * 8 : MapLayout.MAX - k * 8;
        return new int[]{x, z};
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
