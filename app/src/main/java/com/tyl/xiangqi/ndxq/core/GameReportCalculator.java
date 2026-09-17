package com.tyl.xiangqi.ndxq.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * V18.5 对局报告评分器。
 *
 * 输入评分统一使用“红方视角”保存：普通分为引擎分，Mate 局面以 score 的正负表示红/黑占优，
 * matePlies 保存绝杀距离。计算某一手质量时，再转换到该手走子方视角。
 */
public final class GameReportCalculator {
    public static final double OPENING_WEIGHT = 0.23567d;
    public static final double MIDDLEGAME_WEIGHT = 0.76047d;
    public static final double ENDGAME_WEIGHT = 0.01906d;
    public static final double ERROR_LOSS_THRESHOLD = 1000d;
    /** 非 Mate 且双方仍处于同方向的超高评估区时，L 不再用于错招统计/标注。 */
    public static final int HIGH_SCORE_ERROR_IGNORE_THRESHOLD = 5500;
    /** 阶段 SoftMin 默认温度。参数越大打分越宽松（趋近算术平均），越小越严格（更强调低分招法）。默认 15。 */
    public static final double PHASE_SOFTMIN_TAU = 15d;

    private GameReportCalculator() {}

    public static Report calculate(int initialScoreRed, int initialMatePly,
                                   boolean initialScoreKnown,
                                   boolean initialRedToMove,
                                   List<Integer> scoresRed,
                                   List<Integer> matePlies,
                                   List<Boolean> scoreKnown,
                                   int totalMoves,
                                   int endgameRound,
                                   double softMinTau) {
        int moves = Math.max(0, totalMoves);
        if (moves <= 0 || !initialScoreKnown
                || scoresRed == null || matePlies == null || scoreKnown == null
                || scoresRed.size() < moves || matePlies.size() < moves
                || scoreKnown.size() < moves) {
            return Report.incomplete();
        }
        for (int i = 0; i < moves; i++) {
            if (!Boolean.TRUE.equals(scoreKnown.get(i))) return Report.incomplete();
        }

        SideAccumulator red = new SideAccumulator(softMinTau);
        SideAccumulator black = new SideAccumulator(softMinTau);
        ArrayList<Integer> errors = new ArrayList<Integer>();
        int effectiveEndgameRound = endgameRound < 0 ? Integer.MAX_VALUE
                : Math.max(11, endgameRound);

        for (int i = 0; i < moves; i++) {
            boolean redMover = initialRedToMove ? (i % 2) == 0 : (i % 2) != 0;
            int beforeScoreRed = i == 0 ? initialScoreRed : safeInt(scoresRed, i - 1);
            int beforeMatePly = i == 0 ? initialMatePly : safeInt(matePlies, i - 1);
            int afterScoreRed = safeInt(scoresRed, i);
            int afterMatePly = safeInt(matePlies, i);
            MoveQuality quality = scoreMove(beforeScoreRed, beforeMatePly,
                    afterScoreRed, afterMatePly, redMover);
            int round = i / 2 + 1;
            int phase = round <= 10 ? 0 : (round >= effectiveEndgameRound ? 2 : 1);
            SideAccumulator side = redMover ? red : black;
            // 高分饱和区只影响“是否算错招”，不影响单步分和阶段 SoftMin 聚合。
            side.add(phase, quality.score);
            if (quality.errorEligible && quality.loss > ERROR_LOSS_THRESHOLD) {
                errors.add(i + 1);
                side.addError();
                side.addSevereBlunder(quality.score);
            }
        }

        return new Report(true, red.finish(), black.finish(), errors);
    }

    /** V19.0：供棋盘“重新打分优劣提示”复用与报告完全相同的 Mate/普通分损失 L 逻辑。 */
    public static double moveLoss(int beforeScoreRed, int beforeMatePly,
                                  int afterScoreRed, int afterMatePly,
                                  boolean redMover) {
        return scoreMove(beforeScoreRed, beforeMatePly, afterScoreRed, afterMatePly, redMover).loss;
    }

