package com.tyl.xiangqi.ndxq.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PointF;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import android.view.View;

import com.tyl.xiangqi.ndxq.R;
import com.tyl.xiangqi.ndxq.core.Move;
import com.tyl.xiangqi.ndxq.core.XiangqiRules;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 手机端棋盘控件。
 *
 * 迁移原则：
 * 1. 棋盘数据仍然使用 PC 端 public-Xiangqi 的 char[10][9]；
 * 2. 走子仍然走 XiangqiRules.canPieceMove -> isLegalMove -> 送将回滚；
 * 3. 视觉层不再手绘粗糙按钮棋盘，而是使用用户指定的“0-默认（透明）”棋盘和棋子；
 * 4. 坐标换算按 1120x1300 棋盘 PNG 实测：交叉点从约 (99.5,131.5) 开始，间距约 115。
 */
public class ChessBoardView extends View {
    public interface Listener {
        void onMoveMade(Move move, char movedPiece, char capturedPiece, String fenAfterMove, boolean redToMoveNow);
        void onMessage(String message);
        void onEditBoardChanged();
    }

    /** 动态网格校准回调：拖动任意角点或整体平移时实时返回当前网格。 */
    public interface GridAdjustmentListener {
        void onGridChanged(float[] corners);
    }

    /** V19.2：校准角点的持久选中状态，供方向键微调 UI 同步显示。 */
    public interface GridHandleSelectionListener {
        void onGridHandleSelected(int handle);
    }

    private static final int DEFAULT_BOARD_IMAGE_W = 1120;
    private static final int DEFAULT_BOARD_IMAGE_H = 1300;
    /** 棋盘缩小时左右留白，与棋盘页浅绿色信息区保持一致。 */
    private int boardSideFillColor = UiTheme.DEFAULT_BACKGROUND_COLOR;
    // 内置 default 皮肤的交叉点框架，使用归一化四角；外部皮肤可保存各自的可调四边形框架。
    private static final float DEFAULT_TL_X = 99.5f / 1120f;
    private static final float DEFAULT_TL_Y = 131.5f / 1300f;
    private static final float DEFAULT_TR_X = (99.5f + 8f * 115f) / 1120f;
    private static final float DEFAULT_TR_Y = DEFAULT_TL_Y;
    private static final float DEFAULT_BL_X = DEFAULT_TL_X;
    private static final float DEFAULT_BL_Y = (131.5f + 9f * 115f) / 1300f;
    private static final float DEFAULT_BR_X = DEFAULT_TR_X;
    private static final float DEFAULT_BR_Y = DEFAULT_BL_Y;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Map<Character, Bitmap> pieceBitmapMap = new HashMap<>();
    private final PieceShadowRenderer pieceShadowRenderer = new PieceShadowRenderer();
    private final List<Move> hintMoves = new ArrayList<>();
    private final List<AnalysisArrow> hintArrows = new ArrayList<>();

    public static final class AnalysisArrow {
        public final Move move;
        public final int pvOrder;
        public final boolean reply;
        /** 单 PV 第 3/4 步使用更浅的同色箭头。 */
        public final boolean continuation;
        /** Color.TRANSPARENT 表示沿用原分析箭头红/蓝配色。 */
        public final int colorOverride;
        /** 重新打分回看时显示在实际落子棋子左上角的 ★/优/中/差/错。 */
        public final String endpointLabel;
        public final int endpointLabelColor;
        /** false 时只保留端点等级徽标，不绘制该着法箭头。 */
        public final boolean drawArrow;

        public AnalysisArrow(Move move, int pvOrder, boolean reply) {
            this(move, pvOrder, reply, false);
        }
        public AnalysisArrow(Move move, int pvOrder, boolean reply, boolean continuation) {
            this(move, pvOrder, reply, continuation, Color.TRANSPARENT, null, Color.TRANSPARENT);
        }
        public AnalysisArrow(Move move, int pvOrder, boolean reply, boolean continuation,
                             int colorOverride, String endpointLabel, int endpointLabelColor) {
            this(move, pvOrder, reply, continuation, colorOverride, endpointLabel,
                    endpointLabelColor, true);
        }

        public AnalysisArrow(Move move, int pvOrder, boolean reply, boolean continuation,
                             int colorOverride, String endpointLabel, int endpointLabelColor,
                             boolean drawArrow) {
            this.move = move;
            this.pvOrder = pvOrder;
            this.reply = reply;
            this.continuation = continuation;
            this.colorOverride = colorOverride;
            this.endpointLabel = endpointLabel;
            this.endpointLabelColor = endpointLabelColor;
            this.drawArrow = drawArrow;
        }
    }

    /** 分析箭头按红/黑方着色：红方箭杆为红色、黑方箭杆为蓝色；第 3/4 步用同色更浅的续着色。 */
    private int redArrowColor = Color.rgb(214, 40, 40);
    private int blackArrowColor = Color.rgb(40, 100, 214);
    private int redContinuationArrowColor = Color.rgb(236, 148, 148);
    private int blackContinuationArrowColor = Color.rgb(148, 185, 236);
    private int arrowOpacityPercent = 97;
    private int arrowThicknessPercent = 100;
    private int pvLabelTextColor = Color.rgb(255, 183, 0);
    private int pvLabelFillColor = Color.rgb(76, 175, 80);

    /**
     * V19.1 性能修复：内置 default 皮肤在进程内只解码一次。
     * 棋盘页、推演页、皮肤预览都会复用这组只读 Bitmap，避免每次进入页面时
     * 在主线程重复解码约 7MB 的棋盘/棋子像素并立即触发回收。
     */
    private static final Object BUNDLED_SKIN_LOCK = new Object();
    private static Bitmap bundledBoardBitmap;
    private static final Map<Character, Bitmap> bundledPieceBitmapMap = new HashMap<Character, Bitmap>();
    /** 只缓存最近使用的一套外部皮肤，防止皮肤越切越多导致内存常驻增长。 */
    private static String cachedExternalSkinPath = "";
    private static long cachedExternalSkinSignature = Long.MIN_VALUE;
    private static Bitmap cachedExternalBoardBitmap;
    private static final Map<Character, Bitmap> cachedExternalPieceBitmapMap =
            new HashMap<Character, Bitmap>();

    private Bitmap boardBitmap;
    /** 当前 View 的 Bitmap 是否由该 View 独占；内置 default 使用进程级共享缓存，不可 recycle。 */
    private boolean ownsSkinBitmaps = true;
    private int boardImageWidth = DEFAULT_BOARD_IMAGE_W;
    private int boardImageHeight = DEFAULT_BOARD_IMAGE_H;
    private int pieceSizePercent = 96;
    // 依次为 TL(x,y)、TR(x,y)、BL(x,y)、BR(x,y)，均相对于棋盘图片宽高归一化。
    private final float[] gridCorners = new float[]{
            DEFAULT_TL_X, DEFAULT_TL_Y, DEFAULT_TR_X, DEFAULT_TR_Y,
            DEFAULT_BL_X, DEFAULT_BL_Y, DEFAULT_BR_X, DEFAULT_BR_Y
    };
    private char[][] board = XiangqiRules.newBoard();
    private boolean redToMove = true;
    private boolean reversed = false;
    private boolean showCoordinate = true;
    private boolean showArrow = true;
    private boolean pieceShadowEnabled = true;
    private boolean editMode = false;
    private boolean inputEnabled = true;
    // 棋盘显示缩放百分比。100 表示铺满屏宽；50 表示按屏宽 50% 显示，并保持水平居中。
    private int boardScalePercent = 100;
    // 编辑局面面板选择的“画笔”棋子；0 表示普通编辑移动，'-' 表示去除棋子。
    private char editPaintPiece = 0;

