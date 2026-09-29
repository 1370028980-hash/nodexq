package com.tyl.xiangqi.ndxq;

import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

/** 棋谱自动播放状态机，独立于导航按钮和棋盘页面布局。 */
final class PlaybackController {
    private static final int DEFAULT_DELAY_MS = 1000;
    private static final int MIN_DELAY_MS = 100;
    private static final int MAX_DELAY_MS = 60000;
    private final MainActivity host;
    private final Handler handler;
    private final Runnable step = new Runnable() {
        @Override public void run() {
            if (!running || host.boardView == null || host.currentPly >= host.engineMoves.size()) {
                finish();
                return;
            }
            long startedAt = SystemClock.uptimeMillis();
            host.navigateToPly(host.currentPly + 1);
            if (running && host.currentPly < host.engineMoves.size()) {
                long dueAt = startedAt + host.playbackDelayMs();
                handler.postDelayed(this, Math.max(0L, dueAt - SystemClock.uptimeMillis()));
            } else {
                finish();
            }
        }
    };
    private boolean running;

    PlaybackController(MainActivity host) {
        this.host = host;
        // MainActivity 字段初始化早于 Context attach；使用静态主 Looper，避免此处
        // 通过尚未 attach 的 Activity Context 调用 getMainLooper() 导致启动闪退。
        this.handler = new Handler(Looper.getMainLooper());
    }

    static int normalizeDelayMs(int value) {
        return Math.max(MIN_DELAY_MS, Math.min(MAX_DELAY_MS, value));
    }

    void start() {
        stop();
        if (host.boardView == null || host.currentPly >= host.engineMoves.size()) return;
        running = true;
        host.refreshPlaybackButtonState();
        step.run();
    }

    void stop() {
        running = false;
        handler.removeCallbacks(step);
        host.refreshPlaybackButtonState();
    }

    private void finish() {
        if (!running) return;
        running = false;
        handler.removeCallbacks(step);
        host.refreshPlaybackButtonState();
    }

    boolean isRunning() {
        return running;
    }

    static int defaultDelayMs() {
        return DEFAULT_DELAY_MS;
    }
}
