package com.tyl.xiangqi.ndxq;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.tyl.xiangqi.ndxq.core.XiangqiRules;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** 自选难度的起始局面选择与 balance.txt 资源读取。 */
final class OpeningPositionController {
    interface Callback {
        void onSelected(String fen);
    }

    static final String PREF_RANDOM_BALANCED = "custom_random_balanced_opening";
    private static final String ASSET_NAME = "balance.txt";
    private final MainActivity host;
    private final SecureRandom random = new SecureRandom();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService loader = Executors.newSingleThreadExecutor();
    private volatile List<String> cachedPositions;
    private volatile boolean shutdown;
    private volatile boolean loadScheduled;

    OpeningPositionController(MainActivity host) {
        // MainActivity 的字段初始化发生在 attachBaseContext() 之前；这里只保存引用，
        // 不能在构造器里调用 getApplicationContext()/getSharedPreferences()/getAssets()。
        this.host = host;
    }

    boolean isRandomBalancedEnabled() {
        return host.getSharedPreferences(MainActivity.PREFS, Context.MODE_PRIVATE)
                .getBoolean(PREF_RANDOM_BALANCED, false);
    }

    void setRandomBalancedEnabled(boolean enabled) {
        host.getSharedPreferences(MainActivity.PREFS, Context.MODE_PRIVATE)
                .edit().putBoolean(PREF_RANDOM_BALANCED, enabled).apply();
    }

    void preloadIfEnabled() {
        if (isRandomBalancedEnabled()) preload();
    }

    void preload() {
        if (cachedPositions != null || shutdown || loader.isShutdown()) return;
        synchronized (this) {
            if (cachedPositions != null || shutdown || loader.isShutdown() || loadScheduled) return;
            loadScheduled = true;
        }
        try {
            loader.execute(this::loadPositions);
        } catch (RuntimeException ignored) {
            loadScheduled = false;
        }
    }

    boolean isReady() {
        return cachedPositions != null;
    }

    void chooseStartingFenAsync(String normalFen, Callback callback) {
        if (callback == null || shutdown || loader.isShutdown()) return;
        loader.execute(() -> {
            String selected = chooseValidStartingFen(loadPositions(), normalFen);
            if (shutdown) return;
            mainHandler.post(() -> {
                if (!shutdown) callback.onSelected(selected);
            });
        });
    }

    void shutdown() {
        shutdown = true;
        mainHandler.removeCallbacksAndMessages(null);
        loader.shutdownNow();
    }

    private String chooseValidStartingFen(List<String> positions, String normalFen) {
        if (positions == null || positions.isEmpty()) return normalFen;
        int attempts = Math.min(32, positions.size());
        for (int i = 0; i < attempts; i++) {
            String fen = positions.get(random.nextInt(positions.size()));
            try {
                XiangqiRules.fromFen(fen);
                return fen;
            } catch (RuntimeException ignored) {
            }
        }
        return normalFen;
    }

    private synchronized List<String> loadPositions() {
        if (cachedPositions != null) return cachedPositions;
        ArrayList<String> positions = new ArrayList<String>();
        try (InputStream input = host.getAssets().open(ASSET_NAME);
             BufferedReader reader = new BufferedReader(new InputStreamReader(
                     input, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String fen = line.trim();
                if (fen.isEmpty() || fen.startsWith("#")) continue;
                positions.add(fen);
            }
        } catch (Exception ignored) {
            // The normal starting position remains the deterministic fallback.
        }
        cachedPositions = positions;
        loadScheduled = false;
        return cachedPositions;
    }
}