    private int selectedRow = -1;
    private int selectedCol = -1;
    private Move lastMove;
    private Listener listener;

    private float touchDownX;
    private float touchDownY;
    private boolean boardLongPressTriggered;
    private final Runnable boardLongPressRunnable = new Runnable() {
        @Override public void run() {
            boardLongPressTriggered = true;
            performLongClick();
        }
    };

    private float boardLeft;
    private float boardTop;
    private final RectF boardDst = new RectF();
    private boolean gridAdjustmentMode;
    /** 0..3 为四角拖动，-2 为整体平移，-1 为未选中。 */
    private int activeGridHandle = -1;
    /** V19.2：0..3 为方向键微调所控制的角点，-1 表示未选择。 */
    private int selectedGridHandle = -1;
    private float lastAdjustTouchX;
    private float lastAdjustTouchY;
    private boolean gridHandleDragged;
    private GridAdjustmentListener gridAdjustmentListener;
    private GridHandleSelectionListener gridHandleSelectionListener;

    /** 编辑模式双击删除：只有同一棋子在系统双击时间窗内连续点击才触发。 */
    private long lastEditTapAt;
    private int lastEditTapRow = -1;
    private int lastEditTapCol = -1;

    public ChessBoardView(Context context) {
        this(context, true);
    }

    /**
     * @param loadDefaultImmediately true 保持旧调用兼容；false 用于调用方马上应用指定皮肤的场景，
     *                               避免“构造时 default + 紧接着再加载当前皮肤”的双重解码。
     */
    public ChessBoardView(Context context, boolean loadDefaultImmediately) {
        super(context);
        setFocusable(true);
        setClickable(true);
        setLongClickable(true);
        if (loadDefaultImmediately) loadBitmaps();
    }

    private void loadBitmaps() {
        // V18.9：default 皮肤随 APK 内置。用户无需外部目录即可直接看到默认棋盘；
        // /storage/emulated/0/nodexq/pic/<皮肤名>/ 仅用于用户新增皮肤。
        loadBundledDefaultSkin(96, defaultGridCorners());
    }

    /** 在后台提前解码内置皮肤；重复调用不会重复分配 Bitmap。 */
    public static void preloadBundledDefaultSkin(Context context) {
        if (context == null) return;
        ensureBundledSkinDecoded(context.getApplicationContext());
    }

    private static boolean ensureBundledSkinDecoded(Context context) {
        synchronized (BUNDLED_SKIN_LOCK) {
            if (bundledBoardBitmap != null && !bundledBoardBitmap.isRecycled()
                    && bundledPieceBitmapMap.size() == 14) return true;

            Bitmap newBoard = BitmapFactory.decodeResource(context.getResources(), R.drawable.board);
            if (newBoard == null) return false;
            Map<Character, Bitmap> decoded = new HashMap<Character, Bitmap>();
            char[] pieces = new char[]{'r','n','b','a','k','c','p','R','N','B','A','K','C','P'};
            int[] ids = new int[]{R.drawable.br, R.drawable.bn, R.drawable.bb, R.drawable.ba,
                    R.drawable.bk, R.drawable.bc, R.drawable.bp, R.drawable.rr, R.drawable.rn,
                    R.drawable.rb, R.drawable.ra, R.drawable.rk, R.drawable.rc, R.drawable.rp};
            for (int i = 0; i < pieces.length; i++) {
                Bitmap bitmap = BitmapFactory.decodeResource(context.getResources(), ids[i]);
                if (bitmap == null) {
                    if (!newBoard.isRecycled()) newBoard.recycle();
                    recycleMap(decoded);
                    return false;
                }
                decoded.put(pieces[i], bitmap);
            }
            // 正常情况下旧缓存不会存在；若系统/测试环境曾回收其中一张，则只清引用，
            // 不主动 recycle 旧共享对象，避免仍在屏幕上的 View 被误伤。
            bundledBoardBitmap = newBoard;
            bundledPieceBitmapMap.clear();
            bundledPieceBitmapMap.putAll(decoded);
            return true;
        }
    }

    public static float[] defaultGridCorners() {
        return new float[]{DEFAULT_TL_X, DEFAULT_TL_Y, DEFAULT_TR_X, DEFAULT_TR_Y,
                DEFAULT_BL_X, DEFAULT_BL_Y, DEFAULT_BR_X, DEFAULT_BR_Y};
    }

    /** 加载随 APK 内置的 default 皮肤，资源名与外部皮肤一致：board、br…rp。 */
    public boolean loadBundledDefaultSkin(int requestedPieceSizePercent, float[] corners) {
        if (!ensureBundledSkinDecoded(getContext())) return false;
        synchronized (BUNDLED_SKIN_LOCK) {
            if (bundledBoardBitmap == null || bundledBoardBitmap.isRecycled()
                    || bundledPieceBitmapMap.size() < 14) return false;
            return applyDecodedSkin(bundledBoardBitmap,
                    new HashMap<Character, Bitmap>(bundledPieceBitmapMap),
                    requestedPieceSizePercent, corners, false);
        }
    }

    /** 在首页后台预热当前外部皮肤；只保留最近一套，文件变化后会自动重新解码。 */
    public static boolean preloadSkin(File skinDir) {
        return ensureExternalSkinDecoded(skinDir);
    }

    /**
     * V19.3：编辑面板复用棋盘已经解码好的共享棋子 Bitmap，避免进入/刷新编辑面板时
     * 再从磁盘单独 decode 14 张外部皮肤棋子图。返回对象只读共享，调用方不得 recycle。
     */
    public static Bitmap sharedSkinPieceBitmap(Context context, File skinDir, char piece) {
        if (context == null) return null;
        if (skinDir == null) {
            if (!ensureBundledSkinDecoded(context.getApplicationContext())) return null;
            synchronized (BUNDLED_SKIN_LOCK) {
                Bitmap bitmap = bundledPieceBitmapMap.get(piece);
                return bitmap != null && !bitmap.isRecycled() ? bitmap : null;
            }
        }
        String path;
        try { path = skinDir.getCanonicalPath(); }
        catch (Exception e) { path = skinDir.getAbsolutePath(); }
        // 当前棋盘已经加载该外部皮肤时直接取共享缓存，不重复计算 15 个文件的签名。
        synchronized (BUNDLED_SKIN_LOCK) {
            if (path.equals(cachedExternalSkinPath) && cachedExternalPieceBitmapMap.size() == 14) {
                Bitmap bitmap = cachedExternalPieceBitmapMap.get(piece);
                if (bitmap != null && !bitmap.isRecycled()) return bitmap;
            }
        }
        if (!ensureExternalSkinDecoded(skinDir)) return null;
        synchronized (BUNDLED_SKIN_LOCK) {
            Bitmap bitmap = cachedExternalPieceBitmapMap.get(piece);
            return bitmap != null && !bitmap.isRecycled() ? bitmap : null;
        }
    }

