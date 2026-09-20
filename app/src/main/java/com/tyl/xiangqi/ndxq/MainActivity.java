package com.tyl.xiangqi.ndxq;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Color;
import android.media.AudioAttributes;
import android.media.SoundPool;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Environment;
import android.provider.Settings;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.ClickableSpan;
import android.text.style.UnderlineSpan;
import android.text.method.LinkMovementMethod;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.tyl.xiangqi.ndxq.book.ObkBook;
import com.tyl.xiangqi.ndxq.core.ChineseNotation;
import com.tyl.xiangqi.ndxq.core.Move;
import com.tyl.xiangqi.ndxq.core.GameReportCalculator;
import com.tyl.xiangqi.ndxq.core.PgnManualUtils;
import com.tyl.xiangqi.ndxq.core.XqfManualUtils;
import com.tyl.xiangqi.ndxq.core.XiangqiRules;
import com.tyl.xiangqi.ndxq.engine.PikafishEngine;
import com.tyl.xiangqi.ndxq.storage.ManualFileIo;
import com.tyl.xiangqi.ndxq.storage.RecentGameStore;
import com.tyl.xiangqi.ndxq.ui.ChessBoardView;
import com.tyl.xiangqi.ndxq.ui.EngineAnalysisPanel;
import com.tyl.xiangqi.ndxq.ui.ManualStoreDialog;
import com.tyl.xiangqi.ndxq.ui.RecentGamesScreen;
import com.tyl.xiangqi.ndxq.ui.RescoreOptionsDialog;
import com.tyl.xiangqi.ndxq.ui.SegmentedDifficultyView;
import com.tyl.xiangqi.ndxq.ui.ToolbarIconButton;
import com.tyl.xiangqi.ndxq.ui.UiTheme;
import com.tyl.xiangqi.ndxq.ui.V68SituationChartView;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.security.SecureRandom;
import java.util.TreeMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


/** 节点象棋。版本号由 Gradle BuildConfig 提供。 */
public final class MainActivity extends Activity implements ChessBoardView.Listener {
    static final String VERSION_NAME = "V" + BuildConfig.VERSION_NAME;
    static final String PREFS = "human_vs_engine_v1";
    static final String MANUAL_UCI_PREFIX = "manual_uci::";
    static final String SAVED_ANALYSIS_RECORD = "saved_analysis_record";
    static final String SAVED_GAME_RECORD = "saved_game_record";
    static final String SAVED_EVALUATION_RECORD = "saved_evaluation_record";
    static final String START_FEN =
            "rnbakabnr/9/1c5c1/p1p1p1p1p/9/9/P1P1P1P1P/1C5C1/9/RNBAKABNR w - - 0 1";
    static final int SITUATION_MATE_LIMIT = 6000;
    static final int REQ_OPEN_MANUAL = 2701;
    static final int REQ_STORE_MANUAL = 2702;
    private static final int REQ_NODE_STORAGE_ACCESS = 2703;
    private static final String NODE_ROOT_DIR_NAME = "nodexq";
    private static final String RECENT_DIR_NAME = "recent";
    private static final String CORRECTION_DIR_NAME = "correction";
    private static final String PIC_DIR_NAME = "pic";
    static final String DEFAULT_SKIN_NAME = "default";
    static final String PREF_RED_ARROW_COLOR = "red_arrow_color";
    static final String PREF_BLACK_ARROW_COLOR = "black_arrow_color";
    static final String PREF_SITUATION_RED_COLOR = "situation_red_advantage_color";
    static final String PREF_SITUATION_BLACK_COLOR = "situation_black_advantage_color";
    static final String PREF_ENGINE_OUTPUT_STEPS = "engine_output_steps";
    static final String PREF_LATEST_DEPTH_ONLY = "engine_latest_depth_only";
    private static final String PREF_RATING = "player_rating";
    private static final String PREF_SHOW_RATING = "show_player_rating";
    private static final String PREF_V202_RATING_MIGRATED = "__v202_rating_migrated";
    static final int MIN_PLAYER_RATING = 500;
    private static final int[] CUSTOM_DIFFICULTY_INDICES =
            new int[]{0, 1, 3, 5, 7, 9, 11, 14, 16, 18, 19, 20, 21};
    static final int DEFAULT_RED_ARROW_COLOR = Color.rgb(214, 40, 40);
    static final int DEFAULT_BLACK_ARROW_COLOR = Color.rgb(40, 100, 214);
    static final int DEFAULT_SITUATION_RED_COLOR = Color.rgb(238, 42, 42);
    static final int DEFAULT_SITUATION_BLACK_COLOR = Color.rgb(25, 102, 235);
    static final int STORE_FORMAT_XQF = 0;
    static final int STORE_FORMAT_PGN = 1;
    static final int STORE_FORMAT_DHTML_UBB = 2;
    /** V19.6：玩家信息行继续保持紧凑高度，避免重复留白。 */
    static final int PLAYER_ROW_HEIGHT_DP = 24;
    static final int LAUNCHER_ACTION_HEIGHT_DP = 50;
    /** 分支框固定预留 3.2 行高度（略超出三行，提示下方还有更多分支）；超出部分由 ScrollView 自身滚动。 */
    static final int BRANCH_ROW_HEIGHT_DP = 34;
    static final float MAX_VISIBLE_BRANCH_ROWS = 3.2f;
    /** V19.7：首页文字根据实际背景采样动态选择黑/白高对比色。 */
    static final String LAUNCHER_TEXT_BG_GLOBAL = "launcher_text_global";
    static final String LAUNCHER_TEXT_BG_HOME = "launcher_text_home";
    static final String LAUNCHER_TEXT_BG_HOME_DOUBLE = "launcher_text_home_double";
    /** V19.8：首页“引擎执子”外层首页按钮色 + 内层高亮色的实际叠加背景。 */
    static final String LAUNCHER_TEXT_BG_HOME_HIGHLIGHT = "launcher_text_home_highlight";
    static final String LAUNCHER_TEXT_BG_HIGHLIGHT = "launcher_text_highlight";

    private static final DifficultyProfile[] DIFFICULTIES = DifficultyProfiles.createDefault();
    private DifficultyPreferences difficultyPreferences;
    GameSessionRepository sessionRepository;
    private NodeStorageManager nodeStorageManager;

    final Handler handler = new Handler(Looper.getMainLooper());
    /** 快速出招立即请求当前搜索收束；真正落子只接受同一局面的最终 bestmove。 */
    final Runnable immediateMoveStopCheck = new Runnable() {
        @Override public void run() { maybeRequestImmediateAnalysisBestMove(); }
    };
    /** 将可能等待旧搜索结束的分析启动移出主线程，避免点击放大镜和连续走棋时卡住界面。 */
    private final ExecutorService manualAnalysisStarter = Executors.newSingleThreadExecutor();
    /** V19.8：每次局面变化/暂停都会递增，阻止排队中的旧放大镜请求在稍后重新启动旧局面。 */
    volatile int manualAnalysisLaunchGeneration;
    /** V19.2：刷新当前步走前推荐使用独立启动线程，不阻塞 UI 或 go infinite 启动。 */
    private final ExecutorService reviewRefreshStarter = Executors.newSingleThreadExecutor();
    private final StringBuilder logBuffer = new StringBuilder();
    final List<String> engineMoves = new ArrayList<String>();
    final List<String> readableMoves = new ArrayList<String>();
    /** 初始局面/整盘棋谱说明；XQF 根注释和 PGN 首个正文注释均使用此字段。 */
    String initialComment = "";
    /** 导入棋谱携带的 PGN/UBB 标签，单独保存，避免与走法和注释耦合。 */
    final ManualMetadata manualMetadata = new ManualMetadata();
    /** 与主线每一步一一对应的棋谱注释；XQF/PGN 打开、编辑和保存均使用此列表。 */
    final List<String> moveComments = new ArrayList<String>();
    final List<Integer> redPerspectiveScores = new ArrayList<Integer>();
    final List<Integer> scoreMatePlies = new ArrayList<Integer>();
    /** 与每手局势分一一对应；false 表示该局面尚未取得真实评分，0 分也可被正确区分。 */
    final List<Boolean> scoreKnown = new ArrayList<Boolean>();
    /** V19.0：重新打分后，按实际 ply 保存“走前 PV1 推荐首着”以及该走后局面是否由重新打分得到。 */
    final List<String> rescoreRecommendedMoves = new ArrayList<String>();
    final List<Boolean> rescoreScoreKnown = new ArrayList<Boolean>();
    /** 报告计算第一手质量需要单独保存初始局面的评分。 */
    int initialScoreRed;
    int initialMatePly;
    boolean initialScoreKnown;
    boolean initialRescoreScoreKnown;
    /** V19.7：局势评分常早于“下一手”落子；按走前局面暂存 PV1/bestmove，落子时再写入对应复盘推荐。 */
    final Map<String, String> pendingSituationRecommendations =
            new HashMap<String, String>();
    /** V19.2：放大镜分析时并行刷新“当前已走一步”的走前评分/PV1 推荐，避免优劣提示被实时分析清空。 */
    volatile int reviewRefreshGeneration;
    volatile int reviewRefreshPly = -1;
    final Map<Integer, List<ManualVariation>> manualVariations =
            new HashMap<Integer, List<ManualVariation>>();
    /** 每个分支节点当前显示线路的固定字母编号。字母跟随线路，不随点击切换。 */
    final Map<Integer, String> activeBranchLabels =
            new HashMap<Integer, String>();
    final ManualBranchManager manualBranchManager =
            new ManualBranchManager(manualVariations, activeBranchLabels);
    final ManualBranchPanel manualBranchPanel = new ManualBranchPanel(this);
    final ManualBranchPanel.Actions manualBranchActions = new ManualBranchPanel.Actions() {
        @Override public void navigateToPly(int target) {
            MainActivity.this.navigateToPly(target);
        }

        @Override public void switchToVariation(int node, int variationIndex) {
            MainActivity.this.switchToVariation(node, variationIndex);
        }

        @Override public void showBranchActions(int node, int variationIndex,
                                                boolean active, String label) {
            MainActivity.this.showBranchLongPressDialog(node, variationIndex, active, label);
        }

        @Override public void editComment(int targetIndex) {
            MainActivity.this.showManualCommentDialog(targetIndex);
        }
    };
    final List<String> analysisBannedRootMoves = new ArrayList<String>();
    final TreeMap<Integer, AnalysisDisplayEntry> analysisEntries =
            new TreeMap<Integer, AnalysisDisplayEntry>();
    /** 按 MultiPV 序号分别保存各分支的深度历史，避免相同深度互相覆盖。 */
    final TreeMap<Integer, TreeMap<Integer, AnalysisDisplayEntry>> analysisPvEntries =
            new TreeMap<Integer, TreeMap<Integer, AnalysisDisplayEntry>>();

    FrameLayout appRoot;
    ChessBoardView boardView;
    LinearLayout gameContentHost;
    /** 棋盘过大时允许整页纵向滚动，确保底部注释区始终可达。 */
    ScrollView gamePageScroll;
    TextView manualTab;
    TextView engineTab;
    TextView situationTab;
    TextView topPlayerLabel;
    TextView bottomPlayerLabel;
    ToolbarIconButton analysisButton;
    ToolbarIconButton computerRedButton;
    ToolbarIconButton computerBlackButton;
    // V10 编辑模式全屏：需要隐藏这些行
    View editModeToolbar;
    View editModeTopRow;
    View editModeBottomLabel;
    View editModeTabs;
    /** V19.3：编辑面板进入编辑模式时只创建一次；后续交互仅原地刷新，避免每次点击重建整棵 View。 */
    View editPanelRoot;
    final Map<Character, View> editPieceCells = new HashMap<Character, View>();
    final Map<Character, ImageView> editPieceIcons = new HashMap<Character, ImageView>();
    final Map<Character, TextView> editPieceCounts = new HashMap<Character, TextView>();
    TextView editDeleteAction;
    TextView editBlackFirstAction;
    TextView editRedFirstAction;
    ScrollView manualScrollView;
    /** 新增棋步时自动跟随最新行；普通导航不设置此标记。 */
    boolean autoFollowLatestMove;
    ScrollView branchScrollView;
    ScrollView engineScrollView;
    LinearLayout engineContentHost;
    final SituationPanelController situationPanel = new SituationPanelController(this);
    final SituationPanelController.Actions situationPanelActions =
            new SituationPanelController.Actions() {
                @Override public void stopRescore() {
                    MainActivity.this.stopRescore(true);
                }

                @Override public void showRescoreOptions() {
                    MainActivity.this.showRescoreDialog();
                }

                @Override public void showGameReport() {
                    MainActivity.this.showGameReport();
                }

                @Override public void openCorrectionBook() {
                    MainActivity.this.openCorrectionBookFromSituation();
                }

                @Override public void previewPly(int target) {
                    MainActivity.this.previewSituationPly(target);
                }

                @Override public void navigateToPly(int target) {
                    MainActivity.this.navigateToPly(target);
                }
            };
    TextView launcherStatsText;
    AlertDialog drawAnalysisDialog;
    private final GameReportDialogController gameReportController =
            new GameReportDialogController(this, new GameReportDialogController.Source() {
                @Override public GameReportCalculator.Report buildReport(double softMinTau) {
                    return MainActivity.this.buildGameReport(findFirstEndgameRound(), softMinTau);
                }

                @Override public List<String> readableMoves() {
                    return new ArrayList<String>(MainActivity.this.readableMoves);
                }

                @Override public boolean redToMoveAtRoot() {
                    return XiangqiRules.redToMoveFromFen(baseFen);
                }

                @Override public void navigateToPly(int target) {
                    MainActivity.this.navigateToPly(target);
                }

                @Override public void refreshReportButton(GameReportCalculator.Report report) {
                    MainActivity.this.refreshReportButtonState(report);
                }
            });
    private SoundPool moveSoundPool;
    private int moveSoundId;
    private int checkSoundId;
    private boolean moveSoundLoaded;
    private boolean checkSoundLoaded;
    boolean soundEnabled = true;

    PikafishEngine gameEngine;
    PikafishEngine manualEngine;
    PikafishEngine drawEngine;
    PikafishEngine rescoreEngine;
    /** 对弈局势图专用：固定虚拟位 131、4 线程、每局面 100ms，并使用 ComputerRule。 */
    PikafishEngine situationEngine;
    ObkBook openingBook;
    boolean openingBookReady;

    int selectedDifficultyIndex;
    int playerRating = MIN_PLAYER_RATING;
    boolean showPlayerRating = true;
    /** 评测模式与自选难度共用棋盘流程，但等级分资格和页面能力独立。 */
    boolean evaluationMode;
    private int evaluationDifficultyIndex;
    private boolean evaluationEnginePlaysRed;
    boolean evaluationSession;
    /** 当前普通对局是否从自选难度页进入，用于系统返回键回到对应入口。 */
    private boolean customDifficultySession;
    boolean evaluationLauncherVisible;
    boolean evaluationRatingHistoryVisible;
    boolean customDifficultyLauncherVisible;
    Button evaluationDrawToolbarButton;
    Button evaluationResignToolbarButton;
    final SecureRandom secureRandom = new SecureRandom();
    boolean latestDepthOnly;
    boolean ratingEligible;
    boolean ratingDisqualified;
    boolean ratingCounted;
    private String loggedQingyunDrawRule = "";
    boolean computerRedBlackActive;
    boolean computerRedActive;
    boolean computerBlackActive;
    /** 自主分析中的电脑执红/黑搜索独立占用态，避免思考时棋盘仍可落子。 */
    boolean computerSideThinking;
    int computerMoveGeneration;
    PikafishEngine.SearchLimit manualPlayLimit = PikafishEngine.SearchLimit.movetime(100);
    private boolean manualPlayLimitConfigured;
    TextView launcherRatingText;
    boolean enginePlaysRed;
    boolean gameScreenVisible;
    boolean recentScreenVisible;
    boolean correctionListVisible;
    boolean correctionBoardVisible;
    /** 错题本从局势图进入时，退出后返回原对局局势图，而不是最近对局/首页。 */
    boolean correctionReturnToGame;
    boolean correctionReturnReversed;
    boolean correctionPermissionPending;
    volatile boolean temporaryAnalysisRunning;
    volatile int temporaryAnalysisGeneration;
    boolean engineThinking;
    boolean autoMoveInProgress;
    volatile boolean analysisMode;
    boolean selfAnalysisMode;
    boolean gameOver;
    /** 正式对弈一旦结束即保持为 true，棋谱导航和模式切换都不得清除。 */
    boolean completedDuelGame;
    /** 终局后从历史棋步分叉出的“1.5盘”，仅存在于内存，不写战绩/最近对局。 */
    boolean postGameSandboxActive;
    boolean terminalDialogShown;
    /**
     * 当前棋谱是否仍属于可计入历史战绩的正式对局。
     * V19.8 起，正式对弈中使用放大镜分析（含立即出招）不再改变本标记；
     * 只有切换自主分析模式、粘贴外部棋谱等真正改变棋谱性质的操作才取消战绩资格。
     */
    boolean competitiveResultEligible;
    /** 同一盘棋只允许写入一次历史战绩，棋谱导航和重新打分都不能重复结算。 */
    boolean resultRecordedForCurrentGame;
    String gameResultTag = "*";
    boolean drawOfferInProgress;
    boolean isRescoring;
    private final RescoreController rescoreController = new RescoreController(this);
    private final ManualEditorController manualEditorController =
            new ManualEditorController(this);
    private final ManualNavigationController manualNavigationController =
            new ManualNavigationController(this);
    private final EngineSettingsController engineSettingsController =
            new EngineSettingsController(this);
    private final SettingsController settingsController =
            new SettingsController(this);
    private final UiComponentController uiComponentController =
            new UiComponentController(this);
    private final GameTurnController gameTurnController =
            new GameTurnController(this);
    private final EvaluationRatingController evaluationRatingController =
            new EvaluationRatingController(this);
    private final GameMoveController gameMoveController =
            new GameMoveController(this);
    private final DuelEngineController duelEngineController =
            new DuelEngineController(this);
    private final ManualRecordController manualRecordController =
            new ManualRecordController(this);
    private final GameSessionController gameSessionController =
            new GameSessionController(this);
    private final GameEndDialogController gameEndDialogController =
            new GameEndDialogController(this);
    final AnalysisSessionController analysisSessionController =
            new AnalysisSessionController(this);
    private final AnalysisDisplayController analysisDisplayController =
            new AnalysisDisplayController(this);
    final AnalysisMoveController analysisMoveController =
            new AnalysisMoveController(this);
    final ComputerSideController computerSideController =
            new ComputerSideController(this);
    final GameScreenController gameScreenController =
            new GameScreenController(this);
    final AuxiliaryBoardController auxiliaryBoardController =
            new AuxiliaryBoardController(this);
    final CorrectionBookController correctionBookController =
            new CorrectionBookController(this);
    final GameActionMenuController gameActionMenuController =
            new GameActionMenuController(this);
    final GameContentController gameContentController =
            new GameContentController(this);
    final SkinRuntimeController skinRuntimeController =
            new SkinRuntimeController(this);
    private final SituationScoreController situationScoreController =
            new SituationScoreController(this);
    int selectedGameTab; // 0=棋谱，1=引擎，2=局势图
    int currentPly;
    volatile int operationGeneration;
    int drawGeneration;
    int situationScoreGeneration;
    int reportBackfillPass;
    boolean situationScoreEngineUnavailableLogged;
    int sixtyMoveDrawArmedPly = -1;
    long lastAnalysisLogAt;
    long lastEngineUiAt;
    int manualScrollY;
    int branchScrollY;
    /** 导航按钮改变局面后，棋谱页下一次布局自动把当前着法滚动到可见区域。 */
    boolean manualScrollToCurrentPly;
    int engineScrollY;
    int boardScalePercent = 100;
    /** 当前外置棋盘/棋子皮肤；文件夹名即菜单显示名。 */
    String currentSkinName = DEFAULT_SKIN_NAME;
    int currentSkinPieceSizePercent = 96;
    /** V19.4：红黑双方分析/推荐箭头颜色，可在皮肤设置中自定义。 */
    int redArrowColor = DEFAULT_RED_ARROW_COLOR;
    int blackArrowColor = DEFAULT_BLACK_ARROW_COLOR;
    /** V19.4：局势图红优/黑优曲线颜色。 */
    int situationRedAdvantageColor = DEFAULT_SITUATION_RED_COLOR;
    int situationBlackAdvantageColor = DEFAULT_SITUATION_BLACK_COLOR;
    /** V19.5：引擎界面每个深度最多显示多少步 PV；0 表示不限制。 */
    int engineOutputMoveLimit;
    /** 首页当前 View 树，仅用于原地刷新文字对比度，不触发整页重建。 */
    View launcherScreenRoot;
    SegmentedDifficultyView launcherDifficultyView;
    boolean launcherContrastRefreshPosted;
    /** 引擎页箭头显示模式：0=不显示，1=显示全部，2=只显示当前行棋方。 */
    int engineArrowDisplayMode = 1;
    /** 引擎页箭头显示总开关；由 engineArrowDisplayMode 派生并兼容旧调用方。 */
    boolean showEngineArrows = true;
    /** 单 PV 时循环 1/2/3/4/0 步；多 PV 使用 engineArrowDisplayMode 三态。 */
    int arrowStepCount = 2;

