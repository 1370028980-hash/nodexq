package com.tyl.xiangqi.ndxq;

/** 玩家等级分的固定难度映射与 Elo 更新规则。 */
final class PlayerRatingCalculator {
    private static final int[] DIFFICULTY_RATINGS = {
            520, 765, 875, 985, 1130, 1210, 1310, 1360, 1410, 1535, 1650,
            1770, 1830, 1890, 2000, 2110, 2210, 2300, 2400, 2600, 2870, 3290
    };
    private static final int MIN_RATING = 500;
    private static final int MAX_RATING = 4000;
    private static final double K_FACTOR = 32.0;

    private PlayerRatingCalculator() {}

    static int difficultyRating(int difficultyIndex) {
        int index = Math.max(0, Math.min(difficultyIndex, DIFFICULTY_RATINGS.length - 1));
        return DIFFICULTY_RATINGS[index];
    }

    static int update(int playerRating, int opponentRating, double actualScore) {
        return update(playerRating, opponentRating, actualScore, K_FACTOR);
    }

    static int update(int playerRating, int opponentRating, double actualScore, double kFactor) {
        int current = clamp(playerRating);
        double actual = Math.max(0.0, Math.min(1.0, actualScore));
        double expected = 1.0 / (1.0 + Math.pow(10.0, (opponentRating - current) / 400.0));
        double delta = Math.max(0d, kFactor) * (actual - expected);
        int change = (int) Math.round(delta);
        if (change == 0 && delta != 0d) change = delta > 0d ? 1 : -1;
        return clamp(current + change);
    }

    static int clamp(int rating) {
        return Math.max(MIN_RATING, Math.min(MAX_RATING, rating));
    }
}
