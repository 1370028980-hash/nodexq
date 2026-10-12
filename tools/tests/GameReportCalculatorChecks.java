import com.tyl.xiangqi.ndxq.core.GameReportCalculator;
import com.tyl.xiangqi.ndxq.core.PositionScore;
import com.tyl.xiangqi.ndxq.core.XiangqiRules;

import java.util.Collections;

/** 无测试框架依赖的评分回归用例；本次按要求仅提供源码，未编译执行。 */
public final class GameReportCalculatorChecks {
    public static void main(String[] args) {
        close(quality(1000, 0, 0, 0).score, 55, "普通分区间保留");
        GameReportCalculator.MoveQuality high = quality(5200, 0, 4100, 0);
        check(high.score > 70 && high.score < 80 && !high.isError(), "大优波动不过度判错");
        check(quality(5200, 0, 0, 0).isError(), "大优丢成均势仍判错");
        check(quality(5200, 0, -4100, 0).isError(), "优势反转仍判错");
        close(quality(4500, 0, 6000, 20).score, 100, "进入我方 M20 不扣分");
        close(quality(4500, 0, 6000, 100).score, 100, "进入我方长杀不扣分");
        check(quality(6000, 31, 0, 0).isError(), "M31 丢成均势不能满分");
        check(quality(6000, 20, 4500, 0).score < 100, "杀棋消失但仍大优有适度损失");
        close(quality(-6000, 31, 0, 0).score, 100, "解除对方长杀不扣分");
        check(quality(6000, 20, -6000, 20).isError(), "我方杀变对方杀");
        check(!quality(6000, 1, 6000, 100).isError(), "仍有强制杀只评价效率");
        close(quality(4500, 0, 6000, PositionScore.TERMINAL).score, 100, "实际获胜不扣分");
        check(quality(4500, 0, -6000, PositionScore.TERMINAL).isError(), "实际输棋判错");
        close(quality(6000, 0, 5900, 0).score, 100, "普通 6000 不是 M1");
        check(Math.abs(quality(6000, 0, 5900, 0).loss
                - quality(6001, 0, 5900, 0).loss) < 1, "6000 附近平滑");
        check(Math.abs(quality(7000, 0, 5500, 0).loss
                - quality(7000, 0, 5501, 0).loss) < 1, "5500 附近平滑");
        check(PositionScore.terminalScore(XiangqiRules.fromFen(XiangqiRules.START_FEN), true) == null,
                "正常局面不是终局");
        check(PositionScore.terminalScore(XiangqiRules.fromFen(
                "4k4/9/9/9/9/9/9/9/9/9 w - - 0 1"), true) == -6000, "缺帅确认黑胜");

        int[] scores = {-10000, -6001, -6000, -5500, -4000, 0, 4000, 5500, 6000, 6001, 10000};
        int[] mates = {0, 1, 20, 31, 100, PositionScore.TERMINAL};
        for (int before : scores) for (int after : scores) {
            for (int beforeMate : mates) for (int afterMate : mates) {
                if ((before == 0 && beforeMate != 0) || (after == 0 && afterMate != 0)) continue;
                GameReportCalculator.MoveQuality q = quality(before, beforeMate, after, afterMate);
                GameReportCalculator.MoveQuality black = GameReportCalculator.scoreMove(
                        -before, beforeMate, -after, afterMate, false);
                close(q.score, black.score, "红黑对称");
                check(Double.isFinite(q.score) && q.score >= 0 && q.score <= 100, "分数范围");
                GameReportCalculator.Report report = report(before, beforeMate, after, afterMate, 15);
                check(report.complete && report.red.errorCount == (q.isError() ? 1 : 0), "报告判错一致");
                check("错".equals(q.label()) == q.isError(), "徽标判错一致");
                check(report.errorPlies.contains(1) == q.isError(), "曲线标错一致");
            }
        }
        close(report(100, 0, 100, 0, 0.000001).red.total, 100, "极小 τ 满分不下溢");
        close(report(100, 0, 100, 0, Double.NaN).red.total, 100, "非法 τ 回退默认");
        for (int before : new int[]{Integer.MIN_VALUE, Integer.MAX_VALUE}) {
            check(Double.isFinite(quality(before, 0, 0, 0).score), "极端整数不溢出");
        }
        System.out.println("GameReportCalculator checks passed");
    }

    private static GameReportCalculator.MoveQuality quality(int before, int beforeMate, int after, int afterMate) {
        return GameReportCalculator.scoreMove(before, beforeMate, after, afterMate, true);
    }

    private static GameReportCalculator.Report report(int before, int beforeMate, int after, int afterMate, double tau) {
        return GameReportCalculator.calculate(before, beforeMate, true, true,
                Collections.singletonList(after), Collections.singletonList(afterMate),
                Collections.singletonList(true), 1, -1, tau);
    }

    private static void close(double actual, double expected, String label) {
        check(Double.isFinite(actual) && Math.abs(actual - expected) < 0.000001, label);
    }

    private static void check(boolean condition, String label) {
        if (!condition) throw new AssertionError(label);
    }
}
