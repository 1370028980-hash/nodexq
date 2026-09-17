package com.tyl.xiangqi.ndxq;

import com.tyl.xiangqi.ndxq.engine.PikafishEngine;

/** V20.2 的 22 档内置难度配置。数组下标仍从 0 开始，对外编号为下标 + 1。 */
final class DifficultyProfiles {
    private DifficultyProfiles() {}

    static DifficultyProfile[] createDefault() {
        return new DifficultyProfile[] {
                DifficultyProfile.depth("独孤求胜", "HCE", 5, 2, 1),
                DifficultyProfile.nodes("略知一二", "HCE", 5, 2, 400),
                DifficultyProfile.nodes(null, "HCE", 5, 2, 700),
                DifficultyProfile.nodes("雾里看花", "HCE", 5, 2, 1200),
                DifficultyProfile.nodes(null, "HCE", 5, 2, 1900),
                DifficultyProfile.nodes("初悟棋道", "HCE", 5, 2, 2400),
                DifficultyProfile.nodes(null, "HCE", 5, 2, 3200),
                DifficultyProfile.nodes("初露锋芒", "duf", 5, 2, 1000),
                DifficultyProfile.nodes(null, "HCE", 5, 2, 4000),
                DifficultyProfile.nodes("棋摊中坚", "duf", 5, 2, 1600),
                DifficultyProfile.nodes(null, "duf", 5, 2, 2100),
                DifficultyProfile.nodes("街头霸王", "duf", 5, 2, 2800),
                DifficultyProfile.nodes(null, "duf", 5, 2, 3200),
                DifficultyProfile.nodes(null, "duf", 5, 2, 4000),
                DifficultyProfile.nodes("县镇好手", "duf", 8, 2, 6000),
                DifficultyProfile.nodes(null, "duf", 8, 2, 10000),
                DifficultyProfile.nodes("市县好手", "duf", 8, 2, 15000),
                DifficultyProfile.nodes(null, "duf", 8, 2, 25000),
                DifficultyProfile.nodes("业余顶尖", "duf", 10, 2, 40000),
                DifficultyProfile.nodes("特级大师", "duf", 10, 2, 120000),
                DifficultyProfile.nodes("天下无敌", "duf", 5, 2, 1600000),
                DifficultyProfile.nodes("青云", "131", 3, 4, 4500000)
        };
    }
}

final class DifficultyProfile {
    final String name;
    final String engineSlot;
    final int outBookRounds;
    final int threads;
    final PikafishEngine.SearchLimit limit;

    private DifficultyProfile(String name, String engineSlot, int outBookRounds,
                              int threads, PikafishEngine.SearchLimit limit) {
        this.name = name;
        this.engineSlot = engineSlot;
        this.outBookRounds = outBookRounds;
        this.threads = threads;
        this.limit = limit;
    }

    static DifficultyProfile depth(String name, String slot, int rounds, int threads, int depth) {
        return new DifficultyProfile(name, slot, rounds, threads,
                PikafishEngine.SearchLimit.depth(depth));
    }

    static DifficultyProfile nodes(String name, String slot, int rounds, int threads, int nodes) {
        return new DifficultyProfile(name, slot, rounds, threads,
                PikafishEngine.SearchLimit.nodes(nodes));
    }
}
