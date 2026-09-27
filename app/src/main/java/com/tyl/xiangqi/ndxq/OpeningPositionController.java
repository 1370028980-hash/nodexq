package com.tyl.xiangqi.ndxq;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.os.Handler;
import android.os.Looper;

import com.tyl.xiangqi.ndxq.core.XiangqiRules;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
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
    private volatile boolean assetRandomAccessReady;
    private volatile long assetLength;

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
        if (isReady() || shutdown || loader.isShutdown()) return;
        synchronized (this) {
            if (isReady() || shutdown || loader.isShutdown() || loadScheduled) return;
            loadScheduled = true;
        }
        try {
            loader.execute(() -> {
                try {
                    if (!prepareRandomAccess()) loadPositions();
                } finally {
                    loadScheduled = false;
                }
            });
        } catch (RuntimeException ignored) {
            loadScheduled = false;
        }
    }

    boolean isReady() {
        return assetRandomAccessReady || cachedPositions != null;
    }

    void chooseStartingFenAsync(String normalFen, Callback callback) {
        if (callback == null || shutdown || loader.isShutdown()) return;
        loader.execute(() -> {
            if (!assetRandomAccessReady) prepareRandomAccess();
            String selected = chooseStartingFen(normalFen);
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

    private String chooseStartingFen(String normalFen) {
        if (assetRandomAccessReady) {
            for (int i = 0; i < 16; i++) {
                String fen = readRandomFen();
                if (isValidFen(fen)) return fen;
            }
            // Fall back to one full scan only when random offsets miss valid lines.
        }
        return chooseValidStartingFen(loadPositions(), normalFen);
    }

    private boolean prepareRandomAccess() {
        if (assetRandomAccessReady) return true;
        try (AssetFileDescriptor descriptor = host.getAssets().openFd(ASSET_NAME)) {
            long length = descriptor.getLength();
            if (length <= 0L) return false;
            assetLength = length;
            assetRandomAccessReady = true;
            return true;
        } catch (IOException | RuntimeException ignored) {
            // Compressed or legacy assets use the full-scan fallback.
            return false;
        }
    }

    private String readRandomFen() {
        AssetFileDescriptor descriptor = null;
        FileInputStream input = null;
        try {
            descriptor = host.getAssets().openFd(ASSET_NAME);
            long length = assetLength > 0L ? assetLength : descriptor.getLength();
            if (length <= 0L) return null;
            long offset = nextRandomOffset(length);
            input = new FileInputStream(descriptor.getFileDescriptor());
            input.getChannel().position(descriptor.getStartOffset() + offset);
            BufferedReader reader = new BufferedReader(new InputStreamReader(
                    input, StandardCharsets.UTF_8));
            if (offset > 0L) reader.readLine();
            for (int i = 0; i < 8; i++) {
                String line = reader.readLine();
                if (line == null) return null;
                String fen = line.trim();
                if (!fen.isEmpty() && !fen.startsWith("#")) return fen;
            }
        } catch (IOException | RuntimeException ignored) {
            return null;
        } finally {
            if (input != null) {
                try { input.close(); } catch (IOException ignored) {}
            }
            if (descriptor != null) {
                try { descriptor.close(); } catch (IOException ignored) {}
            }
        }
        return null;
    }

    private long nextRandomOffset(long bound) {
        return (random.nextLong() & Long.MAX_VALUE) % bound;
    }

    private boolean isValidFen(String fen) {
        if (fen == null || fen.isEmpty()) return false;
        try {
            XiangqiRules.fromFen(fen);
            return true;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private String chooseValidStartingFen(List<String> positions, String normalFen) {
        if (positions == null || positions.isEmpty()) return normalFen;
        int attempts = Math.min(32, positions.size());
        for (int i = 0; i < attempts; i++) {
            String fen = positions.get(random.nextInt(positions.size()));
            if (isValidFen(fen)) return fen;
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