    private static boolean ensureExternalSkinDecoded(File skinDir) {
        try {
            if (skinDir == null || !skinDir.isDirectory()) return false;
            String path;
            try { path = skinDir.getCanonicalPath(); }
            catch (Exception e) { path = skinDir.getAbsolutePath(); }
            long signature = skinSignature(skinDir);
            if (signature == Long.MIN_VALUE) return false;
            synchronized (BUNDLED_SKIN_LOCK) {
                if (path.equals(cachedExternalSkinPath)
                        && signature == cachedExternalSkinSignature
                        && cachedExternalBoardBitmap != null && !cachedExternalBoardBitmap.isRecycled()
                        && cachedExternalPieceBitmapMap.size() == 14) return true;

                Bitmap newBoard = decodeSkinBitmap(skinDir, "board");
                if (newBoard == null) return false;
                Map<Character, Bitmap> decoded = new HashMap<Character, Bitmap>();
                char[] pieces = new char[]{'r','n','b','a','k','c','p','R','N','B','A','K','C','P'};
                String[] names = new String[]{"br","bn","bb","ba","bk","bc","bp",
                        "rr","rn","rb","ra","rk","rc","rp"};
                for (int i = 0; i < pieces.length; i++) {
                    Bitmap bitmap = decodeSkinBitmap(skinDir, names[i]);
                    if (bitmap == null) {
                        if (!newBoard.isRecycled()) newBoard.recycle();
                        recycleMap(decoded);
                        return false;
                    }
                    decoded.put(pieces[i], bitmap);
                }
                // 不 recycle 被替换的旧共享 Bitmap：旧棋盘 View 可能仍在一次页面切换的 detach 流程中。
                // 静态引用替换后，旧对象会在所有旧 View 释放引用后由运行时回收。
                cachedExternalSkinPath = path;
                cachedExternalSkinSignature = signature;
                cachedExternalBoardBitmap = newBoard;
                cachedExternalPieceBitmapMap.clear();
                cachedExternalPieceBitmapMap.putAll(decoded);
                return true;
            }
        } catch (SecurityException e) {
            return false;
        } catch (OutOfMemoryError e) {
            return false;
        }
    }

    private static long skinSignature(File dir) {
        String[] names = new String[]{"board","br","bn","bb","ba","bk","bc","bp",
                "rr","rn","rb","ra","rk","rc","rp"};
        long signature = 1125899906842597L;
        for (String name : names) {
            File file = findSkinBitmapFile(dir, name);
            if (file == null) return Long.MIN_VALUE;
            signature = signature * 31L + file.length();
            signature = signature * 31L + file.lastModified();
        }
        return signature;
    }

    private static File findSkinBitmapFile(File dir, String baseName) {
        String[] extensions = new String[]{".png", ".webp", ".jpg", ".jpeg"};
        for (String ext : extensions) {
            File file = new File(dir, baseName + ext);
            if (file.isFile()) return file;
        }
        return null;
    }

    /**
     * 从用户独立皮肤文件夹读取棋盘与十四张棋子图片。文件基础名固定为：
     * board、br/bn/bb/ba/bk/bc/bp、rr/rn/rb/ra/rk/rc/rp；支持 png/webp/jpg/jpeg。
     * 先完整解码到临时对象，15 张图片全部有效后才替换当前皮肤。
     */
    public boolean loadSkin(File skinDir, int requestedPieceSizePercent, float[] corners) {
        if (!ensureExternalSkinDecoded(skinDir)) return false;
        String path;
        try { path = skinDir.getCanonicalPath(); }
        catch (Exception e) { path = skinDir.getAbsolutePath(); }
        synchronized (BUNDLED_SKIN_LOCK) {
            if (!path.equals(cachedExternalSkinPath) || cachedExternalBoardBitmap == null
                    || cachedExternalBoardBitmap.isRecycled()
                    || cachedExternalPieceBitmapMap.size() < 14) return false;
            return applyDecodedSkin(cachedExternalBoardBitmap,
                    new HashMap<Character, Bitmap>(cachedExternalPieceBitmapMap),
                    requestedPieceSizePercent, corners, false);
        }
    }

    private boolean applyDecodedSkin(Bitmap newBoard, Map<Character, Bitmap> decoded,
                                     int requestedPieceSizePercent, float[] corners,
                                     boolean ownsDecodedBitmaps) {
        if (newBoard == null || decoded == null || decoded.size() < 14) return false;
        recycleBitmaps();
        boardBitmap = newBoard;
        pieceBitmapMap.putAll(decoded);
        ownsSkinBitmaps = ownsDecodedBitmaps;
        boardImageWidth = Math.max(1, boardBitmap.getWidth());
        boardImageHeight = Math.max(1, boardBitmap.getHeight());
        pieceSizePercent = Math.max(55, Math.min(125, requestedPieceSizePercent));
        setGridCorners(corners == null ? defaultGridCorners() : corners);
        requestLayout();
        invalidate();
        return true;
    }

    private static Bitmap decodeSkinBitmap(File dir, String baseName) {
        String[] extensions = new String[]{".png", ".webp", ".jpg", ".jpeg"};
        for (String ext : extensions) {
            File file = new File(dir, baseName + ext);
            if (!file.isFile()) continue;
            Bitmap bitmap = BitmapFactory.decodeFile(file.getAbsolutePath());
            if (bitmap != null) return bitmap;
        }
        return null;
    }

    public void setBoardSideFillColor(int color) {
        // V19.5：允许透明值；back 背景生效时棋盘图片两侧留白直接透出全局背景图。
        if (boardSideFillColor == color) return;
        boardSideFillColor = color;
        invalidate();
    }

    public void setPieceSizePercent(int percent) {
        int p = Math.max(55, Math.min(125, percent));
        if (pieceSizePercent == p) return;
        pieceSizePercent = p;
        invalidate();
    }

    public int getPieceSizePercent() {
        return pieceSizePercent;
    }

    public void setSkinGridCorners(float[] corners) {
        setGridCorners(corners == null ? defaultGridCorners() : corners);
        invalidate();
    }

    public float[] getSkinGridCorners() {
        return gridCorners.clone();
    }

    public void startGridAdjustment(GridAdjustmentListener listener) {
        gridAdjustmentMode = true;
        activeGridHandle = -1;
        selectedGridHandle = -1;
        gridHandleDragged = false;
        gridAdjustmentListener = listener;
        notifyGridHandleSelection();
        invalidate();
    }

    public void stopGridAdjustment() {
        gridAdjustmentMode = false;
        activeGridHandle = -1;
        selectedGridHandle = -1;
        gridHandleDragged = false;
        gridAdjustmentListener = null;
        notifyGridHandleSelection();
        invalidate();
    }

    public boolean isGridAdjustmentMode() {
        return gridAdjustmentMode;
    }

    public void setGridHandleSelectionListener(GridHandleSelectionListener listener) {
        gridHandleSelectionListener = listener;
        notifyGridHandleSelection();
    }

    public int getSelectedGridHandle() {
        return selectedGridHandle;
    }

    /**
     * V19.2：按屏幕 dp 微调当前选中的角点。返回 false 表示尚未选择角点。
     * 拖动与方向键共用同一边界约束，因此不会把四边形角点越过相邻边。
     */
    public boolean nudgeSelectedGridHandle(float dxDp, float dyDp) {
        if (!gridAdjustmentMode || selectedGridHandle < 0
                || boardDst.width() <= 0f || boardDst.height() <= 0f) return false;
        float density = getResources().getDisplayMetrics().density;
        int i = selectedGridHandle * 2;
        float nx = gridCorners[i] + dxDp * density / boardDst.width();
        float ny = gridCorners[i + 1] + dyDp * density / boardDst.height();
        setGridHandleNormalized(selectedGridHandle, nx, ny);
        invalidate();
        notifyGridChanged();
        return true;
    }

    private void notifyGridHandleSelection() {
        if (gridHandleSelectionListener != null) {
            gridHandleSelectionListener.onGridHandleSelected(selectedGridHandle);
        }
    }

    private void notifyGridChanged() {
        if (gridAdjustmentListener != null) gridAdjustmentListener.onGridChanged(getSkinGridCorners());
    }

    private int nearestGridHandle(float x, float y) {
        int best = -1;
        float bestDistance = Float.MAX_VALUE;
        int[][] rc = new int[][]{{0,0},{0,8},{9,0},{9,8}};
        for (int i = 0; i < 4; i++) {
            PointF p = gridPointForView(rc[i][0], rc[i][1]);
            float d = (float) Math.hypot(x - p.x, y - p.y);
            if (d < bestDistance) { bestDistance = d; best = i; }
        }
        float handleRadius = Math.max(28f, gridCellSize() * 0.62f);
        return bestDistance <= handleRadius ? best : -2;
    }

