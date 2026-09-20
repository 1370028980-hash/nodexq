package com.tyl.xiangqi.ndxq;

import android.content.SharedPreferences;

/** 评测等级分结算与连胜加速规则。 */
final class EvaluationRatingController {
    private static final String PREF_WIN_STREAK = "evaluation_win_streak";

    private final MainActivity host;

    EvaluationRatingController(MainActivity host) {
        this.host = host;
    }

    void updateAfterGame(GameEndType type) {
        if (host.ratingCounted) return;
        if (!host.ratingEligible || host.ratingDisqualified || host.selfAnalysisMode) {
            if (host.ratingDisqualified) {
                host.appendLog("本局等级分不计算；既有连胜记录保持不变。\n");
            }
            return;
        }
        if ((type == GameEndType.AGREED_DRAW || type == GameEndType.NO_CAPTURE_DRAW)
                && host.currentPly < 50) {
            clearWinStreak();
            host.appendLog("本局提和发生在前25回合，等级分不变；连胜已清零。\n");
            host.ratingCounted = true;
            return;
        }

        boolean playerWon = isPlayerWin(type);
        int beforeRating = host.playerRating;
        int opponentRating = PlayerRatingCalculator.difficultyRating(host.selectedDifficultyIndex);
        int streak = playerWon ? winStreak() + 1 : 0;
        double actual = type == GameEndType.AGREED_DRAW || type == GameEndType.NO_CAPTURE_DRAW
                ? 0.5 : (playerWon ? 1.0 : 0.0);
        // 连败不增加惩罚：失败和和棋均按基础 K=32 结算，只有胜局按连胜阶段加速。
        int kFactor = playerWon ? kFactorForStreak(streak) : 32;

        host.playerRating = PlayerRatingCalculator.update(beforeRating, opponentRating, actual,
                kFactor);
        saveWinStreak(streak);
        host.ratingCounted = true;
        host.saveLauncherPreferences();
        int change = host.playerRating - beforeRating;
        String signedChange = change > 0 ? "+" + change : String.valueOf(change);
        host.appendLog("玩家等级分 " + beforeRating + " -> " + host.playerRating
                + "（" + signedChange + "，K=" + kFactor + "，连胜 " + streak + "）。\n");
        EvaluationRatingHistory.append(host, beforeRating, host.playerRating, opponentRating,
                kFactor, streak, playerWon ? "胜" : (actual == 0.5 ? "和" : "负"));
    }

    /** 维护入口：将评测等级分和连胜状态恢复到初始值。 */
    void resetRatingToMinimum() {
        host.playerRating = MainActivity.MIN_PLAYER_RATING;
        preferences().edit().putInt(PREF_WIN_STREAK, 0).apply();
        host.ratingCounted = false;
        host.saveLauncherPreferences();
        host.updateLauncherRatingText();
        host.appendLog("维护入口已将评测等级分重置为 " + MainActivity.MIN_PLAYER_RATING + "，连胜已清零。\n");
    }

    private boolean isPlayerWin(GameEndType type) {
        return (type == GameEndType.RED_WIN && !host.enginePlaysRed)
                || (type == GameEndType.BLACK_WIN && host.enginePlaysRed);
    }

    private int winStreak() {
        return Math.max(0, preferences().getInt(PREF_WIN_STREAK, 0));
    }

    private void clearWinStreak() {
        saveWinStreak(0);
    }

    private void saveWinStreak(int value) {
        preferences().edit().putInt(PREF_WIN_STREAK, Math.max(0, value)).apply();
    }

    private SharedPreferences preferences() {
        return host.getSharedPreferences(MainActivity.PREFS, MainActivity.MODE_PRIVATE);
    }

    private int kFactorForStreak(int streak) {
        if (streak >= 10) return 108;
        if (streak >= 5) return 64;
        return 32;
    }
}
