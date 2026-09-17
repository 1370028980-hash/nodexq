package com.tyl.xiangqi.ndxq.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 从 v68 局势图模块抽离出的轻量版本。
 * 保留 v68 的 1700 / 3700 / 6000 分段坐标、红蓝分段曲线、
 * 第 10 回合中局线、残局线、当前位置标记和左右拖动定位棋谱功能。
 */
public final class V68SituationChartView extends View {
    public interface Listener {
        void onPreviewPly(int ply);
        void onNavigateToPly(int ply);
    }

    private static final int SCORE_SPLIT_1 = 1700;
    private static final int SCORE_SPLIT_2 = 3700;
    private static final int MATE_LIMIT = 6000;

    private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint redCurvePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint blueCurvePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint neutralCurvePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stagePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pointPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint locatorPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint errorPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final int touchSlop;

    private List<Integer> scores = Collections.emptyList();
    private List<Integer> matePlies = Collections.emptyList();
    private int initialScoreRed;
    private boolean initialRedToMove = true;
    private int totalMoves;
    private int currentPly;
    private int endgameRound = -1;
    private List<Integer> errorPlies = Collections.emptyList();
    private Listener listener;

    private boolean trackingTouch;
    private boolean dragMoved;
    private float touchDownX;
    private float touchDownY;
    private int previewPly = -1;
    /** Last ply already sent through the lightweight drag-preview callback. */
    private int previewedPly = -1;