    private void updateGridAdjustment(float x, float y) {
        if (!gridAdjustmentMode || boardDst.width() <= 0f || boardDst.height() <= 0f) return;
        if (activeGridHandle >= 0) {
            float nx = (x - boardDst.left) / boardDst.width();
            float ny = (y - boardDst.top) / boardDst.height();
            setGridHandleNormalized(activeGridHandle, nx, ny);
        } else if (activeGridHandle == -2) {
            float dx = (x - lastAdjustTouchX) / boardDst.width();
            float dy = (y - lastAdjustTouchY) / boardDst.height();
            float minX = 1f, maxX = 0f, minY = 1f, maxY = 0f;
            for (int i = 0; i < 4; i++) {
                minX = Math.min(minX, gridCorners[i * 2]);
                maxX = Math.max(maxX, gridCorners[i * 2]);
                minY = Math.min(minY, gridCorners[i * 2 + 1]);
                maxY = Math.max(maxY, gridCorners[i * 2 + 1]);
            }
            dx = Math.max(-minX + 0.001f, Math.min(0.999f - maxX, dx));
            dy = Math.max(-minY + 0.001f, Math.min(0.999f - maxY, dy));
            for (int i = 0; i < 4; i++) {
                gridCorners[i * 2] += dx;
                gridCorners[i * 2 + 1] += dy;
            }
        }
        lastAdjustTouchX = x;
        lastAdjustTouchY = y;
        invalidate();
        notifyGridChanged();
    }

    private void setGridHandleNormalized(int handle, float nx, float ny) {
        if (handle < 0 || handle > 3) return;
        nx = Math.max(0.001f, Math.min(0.999f, nx));
        ny = Math.max(0.001f, Math.min(0.999f, ny));
        final float minGap = 0.10f;
        switch (handle) {
            case 0: // TL
                nx = Math.min(nx, gridCorners[2] - minGap);
                ny = Math.min(ny, gridCorners[5] - minGap);
                break;
            case 1: // TR
                nx = Math.max(nx, gridCorners[0] + minGap);
                ny = Math.min(ny, gridCorners[7] - minGap);
                break;
            case 2: // BL
                nx = Math.min(nx, gridCorners[6] - minGap);
                ny = Math.max(ny, gridCorners[1] + minGap);
                break;
            case 3: // BR
                nx = Math.max(nx, gridCorners[4] + minGap);
                ny = Math.max(ny, gridCorners[3] + minGap);
                break;
            default: return;
        }
        gridCorners[handle * 2] = nx;
        gridCorners[handle * 2 + 1] = ny;
    }

    private void drawCalibrationOverlay(Canvas canvas) {
        if (!gridAdjustmentMode) return;
        float stroke = Math.max(2f, gridCellSize() * 0.026f);
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeWidth(stroke);
        linePaint.setColor(Color.argb(205, 255, 183, 0));
        for (int r = 0; r < 10; r++) {
            PointF left = gridPointForView(r, 0);
            PointF right = gridPointForView(r, 8);
            canvas.drawLine(left.x, left.y, right.x, right.y, linePaint);
        }
        for (int c = 0; c < 9; c++) {
            PointF top = gridPointForView(0, c);
            PointF bottom = gridPointForView(9, c);
            canvas.drawLine(top.x, top.y, bottom.x, bottom.y, linePaint);
        }

        int[][] rc = new int[][]{{0,0},{0,8},{9,0},{9,8}};
        float radius = Math.max(12f, gridCellSize() * 0.18f);
        for (int i = 0; i < 4; i++) {
            PointF p = gridPointForView(rc[i][0], rc[i][1]);
            boolean selected = i == selectedGridHandle;
            boolean active = i == activeGridHandle;
            float shownRadius = radius * (selected ? 1.16f : 1f);
            linePaint.setStyle(Paint.Style.FILL);
            linePaint.setColor((active || selected)
                    ? Color.argb(240, 40, 145, 83) : Color.argb(230, 255, 183, 0));
            canvas.drawCircle(p.x, p.y, shownRadius, linePaint);
            linePaint.setStyle(Paint.Style.STROKE);
            linePaint.setStrokeWidth(Math.max(2f, stroke) * (selected ? 1.55f : 1f));
            linePaint.setColor(Color.argb(245, 255, 255, 255));
            canvas.drawCircle(p.x, p.y, shownRadius, linePaint);
        }
    }

    private void setGridCorners(float[] value) {
        if (value == null || value.length < 8) return;
        for (int i = 0; i < 8; i++) {
            float v = value[i];
            if (Float.isNaN(v) || Float.isInfinite(v)) v = 0.5f;
            gridCorners[i] = Math.max(0.001f, Math.min(0.999f, v));
        }
    }

    private void recycleBitmaps() {
        if (ownsSkinBitmaps) {
            if (boardBitmap != null && !boardBitmap.isRecycled()) boardBitmap.recycle();
            recycleMap(pieceBitmapMap);
        }
        boardBitmap = null;
        pieceBitmapMap.clear();
        ownsSkinBitmaps = true;
    }