    int getArrowStepCount() {
        return arrowStepCount;
    }

    boolean showOnlyCurrentSideArrows() {
        return engineArrowDisplayMode == 2;
    }

    void disqualifyRating(String reason) {
        if (!evaluationMode || selfAnalysisMode || !ratingEligible || ratingDisqualified) return;
        ratingDisqualified = true;
        appendLog("本局已取消等级分计算资格：" + reason + "；连胜记录保留。\n");
    }
    /** 棋谱+引擎二合一布局开关。 */
    boolean combinedManualEngineMode;
    boolean pushModeActive;
    boolean pushReturnReversed;
    boolean pushResumeAnalysis;
    int pendingStoreFormat = STORE_FORMAT_XQF;
    volatile boolean immediateMovePending;
    boolean immediateMoveStopRequested;
    int immediateMoveGeneration = -1;
    String immediateMovePositionKey = "";
    int latestAnalysisDepth;
    int editPreviousBoardScalePercent = 100;

    String baseFen = START_FEN;
    String editBackupFen = START_FEN;
    int editBackupPly;
    char selectedEditPiece;
    Integer pendingMoveScoreRed;
    int pendingMoveMatePly;
    PikafishEngine.EngineInfo latestGameInfo;
    PikafishEngine.EngineInfo latestAnalysisInfo;
    String latestAnalysisBestMove = "";
    String analysisPositionKey = "";
    int analysisExpectedMultiPv = 1;
    /** 保存用户配置的 MultiPV，避免重建标签页时被瞬时单 PV info 误判。 */
    int analysisConfiguredMultiPv = 1;
    /** 最近一次完整 MultiPV 模块快照，切页/重建 View 时继续使用。 */
    List<EngineAnalysisPanel.DepthModule> lastMultiPvModules =
            Collections.<EngineAnalysisPanel.DepthModule>emptyList();
    /** 用户主动停止分析后冻结最后一次显示；导航/切页/走子都不能改写，直到下一次启动分析。 */
    boolean analysisDisplayFrozen;
    int frozenAnalysisExpectedMultiPv = 1;
    CharSequence frozenEngineContent = "";
    CharSequence frozenCompactEngineContent = "";
    List<EngineAnalysisPanel.DepthModule> frozenMultiPvModules =
            Collections.<EngineAnalysisPanel.DepthModule>emptyList();
    CharSequence engineContent = "尚未开始分析。点击上方“分析”后，将在此显示实时内容。";

