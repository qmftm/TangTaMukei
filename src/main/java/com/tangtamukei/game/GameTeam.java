package com.tangtamukei.game;

import net.kyori.adventure.text.format.NamedTextColor;

/** 팀과 시작 꼭짓점. 맵은 -128..127 이고 각 팀은 꼭짓점 칸 중앙에서 시작. */
public enum GameTeam {
    RED("레드", NamedTextColor.RED, -1, -1),       // 북서
    YELLOW("옐로우", NamedTextColor.YELLOW, 1, -1), // 북동
    GREEN("그린", NamedTextColor.GREEN, 1, 1),     // 남동
    BLUE("블루", NamedTextColor.BLUE, -1, 1);      // 남서

    public final String displayName;
    public final NamedTextColor color;
    private final int signX, signZ;

    GameTeam(String displayName, NamedTextColor color, int signX, int signZ) {
        this.displayName = displayName;
        this.color = color;
        this.signX = signX;
        this.signZ = signZ;
    }

    /** 꼭짓점 방향 (-1 = 서/북, 1 = 동/남). */
    public int signX() {
        return signX;
    }

    public int signZ() {
        return signZ;
    }

    /** 팀 수에 따라 쓸 팀. 2팀이면 대각선 꼭짓점끼리. */
    public static GameTeam[] forCount(int count) {
        return switch (count) {
            case 1 -> new GameTeam[]{RED};
            case 2 -> new GameTeam[]{RED, GREEN};
            case 3 -> new GameTeam[]{RED, YELLOW, GREEN};
            default -> values();
        };
    }
}