    public V68SituationChartView(Context context) {
        super(context);
        setBackgroundColor(Color.WHITE);
        gridPaint.setStyle(Paint.Style.STROKE);
        gridPaint.setStrokeWidth(Math.max(1f, dp(1)));
        configureCurvePaint(redCurvePaint, Color.rgb(238, 42, 42));
        configureCurvePaint(blueCurvePaint, Color.rgb(25, 102, 235));
        configureCurvePaint(neutralCurvePaint, Color.rgb(105, 115, 125));
        textPaint.setColor(Color.rgb(50, 55, 60));
        textPaint.setTextSize(dp(9));
        pointPaint.setStyle(Paint.Style.FILL);
        stagePaint.setStyle(Paint.Style.STROKE);
        stagePaint.setStrokeWidth(Math.max(1f, dp(1)));
        stagePaint.setPathEffect(new DashPathEffect(new float[]{dp(5), dp(4)}, 0));
        locatorPaint.setStyle(Paint.Style.STROKE);
        locatorPaint.setStrokeWidth(Math.max(1f, dp(1)));
        locatorPaint.setColor(Color.argb(190, 55, 65, 75));
        locatorPaint.setPathEffect(new DashPathEffect(new float[]{dp(4), dp(3)}, 0));
        errorPaint.setStyle(Paint.Style.STROKE);
        errorPaint.setStrokeWidth(Math.max(1f, dp(1)));
        errorPaint.setStrokeCap(Paint.Cap.ROUND);
        errorPaint.setStrokeJoin(Paint.Join.ROUND);
        errorPaint.setColor(Color.rgb(186, 45, 40));
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        setClickable(true);
        setFocusable(true);
        setContentDescription("局势图，可左右滑动定位棋谱局面");
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    /** V19.4：自定义局势图红优/黑优曲线颜色。 */
    public void setAdvantageColors(int redColor, int blackColor) {
        redCurvePaint.setColor(redColor);
        blueCurvePaint.setColor(blackColor);
        invalidate();
    }

    public void setData(List<Integer> scores, List<Integer> matePlies,
                        int initialScoreRed, boolean initialRedToMove,
                        int totalMoves, int currentPly, int endgameRound,
                        List<Integer> errorPlies) {
        this.scores = scores == null
                ? Collections.<Integer>emptyList() : new ArrayList<Integer>(scores);
        this.matePlies = matePlies == null
                ? Collections.<Integer>emptyList() : new ArrayList<Integer>(matePlies);
        this.initialScoreRed = initialScoreRed;
        this.initialRedToMove = initialRedToMove;
        this.totalMoves = Math.max(0, totalMoves);
        this.currentPly = Math.max(0, Math.min(this.totalMoves, currentPly));
        this.endgameRound = endgameRound;
        this.errorPlies = errorPlies == null
                ? Collections.<Integer>emptyList() : new ArrayList<Integer>(errorPlies);
        invalidate();
    }

    private void configureCurvePaint(Paint paint, int color) {
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(2f, dp(2)));
        paint.setStrokeJoin(Paint.Join.ROUND);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setColor(color);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int width = getWidth();
        int height = getHeight();
        if (width <= dp(60) || height <= dp(60)) return;

        float left = dp(1);
        float right = width - dp(1);
        float top = dp(2);
        float bottom = height - dp(2);
        float centerY = (top + bottom) / 2f;
        float halfHeight = Math.max(1f, (bottom - top) / 2f);
        float plotWidth = Math.max(1f, right - left);

        gridPaint.setColor(Color.rgb(0, 220, 235));
        gridPaint.setPathEffect(null);
        drawScoreGuide(canvas, left, right, centerY, halfHeight, SCORE_SPLIT_1, "1700");
        drawScoreGuide(canvas, left, right, centerY, halfHeight, SCORE_SPLIT_2, "3700");
        gridPaint.setColor(Color.rgb(150, 160, 168));
        canvas.drawLine(left, centerY, right, centerY, gridPaint);
        canvas.drawLine(left, top, right, top, gridPaint);
        canvas.drawLine(left, bottom, right, bottom, gridPaint);
        canvas.drawLine(left, top, left, bottom, gridPaint);
        canvas.drawLine(right, top, right, bottom, gridPaint);

        textPaint.setTextSize(Math.max(dp(7), Math.min(dp(9), width / 44f)));
        textPaint.setColor(Color.rgb(55, 65, 70));
        drawInsideLeftText(canvas, String.valueOf(MATE_LIMIT), left + dp(3), top + textPaint.getTextSize());
        drawInsideLeftText(canvas, "0", left + dp(3), centerY - dp(2));
        drawInsideLeftText(canvas, "-" + MATE_LIMIT, left + dp(3), bottom - dp(3));

        int totalRounds = Math.max(1, (totalMoves + 1) / 2);
        if (totalRounds >= 10) {
            drawStageLine(canvas, left, top, bottom, plotWidth,
                    10, totalRounds, "中局（10）", Color.rgb(0, 145, 110), 0);
        }
        if (endgameRound >= 0 && endgameRound <= totalRounds) {
            drawStageLine(canvas, left, top, bottom, plotWidth,
                    endgameRound, totalRounds, "残局（" + endgameRound + "）",
                    Color.rgb(190, 70, 150), 1);
        }

        Point previous = null;
        float previousX = 0f;
        float previousY = 0f;
        for (int i = 0; i < scores.size() && i < totalMoves; i++) {
            Point point = pointAt(i);
            float x = left + (point.roundPosition() / Math.max(1f, totalRounds)) * plotWidth;
            float y = centerY - scoreRatio(point.redScore) * halfHeight;
            if (previous != null) {
                drawColoredSegment(canvas, previousX, previousY, previous.redScore,
                        x, y, point.redScore, centerY);
            }
            previous = point;
            previousX = x;
            previousY = y;
        }

        int markerPly = trackingTouch && previewPly >= 0 ? previewPly : currentPly;
        int markerIndex = markerPly - 1;
        Point markerPoint = markerIndex >= 0 && markerIndex < scores.size() ? pointAt(markerIndex) : null;
        float markerRound = markerPly / 2f;
        float markerX = left + (markerRound / Math.max(1f, totalRounds)) * plotWidth;
        markerX = Math.max(left, Math.min(right, markerX));
        float markerY = markerPoint == null ? centerY
                : centerY - scoreRatio(markerPoint.redScore) * halfHeight;
        int markerScore = markerPoint == null ? 0 : markerPoint.redScore;

        // 当前局面说明仍按原逻辑寻找不压住曲线的位置；错误招法直接在数据点画圆。
        String label = markerPoint == null ? navigationLabel(markerPly) : pointLabel(markerPoint);
        textPaint.setTextSize(Math.max(dp(9), Math.min(dp(12), width / 30f)));
        LabelPlacement markerLabel = chooseLabelPlacement(label, markerX, markerY,
                left, right, top, bottom, centerY, halfHeight, plotWidth, totalRounds,
                null, markerY < centerY);
        drawErrorMarkers(canvas, left, right, top, bottom, centerY, halfHeight,
                plotWidth, totalRounds);

        if (trackingTouch) canvas.drawLine(markerX, top, markerX, bottom, locatorPaint);
        pointPaint.setColor(markerScore > 0 ? Color.rgb(238, 42, 42)
                : markerScore < 0 ? Color.rgb(25, 102, 235)
                : Color.rgb(105, 115, 125));
        canvas.drawCircle(markerX, markerY, trackingTouch ? dp(5) : dp(4), pointPaint);

        textPaint.setTextSize(Math.max(dp(9), Math.min(dp(12), width / 30f)));
        textPaint.setTextAlign(Paint.Align.LEFT);
        textPaint.setColor(markerScore > 0 ? Color.rgb(220, 30, 30)
                : markerScore < 0 ? Color.rgb(15, 82, 215)
                : Color.rgb(80, 90, 100));
        canvas.drawText(label, markerLabel.x, markerLabel.baseline, textPaint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event == null || totalMoves <= 0) return super.onTouchEvent(event);
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                trackingTouch = true;
                dragMoved = false;
                touchDownX = event.getX();
                touchDownY = event.getY();
                previewPly = plyForTouchX(event.getX());
                previewedPly = -1;
                if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
                invalidate();
                return true;
            case MotionEvent.ACTION_MOVE:
                if (!trackingTouch) return false;
                if (!dragMoved && (Math.abs(event.getX() - touchDownX) >= touchSlop
                        || Math.abs(event.getY() - touchDownY) >= touchSlop)) dragMoved = true;
                int next = plyForTouchX(event.getX());
                if (next != previewPly) {
                    previewPly = next;
                    dispatchPreview(next);
                    invalidate();
                }
                return true;
            case MotionEvent.ACTION_UP:
                if (!trackingTouch) return false;
                int target = plyForTouchX(event.getX());
                boolean wasDrag = dragMoved;
                boolean wasPreviewed = previewedPly >= 0;
                previewPly = target;
                if (wasDrag || wasPreviewed) {
                    // The board has already followed the finger. Do not rebuild the whole
                    // content tree while this UP event is still being dispatched.
                    dispatchPreview(target);
                    currentPly = target;
                }
                finishTracking();
                if (!wasDrag) performClick();
                if (!wasDrag && !wasPreviewed && listener != null) {
                    listener.onNavigateToPly(target);
                }
                invalidate();
                return true;
            case MotionEvent.ACTION_CANCEL:
                int cancelledTarget = previewPly >= 0 ? previewPly : currentPly;
                if (dragMoved || previewedPly >= 0) {
                    dispatchPreview(cancelledTarget);
                    currentPly = cancelledTarget;
                }
                finishTracking();
                invalidate();
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

    private void finishTracking() {
        trackingTouch = false;
        dragMoved = false;
        previewPly = -1;
        previewedPly = -1;
        if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(false);
    }

    private void dispatchPreview(int target) {
        if (target == previewedPly) return;
        previewedPly = target;
        if (listener != null) listener.onPreviewPly(target);
    }

    private int plyForTouchX(float x) {
        float left = dp(1);
        float right = Math.max(left + 1f, getWidth() - dp(1));
        float ratio = (x - left) / (right - left);
        ratio = Math.max(0f, Math.min(1f, ratio));
        int rounds = Math.max(1, (totalMoves + 1) / 2);
        int index = Math.round(ratio * rounds * 2f);
        return Math.max(0, Math.min(totalMoves, index));
    }

    private Point pointAt(int index) {
        int score = index < scores.size() && scores.get(index) != null ? scores.get(index) : 0;
        int mate = index < matePlies.size() && matePlies.get(index) != null ? matePlies.get(index) : 0;
        return new Point(index, score, mate);
    }

    private void drawErrorMarkers(Canvas canvas, float left, float right, float top, float bottom,
                                  float centerY, float halfHeight, float plotWidth,
                                  int totalRounds) {
        if (errorPlies == null || errorPlies.isEmpty()) return;
        errorPaint.setStyle(Paint.Style.STROKE);
        errorPaint.setStrokeWidth(Math.max(dp(2), 2f));
        final float radius = dp(5.5f);
        for (Integer plyValue : errorPlies) {
            if (plyValue == null) continue;
            int index = plyValue - 1;
            if (index < 0 || index >= scores.size() || index >= totalMoves) continue;
            Point point = pointAt(index);
            float x = left + (point.roundPosition() / Math.max(1f, totalRounds)) * plotWidth;
            float y = centerY - scoreRatio(point.redScore) * halfHeight;

            // 折线向屏幕下方（红方视角分数下降）= 红方犯错；向上 = 黑方犯错。
            int beforeScore = index > 0 && index - 1 < scores.size() && scores.get(index - 1) != null
                    ? scores.get(index - 1) : initialScoreRed;
            boolean redBlunder;
            if (point.redScore != beforeScore) {
                redBlunder = point.redScore < beforeScore;
            } else {
                // Mate 距离变化时折线可能仍停在同一个 ±6000 高度；此时用实际走子方兜底。
                redBlunder = initialRedToMove ? (index % 2) == 0 : (index % 2) != 0;
            }
            errorPaint.setColor(redBlunder ? Color.rgb(220, 35, 35) : Color.BLACK);
            canvas.drawCircle(x, y, radius, errorPaint);
        }
    }

    private LabelPlacement chooseLabelPlacement(String text, float pointX, float pointY,
                                                float left, float right, float top, float bottom,
                                                float centerY, float halfHeight, float plotWidth,
                                                int totalRounds, List<RectF> occupied,
                                                boolean preferBelow) {
        Paint.FontMetrics fm = textPaint.getFontMetrics();
        float textWidth = textPaint.measureText(text);
        ArrayList<LabelPlacement> candidates = new ArrayList<LabelPlacement>();

        // 多档距离寻找空白区：先贴近局面点，曲线密集时再逐级拉远。
        float[] gaps = new float[]{dp(7), dp(15), dp(23)};
        for (float gap : gaps) {
            addVerticalCandidates(candidates, textWidth, fm, pointX, pointY, gap, preferBelow);
            addHorizontalCandidates(candidates, textWidth, fm, pointX, pointY, gap);
            addDiagonalCandidates(candidates, textWidth, fm, pointX, pointY, gap);
        }

        return bestPlacement(candidates, pointX, pointY, -1, left, right, top, bottom,
                centerY, halfHeight, plotWidth, totalRounds, occupied, false);
    }

    private LabelPlacement chooseErrorPlacement(String text, float pointX, float pointY,
                                                int pointIndex, float left, float right,
                                                float top, float bottom, float centerY,
                                                float halfHeight, float plotWidth, int totalRounds,
                                                List<RectF> occupied) {
        Paint.FontMetrics fm = textPaint.getFontMetrics();
        float textWidth = textPaint.measureText(text);
        ArrayList<LabelPlacement> candidates = new ArrayList<LabelPlacement>();

        // 优先斜向短箭头，必要时逐级拉远，避免原来的长竖线压住局势曲线。
        float[] gaps = new float[]{dp(9), dp(15), dp(21)};
        for (float gap : gaps) {
            addCurveNormalCandidates(candidates, textWidth, fm, pointX, pointY, pointIndex,
                    gap, centerY, halfHeight, plotWidth, totalRounds);
            addDiagonalCandidates(candidates, textWidth, fm, pointX, pointY, gap);
            addHorizontalCandidates(candidates, textWidth, fm, pointX, pointY, gap);
            addVerticalCandidates(candidates, textWidth, fm, pointX, pointY, gap, pointY < centerY);
        }

        return bestPlacement(candidates, pointX, pointY, pointIndex, left, right, top, bottom,
                centerY, halfHeight, plotWidth, totalRounds, occupied, true);
    }

    private void addCurveNormalCandidates(List<LabelPlacement> out, float textWidth,
                                          Paint.FontMetrics fm, float pointX, float pointY,
                                          int pointIndex, float gap, float centerY,
                                          float halfHeight, float plotWidth, int totalRounds) {
        int count = Math.min(scores.size(), totalMoves);
        if (pointIndex < 0 || pointIndex >= count || count < 2) return;
        int a = Math.max(0, pointIndex - 1);
        int b = Math.min(count - 1, pointIndex + 1);
        if (a == b) return;
        Point pa = pointAt(a);
        Point pb = pointAt(b);
        float left = dp(1);
        float ax = left + (pa.roundPosition() / Math.max(1f, totalRounds)) * plotWidth;
        float ay = centerY - scoreRatio(pa.redScore) * halfHeight;
        float bx = left + (pb.roundPosition() / Math.max(1f, totalRounds)) * plotWidth;
        float by = centerY - scoreRatio(pb.redScore) * halfHeight;
        float tx = bx - ax;
        float ty = by - ay;
        float length = (float) Math.sqrt(tx * tx + ty * ty);
        if (length < 0.5f) return;
        float nx = -ty / length;
        float ny = tx / length;
        for (int sign : new int[]{1, -1}) {
            float cx = pointX + nx * gap * sign;
            float cy = pointY + ny * gap * sign;
            float baseline = cy - (fm.ascent + fm.descent) / 2f;
            out.add(makePlacement(cx - textWidth / 2f, baseline, textWidth, fm, pointX, pointY));
        }
    }

    private void addVerticalCandidates(List<LabelPlacement> out, float textWidth,
                                       Paint.FontMetrics fm, float pointX, float pointY,
                                       float gap, boolean preferBelow) {
        float belowBaseline = pointY + gap - fm.ascent;
        float aboveBaseline = pointY - gap - fm.descent;
        float centeredX = pointX - textWidth / 2f;
        LabelPlacement below = makePlacement(centeredX, belowBaseline, textWidth, fm, pointX, pointY);
        LabelPlacement above = makePlacement(centeredX, aboveBaseline, textWidth, fm, pointX, pointY);
        if (preferBelow) {
            out.add(below);
            out.add(above);
        } else {
            out.add(above);
            out.add(below);
        }
    }

    private void addHorizontalCandidates(List<LabelPlacement> out, float textWidth,
                                         Paint.FontMetrics fm, float pointX, float pointY,
                                         float gap) {
        float baseline = pointY - (fm.ascent + fm.descent) / 2f;
        out.add(makePlacement(pointX + gap, baseline, textWidth, fm, pointX, pointY));
        out.add(makePlacement(pointX - gap - textWidth, baseline, textWidth, fm, pointX, pointY));
    }

    private void addDiagonalCandidates(List<LabelPlacement> out, float textWidth,
                                       Paint.FontMetrics fm, float pointX, float pointY,
                                       float gap) {
        float belowBaseline = pointY + gap - fm.ascent;
        float aboveBaseline = pointY - gap - fm.descent;
        out.add(makePlacement(pointX + gap, aboveBaseline, textWidth, fm, pointX, pointY));
        out.add(makePlacement(pointX - gap - textWidth, aboveBaseline, textWidth, fm, pointX, pointY));
        out.add(makePlacement(pointX + gap, belowBaseline, textWidth, fm, pointX, pointY));
        out.add(makePlacement(pointX - gap - textWidth, belowBaseline, textWidth, fm, pointX, pointY));
    }

    private LabelPlacement makePlacement(float x, float baseline, float textWidth,
                                         Paint.FontMetrics fm, float pointX, float pointY) {
        RectF bounds = new RectF(x, baseline + fm.ascent, x + textWidth, baseline + fm.descent);
        float anchorX = Math.max(bounds.left, Math.min(bounds.right, pointX));
        float anchorY = Math.max(bounds.top, Math.min(bounds.bottom, pointY));
        return new LabelPlacement(x, baseline, bounds, anchorX, anchorY);
    }

    private LabelPlacement bestPlacement(List<LabelPlacement> candidates,
                                         float pointX, float pointY, int pointIndex,
                                         float left, float right, float top, float bottom,
                                         float centerY, float halfHeight, float plotWidth,
                                         int totalRounds, List<RectF> occupied,
                                         boolean checkArrow) {
        LabelPlacement best = candidates.get(0);
        double bestPenalty = Double.MAX_VALUE;
        RectF safeBounds = new RectF(left + dp(2), top + dp(2), right - dp(2), bottom - dp(2));
        for (int i = 0; i < candidates.size(); i++) {
            LabelPlacement candidate = fitInside(candidates.get(i), safeBounds, pointX, pointY);
            double penalty = i * 0.02d; // 同分时保持候选优先级稳定。
            penalty += curveRectPenalty(candidate.bounds, centerY, halfHeight, plotWidth,
                    totalRounds) * 1000d;
            penalty += guideRectPenalty(candidate.bounds, left, right, top, bottom, centerY,
                    halfHeight, plotWidth, totalRounds) * 120d;
            penalty += occupiedPenalty(candidate.bounds, occupied) * 10000d;
            if (candidate.bounds.contains(pointX, pointY)) penalty += 100000d;
            if (checkArrow) {
                penalty += curveLinePenalty(candidate.anchorX, candidate.anchorY, pointX, pointY,
                        pointIndex, centerY, halfHeight, plotWidth, totalRounds) * 400d;
                penalty += occupiedLinePenalty(candidate.anchorX, candidate.anchorY,
                        pointX, pointY, occupied) * 10000d;
            }
            if (penalty < bestPenalty) {
                bestPenalty = penalty;
                best = candidate;
            }
        }

        return best;
    }

    private LabelPlacement fitInside(LabelPlacement placement, RectF safeBounds,
                                     float pointX, float pointY) {
        float dx = 0f;
        float dy = 0f;
        if (placement.bounds.left < safeBounds.left) dx = safeBounds.left - placement.bounds.left;
        else if (placement.bounds.right > safeBounds.right) dx = safeBounds.right - placement.bounds.right;
        if (placement.bounds.top < safeBounds.top) dy = safeBounds.top - placement.bounds.top;
        else if (placement.bounds.bottom > safeBounds.bottom) dy = safeBounds.bottom - placement.bounds.bottom;
        return dx == 0f && dy == 0f ? placement : placement.offset(dx, dy, pointX, pointY);
    }

    private int guideRectPenalty(RectF bounds, float left, float right, float top, float bottom,
                                 float centerY, float halfHeight, float plotWidth,
                                 int totalRounds) {
        RectF expanded = new RectF(bounds);
        expanded.inset(-dp(1), -dp(1));
        int hits = 0;
        float upper1700 = centerY - Math.abs(scoreRatio(SCORE_SPLIT_1)) * halfHeight;
        float lower1700 = centerY + Math.abs(scoreRatio(SCORE_SPLIT_1)) * halfHeight;
        float upper3700 = centerY - Math.abs(scoreRatio(SCORE_SPLIT_2)) * halfHeight;
        float lower3700 = centerY + Math.abs(scoreRatio(SCORE_SPLIT_2)) * halfHeight;
        for (float y : new float[]{top, upper3700, upper1700, centerY, lower1700, lower3700, bottom}) {
            if (y >= expanded.top && y <= expanded.bottom) hits++;
        }
        if (totalRounds >= 10) {
            float x = left + (10f / Math.max(1f, totalRounds)) * plotWidth;
            if (x >= expanded.left && x <= expanded.right) hits++;
        }
        if (endgameRound >= 0 && endgameRound <= totalRounds) {
            float x = left + (endgameRound / Math.max(1f, totalRounds)) * plotWidth;
            if (x >= expanded.left && x <= expanded.right) hits++;
        }
        if (trackingTouch) {
            int activePly = previewPly >= 0 ? previewPly : currentPly;
            float x = left + ((activePly / 2f) / Math.max(1f, totalRounds)) * plotWidth;
            if (x >= expanded.left && x <= expanded.right) hits++;
        }
        return hits;
    }

    private int curveRectPenalty(RectF bounds, float centerY, float halfHeight,
                                 float plotWidth, int totalRounds) {
        RectF expanded = new RectF(bounds);
        expanded.inset(-dp(2), -dp(2));
        int hits = 0;
        float left = dp(1);
        Point previous = null;
        float previousX = 0f;
        float previousY = 0f;
        for (int i = 0; i < scores.size() && i < totalMoves; i++) {
            Point point = pointAt(i);
            float x = left + (point.roundPosition() / Math.max(1f, totalRounds)) * plotWidth;
            float y = centerY - scoreRatio(point.redScore) * halfHeight;
            if (previous != null && segmentBoundsIntersect(previousX, previousY, x, y, expanded)) hits++;
            previous = point;
            previousX = x;
            previousY = y;
        }
        return hits;
    }

    private int curveLinePenalty(float x1, float y1, float x2, float y2, int pointIndex,
                                 float centerY, float halfHeight, float plotWidth,
                                 int totalRounds) {
        int hits = 0;
        float left = dp(1);
        float previousX = 0f;
        float previousY = 0f;
        boolean hasPrevious = false;
        for (int i = 0; i < scores.size() && i < totalMoves; i++) {
            Point point = pointAt(i);
            float x = left + (point.roundPosition() / Math.max(1f, totalRounds)) * plotWidth;
            float y = centerY - scoreRatio(point.redScore) * halfHeight;
            if (hasPrevious && i != pointIndex && i != pointIndex + 1
                    && segmentsIntersect(x1, y1, x2, y2, previousX, previousY, x, y)) hits++;
            previousX = x;
            previousY = y;
            hasPrevious = true;
        }
        return hits;
    }

    private int occupiedPenalty(RectF bounds, List<RectF> occupied) {
        if (occupied == null) return 0;
        RectF expanded = new RectF(bounds);
        expanded.inset(-dp(2), -dp(2));
        int hits = 0;
        for (RectF item : occupied) {
            if (item != null && RectF.intersects(expanded, item)) hits++;
        }
        return hits;
    }

    private int occupiedLinePenalty(float x1, float y1, float x2, float y2, List<RectF> occupied) {
        if (occupied == null) return 0;
        int hits = 0;
        for (RectF item : occupied) {
            if (item != null && segmentBoundsIntersect(x1, y1, x2, y2, item)) hits++;
        }
        return hits;
    }

    private boolean segmentBoundsIntersect(float x1, float y1, float x2, float y2, RectF rect) {
        RectF segment = new RectF(Math.min(x1, x2), Math.min(y1, y2),
                Math.max(x1, x2), Math.max(y1, y2));
        if (segment.width() < 1f) segment.right = segment.left + 1f;
        if (segment.height() < 1f) segment.bottom = segment.top + 1f;
        return RectF.intersects(segment, rect) || rect.contains(x1, y1) || rect.contains(x2, y2);
    }

    private boolean segmentsIntersect(float ax, float ay, float bx, float by,
                                      float cx, float cy, float dx, float dy) {
        float d1 = cross(ax, ay, bx, by, cx, cy);
        float d2 = cross(ax, ay, bx, by, dx, dy);
        float d3 = cross(cx, cy, dx, dy, ax, ay);
        float d4 = cross(cx, cy, dx, dy, bx, by);
        return ((d1 > 0f && d2 < 0f) || (d1 < 0f && d2 > 0f))
                && ((d3 > 0f && d4 < 0f) || (d3 < 0f && d4 > 0f));
    }

    private float cross(float ax, float ay, float bx, float by, float px, float py) {
        return (bx - ax) * (py - ay) - (by - ay) * (px - ax);
    }

    private void drawColoredSegment(Canvas canvas, float x1, float y1, int score1,
                                    float x2, float y2, int score2, float centerY) {
        if (score1 == 0 && score2 == 0) {
            canvas.drawLine(x1, y1, x2, y2, neutralCurvePaint);
        } else if (score1 >= 0 && score2 >= 0) {
            canvas.drawLine(x1, y1, x2, y2, redCurvePaint);
        } else if (score1 <= 0 && score2 <= 0) {
            canvas.drawLine(x1, y1, x2, y2, blueCurvePaint);
        } else {
            float total = Math.abs((float) score1) + Math.abs((float) score2);
            float ratio = total <= 0f ? 0.5f : Math.abs((float) score1) / total;
            float crossX = x1 + (x2 - x1) * ratio;
            Paint first = score1 > 0 ? redCurvePaint : blueCurvePaint;
            Paint second = score2 > 0 ? redCurvePaint : blueCurvePaint;
            canvas.drawLine(x1, y1, crossX, centerY, first);
            canvas.drawLine(crossX, centerY, x2, y2, second);
        }
    }

    private void drawScoreGuide(Canvas canvas, float left, float right,
                                float centerY, float halfHeight, int score, String label) {
        float ratio = Math.abs(scoreRatio(score));
        float upper = centerY - ratio * halfHeight;
        float lower = centerY + ratio * halfHeight;
        canvas.drawLine(left, upper, right, upper, gridPaint);
        canvas.drawLine(left, lower, right, lower, gridPaint);
        textPaint.setTextSize(dp(8));
        textPaint.setColor(Color.rgb(0, 150, 170));
        drawInsideLeftText(canvas, label, left + dp(3), upper - dp(2));
        drawInsideLeftText(canvas, "-" + label, left + dp(3), lower - dp(2));
    }

    private void drawStageLine(Canvas canvas, float left, float top, float bottom,
                               float plotWidth, int round, int totalRounds,
                               String label, int color, int row) {
        float x = left + (round / Math.max(1f, totalRounds)) * plotWidth;
        stagePaint.setColor(color);
        canvas.drawLine(x, top, x, bottom, stagePaint);
        textPaint.setColor(color);
        textPaint.setTextSize(dp(8));
        float labelX = Math.max(left + dp(2),
                Math.min(left + plotWidth - textPaint.measureText(label) - dp(2), x + dp(3)));
        canvas.drawText(label, labelX, top + dp(9 + row * 10), textPaint);
    }

    private void drawInsideLeftText(Canvas canvas, String text, float x, float baseline) {
        textPaint.setTextAlign(Paint.Align.LEFT);
        canvas.drawText(text, x, baseline, textPaint);
    }

    private float scoreRatio(int score) {
        float value = Math.min(MATE_LIMIT, Math.abs(score));
        float segment;
        if (value <= SCORE_SPLIT_1) {
            segment = (value / SCORE_SPLIT_1) / 3f;
        } else if (value <= SCORE_SPLIT_2) {
            segment = 1f / 3f + ((value - SCORE_SPLIT_1)
                    / (SCORE_SPLIT_2 - SCORE_SPLIT_1)) / 3f;
        } else {
            segment = 2f / 3f + ((value - SCORE_SPLIT_2)
                    / (MATE_LIMIT - SCORE_SPLIT_2)) / 3f;
        }
        return score < 0 ? -segment : segment;
    }

    private String navigationLabel(int ply) {
        if (ply <= 0) return "起始局面";
        return ((ply + 1) / 2) + "回合";
    }

    private String pointLabel(Point point) {
        int round = (point.plyIndex + 2) / 2;
        // ±6000 是规则终局补分；引擎普通分数即使超过 6000 也仍显示真实分值。
        if (Math.abs(point.redScore) == MATE_LIMIT && point.matePly <= 0) {
            return round + (point.redScore >= 0 ? "-红方胜" : "-黑方胜");
        }
        if (point.matePly > 0) {
            String side = point.redScore >= 0 ? "红绝杀" : "黑绝杀";
            return round + "-" + side + "M" + point.matePly;
        }
        if (point.redScore > 0) return round + "-红优" + point.redScore + "分";
        if (point.redScore < 0) return round + "-黑优" + Math.abs(point.redScore) + "分";
        return round + "-均势0分";
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static final class LabelPlacement {
        final float x;
        final float baseline;
        final RectF bounds;
        final float anchorX;
        final float anchorY;

        LabelPlacement(float x, float baseline, RectF bounds, float anchorX, float anchorY) {
            this.x = x;
            this.baseline = baseline;
            this.bounds = bounds;
            this.anchorX = anchorX;
            this.anchorY = anchorY;
        }

        LabelPlacement offset(float dx, float dy, float pointX, float pointY) {
            RectF moved = new RectF(bounds);
            moved.offset(dx, dy);
            float movedX = x + dx;
            float movedBaseline = baseline + dy;
            float movedAnchorX = Math.max(moved.left, Math.min(moved.right, pointX));
            float movedAnchorY = Math.max(moved.top, Math.min(moved.bottom, pointY));
            return new LabelPlacement(movedX, movedBaseline, moved, movedAnchorX, movedAnchorY);
        }
    }

    private static final class Point {
        final int plyIndex;
        final int redScore;
        final int matePly;

        Point(int plyIndex, int redScore, int matePly) {
            this.plyIndex = plyIndex;
            this.redScore = redScore;
            this.matePly = matePly;
        }

        float roundPosition() {
            return (plyIndex + 1) / 2f;
        }
    }
}
