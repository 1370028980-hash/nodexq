package com.tyl.xiangqi.ndxq;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.tyl.xiangqi.ndxq.ui.ChessBoardView;
import com.tyl.xiangqi.ndxq.ui.SegmentedDifficultyView;
import com.tyl.xiangqi.ndxq.ui.UiTheme;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** 皮肤文件、全局背景图与首页背景对比度的运行时协调。 */
final class SkinRuntimeController {
    private final MainActivity host;
    private final ExecutorService globalBackgroundLoader = Executors.newSingleThreadExecutor();
    private volatile int globalBackgroundRequestGeneration;
    private final Object globalBackgroundLock = new Object();
    private Bitmap cachedGlobalBackgroundBitmap;
    private String cachedGlobalBackgroundSkin = "";
    private long cachedGlobalBackgroundSignature = Long.MIN_VALUE;

    SkinRuntimeController(MainActivity host) {
        this.host = host;
    }

    void preloadCurrentSkin() {
        final String startupSkin = sanitizeSkinName(host.currentSkinName);
        new Thread(() -> {
            if (MainActivity.DEFAULT_SKIN_NAME.equals(startupSkin)) {
                ChessBoardView.preloadBundledDefaultSkin(host.getApplicationContext());
            } else if (!ChessBoardView.preloadSkin(skinDirectory(startupSkin))) {
                ChessBoardView.preloadBundledDefaultSkin(host.getApplicationContext());
            }
        }, "Skin-preload").start();
    }

    File skinDirectory(String skinName) {
        return host.storageManager().skinDirectory(sanitizeSkinName(skinName));
    }

    private File findSkinImageFile(File dir, String baseName) {
        return host.storageManager().findImage(dir, baseName);
    }

    int globalBackgroundColor() {
        return UiTheme.background(host);
    }

    int globalRootBackgroundColor() {
        return UiTheme.backgroundRgb(host);
    }

    int homepageButtonColor() {
        return UiTheme.homeButton(host);
    }

    int globalBackgroundTextColor() {
        return UiTheme.textOnBackground(host);
    }

    private boolean hasActiveGlobalBackgroundImage() {
        synchronized (globalBackgroundLock) {
            return sanitizeSkinName(host.currentSkinName).equals(cachedGlobalBackgroundSkin)
                    && cachedGlobalBackgroundBitmap != null
                    && !cachedGlobalBackgroundBitmap.isRecycled();
        }
    }

    int globalSurfaceFillColor() {
        return globalBackgroundColor();
    }

    /** 异步读取当前皮肤的 back 图片，并以 generation 丢弃过期结果。 */
    void requestGlobalBackgroundRefresh() {
        final String skin = sanitizeSkinName(host.currentSkinName);
        final int generation = ++globalBackgroundRequestGeneration;
        if (host.appRoot != null && !hasActiveGlobalBackgroundImage()) {
            host.appRoot.setBackgroundColor(globalRootBackgroundColor());
        }
        globalBackgroundLoader.execute(() -> {
            if (generation != globalBackgroundRequestGeneration) return;
            File file = null;
            try {
                if (host.ensureNodeStorageReady(false)) {
                    file = findSkinImageFile(skinDirectory(skin), "back");
                }
            } catch (Exception ignored) {}
            final long signature = file == null ? Long.MIN_VALUE
                    : (file.length() * 31L + file.lastModified());
            synchronized (globalBackgroundLock) {
                if (skin.equals(cachedGlobalBackgroundSkin)
                        && signature == cachedGlobalBackgroundSignature) {
                    host.handler.post(() -> {
                        if (generation == globalBackgroundRequestGeneration) {
                            applyGlobalBackgroundToAppRoot();
                        }
                    });
                    return;
                }
            }
            if (generation != globalBackgroundRequestGeneration) return;
            Bitmap decoded = file == null ? null : decodeSampledGlobalBackground(file);
            if (generation != globalBackgroundRequestGeneration) return;
            synchronized (globalBackgroundLock) {
                cachedGlobalBackgroundSkin = skin;
                cachedGlobalBackgroundSignature = signature;
                cachedGlobalBackgroundBitmap = decoded;
            }
            host.handler.post(() -> {
                if (generation != globalBackgroundRequestGeneration
                        || !skin.equals(sanitizeSkinName(host.currentSkinName))) return;
                refreshGlobalSurfaceTheme();
            });
        });
    }