    /**
     * 极端系统字体倍率会把大量固定高度的棋盘工具行挤爆。V19.5 仅对应用内字号
     * 做温和上限保护；系统“显示大小”仍照常生效，正文/滚动区域也继续可滚动。
     */
    @Override
    protected void attachBaseContext(Context newBase) {
        Configuration current = newBase.getResources().getConfiguration();
        if (current.fontScale > 1.18f) {
            Configuration adjusted = new Configuration(current);
            adjusted.fontScale = 1.18f;
            super.attachBaseContext(newBase.createConfigurationContext(adjusted));
        } else {
            super.attachBaseContext(newBase);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (Build.VERSION.SDK_INT >= 21) {
            getWindow().setStatusBarColor(Color.rgb(35, 46, 41));
            getWindow().setNavigationBarColor(Color.rgb(25, 30, 28));
        }
        clearStaleEnginePrefsOnUpgrade();
        difficultyPreferences = new DifficultyPreferences(
                getSharedPreferences(PREFS, MODE_PRIVATE), DIFFICULTIES, CUSTOM_DIFFICULTY_INDICES);
        sessionRepository = new GameSessionRepository(
                getSharedPreferences(PREFS, MODE_PRIVATE), START_FEN, DIFFICULTIES.length);
        nodeStorageManager = new NodeStorageManager(getApplicationContext(), NODE_ROOT_DIR_NAME,
                RECENT_DIR_NAME, CORRECTION_DIR_NAME, PIC_DIR_NAME);
        loadPreferences();
        initializeMoveSounds();
        appRoot = new FrameLayout(this);
        appRoot.setBackgroundColor(globalRootBackgroundColor());
        setContentView(appRoot);
        applySystemBarInsets(appRoot);
        requestGlobalBackgroundRefresh();

        // false：不读取旧版全局 UCI 参数，四个用途分别用 session options 隔离。
        gameEngine = new PikafishEngine(this, false);
        manualEngine = new PikafishEngine(this, false);
        drawEngine = new PikafishEngine(this, false);
        rescoreEngine = new PikafishEngine(this, false);
        situationEngine = new PikafishEngine(this, false);
        configureToolEngines();
        installAndLoadRpBook();

        appendLog("节点象棋 " + VERSION_NAME + " 启动。\n");
        showLauncherScreen();
        // V19.1：在首页展示期间后台预热当前皮肤，进入棋盘时直接复用已解码 Bitmap。
        skinRuntimeController.preloadCurrentSkin();
        handler.postDelayed(this::prepareNodeStorageOnFirstLaunch, 260L);
        handler.post(() -> handleIncomingManualIntent(getIntent()));
    }

    private void initializeMoveSounds() {
        AudioAttributes attributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();
        moveSoundPool = new SoundPool.Builder()
                .setMaxStreams(2)
                .setAudioAttributes(attributes)
                .build();
        moveSoundPool.setOnLoadCompleteListener((soundPool, sampleId, status) -> {
            if (status != 0) return;
            if (sampleId == moveSoundId) moveSoundLoaded = true;
            if (sampleId == checkSoundId) checkSoundLoaded = true;
        });
        moveSoundId = moveSoundPool.load(this, R.raw.move, 1);
        checkSoundId = moveSoundPool.load(this, R.raw.check, 1);
    }

    /** 当前棋盘已完成一步走子或向后导航后，根据新局面选择走子/将军音效。 */
    void playMoveSoundForCurrentPosition(boolean redToMoveNow) {
        if (!soundEnabled || moveSoundPool == null || boardView == null) return;
        boolean checking = XiangqiRules.isInCheck(boardView.copyBoard(), redToMoveNow);
        int soundId = checking ? checkSoundId : moveSoundId;
        boolean loaded = checking ? checkSoundLoaded : moveSoundLoaded;
        if (loaded && soundId != 0) moveSoundPool.play(soundId, 1f, 1f, 1, 0, 1f);
    }

    // V8.0 覆盖安装修复：升级后清理引擎缓存的旧 EvalFile、Threads/Hash 等持久化参数，
    // 防止旧 APK 的 nativeLibraryDir 路径或其他残留值污染新版本引擎启动。
    // 使用独立的一次性标记（不依赖 versionCode，防止同版本反复安装导致条件不触发）。
    private void clearStaleEnginePrefsOnUpgrade() {
        try {
            SharedPreferences sp = getSharedPreferences(PREFS, MODE_PRIVATE);
            boolean alreadyCleared = sp.getBoolean("__v8_engine_cleaned_v2", false);
            if (!alreadyCleared) {
                getSharedPreferences("pikafish_engine_options", MODE_PRIVATE).edit().clear().apply();
                // 同时清理 manual_uci:: 前缀的手动引擎选项
                SharedPreferences.Editor edit = sp.edit();
                for (String key : sp.getAll().keySet()) {
                    if (key != null && key.startsWith(MANUAL_UCI_PREFIX)) edit.remove(key);
                }
                edit.putBoolean("__v8_engine_cleaned_v2", true).apply();
                appendLog("[V8修复] 已清理引擎残留缓存（含手动UCI选项），确保覆盖安装后引擎正常启动。\n");
            }
        } catch (Exception ignored) {}
    }

    private void applySystemBarInsets(final View root) {
        if (root == null || Build.VERSION.SDK_INT < 21) return;
        root.setFitsSystemWindows(true);
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            int top;
            int bottom;
            if (Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars());
                top = bars.top;
                bottom = bars.bottom;
            } else {
                top = insets.getSystemWindowInsetTop();
                bottom = insets.getSystemWindowInsetBottom();
            }
            v.setPadding(0, Math.max(0, top), 0, Math.max(0, bottom));
            return insets;
        });
        root.requestApplyInsets();
    }

    private void loadPreferences() {
        difficultyPreferences.migrateFromV199(SAVED_GAME_RECORD, SAVED_ANALYSIS_RECORD);
        difficultyPreferences.migrateFromV202(SAVED_GAME_RECORD, SAVED_ANALYSIS_RECORD);
        difficultyPreferences.fixEarlyV202CustomMapping();
        SharedPreferences sp = getSharedPreferences(PREFS, MODE_PRIVATE);
        selectedDifficultyIndex = clamp(sp.getInt("difficulty", 0), 0, DIFFICULTIES.length - 1);
        enginePlaysRed = sp.getBoolean("engine_red", false);
        // 首次升级到独立自选难度存储时，先保留旧版用户选择；评测随机档位随后
        // 只写评测会话，不再改变自选入口的默认档位和执子方。
        if (!difficultyPreferences.hasCustomSelection()) {
            difficultyPreferences.saveCustomSelection(selectedDifficultyIndex, enginePlaysRed);
        }
        boardScalePercent = clamp(sp.getInt("board_scale_percent", 100), 50, 100);
        currentSkinName = sanitizeSkinName(sp.getString("skin_name", DEFAULT_SKIN_NAME));
        currentSkinPieceSizePercent = clamp(sp.getInt(skinPieceSizeKey(currentSkinName), 96), 55, 125);
        redArrowColor = sp.getInt(PREF_RED_ARROW_COLOR, DEFAULT_RED_ARROW_COLOR);
        blackArrowColor = sp.getInt(PREF_BLACK_ARROW_COLOR, DEFAULT_BLACK_ARROW_COLOR);
        situationRedAdvantageColor = sp.getInt(PREF_SITUATION_RED_COLOR, DEFAULT_SITUATION_RED_COLOR);
        situationBlackAdvantageColor = sp.getInt(PREF_SITUATION_BLACK_COLOR, DEFAULT_SITUATION_BLACK_COLOR);
        engineOutputMoveLimit = clamp(sp.getInt(PREF_ENGINE_OUTPUT_STEPS, 0), 0, 999);
        latestDepthOnly = sp.getBoolean(PREF_LATEST_DEPTH_ONLY, false);
        migratePlayerRatingMinimum(sp);
        playerRating = Math.max(MIN_PLAYER_RATING, clamp(sp.getInt(PREF_RATING, 500), MIN_PLAYER_RATING, 4000));
        showPlayerRating = true;
        pendingStoreFormat = clamp(sp.getInt("manual_store_format", STORE_FORMAT_XQF),
                STORE_FORMAT_XQF, STORE_FORMAT_DHTML_UBB);
        selectedGameTab = clamp(sp.getInt("selected_game_tab", 0), 0, 2);
        boolean oldArrowEnabled = sp.getBoolean("show_engine_arrows", true);
        arrowStepCount = clamp(sp.getInt("engine_arrow_steps", oldArrowEnabled ? 2 : 0), 0, 4);
        int savedArrowMode = sp.getInt("engine_arrow_display_mode", -1);
        engineArrowDisplayMode = savedArrowMode >= 0
                ? clamp(savedArrowMode, 0, 2) : (arrowStepCount > 0 ? 1 : 0);
        showEngineArrows = engineArrowDisplayMode != 0;
        soundEnabled = sp.getBoolean("move_sound_enabled", true);
        combinedManualEngineMode = sp.getBoolean("combined_manual_engine_mode", false);
        int savedManualMs = Math.max(0, sp.getInt("manual_play_movetime_ms", 100));
        int savedManualDepth = Math.max(0, sp.getInt("manual_play_depth", 0));
        int savedManualNodes = Math.max(0, sp.getInt("manual_play_nodes", 0));
        manualPlayLimitConfigured = sp.getBoolean("manual_play_limit_configured", false);
        // V20.1 originally shipped with an implicit one-second default. Migrate only that
        // untouched three-field preset; explicitly saved user limits remain intact.
        if (!manualPlayLimitConfigured && savedManualMs == 1000
                && savedManualDepth == 0 && savedManualNodes == 0) {
            savedManualMs = 100;
        }
        manualPlayLimit = PikafishEngine.SearchLimit.combined(
                savedManualDepth, savedManualNodes, savedManualMs);
        if (combinedManualEngineMode && selectedGameTab == 1) selectedGameTab = 0;
    }

    void saveLauncherPreferences() {
        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                .putInt("difficulty", selectedDifficultyIndex)
                .putBoolean("engine_red", enginePlaysRed)
                .putInt("board_scale_percent", boardScalePercent)
                .putString("skin_name", currentSkinName)
                .putInt(skinPieceSizeKey(currentSkinName), currentSkinPieceSizePercent)
                .putInt(PREF_RED_ARROW_COLOR, redArrowColor)
                .putInt(PREF_BLACK_ARROW_COLOR, blackArrowColor)
                .putInt(PREF_SITUATION_RED_COLOR, situationRedAdvantageColor)
                .putInt(PREF_SITUATION_BLACK_COLOR, situationBlackAdvantageColor)
                .putInt(PREF_ENGINE_OUTPUT_STEPS, engineOutputMoveLimit)
                .putBoolean(PREF_LATEST_DEPTH_ONLY, latestDepthOnly)
                .putInt(PREF_RATING, Math.max(MIN_PLAYER_RATING, clamp(playerRating, MIN_PLAYER_RATING, 4000)))
                .putInt("manual_store_format", pendingStoreFormat)
                .putInt("selected_game_tab", selectedGameTab)
                .putBoolean("show_engine_arrows", showEngineArrows)
                .putInt("engine_arrow_display_mode", engineArrowDisplayMode)
                .putInt("engine_arrow_steps", arrowStepCount)
                .putBoolean("move_sound_enabled", soundEnabled)
                .putBoolean("combined_manual_engine_mode", combinedManualEngineMode)
                .putInt("manual_play_movetime_ms", manualPlayLimit.moveTimeMs)
                .putInt("manual_play_depth", manualPlayLimit.depth)
                .putInt("manual_play_nodes", manualPlayLimit.nodes)
                .putBoolean("manual_play_limit_configured", manualPlayLimitConfigured)
                .apply();
    }

    private void migratePlayerRatingMinimum(SharedPreferences sp) {
        if (sp.getBoolean(PREF_V202_RATING_MIGRATED, false)) return;
        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                .putInt(PREF_RATING, MIN_PLAYER_RATING)
                .putBoolean(PREF_V202_RATING_MIGRATED, true)
                .apply();
    }

    private void configureToolEngines() {
        // 优先使用专用 131 槽；源码包未内置 lib131.so 时，自动回退到“引擎设置”
        // 中当前选择的外部/通用引擎，避免分析、重新打分一直停在等待状态。
        boolean has131 = manualEngine.hasVirtualEngine("131");
        String toolSlot = has131 ? "131" : "";
        manualEngine.setVirtualEngineSlot(toolSlot);
        rescoreEngine.setVirtualEngineSlot(toolSlot);
        drawEngine.setVirtualEngineSlot(toolSlot);
        applyStoredManualOptions(manualEngine);
        applyStoredManualOptions(rescoreEngine);
        // 提和固定 8 线程，不受“引擎设置”里的 Threads 值影响。
        drawEngine.clearSessionOptions();
        drawEngine.setSessionOption("Threads", "8");
        drawEngine.setSessionOption("MultiPV", "1");
        drawEngine.setSessionOption("UCI_ShowWDL", "true");

        // V18.5 修正：局势图评分继续严格固定虚拟位 131，只额外使用 ComputerRule；
        // 线程数、MultiPV、WDL 及其他用途的引擎选择均保持原逻辑不变。
        situationEngine.setVirtualEngineSlot("131");
        situationEngine.clearSessionOptions();
        situationEngine.setSessionOption("Threads", "4");
        situationEngine.setSessionOption("MultiPV", "1");
        situationEngine.setSessionOption("UCI_ShowWDL", "true");
        situationEngine.setSessionOption("Repetition Rule", "ComputerRule");
        if (!has131) {
            appendLog("未发现 lib131.so：分析、重新打分和提和已自动改用当前选择的引擎。\n");
        }
    }

    void applyStoredManualOptions(PikafishEngine engine) {
        if (engine == null) return;
        engine.clearSessionOptions();
        SharedPreferences sp = getSharedPreferences(PREFS, MODE_PRIVATE);
        for (Map.Entry<String, ?> entry : sp.getAll().entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            if (key != null && key.startsWith(MANUAL_UCI_PREFIX) && value instanceof String) {
                engine.setSessionOption(key.substring(MANUAL_UCI_PREFIX.length()), String.valueOf(value));
            }
        }
    }

    void showLauncherScreen() {
        evaluationMode = false;
        evaluationLauncherVisible = false;
        evaluationRatingHistoryVisible = false;
        customDifficultyLauncherVisible = false;
        customDifficultySession = false;
        stopAllEngineWork();
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        gameScreenVisible = false;
        recentScreenVisible = false;
        correctionListVisible = false;
        correctionBoardVisible = false;
        correctionReturnToGame = false;
        clearFrozenAnalysisDisplay();
        gamePageScroll = null;
        appRoot.removeAllViews();

        LauncherController.showHome(this);
    }

    /** 原首页的完整自选难度界面保留，只作为独立入口页面展示。 */
    void showCustomDifficultyLauncher() {
        evaluationMode = false;
        activateCustomDifficultySelection();
        customDifficultyLauncherVisible = true;
        evaluationLauncherVisible = false;
        showCustomDifficultyPage();
    }

    void showEvaluationLauncher() {
        evaluationLauncherVisible = true;
        evaluationRatingHistoryVisible = false;
        customDifficultyLauncherVisible = false;
        stopAllEngineWork();
        gameScreenVisible = false;
        launcherScreenRoot = null;
        appRoot.removeAllViews();
        LauncherController.showEvaluation(this);
    }

    void handleEvaluationLauncherStart() {
        if (evaluationSession && !gameScreenVisible && !completedDuelGame
                && !engineMoves.isEmpty()) {
            resumeEvaluationSession();
            return;
        }
        SavedSession saved = readSavedEvaluationSession();
        if (saved != null) {
            promptSavedEvaluationSession(saved);
            return;
        }
        startEvaluationSession();
    }

    private void promptSavedEvaluationSession(SavedSession saved) {
        new AlertDialog.Builder(this)
                .setMessage("上次还有未完成的评测，是否继续？")
                .setNegativeButton("新的对局", (d, w) -> {
                    clearEvaluationSession();
                    startEvaluationSession();
                })
                .setPositiveButton("继续对局", (d, w) -> resumeSavedEvaluationSession(saved))
                .setOnCancelListener(d -> {})
                .show();
    }

    private void showCustomDifficultyPage() {
        customDifficultyLauncherVisible = true;
        evaluationLauncherVisible = false;
        stopAllEngineWork();
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        gameScreenVisible = false;
        recentScreenVisible = false;
        correctionListVisible = false;
        correctionBoardVisible = false;
        correctionReturnToGame = false;
        correctionPermissionPending = false;
        clearFrozenAnalysisDisplay();
        gamePageScroll = null;
        appRoot.removeAllViews();
        LauncherController.showCustomDifficulty(this);
    }

    private void activateCustomDifficultySelection() {
        selectedDifficultyIndex =
                difficultyPreferences.customSelectedDifficulty(selectedDifficultyIndex);
        enginePlaysRed = difficultyPreferences.customEnginePlaysRed(enginePlaysRed);
    }

    /** 二级入口只把未完成评测标成“继续”；已完成评测已经结算并清理，不得恢复。 */
    boolean evaluationResumeAvailable() {
        if (completedDuelGame) return false;
        if (evaluationSession && !gameScreenVisible && !engineMoves.isEmpty()) return true;
        return readSavedEvaluationSession() != null;
    }

    private String statsKey(int difficultyIndex, String suffix) {
        return difficultyPreferences.statsKey(difficultyIndex, suffix);
    }

    String difficultyNameKey(int difficultyIndex) {
        return difficultyPreferences.nameKey(difficultyIndex);
    }

    String difficultyDisplayName(int difficultyIndex) {
        return difficultyPreferences.displayName(difficultyIndex);
    }

    int[] customDifficultyIndices() {
        return CUSTOM_DIFFICULTY_INDICES.clone();
    }

    String difficultyDefaultName(int difficultyIndex) {
        return DIFFICULTIES[clamp(difficultyIndex, 0, DIFFICULTIES.length - 1)].name;
    }

    String currentDifficultyDisplayName() {
        return difficultyDisplayName(selectedDifficultyIndex);
    }

    private String[] difficultyDisplayLabels() {
        return difficultyPreferences.displayLabels();
    }

    String[] customDifficultyDisplayLabels() {
        return difficultyPreferences.customDisplayLabels();
    }

    int[] customDifficultyDisplayNumbers() {
        return difficultyPreferences.customDisplayNumbers();
    }

    int customDifficultyPosition(int difficultyIndex) {
        return difficultyPreferences.customPosition(difficultyIndex);
    }

    void refreshLauncherDifficultyLabels() {
        if (launcherDifficultyView == null) return;
        launcherDifficultyView.setLabels(customDifficultyDisplayLabels());
        launcherDifficultyView.setSelectedIndex(customDifficultyPosition(selectedDifficultyIndex));
    }

    void selectCustomDifficultyPosition(int position) {
        selectedDifficultyIndex = CUSTOM_DIFFICULTY_INDICES[
                clamp(position, 0, CUSTOM_DIFFICULTY_INDICES.length - 1)];
        difficultyPreferences.saveCustomSelection(selectedDifficultyIndex, enginePlaysRed);
        saveLauncherPreferences();
        updateLauncherStatsText();
    }

    void setCustomEnginePlaysRed(boolean red) {
        enginePlaysRed = red;
        difficultyPreferences.saveCustomSelection(selectedDifficultyIndex, enginePlaysRed);
        saveLauncherPreferences();
    }

    void randomizeCustomDifficulty() {
        selectedDifficultyIndex = CUSTOM_DIFFICULTY_INDICES[
                secureRandom.nextInt(CUSTOM_DIFFICULTY_INDICES.length)];
        enginePlaysRed = secureRandom.nextBoolean();
        difficultyPreferences.saveCustomSelection(selectedDifficultyIndex, enginePlaysRed);
        saveLauncherPreferences();
        refreshLauncherDifficultyLabels();
        updateLauncherStatsText();
    }


    private int getDifficultyStat(int difficultyIndex, String suffix) {
        return difficultyPreferences.getStat(difficultyIndex, suffix);
    }

    void updateLauncherStatsText() {
        if (launcherStatsText == null) return;
        int wins = getDifficultyStat(selectedDifficultyIndex, "win");
        int draws = getDifficultyStat(selectedDifficultyIndex, "draw");
        int losses = getDifficultyStat(selectedDifficultyIndex, "loss");
        launcherStatsText.setText(wins + "胜" + draws + "和" + losses + "负");
    }

    void resetAllDifficultyStats() {
        difficultyPreferences.resetAllStats();
        updateLauncherStatsText();
        Toast.makeText(this, "战绩已重置", Toast.LENGTH_SHORT).show();
    }

    void handleLauncherStartGame() {
        evaluationMode = false;
        evaluationSession = false;
        customDifficultySession = true;
        activateCustomDifficultySelection();
        SavedSession saved = readSavedSession(false);
        if (saved == null) {
            startNewGame();
            return;
        }
        String difficultyName = difficultyDisplayName(saved.difficultyIndex);
        String message = saved.completedDuelGame
                ? "上次保留了一盘已结束的棋谱，难度为" + difficultyName + "，是否查看？"
                : "上次还有未完成的对局，难度为" + difficultyName + "，是否继续？";
        new AlertDialog.Builder(this)
                .setMessage(message)
                .setNegativeButton("新的对局", (d, w) -> startNewGame())
                .setPositiveButton(saved.completedDuelGame ? "查看棋谱" : "继续对局",
                        (d, w) -> resumeSavedSession(saved))
                .show();
    }

    void startEvaluationSession() {
        customDifficultySession = false;
        int[] ratings = new int[DIFFICULTIES.length];
        for (int i = 0; i < ratings.length; i++) ratings[i] = difficultyRatingForIndex(i);
        evaluationDifficultyIndex = EvaluationMatcher.choose(playerRating, ratings);
        evaluationEnginePlaysRed = EvaluationMatcher.randomRed();
        selectedDifficultyIndex = evaluationDifficultyIndex;
        enginePlaysRed = evaluationEnginePlaysRed;
        evaluationMode = true;
        evaluationSession = true;
        clearEvaluationSession();
        startNewGame();
        appendLog("开始评测：匹配" + currentDifficultyDisplayName() + "，电脑执"
                + (enginePlaysRed ? "红" : "黑") + "，玩家等级分 " + playerRating + "。\n");
    }

    private void resumeEvaluationSession() {
        if (!evaluationSession || engineMoves.isEmpty()) {
            startEvaluationSession();
            return;
        }
        evaluationMode = true;
        customDifficultySession = false;
        configureGameEngine();
        showGameScreen();
        try {
            rebuildBoardToPly(currentPly);
            boardView.setReversed(enginePlaysRed);
        } catch (Exception e) {
            appendLog("恢复评测对局失败：" + e.getMessage() + "。\n");
            startEvaluationSession();
            return;
        }
        updatePlayerLabels();
        refreshBoardInputState();
        updateGameContent();
        if (!gameOver && !completedDuelGame) handler.postDelayed(this::maybeAutoMove, 120L);
    }

    private void returnToEvaluationLauncherPreservingGame() {
        if (!evaluationMode) {
            showLauncherScreen();
            return;
        }
        if (completedDuelGame) {
            discardFinishedEvaluationState();
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            showEvaluationLauncher();
            return;
        }
        if (!evaluationSession) {
            showLauncherScreen();
            return;
        }
        persistCurrentSession(true);
        stopAllEngineWork();
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        gameScreenVisible = false;
        gamePageScroll = null;
        appRoot.removeAllViews();
        showEvaluationLauncher();
    }

    /** 评测终局离开棋盘后清掉仅供复盘的内存快照，避免污染后续入口状态。 */
    void discardFinishedEvaluationState() {
        evaluationSession = false;
        evaluationMode = false;
        customDifficultySession = false;
        resetGameState();
        gameScreenController.discardFinishedEvaluationScreen();
    }

    private void returnToCustomDifficultyLauncherPreservingGame() {
        if (!customDifficultySession) {
            showLauncherScreen();
            return;
        }
        persistCurrentSession(true);
        stopAllEngineWork();
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        gameScreenVisible = false;
        gamePageScroll = null;
        appRoot.removeAllViews();
        showCustomDifficultyPage();
    }

    void handleLauncherAnalysisEntry() {
        SavedSession saved = readSavedSession(true);
        if (saved != null) resumeSavedSession(saved);
        else startSelfAnalysisSession();
    }


    /** Android 无法在 APK 安装阶段直接执行代码；首次启动时创建指定公共目录。 */
    private void prepareNodeStorageOnFirstLaunch() {
        if (ensureNodeStorageReady(false)) return;
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        if (prefs.getBoolean("node_storage_prompted_v178", false)) return;
        prefs.edit().putBoolean("node_storage_prompted_v178", true).apply();
        new AlertDialog.Builder(this)
                .setTitle("存储权限")
                .setMessage("为读取用户新增的 /storage/emulated/0/nodexq/pic 皮肤并保存最近对局，需要授予文件访问权限。默认皮肤已内置，无需授权也可正常使用。")
                .setPositiveButton("去授权", (d, w) -> requestNodeStorageAccess())
                .setNegativeButton("暂不", null)
                .show();
    }

    private File nodeRootDirectory() {
        return nodeStorageManager.rootDirectory();
    }

    private File recentDirectory() {
        return nodeStorageManager.recentDirectory();
    }

    File correctionDirectory() {
        return nodeStorageManager.correctionDirectory();
    }

    private File picDirectory() {
        return nodeStorageManager.pictureDirectory();
    }

    NodeStorageManager storageManager() {
        return nodeStorageManager;
    }

    File skinDirectory(String skinName) {
        return skinRuntimeController.skinDirectory(skinName);
    }

    int globalBackgroundColor() {
        return skinRuntimeController.globalBackgroundColor();
    }

    int globalRootBackgroundColor() {
        return skinRuntimeController.globalRootBackgroundColor();
    }

    int homepageButtonColor() {
        return skinRuntimeController.homepageButtonColor();
    }

    int globalBackgroundTextColor() {
        return skinRuntimeController.globalBackgroundTextColor();
    }

    int globalSurfaceFillColor() {
        return skinRuntimeController.globalSurfaceFillColor();
    }

    void requestGlobalBackgroundRefresh() {
        skinRuntimeController.requestGlobalBackgroundRefresh();
    }

    void refreshGlobalSurfaceTheme() {
        skinRuntimeController.refreshGlobalSurfaceTheme();
    }

    void markLauncherTextBackground(TextView view, String backgroundKind) {
        skinRuntimeController.markLauncherTextBackground(view, backgroundKind);
    }

    void scheduleLauncherTextContrastRefresh(View anchor) {
        skinRuntimeController.scheduleLauncherTextContrastRefresh(anchor);
    }

    boolean ensureNodeStorageReady(boolean requestIfMissing) {
        if (Build.VERSION.SDK_INT >= 30 && !Environment.isExternalStorageManager()) {
            if (requestIfMissing) requestNodeStorageAccess();
            return false;
        }
        if (Build.VERSION.SDK_INT >= 23 && Build.VERSION.SDK_INT <= 29
                && checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            if (requestIfMissing) requestNodeStorageAccess();
            return false;
        }
        NodeStorageManager.EnsureResult result = nodeStorageManager.ensureDirectories();
        if (result.ready) return true;
        appendLog("创建 nodexq 公共目录失败：" + result.error + "。\n");
        return false;
    }

    void requestNodeStorageAccess() {
        try {
            if (Build.VERSION.SDK_INT >= 30) {
                Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                        Uri.parse("package:" + getPackageName()));
                startActivityForResult(intent, REQ_NODE_STORAGE_ACCESS);
            } else if (Build.VERSION.SDK_INT >= 23) {
                requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                        REQ_NODE_STORAGE_ACCESS);
            } else {
                ensureNodeStorageReady(false);
            }
        } catch (Exception primary) {
            if (Build.VERSION.SDK_INT >= 30) {
                try {
                    startActivityForResult(new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION),
                            REQ_NODE_STORAGE_ACCESS);
                    return;
                } catch (Exception ignored) {}
            }
            Toast.makeText(this, "无法打开存储权限设置：" + primary.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    void showRecentGamesScreen() {
        launcherScreenRoot = null;
        launcherDifficultyView = null;
        launcherContrastRefreshPosted = false;
        stopAllEngineWork();
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        gameScreenVisible = false;
        recentScreenVisible = true;
        correctionListVisible = false;
        correctionBoardVisible = false;
        correctionReturnToGame = false;
        correctionPermissionPending = false;
        gamePageScroll = null;
        appRoot.removeAllViews();
        boolean ready = ensureNodeStorageReady(false);
        List<RecentGameStore.Record> records = ready
                ? recentGameStore().list() : Collections.<RecentGameStore.Record>emptyList();
        RecentGamesScreen.show(this, appRoot, ready, records, new RecentGamesScreen.Listener() {
            @Override public void onBack() { showLauncherScreen(); }
            @Override public void onGrantStorage() { requestNodeStorageAccess(); }
            @Override public void onOpen(RecentGameStore.Record record) { handleRecentGameClick(record); }
            @Override public void onDelete(RecentGameStore.Record record) {
                if (!recentGameStore().delete(record))
                    Toast.makeText(MainActivity.this, "删除对局文件失败", Toast.LENGTH_LONG).show();
                showRecentGamesScreen();
            }
            @Override public void onClearAll() {
                RecentGameStore store = recentGameStore();
                int before = store.list().size();
                int deleted = store.clear();
                if (deleted < before) Toast.makeText(MainActivity.this, "部分对局文件删除失败", Toast.LENGTH_LONG).show();
                showRecentGamesScreen();
            }
        });
    }

    private RecentGameStore recentGameStore() {
        return new RecentGameStore(recentDirectory());
    }

    private void handleRecentGameClick(RecentGameStore.Record record) {
        if (record == null || record.file == null) return;
        SavedSession analysisSaved = readSavedSession(true);
        if (analysisSaved == null) {
            loadRecentGameFile(record.file);
            return;
        }
        new AlertDialog.Builder(this)
                .setMessage("分析模式已有棋谱，是否覆盖？")
                .setPositiveButton("覆盖", (d, w) -> loadRecentGameFile(record.file))
                .setNegativeButton("取消", null)
                .show();
    }

    private void loadRecentGameFile(File file) {
        try {
            byte[] bytes;
            try (InputStream input = new java.io.FileInputStream(file);
                 ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[8192];
                int count;
                while ((count = input.read(buffer)) > 0) output.write(buffer, 0, count);
                bytes = output.toByteArray();
            }
            PgnManualUtils.ParsedManual manual = XqfManualUtils.parse(bytes, START_FEN);
            if (manual.moves.isEmpty() && !manual.hasExplicitFen) {
                throw new IllegalArgumentException("未解析到有效棋谱");
            }
            startSelfAnalysisSession();
            loadParsedManual(manual);
            Toast.makeText(this, "已在分析模式打开最近对局", Toast.LENGTH_SHORT).show();
            appendLog("已从最近对局打开：" + file.getName() + "。\n");
        } catch (Exception e) {
            Toast.makeText(this, "打开最近对局失败：" + e.getMessage(), Toast.LENGTH_LONG).show();
            appendLog("打开最近对局失败：" + e.getMessage() + "。\n");
        }
    }

    private void saveCompletedDuelToRecent(GameEndType type) {
        if (!ensureNodeStorageReady(false)) {
            Toast.makeText(this, "最近对局未保存：请授予文件访问权限", Toast.LENGTH_LONG).show();
            appendLog("最近对局未保存：缺少 /storage/emulated/0/nodexq 的访问权限。\n");
            return;
        }
        try {
            Date completedAt = new Date();
            String timestamp = new SimpleDateFormat("yyMMddHHmmss", Locale.CHINA).format(completedAt);
            String evaluationName = String.valueOf(selectedDifficultyIndex + 1);
            String redName = enginePlaysRed
                    ? (evaluationMode ? evaluationName : currentDifficultyDisplayName()) : "玩家";
            String blackName = enginePlaysRed ? "玩家"
                    : (evaluationMode ? evaluationName : currentDifficultyDisplayName());
            String result = type == GameEndType.RED_WIN ? "先胜"
                    : (type == GameEndType.BLACK_WIN ? "先负" : "先和");
            String baseName = timestamp + "__" + safeRecentFilePart(redName) + "__"
                    + result + "__" + safeRecentFilePart(blackName);
            File target = new File(recentDirectory(), baseName + ".xqf");
            int duplicate = 2;
            while (target.exists()) {
                target = new File(recentDirectory(), baseName + "_" + duplicate++ + ".xqf");
            }
            try (FileOutputStream output = new FileOutputStream(target)) {
                output.write(buildStoredManualBytes(STORE_FORMAT_XQF));
                output.flush();
            }
            appendLog("最近对局已保存：" + target.getAbsolutePath() + "。\n");
        } catch (Exception e) {
            Toast.makeText(this, "最近对局保存失败：" + e.getMessage(), Toast.LENGTH_LONG).show();
            appendLog("最近对局保存失败：" + e.getMessage() + "。\n");
        }
    }

    private String safeRecentFilePart(String value) {
        String cleaned = value == null ? "未知" : value.trim();
        cleaned = cleaned.replaceAll("[\\\\/:*?\"<>|]", "_");
        return cleaned.length() == 0 ? "未知" : cleaned;
    }

    void showBoardScaleDialog() {
        final int originalPercent = boardScalePercent;
        final int[] selected = new int[]{boardScalePercent};

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(14), dp(8), dp(14), dp(4));
        panel.setBackgroundColor(Color.WHITE);

        SeekBar slider = new SeekBar(this);
        slider.setMax(50);
        slider.setProgress(boardScalePercent - 50);
        panel.addView(slider, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(48)));

        final boolean[] previewEntered = new boolean[]{gameScreenVisible};
        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            private void enterActualBoardPreview() {
                if (!previewEntered[0] && !gameScreenVisible) {
                    // 从初始页拖动时直接进入真实的分析模式页面；不再创建第二套预览界面。
                    handleLauncherAnalysisEntry();
                }
                previewEntered[0] = true;
            }

            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                selected[0] = 50 + progress;
                if (!fromUser) return;
                enterActualBoardPreview();
                boardScalePercent = selected[0];
                if (boardView != null) {
                    boardView.setBoardScalePercent(boardScalePercent);
                    boardView.post(MainActivity.this::adjustGameContentHeightForViewport);
                }
            }

            @Override public void onStartTrackingTouch(SeekBar seekBar) {
                enterActualBoardPreview();
            }

            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(panel)
                .setPositiveButton("保存", (d, which) -> {
                    boardScalePercent = clamp(selected[0], 50, 100);
                    saveLauncherPreferences();
                    if (boardView != null) {
                        boardView.setBoardScalePercent(boardScalePercent);
                        boardView.post(MainActivity.this::adjustGameContentHeightForViewport);
                    }
                })
                .setNegativeButton("取消", (d, which) -> {
                    boardScalePercent = originalPercent;
                    if (boardView != null) {
                        boardView.setBoardScalePercent(boardScalePercent);
                        boardView.post(MainActivity.this::adjustGameContentHeightForViewport);
                    }
                })
                .setCancelable(false)
                .create();
        dialog.setOnShowListener(ignored -> {
            Window window = dialog.getWindow();
            if (window != null) {
                window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT);
            }
        });
        dialog.show();
    }

    String sanitizeSkinName(String name) {
        return skinRuntimeController.sanitizeSkinName(name);
    }

    String skinPieceSizeKey(String skinName) {
        return skinRuntimeController.skinPieceSizeKey(skinName);
    }

    float[] loadSkinGrid(String skinName) {
        return skinRuntimeController.loadSkinGrid(skinName);
    }

    void saveSkinGrid(SharedPreferences.Editor editor, String skinName, float[] corners) {
        skinRuntimeController.saveSkinGrid(editor, skinName, corners);
    }

    List<String> listAvailableSkinNames() {
        return skinRuntimeController.listAvailableSkinNames();
    }

    boolean applyCurrentSkinToBoard(ChessBoardView target, boolean showMessage) {
        return skinRuntimeController.applyCurrentSkinToBoard(target, showMessage);
    }

    void showSkinSettingsDialog() {
        SkinSettingsController.show(this);
    }

    interface SkinGridCalibrationListener {
        void onFinished(float[] corners, int pieceSizePercent);
    }

    void showDynamicCalibrationPage(String skinName, int pieceSize, float[] initialGrid,
                                            SkinGridCalibrationListener listener) {
        SkinCalibrationController.show(this, skinName, pieceSize, initialGrid, listener);
    }

    void putOptionalColor(SharedPreferences.Editor editor, String key, int value) {
        if (editor == null || key == null) return;
        if (value == EngineAnalysisPanel.COLOR_AUTO) editor.remove(key);
        else editor.putInt(key, value);
    }

    void refreshEngineAnalysisColorsAfterSkinSave() {
        if (analysisPvEntries.isEmpty()) {
            refreshEngineContentText();
            return;
        }
        boolean redAtBottom = boardView == null || !boardView.isReversed();
        engineContent = buildV68EngineAnalysisContent(redAtBottom);
        if (analysisDisplayFrozen && frozenAnalysisExpectedMultiPv <= 1) {
            frozenEngineContent = new SpannableStringBuilder(engineContent);
            frozenCompactEngineContent = new SpannableStringBuilder(buildCompactSinglePvContent());
        }
        refreshEngineContentText();
    }


    Float parseCalibrationStepDp(EditText input) {
        String raw = input == null ? "" : input.getText().toString().trim();
        try {
            float value = Float.parseFloat(raw);
            if (Float.isNaN(value) || Float.isInfinite(value) || value <= 0f || value > 100f) {
                throw new NumberFormatException();
            }
            return value;
        } catch (Exception e) {
            Toast.makeText(this, "步长请输入大于 0 且不超过 100 的有效数字（如 2）", Toast.LENGTH_SHORT).show();
            return null;
        }
    }

    interface HighlightColorListener {
        void onSelected(int color);
    }

    void showColorPicker(String title, int initialColor, HighlightColorListener listener) {
        ColorPickerController.show(this, title, initialColor, listener);
    }

    void styleHighlightColorButton(Button button, int color) {
        if (button == null) return;
        button.setText(String.format(Locale.CHINA, "#%02X%02X%02X  点击选择",
                Color.red(color), Color.green(color), Color.blue(color)));
        button.setTextColor(UiTheme.textOnHighlight(this, color));
        setRoundedBackground(button, color, 7, Color.TRANSPARENT);
    }

    void styleEngineColorButton(Button button, int configuredColor, int automaticColor) {
        if (button == null) return;
        int color = configuredColor == EngineAnalysisPanel.COLOR_AUTO
                ? automaticColor : configuredColor;
        if (configuredColor == EngineAnalysisPanel.COLOR_AUTO) {
            button.setText(String.format(Locale.CHINA, "默认自适应  #%02X%02X%02X",
                    Color.red(color), Color.green(color), Color.blue(color)));
        } else {
            button.setText(String.format(Locale.CHINA, "#%02X%02X%02X  点击选择",
                    Color.red(color), Color.green(color), Color.blue(color)));
        }
        button.setTextColor(UiTheme.textOnHighlight(this, color));
        setRoundedBackground(button, color, 7, Color.TRANSPARENT);
    }

    int highlightColor() {
        return UiTheme.highlight(this);
    }

    int highlightTextColor() {
        return UiTheme.textOnHighlight(this, highlightColor());
    }

    private void refreshHighlightTheme() {
        refreshGameTabs();
        styleAnalysisButton();
        updateGameContent();
        if (analysisButton != null) analysisButton.invalidate();
        skinRuntimeController.invalidateViewTree(appRoot);
    }

    void startNewGame() {
        // 同一对弈页面点“新建”时复用现有 View，避免整页销毁/重建与皮肤重新加载。
        boolean reuseGameScreen = gameScreenVisible && boardView != null && !selfAnalysisMode;
        clearSavedSession(false);
        selfAnalysisMode = false;
        saveLauncherPreferences();
        resetGameState();
        configureGameEngine();
        // 新建对局意味着全新局面，不再沿用停止分析时冻结的旧引擎快照。
        clearFrozenAnalysisDisplay();
        if (reuseGameScreen) resetVisibleGameScreenForNewSession();
        else showGameScreen();
        appendLog("开始对弈：" + currentDifficultyDisplayName() + "，电脑执"
                + (enginePlaysRed ? "红" : "黑") + "。\n");
        handler.postDelayed(this::requestInitialSituationScore, 80L);
        handler.postDelayed(this::maybeAutoMove, 260L);
    }

    void startSelfAnalysisSession() {
        // 分析模式内“新建”同样只重置棋盘数据，不重复构造整个页面。
        boolean reuseGameScreen = gameScreenVisible && boardView != null && selfAnalysisMode;
        clearSavedSession(true);
        evaluationMode = false;
        evaluationSession = false;
        customDifficultySession = false;
        selfAnalysisMode = true;
        saveLauncherPreferences();
        resetGameState();
        // 从初始界面重新进入分析模式，不再沿用停止分析时冻结的旧引擎快照。
        clearFrozenAnalysisDisplay();
        if (reuseGameScreen) resetVisibleGameScreenForNewSession();
        else showGameScreen();
        // 自主分析没有电脑执子方；每次新建分析棋局默认让红方在下，
        // 但不改写 enginePlaysRed，避免影响其它模式的先后手设置。
        if (boardView != null) boardView.setReversed(false);
        warmComputerSideEngine();
        appendLog("进入自主分析模式：双方均由玩家操作，不调用对弈引擎。\n");
    }

    /**
     * V19.1：同模式新建局面时保留当前页面/棋盘 Bitmap，只重置棋盘与内容区。
     * 这样不会因为点击“新建”再次创建 ChessBoardView、解码皮肤或重建整棵 View 树。
     */
    private void resetVisibleGameScreenForNewSession() {
        if (boardView == null) {
            showGameScreen();
            return;
        }
        // 若用户从编辑局面直接点“新建”，复用 View 时也要恢复普通棋盘页状态。
        if (boardView.isEditMode()) {
            boardView.setEditPaintPiece('\0');
            boardView.setEditMode(false);
            boardView.setBoardScalePercent(boardScalePercent);
            if (editModeToolbar != null) editModeToolbar.setVisibility(View.VISIBLE);
            if (editModeTopRow != null) editModeTopRow.setVisibility(View.VISIBLE);
            if (editModeBottomLabel != null) editModeBottomLabel.setVisibility(View.VISIBLE);
            if (editModeTabs != null) editModeTabs.setVisibility(View.VISIBLE);
        }
        boardView.newGame();
        boardView.setReversed(enginePlaysRed);
        boardView.setShowCoordinate(false);
        boardView.setShowArrow(showEngineArrows);
        boardView.setAnalysisArrows(Collections.<ChessBoardView.AnalysisArrow>emptyList());
        if (gamePageScroll != null) gamePageScroll.scrollTo(0, 0);
        updatePlayerLabels();
        refreshGameTabs();
        styleAnalysisButton();
        refreshBoardInputState();
        updateGameContent();
        if (gamePageScroll != null) gamePageScroll.post(this::adjustGameContentHeightForViewport);
    }

    private void resumeSavedSession(SavedSession saved) {
        if (saved == null) return;
        selfAnalysisMode = saved.analysisMode;
        selectedDifficultyIndex = clamp(saved.difficultyIndex, 0, DIFFICULTIES.length - 1);
        enginePlaysRed = saved.enginePlaysRed;
        saveLauncherPreferences();
        resetGameState();
        if (!selfAnalysisMode) configureGameEngine();
        // 从初始界面恢复留存对局，不再沿用停止分析时冻结的旧引擎快照。
        clearFrozenAnalysisDisplay();
        showGameScreen();
        try {
            restoreSavedSession(saved);
            appendLog((selfAnalysisMode ? "分析棋谱" : "对弈棋谱")
                    + "已恢复，共 " + engineMoves.size() + " 手。\n");
            if (!selfAnalysisMode) {
                // requestInitialSituationScore 内部会自行判断评分与首着推荐是否已齐全。
                handler.postDelayed(this::requestInitialSituationScore, 80L);
                handler.postDelayed(this::maybeAutoMove, 260L);
            }
        } catch (Exception e) {
            appendLog("恢复留存棋谱失败：" + e.getMessage() + "。\n");
            clearSavedSession(saved.analysisMode);
            if (selfAnalysisMode) startSelfAnalysisSession();
            else startNewGame();
        }
    }

    private void resumeSavedEvaluationSession(SavedSession saved) {
        if (saved == null) return;
        evaluationMode = true;
        evaluationSession = true;
        customDifficultySession = false;
        selfAnalysisMode = false;
        selectedDifficultyIndex = clamp(saved.difficultyIndex, 0, DIFFICULTIES.length - 1);
        evaluationDifficultyIndex = selectedDifficultyIndex;
        enginePlaysRed = saved.enginePlaysRed;
        evaluationEnginePlaysRed = enginePlaysRed;
        saveLauncherPreferences();
        resetGameState();
        configureGameEngine();
        clearFrozenAnalysisDisplay();
        showGameScreen();
        try {
            restoreSavedSession(saved);
            appendLog("评测对局已恢复，共 " + engineMoves.size() + " 手。\n");
            handler.postDelayed(this::requestInitialSituationScore, 80L);
            handler.postDelayed(this::maybeAutoMove, 260L);
        } catch (Exception e) {
            appendLog("恢复评测对局失败：" + e.getMessage() + "。\n");
            clearEvaluationSession();
            startEvaluationSession();
        }
    }

    private void resetGameState() {
        stopAllEngineWork();
        baseFen = START_FEN;
        engineMoves.clear();
        readableMoves.clear();
        initialComment = "";
        manualMetadata.clear();
        moveComments.clear();
        redPerspectiveScores.clear();
        scoreMatePlies.clear();
        scoreKnown.clear();
        rescoreRecommendedMoves.clear();
        rescoreScoreKnown.clear();
        initialRescoreScoreKnown = false;
        pendingSituationRecommendations.clear();
        initialScoreRed = 0;
        initialMatePly = 0;
        initialScoreKnown = false;
        reportBackfillPass = 0;
        manualVariations.clear();
        activeBranchLabels.clear();
        analysisBannedRootMoves.clear();
        analysisEntries.clear();
        analysisPvEntries.clear();
        currentPly = 0;
        pendingMoveScoreRed = null;
        pendingMoveMatePly = 0;
        latestGameInfo = null;
        latestAnalysisInfo = null;
        latestAnalysisBestMove = "";
        analysisPositionKey = "";
        analysisExpectedMultiPv = 1;
        analysisConfiguredMultiPv = 1;
        lastMultiPvModules = Collections.<EngineAnalysisPanel.DepthModule>emptyList();
        cancelImmediateMoveRequest();
        latestAnalysisDepth = 0;
        engineContent = "尚未开始分析。点击上方“分析”后，将在此显示实时内容。";
        engineThinking = false;
        autoMoveInProgress = false;
        computerRedActive = false;
        computerBlackActive = false;
        computerRedBlackActive = false;
        computerSideThinking = false;
        computerMoveGeneration++;
        computerSideController.resetRestrictions();
        loggedQingyunDrawRule = "";
        styleComputerSideButtons();
        analysisMode = false;
        gameOver = false;
        completedDuelGame = false;
        postGameSandboxActive = false;
        terminalDialogShown = false;
        competitiveResultEligible = !selfAnalysisMode;
        resultRecordedForCurrentGame = false;
        gameResultTag = "*";
        ratingEligible = evaluationMode && !selfAnalysisMode;
        ratingDisqualified = false;
        ratingCounted = false;
        drawOfferInProgress = false;
        rescoreController.reset();
        sixtyMoveDrawArmedPly = -1;
        selectedEditPiece = 0;
        manualScrollY = 0;
        branchScrollY = 0;
        manualScrollToCurrentPly = false;
        engineScrollY = 0;
        operationGeneration++;
        if (gameEngine != null) gameEngine.notifyNewGame();
        if (manualEngine != null) manualEngine.notifyNewGame();
        if (drawEngine != null) drawEngine.notifyNewGame();
        if (rescoreEngine != null) rescoreEngine.notifyNewGame();
        if (situationEngine != null) situationEngine.notifyNewGame();
    }

    private void configureGameEngine() {
        DifficultyProfile p = currentDifficulty();
        gameEngine.setVirtualEngineSlot(p.engineSlot);
        gameEngine.clearSessionOptions();
        gameEngine.setSessionOption("Threads", String.valueOf(p.threads));
        gameEngine.setSessionOption("MultiPV", "1");
        gameEngine.setSessionOption("UCI_ShowWDL", "true");
        // V20.2：新版 libduf.so 通过 UCI 暴露棋规选项；人机难度默认采用
        // github_AXF，避免沿用 PikafishEngine 通用默认的 SkyRule。
        if ("duf".equalsIgnoreCase(p.engineSlot)) {
            gameEngine.setSessionOption("Repetition Rule", "github_AXF");
        }
        updateQingyunDrawRule();
        // V16.1：duf 的当前 libduf.so 已内嵌完整 NNUE，不设置 EvalFile。
        // 131/HCE 及其他引擎仍由 PikafishEngine 按各自规则处理网络文件。
    }

    void updateQingyunDrawRule() {
        if (gameEngine == null) return;
        String rule = "none";
        if (selectedDifficultyIndex == DIFFICULTIES.length - 1 && currentPly < 50) {
            rule = enginePlaysRed ? "drawrepasblackwin" : "drawrepasredwin";
        }
        gameEngine.setSessionOption("DrawRule", rule);
        if (selectedDifficultyIndex == DIFFICULTIES.length - 1
                && !rule.equals(loggedQingyunDrawRule)) {
            loggedQingyunDrawRule = rule;
            appendLog("[青云 DrawRule] " + rule + "（第 " + (currentPly / 2 + 1)
                    + " 回合，电脑执" + (enginePlaysRed ? "红" : "黑") + "）。\n");
        }
    }

    void showGameScreen() {
        gameScreenController.showGameScreen();
    }

    private void adjustGameContentHeightForViewport() {
        gameScreenController.adjustContentHeightForViewport();
    }
    void showGameActionMenu() {
        gameActionMenuController.show();
    }

    /** 对弈模式临时推演：独立棋盘、独立走法列表，绝不写入正式对局字段。 */
    void enterPushMode() {
        auxiliaryBoardController.enterPushMode();
    }

    void addCurrentPositionToCorrectionBook() {
        correctionBookController.addCurrentPositionToCorrectionBook();
    }

    void openCorrectionBookFromSituation() {
        correctionBookController.openFromSituation();
    }

    void returnFromCorrectionListScreen() {
        correctionBookController.returnFromList();
    }

    void showCorrectionListScreen() {
        correctionBookController.showList();
    }

    void showCorrectionBoard(String fen) {
        correctionBookController.showBoard(fen);
    }

    void toggleTemporaryAnalysis(final ChessBoardView tempBoard,
                                 final LinearLayout analysisHost, final Button button) {
        auxiliaryBoardController.toggleTemporaryAnalysis(tempBoard, analysisHost, button);
    }

    void showTemporaryAnalysisPlaceholder(LinearLayout analysisHost) {
        auxiliaryBoardController.showTemporaryAnalysisPlaceholder(analysisHost);
    }

    void startTemporaryAnalysisNow(final ChessBoardView tempBoard,
                                   final LinearLayout analysisHost, final Button button) {
        auxiliaryBoardController.startTemporaryAnalysisNow(tempBoard, analysisHost, button);
    }

    void playMoveSoundForBoard(ChessBoardView target, boolean redToMoveNow) {
        if (!soundEnabled || target == null || moveSoundPool == null) return;
        boolean checking = XiangqiRules.isInCheck(target.copyBoard(), redToMoveNow);
        int soundId = checking && checkSoundLoaded ? checkSoundId
                : (moveSoundLoaded ? moveSoundId : 0);
        if (soundId != 0) moveSoundPool.play(soundId, 1f, 1f, 1, 0, 1f);
    }

    TextView manualNavButton(String text, boolean enabled, View.OnClickListener listener) {
        return uiComponentController.manualNavButton(text, enabled, listener);
    }

    TextView pushNavButton(String text, boolean enabled, View.OnClickListener listener) {
        return uiComponentController.pushNavButton(text, enabled, listener);
    }

    LinearLayout.LayoutParams pushNavLp() {
        return uiComponentController.pushNavLp();
    }

    void exitPushMode(boolean reversed, boolean resumeAnalysis) {
        auxiliaryBoardController.exitPushMode(reversed, resumeAnalysis);
    }

    LinearLayout toolbarRow() {
        return uiComponentController.toolbarRow();
    }

    void handleNewGameAction() {
        // 棋盘页“新建”始终直接重置当前模式，不再区分棋谱是否为空，也不弹确认框。
        // 对弈模式保留当前难度和电脑执子；分析模式继续保持自主分析模式。
        if (selfAnalysisMode) {
            startSelfAnalysisSession();
            appendLog("分析模式下已直接新建棋局。\n");
        } else {
            startNewGame();
            appendLog("对弈模式下已直接新建对局，难度与先后手保持不变。\n");
        }
    }

    void updateModeToggleButton() {
        // V17.8：模式切换入口已移入菜单，棋盘上下不再放置独立按钮。
    }

    /**
     * 自主分析与正式对弈之间切换时重建工具栏和棋盘页。
     *
     * <p>调用方已经通过 {@link #stopSearchForPositionChange()} 结束旧模式的搜索；
     * 这里的重建只服务于模式本身发生变化。二合一开关是纯显示调整，走
     * {@link GameScreenController#applyCombinedModeLayout()}，不会进入本方法。</p>
     */
    private void rebuildGameScreenForModeSwitch() {
        if (boardView == null) return;
        boolean reversed = boardView.isReversed();
        int target = currentPly;
        boolean resumeComputerTurn = !selfAnalysisMode && !completedDuelGame;
        boolean resumeComputerSide = selfAnalysisMode && computerRedBlackActive;
        showGameScreen();
        try {
            rebuildBoardToPly(target);
            currentPly = target;
            boardView.setReversed(reversed);
            boardView.setShowArrow(showEngineArrows);
            updatePlayerLabels();
            refreshBoardInputState();
            updateGameContent();
            if (analysisMode) continueManualAnalysisForCurrentPosition(30L);
            if (resumeComputerSide) handler.postDelayed(this::startComputerSideMove, 30L);
            else if (resumeComputerTurn) handler.postDelayed(this::maybeAutoMove, 180L);
        } catch (Exception e) {
            appendLog("界面重建失败：" + e.getMessage() + "\n");
        }
    }

    void toggleSelfAnalysisMode() {
        if (boardView == null || boardView.isEditMode() || isRescoring || drawOfferInProgress) return;
        if (selfAnalysisMode) {
            requestEnterDuelModeFromAnalysis();
            return;
        }
        markCurrentLineAsAnalysisOnly("已切换到自主分析模式");
        ratingEligible = false;
        ratingDisqualified = true;
        persistCurrentSession(true);
        stopSearchForPositionChange();
        selfAnalysisMode = true;
        autoMoveInProgress = false;
        engineThinking = false;
        gameEngine.stopAnalysis();
        rebuildGameScreenForModeSwitch();
        warmComputerSideEngine();
        appendLog("已进入自主分析模式：停止对弈引擎，双方均由玩家操作。\n");
        updatePlayerLabels();
        updateGameContent();
        refreshBoardInputState();
        persistCurrentSession();
        if (analysisMode) continueManualAnalysisForCurrentPosition(20L);
    }

    private void requestEnterDuelModeFromAnalysis() {
        SavedSession savedDuel = readSavedSession(false);
        boolean hasUnfinishedDuel = savedDuel != null && !savedDuel.completedDuelGame;
        if (!hasUnfinishedDuel) {
            confirmBranchesAndEnterDuel(false);
            return;
        }
        new AlertDialog.Builder(this)
                .setMessage("对弈模式已有未完成对局，是否覆盖？")
                .setPositiveButton("确认", (d, w) -> confirmBranchesAndEnterDuel(true))
                .setNegativeButton("取消", null)
                .show();
    }

    private void confirmBranchesAndEnterDuel(boolean overwriteSavedDuel) {
        if (hasAnyManualBranch()) {
            new AlertDialog.Builder(this)
                    .setMessage("检测到棋谱存在不同分支，进入对弈模式仅保留主分支，是否进入？")
                    .setPositiveButton("确定", (d, w) -> {
                        if (overwriteSavedDuel) clearSavedSession(false);
                        manualVariations.clear();
                        activeBranchLabels.clear();
                        enterDuelModeFromAnalysis();
                    })
                    .setNegativeButton("取消", null)
                    .show();
            return;
        }
        if (overwriteSavedDuel) clearSavedSession(false);
        enterDuelModeFromAnalysis();
    }

    private boolean hasAnyManualBranch() {
        for (List<ManualVariation> variations : manualVariations.values()) {
            if (variations != null && !variations.isEmpty()) return true;
        }
        return false;
    }

    private void enterDuelModeFromAnalysis() {
        // 从首页进入分析模式后再切换对弈，整盘仍属于分析棋谱，不计历史战绩。
        competitiveResultEligible = false;
        ratingDisqualified = true;
        ratingEligible = false;
        resultRecordedForCurrentGame = false;
        persistCurrentSession(true);
        stopSearchForPositionChange();
        selfAnalysisMode = false;
        computerRedActive = false;
        computerBlackActive = false;
        computerRedBlackActive = false;
        computerSideThinking = false;
        computerMoveGeneration++;
        autoMoveInProgress = false;
        engineThinking = false;
        styleComputerSideButtons();
        configureGameEngine();
        gameEngine.notifyNewGame();
        rebuildGameScreenForModeSwitch();
        appendLog("已进入对弈模式：恢复难度“" + currentDifficultyDisplayName() + "”。\n");
        updateModeToggleButton();
        updatePlayerLabels();
        updateGameContent();
        refreshBoardInputState();
        persistCurrentSession();
        if (analysisMode) continueManualAnalysisForCurrentPosition(20L);
        if (!completedDuelGame) handler.postDelayed(this::maybeAutoMove, 180L);
    }

    void reverseBoard() {
        if (boardView == null) return;
        boardView.setReversed(!boardView.isReversed());
        updatePlayerLabels();
        if (analysisMode && !analysisPvEntries.isEmpty()) {
            engineContent = buildV68EngineAnalysisContent(boardView.isRedToMove());
            refreshEngineContentText();
        }
        appendLog("棋盘已翻转。\n");
    }

    void updatePlayerLabels() {
        if (boardView == null || topPlayerLabel == null || bottomPlayerLabel == null) return;
        boolean topIsRed = boardView.isReversed();
        bindPlayerLabel(topPlayerLabel, topIsRed);
        bindPlayerLabel(bottomPlayerLabel, !topIsRed);
        // V17.8: 分析模式下上下行已无文字（"红方/黑方 · 自主分析"），整行隐藏不占位；
        // 对弈模式恢复显示。编辑模式的行可见性由 toggleEditMode / finishEditMode 独立控制。
        boolean hidePlayerRows = selfAnalysisMode || boardView.isEditMode();
        if (editModeTopRow != null) editModeTopRow.setVisibility(hidePlayerRows ? View.GONE : View.VISIBLE);
        if (editModeBottomLabel != null) editModeBottomLabel.setVisibility(hidePlayerRows ? View.GONE : View.VISIBLE);
        // 行可见性变化后重算棋谱区高度，避免模式切换后底部留白。
        if (gamePageScroll != null) gamePageScroll.post(this::adjustGameContentHeightForViewport);
        updateModeToggleButton();
        refreshBoardInputState();
    }

    private void bindPlayerLabel(TextView label, boolean redSide) {
        boolean computer = redSide == enginePlaysRed;
        String side = redSide ? "红方" : "黑方";
        String text;
        if (selfAnalysisMode) {
            text = "";
        } else if (evaluationMode) {
            text = computer ? side + "·电脑" : side + "·玩家";
            if (computer && engineThinking) text += "·思考中";
        } else {
            text = computer ? side + " · 电脑 · " + currentDifficultyDisplayName() : side + " · 玩家";
            if (computer && engineThinking) text += " · 思考中";
        }
        if (!selfAnalysisMode && !evaluationMode && (gameOver || completedDuelGame)) text += " · 对局结束";
        label.setText(text);
        boolean lightBackground = globalBackgroundTextColor() != Color.WHITE;
        label.setTextColor(redSide
                ? (lightBackground ? Color.rgb(150, 39, 35) : Color.rgb(255, 158, 148))
                : globalBackgroundTextColor());
        setRoundedBackground(label, globalSurfaceFillColor(), 0, Color.TRANSPARENT);
    }

    void refreshBoardInputState() {
        if (boardView == null) return;
        boolean humanTurn = selfAnalysisMode || boardView.isRedToMove() != enginePlaysRed;
        boolean postGameSandbox = completedDuelGame && postGameSandboxActive
                && !selfAnalysisMode && !evaluationMode;
        boolean enabled = boardView.isEditMode()
                || ((!gameOver || postGameSandbox)
                && !isRescoring && !drawOfferInProgress
                && humanTurn && !engineThinking && !autoMoveInProgress
                && !computerSideThinking);
        boardView.setInputEnabled(enabled);
    }

    void selectGameTab(int index) {
        if (boardView != null && boardView.isEditMode()) return;
        if (combinedManualEngineMode && index == 1) index = 0;
        selectedGameTab = clamp(index, 0, 2);
        saveLauncherPreferences();
        refreshGameTabs();
        updateGameContent();
    }

    void refreshGameTabs() {
        styleTab(manualTab, selectedGameTab == 0);
        if (engineTab != null) styleTab(engineTab, selectedGameTab == 1);
        styleTab(situationTab, selectedGameTab == 2);
    }

    void updateGameContent() {
        gameContentController.updateGameContent();
    }

    void keepNestedScrollGestures(View child) {
        gameContentController.keepNestedScrollGestures(child);
    }

    View buildManualNavigationBar() {
        return manualNavigationController.buildNavigationBar();
    }

    View buildAlignedNavigationRow() {
        return manualNavigationController.buildAlignedNavigationRow();
    }

    void navigateWithManualFollow(int target) {
        manualNavigationController.navigateWithFollow(target);
    }

    void scrollManualRowIntoView(View row, View listRoot) {
        manualNavigationController.scrollRowIntoView(row, listRoot);
    }

    String branchLabelForOrdinal(int index) {
        return ManualBranchManager.labelForOrdinal(index);
    }

    int branchLabelOrdinal(String label) {
        return ManualBranchManager.labelOrdinal(label);
    }

    void ensureBranchLabels(int node) {
        manualBranchManager.ensureLabels(node);
    }

    void renumberBranchLabels(int node) {
        manualBranchManager.renumberLabels(node);
    }

    String activeBranchLabel(int node) {
        return manualBranchManager.activeLabel(node);
    }

    String nextAvailableBranchLabel(int node) {
        return manualBranchManager.nextAvailableLabel(node);
    }

    View buildManualCommentEditor() {
        return manualNavigationController.buildCommentEditor();
    }

    void showManualCommentDialog(final int targetIndex) {
        manualNavigationController.showCommentDialog(targetIndex);
    }

    void ensureCommentSize(int size) {
        manualNavigationController.ensureCommentSize(size);
    }

    View buildBranchList() {
        return manualNavigationController.buildBranchList();
    }

    int measureManualPaneWidth() {
        return gameContentController.measureManualPaneWidth();
    }

    private ManualBranchPanel.Comment currentManualComment() {
        return manualNavigationController.currentManualComment();
    }

    private ManualBranchPanel.BranchList currentBranchList() {
        return manualNavigationController.currentBranchList();
    }

    private void showBranchLongPressDialog(int node, int variationIndex,
                                           boolean active, String label) {
        manualNavigationController.showBranchLongPressDialog(node, variationIndex, active, label);
    }

    private void deleteBranch(int node, int variationIndex,
                              boolean deleteActive, String label) {
        manualNavigationController.deleteBranch(node, variationIndex, deleteActive, label);
    }

    void captureDescendantBranches(ManualVariation target, int node, int endExclusive) {
        manualBranchManager.captureDescendants(target, node, endExclusive);
    }

    void restoreDescendantBranches(ManualVariation source, int node) {
        manualBranchManager.restoreDescendants(source, node);
    }

    void removeDescendantBranches(int node, int endExclusive) {
        manualBranchManager.removeDescendants(node, endExclusive);
    }


    void refreshSituationChart() {
        if (!situationPanel.hasChart()) return;
        int endgameRound = findFirstEndgameRound();
        GameReportCalculator.Report report = buildGameReport(endgameRound);
        refreshReportButtonState(report);
        refreshOpenReportDialog(report);
        situationPanel.refresh(situationPanelData(endgameRound, report));
    }

    void refreshSituationChartColors() {
        situationPanel.updateAdvantageColors(
                situationRedAdvantageColor, situationBlackAdvantageColor);
    }

    void updateRescoreProgress(String text) {
        situationPanel.updateRescoreProgress(text);
    }

    private void refreshReportButtonState(GameReportCalculator.Report report) {
        situationPanel.setReportAvailable(report != null && report.complete);
    }

    SituationPanelController.Data situationPanelData(int endgameRound,
                                                             GameReportCalculator.Report report) {
        return new SituationPanelController.Data(isRescoring,
                !engineThinking && !autoMoveInProgress && !drawOfferInProgress,
                report != null && report.complete,
                situationRedAdvantageColor, situationBlackAdvantageColor,
                redPerspectiveScores, scoreMatePlies, initialScoreRed,
                XiangqiRules.redToMoveFromFen(baseFen), engineMoves.size(), currentPly,
                endgameRound, report != null && report.complete
                        ? report.errorPlies : Collections.<Integer>emptyList(),
                currentRescoreProgressText());
    }

    GameReportCalculator.Report buildGameReport() {
        return buildGameReport(findFirstEndgameRound());
    }

    GameReportCalculator.Report buildGameReport(int endgameRound) {
        return buildGameReport(endgameRound, gameReportController.softMinTau());
    }

    private GameReportCalculator.Report buildGameReport(int endgameRound, double softMinTau) {
        return GameReportCalculator.calculate(initialScoreRed, initialMatePly, initialScoreKnown,
                XiangqiRules.redToMoveFromFen(baseFen),
                redPerspectiveScores, scoreMatePlies, scoreKnown,
                engineMoves.size(), endgameRound, softMinTau);
    }

    private void showGameReport() {
        gameReportController.show();
    }

    private void refreshOpenReportDialog(GameReportCalculator.Report report) {
        gameReportController.refreshIfOpen(report);
    }

    void navigateToPly(int target) {
        navigateToPly(target, true);
    }

    /** 拖动局势图时只更新棋盘，保留图表实例持续接收后续手势。 */
    private void previewSituationPly(int target) {
        navigateToPly(target, false);
    }

    private void navigateToPly(int target, boolean refreshContent) {
        if (boardView == null || boardView.isEditMode() || isRescoring) return;
        int previousPly = currentPly;
        target = clamp(target, 0, engineMoves.size());
        stopSearchForPositionChange();
        try {
            rebuildBoardToPly(target);
            currentPly = target;
            if (refreshContent && target > previousPly) {
                playMoveSoundForCurrentPosition(boardView.isRedToMove());
            }
            if (target < engineMoves.size()) {
                gameOver = false;
                terminalDialogShown = false;
                if (completedDuelGame && !selfAnalysisMode) postGameSandboxActive = true;
                if (!completedDuelGame) gameResultTag = "*";
            }
            updatePlayerLabels();
            refreshBoardInputState();
            if (refreshContent) {
                updateGameContent();
                appendLog("棋谱导航到 " + currentPly + "/" + engineMoves.size() + " 手。\n");
            }
            // 导航到末局时，若为绝杀/无子可动，补写局势图终局分数
            if (currentPly == engineMoves.size() && engineMoves.size() > 0) {
                boolean redToMoveNow = boardView == null || boardView.isRedToMove();
                boolean noLegal = XiangqiRules.generateLegalMoves(
                        boardView.copyBoard(), redToMoveNow).isEmpty();
                if (noLegal) {
                    GameEndType endType = redToMoveNow ? GameEndType.BLACK_WIN : GameEndType.RED_WIN;
                    applyTerminalSituationScore(endType);
                }
            }
            if (refreshContent && analysisMode) continueManualAnalysisForCurrentPosition(20L);
        } catch (Exception e) {
            if (refreshContent) appendLog("棋谱导航失败：" + e.getMessage() + "。\n");
        }
    }

    void rebuildBoardToPly(int target) {
        boardView.setBoardFromFen(baseFen);
        for (int i = 0; i < target; i++) {
            if (!boardView.playMoveSilently(Move.fromEngineStep(engineMoves.get(i)))) {
                throw new IllegalStateException("第 " + (i + 1) + " 手无法重放");
            }
        }
    }

    void captureFutureAsVariation(int node) {
        if (node < 0 || node >= engineMoves.size()) return;
        ensureBranchLabels(node);
        String oldActiveLabel = activeBranchLabels.get(node);
        if (oldActiveLabel == null || oldActiveLabel.length() == 0) oldActiveLabel = "A";
        activeBranchLabels.put(node, oldActiveLabel);

        ManualVariation branch = new ManualVariation();
        branch.label = oldActiveLabel;
        branch.engineSteps.addAll(engineMoves.subList(node, engineMoves.size()));
        branch.readableMoves.addAll(readableMoves.subList(node, readableMoves.size()));
        branch.comments.addAll(moveComments.subList(Math.min(node, moveComments.size()), moveComments.size()));
        branch.scores.addAll(redPerspectiveScores.subList(node, redPerspectiveScores.size()));
        branch.matePlies.addAll(scoreMatePlies.subList(node, scoreMatePlies.size()));
        branch.scoreKnown.addAll(scoreKnown.subList(node, scoreKnown.size()));
        branch.rescoreRecommendedMoves.addAll(rescoreRecommendedMoves.subList(node, rescoreRecommendedMoves.size()));
        branch.rescoreScoreKnown.addAll(rescoreScoreKnown.subList(node, rescoreScoreKnown.size()));
        captureDescendantBranches(branch, node, engineMoves.size());
        List<ManualVariation> vars = manualVariations.get(node);
        if (vars == null) {
            vars = new ArrayList<ManualVariation>();
            manualVariations.put(node, vars);
        }
        boolean exists = false;
        for (ManualVariation existing : vars) {
            if (existing != null && existing.engineSteps.equals(branch.engineSteps)) {
                exists = true;
                if (existing.label == null || existing.label.length() == 0) existing.label = branch.label;
                break;
            }
        }
        if (!exists) vars.add(branch);
        activeBranchLabels.put(node, nextAvailableBranchLabel(node));
        ensureBranchLabels(node);
        appendLog("已在第 " + node + " 手处建立新分支，原线路编号保持不变。\n");
    }

    void switchToVariation(int node, int variationIndex) {
        List<ManualVariation> vars = manualVariations.get(node);
        if (vars == null || variationIndex < 0 || variationIndex >= vars.size()) return;
        ensureBranchLabels(node);
        stopSearchForPositionChange();
        ManualVariation selected = vars.remove(variationIndex);
        ManualVariation oldActive = new ManualVariation();
        oldActive.label = activeBranchLabel(node);
        if (node < engineMoves.size()) {
            oldActive.engineSteps.addAll(engineMoves.subList(node, engineMoves.size()));
            oldActive.readableMoves.addAll(readableMoves.subList(node, readableMoves.size()));
            oldActive.comments.addAll(moveComments.subList(Math.min(node, moveComments.size()), moveComments.size()));
            oldActive.scores.addAll(redPerspectiveScores.subList(node, redPerspectiveScores.size()));
            oldActive.matePlies.addAll(scoreMatePlies.subList(node, scoreMatePlies.size()));
            oldActive.scoreKnown.addAll(scoreKnown.subList(node, scoreKnown.size()));
            oldActive.rescoreRecommendedMoves.addAll(rescoreRecommendedMoves.subList(node, rescoreRecommendedMoves.size()));
            oldActive.rescoreScoreKnown.addAll(rescoreScoreKnown.subList(node, rescoreScoreKnown.size()));
            captureDescendantBranches(oldActive, node, engineMoves.size());
        }
        truncateListsTo(node);
        engineMoves.addAll(selected.engineSteps);
        readableMoves.addAll(selected.readableMoves);
        moveComments.addAll(selected.comments);
        while (moveComments.size() < readableMoves.size()) moveComments.add("");
        redPerspectiveScores.addAll(selected.scores);
        scoreMatePlies.addAll(selected.matePlies);
        scoreKnown.addAll(selected.scoreKnown);
        rescoreRecommendedMoves.addAll(selected.rescoreRecommendedMoves);
        rescoreScoreKnown.addAll(selected.rescoreScoreKnown);
        ensureScoreSize(engineMoves.size());
        restoreDescendantBranches(selected, node);
        if (!oldActive.engineSteps.isEmpty()) vars.add(oldActive);
        activeBranchLabels.put(node, selected.label);
        if (vars.isEmpty()) manualVariations.remove(node);
        else manualVariations.put(node, vars);
        ensureBranchLabels(node);
        currentPly = Math.min(engineMoves.size(), node + 1);
        rebuildBoardToPly(currentPly);
        playMoveSoundForCurrentPosition(boardView.isRedToMove());
        gameOver = false;
        terminalDialogShown = false;
        if (!completedDuelGame) gameResultTag = "*";
        updatePlayerLabels();
        updateGameContent();
        sixtyMoveDrawArmedPly = computeNoCaptureMoveCountAtPly(currentPly) == 119
                ? currentPly + 1 : -1;
        persistCurrentSession();
        if (analysisMode) continueManualAnalysisForCurrentPosition(20L);
    }

    int findVariationIndexByFirstStep(int node, String step) {
        if (step == null || step.length() == 0) return -1;
        List<ManualVariation> vars = manualVariations.get(node);
        if (vars == null) return -1;
        for (int i = 0; i < vars.size(); i++) {
            ManualVariation variation = vars.get(i);
            if (variation == null || variation.engineSteps.isEmpty()) continue;
            if (step.equalsIgnoreCase(variation.engineSteps.get(0))) return i;
        }
        return -1;
    }

    void truncateListsTo(int size) {
        if (engineMoves.size() > size) invalidateSituationScoreRequests();
        while (engineMoves.size() > size) engineMoves.remove(engineMoves.size() - 1);
        while (readableMoves.size() > size) readableMoves.remove(readableMoves.size() - 1);
        while (moveComments.size() > size) moveComments.remove(moveComments.size() - 1);
        while (redPerspectiveScores.size() > size) redPerspectiveScores.remove(redPerspectiveScores.size() - 1);
        while (scoreMatePlies.size() > size) scoreMatePlies.remove(scoreMatePlies.size() - 1);
        while (scoreKnown.size() > size) scoreKnown.remove(scoreKnown.size() - 1);
        while (rescoreRecommendedMoves.size() > size) rescoreRecommendedMoves.remove(rescoreRecommendedMoves.size() - 1);
        while (rescoreScoreKnown.size() > size) rescoreScoreKnown.remove(rescoreScoreKnown.size() - 1);
        if (size < sixtyMoveDrawArmedPly) sixtyMoveDrawArmedPly = -1;
    }

    void toggleEditMode() {
        if (boardView == null) return;
        if (boardView.isEditMode()) {
            finishEditMode(true);
            return;
        }
        editBackupFen = boardView.getFen();
        editBackupPly = currentPly;
        editPreviousBoardScalePercent = boardView.getBoardScalePercent();
        // V10: 编辑模式全新视图 — 隐藏工具栏/标签/Tab/模式切换，棋盘全宽不留空
        if (editModeToolbar != null) editModeToolbar.setVisibility(View.GONE);
        if (editModeTopRow != null) editModeTopRow.setVisibility(View.GONE);
        if (editModeBottomLabel != null) editModeBottomLabel.setVisibility(View.GONE);
        if (editModeTabs != null) editModeTabs.setVisibility(View.GONE);
        boardView.setBoardScalePercent(100);
        stopSearchForPositionChange();
        selectedEditPiece = 0;
        boardView.setEditPaintPiece('\0');
        boardView.setEditMode(true);
        boardView.setShowCoordinate(true);
        refreshBoardInputState();
        updateGameContent();
        appendLog("进入局面编辑。\n");
    }

    private void fitBoardForEditPanel() {
        final ChessBoardView editingBoard = boardView;
        if (editingBoard == null) return;
        editingBoard.post(() -> {
            if (boardView != editingBoard || !editingBoard.isEditMode()) return;
            int currentScale = Math.max(50, editingBoard.getBoardScalePercent());
            int currentHeight = editingBoard.getMeasuredHeight();
            int usableHeight = appRoot == null ? 0 : appRoot.getHeight()
                    - appRoot.getPaddingTop() - appRoot.getPaddingBottom();
            if (currentHeight <= 0 || usableHeight <= 0) return;
            float fullBoardHeight = currentHeight * 100f / currentScale;
            int fixedUiHeight = dp(66 + 30 + 30 + 36);
            int requiredEditHeight = dp(172);
            int availableBoardHeight = Math.max(1, usableHeight - fixedUiHeight - requiredEditHeight);
            int fittedScale = (int) Math.floor(availableBoardHeight * 100f / fullBoardHeight);
            int target = clamp(Math.min(editPreviousBoardScalePercent, fittedScale), 50, 78);
            editingBoard.setBoardScalePercent(target);
        });
    }

    View buildEditPanel() {
        return manualEditorController.buildPanel();
    }

    private void refreshEditPanelState() {
        manualEditorController.refreshPanelState();
    }

    void clearEditPanelReferences() {
        manualEditorController.clearPanelReferences();
    }

    private void selectEditPiece(char piece) {
        manualEditorController.selectPiece(piece);
    }

    void finishEditMode(boolean save) {
        if (boardView == null || !boardView.isEditMode()) return;
        if (save) {
            String validationMessage = validateEditedPosition();
            if (validationMessage != null) {
                new AlertDialog.Builder(this)
                        .setMessage(validationMessage)
                        .setPositiveButton("确认", null)
                        .show();
                return;
            }
        }
        boardView.setEditPaintPiece('\0');
        boardView.setEditMode(false);
        boardView.setShowCoordinate(false);
        // 恢复编辑模式下隐藏的行
        if (editModeToolbar != null) editModeToolbar.setVisibility(View.VISIBLE);
        if (editModeTopRow != null) editModeTopRow.setVisibility(View.VISIBLE);
        if (editModeBottomLabel != null) editModeBottomLabel.setVisibility(View.VISIBLE);
        if (editModeTabs != null) editModeTabs.setVisibility(View.VISIBLE);
        boardView.setBoardScalePercent(editPreviousBoardScalePercent);
        selectedEditPiece = 0;
        if (save) {
            invalidateSituationScoreRequests();
            baseFen = normalizeFen(boardView.getFen());
            engineMoves.clear();
            readableMoves.clear();
            redPerspectiveScores.clear();
            scoreMatePlies.clear();
            scoreKnown.clear();
            rescoreRecommendedMoves.clear();
            rescoreScoreKnown.clear();
            initialRescoreScoreKnown = false;
            initialScoreRed = 0;
            initialMatePly = 0;
            initialScoreKnown = false;
            manualVariations.clear();
            currentPly = 0;
            gameOver = false;
            terminalDialogShown = false;
            gameResultTag = "*";
            gameEngine.notifyNewGame();
            manualEngine.notifyNewGame();
            rescoreEngine.notifyNewGame();
            situationEngine.notifyNewGame();
            appendLog("编辑局面已保存，并设为新的起始局面。\n");
        } else {
            boardView.setBoardFromFen(editBackupFen);
            currentPly = editBackupPly;
            appendLog("已取消局面编辑。\n");
        }
        updatePlayerLabels();
        refreshBoardInputState();
        updateGameContent();
        if (save) persistCurrentSession();
        if (analysisMode) continueManualAnalysisForCurrentPosition(20L);
        handler.postDelayed(this::maybeAutoMove, 220L);
    }

    private String validateEditedPosition() {
        char[][] board = boardView.copyBoard();
        int redKingCount = 0;
        int blackKingCount = 0;
        int redKingRow = -1;
        int redKingCol = -1;
        int blackKingRow = -1;
        int blackKingCol = -1;
        for (int r = 0; r < 10; r++) {
            for (int c = 0; c < 9; c++) {
                if (board[r][c] == 'K') {
                    redKingCount++;
                    redKingRow = r;
                    redKingCol = c;
                } else if (board[r][c] == 'k') {
                    blackKingCount++;
                    blackKingRow = r;
                    blackKingCol = c;
                }
            }
        }
        boolean redKingInPalace = redKingCount == 1
                && redKingRow >= 7 && redKingRow <= 9
                && redKingCol >= 3 && redKingCol <= 5;
        boolean blackKingInPalace = blackKingCount == 1
                && blackKingRow >= 0 && blackKingRow <= 2
                && blackKingCol >= 3 && blackKingCol <= 5;
        if (!redKingInPalace || !blackKingInPalace) return "将/帅位置不对！";

        char[] pieces = new char[]{'r','n','b','a','k','c','p','R','N','B','A','K','C','P'};
        for (char piece : pieces) {
            int max = XiangqiRules.maxPieceCount(piece);
            if (XiangqiRules.countPiece(board, piece) > max) {
                return pieceName(piece) + "数量超过上限！";
            }
        }
        return null;
    }

    void toggleAnalysis() {
        analysisSessionController.toggle();
    }

    void stopManualAnalysis(boolean clearArrow) {
        analysisSessionController.stop(clearArrow);
    }

    void scheduleManualAnalysisRestart(long delayMs) {
        analysisSessionController.scheduleRestart(delayMs);
    }

    boolean shouldPauseManualAnalysisForComputerTurn() {
        return analysisSessionController.shouldPauseForComputerTurn();
    }

    void pauseManualAnalysisForComputerTurn() {
        analysisSessionController.pauseForComputerTurn();
    }

    void continueManualAnalysisForCurrentPosition(long delayMs) {
        analysisSessionController.continueForCurrentPosition(delayMs);
    }

    void startManualAnalysis() {
        analysisSessionController.start();
    }

    void executeManualAnalysisTask(Runnable task) {
        if (task != null) manualAnalysisStarter.execute(task);
    }

    void clearPendingAnalysisUiUpdates() {
        analysisSessionController.clearPendingUiUpdates();
    }

    private void freezeAnalysisDisplay() {
        analysisSessionController.freezeDisplay();
    }

    void clearFrozenAnalysisDisplay() {
        analysisSessionController.clearFrozenDisplay();
    }

    List<String> pvForDisplay(List<String> pv) {
        return analysisDisplayController.pvForDisplay(pv);
    }

    void refreshEngineContentText() {
        analysisDisplayController.refreshEngineContentText();
    }

    /**
     * V19.0：浏览已重新打分的棋谱时，仅显示当前一手的实际着法和 PV1 推荐着法。
     * 只有“走前评分 + 走后评分 + 走前 PV1 推荐”都来自重新打分时才展示，
     * 因此局部打分、中途停止或旧版本留存不会产生半截提示。
     */
    void updateRescoreReviewArrows() {
        analysisDisplayController.updateRescoreReviewArrows();
    }

    private boolean hasCompleteRescoreReview(int index) {
        return analysisDisplayController.hasCompleteRescoreReview(index);
    }

    void updateAnalysisArrows() {
        analysisDisplayController.updateAnalysisArrows();
    }

    void appendAnalysisArrows(List<ChessBoardView.AnalysisArrow> arrows,
                                      AnalysisDisplayEntry entry, int label, int maxSteps) {
        analysisDisplayController.appendAnalysisArrows(arrows, entry, label, maxSteps);
    }

    CharSequence buildV68EngineAnalysisContent(boolean ignoredRedToMoveAtRoot) {
        return analysisDisplayController.buildV68EngineAnalysisContent();
    }

    List<EngineAnalysisPanel.DepthModule> buildMultiPvModules() {
        return analysisDisplayController.buildMultiPvModules();
    }

    CharSequence buildCompactSinglePvContent() {
        return analysisDisplayController.buildCompactSinglePvContent();
    }

    int computeNoCaptureMoveCount() {
        return computeNoCaptureMoveCountAtPly(currentPly);
    }

    int computeNoCaptureMoveCountAtPly(int ply) {
        int end = Math.max(0, Math.min(ply, engineMoves.size()));
        int quiet = 0;
        try {
            char[][] board = XiangqiRules.fromFen(baseFen);
            for (int i = 0; i < end; i++) {
                Move move = Move.fromEngineStep(engineMoves.get(i));
                if (!XiangqiRules.inBoard(move.fromRow, move.fromCol)
                        || !XiangqiRules.inBoard(move.toRow, move.toCol)) break;
                char moved = board[move.fromRow][move.fromCol];
                char captured = board[move.toRow][move.toCol];
                if (captured != ' ') quiet = 0;
                else quiet++;
                board[move.toRow][move.toCol] = moved;
                board[move.fromRow][move.fromCol] = ' ';
            }
        } catch (Exception e) {
            return 0;
        }
        return quiet;
    }

    boolean checkSixtyMoveDrawAfterMove(char capturedPiece) {
        if (selfAnalysisMode || gameOver) return false;
        int quiet = computeNoCaptureMoveCountAtPly(currentPly);
        if (capturedPiece != ' ') sixtyMoveDrawArmedPly = -1;
        if (quiet >= 120 && sixtyMoveDrawArmedPly > 0
                && currentPly >= sixtyMoveDrawArmedPly) {
            finishGame("60回合不吃子，和棋！", GameEndType.NO_CAPTURE_DRAW);
            return true;
        }
        return false;
    }

    void updateSixtyMoveDrawArmFromScore(int scoredPly) {
        if (selfAnalysisMode || gameOver || scoredPly <= 0) return;
        int quiet = computeNoCaptureMoveCountAtPly(scoredPly);
        if (quiet == 119) {
            if (currentPly < scoredPly) return;
            int laterMoves = currentPly - scoredPly;
            int currentQuiet = computeNoCaptureMoveCountAtPly(currentPly);
            // 评分可能异步返回；若评分局面之后已经发生吃子，不得沿用旧的 119 步预警。
            if (currentQuiet < 119 + laterMoves) return;
            sixtyMoveDrawArmedPly = scoredPly + 1;
            appendLog("[局势评分] 已检测到连续 119 步不吃子；下一步仍不吃子将判和。\n");
            if (currentPly >= sixtyMoveDrawArmedPly && currentQuiet >= 120) {
                finishGame("60回合不吃子，和棋！", GameEndType.NO_CAPTURE_DRAW);
            }
        } else if (sixtyMoveDrawArmedPly > 0 && scoredPly >= sixtyMoveDrawArmedPly) {
            sixtyMoveDrawArmedPly = -1;
        }
    }

    /**
     * V19.2：如果当前浏览步已有完整重新打分结果，开启放大镜时用独立 rescoreEngine
     * 对“走这一步之前的局面”做一次短刷新。这样当前步的 PV1 推荐与走前评分会更新，
     * 而 manualEngine 的 go infinite 则持续更新走后评分，优劣等级因此可以随分析动态变化。
     */
    void startReviewRefreshForCurrentPly(final int analysisGeneration,
                                                  final String positionKey) {
        // V19.8：正式对局未结束时放大镜只提供当前局面的 PV，不再并发刷新复盘优劣数据。
        if (!selfAnalysisMode && !completedDuelGame) return;
        if (!analysisMode || isRescoring || currentPly <= 0 || boardView == null) return;
        final int ply = currentPly;
        final int index = ply - 1;
        if (!hasCompleteRescoreReview(index)) return;
        final int generation = ++reviewRefreshGeneration;
        reviewRefreshPly = ply;
        final List<String> beforeMoves = new ArrayList<String>(engineMoves.subList(0, index));
        final boolean redToMoveBefore = redToMoveAtPly(index);
        reviewRefreshStarter.execute(() -> {
            if (!analysisMode || isRescoring || generation != reviewRefreshGeneration
                    || analysisGeneration != operationGeneration) return;
            ensureRescoreEngineAlive();
            if (!analysisMode || isRescoring || generation != reviewRefreshGeneration
                    || analysisGeneration != operationGeneration
                    || rescoreEngine == null || !rescoreEngine.hasUsableEngine()) return;
            rescoreEngine.stopAnalysis();
            final PikafishEngine.EngineInfo[] latest = new PikafishEngine.EngineInfo[1];
            rescoreEngine.requestBestMoveWithInfo(baseFen, beforeMoves,
                    PikafishEngine.SearchLimit.movetime(1000),
                    new PikafishEngine.AnalysisCallback() {
                        @Override public void onInfo(PikafishEngine.EngineInfo info, String rawLine) {
                            if (info == null || Math.max(1, info.multiPv) != 1) return;
                            latest[0] = copyInfo(info);
                            handler.post(() -> applyLiveReviewRefresh(generation, analysisGeneration,
                                    positionKey, ply, index, redToMoveBefore, info, false));
                        }

                        @Override public void onBestMove(String bestMove, String rawInfo) {
                            handler.post(() -> {
                                if (!isLiveReviewRefreshValid(generation, analysisGeneration, positionKey, ply)) return;
                                PikafishEngine.EngineInfo info = latest[0];
                                applyLiveReviewRefresh(generation, analysisGeneration, positionKey, ply, index,
                                        redToMoveBefore, info, true);
                                String fallback = normalizeStep(bestMove);
                                if (fallback.length() >= 4 && index < rescoreRecommendedMoves.size()) {
                                    rescoreRecommendedMoves.set(index, fallback);
                                }
                                updateAnalysisArrows();
                                refreshSituationChart();
                                persistCurrentSession();
                            });
                        }

                        @Override public void onError(String message) {
                            handler.post(() -> {
                                if (!isLiveReviewRefreshValid(generation, analysisGeneration, positionKey, ply)) return;
                                if (latest[0] != null) {
                                    applyLiveReviewRefresh(generation, analysisGeneration, positionKey, ply, index,
                                            redToMoveBefore, latest[0], true);
                                    persistCurrentSession();
                                }
                            });
                        }
                    });
        });
    }

    private boolean isLiveReviewRefreshValid(int generation, int analysisGeneration,
                                             String positionKey, int ply) {
        return analysisMode && !isRescoring && generation == reviewRefreshGeneration
                && analysisGeneration == operationGeneration && reviewRefreshPly == ply
                && currentPly == ply && positionKey.equals(currentPositionKey());
    }

    private void applyLiveReviewRefresh(int generation, int analysisGeneration,
                                        String positionKey, int ply, int index,
                                        boolean redToMoveBefore, PikafishEngine.EngineInfo info,
                                        boolean finalFrame) {
        if (info == null || !isLiveReviewRefreshValid(generation, analysisGeneration, positionKey, ply)) return;
        ensureScoreSize(engineMoves.size());
        if (info.hasScore) {
            int score = redScoreFromInfo(info, redToMoveBefore);
            int mate = info.mateScore ? Math.abs(info.score) : 0;
            if (index == 0) {
                initialScoreRed = score;
                initialMatePly = mate;
                initialScoreKnown = true;
                initialRescoreScoreKnown = true;
            } else if (index - 1 < redPerspectiveScores.size()) {
                redPerspectiveScores.set(index - 1, score);
                scoreMatePlies.set(index - 1, mate);
                scoreKnown.set(index - 1, true);
                rescoreScoreKnown.set(index - 1, true);
            }
        }
        String recommended = normalizeStep(info.firstMove());
        if (recommended.length() >= 4 && index < rescoreRecommendedMoves.size()) {
            rescoreRecommendedMoves.set(index, recommended);
        }
        updateAnalysisArrows();
        if (finalFrame) refreshSituationChart();
    }

    void updateCurrentSituationScoreFromAnalysis(PikafishEngine.EngineInfo info,
                                                          boolean redToMoveAtRoot,
                                                          String positionKey) {
        // V19.8：未结束的正式对局中，放大镜只做提示，不覆盖固定 100ms 的赛后复盘评分来源。
        if (!selfAnalysisMode && !completedDuelGame) return;
        if (info == null || !info.hasScore || Math.max(1, info.multiPv) != 1
                || !positionKey.equals(currentPositionKey())) return;
        if (currentPly == 0) {
            int newScore = redScoreFromInfo(info, redToMoveAtRoot);
            int newMate = info.mateScore ? Math.abs(info.score) : 0;
            invalidateInitialRescoreIfChanged(newScore, newMate);
            initialScoreRed = newScore;
            initialMatePly = newMate;
            initialScoreKnown = true;
            return;
        }
        int index = currentPly - 1;
        ensureScoreSize(engineMoves.size());
        if (index < 0 || index >= redPerspectiveScores.size()) return;
        int newScore = redScoreFromInfo(info, redToMoveAtRoot);
        int newMate = info.mateScore ? Math.abs(info.score) : 0;
        // V19.2：若当前步已有完整重新打分提示，放大镜实时分析就是对这份提示的“刷新”，
        // 不应因为分数变化把 rescore 标记撤销，否则优劣图标会立即消失。
        boolean refreshExistingReview = hasCompleteRescoreReview(index);
        if (!refreshExistingReview) invalidateRescoreIfChanged(index, newScore, newMate);
        redPerspectiveScores.set(index, newScore);
        scoreMatePlies.set(index, newMate);
        scoreKnown.set(index, true);
        if (refreshExistingReview && index < rescoreScoreKnown.size()) rescoreScoreKnown.set(index, true);
        // 由 onInfo 的统一节流刷新负责重绘；这里仅更新数据，避免每条 info 重画曲线。
    }

    void immediateMove() {
        analysisMoveController.immediateMove();
    }

    void maybeRequestImmediateAnalysisBestMove() {
        analysisMoveController.maybeRequestImmediateAnalysisBestMove();
    }

    void executeImmediateAnalysisMove() {
        analysisMoveController.executeImmediateAnalysisMove();
    }

    void cancelImmediateMoveRequest() {
        analysisMoveController.cancelImmediateMoveRequest();
    }

    void forceAlternativeMove() {
        analysisMoveController.forceAlternativeMove();
    }

    void maybeAutoMove() {
        duelEngineController.maybeAutoMove();
    }

    void playEngineStep(String step, String source, Integer scoreRed, int matePly) {
        gameMoveController.playEngineStep(step, source, scoreRed, matePly);
    }

    void fallbackLegalMove(String reason) {
        gameMoveController.fallbackLegalMove(reason);
    }

    @Override
    public void onMoveMade(Move move, char movedPiece, char capturedPiece,
                           String fenAfterMove, boolean redToMoveNow) {
        gameMoveController.onMoveMade(move, movedPiece, capturedPiece, fenAfterMove,
                redToMoveNow);
    }

    @Override
    public void onMessage(String message) {
        appendLog(message + "\n");
    }

    @Override
    public void onEditBoardChanged() {
        if (boardView == null || !boardView.isEditMode()) return;
        if (XiangqiRules.isPiece(selectedEditPiece)
                && XiangqiRules.countPiece(boardView.copyBoard(), selectedEditPiece)
                >= XiangqiRules.maxPieceCount(selectedEditPiece)) {
            selectedEditPiece = 0;
            boardView.setEditPaintPiece((char) 0);
        }
        // V19.3：棋盘编辑回调不再 post 整页重建；本帧直接轻量更新数量和选中态。
        refreshEditPanelState();
    }

    void finishGame(String message, GameEndType type) {
        // 重新打分属于纯分析流程。即使有延迟回调误触终局检测，也不得弹窗或写入战绩。
        if (isRescoring) {
            appendLog("[重新打分] 已忽略终局结算触发，不影响对局和历史战绩。\n");
            return;
        }
        if (selfAnalysisMode) {
            finishAnalysisPosition(message, type);
            return;
        }
        if (gameOver && terminalDialogShown) return;
        applyTerminalSituationScore(type);
        gameOver = true;
        completedDuelGame = true;
        postGameSandboxActive = false;
        terminalDialogShown = true;
        if (type == GameEndType.RED_WIN) gameResultTag = "1-0";
        else if (type == GameEndType.BLACK_WIN) gameResultTag = "0-1";
        else gameResultTag = "1/2-1/2";
        if (evaluationMode) {
            updatePlayerRating(type);
            // 已结束的评测不能被“继续评测”入口恢复；下一次只能重新匹配新局。
            evaluationSession = false;
        }
        autoMoveInProgress = false;
        engineThinking = false;
        computerSideThinking = false;
        drawOfferInProgress = false;
        sixtyMoveDrawArmedPly = -1;
        if (analysisMode) stopManualAnalysis(true);
        if (gameEngine != null) gameEngine.stopAnalysis();
        if (drawEngine != null) drawEngine.stopAnalysis();
        if (situationEngine != null) situationEngine.stopAnalysis();
        if (!selfAnalysisMode) {
            if (!competitiveResultEligible) {
                appendLog("本局曾进入分析流程，终局不计入历史战绩。\n");
            } else if (!evaluationMode) {
                appendLog("自选难度对局不计入等级分，历史战绩照常记录。\n");
                if (!resultRecordedForCurrentGame) {
                    recordDifficultyResult(type);
                    resultRecordedForCurrentGame = true;
                }
            } else if (!resultRecordedForCurrentGame) {
                recordDifficultyResult(type);
                resultRecordedForCurrentGame = true;
            } else {
                appendLog("历史战绩已结算，本盘不重复计数。\n");
            }
            saveCompletedDuelToRecent(type);
            if (evaluationMode) clearEvaluationSession();
            else clearSavedSession(false);
        }
        updatePlayerLabels();
        refreshBoardInputState();
        if (evaluationMode) {
            int restorePly = currentPly;
            boolean restoreReversed = boardView != null && boardView.isReversed();
            showGameScreen();
            try {
                rebuildBoardToPly(restorePly);
                boardView.setReversed(restoreReversed);
            } catch (Exception e) {
                appendLog("评测终局界面恢复失败：" + e.getMessage() + "。\n");
            }
            updatePlayerLabels();
            refreshBoardInputState();
            updateGameContent();
        } else {
            updateGameContent();
        }
        appendLog("对局结束：" + message + "\n");
        reportBackfillPass = 0;
        handler.postDelayed(this::backfillCompletedDuelScores, 120L);
        showUnifiedGameEndDialog(message, type);
    }

    /**
     * 分析棋谱到达终局时只更新棋谱状态与局势图，不执行正式对局结算。
     * 这里故意不显示胜负弹窗、不写入历史战绩，也不清除留存棋谱。
     */
    private void finishAnalysisPosition(String message, GameEndType type) {
        if (gameOver) return;
        applyTerminalSituationScore(type);
        gameOver = true;
        terminalDialogShown = false;
        if (type == GameEndType.RED_WIN) gameResultTag = "1-0";
        else if (type == GameEndType.BLACK_WIN) gameResultTag = "0-1";
        else gameResultTag = "1/2-1/2";
        autoMoveInProgress = false;
        engineThinking = false;
        drawOfferInProgress = false;
        sixtyMoveDrawArmedPly = -1;
        if (analysisMode) stopManualAnalysis(true);
        if (gameEngine != null) gameEngine.stopAnalysis();
        if (drawEngine != null) drawEngine.stopAnalysis();
        if (situationEngine != null) situationEngine.stopAnalysis();
        updatePlayerLabels();
        refreshBoardInputState();
        updateGameContent();
        appendLog("分析到达终局：" + message + "；未弹窗，也未计入历史战绩。\n");
    }

    void markCurrentLineAsAnalysisOnly(String reason) {
        if (!competitiveResultEligible) return;
        competitiveResultEligible = false;
        disqualifyRating(reason);
        appendLog(reason + "：本棋谱后续仅作为分析记录，不计入历史战绩。\n");
        persistCurrentSession();
    }

    private void applyTerminalSituationScore(GameEndType type) {
        if (engineMoves.isEmpty()) return;
        ensureScoreSize(engineMoves.size());
        int index = engineMoves.size() - 1;
        if (type == GameEndType.RED_WIN) {
            invalidateRescoreIfChanged(index, SITUATION_MATE_LIMIT, 0);
            redPerspectiveScores.set(index, SITUATION_MATE_LIMIT);
            scoreMatePlies.set(index, 0);
            scoreKnown.set(index, true);
            rescoreScoreKnown.set(index, true);
        } else if (type == GameEndType.BLACK_WIN) {
            invalidateRescoreIfChanged(index, -SITUATION_MATE_LIMIT, 0);
            redPerspectiveScores.set(index, -SITUATION_MATE_LIMIT);
            scoreMatePlies.set(index, 0);
            scoreKnown.set(index, true);
            rescoreScoreKnown.set(index, true);
        }
        refreshSituationChart();
        persistCurrentSession();
    }

    private void recordDifficultyResult(GameEndType type) {
        if (selfAnalysisMode || !competitiveResultEligible) return;
        String bucket;
        if (type == GameEndType.AGREED_DRAW || type == GameEndType.NO_CAPTURE_DRAW) {
            bucket = "draw";
        } else {
            boolean playerWon = (type == GameEndType.RED_WIN && !enginePlaysRed)
                    || (type == GameEndType.BLACK_WIN && enginePlaysRed);
            bucket = playerWon ? "win" : "loss";
        }
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        String key = statsKey(selectedDifficultyIndex, bucket);
        prefs.edit().putInt(key, prefs.getInt(key, 0) + 1).commit();
    }

    private void updatePlayerRating(GameEndType type) {
        evaluationRatingController.updateAfterGame(type);
    }

    private void showUnifiedGameEndDialog(String message, GameEndType type) {
        gameEndDialogController.show(message, type);
    }

    void resetAnalysisPositionState() {
        manualAnalysisLaunchGeneration++;
        clearPendingAnalysisUiUpdates();
        analysisBannedRootMoves.clear();
        cancelImmediateMoveRequest();
        latestAnalysisBestMove = "";
        latestAnalysisInfo = null;
        latestAnalysisDepth = 0;
        analysisPositionKey = "";
        analysisEntries.clear();
        analysisPvEntries.clear();
        lastMultiPvModules = Collections.<EngineAnalysisPanel.DepthModule>emptyList();
        analysisExpectedMultiPv = Math.max(1, analysisConfiguredMultiPv);
    }

    void stopSearchForPositionChange() {
        operationGeneration++;
        computerMoveGeneration++;
        computerSideController.cancelPendingImmediateRequest();
        computerSideThinking = false;
        autoMoveInProgress = false;
        engineThinking = false;
        if (gameEngine != null) gameEngine.cancelSearch();
        if (manualEngine != null) manualEngine.cancelSearch();
        if (drawOfferInProgress) {
            drawGeneration++;
            drawOfferInProgress = false;
            if (drawEngine != null) drawEngine.stopAnalysis();
            dismissDrawAnalysisDialog();
        }
        resetAnalysisPositionState();
        updatePlayerLabels();
    }

    private void stopAllEngineWork() {
        operationGeneration++;
        manualAnalysisLaunchGeneration++;
        drawGeneration++;
        rescoreController.cancelAll();
        situationScoreGeneration++;
        autoMoveInProgress = false;
        engineThinking = false;
        analysisMode = false;
        computerSideController.cancelPendingImmediateRequest();
        drawOfferInProgress = false;
        cancelImmediateMoveRequest();
        if (gameEngine != null) gameEngine.cancelSearch();
        if (manualEngine != null) manualEngine.cancelSearch();
        if (drawEngine != null) drawEngine.stopAnalysis();
        if (rescoreEngine != null) rescoreEngine.stopAnalysis();
        if (situationEngine != null) situationEngine.stopAnalysis();
        dismissDrawAnalysisDialog();
        if (boardView != null) boardView.setAnalysisArrows(
                Collections.<ChessBoardView.AnalysisArrow>emptyList());
    }

    void showDrawAnalysisDialog() {
        dismissDrawAnalysisDialog();
        drawAnalysisDialog = new AlertDialog.Builder(this)
                .setMessage("分析中")
                .setCancelable(false)
                .create();
        drawAnalysisDialog.show();
    }

    void dismissDrawAnalysisDialog() {
        if (drawAnalysisDialog != null) {
            try { drawAnalysisDialog.dismiss(); } catch (Exception ignored) {}
            drawAnalysisDialog = null;
        }
    }

    void offerDraw() {
        gameTurnController.offerDraw();
    }

    void confirmResign() {
        gameTurnController.confirmResign();
    }

    void updateDrawButtonState() {
        gameActionMenuController.updateDrawButtonState();
    }

    boolean checkNoAttackDraw(char[][] board) {
        return gameTurnController.checkNoAttackDraw(board);
    }

    void undoToPreviousHumanTurn() {
        gameTurnController.undoToPreviousHumanTurn();
    }

    boolean redToMoveAtPly(int ply) {
        boolean baseRed;
        try { baseRed = XiangqiRules.redToMoveFromFen(baseFen); }
        catch (Exception e) { baseRed = true; }
        return (ply & 1) == 0 ? baseRed : !baseRed;
    }

    void openManualFilePicker() {
        manualRecordController.openManualFilePicker();
    }

    void showStoreManualDialog() {
        manualRecordController.showStoreManualDialog();
    }

    private byte[] buildStoredManualBytes(int format) {
        return manualRecordController.buildStoredManualBytes(format);
    }

    void ensureNestedBranchLabels(ManualVariation owner, int relativeNode) {
        manualRecordController.ensureNestedBranchLabels(owner, relativeNode);
    }

    private void loadManualFromUri(Uri uri) {
        manualRecordController.loadManualFromUri(uri);
    }

    private void loadParsedManual(PgnManualUtils.ParsedManual manual) {
        manualRecordController.loadParsedManual(manual);
    }

    void loadParsedManualForCorrection(PgnManualUtils.ParsedManual manual) {
        manualRecordController.loadParsedManual(manual);
    }

    private void handleIncomingManualIntent(Intent intent) {
        manualRecordController.handleIncomingManualIntent(intent);
    }

    void showBoardClipboardDialog() {
        manualRecordController.showBoardClipboardDialog();
    }

    private void showRescoreDialog() {
        if (isRescoring) return;
        if (engineMoves.isEmpty()) {
            Toast.makeText(this, "棋谱为空，无需重新打分", Toast.LENGTH_SHORT).show();
            return;
        }
        RescoreOptionsDialog.show(this, engineMoves.size(), maxManualLinePlyCount(),
                (timeMs, startPly, endPly, forward, allBranches, appendScoresToComments) ->
                        startRescore(timeMs, startPly, endPly, forward, allBranches,
                                appendScoresToComments));
    }

    /** 所有活动/非活动线路中最长的绝对 ply，用于“所有分支”范围校验。 */
    int maxManualLinePlyCount() {
        return maxManualLinePlyCount(Collections.<String>emptyList(), engineMoves, manualVariations);
    }

    int maxManualLinePlyCount(List<String> prefix, List<String> lineMoves,
                                      Map<Integer, List<ManualVariation>> branches) {
        int prefixSize = prefix == null ? 0 : prefix.size();
        int max = prefixSize + (lineMoves == null ? 0 : lineMoves.size());
        if (lineMoves == null || branches == null || branches.isEmpty()) return max;
        ArrayList<Integer> nodes = new ArrayList<Integer>(branches.keySet());
        Collections.sort(nodes);
        for (Integer nodeValue : nodes) {
            if (nodeValue == null) continue;
            int node = nodeValue;
            if (node < 0 || node > lineMoves.size()) continue;
            List<ManualVariation> vars = branches.get(nodeValue);
            if (vars == null) continue;
            ArrayList<String> branchPrefix = new ArrayList<String>(prefixSize + node);
            if (prefix != null) branchPrefix.addAll(prefix);
            branchPrefix.addAll(lineMoves.subList(0, node));
            for (ManualVariation variation : vars) {
                if (variation == null || variation.engineSteps.isEmpty()) continue;
                max = Math.max(max, maxManualLinePlyCount(branchPrefix, variation.engineSteps,
                        variation.variations));
            }
        }
        return max;
    }

    private void startRescore(int timeMs, int startIndex, int endExclusive,
                              boolean forward, boolean allBranches,
                              boolean appendScoresToComments) {
        rescoreController.start(timeMs, startIndex, endExclusive, forward, allBranches,
                appendScoresToComments);
    }

    void ensureRescoreEngineAlive() {
        rescoreController.ensureEngineAlive();
    }

    void ensureVariationScoreSize(ManualVariation variation) {
        if (variation == null) return;
        int size = variation.engineSteps.size();
        while (variation.scores.size() < size) variation.scores.add(0);
        while (variation.matePlies.size() < size) variation.matePlies.add(0);
        while (variation.scoreKnown.size() < size) variation.scoreKnown.add(false);
        while (variation.rescoreRecommendedMoves.size() < size) variation.rescoreRecommendedMoves.add("");
        while (variation.rescoreScoreKnown.size() < size) variation.rescoreScoreKnown.add(false);
    }

    boolean applyRescoreBranchScore(RescoreBranchTask task, int localIndex,
                                            List<String> absoluteMoves,
                                            boolean redToMoveAtPosition,
                                            PikafishEngine.EngineInfo info) {
        if (task == null || task.variation == null || localIndex < 0
                || localIndex >= task.variation.engineSteps.size()) return false;
        ensureVariationScoreSize(task.variation);
        Integer terminal = terminalRedScoreForMoves(absoluteMoves);
        if (terminal != null) {
            task.variation.scores.set(localIndex, terminal);
            task.variation.matePlies.set(localIndex, 0);
            task.variation.scoreKnown.set(localIndex, true);
            task.variation.rescoreScoreKnown.set(localIndex, true);
            return true;
        }
        if (info != null && info.hasScore) {
            task.variation.scores.set(localIndex, redScoreFromInfo(info, redToMoveAtPosition));
            task.variation.matePlies.set(localIndex, info.mateScore ? Math.abs(info.score) : 0);
            task.variation.scoreKnown.set(localIndex, true);
            task.variation.rescoreScoreKnown.set(localIndex, true);
            return true;
        }
        return false;
    }

    void applyRescoreBranchRecommendation(ManualVariation variation, int localPlyIndex,
                                                  PikafishEngine.EngineInfo info,
                                                  String bestMoveFallback) {
        if (variation == null) return;
        ensureVariationScoreSize(variation);
        if (localPlyIndex < 0 || localPlyIndex >= variation.engineSteps.size()) return;
        String step = info == null ? "" : normalizeStep(info.firstMove());
        if (step.length() < 4) step = normalizeStep(bestMoveFallback);
        if (step.length() >= 4) variation.rescoreRecommendedMoves.set(localPlyIndex, step);
    }

    /**
     * 重新打分只更新曲线数据，不参与走棋或胜负结算。终局常见 bestmove (none) 且无 score，
     * 此时直接依据棋盘终局状态补成红/黑绝杀分，避免最后一步错误显示为 0 分。
     */
    boolean applyRescoreScore(int index, int total, boolean redToMoveAtPosition,
                                      PikafishEngine.EngineInfo info) {
        ensureScoreSize(total);
        // 最后一手若已形成终局，以规则结果为最高优先级；即使引擎返回 0 分也不能覆盖胜负。
        Integer terminalScore = index == total - 1 ? terminalRedScoreAtPly(index + 1) : null;
        if (terminalScore != null) {
            redPerspectiveScores.set(index, terminalScore);
            scoreMatePlies.set(index, 0);
            scoreKnown.set(index, true);
            rescoreScoreKnown.set(index, true);
            refreshSituationChart();
            appendLog("重新打分第 " + (index + 1)
                    + " 手：已按终局胜负记为 "
                    + (terminalScore > 0 ? "红方胜" : "黑方胜") + "。\n");
            return true;
        }
        if (info != null && info.hasScore) {
            redPerspectiveScores.set(index, redScoreFromInfo(info, redToMoveAtPosition));
            scoreMatePlies.set(index, info.mateScore ? Math.abs(info.score) : 0);
            scoreKnown.set(index, true);
            rescoreScoreKnown.set(index, true);
            refreshSituationChart();
            return true;
        }
        return false;
    }

    /** 保存“该局面”的 PV1 首着，plyIndex 即它将作为实际第几手的推荐。 */
    void applyRescoreRecommendation(int plyIndex, PikafishEngine.EngineInfo info) {
        applyRescoreRecommendation(plyIndex, info, "");
    }

    void applyRescoreRecommendation(int plyIndex, PikafishEngine.EngineInfo info,
                                             String bestMoveFallback) {
        ensureScoreSize(engineMoves.size());
        if (plyIndex < 0 || plyIndex >= engineMoves.size()) return;
        String step = info == null ? "" : normalizeStep(info.firstMove());
        if (step.length() < 4) step = normalizeStep(bestMoveFallback);
        if (step.length() >= 4) rescoreRecommendedMoves.set(plyIndex, step);
    }

    Integer terminalRedScoreAtPly(int ply) {
        int limit = Math.min(Math.max(0, ply), engineMoves.size());
        return terminalRedScoreForMoves(new ArrayList<String>(engineMoves.subList(0, limit)));
    }

    Integer terminalRedScoreForMoves(List<String> moves) {
        try {
            char[][] board = XiangqiRules.fromFen(baseFen);
            boolean redToMove = XiangqiRules.redToMoveFromFen(baseFen);
            if (moves != null) {
                for (String step : moves) {
                    Move move = Move.fromEngineStep(step);
                    if (!XiangqiRules.isLegalMove(board, move)) return null;
                    board[move.toRow][move.toCol] = board[move.fromRow][move.fromCol];
                    board[move.fromRow][move.fromCol] = ' ';
                    redToMove = !redToMove;
                }
            }
            boolean redKing = false;
            boolean blackKing = false;
            for (int r = 0; r < board.length; r++) {
                for (int c = 0; c < board[r].length; c++) {
                    if (board[r][c] == 'K') redKing = true;
                    else if (board[r][c] == 'k') blackKing = true;
                }
            }
            if (!redKing) return -SITUATION_MATE_LIMIT;
            if (!blackKing) return SITUATION_MATE_LIMIT;
            if (XiangqiRules.generateLegalMoves(board, redToMove).isEmpty()) {
                return redToMove ? -SITUATION_MATE_LIMIT : SITUATION_MATE_LIMIT;
            }
        } catch (Exception ignored) {}
        return null;
    }

    void ensureScoreSize(int size) {
        while (redPerspectiveScores.size() < size) redPerspectiveScores.add(0);
        while (scoreMatePlies.size() < size) scoreMatePlies.add(0);
        while (scoreKnown.size() < size) scoreKnown.add(false);
        while (rescoreRecommendedMoves.size() < size) rescoreRecommendedMoves.add("");
        while (rescoreScoreKnown.size() < size) rescoreScoreKnown.add(false);
    }

    private void stopRescore(boolean userInitiated) {
        rescoreController.stop(userInitiated);
    }

    private void updateRescoreProgressText() {
        rescoreController.updateProgressText();
    }

    private String currentRescoreProgressText() {
        return rescoreController.progressText();
    }

    private void clearRescoreProgress() {
        rescoreController.clearProgress();
    }

    int findFirstEndgameRound() {
        try {
            char[][] board = XiangqiRules.fromFen(baseFen);
            if (isReportEndgameMaterial(board)) return 0;
            for (int i = 0; i < engineMoves.size(); i++) {
                Move move = Move.fromEngineStep(engineMoves.get(i));
                if (!XiangqiRules.isLegalMove(board, move)) return -1;
                board[move.toRow][move.toCol] = board[move.fromRow][move.fromCol];
                board[move.fromRow][move.fromCol] = ' ';
                if (isReportEndgameMaterial(board)) return (i + 2) / 2;
            }
        } catch (Exception ignored) {}
        return -1;
    }

    /** 打分算法定义：双方各自的车、马、炮数量都 <= 2 时进入残局。 */
    private boolean isReportEndgameMaterial(char[][] board) {
        int red = 0;
        int black = 0;
        for (int r = 0; r < 10; r++) {
            for (int c = 0; c < 9; c++) {
                char piece = board[r][c];
                char p = Character.toLowerCase(piece);
                if (p != 'r' && p != 'n' && p != 'c') continue;
                if (Character.isUpperCase(piece)) red++;
                else black++;
            }
        }
        return red <= 2 && black <= 2;
    }

    void showLauncherSettingsMenu() {
        settingsController.showLauncherSettingsMenu();
    }

    void showPlayerRatingSettingsDialog() {
        settingsController.showPlayerRatingSettingsDialog();
    }

    void resetPlayerRatingToMinimum() {
        evaluationRatingController.resetRatingToMinimum();
    }

    void showDifficultyNameSettingsDialog() {
        settingsController.showDifficultyNameSettingsDialog();
    }

    void showEvaluationRatingHistory() {
        evaluationRatingHistoryVisible = true;
        evaluationLauncherVisible = false;
        LauncherController.showEvaluationRatingHistory(this);
    }

    void showGameSettingsMenu() {
        settingsController.showGameSettingsMenu();
    }

    void showManualPlaySettingsDialog() {
        settingsController.showManualPlaySettingsDialog();
    }

    void toggleComputerSide(boolean red) {
        computerSideController.toggle(red);
    }

    private void warmComputerSideEngine() {
        computerSideController.warmUp();
    }

    void startComputerSideMove() {
        computerSideController.startMove();
    }
    void showLogDialog() {
        settingsController.showLogDialog();
    }

    void showEngineOptionsDialog() {
        engineSettingsController.showOptionsDialog();
    }

    private void showAboutDialog() {
        settingsController.showAboutDialog();
    }

    void prepareAboutDialog() {
        settingsController.prepareAboutDialog();
    }

    private void installAndLoadRpBook() {
        openingBook = new ObkBook();
        File dir = new File(getFilesDir(), "books/rp");
        File book = new File(dir, "book.obk");
        try {
            // V15.1: 始终覆盖解压开局库，确保使用 APK 内置最新版，避免旧文件残留。
            dir.mkdirs();
            try (InputStream in = getAssets().open("books/rp/book.obk");
                 FileOutputStream out = new FileOutputStream(book)) {
                byte[] buffer = new byte[64 * 1024];
                int read;
                while ((read = in.read(buffer)) >= 0) out.write(buffer, 0, read);
            }
            openingBook.configure(dir.getAbsolutePath(), "rp", false);
            openingBookReady = openingBook.load();
            appendLog(openingBookReady ? "开局库已加载。\n" : openingBook.getLastMessage() + "\n");
        } catch (Exception e) {
            openingBookReady = false;
            openingBook.configure(dir.getAbsolutePath(), "rp", false);
            appendLog("开局库文件尚未内置。\n");
        }
    }

    private void clearEvaluationSession() {
        gameSessionController.clearEvaluationSession();
    }

    void clearSavedSession(boolean analysis) {
        gameSessionController.clearSavedSession(analysis);
    }

    void persistCurrentSession() {
        gameSessionController.persistCurrentSession();
    }

    private void persistCurrentSession(boolean synchronous) {
        gameSessionController.persistCurrentSession(synchronous);
    }

    private void persistCompletedDuelSession(boolean synchronous) {
        gameSessionController.persistCompletedDuelSession(synchronous);
    }

    private SavedSession readSavedSession(boolean analysis) {
        return gameSessionController.readSavedSession(analysis);
    }

    private SavedSession readSavedEvaluationSession() {
        return gameSessionController.readSavedEvaluationSession();
    }

    private void restoreSavedSession(SavedSession saved) {
        gameSessionController.restoreSavedSession(saved);
    }

    List<String> movesUpToCurrentPly() {
        return gameSessionController.movesUpToCurrentPly();
    }

    String currentPositionKey() {
        return gameSessionController.currentPositionKey();
    }

    void requestSituationScoreForPly(final int plyIndex) {
        situationScoreController.requestSituationScoreForPly(plyIndex);
    }

    private void requestSituationScoreForPly(final int plyIndex, final Runnable completion) {
        situationScoreController.requestSituationScoreForPly(plyIndex, completion);
    }

    private void requestInitialSituationScore() {
        situationScoreController.requestInitialSituationScore();
    }

    private void requestInitialSituationScore(final Runnable completion) {
        situationScoreController.requestInitialSituationScore(completion);
    }

    private void backfillCompletedDuelScores() {
        situationScoreController.backfillCompletedDuelScores();
    }

    void applyPendingSituationRecommendationForMove(int moveIndex) {
        situationScoreController.applyPendingSituationRecommendationForMove(moveIndex);
    }

    private void invalidateRescoreIfChanged(int index, int newScore, int newMate) {
        situationScoreController.invalidateRescoreIfChanged(index, newScore, newMate);
    }

    private void invalidateInitialRescoreIfChanged(int newScore, int newMate) {
        situationScoreController.invalidateInitialRescoreIfChanged(newScore, newMate);
    }

    void invalidateSituationScoreRequests() {
        situationScoreController.invalidateSituationScoreRequests();
    }

    int redScoreFromInfo(PikafishEngine.EngineInfo info, boolean redToMove) {
        return situationScoreController.redScoreFromInfo(info, redToMove);
    }

    int lastKnownScore() {
        return situationScoreController.lastKnownScore();
    }

    int lastKnownMatePly() {
        return situationScoreController.lastKnownMatePly();
    }

    String compactInfo(PikafishEngine.EngineInfo info) {
        if (info == null) return "等待 info";
        String score = info.hasScore
                ? (info.mateScore ? ((info.score >= 0 ? "+M" : "-M") + Math.abs(info.score))
                : ((info.score > 0 ? "+" : "") + info.score)) : "-";
        return "depth=" + info.depth + " score=" + score + " nodes=" + info.nodes
                + " nps=" + info.nps + " pv=" + join(info.pv);
    }

    PikafishEngine.EngineInfo copyInfo(PikafishEngine.EngineInfo src) {
        if (src == null) return null;
        PikafishEngine.EngineInfo out = new PikafishEngine.EngineInfo();
        out.depth = src.depth;
        out.selDepth = src.selDepth;
        out.multiPv = src.multiPv;
        out.hasWdl = src.hasWdl;
        out.wdlWin = src.wdlWin;
        out.wdlDraw = src.wdlDraw;
        out.wdlLoss = src.wdlLoss;
        out.hasScore = src.hasScore;
        out.mateScore = src.mateScore;
        out.score = src.score;
        out.nodes = src.nodes;
        out.hashFull = src.hashFull;
        out.nps = src.nps;
        out.timeMs = src.timeMs;
        out.pv.addAll(src.pv);
        return out;
    }

    int getStoredManualOptionInt(String optionName, int fallback) {
        SharedPreferences sp = getSharedPreferences(PREFS, MODE_PRIVATE);
        String wanted = MANUAL_UCI_PREFIX + optionName;
        for (Map.Entry<String, ?> entry : sp.getAll().entrySet()) {
            if (entry.getKey() != null && entry.getKey().equalsIgnoreCase(wanted)) {
                try {
                    return Math.max(1, Integer.parseInt(String.valueOf(entry.getValue()).trim()));
                } catch (Exception ignored) {
                    return fallback;
                }
            }
        }
        return fallback;
    }

    DifficultyProfile currentDifficulty() {
        return DIFFICULTIES[clamp(selectedDifficultyIndex, 0, DIFFICULTIES.length - 1)];
    }

    private int difficultyRatingForIndex(int index) {
        return PlayerRatingCalculator.difficultyRating(index);
    }

    void updateLauncherRatingText() {
        if (launcherRatingText == null) return;
        launcherRatingText.setVisibility(showPlayerRating ? View.VISIBLE : View.GONE);
        launcherRatingText.setText("我的等级分：" + playerRating);
    }

    void styleAnalysisButton() {
        if (analysisButton == null) return;
        analysisButton.setActive(analysisMode);
    }

    void styleComputerSideButtons() {
        if (computerRedButton != null) computerRedButton.setActive(computerRedActive);
        if (computerBlackButton != null) computerBlackButton.setActive(computerBlackActive);
    }

    TextView sectionTitle(String text) {
        return uiComponentController.sectionTitle(text);
    }

    TextView selectorOption(String text) {
        return uiComponentController.selectorOption(text);
    }

    void styleSelectorOption(TextView view, boolean selected) {
        uiComponentController.styleSelectorOption(view, selected);
    }

    Button largeButton(CharSequence text, boolean primary) {
        return uiComponentController.largeButton(text, primary);
    }

    ToolbarIconButton toolbarIconButton(ToolbarIconButton.Icon icon,
                                                      String description,
                                                      View.OnClickListener listener) {
        return uiComponentController.toolbarIconButton(icon, description, listener);
    }

    Button evaluationToolbarButton(String text, View.OnClickListener listener) {
        return uiComponentController.evaluationToolbarButton(text, listener);
    }

    void styleEvaluationToolbarButton(Button button, boolean highlighted, boolean enabled) {
        uiComponentController.styleEvaluationToolbarButton(button, highlighted, enabled);
    }

    Button surfaceActionButton(String text) {
        return uiComponentController.surfaceActionButton(text);
    }

    Button compactButton(String text) {
        return uiComponentController.compactButton(text);
    }

    TextView playerLabel() {
        return uiComponentController.playerLabel();
    }

    TextView tabText(String text) {
        return uiComponentController.tabText(text);
    }

    void styleTab(TextView tab, boolean selected) {
        uiComponentController.styleTab(tab, selected);
    }

    TextView manualMoveRow(String text, boolean selected) {
        return uiComponentController.manualMoveRow(text, selected);
    }

    View manualMoveCell(String text, boolean selected, int node, boolean firstInRound) {
        return uiComponentController.manualMoveCell(text, selected, node, firstInRound);
    }

    LinearLayout.LayoutParams toolbarLp() {
        return uiComponentController.toolbarLp();
    }

    LinearLayout.LayoutParams matchWrap() {
        return uiComponentController.matchWrap();
    }

    void setRoundedBackground(View view, int fill, int radiusDp, int stroke) {
        uiComponentController.setRoundedBackground(view, fill, radiusDp, stroke);
    }

    void appendLog(String text) {
        if (text == null || text.length() == 0) return;
        logBuffer.append(text);
        if (logBuffer.length() > 80000) logBuffer.delete(0, logBuffer.length() - 65000);
    }

    String logText() {
        return logBuffer.toString();
    }

    void clearLogText() {
        logBuffer.setLength(0);
    }

    void setManualPlayLimitConfigured(boolean configured) {
        manualPlayLimitConfigured = configured;
    }

    void copyToClipboard(String label, String text) {
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) cm.setPrimaryClip(ClipData.newPlainText(label, text == null ? "" : text));
        Toast.makeText(this, "已复制", Toast.LENGTH_SHORT).show();
    }

    String normalizeStep(String move) {
        if (move == null) return "";
        String value = move.trim().toLowerCase(Locale.ROOT);
        return value.length() >= 4 ? value.substring(0, 4) : "";
    }

    String normalizeFen(String fen) {
        if (fen == null || fen.trim().length() == 0) return START_FEN;
        String value = fen.trim();
        if (value.toLowerCase(Locale.ROOT).startsWith("fen ")) value = value.substring(4).trim();
        String[] parts = value.split("\\s+");
        String side = parts.length >= 2 ? parts[1].toLowerCase(Locale.ROOT) : "w";
        if ("r".equals(side)) side = "w";
        if (!"w".equals(side) && !"b".equals(side)) side = "w";
        if (parts.length >= 6) return parts[0] + " " + side + " " + parts[2] + " "
                + parts[3] + " " + parts[4] + " " + parts[5];
        if (parts.length >= 2) return parts[0] + " " + side + " - - 0 1";
        return parts[0] + " w - - 0 1";
    }

    String join(List<String> values) {
        if (values == null || values.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (String value : values) {
            if (sb.length() > 0) sb.append(' ');
            sb.append(value);
        }
        return sb.toString();
    }

    private String pieceName(char piece) {
        switch (Character.toLowerCase(piece)) {
            case 'r': return "车";
            case 'n': return "马";
            case 'b': return Character.isUpperCase(piece) ? "相" : "象";
            case 'a': return Character.isUpperCase(piece) ? "仕" : "士";
            case 'k': return Character.isUpperCase(piece) ? "帅" : "将";
            case 'c': return "炮";
            case 'p': return Character.isUpperCase(piece) ? "兵" : "卒";
            default: return "?";
        }
    }

    int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handler.post(() -> handleIncomingManualIntent(intent));
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_NODE_STORAGE_ACCESS) {
            boolean ready = ensureNodeStorageReady(false);
            if (ready) requestGlobalBackgroundRefresh();
            if (ready && gameScreenVisible && boardView != null) applyCurrentSkinToBoard(boardView, false);
            if (ready && correctionPermissionPending) {
                correctionPermissionPending = false;
                showCorrectionListScreen();
            } else if (ready && recentScreenVisible) {
                showRecentGamesScreen();
            } else if (!ready) {
                correctionPermissionPending = false;
                Toast.makeText(this, "未获得文件访问权限", Toast.LENGTH_SHORT).show();
            }
            return;
        }
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        if (requestCode == REQ_OPEN_MANUAL) {
            loadManualFromUri(uri);
        } else if (requestCode == REQ_STORE_MANUAL) {
            try {
                ManualFileIo.writeBytes(getContentResolver(), uri,
                        buildStoredManualBytes(pendingStoreFormat));
                String format = ManualFileIo.formatName(pendingStoreFormat);
                Toast.makeText(this, "已存储 " + format + " 棋谱", Toast.LENGTH_SHORT).show();
                appendLog("已把 " + format + " 棋谱存储到用户选择的路径。\n");
            } catch (Exception e) {
                Toast.makeText(this, "存储棋谱失败：" + e.getMessage(), Toast.LENGTH_LONG).show();
                appendLog("存储棋谱失败：" + e.getMessage() + "。\n");
            }
        }
    }

    @Override
    public void onBackPressed() {
        if (pushModeActive) {
            exitPushMode(pushReturnReversed, pushResumeAnalysis);
            return;
        }
        // V16：编辑局面时，系统返回键必须与编辑面板“取消”完全一致，
        // 先恢复进入编辑前的局面，而不是按空棋谱规则返回初始界面。
        if (gameScreenVisible && boardView != null && boardView.isEditMode()) {
            finishEditMode(false);
            return;
        }
        if (correctionBoardVisible) {
            manualEngine.stopAnalysis();
            temporaryAnalysisRunning = false;
            temporaryAnalysisGeneration++;
            showCorrectionListScreen();
            return;
        }
        if (correctionListVisible) {
            returnFromCorrectionListScreen();
            return;
        }
        if (recentScreenVisible) {
            showLauncherScreen();
            return;
        }
        if (evaluationRatingHistoryVisible) {
            showEvaluationLauncher();
            return;
        }
        if (gameScreenVisible && evaluationMode) {
            returnToEvaluationLauncherPreservingGame();
            return;
        }
        if (gameScreenVisible && customDifficultySession) {
            returnToCustomDifficultyLauncherPreservingGame();
            return;
        }
        if (evaluationLauncherVisible || customDifficultyLauncherVisible) {
            showLauncherScreen();
            return;
        }
        if (!gameScreenVisible) {
            super.onBackPressed();
            return;
        }
        if (completedDuelGame) {
            clearSavedSession(false);
            showLauncherScreen();
            return;
        }
        if (engineMoves.isEmpty()) {
            showLauncherScreen();
            return;
        }
        // V17.8: 返回初始界面不再弹确认框，默认保留当前棋谱直接返回。
        persistCurrentSession(true);
        showLauncherScreen();
    }

    @Override
    protected void onResume() {
        super.onResume();
        boolean ready = ensureNodeStorageReady(false);
        if (ready) requestGlobalBackgroundRefresh();
        if (ready && gameScreenVisible && boardView != null) applyCurrentSkinToBoard(boardView, false);
        if (ready && correctionPermissionPending) {
            correctionPermissionPending = false;
            handler.post(this::showCorrectionListScreen);
        } else if (ready && recentScreenVisible) {
            handler.post(this::showRecentGamesScreen);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions,
                                           int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQ_NODE_STORAGE_ACCESS) return;
        boolean ready = ensureNodeStorageReady(false);
        if (ready) requestGlobalBackgroundRefresh();
        if (ready && gameScreenVisible && boardView != null) applyCurrentSkinToBoard(boardView, false);
        if (ready && correctionPermissionPending) {
            correctionPermissionPending = false;
            showCorrectionListScreen();
        } else if (ready && recentScreenVisible) {
            showRecentGamesScreen();
        } else if (!ready) {
            correctionPermissionPending = false;
            Toast.makeText(this, "未获得文件访问权限", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onPause() {
        if (gameScreenVisible && !gameOver && !completedDuelGame) {
            persistCurrentSession(true);
        }
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (gameScreenVisible && !gameOver && !completedDuelGame) {
            persistCurrentSession(true);
        }
        handler.removeCallbacksAndMessages(null);
        manualAnalysisStarter.shutdownNow();
        reviewRefreshStarter.shutdownNow();
        skinRuntimeController.shutdown();
        dismissDrawAnalysisDialog();
        gameReportController.dismiss();
        settingsController.dismiss();
        if (gameEngine != null) gameEngine.stop();
        if (manualEngine != null) manualEngine.stop();
        if (drawEngine != null) drawEngine.stop();
        if (rescoreEngine != null) rescoreEngine.stop();
        if (situationEngine != null) situationEngine.stop();
        if (openingBook != null) openingBook.close();
        if (moveSoundPool != null) {
            moveSoundPool.release();
            moveSoundPool = null;
        }
        super.onDestroy();
    }

}