    static MoveQuality scoreMove(int beforeScoreRed, int beforeMatePly,
                                 int afterScoreRed, int afterMatePly,
                                 boolean redMover) {
        int perspective = redMover ? 1 : -1;
        int before = beforeScoreRed * perspective;
        int after = afterScoreRed * perspective;
        int beforeMate = Math.max(0, beforeMatePly);
        int afterMate = Math.max(0, afterMatePly);
        // 局势图沿用 ±6000 + mate=0 表示规则终局；报告按 M1 处理，避免将绝杀只当普通 6000 分。
        if (beforeMate == 0 && Math.abs(before) == 6000) beforeMate = 1;
        if (afterMate == 0 && Math.abs(after) == 6000) afterMate = 1;
        boolean beforeIsMate = beforeMate > 0;
        boolean afterIsMate = afterMate > 0;

        double loss;
        if (beforeIsMate && afterIsMate && Integer.signum(before) == Integer.signum(after)) {
            int sign = Integer.signum(before);
            if (sign > 0 && afterMate > beforeMate) {
                loss = (afterMate - beforeMate) * 300d * mateDiscount(beforeMate);
            } else if (sign < 0 && afterMate < beforeMate) {
                loss = (beforeMate - afterMate) * 300d * mateDiscount(beforeMate);
            } else {
                loss = 0d;
            }
        } else {
            loss = equivalentElo(before, beforeMate) - equivalentElo(after, afterMate);
        }

        boolean errorEligible = !sameDirectionHighNonMateZone(
                before, beforeIsMate, after, afterIsMate);
        return new MoveQuality(loss, scoreFromLoss(loss), errorEligible);
    }

    /**
     * 只有走前、走后都没有 Mate 信息，且都已处在同一优势方向的 |score|>5500 区间，
     * 才认为 L 对“错招”统计失去参考意义。跨越阈值、翻转优势方向或进入 Mate 均不豁免。
     */
    private static boolean sameDirectionHighNonMateZone(int before, boolean beforeIsMate,
                                                        int after, boolean afterIsMate) {
        if (beforeIsMate || afterIsMate) return false;
        if (Math.abs(before) <= HIGH_SCORE_ERROR_IGNORE_THRESHOLD
                || Math.abs(after) <= HIGH_SCORE_ERROR_IGNORE_THRESHOLD) return false;
        return Integer.signum(before) == Integer.signum(after);
    }

    private static double equivalentElo(int perspectiveScore, int matePly) {
        if (matePly <= 0) return perspectiveScore;
        // M1=9000，M30=300，M31 及以上=0。
        double magnitude = Math.max(0d, 9000d - (Math.max(1, matePly) - 1) * 300d);
        return perspectiveScore >= 0 ? magnitude : -magnitude;
    }

    private static double mateDiscount(int matePly) {
        if (matePly <= 1) return 0.90d;
        if (matePly <= 3) return 0.70d;
        if (matePly <= 6) return 0.50d;
        if (matePly <= 10) return 0.35d;
        return 0.20d;
    }

    private static double scoreFromLoss(double loss) {
        if (loss <= 50d) return 100d;
        if (loss <= 150d) return linear(loss, 50d, 150d, 100d, 95d);
        if (loss <= 500d) return linear(loss, 150d, 500d, 95d, 80d);
        if (loss <= 1000d) return linear(loss, 500d, 1000d, 80d, 55d);
        double capped = Math.min(9999d, loss);
        return Math.max(0d, linear(capped, 1000d, 9999d, 55d, 0d));
    }

    private static double linear(double value, double x1, double x2, double y1, double y2) {
        if (x2 <= x1) return y2;
        double ratio = (value - x1) / (x2 - x1);
        return y1 + (y2 - y1) * ratio;
    }

    private static int safeInt(List<Integer> values, int index) {
        if (values == null || index < 0 || index >= values.size() || values.get(index) == null) return 0;
        return values.get(index);
    }

    public static final class Report {
        public final boolean complete;
        public final SideReport red;
        public final SideReport black;
        /** 1-based ply 编号。 */
        public final List<Integer> errorPlies;