    private Bitmap decodeSampledGlobalBackground(File file) {
        if (file == null || !file.isFile()) return null;
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(file.getAbsolutePath(), bounds);
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null;
            int targetW = Math.max(720,
                    host.getResources().getDisplayMetrics().widthPixels * 2);
            int targetH = Math.max(1280,
                    host.getResources().getDisplayMetrics().heightPixels * 2);
            int sample = 1;
            while (bounds.outWidth / (sample * 2) >= targetW
                    && bounds.outHeight / (sample * 2) >= targetH) {
                sample *= 2;
            }
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = Math.max(1, sample);
            options.inPreferredConfig = Bitmap.Config.ARGB_8888;
            return BitmapFactory.decodeFile(file.getAbsolutePath(), options);
        } catch (OutOfMemoryError | RuntimeException e) {
            return null;
        }
    }

    private void applyGlobalBackgroundToAppRoot() {
        if (host.appRoot == null) return;
        Bitmap bitmap = null;
        synchronized (globalBackgroundLock) {
            if (sanitizeSkinName(host.currentSkinName).equals(cachedGlobalBackgroundSkin)) {
                bitmap = cachedGlobalBackgroundBitmap;
            }
        }
        if (bitmap != null && !bitmap.isRecycled()) {
            BitmapDrawable drawable = new BitmapDrawable(host.getResources(), bitmap);
            drawable.setGravity(Gravity.FILL);
            host.appRoot.setBackground(drawable);
        } else {
            host.appRoot.setBackgroundColor(globalRootBackgroundColor());
        }
    }

    private void applyGlobalSurfaceBackgroundOnly() {
        applyGlobalBackgroundToAppRoot();
        int surface = globalSurfaceFillColor();
        if (host.boardView != null) host.boardView.setBoardSideFillColor(surface);
        if (host.editModeToolbar != null) host.editModeToolbar.setBackgroundColor(surface);
        if (host.editModeTopRow != null) host.editModeTopRow.setBackgroundColor(surface);
        if (host.editModeTabs != null) host.editModeTabs.setBackgroundColor(surface);
        host.updatePlayerLabels();
        host.refreshGameTabs();
        host.styleAnalysisButton();
        refreshLauncherTextContrast();
        invalidateViewTree(host.appRoot);
    }

    void refreshGlobalSurfaceTheme() {
        applyGlobalSurfaceBackgroundOnly();
        host.updateGameContent();
    }

    void markLauncherTextBackground(TextView view, String backgroundKind) {
        if (view == null) return;
        view.setTag(backgroundKind);
        int actual = globalRootBackgroundColor();
        int home = homepageButtonColor();
        int highlight = UiTheme.highlight(host);
        if (MainActivity.LAUNCHER_TEXT_BG_HOME.equals(backgroundKind)
                || MainActivity.LAUNCHER_TEXT_BG_HOME_DOUBLE.equals(backgroundKind)
                || MainActivity.LAUNCHER_TEXT_BG_HOME_HIGHLIGHT.equals(backgroundKind)) {
            actual = UiTheme.compositeOver(actual, home);
        } else if (MainActivity.LAUNCHER_TEXT_BG_HIGHLIGHT.equals(backgroundKind)) {
            actual = UiTheme.compositeOver(actual, highlight);
        }
        if (MainActivity.LAUNCHER_TEXT_BG_HOME_DOUBLE.equals(backgroundKind)) {
            actual = UiTheme.compositeOver(actual, home);
        } else if (MainActivity.LAUNCHER_TEXT_BG_HOME_HIGHLIGHT.equals(backgroundKind)) {
            actual = UiTheme.compositeOver(actual, highlight);
        }
        view.setTextColor(UiTheme.textForColor(actual));
    }

    void scheduleLauncherTextContrastRefresh(View anchor) {
        if (host.launcherContrastRefreshPosted) return;
        host.launcherContrastRefreshPosted = true;
        View target = anchor == null ? host.launcherScreenRoot : anchor;
        if (target == null) {
            host.launcherContrastRefreshPosted = false;
            return;
        }
        target.postOnAnimation(() -> {
            host.launcherContrastRefreshPosted = false;
            refreshLauncherTextContrast();
        });
    }

    void refreshLauncherTextContrastFor(TextView view) {
        applyLauncherTextContrast(view);
    }

    private void refreshLauncherTextContrast() {
        if (host.launcherScreenRoot == null || host.launcherScreenRoot.getParent() == null) return;
        refreshLauncherTextContrastInTree(host.launcherScreenRoot);
        SegmentedDifficultyView difficulty = host.launcherDifficultyView;
        if (difficulty != null) {
            difficulty.setLabelTextColor(launcherTextColorFor(difficulty, Color.TRANSPARENT));
        }
    }

    private void refreshLauncherTextContrastInTree(View view) {
        if (view == null) return;
        if (view instanceof TextView) applyLauncherTextContrast((TextView) view);
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                refreshLauncherTextContrastInTree(group.getChildAt(i));
            }
        }
    }

    private void applyLauncherTextContrast(TextView view) {
        if (view == null) return;
        Object tag = view.getTag();
        if (!(tag instanceof String)) return;
        String kind = (String) tag;
        int overlay;
        if (MainActivity.LAUNCHER_TEXT_BG_HOME_DOUBLE.equals(kind)
                || MainActivity.LAUNCHER_TEXT_BG_HOME_HIGHLIGHT.equals(kind)) {
            int sampled = sampleGlobalBackgroundBehind(view);
            int actual = UiTheme.compositeOver(sampled, homepageButtonColor());
            actual = UiTheme.compositeOver(actual,
                    MainActivity.LAUNCHER_TEXT_BG_HOME_DOUBLE.equals(kind)
                            ? homepageButtonColor() : UiTheme.highlight(host));
            view.setTextColor(UiTheme.textForColor(actual));
            return;
        }
        if (MainActivity.LAUNCHER_TEXT_BG_HOME.equals(kind)) overlay = homepageButtonColor();
        else if (MainActivity.LAUNCHER_TEXT_BG_HIGHLIGHT.equals(kind)) {
            overlay = UiTheme.highlight(host);
        } else if (MainActivity.LAUNCHER_TEXT_BG_GLOBAL.equals(kind)) {
            overlay = Color.TRANSPARENT;
        } else return;
        view.setTextColor(launcherTextColorFor(view, overlay));
    }

    private int launcherTextColorFor(View anchor, int overlayColor) {
        int sampled = sampleGlobalBackgroundBehind(anchor);
        int actual = UiTheme.compositeOver(sampled, overlayColor);
        return UiTheme.textForColor(actual);
    }

    private int sampleGlobalBackgroundBehind(View anchor) {
        Bitmap bitmap = null;
        synchronized (globalBackgroundLock) {
            if (sanitizeSkinName(host.currentSkinName).equals(cachedGlobalBackgroundSkin)) {
                bitmap = cachedGlobalBackgroundBitmap;
            }
        }
        if (bitmap == null || bitmap.isRecycled() || host.appRoot == null
                || host.appRoot.getWidth() <= 0 || host.appRoot.getHeight() <= 0
                || anchor == null || anchor.getWidth() <= 0 || anchor.getHeight() <= 0) {
            return globalRootBackgroundColor();
        }
        try {
            int[] rootLoc = new int[2];
            int[] viewLoc = new int[2];
            host.appRoot.getLocationOnScreen(rootLoc);
            anchor.getLocationOnScreen(viewLoc);
            float cx = viewLoc[0] - rootLoc[0] + anchor.getWidth() * 0.5f;
            float cy = viewLoc[1] - rootLoc[1] + anchor.getHeight() * 0.5f;
            int bx = host.clamp(Math.round(cx * bitmap.getWidth()
                            / Math.max(1f, host.appRoot.getWidth())),
                    0, bitmap.getWidth() - 1);
            int by = host.clamp(Math.round(cy * bitmap.getHeight()
                            / Math.max(1f, host.appRoot.getHeight())),
                    0, bitmap.getHeight() - 1);
            int radiusX = Math.max(1, bitmap.getWidth() / 180);
            int radiusY = Math.max(1, bitmap.getHeight() / 320);
            long r = 0, g = 0, b = 0, count = 0;
            for (int y = Math.max(0, by - radiusY);
                    y <= Math.min(bitmap.getHeight() - 1, by + radiusY); y++) {
                for (int x = Math.max(0, bx - radiusX);
                        x <= Math.min(bitmap.getWidth() - 1, bx + radiusX); x++) {
                    int color = bitmap.getPixel(x, y);
                    r += Color.red(color);
                    g += Color.green(color);
                    b += Color.blue(color);
                    count++;
                }
            }
            if (count > 0) return Color.rgb((int) (r / count), (int) (g / count),
                    (int) (b / count));
        } catch (RuntimeException ignored) {}
        return globalRootBackgroundColor();
    }

    String sanitizeSkinName(String name) {
        String value = name == null ? "" : name.trim();
        if (value.length() == 0 || ".".equals(value) || "..".equals(value)
                || value.contains("/") || value.contains("\\")) {
            return MainActivity.DEFAULT_SKIN_NAME;
        }
        return value;
    }

    String skinPieceSizeKey(String skinName) {
        return "skin_piece_size::" + sanitizeSkinName(skinName);
    }

    private String skinGridKey(String skinName) {
        return "skin_grid::" + sanitizeSkinName(skinName);
    }

    float[] loadSkinGrid(String skinName) {
        String raw = host.getSharedPreferences(MainActivity.PREFS, MainActivity.MODE_PRIVATE)
                .getString(skinGridKey(skinName), "");
        if (raw != null && raw.length() > 0) {
            String[] parts = raw.split(",");
            if (parts.length == 8) {
                float[] result = new float[8];
                try {
                    for (int i = 0; i < 8; i++) result[i] = Float.parseFloat(parts[i]);
                    return result;
                } catch (Exception ignored) {}
            }
        }
        return ChessBoardView.defaultGridCorners();
    }

    void saveSkinGrid(android.content.SharedPreferences.Editor editor,
                      String skinName, float[] corners) {
        if (editor == null || corners == null || corners.length < 8) return;
        StringBuilder value = new StringBuilder();
        for (int i = 0; i < 8; i++) {
            if (i > 0) value.append(',');
            value.append(corners[i]);
        }
        editor.putString(skinGridKey(skinName), value.toString());
    }

    List<String> listAvailableSkinNames() {
        ArrayList<String> names = new ArrayList<String>();
        names.add(MainActivity.DEFAULT_SKIN_NAME);
        File root = host.storageManager().pictureDirectory();
        File[] dirs = null;
        try { dirs = root.listFiles(); } catch (SecurityException ignored) {}
        if (dirs != null) {
            for (File dir : dirs) {
                if (dir != null && dir.isDirectory() && !dir.isHidden()) {
                    String name = sanitizeSkinName(dir.getName());
                    if (!MainActivity.DEFAULT_SKIN_NAME.equalsIgnoreCase(name)
                            && !names.contains(name)) {
                        names.add(name);
                    }
                }
            }
        }
        if (names.size() > 1) {
            List<String> external = new ArrayList<String>(names.subList(1, names.size()));
            Collections.sort(external, (a, b) -> a.compareToIgnoreCase(b));
            names.clear();
            names.add(MainActivity.DEFAULT_SKIN_NAME);
            names.addAll(external);
        }
        return names;
    }

    boolean applyCurrentSkinToBoard(ChessBoardView target, boolean showMessage) {
        if (target == null) return false;
        target.setArrowColors(host.redArrowColor, host.blackArrowColor,
                Color.rgb(255, 183, 0), Color.rgb(76, 175, 80));
        target.setBoardSideFillColor(globalSurfaceFillColor());
        android.content.SharedPreferences sp = host.getSharedPreferences(
                MainActivity.PREFS, MainActivity.MODE_PRIVATE);
        String skin = sanitizeSkinName(host.currentSkinName);
        int size = host.clamp(sp.getInt(skinPieceSizeKey(skin),
                host.currentSkinPieceSizePercent), 55, 125);
        float[] grid = loadSkinGrid(skin);
        if (MainActivity.DEFAULT_SKIN_NAME.equals(skin)) {
            boolean loaded = target.loadBundledDefaultSkin(size, grid);
            if (loaded) host.currentSkinPieceSizePercent = size;
            return loaded;
        }

        boolean storageReady = host.ensureNodeStorageReady(false);
        boolean loaded = storageReady && target.loadSkin(skinDirectory(skin), size, grid);
        if (loaded) {
            host.currentSkinPieceSizePercent = size;
            return true;
        }

        int fallbackSize = host.clamp(sp.getInt(skinPieceSizeKey(
                MainActivity.DEFAULT_SKIN_NAME), 96), 55, 125);
        boolean fallbackLoaded = target.loadBundledDefaultSkin(fallbackSize,
                loadSkinGrid(MainActivity.DEFAULT_SKIN_NAME));
        if (storageReady) {
            host.currentSkinName = MainActivity.DEFAULT_SKIN_NAME;
            host.currentSkinPieceSizePercent = fallbackSize;
            host.saveLauncherPreferences();
        }
        if (showMessage) {
            String message = storageReady
                    ? "皮肤读取失败，已使用内置 default。请检查 /storage/emulated/0/nodexq/pic/"
                        + skin + " 中的 board、br…rp 图片"
                    : "当前无法读取外部皮肤，已临时使用内置 default；授权文件访问后可再次选择。";
            android.widget.Toast.makeText(host, message,
                    android.widget.Toast.LENGTH_LONG).show();
        }
        return fallbackLoaded;
    }

    void shutdown() {
        globalBackgroundRequestGeneration++;
        globalBackgroundLoader.shutdownNow();
    }

    void invalidateViewTree(View view) {
        if (view == null) return;
        view.invalidate();
        if (!(view instanceof ViewGroup)) return;
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) {
            invalidateViewTree(group.getChildAt(i));
        }
    }
}