    private static void recycleMap(Map<Character, Bitmap> map) {
        if (map == null) return;
        for (Bitmap bitmap : map.values()) {
            if (bitmap != null && !bitmap.isRecycled()) bitmap.recycle();
        }
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public void newGame() {
        board = XiangqiRules.newBoard();
        redToMove = true;
        selectedRow = -1;
        selectedCol = -1;
        lastMove = null;
        hintMoves.clear();
        hintArrows.clear();
        invalidate();
    }

    public void setBoardFromFen(String fen) {
        board = XiangqiRules.fromFen(fen);
        redToMove = XiangqiRules.redToMoveFromFen(fen);
        selectedRow = -1;
        selectedCol = -1;
        lastMove = null;
        hintMoves.clear();
        hintArrows.clear();
        invalidate();
    }

    public String getFen() {
        return XiangqiRules.toFen(board, redToMove);
    }

    public char[][] copyBoard() {
        return XiangqiRules.copyBoard(board);
    }

    public boolean isRedToMove() {
        return redToMove;
    }

    public void setRedToMove(boolean redToMove) {
        this.redToMove = redToMove;
        invalidate();
    }

    public void setReversed(boolean reversed) {
        this.reversed = reversed;
        selectedRow = -1;
        selectedCol = -1;
        invalidate();
    }

    public boolean isReversed() {
        return reversed;
    }

    public void setShowCoordinate(boolean showCoordinate) {
        this.showCoordinate = showCoordinate;
        invalidate();
    }

    public void setShowArrow(boolean showArrow) {
        this.showArrow = showArrow;
        invalidate();
    }

    public void setBoardScalePercent(int percent) {
        int p = Math.max(50, Math.min(100, percent));
        if (boardScalePercent == p) return;
        boardScalePercent = p;
        requestLayout();
        invalidate();
    }

    public int getBoardScalePercent() {
        return boardScalePercent;
    }

    public void setEditMode(boolean editMode) {
        this.editMode = editMode;
        selectedRow = -1;
        selectedCol = -1;
        if (!editMode) editPaintPiece = 0;
        invalidate();
    }

    public boolean isEditMode() {
        return editMode;
    }

    /** 编辑模式下是否正选中棋盘上的已有棋子。 */
    public boolean hasSelectedPieceInEditMode() {
        return editMode
                && XiangqiRules.inBoard(selectedRow, selectedCol)
                && XiangqiRules.isPiece(board[selectedRow][selectedCol]);
    }

    /**
     * 编辑模式下，棋盘已有选中棋子时，点击棋盘外的棋子按钮应把该棋子移回数量池，
     * 而不是直接切换为新的放置画笔。
     */
    public boolean removeSelectedPieceInEditMode() {
        if (!hasSelectedPieceInEditMode()) return false;
        char selected = board[selectedRow][selectedCol];
        board[selectedRow][selectedCol] = ' ';
        selectedRow = -1;
        selectedCol = -1;
        lastMove = null;
        if (listener != null) listener.onMessage(
                "编辑：已将" + XiangqiRules.pieceName(selected) + "移回棋子数量池。");
        invalidate();
        notifyEditBoardChanged();
        return true;
    }

    /** 编辑局面：设置当前要放置的棋子。piece=0 为普通移动，piece='-' 为去除。 */
    public void setEditPaintPiece(char piece) {
        this.editPaintPiece = piece;
        selectedRow = -1;
        selectedCol = -1;
        invalidate();
    }

    public char getEditPaintPiece() {
        return editPaintPiece;
    }

    public void setHintMoves(List<Move> moves) {
        hintMoves.clear();
        hintArrows.clear();
        if (moves != null) {
            hintMoves.addAll(moves);
        }
        invalidate();
    }

    public void setAnalysisArrows(List<AnalysisArrow> arrows) {
        hintMoves.clear();
        hintArrows.clear();
        if (arrows != null) hintArrows.addAll(arrows);
        invalidate();
    }

    public int getSideArrowColor(boolean redSide) {
        return redSide ? redArrowColor : blackArrowColor;
    }

    public void setArrowColors(int redColor, int blackColor, int labelTextColor, int labelFillColor) {
        this.redArrowColor = redColor;
        this.blackArrowColor = blackColor;
        this.redContinuationArrowColor = lightenArrowColor(redColor);
        this.blackContinuationArrowColor = lightenArrowColor(blackColor);
        this.pvLabelTextColor = labelTextColor;
        this.pvLabelFillColor = labelFillColor;
        invalidate();
    }

    private int lightenArrowColor(int color) {
        return Color.rgb((Color.red(color) + 255) / 2,
                (Color.green(color) + 255) / 2,
                (Color.blue(color) + 255) / 2);
    }

    public void setArrowOpacityPercent(int percent) {
        int p = Math.max(40, Math.min(100, percent));
        if (this.arrowOpacityPercent == p) return;
        this.arrowOpacityPercent = p;
        invalidate();
    }

    public void setArrowThicknessPercent(int percent) {
        int p = ThoughtArrowRenderer.clampThicknessPercent(percent);
        if (this.arrowThicknessPercent == p) return;
        this.arrowThicknessPercent = p;
        invalidate();
    }

    public void setPieceShadowEnabled(boolean enabled) {
        if (pieceShadowEnabled == enabled) return;
        pieceShadowEnabled = enabled;
        invalidate();
    }

    public boolean isPieceShadowEnabled() {
        return pieceShadowEnabled;
    }

    /** 普通对弈输入开关；编辑模式始终可操作。 */
    public void setInputEnabled(boolean enabled) {
        inputEnabled = enabled;
        if (!enabled && !editMode) {
            selectedRow = -1;
            selectedCol = -1;
            invalidate();
        }
    }

    public boolean isInputEnabled() {
        return inputEnabled;
    }

    private void notifyEditBoardChanged() {
        if (listener != null && editMode) listener.onEditBoardChanged();
    }

    private float currentArrowStrokeWidth() {
        return ThoughtArrowRenderer.strokeWidth(gridCellSize(), arrowThicknessPercent, 6f);
    }

    private int withArrowOpacity(int color) {
        int alpha = Math.round(255f * Math.max(40, Math.min(100, arrowOpacityPercent)) / 100f);
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color));
    }

    public List<Move> legalMovesForSideToMove() {
        return XiangqiRules.generateLegalMoves(board, redToMove);
    }

    public boolean playMove(Move move) {
        if (move == null) return false;
        if (!XiangqiRules.isLegalMove(board, move.fromRow, move.fromCol, move.toRow, move.toCol)) {
            if (listener != null) listener.onMessage("非法着法：" + move);
            return false;
        }
        return applyMove(move, true);
    }

    /** 棋谱导航/回放专用：只改棋盘，不回调 MainActivity，避免重复写入历史。 */
    public boolean playMoveSilently(Move move) {
        if (move == null) return false;
        if (!XiangqiRules.isLegalMove(board, move.fromRow, move.fromCol, move.toRow, move.toCol)) return false;
        return applyMove(move, false);
    }

    /** 编辑模式：直接清空指定格子，不做走法校验。 */
    public void clearCell(int row, int col) {
        if (!XiangqiRules.inBoard(row, col)) return;
        board[row][col] = ' ';
        selectedRow = -1;
        selectedCol = -1;
        lastMove = null;
        invalidate();
        notifyEditBoardChanged();
    }

    /** 编辑模式：直接设置指定格子的棋子，不做走法校验。piece=' ' 表示清空。 */
    public void setCellPiece(int row, int col, char piece) {
        if (!XiangqiRules.inBoard(row, col)) return;
        board[row][col] = XiangqiRules.isPiece(piece) ? piece : ' ';
        selectedRow = -1;
        selectedCol = -1;
        lastMove = null;
        invalidate();
        notifyEditBoardChanged();
    }

    /** 编辑模式：清空除将、帅以外的全部棋子。 */
    public void clearBoard() {
        for (int r = 0; r < 10; r++) {
            for (int c = 0; c < 9; c++) {
                if (board[r][c] != 'k' && board[r][c] != 'K') board[r][c] = ' ';
            }
        }
        selectedRow = -1;
        selectedCol = -1;
        lastMove = null;
        invalidate();
        notifyEditBoardChanged();
    }

    /** 编辑模式：恢复标准初始阵型，同时保留当前先手方设置。 */
    public void fillInitialBoard() {
        board = XiangqiRules.newBoard();
        selectedRow = -1;
        selectedCol = -1;
        lastMove = null;
        invalidate();
        notifyEditBoardChanged();
    }


    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        if (width <= 0) {
            width = getSuggestedMinimumWidth();
        }
        int p = Math.max(50, Math.min(100, boardScalePercent));
        int boardWidth = Math.round(width * (p / 100f));
        int desiredHeight = Math.round(boardWidth * boardImageHeight / (float) Math.max(1, boardImageWidth));
        // 控件高度跟随缩放后的棋盘高度变化：棋盘缩小后，下方导航/信息框自然获得更多空间。
        setMeasuredDimension(width, desiredHeight);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        // 缩放棋盘后，棋盘 PNG 以外的左右留白统一跟随全局底色。
        // 棋盘本体由当前皮肤的 board 图片保持原有木纹/底色。
        canvas.drawColor(boardSideFillColor);
        calculateBoardRect();

        if (boardBitmap != null) {
            canvas.drawBitmap(boardBitmap, null, boardDst, paint);
        } else {
            textPaint.setColor(Color.rgb(90, 98, 93));
            textPaint.setTextAlign(Paint.Align.CENTER);
            textPaint.setTextSize(Math.max(14f, getWidth() * 0.04f));
            canvas.drawText("未加载棋盘皮肤，请在菜单 → 皮肤设置中选择",
                    getWidth() / 2f, getHeight() / 2f, textPaint);
        }

        if (showCoordinate && boardBitmap != null) {
            drawCoordinates(canvas);
        }
        drawPieces(canvas);
        // 思考箭头必须浮在棋子之上。只画当前引擎 PV 箭头，不再把上一手旧箭头混入分析箭头。
        // 先画全部箭头，再统一画 PV 序号，保证序号图层始终在所有箭头之上。
        ArrayList<AnalysisArrow> topQualityBadges = new ArrayList<AnalysisArrow>();
        if (showArrow) {
            if (!hintArrows.isEmpty()) {
                ArrayList<AnalysisArrow> labels = new ArrayList<AnalysisArrow>();
                // PV 首手为当前行棋方，之后红黑交替；按着法实际行棋方着色。
                boolean firstIsRed = isRedToMove();
                for (int i = 0; i < hintArrows.size(); i++) {
                    AnalysisArrow a = hintArrows.get(i);
                    if (a == null || a.move == null) continue;
                    boolean redMove = (i % 2 == 0) ? firstIsRed : !firstIsRed;
                    int color = a.colorOverride != Color.TRANSPARENT
                            ? a.colorOverride
                            : (redMove
                                ? (a.continuation ? redContinuationArrowColor : redArrowColor)
                                : (a.continuation ? blackContinuationArrowColor : blackArrowColor));
                    if (a.drawArrow) drawMoveArrow(canvas, a.move, withArrowOpacity(color), currentArrowStrokeWidth());
                    if (a.pvOrder > 0 || (a.endpointLabel != null && a.endpointLabel.length() > 0)) labels.add(a);
                }
                for (int i = 0; i < labels.size(); i++) {
                    AnalysisArrow a = labels.get(i);
                    if (a.pvOrder > 0) drawPvOrderLabel(canvas, a.move, a.pvOrder);
                    if (a.endpointLabel != null && a.endpointLabel.length() > 0) {
                        topQualityBadges.add(a);
                    }
                }
            } else {
                // 合法走法提示：全部为当前行棋方的候选着法，统一用行棋方颜色。
                int color = isRedToMove() ? redArrowColor : blackArrowColor;
                for (int i = 0; i < hintMoves.size(); i++) {
                    drawMoveArrow(canvas, hintMoves.get(i), withArrowOpacity(color), currentArrowStrokeWidth());
                }
            }
        }
        drawSelection(canvas);
        drawCalibrationOverlay(canvas);
        // V19.2：优/中/差/错/★ 永远最后绘制，避免被上一手四角框或其他棋盘标记盖住。
        for (int i = 0; i < topQualityBadges.size(); i++) {
            AnalysisArrow a = topQualityBadges.get(i);
            drawMoveQualityBadge(canvas, a.move, a.endpointLabel, a.endpointLabelColor);
        }
    }

    private void calculateBoardRect() {
        float availableW = getWidth();
        float p = Math.max(50, Math.min(100, boardScalePercent)) / 100f;
        float w = availableW * p;
        float h = w * boardImageHeight / (float) Math.max(1, boardImageWidth);
        boardLeft = (availableW - w) / 2f;
        boardTop = 0f;
        boardDst.set(boardLeft, boardTop, boardLeft + w, boardTop + h);
    }

    private int viewRow(int row) {
        return reversed ? 9 - row : row;
    }

    private int viewCol(int col) {
        return reversed ? 8 - col : col;
    }

    private int boardRowFromView(int vRow) {
        return reversed ? 9 - vRow : vRow;
    }

    private int boardColFromView(int vCol) {
        return reversed ? 8 - vCol : vCol;
    }

    private PointF gridPointForView(int viewRow, int viewCol) {
        float u = Math.max(0f, Math.min(1f, viewCol / 8f));
        float v = Math.max(0f, Math.min(1f, viewRow / 9f));
        float tlx = gridCorners[0], tly = gridCorners[1];
        float trx = gridCorners[2], tryy = gridCorners[3];
        float blx = gridCorners[4], bly = gridCorners[5];
        float brx = gridCorners[6], bry = gridCorners[7];
        float nx = (1f - u) * (1f - v) * tlx + u * (1f - v) * trx + (1f - u) * v * blx + u * v * brx;
        float ny = (1f - u) * (1f - v) * tly + u * (1f - v) * tryy + (1f - u) * v * bly + u * v * bry;
        return new PointF(boardDst.left + nx * boardDst.width(), boardDst.top + ny * boardDst.height());
    }

    private float cellCenterX(int row, int col) {
        return gridPointForView(viewRow(row), viewCol(col)).x;
    }

    private float cellCenterY(int row, int col) {
        return gridPointForView(viewRow(row), viewCol(col)).y;
    }

    private int[] cellFromPoint(float x, float y) {
        int bestVr = -1, bestVc = -1;
        float bestDistance = Float.MAX_VALUE;
        for (int vr = 0; vr < 10; vr++) {
            for (int vc = 0; vc < 9; vc++) {
                PointF p = gridPointForView(vr, vc);
                float d = (float) Math.hypot(x - p.x, y - p.y);
                if (d < bestDistance) { bestDistance = d; bestVr = vr; bestVc = vc; }
            }
        }
        float maxDistance = Math.max(18f, gridCellSize() * 0.53f);
        if (bestVr < 0 || bestVc < 0 || bestDistance > maxDistance) return null;
        return new int[]{boardRowFromView(bestVr), boardColFromView(bestVc)};
    }

    private float gridCellSize() {
        float shortest = Float.MAX_VALUE;
        for (int viewRow = 0; viewRow < 10; viewRow++) {
            PointF a = gridPointForView(viewRow, 0);
            PointF b = gridPointForView(viewRow, 1);
            shortest = Math.min(shortest, (float) Math.hypot(b.x - a.x, b.y - a.y));
        }
        return Math.max(1f, shortest);
    }

    private void drawCoordinates(Canvas canvas) {
        textPaint.setColor(Color.rgb(20, 20, 20));
        textPaint.setTextAlign(Paint.Align.CENTER);
        float cell = gridCellSize();
        textPaint.setTextSize(Math.max(12f, cell * (34f / 115f)));
        Paint.FontMetrics fm = textPaint.getFontMetrics();
        float baselineFix = -(fm.ascent + fm.descent) / 2f;
        final String[] redFilesNormal = {"九", "八", "七", "六", "五", "四", "三", "二", "一"};
        final String[] redFilesReversed = {"一", "二", "三", "四", "五", "六", "七", "八", "九"};
        float offset = Math.max(10f, cell * 0.63f);
        for (int c = 0; c < 9; c++) {
            PointF top = gridPointForView(0, c);
            PointF bottom = gridPointForView(9, c);
            float topY = Math.max(boardDst.top + textPaint.getTextSize() * 0.55f, top.y - offset) + baselineFix;
            float bottomY = Math.min(boardDst.bottom - textPaint.getTextSize() * 0.55f, bottom.y + offset) + baselineFix;
            if (reversed) {
                canvas.drawText(redFilesReversed[c], top.x, topY, textPaint);
                canvas.drawText(String.valueOf(9 - c), bottom.x, bottomY, textPaint);
            } else {
                canvas.drawText(String.valueOf(c + 1), top.x, topY, textPaint);
                canvas.drawText(redFilesNormal[c], bottom.x, bottomY, textPaint);
            }
        }
    }

    private void drawPieces(Canvas canvas) {
        float piece = gridCellSize() * pieceSizePercent / 100f;
        for (int r = 0; r < 10; r++) {
            for (int c = 0; c < 9; c++) {
                char p = board[r][c];
                if (!XiangqiRules.isPiece(p)) continue;
                Bitmap bm = pieceBitmapMap.get(p);
                if (bm == null) continue;
                float cx = cellCenterX(r, c);
                float cy = cellCenterY(r, c);
                RectF dst = new RectF(cx - piece / 2f, cy - piece / 2f, cx + piece / 2f, cy + piece / 2f);
                if (pieceShadowEnabled) pieceShadowRenderer.draw(canvas, dst);
                canvas.drawBitmap(bm, null, dst, paint);
            }
        }
    }

    private void drawSelection(Canvas canvas) {
        if (selectedRow >= 0 && selectedCol >= 0) {
            drawCornerMark(canvas, selectedRow, selectedCol, Color.rgb(37, 230, 156), gridCellSize() * (8f / 115f));
        }
        // 编辑模式只显示真正的当前选中框，不绘制上一手落点，避免取消选择后仍残留绿色框。
        if (!editMode && lastMove != null) {
            drawCornerMark(canvas, lastMove.fromRow, lastMove.fromCol, Color.argb(190, 255, 255, 255), gridCellSize() * (5f / 115f));
            drawCornerMark(canvas, lastMove.toRow, lastMove.toCol, Color.rgb(37, 230, 156), gridCellSize() * (6f / 115f));
        }
    }

    private void drawCornerMark(Canvas canvas, int row, int col, int color, float stroke) {
        float cx = cellCenterX(row, col);
        float cy = cellCenterY(row, col);
        float r = gridCellSize() * (54f / 115f);
        float len = gridCellSize() * (22f / 115f);
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeWidth(Math.max(3f, stroke));
        linePaint.setColor(color);
        linePaint.setStrokeCap(Paint.Cap.SQUARE);
        // 左上
        canvas.drawLine(cx - r, cy - r, cx - r + len, cy - r, linePaint);
        canvas.drawLine(cx - r, cy - r, cx - r, cy - r + len, linePaint);
        // 右上
        canvas.drawLine(cx + r, cy - r, cx + r - len, cy - r, linePaint);
        canvas.drawLine(cx + r, cy - r, cx + r, cy - r + len, linePaint);
        // 左下
        canvas.drawLine(cx - r, cy + r, cx - r + len, cy + r, linePaint);
        canvas.drawLine(cx - r, cy + r, cx - r, cy + r - len, linePaint);
        // 右下
        canvas.drawLine(cx + r, cy + r, cx + r - len, cy + r, linePaint);
        canvas.drawLine(cx + r, cy + r, cx + r, cy + r - len, linePaint);
    }

    private void drawMoveArrow(Canvas canvas, Move move, int color, float strokeWidth) {
        if (move == null) return;
        float sx = cellCenterX(move.fromRow, move.fromCol);
        float sy = cellCenterY(move.fromRow, move.fromCol);
        float ex = cellCenterX(move.toRow, move.toCol);
        float ey = cellCenterY(move.toRow, move.toCol);
        // 箭头仍严格从起点交叉点指向终点交叉点；头部由统一绘制器生成实心导航图标轮廓。
        ThoughtArrowRenderer.draw(canvas, linePaint, sx, sy, ex, ey,
                gridCellSize(), strokeWidth, color);
    }

    private void drawPvOrderLabel(Canvas canvas, Move move, int order) {
        if (move == null || order <= 0) return;
        float sx = cellCenterX(move.fromRow, move.fromCol);
        float sy = cellCenterY(move.fromRow, move.fromCol);
        float ex = cellCenterX(move.toRow, move.toCol);
        float ey = cellCenterY(move.toRow, move.toCol);
        float dx = ex - sx;
        float dy = ey - sy;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < 1f) return;
        float ux = dx / len;
        float uy = dy / len;
        // 序号放在箭头头端向内一点的位置，避免遮住箭头尖。
        float cx = ex - ux * gridCellSize() * (34f / 115f);
        float cy = ey - uy * gridCellSize() * (34f / 115f);
        float radius = Math.max(12f, gridCellSize() * (17f / 115f));

        linePaint.setStyle(Paint.Style.FILL);
        linePaint.setColor(pvLabelFillColor);
        canvas.drawCircle(cx, cy, radius, linePaint);
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeWidth(Math.max(2f, gridCellSize() * (2.4f / 115f)));
        linePaint.setColor(Color.argb(230, 255, 255, 255));
        canvas.drawCircle(cx, cy, radius, linePaint);

        textPaint.setColor(pvLabelTextColor);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        textPaint.setTextSize(Math.max(14f, gridCellSize() * (23f / 115f)));
        Paint.FontMetrics fm = textPaint.getFontMetrics();
        float base = cy - (fm.ascent + fm.descent) / 2f;
        canvas.drawText(String.valueOf(order), cx, base, textPaint);
        textPaint.setTypeface(android.graphics.Typeface.DEFAULT);
    }

    /**
     * V19.1：优劣标记固定在“实际落子后的棋子左上角”，不再压在箭头末端/棋子中央。
     * 标记中心略向棋子外侧偏移，并限制在棋盘图片范围内；换皮肤或改变棋子大小后仍跟随棋子。
     */
    private void drawMoveQualityBadge(Canvas canvas, Move move, String label, int color) {
        if (move == null || label == null || label.length() == 0) return;
        float ex = cellCenterX(move.toRow, move.toCol);
        float ey = cellCenterY(move.toRow, move.toCol);
        float cell = gridCellSize();
        float piece = cell * pieceSizePercent / 100f;
        int badgeColor = color == Color.TRANSPARENT ? Color.rgb(46, 145, 83) : color;

        // 以棋子外接圆的左上角为锚点，圆标大部分位于棋子轮廓之外，尽量不遮挡棋面文字。
        float radius = Math.max(11f, cell * 0.17f);
        float cx = ex - piece * 0.43f;
        float cy = ey - piece * 0.43f;
        if (!boardDst.isEmpty()) {
            cx = Math.max(boardDst.left + radius, Math.min(boardDst.right - radius, cx));
            cy = Math.max(boardDst.top + radius, Math.min(boardDst.bottom - radius, cy));
        }

        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        if ("★".equals(label)) {
            textPaint.setTextSize(Math.max(15f, cell * 0.31f));
            textPaint.setColor(badgeColor);
            textPaint.setShadowLayer(Math.max(1.5f, cell * 0.025f), 0f, 0f, Color.WHITE);
            Paint.FontMetrics fm = textPaint.getFontMetrics();
            canvas.drawText(label, cx, cy - (fm.ascent + fm.descent) / 2f, textPaint);
            textPaint.clearShadowLayer();
        } else {
            linePaint.setStyle(Paint.Style.FILL);
            linePaint.setColor(Color.argb(238, Color.red(badgeColor), Color.green(badgeColor), Color.blue(badgeColor)));
            canvas.drawCircle(cx, cy, radius, linePaint);
            linePaint.setStyle(Paint.Style.STROKE);
            linePaint.setStrokeWidth(Math.max(1.5f, cell * 0.018f));
            linePaint.setColor(Color.argb(225, 255, 255, 255));
            canvas.drawCircle(cx, cy, radius, linePaint);
            textPaint.setTextSize(Math.max(12f, cell * 0.21f));
            textPaint.setColor(UiTheme.textOnHighlight(getContext(), badgeColor));
            Paint.FontMetrics fm = textPaint.getFontMetrics();
            canvas.drawText(label, cx, cy - (fm.ascent + fm.descent) / 2f, textPaint);
        }
        linePaint.setStyle(Paint.Style.STROKE);
        textPaint.setTypeface(android.graphics.Typeface.DEFAULT);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
                touchDownX = event.getX();
                touchDownY = event.getY();
                boardLongPressTriggered = false;
                removeCallbacks(boardLongPressRunnable);
                if (gridAdjustmentMode) {
                    activeGridHandle = nearestGridHandle(event.getX(), event.getY());
                    gridHandleDragged = false;
                    lastAdjustTouchX = event.getX();
                    lastAdjustTouchY = event.getY();
                    invalidate();
                } else {
                    postDelayed(boardLongPressRunnable, ViewConfiguration.getLongPressTimeout());
                }
                return true;
            case MotionEvent.ACTION_MOVE:
                int slop = ViewConfiguration.get(getContext()).getScaledTouchSlop();
                float dx = event.getX() - touchDownX;
                float dy = event.getY() - touchDownY;
                if (gridAdjustmentMode) {
                    removeCallbacks(boardLongPressRunnable);
                    if (dx * dx + dy * dy > slop * slop) gridHandleDragged = true;
                    updateGridAdjustment(event.getX(), event.getY());
                    return true;
                }
                if (dx * dx + dy * dy > slop * slop) {
                    removeCallbacks(boardLongPressRunnable);
                }
                return true;
            case MotionEvent.ACTION_CANCEL:
                if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(false);
                removeCallbacks(boardLongPressRunnable);
                boardLongPressTriggered = false;
                activeGridHandle = -1;
                gridHandleDragged = false;
                invalidate();
                return true;
            case MotionEvent.ACTION_UP:
                if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(false);
                removeCallbacks(boardLongPressRunnable);
                if (gridAdjustmentMode) {
                    boardLongPressTriggered = false;
                    int releasedHandle = activeGridHandle;
                    if (gridHandleDragged) {
                        updateGridAdjustment(event.getX(), event.getY());
                        if (releasedHandle >= 0 && selectedGridHandle != releasedHandle) {
                            selectedGridHandle = releasedHandle;
                            notifyGridHandleSelection();
                        }
                    } else if (releasedHandle >= 0) {
                        selectedGridHandle = selectedGridHandle == releasedHandle ? -1 : releasedHandle;
                        notifyGridHandleSelection();
                    }
                    activeGridHandle = -1;
                    gridHandleDragged = false;
                    invalidate();
                    return true;
                }
                if (boardLongPressTriggered) {
                    boardLongPressTriggered = false;
                    return true;
                }
                if (!editMode && !inputEnabled) return true;
                performClick();
                int[] cell = cellFromPoint(event.getX(), event.getY());
                if (cell == null) {
                    selectedRow = -1;
                    selectedCol = -1;
                    lastEditTapAt = 0L;
                    lastEditTapRow = -1;
                    lastEditTapCol = -1;
                    invalidate();
                    return true;
                }
                if (editMode && editPaintPiece == 0 && XiangqiRules.isPiece(board[cell[0]][cell[1]])) {
                    long now = android.os.SystemClock.uptimeMillis();
                    boolean doubleTap = cell[0] == lastEditTapRow && cell[1] == lastEditTapCol
                            && now - lastEditTapAt <= ViewConfiguration.getDoubleTapTimeout();
                    if (doubleTap) {
                        deleteEditPieceAt(cell[0], cell[1]);
                        lastEditTapAt = 0L;
                        lastEditTapRow = -1;
                        lastEditTapCol = -1;
                        return true;
                    }
                    lastEditTapAt = now;
                    lastEditTapRow = cell[0];
                    lastEditTapCol = cell[1];
                } else {
                    lastEditTapAt = 0L;
                    lastEditTapRow = -1;
                    lastEditTapCol = -1;
                }
                handleCellTap(cell[0], cell[1]);
                return true;
            default:
                return true;
        }
    }

    @Override
    public boolean performClick() {
        super.performClick();
        return true;
    }

    private void deleteEditPieceAt(int row, int col) {
        if (!editMode || row < 0 || row >= 10 || col < 0 || col >= 9
                || !XiangqiRules.isPiece(board[row][col])) return;
        char removed = board[row][col];
        board[row][col] = ' ';
        selectedRow = -1;
        selectedCol = -1;
        lastMove = null;
        invalidate();
        if (listener != null) listener.onMessage("编辑：双击已删除" + XiangqiRules.pieceName(removed) + "。");
        notifyEditBoardChanged();
    }

    private void handleCellTap(int row, int col) {
        char tapped = board[row][col];
        if (editMode && editPaintPiece != 0) {
            char newPiece = editPaintPiece == '-' ? ' ' : editPaintPiece;
            if (XiangqiRules.isPiece(newPiece) && board[row][col] != newPiece) {
                int max = XiangqiRules.maxPieceCount(newPiece);
                if (max > 0 && XiangqiRules.countPiece(board, newPiece) >= max) {
                    if (listener != null) listener.onMessage(
                            XiangqiRules.pieceName(newPiece) + "数量已达到上限。");
                    return;
                }
            }
            char oldPiece = board[row][col];
            board[row][col] = XiangqiRules.isPiece(newPiece) ? newPiece : ' ';
            selectedRow = -1;
            selectedCol = -1;
            lastMove = null;
            if (listener != null) {
                listener.onMessage(editPaintPiece == '-' ? "编辑：已去除棋子。" : "编辑：已放置" + XiangqiRules.pieceName(editPaintPiece) + "。");
            }
            invalidate();
            if (oldPiece != board[row][col]) notifyEditBoardChanged();
            return;
        }
        if (selectedRow < 0 || selectedCol < 0) {
            if (XiangqiRules.isPiece(tapped)) {
                if (editMode || XiangqiRules.isRed(tapped) == redToMove) {
                    selectedRow = row;
                    selectedCol = col;
                    if (editMode) lastMove = null;
                    if (listener != null) listener.onMessage(editMode ? "编辑：已选中棋子，点目标格移动；点同一格取消。" : "已选中：" + XiangqiRules.pieceName(tapped));
                }
            }
            invalidate();
            return;
        }

        if (selectedRow == row && selectedCol == col) {
            selectedRow = -1;
            selectedCol = -1;
            if (editMode) lastMove = null;
            invalidate();
            return;
        }

        char selected = board[selectedRow][selectedCol];
        if (!XiangqiRules.isPiece(selected)) {
            selectedRow = -1;
            selectedCol = -1;
            invalidate();
            return;
        }

        if (editMode) {
            // 编辑模式严格不调用走法规则，也不写入棋谱历史：用于摆局、修正局面。
            char captured = board[row][col];
            board[row][col] = selected;
            board[selectedRow][selectedCol] = ' ';
            lastMove = new Move(selectedRow, selectedCol, row, col);
            selectedRow = -1;
            selectedCol = -1;
            if (listener != null) listener.onMessage("编辑：已移动" + XiangqiRules.pieceName(selected) + (captured == ' ' ? "。" : "，覆盖" + XiangqiRules.pieceName(captured) + "。"));
            invalidate();
            notifyEditBoardChanged();
            return;
        }

        if (XiangqiRules.isPiece(tapped) && XiangqiRules.sameSide(tapped, selected)) {
            selectedRow = row;
            selectedCol = col;
            invalidate();
            return;
        }

        Move move = new Move(selectedRow, selectedCol, row, col);
        if (!XiangqiRules.isLegalMove(board, selectedRow, selectedCol, row, col)) {
            if (listener != null) listener.onMessage("非法着法，已按 PC 端规则拒绝。");
            invalidate();
            return;
        }
        applyMove(move, true);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        pieceShadowRenderer.clear();
    }

    private boolean applyMove(Move move, boolean notifyListener) {
        char moved = board[move.fromRow][move.fromCol];
        char captured = board[move.toRow][move.toCol];
        if (!XiangqiRules.isPiece(moved)) return false;
        board[move.toRow][move.toCol] = moved;
        board[move.fromRow][move.fromCol] = ' ';
        redToMove = !redToMove;
        lastMove = move;
        selectedRow = -1;
        selectedCol = -1;
        hintMoves.clear();
        hintArrows.clear();
        if (notifyListener && listener != null) {
            listener.onMoveMade(move, moved, captured, getFen(), redToMove);
        }
        invalidate();
        return true;
    }
}