        private Report(boolean complete, SideReport red, SideReport black, List<Integer> errorPlies) {
            this.complete = complete;
            this.red = red;
            this.black = black;
            this.errorPlies = errorPlies == null
                    ? Collections.<Integer>emptyList()
                    : Collections.unmodifiableList(new ArrayList<Integer>(errorPlies));
        }

        static Report incomplete() {
            return new Report(false, SideReport.empty(), SideReport.empty(),
                    Collections.<Integer>emptyList());
        }
    }

    public static final class SideReport {
        public final double total;
        public final double opening;
        public final double middlegame;
        public final double endgame;
        public final int errorCount;

        private SideReport(double total, double opening, double middlegame,
                           double endgame, int errorCount) {
            this.total = total;
            this.opening = opening;
            this.middlegame = middlegame;
            this.endgame = endgame;
            this.errorCount = errorCount;
        }

        static SideReport empty() {
            return new SideReport(Double.NaN, Double.NaN, Double.NaN, Double.NaN, 0);
        }
    }

    static final class MoveQuality {
        final double loss;
        final double score;
        final boolean errorEligible;

        MoveQuality(double loss, double score, boolean errorEligible) {
            this.loss = loss;
            this.score = score;
            this.errorEligible = errorEligible;
        }
    }

    private static final class SideAccumulator {
        private final double tau;
        private final double[] expSums = new double[3];
        private final int[] counts = new int[3];
        private int errors;
        /** 跨阶段记录最严重的大漏单步分，避免残局低权重把一次致命失误稀释掉。 */
        private double worstSevereScore = Double.NaN;

        SideAccumulator(double softMinTau) {
            this.tau = softMinTau <= 0d ? PHASE_SOFTMIN_TAU : softMinTau;
        }

        void add(int phase, double score) {
            int p = Math.max(0, Math.min(2, phase));
            double bounded = Math.max(0d, Math.min(100d, score));
            expSums[p] += Math.exp(-bounded / tau);
            counts[p]++;
        }

        void addError() {
            errors++;
        }

        void addSevereBlunder(double moveScore) {
            double bounded = Math.max(0d, Math.min(100d, moveScore));
            if (Double.isNaN(worstSevereScore) || bounded < worstSevereScore) {
                worstSevereScore = bounded;
            }
        }

        SideReport finish() {
            double opening = softMin(0);
            double middle = softMin(1);
            double end = softMin(2);
            double weighted = 0d;
            double weightSum = 0d;
            if (!Double.isNaN(opening)) {
                weighted += OPENING_WEIGHT * opening;
                weightSum += OPENING_WEIGHT;
            }
            if (!Double.isNaN(middle)) {
                weighted += MIDDLEGAME_WEIGHT * middle;
                weightSum += MIDDLEGAME_WEIGHT;
            }
            if (!Double.isNaN(end)) {
                weighted += ENDGAME_WEIGHT * end;
                weightSum += ENDGAME_WEIGHT;
            }
            // 精确权重总和为 1.01520；按实际存在阶段的权重和归一化，保证满分为 100，
            // 同时兼容没有中局/残局的短棋谱。
            double total = weightSum <= 0d ? Double.NaN : weighted / weightSum;
            total = Double.isNaN(total) ? total : Math.max(0d, Math.min(100d, total));
            // 阶段权重继续保持原值，避免残局靠大量好棋“刷分”；但 L>1000 的大漏额外
            // 施加与阶段无关的平滑惩罚。最差单步 100 分时不降，0 分时总评最多保留 70%。
            if (!Double.isNaN(total) && !Double.isNaN(worstSevereScore)) {
                double severeFactor = 0.70d + 0.30d * (worstSevereScore / 100d);
                total = Math.max(0d, Math.min(100d, total * severeFactor));
            }
            return new SideReport(total, opening, middle, end, errors);
        }

        double softMin(int phase) {
            if (counts[phase] <= 0) return Double.NaN;
            double averageExp = expSums[phase] / counts[phase];
            if (averageExp <= 0d) return 0d;
            double value = -tau * Math.log(averageExp);
            return Math.max(0d, Math.min(100d, value));
        }
    }
}
