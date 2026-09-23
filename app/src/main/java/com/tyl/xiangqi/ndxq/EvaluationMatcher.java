package com.tyl.xiangqi.ndxq;

import java.security.SecureRandom;

/** 评测模式按玩家等级分匹配难度；距离越近，抽中概率越高。 */
final class EvaluationMatcher {
    private static final SecureRandom RANDOM = new SecureRandom();

    private EvaluationMatcher() {}

    static int choose(int playerRating, int[] opponentRatings) {
        if (opponentRatings == null || opponentRatings.length == 0) return 0;
        int low = playerRating - 150;
        int high = playerRating + 150;
        int[] candidates = new int[opponentRatings.length];
        int count = 0;
        for (int i = 0; i < opponentRatings.length; i++) {
            if (opponentRatings[i] >= low && opponentRatings[i] <= high) candidates[count++] = i;
        }
        if (count == 0) {
            int best = 0;
            int distance = Math.abs(opponentRatings[0] - playerRating);
            for (int i = 1; i < opponentRatings.length; i++) {
                int d = Math.abs(opponentRatings[i] - playerRating);
                if (d < distance) { best = i; distance = d; }
            }
            return best;
        }
        double total = 0d;
        for (int i = 0; i < count; i++) total += weight(playerRating, opponentRatings[candidates[i]]);
        double pick = RANDOM.nextDouble() * total;
        for (int i = 0; i < count; i++) {
            pick -= weight(playerRating, opponentRatings[candidates[i]]);
            if (pick <= 0d) return candidates[i];
        }
        return candidates[count - 1];
    }

    private static double weight(int playerRating, int opponentRating) {
        return 1d / (25d + Math.abs(opponentRating - playerRating));
    }

    /** 独立于难度匹配的二值抽样；两种执子方各占一个等概率桶。 */
    static boolean randomRed() { return RANDOM.nextInt(2) == 0; }
    static int randomDifficulty(int count) { return count <= 0 ? 0 : RANDOM.nextInt(count); }
}
