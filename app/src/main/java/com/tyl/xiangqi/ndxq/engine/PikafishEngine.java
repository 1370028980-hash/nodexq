package com.tyl.xiangqi.ndxq.engine;

import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 皮卡鱼 UCI/UCCI 引擎桥接层，V15 修正版。
 *
 * 本版重点修正：
 * 1. 分析模式使用 go infinite，并实时回调 info depth / score / nps / pv，而不是只等 bestmove；
 * 2. 立即出招复用当前无限分析，点击即 stop，并只采用当前局面的最终 bestmove；
 * 3. 调用引擎时发送 “position fen <基础局面> moves <本局历史着法>”，不再只发送最终 FEN；
 * 4. 不在每次搜索前 ucinewgame，避免单局拆棋中频繁清空置换表，尽量保留哈希继承；
 * 5. 只在新局 / 重新打开局面时由 UI 调用 notifyNewGame()。
 */
public class PikafishEngine {
    public interface Callback {
        void onBestMove(String bestMove, String rawInfo);
        void onError(String message);
    }

    public interface AnalysisCallback {
        void onInfo(EngineInfo info, String rawLine);
        void onBestMove(String bestMove, String rawInfo);
        void onError(String message);
    }

    /** V19.8：用于丢弃尚在启动队列中的过期分析请求，防止旧局面迟到重启。 */
    public interface AnalysisStartGuard {
        boolean isValid();
    }

    public static final class EngineInfo {
        public int depth = -1;
        public int selDepth = -1;
        public int multiPv = 1;
        public boolean hasWdl = false;
        public int wdlWin = -1;
        public int wdlDraw = -1;
        public int wdlLoss = -1;
        public boolean hasScore = false;
        public boolean mateScore = false;
        public int score = 0;
        public long nodes = -1;
        public int hashFull = -1;
        public long nps = -1;
        public long timeMs = -1;
        public final List<String> pv = new ArrayList<String>();

        public String firstMove() {
            return pv.isEmpty() ? null : pv.get(0);
        }
    }

    public static final class SearchLimit {
        public static final int MODE_TIME = 0;
        public static final int MODE_DEPTH = 1;
        public static final int MODE_NODES = 2;
        public static final int MODE_COMBINED = 3;
        public final int mode;
        public final int value;
        public final int depth;
        public final int nodes;
        public final int moveTimeMs;
        private SearchLimit(int mode, int value) {
            this.mode = mode;
            this.value = Math.max(1, value);
            this.depth = mode == MODE_DEPTH ? this.value : 0;
            this.nodes = mode == MODE_NODES ? this.value : 0;
            this.moveTimeMs = mode == MODE_TIME ? this.value : 0;
        }
        private SearchLimit(int depth, int nodes, int moveTimeMs) {
            this.mode = MODE_COMBINED;
            this.value = 1;
            this.depth = Math.max(0, depth);
            this.nodes = Math.max(0, nodes);
            this.moveTimeMs = Math.max(0, moveTimeMs);
        }
        public static SearchLimit movetime(int ms) { return new SearchLimit(MODE_TIME, Math.max(50, ms)); }
        public static SearchLimit depth(int depth) { return new SearchLimit(MODE_DEPTH, depth); }
        public static SearchLimit nodes(int nodes) { return new SearchLimit(MODE_NODES, nodes); }
        public static SearchLimit combined(int depth, int nodes, int moveTimeMs) {
            if (depth <= 0 && nodes <= 0 && moveTimeMs <= 0) return movetime(1000);
            return new SearchLimit(depth, nodes, moveTimeMs);
        }
        public String goCommand() {
            if (mode == MODE_COMBINED) return combinedGoCommand("go");
            if (mode == MODE_DEPTH) return "go depth " + value;
            if (mode == MODE_NODES) return "go nodes " + value;
            return "go movetime " + value;
        }
        public long timeoutMs() {
            if (mode == MODE_COMBINED) {
                long timeBound = moveTimeMs > 0 ? moveTimeMs + 5000L : 0L;
                long nodeBound = nodes > 0 ? Math.max(10000L, nodes / 20L + 8000L) : 0L;
                long depthBound = depth > 0 ? 45000L : 0L;
                return Math.max(8000L, Math.max(timeBound, Math.max(nodeBound, depthBound)));
            }
            if (mode == MODE_DEPTH) return 45000L;
            if (mode == MODE_NODES) return Math.max(10000L, value / 20L + 8000L);
            return Math.max(8000L, value + 5000L);
        }
        public String display() {
            if (mode == MODE_COMBINED) return combinedGoCommand("").trim();
            if (mode == MODE_DEPTH) return "depth=" + value;
            if (mode == MODE_NODES) return "nodes=" + value;
            return "movetime=" + value + "ms";
        }
        private String combinedGoCommand(String prefix) {
            StringBuilder sb = new StringBuilder(prefix);
            if (depth > 0) sb.append(" depth ").append(depth);
            if (nodes > 0) sb.append(" nodes ").append(nodes);
            if (moveTimeMs > 0) sb.append(" movetime ").append(moveTimeMs);
            return sb.toString();
        }
    }

    public static final class EngineOption {
        public String name = "";
        public String type = "";
        public String defaultValue = "";
        public String currentValue = "";
        public int min = Integer.MIN_VALUE;
        public int max = Integer.MAX_VALUE;
        public final List<String> vars = new ArrayList<String>();
        public boolean isButton() { return "button".equalsIgnoreCase(type); }
        public boolean isCheck() { return "check".equalsIgnoreCase(type); }
        public boolean isSpin() { return "spin".equalsIgnoreCase(type); }
        public boolean isCombo() { return "combo".equalsIgnoreCase(type); }
    }

    public interface OptionsCallback {
        void onOptions(List<EngineOption> options, String rawUciOptions);
        void onError(String message);
    }

    public interface EngineScanCallback {
        void onComplete(List<EngineDescriptor> engines, String summary);
        void onError(String message);
    }

    public interface EngineDeleteCallback {
        void onComplete(String message);
        void onError(String message);
    }

    /** 可切换引擎的持久化描述；内置引擎 path 为空。 */
    public static final class EngineDescriptor {
        public final String name;
        public final String path;
        public final String protocol;
        public final boolean bundled;

        public EngineDescriptor(String name, String path, String protocol, boolean bundled) {
            this.name = name == null || name.trim().length() == 0 ? "未命名引擎" : name.trim();
            this.path = path == null ? "" : path;
            this.protocol = EngineProbe.PROTOCOL_UCCI.equalsIgnoreCase(protocol)
                    ? EngineProbe.PROTOCOL_UCCI : EngineProbe.PROTOCOL_UCI;
            this.bundled = bundled;
        }

        public String stableKey() {
            return bundled ? "@bundled" : path + "\u001f" + protocol;
        }

        public String displayLabel() {
            return name + "  [" + protocol + "]" + (bundled ? "（内置）" : "");
        }
    }

    private static final String PREFS = "pikafish_engine_options";
    private static final String PREF_SELECTED_ENGINE_PATH = "__selected_engine_path";
    private static final String PREF_SELECTED_ENGINE_PROTOCOL = "__selected_engine_protocol";
    private static final String PREF_IMPORTED_ENGINES = "__imported_engines";
    private static final String PREF_LAST_ENGINE_FOLDER = "__last_engine_folder";
    /** EvalFile 必须按引擎隔离，不能与 Threads/Hash 一样跨引擎共用。 */
    private static final String PREF_ENGINE_EVAL_PREFIX = "__engine_eval_file_";
    private static final String OPTION_EVAL_FILE = "EvalFile";
    private static final int MAX_ENGINE_CANDIDATES = 32;
    private static final int MAX_SAF_FILES = 256;
    private static final long MAX_ENGINE_FILE_BYTES = 512L * 1024L * 1024L;
    private static final long MAX_ENGINE_TOTAL_BYTES = 1024L * 1024L * 1024L;
    private static final long MAX_SAF_SIDECAR_FILE_BYTES = 256L * 1024L * 1024L;
    private static final long MAX_EVAL_NETWORK_FILE_BYTES = 512L * 1024L * 1024L;
    private static final long MAX_UNKNOWN_SIDECAR_BYTES = 16L * 1024L * 1024L;
    private static final long MAX_SAF_SIDECAR_TOTAL_BYTES = 512L * 1024L * 1024L;
    private static final String ENGINE_ARMV8 = "libpikafish-armv8.so";
    private static final String ENGINE_ARMV8_DOTPROD = "libpikafish-armv8-dotprod.so";
    /** Android 安装目录中的规范 NNUE 名称；必须以 lib 开头才可稳定按 jniLib 提取。 */
    private static final String NNUE_FILE = "libpikafish.nnue.so";
    /** 兼容旧源码包中使用的非规范名称。构建时会重命名为 NNUE_FILE。 */
    private static final String LEGACY_NNUE_FILE = "pikafish.nnue.so";
    private static final String ENGINE_DEFAULT_NNUE_FILE = "pikafish.nnue";
    /** Duffish 2.2 的默认网络名；当前 libduf.so 已把该网络完整嵌入二进制。 */
    private static final String DUF_NNUE_OPTION = "xiangqi-agg20260512.nnue";
    /** 仅用于诊断旧安装残留；V16.1 不再打包或下发这两个外置文件。 */
    private static final String DUF_NNUE_FILE = "libxiangqi-agg20260512.nnue.so";
    private static final String LEGACY_DUF_NNUE_FILE = "xiangqi-agg20260512.nnue.so";
    private static final String STATIC_EXEC_LOADER = "libndxq-static-exec.so";
    private static final String VIRTUAL_ENGINE_HCE = "HCE";
    private static final String VIRTUAL_ENGINE_DUF = "duf";
    private static final String VIRTUAL_ENGINE_131 = "131";

    private final Context context;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final BlockingQueue<String> lineQueue = new LinkedBlockingQueue<String>();
    private final Object ioLock = new Object();

    private volatile Process process;
    private volatile BufferedWriter writer;
    private volatile Thread readerThread;
    /** 每次启动/停止递增，阻止旧 stdout 线程把迟到 EOF 写入新进程队列。 */
    private volatile long processGeneration;
    private volatile Thread analysisThread;
    /**
     * 用户显式 stop 的代数。startAnalysis 的“等待上一条搜索结束”属于内部 stop，
     * 不递增该值；这样可在取消发生于启动窗口时阻止迟到的分析重新启动。
     */
    private final AtomicLong analysisStopGeneration = new AtomicLong();
    /**
     * 限时实战搜索的代数。电脑执红/黑在用户点击立即出招或变招时，
     * 必须让已经排队或正在读取 stdout 的旧任务立即失效，不能只停止 go infinite。
     */
    private final AtomicLong searchGeneration = new AtomicLong();
    private volatile boolean uciReady;
    private volatile boolean closed;
    private volatile boolean analysisRunning;
    /** go 已发出但尚未收到 bestmove/nobestmove；用于 stop 后严格等待引擎回到空闲态。 */
    private volatile boolean analysisSearchActive;
    /** 当前分析搜索真正发送 go 的时刻，供快速出招判断最低思考时间。 */
    private volatile long analysisSearchStartedAtMs;
    /** 区分“立即出招”主动 stop 与普通停止/兼容模式自然结束。 */
    private volatile boolean analysisBestMoveRequested;
    /** 当前限时实战搜索的状态；用于电脑执红/黑的立即出招。 */
    private volatile boolean timedSearchActive;
    private volatile long timedSearchGeneration;
    private volatile boolean timedSearchStopRequested;
    private volatile long timedSearchStopRequestedAtMs;
    private volatile Thread timedSearchThread;
    private volatile Process timedSearchProcess;
    private static final long TIMED_SEARCH_STOP_DRAIN_TIMEOUT_MS = 1500L;
    /**
     * V20.0：普通停止不能直接让 stdout 读取循环退出。必须持续读取到旧搜索的
     * bestmove/nobestmove，才能安全发送下一局面的 position/go，避免迟到 bestmove 串台。
     */
    private final Object analysisStateLock = new Object();
    private volatile boolean analysisStopRequested;
    private volatile long analysisStopRequestedAtMs;
    private static final long ANALYSIS_STOP_DRAIN_TIMEOUT_MS = 1500L;
    private volatile boolean newGamePending = true;
    private String lastUciOptions = "";
    private volatile String activeProtocol = EngineProbe.PROTOCOL_UCI;
    private volatile boolean ucciUsesMillisec;
    private volatile boolean readyCommandSupported = true;
    /** V10 调试：记录最近一次实际发送的 setoption EvalFile 命令文本。null=尚未发送过。 */
    private String lastSentEvalFileCmd = null;
    private volatile String activeLaunchMode = "";
    /** 当前进程实际下发的 EvalFile；空串表示沿用引擎默认值或当前引擎不提供该选项。 */
    private volatile String activeEvalFile = "";
    private volatile EngineDescriptor activeEngine;
    private volatile Thread engineScanThread;
    private final LinkedHashMap<String, EngineOption> parsedOptions = new LinkedHashMap<String, EngineOption>();
    private final LinkedHashMap<String, String> pendingOptions = new LinkedHashMap<String, String>();
    /** 仅对当前 PikafishEngine 实例生效，不写入 SharedPreferences。 */
    private final LinkedHashMap<String, String> sessionOptions = new LinkedHashMap<String, String>();
    /** 专用人机版使用的内置引擎虚位置；为空时沿用旧版选择逻辑。 */
    private volatile String virtualEngineSlot = "";

    public PikafishEngine(Context context) {
        this(context, true);
    }

    /**
     * @param loadPersistentOptions 是否读取旧版全局 UCI 参数。专用人机版传 false，
     *                              由每个引擎实例的 sessionOptions 隔离参数，
     *                              避免“分析引擎设置”污染固定难度引擎。
     */
    public PikafishEngine(Context context, boolean loadPersistentOptions) {
        this.context = context.getApplicationContext();
        try {
            ApplicationInfo info = this.context.getApplicationInfo();
            File nativeDir = info == null || info.nativeLibraryDir == null
                    ? null : new File(info.nativeLibraryDir);
            EngineProbe.configureStaticExecLoader(nativeDir == null
                    ? null : new File(nativeDir, STATIC_EXEC_LOADER));
        } catch (Exception ignored) {
            EngineProbe.configureStaticExecLoader(null);
        }
        // 外置引擎首次加载采用保守资源值，避免 1GB Hash 在手机上直接触发 OOM；用户仍可在参数页调高。
        pendingOptions.put("Threads", "1");
        pendingOptions.put("Hash", "64");
        pendingOptions.put("UCI_ShowWDL", "true");
        pendingOptions.put("MultiPV", "1");
        pendingOptions.put("Repetition Rule", "SkyRule");
        // 读取上次在“引擎选项”里保存的全部 UCI 参数，避免重启应用后丢失。
        if (loadPersistentOptions) {
            try {
                SharedPreferences sp = this.context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
                for (Map.Entry<String, ?> e : sp.getAll().entrySet()) {
                    // 历史版本把 EvalFile 当成全局参数保存，切换外部引擎后会继续指向
                    // nativeLibraryDir/libpikafish.nnue.so。这里不再加载该旧值；EvalFile
                    // 在握手后按当前引擎工作目录重新解析，并使用独立的每引擎偏好键。
                    if (isReservedPreference(e.getKey()) || isEvalFileOption(e.getKey())) continue;
                    Object v = e.getValue();
                    if (v instanceof String) pendingOptions.put(e.getKey(), String.valueOf(v));
                }
            } catch (Exception ignored) {}
        }
    }

    /**
     * 安全提交引擎串行任务。系统权限页、录屏授权页和文件选择器都会改变
     * Activity 生命周期；任何任务提交都不能让 RejectedExecutionException
     * 冒泡到主线程。
     */
    private boolean submitEngineTask(Runnable task) {
        if (task == null) return false;
        try {
            if (executor.isShutdown() || executor.isTerminated()) return false;
            executor.execute(task);
            return true;
        } catch (RejectedExecutionException e) {
            return false;
        }
    }

    /** V4: 快速检查引擎 executor 是否仍在工作。 */
    public boolean tryPing() {
        return !executor.isShutdown() && !executor.isTerminated();
    }

    /** V4: 静默停止引擎，不抛异常。用于安全替换实例前的清理。 */
    public void stopQuietly() {
        try { stopAnalysis(); } catch (Exception ignored) {}
        try { stop(); } catch (Exception ignored) {}
    }

    /**
     * 选择专用人机版的内置引擎虚位置。引擎文件应位于 nativeLibraryDir：
     * HCE -> libHCE.so，duf -> libduf.so，131 -> lib131.so。
     * 该选择只保存在当前实例内，不影响其他 PikafishEngine 实例。
     */
    public synchronized void setVirtualEngineSlot(String slot) {
        String normalized = normalizeVirtualEngineSlot(slot);
        if (normalized.equals(virtualEngineSlot)) return;
        stopAnalysis();
        virtualEngineSlot = normalized;
        newGamePending = true;
    }

    public synchronized String getVirtualEngineSlot() {
        return virtualEngineSlot;
    }

    public synchronized void clearSessionOptions() {
        if (sessionOptions.isEmpty()) return;
        sessionOptions.clear();
        uciReady = false;
    }

    public synchronized void setSessionOption(String name, String value) {
        if (name == null || name.trim().length() == 0) return;
        String key = name.trim();
        if (usesEmbeddedDufNetwork() && isEvalFileOption(key)) {
            removeSessionOptionIgnoreCase(OPTION_EVAL_FILE);
            removePendingOptionIgnoreCase(OPTION_EVAL_FILE);
            activeEvalFile = "";
            lastSentEvalFileCmd = "(duf 使用 libduf.so 内嵌 NNUE，未发送 EvalFile)";
            uciReady = false;
            return;
        }
        String next = value == null ? "" : value.trim();
        String previous = sessionOptions.put(key, next);
        if (previous == null || !previous.equals(next)) uciReady = false;
    }


    /**
     * 设置仅属于当前实例的 UCI 参数，并在引擎已经启动时立即下发。
     * 与 setOption 不同，本方法不会写入全局 SharedPreferences。
     */
    public synchronized void setLocalOption(String name, String value) {
        if (name == null || name.trim().length() == 0) return;
        String requested = name.trim();
        String actual = findParsedOptionName(requested);
        String key = actual == null ? requested : actual;
        EngineOption option = actual == null ? null : parsedOptions.get(actual);
        if (usesEmbeddedDufNetwork() && isEvalFileOption(key)) {
            removeSessionOptionIgnoreCase(OPTION_EVAL_FILE);
            removePendingOptionIgnoreCase(OPTION_EVAL_FILE);
            activeEvalFile = "";
            lastSentEvalFileCmd = "(duf 使用 libduf.so 内嵌 NNUE，忽略外置 EvalFile)";
            if (option != null) option.currentValue = option.defaultValue;
            uciReady = false;
            return;
        }
        String safe = normalizeOptionValue(option, value == null ? "" : value.trim());
        if (isEvalFileOption(key)) {
            // 设置页通常显示引擎默认相对名 pikafish.nnue。Android 引擎工作目录并不保证
            // 能按该相对名访问网络文件，因此保存/即时下发前先解析为安装目录中的绝对路径。
            safe = resolveEvalFileValueForCurrentEngine(safe);
            removeSessionOptionIgnoreCase(OPTION_EVAL_FILE);
            sessionOptions.put(key, safe);
            activeEvalFile = safe;
        } else if (option == null || !option.isButton()) {
            sessionOptions.put(key, safe);
        }
        if (option != null) option.currentValue = safe;
        if ((analysisRunning || analysisSearchActive) && uciReady) {
            // 搜索过程中不直接插入 setoption；让下一次启动按 sessionOptions 重建进程。
            uciReady = false;
            return;
        }
        if (uciReady && writer != null) {
            try {
                String cmd = optionCommand(key, safe);
                send(cmd);
                if (isEvalFileOption(key)) lastSentEvalFileCmd = cmd;
            } catch (Exception ignored) {}
        }
    }

    /** 执行当前实例的 UCI button 选项，不持久化到全局设置。 */
    public void executeLocalButton(final String name, final Callback callback) {
        Runnable task = new Runnable() {
            @Override public void run() {
                try {
                    stopAnalysisAndWait(1600L);
                    ensureStarted();
                    String actual = findParsedOptionName(name == null ? "" : name.trim());
                    if (actual == null) throw new IOException("引擎未声明选项：" + name);
                    EngineOption option = parsedOptions.get(actual);
                    if (option == null || !option.isButton()) throw new IOException("该选项不是 button：" + actual);
                    send(optionCommand(actual, ""));
                    syncReady(5000L);
                    if (callback != null) callback.onBestMove("", "已执行 " + actual);
                } catch (Exception e) {
                    if (callback != null) callback.onError("执行引擎选项失败：" + e.getMessage());
                }
            }
        };
        if (!submitEngineTask(task) && callback != null) callback.onError("引擎任务队列已关闭。");
    }

    public boolean hasVirtualEngine(String slot) {
        File file = resolveVirtualEngineBinary(normalizeVirtualEngineSlot(slot));
        return file != null && file.isFile();
    }

    /** 当前实例最终解析到的引擎文件是否真实存在。 */
    public boolean hasUsableEngine() {
        EngineDescriptor descriptor = resolveSelectedEngine();
        if (descriptor == null) return false;
        if (descriptor.bundled) return resolveBundledEngineBinary() != null;
        return descriptor.path != null && descriptor.path.length() > 0
                && new File(descriptor.path).isFile();
    }

    public String getInstallHint() {
        EngineDescriptor selected = resolveSelectedEngine();
        if (virtualEngineSlot != null && virtualEngineSlot.length() > 0) {
            File expected = resolveVirtualEngineBinary(virtualEngineSlot);
            return "内置引擎虚位置=" + virtualEngineSlot + "；文件="
                    + (expected == null ? "未解析" : expected.getAbsolutePath())
                    + "；状态=" + (expected != null && expected.isFile() ? "已就绪" : "等待下次编译内置") + "。";
        }
        if (selected != null && !selected.bundled) {
            File f = new File(selected.path);
            return "当前外部引擎：" + selected.displayLabel() + "；文件=" + selected.path
                    + "；状态=" + (f.exists() ? "存在" : "已丢失")
                    + "；启动方式=" + (activeLaunchMode.length() == 0
                    ? "自动选择（动态 linker64 / 静态 ELF 映射 / 直接 exec）" : activeLaunchMode)
                    + "；EvalFile=" + (activeEvalFile.length() == 0 ? "引擎默认/未匹配" : activeEvalFile)
                    + "。动态 ARM64 PIE 会尝试系统 linker64；静态 ARM64 ELF 会尝试 APK 内置映射器；系统 execveat 被拒绝时自动改走 PT_LOAD 用户态映射。";
        }
        File dir = getNativeLibraryDir();
        File safe = new File(dir, ENGINE_ARMV8);
        File dot = new File(dir, ENGINE_ARMV8_DOTPROD);
        File nnue = new File(dir, NNUE_FILE);
        if (safe.exists() || dot.exists()) {
            return "内置皮卡鱼：已在 nativeLibraryDir 发现 "
                    + (safe.exists() ? ENGINE_ARMV8 : ENGINE_ARMV8_DOTPROD)
                    + "；NNUE=" + (nnue.exists() ? "已发现" : "未发现")
                    + "；目录=" + dir.getAbsolutePath();
        }
        return "未发现可用引擎。请把 V10.0 所需的 libHCE.so、libduf.so、lib131.so "
                + "放入 app/src/main/pikafish/arm64-v8a/ 后重新编译。";
    }

    public boolean hasBundledEngine() {
        return resolveBundledEngineBinary() != null;
    }

    public String getCurrentEngineLabel() {
        EngineDescriptor d = activeEngine != null ? activeEngine : resolveSelectedEngine();
        return d == null ? "未选择可用引擎" : d.displayLabel();
    }

    public String getCurrentEngineKey() {
        EngineDescriptor d = activeEngine != null ? activeEngine : resolveSelectedEngine();
        return d == null ? "" : d.stableKey();
    }

    public String getLastEngineFolder() {
        try {
            return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .getString(PREF_LAST_ENGINE_FOLDER, "");
        } catch (Exception ignored) {
            return "";
        }
    }

    public synchronized List<EngineDescriptor> getAvailableEngines() {
        ArrayList<EngineDescriptor> out = new ArrayList<EngineDescriptor>();
        File bundled = resolveBundledEngineBinary();
        if (bundled != null) out.add(new EngineDescriptor("皮卡鱼", "", EngineProbe.PROTOCOL_UCI, true));
        out.addAll(loadImportedEngines());
        Collections.sort(out, new Comparator<EngineDescriptor>() {
            @Override public int compare(EngineDescriptor a, EngineDescriptor b) {
                if (a.bundled != b.bundled) return a.bundled ? -1 : 1;
                int byName = a.name.compareToIgnoreCase(b.name);
                return byName != 0 ? byName : a.path.compareToIgnoreCase(b.path);
            }
        });
        return out;
    }

    /**
     * 扫描目录第一层：先用 ELF 头过滤 arm64，再按 UCCI -> UCI 实际握手。
     * 这样不会递归遍历整个存储，也不会仅凭扩展名误判。
     */
    public synchronized void scanExternalFolder(final File folder, final EngineScanCallback callback) {
        if (engineScanThread != null && engineScanThread.isAlive()) {
            if (callback != null) callback.onError("已有引擎目录正在扫描，请等待当前扫描完成。");
            return;
        }
        engineScanThread = new Thread(new Runnable() {
            @Override public void run() {
                try {
                    if (folder == null || !folder.isDirectory()) {
                        if (callback != null) callback.onError("所选路径不是可读取文件夹。");
                        return;
                    }
                    File[] listed = folder.listFiles();
                    if (listed == null) {
                        if (callback != null) callback.onError("无法读取文件夹：" + folder.getAbsolutePath()
                                + "。请改用系统文件夹选择器重新授权。");
                        return;
                    }
                    ArrayList<File> entries = new ArrayList<File>();
                    for (File file : listed) if (file != null && file.isFile()) entries.add(file);
                    Collections.sort(entries, new Comparator<File>() {
                        @Override public int compare(File a, File b) {
                            return a.getName().compareToIgnoreCase(b.getName());
                        }
                    });
                    if (entries.size() > MAX_SAF_FILES) {
                        entries = new ArrayList<File>(entries.subList(0, MAX_SAF_FILES));
                    }
                    ArrayList<File> arm64Files = new ArrayList<File>();
                    ArrayList<File> candidates = new ArrayList<File>();
                    for (File file : entries) {
                        if (!EngineProbe.isArm64Elf(file)) continue;
                        arm64Files.add(file);
                        // ARM64 依赖 .so 必须在任何引擎握手前先暂存，不能当成引擎探测后删除。
                        if (!EngineProbe.isArm64DependencyLibrary(file)) candidates.add(file);
                    }
                    int totalArm64 = arm64Files.size();
                    int totalCandidates = candidates.size();
                    HashSet<String> allCandidatePaths = new HashSet<String>();
                    for (File c : candidates) allCandidatePaths.add(c.getAbsolutePath());
                    if (candidates.size() > MAX_ENGINE_CANDIDATES) {
                        candidates = new ArrayList<File>(candidates.subList(0, MAX_ENGINE_CANDIDATES));
                    }

                    String sourcePath;
                    try { sourcePath = folder.getCanonicalPath(); }
                    catch (IOException e) { sourcePath = folder.getAbsolutePath(); }
                    File stage = new File(context.getDir("external_engines", Context.MODE_PRIVATE),
                            shortHash("raw:" + sourcePath));
                    if (!stage.exists() && !stage.mkdirs()) throw new IOException("无法创建外部引擎暂存目录");
                    HashSet<String> keepNames = new HashSet<String>();
                    long sidecarBytes = 0L;
                    int sidecars = 0;
                    int evalSidecars = 0;
                    StringBuilder details = new StringBuilder();
                    for (File entry : entries) {
                        if (allCandidatePaths.contains(entry.getAbsolutePath())) continue;
                        long fileLimit = sidecarFileLimit(entry.getName());
                        long size = entry.length();
                        if (size < 0 || size > fileLimit
                                || sidecarBytes + size > MAX_SAF_SIDECAR_TOTAL_BYTES) continue;
                        String name = safeFileName(entry.getName(), entry.getAbsolutePath(), keepNames);
                        File dst = new File(stage, name);
                        try {
                            copyLocalFile(entry, dst, fileLimit);
                            long copied = Math.max(0L, dst.length());
                            if (sidecarBytes + copied > MAX_SAF_SIDECAR_TOTAL_BYTES) {
                                dst.delete();
                                continue;
                            }
                            keepNames.add(name);
                            sidecarBytes += copied;
                            sidecars++;
                            if (isStrongEvalNetworkName(entry.getName())) evalSidecars++;
                        } catch (Exception e) {
                            dst.delete();
                            if (details.length() < 1800) details.append(entry.getName())
                                    .append("：旁文件暂存失败，已跳过：").append(e.getMessage()).append('\n');
                        }
                    }

                    int failed = 0;
                    long engineBytes = 0L;
                    ArrayList<EngineDescriptor> discovered = new ArrayList<EngineDescriptor>();
                    for (File file : candidates) {
                        if (Thread.currentThread().isInterrupted()) return;
                        long size = file.length();
                        if (size < 0 || size > MAX_ENGINE_FILE_BYTES
                                || engineBytes + size > MAX_ENGINE_TOTAL_BYTES) {
                            failed++;
                            if (details.length() < 1800) details.append(file.getName())
                                    .append("：文件过大，超过暂存资源限制。\n");
                            continue;
                        }
                        String name = safeFileName(file.getName(), file.getAbsolutePath(), keepNames);
                        File local = new File(stage, name);
                        try {
                            copyLocalFile(file, local, MAX_ENGINE_FILE_BYTES);
                            try { local.setExecutable(true, false); } catch (SecurityException ignored) {}
                            EngineProbe.Result result = EngineProbe.probe(local, stage, 8000L);
                            if (Thread.currentThread().isInterrupted()) return;
                            if (result.success) {
                                keepNames.add(name);
                                engineBytes += Math.max(0L, local.length());
                                String engineName = result.engineName.length() == 0 ? file.getName() : result.engineName;
                                discovered.add(new EngineDescriptor(engineName, local.getAbsolutePath(), result.protocol, false));
                            } else {
                                local.delete();
                                failed++;
                                if (details.length() < 1800) details.append(file.getName()).append("：")
                                        .append(result.message).append('\n');
                            }
                        } catch (Exception e) {
                            local.delete();
                            failed++;
                            if (details.length() < 1800) details.append(file.getName()).append("：暂存失败：")
                                    .append(e.getMessage()).append('\n');
                        }
                    }
                    if (discovered.isEmpty()) keepNames.clear();
                    pruneStageDirectory(stage, keepNames);
                    // 清理 V58 直接保存外置路径的旧记录，再保存私有暂存路径。
                    replaceImportedEnginesForFolder(folder, Collections.<EngineDescriptor>emptyList());
                    replaceImportedEnginesForFolder(stage, discovered);
                    try {
                        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                                .putString(PREF_LAST_ENGINE_FOLDER, folder.getAbsolutePath()).apply();
                    } catch (Exception ignored) {}
                    String summary = "扫描完成（私有暂存兼容模式）：发现 armv8 ELF " + totalArm64
                            + " 个，其中可探测引擎 " + totalCandidates + " 个，本次检测 " + candidates.size()
                            + " 个；可用 " + discovered.size()
                            + " 个，不可用 " + failed + " 个。旁文件暂存 " + sidecars
                            + " 个（NNUE/网络 " + evalSidecars + " 个）。";
                    if (listed.length > entries.size()) summary += " 为限制资源占用，本次只检查前 " + entries.size() + " 个文件。";
                    if (totalCandidates > candidates.size()) summary += " 其余 ARMv8 候选未检测。";
                    if (arm64Files.isEmpty()) summary += " 未发现 64 位 AArch64 ELF 文件。";
                    if (details.length() > 0) summary += "\n\n不可用详情：\n" + details;
                    if (callback != null) callback.onComplete(getAvailableEngines(), summary);
                } catch (Exception e) {
                    if (callback != null) callback.onError("扫描引擎目录失败：" + e.getClass().getSimpleName()
                            + ": " + String.valueOf(e.getMessage()));
                } finally {
                    synchronized (PikafishEngine.this) {
                        if (engineScanThread == Thread.currentThread()) engineScanThread = null;
                    }
                }
            }
        }, "External-engine-scan");
        engineScanThread.start();
    }


    /**
     * SAF 回退扫描：当 Android 11+ 只授予 content:// 树权限、原始路径不可读时，
     * 仅把 ARM64 候选和常见 NNUE/配置旁文件暂存到应用私有目录，再由 linker64 探测。
     */
    public synchronized void scanExternalTree(final Uri treeUri, final EngineScanCallback callback) {
        if (engineScanThread != null && engineScanThread.isAlive()) {
            if (callback != null) callback.onError("已有引擎目录正在扫描，请等待当前扫描完成。");
            return;
        }
        engineScanThread = new Thread(new Runnable() {
            @Override public void run() {
                try {
                    if (treeUri == null) {
                        if (callback != null) callback.onError("未取得文件夹访问地址。");
                        return;
                    }
                    List<SafEntry> entries = listSafFiles(treeUri);
                    ArrayList<SafEntry> arm64Entries = new ArrayList<SafEntry>();
                    ContentResolver resolver = context.getContentResolver();
                    for (SafEntry entry : entries) {
                        if (Thread.currentThread().isInterrupted()) return;
                        try (InputStream in = resolver.openInputStream(entry.uri)) {
                            if (EngineProbe.isArm64Elf(in)) arm64Entries.add(entry);
                        } catch (Exception ignored) {}
                    }
                    Collections.sort(arm64Entries, new Comparator<SafEntry>() {
                        @Override public int compare(SafEntry a, SafEntry b) {
                            return a.name.compareToIgnoreCase(b.name);
                        }
                    });
                    int totalArm64 = arm64Entries.size();
                    HashSet<String> arm64Ids = new HashSet<String>();
                    for (SafEntry c : arm64Entries) arm64Ids.add(c.documentId);

                    File stage = new File(context.getDir("external_engines", Context.MODE_PRIVATE), shortHash(treeUri.toString()));
                    if (!stage.exists() && !stage.mkdirs()) throw new IOException("无法创建外部引擎暂存目录");
                    HashSet<String> usedNames = new HashSet<String>();
                    HashSet<String> keepNames = new HashSet<String>();
                    long sidecarBytes = 0L;
                    int sidecars = 0;
                    int evalSidecars = 0;
                    StringBuilder details = new StringBuilder();
                    for (SafEntry entry : entries) {
                        if (arm64Ids.contains(entry.documentId)) continue;
                        long fileLimit = sidecarFileLimit(entry.name);
                        if (entry.size > fileLimit) continue;
                        if (entry.size > 0 && sidecarBytes + entry.size > MAX_SAF_SIDECAR_TOTAL_BYTES) continue;
                        String name = safeFileName(entry.name, entry.documentId, usedNames);
                        usedNames.add(name);
                        File dst = new File(stage, name);
                        try {
                            copySafFile(entry, dst, fileLimit);
                            long copied = Math.max(0L, dst.length());
                            if (sidecarBytes + copied > MAX_SAF_SIDECAR_TOTAL_BYTES) {
                                dst.delete();
                                continue;
                            }
                            keepNames.add(name);
                            sidecarBytes += copied;
                            sidecars++;
                            if (isStrongEvalNetworkName(entry.name)) evalSidecars++;
                        } catch (Exception e) {
                            dst.delete();
                            if (details.length() < 1800) details.append(entry.name)
                                    .append("：旁文件暂存失败，已跳过：").append(e.getMessage()).append('\n');
                        }
                    }

                    int failed = 0;
                    long engineBytes = 0L;
                    ArrayList<StagedSafCandidate> stagedCandidates = new ArrayList<StagedSafCandidate>();
                    // 先暂存并分类全部 ARM64 文件，确保实际引擎握手时同目录依赖 .so 已经存在。
                    for (SafEntry entry : arm64Entries) {
                        if (Thread.currentThread().isInterrupted()) return;
                        if (entry.size > MAX_ENGINE_FILE_BYTES) {
                            failed++;
                            if (details.length() < 1800) details.append(entry.name)
                                    .append("：文件过大，超过暂存资源限制。\n");
                            continue;
                        }
                        String name = safeFileName(entry.name, entry.documentId, usedNames);
                        usedNames.add(name);
                        File local = new File(stage, name);
                        try {
                            copySafFile(entry, local, MAX_ENGINE_FILE_BYTES);
                            long copied = Math.max(0L, local.length());
                            if (EngineProbe.isArm64DependencyLibrary(local)) {
                                if (copied > MAX_SAF_SIDECAR_FILE_BYTES
                                        || sidecarBytes + copied > MAX_SAF_SIDECAR_TOTAL_BYTES) {
                                    local.delete();
                                    if (details.length() < 1800) details.append(entry.name)
                                            .append("：ARM64 依赖库超过旁文件资源限制，已跳过。\n");
                                    continue;
                                }
                                keepNames.add(name);
                                sidecarBytes += copied;
                                sidecars++;
                                continue;
                            }
                            if (engineBytes + copied > MAX_ENGINE_TOTAL_BYTES) {
                                local.delete();
                                failed++;
                                if (details.length() < 1800) details.append(entry.name)
                                        .append("：候选引擎累计超过暂存资源限制。\n");
                                continue;
                            }
                            engineBytes += copied;
                            stagedCandidates.add(new StagedSafCandidate(entry, local, name));
                        } catch (Exception e) {
                            local.delete();
                            failed++;
                            if (details.length() < 1800) details.append(entry.name).append("：暂存失败：")
                                    .append(e.getMessage()).append('\n');
                        }
                    }

                    int totalCandidates = stagedCandidates.size();
                    List<StagedSafCandidate> toProbe = stagedCandidates;
                    if (toProbe.size() > MAX_ENGINE_CANDIDATES) {
                        toProbe = new ArrayList<StagedSafCandidate>(toProbe.subList(0, MAX_ENGINE_CANDIDATES));
                    }
                    ArrayList<EngineDescriptor> discovered = new ArrayList<EngineDescriptor>();
                    for (StagedSafCandidate candidate : toProbe) {
                        if (Thread.currentThread().isInterrupted()) return;
                        try {
                            try { candidate.file.setExecutable(true, false); } catch (SecurityException ignored) {}
                            EngineProbe.Result result = EngineProbe.probe(candidate.file, stage, 8000L);
                            if (result.success) {
                                keepNames.add(candidate.stagedName);
                                String engineName = result.engineName.length() == 0
                                        ? candidate.entry.name : result.engineName;
                                discovered.add(new EngineDescriptor(engineName, candidate.file.getAbsolutePath(), result.protocol, false));
                            } else {
                                candidate.file.delete();
                                failed++;
                                if (details.length() < 1800) details.append(candidate.entry.name).append("：")
                                        .append(result.message).append('\n');
                            }
                        } catch (Exception e) {
                            candidate.file.delete();
                            failed++;
                            if (details.length() < 1800) details.append(candidate.entry.name).append("：探测失败：")
                                    .append(e.getMessage()).append('\n');
                        }
                    }
                    if (discovered.isEmpty()) keepNames.clear();
                    pruneStageDirectory(stage, keepNames);
                    replaceImportedEnginesForFolder(stage, discovered);
                    try {
                        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                                .putString(PREF_LAST_ENGINE_FOLDER, treeUri.toString()).apply();
                    } catch (Exception ignored) {}
                    String summary = "扫描完成（SAF 兼容模式）：发现 armv8 ELF " + totalArm64
                            + " 个，其中可探测引擎 " + totalCandidates + " 个，本次检测 " + toProbe.size()
                            + " 个；可用 " + discovered.size() + " 个，不可用 " + failed
                            + " 个。旁文件暂存 " + sidecars
                            + " 个（NNUE/网络 " + evalSidecars + " 个）。";
                    if (totalCandidates > toProbe.size()) summary += " 为限制资源占用，其余候选未检测。";
                    if (arm64Entries.isEmpty()) summary += " 未发现 64 位 AArch64 ELF 文件。";
                    if (details.length() > 0) summary += "\n\n不可用详情：\n" + details;
                    if (callback != null) callback.onComplete(getAvailableEngines(), summary);
                } catch (Exception e) {
                    if (callback != null) callback.onError("通过系统文件选择器扫描引擎失败："
                            + e.getClass().getSimpleName() + ": " + String.valueOf(e.getMessage()));
                } finally {
                    synchronized (PikafishEngine.this) {
                        if (engineScanThread == Thread.currentThread()) engineScanThread = null;
                    }
                }
            }
        }, "External-engine-SAF-scan");
        engineScanThread.start();
    }

    private static final class StagedSafCandidate {
        final SafEntry entry;
        final File file;
        final String stagedName;
        StagedSafCandidate(SafEntry entry, File file, String stagedName) {
            this.entry = entry;
            this.file = file;
            this.stagedName = stagedName;
        }
    }

    private static final class SafEntry {
        final String documentId;
        final Uri uri;
        final String name;
        final long size;
        final long lastModified;
        SafEntry(String documentId, Uri uri, String name, long size, long lastModified) {
            this.documentId = documentId == null ? "" : documentId;
            this.uri = uri;
            this.name = name == null || name.length() == 0 ? "engine" : name;
            this.size = size;
            this.lastModified = lastModified;
        }
    }

    private List<SafEntry> listSafFiles(Uri treeUri) throws IOException {
        ArrayList<SafEntry> out = new ArrayList<SafEntry>();
        ContentResolver resolver = context.getContentResolver();
        String rootId = DocumentsContract.getTreeDocumentId(treeUri);
        Uri children = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, rootId);
        String[] projection = new String[]{
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
                DocumentsContract.Document.COLUMN_SIZE,
                DocumentsContract.Document.COLUMN_LAST_MODIFIED
        };
        try (Cursor cursor = resolver.query(children, projection, null, null, null)) {
            if (cursor == null) throw new IOException("文件提供器未返回目录内容");
            int idCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID);
            int nameCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME);
            int mimeCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE);
            int sizeCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE);
            int modifiedCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED);
            while (cursor.moveToNext() && out.size() < MAX_SAF_FILES) {
                String mime = mimeCol >= 0 ? cursor.getString(mimeCol) : "";
                if (DocumentsContract.Document.MIME_TYPE_DIR.equals(mime)) continue;
                String id = idCol >= 0 ? cursor.getString(idCol) : "";
                String name = nameCol >= 0 ? cursor.getString(nameCol) : "engine";
                long size = sizeCol >= 0 && !cursor.isNull(sizeCol) ? cursor.getLong(sizeCol) : -1L;
                long modified = modifiedCol >= 0 && !cursor.isNull(modifiedCol) ? cursor.getLong(modifiedCol) : -1L;
                Uri child = DocumentsContract.buildDocumentUriUsingTree(treeUri, id);
                out.add(new SafEntry(id, child, name, size, modified));
            }
        } catch (SecurityException e) {
            throw new IOException("文件夹读取权限已失效，请重新选择目录", e);
        }
        return out;
    }

    private void copySafFile(SafEntry entry, File dst, long maxBytes) throws IOException {
        if (entry == null || entry.uri == null) throw new IOException("文件地址无效");
        if (entry.size > maxBytes) throw new IOException("文件超过暂存大小限制");
        if (dst.exists() && entry.size >= 0 && dst.length() == entry.size
                && entry.lastModified > 0 && dst.lastModified() == entry.lastModified) return;
        File parent = dst.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) throw new IOException("无法创建暂存目录");
        File tmp = new File(parent, dst.getName() + ".tmp");
        long total = 0L;
        try (InputStream in = context.getContentResolver().openInputStream(entry.uri);
             FileOutputStream out = new FileOutputStream(tmp)) {
            if (in == null) throw new IOException("无法打开 " + entry.name);
            byte[] buffer = new byte[64 * 1024];
            int n;
            while ((n = in.read(buffer)) >= 0) {
                if (n == 0) continue;
                total += n;
                if (total > maxBytes) throw new IOException("文件超过暂存大小限制");
                out.write(buffer, 0, n);
            }
            out.flush();
            try { out.getFD().sync(); } catch (Exception ignored) {}
        } catch (IOException e) {
            tmp.delete();
            throw e;
        }
        if (dst.exists() && !dst.delete()) {
            tmp.delete();
            throw new IOException("无法替换旧暂存文件 " + dst.getName());
        }
        if (!tmp.renameTo(dst)) {
            tmp.delete();
            throw new IOException("暂存文件落盘失败 " + dst.getName());
        }
        if (entry.lastModified > 0) try { dst.setLastModified(entry.lastModified); } catch (Exception ignored) {}
    }

    private void copyLocalFile(File src, File dst, long maxBytes) throws IOException {
        if (src == null || !src.isFile()) throw new IOException("源文件不存在");
        long size = src.length();
        if (size < 0 || size > maxBytes) throw new IOException("文件超过暂存大小限制");
        if (dst.exists() && dst.length() == size && dst.lastModified() == src.lastModified()) return;
        File parent = dst.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) throw new IOException("无法创建暂存目录");
        File tmp = new File(parent, dst.getName() + ".tmp");
        long total = 0L;
        try (FileInputStream in = new FileInputStream(src);
             FileOutputStream out = new FileOutputStream(tmp)) {
            byte[] buffer = new byte[64 * 1024];
            int n;
            while ((n = in.read(buffer)) >= 0) {
                if (n == 0) continue;
                total += n;
                if (total > maxBytes) throw new IOException("文件超过暂存大小限制");
                out.write(buffer, 0, n);
            }
            out.flush();
            try { out.getFD().sync(); } catch (Exception ignored) {}
        } catch (IOException e) {
            tmp.delete();
            throw e;
        }
        if (dst.exists() && !dst.delete()) {
            tmp.delete();
            throw new IOException("无法替换旧暂存文件 " + dst.getName());
        }
        if (!tmp.renameTo(dst)) {
            tmp.delete();
            throw new IOException("暂存文件落盘失败 " + dst.getName());
        }
        try { dst.setLastModified(src.lastModified()); } catch (Exception ignored) {}
    }

    private static long sidecarFileLimit(String name) {
        if (name == null) return MAX_UNKNOWN_SIDECAR_BYTES;
        String n = name.toLowerCase(Locale.ROOT);
        boolean evalNetwork = n.endsWith(".nnue") || n.endsWith(".nnue.so")
                || n.endsWith(".weights") || n.endsWith(".model")
                || n.endsWith(".network") || n.endsWith(".bin");
        if (evalNetwork) return MAX_EVAL_NETWORK_FILE_BYTES;
        boolean known = n.endsWith(".dat") || n.endsWith(".book") || n.endsWith(".obk")
                || n.endsWith(".ini") || n.endsWith(".cfg") || n.endsWith(".conf")
                || n.endsWith(".json") || n.endsWith(".so") || n.endsWith(".lic")
                || n.endsWith(".key") || n.endsWith(".txt");
        // 未知扩展名也可能是许可证或无后缀配置，但只允许小文件，避免复制整个杂项目录。
        return known ? MAX_SAF_SIDECAR_FILE_BYTES : MAX_UNKNOWN_SIDECAR_BYTES;
    }

    private static String safeFileName(String name, String id, Set<String> used) {
        String safe = name == null ? "engine" : name.replace('/', '_').replace('\\', '_').replace('\u0000', '_').trim();
        if (safe.length() == 0) safe = "engine";
        if (safe.length() > 96) safe = safe.substring(0, 96);
        if (used == null || !used.contains(safe)) return safe;
        String suffix = "_" + shortHash(id == null ? safe : id);
        int keep = Math.max(1, 96 - suffix.length());
        return (safe.length() > keep ? safe.substring(0, keep) : safe) + suffix;
    }

    private static String shortHash(String text) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest((text == null ? "" : text).getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(16);
            for (int i = 0; i < 8; i++) sb.append(String.format(Locale.ROOT, "%02x", digest[i] & 0xff));
            return sb.toString();
        } catch (Exception e) {
            return Integer.toHexString(text == null ? 0 : text.hashCode());
        }
    }

    private static void pruneStageDirectory(File stage, Set<String> keepNames) {
        File[] files = stage == null ? null : stage.listFiles();
        if (files == null) return;
        for (File file : files) {
            if (file.isFile() && (keepNames == null || !keepNames.contains(file.getName()))) file.delete();
        }
    }

    public void selectEngine(final EngineDescriptor target, final Callback callback) {
        if (target == null) {
            if (callback != null) callback.onError("未选择引擎。");
            return;
        }
        stopAnalysis();
        Runnable switchTask = new Runnable() {
            @Override public void run() {
                SharedPreferences sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
                String oldPath = sp.getString(PREF_SELECTED_ENGINE_PATH, "");
                String oldProtocol = sp.getString(PREF_SELECTED_ENGINE_PROTOCOL, EngineProbe.PROTOCOL_UCI);
                try {
                    stopAnalysisAndWait(1800L);
                    // 旧引擎完全退出后再启动新引擎，避免两个大 Hash 进程短时并存。
                    hardStop();
                    SharedPreferences.Editor edit = sp.edit();
                    if (target.bundled) {
                        edit.remove(PREF_SELECTED_ENGINE_PATH).remove(PREF_SELECTED_ENGINE_PROTOCOL);
                    } else {
                        edit.putString(PREF_SELECTED_ENGINE_PATH, target.path)
                                .putString(PREF_SELECTED_ENGINE_PROTOCOL, target.protocol);
                    }
                    edit.commit();
                    ensureStarted();
                    newGamePending = true;
                    if (callback != null) callback.onBestMove("", "已切换为 " + getCurrentEngineLabel());
                } catch (Exception e) {
                    hardStop();
                    activeEngine = null;
                    activeProtocol = oldProtocol;
                    SharedPreferences.Editor rollback = sp.edit();
                    if (oldPath == null || oldPath.length() == 0) {
                        rollback.remove(PREF_SELECTED_ENGINE_PATH).remove(PREF_SELECTED_ENGINE_PROTOCOL);
                    } else {
                        rollback.putString(PREF_SELECTED_ENGINE_PATH, oldPath)
                                .putString(PREF_SELECTED_ENGINE_PROTOCOL, oldProtocol);
                    }
                    rollback.commit();
                    if (callback != null) callback.onError("切换失败，已恢复原选择："
                            + e.getClass().getSimpleName() + ": " + e.getMessage());
                }
            }
        };
        try {
            if (executor.isShutdown()) {
                if (callback != null) callback.onError("引擎模块已关闭，请重新进入页面后再切换。");
                return;
            }
            executor.execute(switchTask);
        } catch (RejectedExecutionException e) {
            // Activity 生命周期切换与用户点击恰好重叠时，不让异常冒泡到主线程导致闪退。
            if (callback != null) callback.onError("引擎任务暂不可用，请重新进入页面后再试。");
        }
    }


    /**
     * 删除一个已导入的外部引擎。
     *
     * 安全边界：
     * 1. 内置皮卡鱼永远不可删除；
     * 2. 只物理删除应用私有 external_engines 目录中的暂存文件，旧版本遗留的外部原始路径只移除记录；
     * 3. 删除当前正在使用的外部引擎前先停止搜索并退出进程，随后自动回退到内置引擎（若存在）；
     * 4. 同目录还有其他已导入引擎时只删除当前可执行文件，避免误删共享 NNUE/依赖文件。
     */
    public void deleteEngine(final EngineDescriptor target, final EngineDeleteCallback callback) {
        if (target == null) {
            if (callback != null) callback.onError("未选择要删除的引擎。");
            return;
        }
        if (target.bundled) {
            if (callback != null) callback.onError("默认的皮卡鱼 [UCI] 内置引擎不可删除。");
            return;
        }
        if (target.path == null || target.path.trim().length() == 0) {
            if (callback != null) callback.onError("外部引擎路径为空，无法删除。");
            return;
        }

        Runnable deleteTask = new Runnable() {
            @Override public void run() {
                SharedPreferences sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
                String selectedPath = sp.getString(PREF_SELECTED_ENGINE_PATH, "");
                boolean deletingCurrent = target.path.equals(selectedPath)
                        || (activeEngine != null && !activeEngine.bundled && target.path.equals(activeEngine.path));
                try {
                    if (deletingCurrent) {
                        stopAnalysisAndWait(1800L);
                        hardStop();
                    }

                    Set<String> old = sp.getStringSet(PREF_IMPORTED_ENGINES, Collections.<String>emptySet());
                    HashSet<String> next = old == null ? new HashSet<String>() : new HashSet<String>(old);
                    ArrayList<String> remove = new ArrayList<String>();
                    for (String item : next) {
                        String[] parts = item.split("\u001f", -1);
                        if (parts.length >= 1 && target.path.equals(parts[0])) remove.add(item);
                    }
                    next.removeAll(remove);

                    File targetFile = new File(target.path);
                    boolean managedFileDeleted = false;
                    boolean managedPackageDeleted = false;
                    boolean externalSourcePreserved = false;
                    if (isManagedImportedEngineFile(targetFile)) {
                        File managedRoot = context.getDir("external_engines", Context.MODE_PRIVATE).getCanonicalFile();
                        File parent = targetFile.getCanonicalFile().getParentFile();
                        boolean siblingEngineRemains = hasRegisteredEngineInFolder(next, parent);
                        boolean safePackageFolder = parent != null && !managedRoot.equals(parent);
                        if (!siblingEngineRemains && safePackageFolder) {
                            managedPackageDeleted = deleteRecursively(parent);
                            if (!managedPackageDeleted && parent.exists()) {
                                throw new IOException("无法删除引擎暂存目录：" + parent.getAbsolutePath());
                            }
                            managedFileDeleted = true;
                        } else if (!targetFile.exists() || targetFile.delete()) {
                            managedFileDeleted = true;
                        } else {
                            throw new IOException("无法删除引擎文件：" + targetFile.getAbsolutePath());
                        }
                    } else {
                        // 兼容旧版本：绝不删除应用私有目录之外的用户原始文件。
                        externalSourcePreserved = true;
                    }

                    EngineDescriptor fallback = deletingCurrent ? chooseDeleteFallback(next) : null;
                    SharedPreferences.Editor edit = sp.edit()
                            .putStringSet(PREF_IMPORTED_ENGINES, next)
                            .remove(evalPreferenceKey(target));
                    if (deletingCurrent) {
                        if (fallback != null && !fallback.bundled) {
                            edit.putString(PREF_SELECTED_ENGINE_PATH, fallback.path)
                                    .putString(PREF_SELECTED_ENGINE_PROTOCOL, fallback.protocol);
                        } else {
                            edit.remove(PREF_SELECTED_ENGINE_PATH).remove(PREF_SELECTED_ENGINE_PROTOCOL);
                        }
                    }
                    if (!edit.commit()) {
                        throw new IOException("引擎列表保存失败");
                    }

                    if (activeEngine != null && !activeEngine.bundled && target.path.equals(activeEngine.path)) {
                        activeEngine = null;
                    }
                    newGamePending = true;
                    StringBuilder message = new StringBuilder("已删除引擎：").append(target.displayLabel());
                    if (managedPackageDeleted) message.append("；已清理该引擎的私有暂存目录");
                    else if (managedFileDeleted) message.append("；已删除私有暂存文件");
                    else if (externalSourcePreserved) message.append("；已从列表移除，外部原始文件已保留");
                    if (deletingCurrent) {
                        message.append(fallback == null ? "；当前暂无可用引擎" : "；已回退到 " + fallback.displayLabel());
                    }
                    if (callback != null) callback.onComplete(message.toString());
                } catch (Exception e) {
                    if (deletingCurrent) {
                        activeEngine = null;
                        newGamePending = true;
                    }
                    if (callback != null) callback.onError("删除引擎失败："
                            + e.getClass().getSimpleName() + ": " + e.getMessage());
                }
            }
        };
        if (!submitEngineTask(deleteTask) && callback != null) {
            callback.onError("引擎任务队列已关闭，请重新进入页面后再删除。");
        }
    }

    private EngineDescriptor chooseDeleteFallback(Set<String> remainingRecords) {
        File bundled = resolveBundledEngineBinary();
        if (bundled != null) {
            return new EngineDescriptor("皮卡鱼", "", EngineProbe.PROTOCOL_UCI, true);
        }
        ArrayList<EngineDescriptor> candidates = new ArrayList<EngineDescriptor>();
        if (remainingRecords != null) {
            for (String item : remainingRecords) {
                String[] parts = item.split("\u001f", -1);
                if (parts.length < 3) continue;
                File file = new File(parts[0]);
                if (!file.isFile()) continue;
                candidates.add(new EngineDescriptor(parts[2], parts[0], parts[1], false));
            }
        }
        if (candidates.isEmpty()) return null;
        Collections.sort(candidates, new Comparator<EngineDescriptor>() {
            @Override public int compare(EngineDescriptor a, EngineDescriptor b) {
                return a.displayLabel().compareToIgnoreCase(b.displayLabel());
            }
        });
        return candidates.get(0);
    }

    private boolean isManagedImportedEngineFile(File file) {
        if (file == null) return false;
        try {
            File root = context.getDir("external_engines", Context.MODE_PRIVATE).getCanonicalFile();
            File canonical = file.getCanonicalFile();
            String rootPath = root.getPath();
            String filePath = canonical.getPath();
            return !filePath.equals(rootPath) && filePath.startsWith(rootPath + File.separator);
        } catch (Exception ignored) {
            return false;
        }
    }

    private boolean hasRegisteredEngineInFolder(Set<String> records, File folder) {
        if (records == null || folder == null) return false;
        try {
            String expected = folder.getCanonicalPath();
            for (String item : records) {
                String[] parts = item.split("\u001f", -1);
                if (parts.length < 1) continue;
                File parent = new File(parts[0]).getCanonicalFile().getParentFile();
                if (parent != null && expected.equals(parent.getCanonicalPath())) return true;
            }
        } catch (Exception ignored) {}
        return false;
    }

    private static boolean deleteRecursively(File file) {
        if (file == null || !file.exists()) return true;
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    if (!deleteRecursively(child)) return false;
                }
            }
        }
        return file.delete() || !file.exists();
    }

    public void preWarm(final Callback callback) {
        Runnable task = new Runnable() {
            @Override public void run() {
                try {
                    ensureStarted();
                    if (callback != null) callback.onBestMove("", "皮卡鱼预启动完成");
                } catch (Exception e) {
                    if (callback != null) callback.onError(e.getClass().getSimpleName() + ": " + e.getMessage());
                }
            }
        };
        if (!submitEngineTask(task) && callback != null) {
            callback.onError("引擎任务队列已关闭，请重新进入页面后再试。");
        }
    }

    public synchronized void setOption(String name, String value) {
        if (name == null || name.trim().length() == 0) return;
        String requested = name.trim();
        String actual = findParsedOptionName(requested);
        String n = actual == null ? requested : actual;
        EngineOption option = actual == null ? null : parsedOptions.get(actual);
        if (usesEmbeddedDufNetwork() && isEvalFileOption(n)) {
            removePendingOptionIgnoreCase(OPTION_EVAL_FILE);
            removeSessionOptionIgnoreCase(OPTION_EVAL_FILE);
            activeEvalFile = "";
            lastSentEvalFileCmd = "(duf 使用 libduf.so 内嵌 NNUE，忽略外置 EvalFile)";
            if (option != null) option.currentValue = option.defaultValue;
            try {
                SharedPreferences sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
                SharedPreferences.Editor edit = sp.edit();
                removeLegacyGlobalEvalPreferences(sp, edit);
                edit.remove(evalPreferenceKey(activeEngine != null ? activeEngine : resolveSelectedEngine()));
                edit.apply();
            } catch (Exception ignored) {}
            uciReady = false;
            return;
        }
        String v = normalizeOptionValue(option, value == null ? "" : value.trim());

        if (isEvalFileOption(n)) {
            // EvalFile 是文件路径，必须按当前引擎保存。设置页给出的默认相对名先解析为
            // 当前引擎工作目录中的真实绝对路径，避免覆盖安装后继续发送 pikafish.nnue。
            v = resolveEvalFileValueForCurrentEngine(v);
            removePendingOptionIgnoreCase(OPTION_EVAL_FILE);
            pendingOptions.put(n, v);
            activeEvalFile = v;
            try {
                SharedPreferences sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
                SharedPreferences.Editor edit = sp.edit();
                removeLegacyGlobalEvalPreferences(sp, edit);
                edit.putString(evalPreferenceKey(activeEngine != null ? activeEngine : resolveSelectedEngine()), v);
                edit.apply();
            } catch (Exception ignored) {}
        } else {
            pendingOptions.put(n, v);
            // 删除同义旧键，避免 UCCI hashsize 与 UCI Hash 同时积累。
            if (!n.equals(requested)) pendingOptions.remove(requested);
            try {
                SharedPreferences.Editor edit = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                        .putString(n, v);
                if (!n.equals(requested)) edit.remove(requested);
                edit.apply();
            } catch (Exception ignored) {}
        }
        if (option != null) option.currentValue = v;
        if (EngineProbe.PROTOCOL_UCCI.equals(activeProtocol) && "usemillisec".equalsIgnoreCase(n)) {
            ucciUsesMillisec = "true".equalsIgnoreCase(v) || "1".equals(v);
        }
        if (uciReady && writer != null) {
            try { send(optionCommand(n, v)); } catch (Exception ignored) {}
        }
    }

    public synchronized String getOptionValue(String name, String fallback) {
        String v = pendingOptions.get(name);
        return v == null ? fallback : v;
    }

    public void loadOptions(final OptionsCallback callback) {
        Runnable task = new Runnable() {
            @Override public void run() {
                try {
                    ensureStarted();
                    final List<EngineOption> snapshot = new ArrayList<EngineOption>(parsedOptions.values());
                    if (callback != null) callback.onOptions(snapshot, lastUciOptions);
                } catch (Exception e) {
                    if (callback != null) callback.onError("读取引擎选项失败：" + e.getClass().getSimpleName() + ": " + e.getMessage()
                            + "\n" + getInstallHint());
                    hardStop();
                }
            }
        };
        if (!submitEngineTask(task) && callback != null) {
            callback.onError("引擎任务队列已关闭，请重新进入页面后再读取参数。");
        }
    }

    public void reloadWithSavedOptions(final Callback callback) {
        Runnable task = new Runnable() {
            @Override public void run() {
                try {
                    stopAnalysisAndWait(1600);
                    hardStop();
                    ensureStarted();
                    newGamePending = true;
                    if (callback != null) callback.onBestMove("", "引擎已按保存参数重新加载");
                } catch (Exception e) {
                    if (callback != null) callback.onError("引擎重新加载失败：" + e.getClass().getSimpleName() + ": " + e.getMessage()
                            + "\n" + getInstallHint());
                    hardStop();
                }
            }
        };
        if (!submitEngineTask(task) && callback != null) {
            callback.onError("引擎任务队列已关闭，请重新进入页面后再重载。");
        }
    }

    public void clearHash(final Callback callback) {
        Runnable task = new Runnable() {
            @Override public void run() {
                try {
                    ensureStarted();
                    String clearName = findParsedOptionName("Clear Hash");
                    if (clearName == null) clearName = findParsedOptionName("clearhash");
                    if (clearName == null) throw new IOException("当前引擎不提供清空哈希选项");
                    send(optionCommand(clearName, ""));
                    syncReady(8000);
                    if (callback != null) callback.onBestMove("", "Clear Hash 完成");
                } catch (Exception e) {
                    if (callback != null) callback.onError("清空哈希失败：" + e.getMessage());
                }
            }
        };
        if (!submitEngineTask(task) && callback != null) {
            callback.onError("引擎任务队列已关闭，请重新进入页面后再清空哈希。");
        }
    }

    /**
     * 新局或打开新 FEN 时调用一次。
     *
     * 先让当前搜索进入可排空的 stop 状态，再只标记 pending，避免 UI 立即开启分析时
     * 出现 notifyNewGame 线程和分析线程同时向引擎写命令。真正的 ucinewgame 会在
     * 下一次搜索/分析开始前，由同一个引擎工作流顺序执行。
     */
    public void notifyNewGame() {
        newGamePending = true;
        cancelSearch();
    }

    /** Starts the engine and completes any pending new-game handshake without searching. */
    public void warmUp(final Callback callback) {
        Runnable task = new Runnable() {
            @Override public void run() {
                try {
                    stopAnalysisAndWait(1600L);
                    ensureStarted();
                    consumeNewGameIfNeeded();
                    if (callback != null) callback.onBestMove("", "引擎预热完成");
                } catch (Exception e) {
                    if (callback != null) callback.onError("引擎预热失败：" + e.getMessage());
                    hardStop();
                }
            }
        };
        if (!submitEngineTask(task) && callback != null) {
            callback.onError("引擎任务队列已关闭，请重新进入页面后再预热。");
        }
    }

    /**
     * 固定时限/层数/节点的实战走棋，同时把每一层 info 实时回调给界面显示。
     * 用于“电脑执红/执黑”，避免只在结束时显示 bestmove。
     */
    public void requestBestMoveWithInfo(final String baseFen, final List<String> moves, final SearchLimit limit, final AnalysisCallback callback) {
        requestBestMoveWithInfo(baseFen, moves, limit, null, null, callback);
    }

    /** 固定搜索限制，并可约束/排除根着；用于 V10.0 的“变招”。 */
    public void requestBestMoveWithInfo(final String baseFen, final List<String> moves, final SearchLimit limit,
                                        final List<String> allowedRootMoves, final List<String> bannedRootMoves,
                                        final AnalysisCallback callback) {
        final long requestGeneration = searchGeneration.incrementAndGet();
        requestTimedSearchStopForReplacement();
        Runnable task = new Runnable() {
            @Override public void run() {
                timedSearchThread = Thread.currentThread();
                StringBuilder raw = new StringBuilder();
                try {
                    if (!isSearchGenerationCurrent(requestGeneration)) return;
                    stopAnalysisAndWait(1600);
                    if (!isSearchGenerationCurrent(requestGeneration)) return;
                    ensureStarted();
                    if (!isSearchGenerationCurrent(requestGeneration)) return;
                    SearchLimit safeLimit = limit == null ? SearchLimit.movetime(3000) : limit;
                    drainOldLines();
                    consumeNewGameIfNeeded();
                    // A pending new game already waits for readyok. For normal consecutive
                    // moves the previous bestmove means the engine is idle.
                    // V15：搜索前最后确认下发的是已解析的绝对 NNUE 路径。
                    sendActiveEvalFileIfConfigured();
                    sendPosition(baseFen, moves);
                    sendRootRestrictions(sanitizeSearchMoves(bannedRootMoves));
                    String go = goCommand(safeLimit, sanitizeSearchMoves(allowedRootMoves));
                    synchronized (analysisStateLock) {
                        if (!isSearchGenerationCurrent(requestGeneration)) return;
                        timedSearchActive = true;
                        timedSearchGeneration = requestGeneration;
                        timedSearchStopRequested = false;
                        timedSearchStopRequestedAtMs = 0L;
                        send(go);
                        timedSearchProcess = process;
                    }
                    long deadline = System.currentTimeMillis() + safeLimit.timeoutMs();
                    while (System.currentTimeMillis() < effectiveTimedSearchDeadline(
                            deadline, requestGeneration)
                            && (isSearchGenerationCurrent(requestGeneration)
                            || isTimedSearchStopRequested(requestGeneration))) {
                        long waitDeadline = effectiveTimedSearchDeadline(deadline, requestGeneration);
                        String line = pollLine(waitDeadline - System.currentTimeMillis());
                        if (line == null) continue;
                        raw.append(line).append('\n');
                        if (line.startsWith("__ENGINE_IO_EXCEPTION__")) throw new IOException(line);
                        if (line.startsWith("__ENGINE_EOF__")) {
                            throw new IOException(engineExitMessage("搜索过程中引擎已退出"));
                        }
                        if (line.startsWith("info ")) {
                            EngineInfo info = parseInfo(line);
                            if (info != null && !info.pv.isEmpty() && callback != null
                                    && isSearchGenerationCurrent(requestGeneration)) {
                                callback.onInfo(info, line);
                            }
                            continue;
                        }
                        if (line.startsWith("nobestmove")) {
                            if (callback != null && isSearchGenerationCurrent(requestGeneration)) {
                                callback.onError("引擎返回 nobestmove，当前局面没有可执行着法。\n" + raw);
                            }
                            return;
                        }
                        if (line.startsWith("bestmove")) {
                            String[] parts = line.split("\\s+");
                            String best = parts.length >= 2 ? parts[1].trim().toLowerCase(Locale.ROOT) : null;
                            if (callback != null && isSearchGenerationCurrent(requestGeneration)) {
                                if (!isUsableBestMove(best)) {
                                    callback.onError("引擎没有返回可用 bestmove：" + String.valueOf(best) + "。\n" + raw);
                                } else {
                                    callback.onBestMove(best, raw.toString());
                                }
                            }
                            return;
                        }
                    }
                    if (!isSearchGenerationCurrent(requestGeneration)) {
                        // 取消/替换请求已经接管；stop 由取消方发出，下一次请求会在
                        // stopAnalysisAndWait() 中等待本线程结束后再接管同一 stdout。
                        hardStopTimedProcessIfOwned(requestGeneration);
                        return;
                    }
                    try { send("stop"); } catch (Exception ignored) {}
                    if (isSearchGenerationCurrent(requestGeneration)) hardStop();
                    if (callback != null && isSearchGenerationCurrent(requestGeneration)) {
                        callback.onError("等待 bestmove 超时。\n" + raw);
                    }
                } catch (Exception e) {
                    if (callback != null && isSearchGenerationCurrent(requestGeneration)) {
                        callback.onError("引擎实战思考失败："
                            + searchFailureDiagnostics(e, raw) + "\n" + getInstallHint());
                    }
                    if (isSearchGenerationCurrent(requestGeneration)) hardStop();
                } finally {
                    synchronized (analysisStateLock) {
                        if (timedSearchGeneration == requestGeneration) {
                            timedSearchActive = false;
                            timedSearchGeneration = 0L;
                            timedSearchStopRequested = false;
                            timedSearchStopRequestedAtMs = 0L;
                            timedSearchProcess = null;
                        }
                    }
                    if (timedSearchThread == Thread.currentThread()) timedSearchThread = null;
                }
            }
        };
        if (!submitEngineTask(task) && callback != null) {
            // 任务队列关闭时必须回调失败并复位 UI，而不是在主线程抛异常。
            callback.onError("引擎任务队列已关闭，请重新进入页面后再调用引擎。");
        }
    }

    public void requestBestMove(final String baseFen, final List<String> moves, final int moveTimeMs, final Callback callback) {
        requestBestMove(baseFen, moves, SearchLimit.movetime(moveTimeMs), callback);
    }

    public void requestBestMove(final String baseFen, final List<String> moves, final SearchLimit limit, final Callback callback) {
        final long requestGeneration = searchGeneration.incrementAndGet();
        requestTimedSearchStopForReplacement();
        Runnable task = new Runnable() {
            @Override public void run() {
                timedSearchThread = Thread.currentThread();
                try {
                    if (!isSearchGenerationCurrent(requestGeneration)) return;
                    stopAnalysisAndWait(1200);
                    if (!isSearchGenerationCurrent(requestGeneration)) return;
                    ensureStarted();
                    if (!isSearchGenerationCurrent(requestGeneration)) return;
                    SearchLimit safeLimit = limit == null ? SearchLimit.movetime(3000) : limit;
                    StringBuilder raw = new StringBuilder();
                    drainOldLines();
                    consumeNewGameIfNeeded();
                    sendPosition(baseFen, moves);
                    synchronized (analysisStateLock) {
                        if (!isSearchGenerationCurrent(requestGeneration)) return;
                        timedSearchActive = true;
                        timedSearchGeneration = requestGeneration;
                        timedSearchStopRequested = false;
                        timedSearchStopRequestedAtMs = 0L;
                        send(goCommand(safeLimit));
                        timedSearchProcess = process;
                    }
                    String best = readBestMove(raw, safeLimit.timeoutMs(), requestGeneration);
                    if (!isSearchGenerationCurrent(requestGeneration)) {
                        hardStopTimedProcessIfOwned(requestGeneration);
                        return;
                    }
                    if (callback != null && isSearchGenerationCurrent(requestGeneration)) {
                        if (!isUsableBestMove(best)) {
                            callback.onError("引擎已启动，但没有返回可用 bestmove：" + String.valueOf(best) + "。\n" + raw);
                        } else {
                            callback.onBestMove(best, raw.toString());
                        }
                    }
                } catch (Exception e) {
                    if (callback != null && isSearchGenerationCurrent(requestGeneration)) {
                        callback.onError("引擎调用失败：" + e.getClass().getSimpleName() + ": " + e.getMessage()
                            + "\n" + getInstallHint());
                    }
                    if (isSearchGenerationCurrent(requestGeneration)) hardStop();
                } finally {
                    synchronized (analysisStateLock) {
                        if (timedSearchGeneration == requestGeneration) {
                            timedSearchActive = false;
                            timedSearchGeneration = 0L;
                            timedSearchStopRequested = false;
                            timedSearchStopRequestedAtMs = 0L;
                            timedSearchProcess = null;
                        }
                    }
                    if (timedSearchThread == Thread.currentThread()) timedSearchThread = null;
                }
            }
        };
        if (!submitEngineTask(task) && callback != null) {
            callback.onError("引擎任务队列已关闭，请重新进入页面后再调用引擎。");
        }
    }

    /**
     * 启动实时分析。该方法会立即返回；info 输出会通过 callback 持续回调。
     */
    public void startAnalysis(final String baseFen, final List<String> moves, final AnalysisCallback callback) {
        startAnalysis(baseFen, moves, null, null, callback);
    }

    public void startAnalysis(final String baseFen, final List<String> moves,
                              final List<String> searchMoves, final AnalysisCallback callback) {
        startAnalysis(baseFen, moves, searchMoves, null, callback);
    }

    /**
     * 启动实时分析。UCI 使用 searchmoves 限制允许根着；UCCI 没有 searchmoves，
     * 按 UCCI 3.0 在 position 后发送 banmoves，再发送 go infinite。
     */
    public void startAnalysis(final String baseFen, final List<String> moves,
                              final List<String> searchMoves, final List<String> bannedMoves,
                              final AnalysisCallback callback) {
        startAnalysis(baseFen, moves, searchMoves, bannedMoves, null, callback);
    }

    /**
     * V19.8：带启动守卫的实时分析。守卫在排队、等待旧搜索结束和真正发送 go 前都会检查，
     * 这样“电脑已经走完/新建棋局”后，旧局面的后台启动任务不能再迟到占用分析引擎。
     */
    public void startAnalysis(final String baseFen, final List<String> moves,
                              final List<String> searchMoves, final List<String> bannedMoves,
                              final AnalysisStartGuard startGuard,
                              final AnalysisCallback callback) {
        if (!analysisStartAllowed(startGuard)) return;
        // V19.1 卡顿修复：startAnalysis 不再持有整个 PikafishEngine 对象锁等待 bestmove。
        // UI 的“新建/切模式”可立即 stop，不会因为后台启动流程占锁而卡住主线程。
        final long stopGenerationAtStart = analysisStopGeneration.get();
        // 重启分析前必须等上一条搜索真正结束，避免两个线程同时读取 stdout。
        stopAnalysisAndWait(1800L);
        if (stopGenerationAtStart != analysisStopGeneration.get()
                || !analysisStartAllowed(startGuard)) return;
        drainOldLines();
        synchronized (analysisStateLock) {
            analysisRunning = true;
            analysisSearchActive = false;
            analysisSearchStartedAtMs = 0L;
            analysisBestMoveRequested = false;
            analysisStopRequested = false;
            analysisStopRequestedAtMs = 0L;
        }
        final List<String> rootSearchMoves = sanitizeSearchMoves(searchMoves);
        final List<String> rootBannedMoves = sanitizeSearchMoves(bannedMoves);
        analysisThread = new Thread(new Runnable() {
            @Override public void run() {
                // 若用户在启动窗口内已经点了停止/新建，或 UI 已切到更新局面，放弃迟到分析。
                if (!analysisRunning || analysisStopRequested
                        || stopGenerationAtStart != analysisStopGeneration.get()
                        || !analysisStartAllowed(startGuard)) {
                    analysisRunning = false;
                    return;
                }
                StringBuilder raw = new StringBuilder();
                boolean boundedCompatibilityMode = false;
                boolean firstInfoSeen = false;
                try {
                    ensureStarted();
                    if (!analysisRunning || analysisStopRequested
                            || stopGenerationAtStart != analysisStopGeneration.get()
                            || !analysisStartAllowed(startGuard)) return;
                    drainOldLines();
                    consumeNewGameIfNeeded();
                    // 正常 position -> go 切换不需要每步额外 isready；上一次 bestmove 已表示引擎空闲。
                    // 仅在新局、选项变更和进程重启时同步 ready，缩短连续立即出招的搜索空窗。
                    sendActiveEvalFileIfConfigured();
                    if (!analysisStartAllowed(startGuard)) return;
                    sendPosition(baseFen, moves);
                    sendRootRestrictions(rootBannedMoves);
                    if (!beginAnalysisSearch(goInfiniteCommand(rootSearchMoves),
                            stopGenerationAtStart, startGuard)) return;
                    long noOutputDeadline = System.currentTimeMillis() + 4000L;

                    while (analysisRunning && !closed) {
                        String line = pollLine(300L);
                        if (line == null) {
                            long now = System.currentTimeMillis();
                            if (process == null || !process.isAlive()) {
                                throw new IOException(engineExitMessage("分析过程中引擎已退出"));
                            }
                            // V20.0：普通 stop 后只等待旧搜索的最终终止标志，期间绝不进入
                            // go infinite 兼容重启。异常引擎若迟迟不回 bestmove，则重建进程。
                            if (analysisStopRequested) {
                                long stopAt = analysisStopRequestedAtMs;
                                if (stopAt > 0L && now - stopAt >= ANALYSIS_STOP_DRAIN_TIMEOUT_MS) {
                                    hardStop();
                                    return;
                                }
                                continue;
                            }
                            if (!firstInfoSeen && now > noOutputDeadline) {
                                if (boundedCompatibilityMode) {
                                    throw new IOException("引擎在兼容搜索中仍未返回 info 或 bestmove");
                                }
                                // 某些移植版引擎能响应有限时搜索，却会忽略 go infinite。
                                // 重建进程后切换为循环 movetime 搜索，界面仍可持续获得实时分数和 PV。
                                boolean keepRunning = analysisRunning;
                                try { send("stop"); } catch (Exception ignored) {}
                                hardStop();
                                analysisRunning = keepRunning;
                                ensureStarted();
                                if (!analysisRunning || analysisStopRequested
                                        || stopGenerationAtStart != analysisStopGeneration.get()
                                        || !analysisStartAllowed(startGuard)) return;
                                consumeNewGameIfNeeded();
                                syncReady(5000L);
                                sendActiveEvalFileIfConfigured();
                                drainOldLines();
                                if (!analysisStartAllowed(startGuard)) return;
                                sendPosition(baseFen, moves);
                                sendRootRestrictions(rootBannedMoves);
                                if (!beginAnalysisSearch(goCommand(SearchLimit.movetime(2200), rootSearchMoves),
                                        stopGenerationAtStart, startGuard)) return;
                                boundedCompatibilityMode = true;
                                noOutputDeadline = System.currentTimeMillis() + 6500L;
                            }
                            continue;
                        }

                        raw.append(line).append('\n');
                        if (line.startsWith("__ENGINE_IO_EXCEPTION__")) throw new IOException(line);
                        if (line.startsWith("__ENGINE_EOF__")) {
                            throw new IOException(engineExitMessage("分析过程中引擎已退出"));
                        }
                        if (line.startsWith("info ")) {
                            EngineInfo info = parseInfo(line);
                            if (info != null && !info.pv.isEmpty()) {
                                firstInfoSeen = true;
                                if (callback != null) callback.onInfo(info, line);
                            }
                            continue;
                        }
                        if (line.startsWith("bestmove") || line.startsWith("nobestmove")) {
                            boolean requestedBestMove;
                            boolean stoppingForReplacement;
                            synchronized (analysisStateLock) {
                                analysisSearchActive = false;
                                analysisSearchStartedAtMs = 0L;
                                requestedBestMove = analysisBestMoveRequested;
                                stoppingForReplacement = analysisStopRequested;
                                analysisBestMoveRequested = false;
                            }
                            String[] parts = line.split("\\s+");
                            String best = line.startsWith("bestmove") && parts.length >= 2
                                    ? parts[1].trim().toLowerCase(Locale.ROOT) : null;
                            if (boundedCompatibilityMode && analysisRunning && !closed
                                    && !requestedBestMove && !stoppingForReplacement) {
                                // 少数精简引擎只返回 bestmove、不输出 info。构造最小 PV 回调，
                                // 让界面至少显示主着，避免一直停留在“等待引擎输出”。
                                if (!firstInfoSeen && isUsableBestMove(best) && callback != null) {
                                    EngineInfo fallbackInfo = new EngineInfo();
                                    fallbackInfo.pv.add(best);
                                    callback.onInfo(fallbackInfo, line);
                                    firstInfoSeen = true;
                                }
                                // 有限时兼容模式完成一轮后立即开始下一轮，模拟持续分析。
                                syncReady(5000L);
                                drainOldLines();
                                if (!analysisStartAllowed(startGuard)) return;
                                sendPosition(baseFen, moves);
                                sendRootRestrictions(rootBannedMoves);
                                if (!beginAnalysisSearch(goCommand(SearchLimit.movetime(2200), rootSearchMoves),
                                        stopGenerationAtStart, startGuard)) return;
                                noOutputDeadline = System.currentTimeMillis() + 6500L;
                                continue;
                            }
                            // 普通“换局面/变招/导航”stop 只负责排空旧搜索，不把旧 bestmove
                            // 回调给新一代界面；“立即出招”主动 stop 仍保持最终 bestmove 语义。
                            if (callback != null && (!stoppingForReplacement || requestedBestMove)) {
                                callback.onBestMove(best, raw.toString());
                            }
                            break;
                        }
                    }
                } catch (Exception e) {
                    if (analysisRunning && !analysisStopRequested && !closed && callback != null) {
                        StringBuilder diag = new StringBuilder();
                        diag.append("分析模式启动失败：").append(e.getClass().getSimpleName())
                            .append(": ").append(e.getMessage()).append('\n')
                            .append(getInstallHint()).append('\n');
                        diag.append("[Debug] activeEvalFile=")
                            .append(activeEvalFile.length() == 0 ? "(未设置)" : activeEvalFile).append('\n');
                        diag.append("[Debug] NNUE安装目录检查=").append(describeEvalFileCandidates()).append('\n');
                        diag.append("[Debug] UCI 握手输出(").append(lastUciOptions.length())
                            .append(" 字符):\n").append(lastUciOptions.length() > 1200
                                ? lastUciOptions.substring(0, 1200) + "…(截断)" : lastUciOptions);
                        // 抓取 go infinite 后引擎的全部原始输出（退出前最后一段）
                        String tail = raw.length() > 0 ? raw.toString() : "(无输出)";
                        diag.append("[Debug] go 后引擎输出(").append(tail.length())
                            .append(" 字符):\n").append(tail.length() > 2000
                                ? tail.substring(tail.length() - 2000) : tail);
                        diag.append("\n[Debug] 实际发送的 setoption EvalFile: ").append(lastSentEvalFileCmd);
                        callback.onError(diag.toString());
                    }
                    if (analysisRunning && !closed) hardStop();
                } finally {
                    synchronized (analysisStateLock) {
                        analysisRunning = false;
                        analysisSearchActive = false;
                        analysisSearchStartedAtMs = 0L;
                        analysisBestMoveRequested = false;
                        analysisStopRequested = false;
                        analysisStopRequestedAtMs = 0L;
                    }
                }
            }
        }, "Pikafish-analysis");
        analysisThread.setDaemon(true);
        if (!analysisRunning || analysisStopRequested
                || stopGenerationAtStart != analysisStopGeneration.get()
                || !analysisStartAllowed(startGuard)) {
            analysisRunning = false;
            return;
        }
        analysisThread.start();
    }

    /**
     * V20.0：把“确认仍可启动 + 标记搜索活跃 + 写入 go”放进同一个很短的状态锁。
     * stopAnalysis() 使用同一把锁，避免 stop 恰好插在检查与 go 写入之间。
     */
    private boolean beginAnalysisSearch(String command, long stopGenerationAtStart,
                                        AnalysisStartGuard startGuard) throws IOException {
        if (!analysisStartAllowed(startGuard)) return false;
        synchronized (analysisStateLock) {
            if (!analysisRunning || analysisStopRequested
                    || stopGenerationAtStart != analysisStopGeneration.get()
                    || !analysisStartAllowed(startGuard)) return false;
            analysisSearchActive = true;
            send(command);
            analysisSearchStartedAtMs = System.currentTimeMillis();
            return true;
        }
    }

    private boolean analysisStartAllowed(AnalysisStartGuard startGuard) {
        if (startGuard == null) return true;
        try {
            return startGuard.isValid();
        } catch (Exception ignored) {
            return false;
        }
    }


    private List<String> sanitizeSearchMoves(List<String> moves) {
        if (moves == null || moves.isEmpty()) return Collections.emptyList();
        ArrayList<String> out = new ArrayList<String>();
        for (String m : moves) {
            if (m == null) continue;
            String v = m.trim().toLowerCase(Locale.ROOT);
            if (v.length() >= 4 && !out.contains(v.substring(0, 4))) out.add(v.substring(0, 4));
        }
        return out;
    }

    private String goCommand(SearchLimit limit, List<String> allowedRootMoves) {
        List<String> allowed = sanitizeSearchMoves(allowedRootMoves);
        if (EngineProbe.PROTOCOL_UCCI.equals(activeProtocol) || allowed.isEmpty()) return goCommand(limit);
        StringBuilder sb = new StringBuilder("go searchmoves");
        for (String move : allowed) sb.append(' ').append(move);
        SearchLimit safe = limit == null ? SearchLimit.movetime(3000) : limit;
        if (safe.mode == SearchLimit.MODE_COMBINED) {
            if (safe.depth > 0) sb.append(" depth ").append(safe.depth);
            if (safe.nodes > 0) sb.append(" nodes ").append(safe.nodes);
            if (safe.moveTimeMs > 0) sb.append(" movetime ").append(safe.moveTimeMs);
        } else if (safe.mode == SearchLimit.MODE_DEPTH) sb.append(" depth ").append(safe.value);
        else if (safe.mode == SearchLimit.MODE_NODES) sb.append(" nodes ").append(safe.value);
        else sb.append(" movetime ").append(safe.value);
        return sb.toString();
    }

    private String goCommand(SearchLimit limit) {
        SearchLimit safe = limit == null ? SearchLimit.movetime(3000) : limit;
        if (safe.mode == SearchLimit.MODE_COMBINED) return safe.goCommand();
        if (safe.mode == SearchLimit.MODE_DEPTH) return "go depth " + safe.value;
        if (safe.mode == SearchLimit.MODE_NODES) return "go nodes " + safe.value;
        if (EngineProbe.PROTOCOL_UCCI.equals(activeProtocol)) {
            int time = ucciUsesMillisec ? safe.value : Math.max(1, (safe.value + 999) / 1000);
            return "go time " + time;
        }
        return "go movetime " + safe.value;
    }

    private void sendRootRestrictions(List<String> bannedMoves) throws IOException {
        if (!EngineProbe.PROTOCOL_UCCI.equals(activeProtocol)
                || bannedMoves == null || bannedMoves.isEmpty()) return;
        StringBuilder sb = new StringBuilder("banmoves");
        for (String move : bannedMoves) {
            if (move != null && move.length() >= 4) sb.append(' ').append(move.substring(0, 4).toLowerCase(Locale.ROOT));
        }
        if (sb.length() > "banmoves".length()) send(sb.toString());
    }

    private String goInfiniteCommand(List<String> searchMoves) {
        if (EngineProbe.PROTOCOL_UCCI.equals(activeProtocol)
                || searchMoves == null || searchMoves.isEmpty()) return "go infinite";
        StringBuilder sb = new StringBuilder("go searchmoves");
        for (String m : searchMoves) {
            if (m != null && m.length() >= 4) sb.append(' ').append(m.substring(0, 4).toLowerCase(Locale.ROOT));
        }
        sb.append(" infinite");
        return sb.toString();
    }

    public void runSelfTest(final Callback callback) {
        final String startFen = "rnbakabnr/9/1c5c1/p1p1p1p1p/9/9/P1P1P1P1P/1C5C1/9/RNBAKABNR w - - 0 1";
        requestBestMove(startFen, Collections.<String>emptyList(), 300, callback);
    }

    /**
     * 请求当前无限分析立即收束并返回 bestmove，但保持读取线程继续运行直到收到
     * bestmove/nobestmove。用于“立即出招”的 stop -> bestmove 快速路径。
     *
     * @return 已向正在搜索的引擎发送 stop 时返回 true。
     */
    public long getCurrentAnalysisElapsedMs() {
        long started = analysisSearchStartedAtMs;
        if (!analysisRunning || !analysisSearchActive || started <= 0L) return -1L;
        return Math.max(0L, System.currentTimeMillis() - started);
    }

    public boolean requestCurrentAnalysisBestMove() {
        synchronized (analysisStateLock) {
            try {
                if (analysisRunning && analysisSearchActive && !analysisStopRequested
                        && analysisThread != null && analysisThread.isAlive() && writer != null) {
                    analysisBestMoveRequested = true;
                    send("stop");
                    return true;
                }
            } catch (Exception ignored) {
                analysisBestMoveRequested = false;
            }
            return false;
        }
    }

    /**
     * 收束当前电脑执红/黑的限时搜索，并让原搜索线程把最终 bestmove 回调给界面。
     * 与 cancelSearch() 不同，本方法保留当前进程和已搜索结果。
     */
    public boolean requestCurrentTimedSearchBestMove() {
        synchronized (analysisStateLock) {
            try {
                if (timedSearchActive && !timedSearchStopRequested
                        && timedSearchGeneration == searchGeneration.get()
                        && writer != null) {
                    timedSearchStopRequested = true;
                    timedSearchStopRequestedAtMs = System.currentTimeMillis();
                    send("stop");
                    return true;
                }
            } catch (Exception ignored) {
                timedSearchStopRequested = false;
                timedSearchStopRequestedAtMs = 0L;
            }
            return false;
        }
    }

    public void stopAnalysis() {
        if (timedSearchActive) {
            cancelSearch();
            return;
        }
        analysisStopGeneration.incrementAndGet();
        stopAnalysisInternal();
    }

    /**
     * 取消当前任意搜索（包括限时实战搜索）并使旧请求失效。
     * 正在搜索时发送 stop，由原拥有线程排空终止输出；下一次请求会等待该线程结束，
     * 避免旧任务继续占用 stdout。
     */
    public void cancelSearch() {
        searchGeneration.incrementAndGet();
        analysisStopGeneration.incrementAndGet();
        boolean timedOwned = false;
        synchronized (analysisStateLock) {
            // 若当前是 go infinite，仍由原读取线程消费 bestmove；这样下一个分析
            // 不会在旧搜索尚未收束时复用同一 stdout。限时搜索则发送 stop 并立即
            // 失效旧回调，其任务会继续排空到 bestmove 或短超时后退出。
            if (timedSearchActive && writer != null) {
                timedOwned = true;
                if (!timedSearchStopRequested) {
                    timedSearchStopRequested = true;
                    timedSearchStopRequestedAtMs = System.currentTimeMillis();
                    try { send("stop"); } catch (Exception ignored) {}
                }
            } else if (analysisSearchActive && analysisThread != null && analysisThread.isAlive()) {
                analysisStopRequested = true;
                analysisStopRequestedAtMs = System.currentTimeMillis();
                if (!analysisBestMoveRequested && writer != null) {
                    try { send("stop"); } catch (Exception ignored) {}
                }
            } else {
                analysisRunning = false;
                analysisSearchActive = false;
                analysisSearchStartedAtMs = 0L;
                analysisBestMoveRequested = false;
                analysisStopRequested = true;
                analysisStopRequestedAtMs = System.currentTimeMillis();
            }
            if (!timedOwned) {
                timedSearchActive = false;
                timedSearchGeneration = 0L;
                timedSearchStopRequested = false;
                timedSearchStopRequestedAtMs = 0L;
                timedSearchProcess = null;
            }
        }
    }

    /** 新的限时请求排队时，先收束仍在进行的旧 go，避免新 position 直接撞入旧搜索。 */
    private void requestTimedSearchStopForReplacement() {
        synchronized (analysisStateLock) {
            if (!timedSearchActive || timedSearchStopRequested || writer == null) return;
            timedSearchStopRequested = true;
            timedSearchStopRequestedAtMs = System.currentTimeMillis();
            try { send("stop"); } catch (Exception ignored) {}
        }
    }

    /** 只有仍持有旧进程的限时线程才允许重建，避免误杀已经接管的新分析进程。 */
    private void hardStopTimedProcessIfOwned(long generation) {
        Process expected;
        synchronized (analysisStateLock) {
            if (timedSearchGeneration != generation || !timedSearchStopRequested) return;
            expected = timedSearchProcess;
        }
        if (expected == null) return;
        synchronized (this) {
            if (process == expected) hardStop();
        }
    }

    private boolean isSearchGenerationCurrent(long generation) {
        return generation == searchGeneration.get() && !closed;
    }

    private boolean isTimedSearchStopRequested(long generation) {
        return timedSearchActive && timedSearchGeneration == generation
                && timedSearchStopRequested;
    }

    private long effectiveTimedSearchDeadline(long searchDeadline, long generation) {
        if (!isTimedSearchStopRequested(generation)) return searchDeadline;
        long stopAt = timedSearchStopRequestedAtMs;
        if (stopAt <= 0L) return searchDeadline;
        return Math.min(searchDeadline, stopAt + TIMED_SEARCH_STOP_DRAIN_TIMEOUT_MS);
    }

    /** 内部切换搜索使用，不把“重启上一条分析”误当成用户显式取消。 */
    private void stopAnalysisInternal() {
        // V20.0：go 已发出时只发送一次 stop，并让原分析线程继续读取 stdout，
        // 直到收到 bestmove/nobestmove。不能先结束读取循环，否则迟到 bestmove 会串到下一局面。
        synchronized (analysisStateLock) {
            // analysisBestMoveRequested=true 说明“立即出招”已经发过 stop；若此时用户又点
            // 变招/导航，只把该 stop 的用途改成普通排空，不能再重复发送第二个 stop。
            boolean stopAlreadySent = analysisBestMoveRequested || analysisStopRequested;
            analysisBestMoveRequested = false;
            Thread t = analysisThread;
            boolean threadAlive = t != null && t.isAlive();
            if (analysisSearchActive && threadAlive && writer != null) {
                if (!analysisStopRequested) {
                    analysisStopRequested = true;
                    analysisStopRequestedAtMs = System.currentTimeMillis();
                    if (!stopAlreadySent) {
                        try {
                            send("stop");
                        } catch (Exception ignored) {
                            uciReady = false;
                            analysisRunning = false;
                        }
                    }
                }
                return;
            }
            // 尚处在握手/position 启动窗口、还没真正发 go 时无需等 bestmove。
            // 直接撤销启动意图，真正发送 go 前还会再次检查该状态。
            analysisStopRequested = true;
            analysisStopRequestedAtMs = System.currentTimeMillis();
            analysisRunning = false;
        }
    }

    /**
     * Activity 暂时进入后台时释放引擎子进程，但保留单线程执行器和扫描线程。
     * 系统文件夹选择器会触发 onStop；此时若调用 stop() 关闭执行器，返回页面后
     * selectEngine() 会因任务被拒绝而出现偶发闪退。
     */
    public synchronized void releaseForBackground() {
        if (executor.isShutdown()) return;
        closed = true;
        try {
            if (writer != null) send("quit");
        } catch (Exception ignored) {}
        hardStop();
    }

    public synchronized void stop() {
        closed = true;
        try {
            if (writer != null) send("quit");
        } catch (Exception ignored) {}
        hardStop();
        Thread scan = engineScanThread;
        if (scan != null) scan.interrupt();
        executor.shutdownNow();
    }

    private void stopAnalysisAndWait(long ms) {
        Thread timed = timedSearchThread;
        if (timed != null && timed != Thread.currentThread() && timed.isAlive()) {
            requestTimedSearchStopForReplacement();
            try {
                timed.join(Math.max(200L, Math.min(1800L, ms)));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            if (timed.isAlive()) {
                // 限时搜索在取消后仍不退出，不能让新 position 进入同一 stdout。
                hardStop();
                timed.interrupt();
                try { timed.join(300L); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            }
        }
        Thread t;
        synchronized (this) {
            t = analysisThread;
        }
        if (t != null && t.isAlive()) stopAnalysisInternal();
        if (t != null && t.isAlive()) {
            try {
                t.join(ms);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        // 若分析线程仍未退出，不能让另一个线程同时读取 stdout；直接重启最安全。
        if (t != null && t.isAlive()) {
            hardStop();
            return;
        }
        if (analysisSearchActive && process != null && process.isAlive()) {
            try {
                if (!waitForAnalysisSearchEnd(Math.max(500L, Math.min(1600L, ms)))) hardStop();
            } catch (Exception e) {
                hardStop();
            }
        }
    }

    /** stop 后必须等 bestmove/nobestmove，UCCI 规范以此作为回到空闲状态的标志。 */
    private boolean waitForAnalysisSearchEnd(long timeoutMs) throws IOException {
        if (!analysisSearchActive) return true;
        long deadline = System.currentTimeMillis() + Math.max(200L, timeoutMs);
        while (System.currentTimeMillis() < deadline) {
            String line = pollLine(deadline - System.currentTimeMillis());
            if (line == null) continue;
            if (line.startsWith("__ENGINE_IO_EXCEPTION__")) throw new IOException(line);
            if (line.startsWith("__ENGINE_EOF__")) {
                throw new IOException(engineExitMessage("等待 stop 终止反馈时引擎已退出"));
            }
            if (line.startsWith("bestmove") || line.startsWith("nobestmove")) {
                synchronized (analysisStateLock) {
                    analysisSearchActive = false;
                    analysisSearchStartedAtMs = 0L;
                    analysisBestMoveRequested = false;
                    analysisStopRequested = false;
                    analysisStopRequestedAtMs = 0L;
                }
                return true;
            }
        }
        return false;
    }

    private synchronized void ensureStarted() throws IOException {
        EngineDescriptor descriptor = resolveSelectedEngine();
        if (process != null && process.isAlive() && writer != null && uciReady
                && activeEngine != null && descriptor != null
                && activeEngine.stableKey().equals(descriptor.stableKey())) return;
        File engine = descriptor == null ? null : (descriptor.bundled ? resolveBundledEngineBinary() : new File(descriptor.path));
        if (engine == null || !engine.exists()) {
            throw new IOException("没有找到当前选择的可执行引擎");
        }
        // 进程重建不等于用户停止分析。首次 startAnalysis 已把 analysisRunning
        // 设为 true；这里清理旧进程时必须保留该意图，否则 go infinite 发出后
        // 分析读取线程会因标志被 hardStop 清零而立即退出。
        boolean preserveAnalysisRun = analysisRunning;
        hardStop();
        analysisRunning = preserveAnalysisRun;
        closed = false;
        clearLineQueue();
        activeEngine = descriptor;
        activeProtocol = descriptor.protocol;
        lastSentEvalFileCmd = null;

        File workDir = engine.getParentFile();
        EngineProbe.StartedProcess started = EngineProbe.startEngineProcess(engine, workDir);
        process = started.process;
        activeLaunchMode = started.launchMode;
        writer = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8));
        long readerGeneration = ++processGeneration;
        startReaderThread(process, readerGeneration);

        String handshake = EngineProbe.PROTOCOL_UCCI.equals(activeProtocol) ? "ucci" : "uci";
        String ok = EngineProbe.PROTOCOL_UCCI.equals(activeProtocol) ? "ucciok" : "uciok";
        StringBuilder raw = new StringBuilder();
        send(handshake);
        readUntil(ok, raw, 8000);
        lastUciOptions = raw.toString();
        parseUciOptions(lastUciOptions);

        configureEvalFileForEngine(descriptor, engine, workDir);
        ucciUsesMillisec = false;
        if (EngineProbe.PROTOCOL_UCCI.equals(activeProtocol)
                && optionExists(lastUciOptions, "usemillisec")) {
            if (!pendingOptions.containsKey("usemillisec")) pendingOptions.put("usemillisec", "true");
            String millis = pendingOptions.get("usemillisec");
            ucciUsesMillisec = millis != null && ("true".equalsIgnoreCase(millis) || "1".equals(millis));
        }
        for (Map.Entry<String, String> e : new ArrayList<Map.Entry<String, String>>(pendingOptions.entrySet())) {
            String actualName = findParsedOptionName(e.getKey());
            if (actualName != null) {
                EngineOption option = parsedOptions.get(actualName);
                String safeValue = normalizeOptionValue(option, e.getValue());
                String cmd = optionCommand(actualName, safeValue);
                send(cmd);
                if (option != null) option.currentValue = safeValue;
                if (isEvalFileOption(actualName)) lastSentEvalFileCmd = cmd;
            }
        }
        if (lastSentEvalFileCmd == null) lastSentEvalFileCmd = "(未发送 EvalFile)";
        // V15.2: findParsedOptionName 对未在 UCI 输出中的选项返回 null，
        // 导致 session/pending 循环跳过该条目。兜底直接发送 EvalFile。
        if (lastSentEvalFileCmd != null && lastSentEvalFileCmd.contains("未发送")
                && activeEvalFile.length() > 0) {
            String cmd = optionCommand(OPTION_EVAL_FILE, activeEvalFile);
            send(cmd);
            lastSentEvalFileCmd = cmd;
        }
        // 会话参数最后发送，覆盖持久参数，但不写回设置。人机难度和"131 手动工具"由不同实例维护。
        for (Map.Entry<String, String> e : new ArrayList<Map.Entry<String, String>>(sessionOptions.entrySet())) {
            String actualName = findParsedOptionName(e.getKey());
            if (actualName != null) {
                EngineOption option = parsedOptions.get(actualName);
                String safeValue = normalizeOptionValue(option, e.getValue());
                String cmd = optionCommand(actualName, safeValue);
                send(cmd);
                if (option != null) option.currentValue = safeValue;
                if (isEvalFileOption(actualName)) lastSentEvalFileCmd = cmd;
            }
        }

        readyCommandSupported = true;
        syncReady(EngineProbe.PROTOCOL_UCCI.equals(activeProtocol) ? 1800L : 8000L);
        uciReady = true;
    }


    private void syncReady(long timeoutMs) throws IOException {
        if (EngineProbe.PROTOCOL_UCCI.equals(activeProtocol) && !readyCommandSupported) return;
        send("isready");
        try {
            readUntil("readyok", new StringBuilder(), timeoutMs);
        } catch (IOException e) {
            String message = e.getMessage() == null ? "" : e.getMessage();
            if (EngineProbe.PROTOCOL_UCCI.equals(activeProtocol)
                    && message.contains("超时") && process != null && process.isAlive()) {
                // 部分旧 UCCI 联赛引擎只实现最小指令集，不提供 isready/readyok。
                readyCommandSupported = false;
                return;
            }
            throw e;
        }
    }

    private void consumeNewGameIfNeeded() throws IOException {
        if (!newGamePending) return;
        if (EngineProbe.PROTOCOL_UCCI.equals(activeProtocol)) {
            // UCCI 3.0 允许界面发送未在 option 列表中声明的参数；象棋巫师每次新局均发送此命令。
            send("setoption newgame");
        } else {
            send("ucinewgame");
        }
        syncReady(5000L);
        newGamePending = false;
    }

    private void sendPosition(String baseFen, List<String> moves) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("position fen ").append(normalizeFen(baseFen));
        if (moves != null && !moves.isEmpty()) {
            sb.append(" moves");
            for (String move : moves) {
                if (move != null && move.length() >= 4) sb.append(' ').append(move.toLowerCase(Locale.ROOT));
            }
        }
        send(sb.toString());
    }

    private String normalizeFen(String fen) {
        if (fen == null || fen.trim().length() == 0) {
            return "rnbakabnr/9/1c5c1/p1p1p1p1p/9/9/P1P1P1P1P/1C5C1/9/RNBAKABNR w - - 0 1";
        }
        String f = fen.trim();
        int idx = f.indexOf(" moves ");
        if (idx >= 0) f = f.substring(0, idx).trim();
        String[] parts = f.split("\\s+");
        if (parts.length >= 6) return parts[0] + " " + parts[1] + " " + parts[2] + " " + parts[3] + " " + parts[4] + " " + parts[5];
        if (parts.length >= 2) return parts[0] + " " + parts[1] + " - - 0 1";
        return parts[0] + " w - - 0 1";
    }

    private void startReaderThread(final Process readerProcess, final long generation) {
        readerThread = new Thread(new Runnable() {
            @Override public void run() {
                try (BufferedReader br = new BufferedReader(new InputStreamReader(readerProcess.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while (!closed && generation == processGeneration && readerProcess == process
                            && (line = br.readLine()) != null) {
                        if (!offerLineIfCurrent(readerProcess, generation, cleanEngineLine(line))) break;
                    }
                } catch (Exception e) {
                    offerLineIfCurrent(readerProcess, generation,
                            "__ENGINE_IO_EXCEPTION__ " + e.getMessage());
                } finally {
                    offerLineIfCurrent(readerProcess, generation, "__ENGINE_EOF__");
                }
            }
        }, "Pikafish-stdout-reader");
        readerThread.setDaemon(true);
        readerThread.start();
    }

    private void readUntil(String token, StringBuilder raw, long timeoutMs) throws IOException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            String line = pollLine(deadline - System.currentTimeMillis());
            if (line == null) continue;
            raw.append(line).append('\n');
            if (line.startsWith("__ENGINE_IO_EXCEPTION__")) throw new IOException(line);
            if (line.startsWith("__ENGINE_EOF__")) throw new IOException(engineExitMessage("等待 " + token + " 时引擎已退出"));
            if (containsEngineToken(line, token)) return;
        }
        throw new IOException("等待 " + token + " 超时；已收到：\n" + raw);
    }


    private static boolean containsEngineToken(String line, String token) {
        if (line == null || token == null) return false;
        String s = line.trim();
        if (s.equalsIgnoreCase(token)) return true;
        String lower = s.toLowerCase(Locale.ROOT);
        String wanted = token.toLowerCase(Locale.ROOT);
        int at = lower.indexOf(wanted);
        if (at < 0) return false;
        boolean left = at == 0 || Character.isWhitespace(lower.charAt(at - 1));
        int end = at + wanted.length();
        boolean right = end >= lower.length() || Character.isWhitespace(lower.charAt(end));
        return left && right;
    }

    private static String cleanEngineLine(String line) {
        if (line == null) return "";
        String s = line.trim();
        if (s.length() > 0 && s.charAt(0) == '﻿') s = s.substring(1).trim();
        return s;
    }

    private String readBestMove(StringBuilder raw, long timeoutMs, long requestGeneration)
            throws IOException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < effectiveTimedSearchDeadline(
                deadline, requestGeneration)
                && (isSearchGenerationCurrent(requestGeneration)
                || isTimedSearchStopRequested(requestGeneration))) {
            long waitDeadline = effectiveTimedSearchDeadline(deadline, requestGeneration);
            String line = pollLine(waitDeadline - System.currentTimeMillis());
            if (line == null) continue;
            raw.append(line).append('\n');
            if (line.startsWith("__ENGINE_IO_EXCEPTION__")) throw new IOException(line);
            if (line.startsWith("__ENGINE_EOF__")) throw new IOException(engineExitMessage("等待 bestmove 时引擎已退出"));
            if (line.startsWith("info ")) continue;
            if (line.startsWith("nobestmove")) return null;
            if (line.startsWith("bestmove")) {
                String[] parts = line.split("\\s+");
                return parts.length >= 2 ? parts[1].trim().toLowerCase(Locale.ROOT) : null;
            }
        }
        return null;
    }

    private String engineExitMessage(String prefix) {
        String code = "";
        try {
            if (process != null && !process.isAlive()) code = "，退出码=" + process.exitValue();
        } catch (Exception ignored) {}
        return prefix + code + "，启动方式=" + (activeLaunchMode.length() == 0 ? "未知" : activeLaunchMode);
    }

    private EngineInfo parseInfo(String line) {
        try {
            String[] t = line.trim().split("\\s+");
            EngineInfo info = new EngineInfo();
            for (int i = 0; i < t.length; i++) {
                String key = t[i];
                if ("depth".equals(key) && i + 1 < t.length) info.depth = safeInt(t[++i], -1);
                else if ("seldepth".equals(key) && i + 1 < t.length) info.selDepth = safeInt(t[++i], -1);
                else if ("multipv".equals(key) && i + 1 < t.length) info.multiPv = Math.max(1, safeInt(t[++i], 1));
                else if ("nodes".equals(key) && i + 1 < t.length) info.nodes = safeLong(t[++i], -1);
                else if ("hashfull".equals(key) && i + 1 < t.length) info.hashFull = safeInt(t[++i], -1);
                else if ("nps".equals(key) && i + 1 < t.length) info.nps = safeLong(t[++i], -1);
                else if ("time".equals(key) && i + 1 < t.length) info.timeMs = safeLong(t[++i], -1);
                else if ("wdl".equals(key) && i + 3 < t.length) {
                    info.hasWdl = true;
                    info.wdlWin = safeInt(t[++i], -1);
                    info.wdlDraw = safeInt(t[++i], -1);
                    info.wdlLoss = safeInt(t[++i], -1);
                }
                else if ("score".equals(key) && i + 1 < t.length) {
                    String first = t[++i];
                    info.hasScore = true;
                    if (("cp".equals(first) || "mate".equals(first)) && i + 1 < t.length) {
                        info.mateScore = "mate".equals(first);
                        info.score = safeInt(t[++i], 0);
                    } else {
                        // UCCI: info depth 6 score 4 pv ...
                        info.mateScore = false;
                        info.score = safeInt(first, 0);
                    }
                } else if ("pv".equals(key)) {
                    for (int j = i + 1; j < t.length; j++) {
                        String move = t[j].toLowerCase(Locale.ROOT);
                        if (isUsableBestMove(move)) info.pv.add(move.substring(0, 4));
                    }
                    break;
                }
            }
            return info.pv.isEmpty() ? null : info;
        } catch (Exception ignored) {
            return null;
        }
    }

    private String pollLine(long waitMs) {
        try {
            return lineQueue.poll(Math.max(1, Math.min(waitMs, 300)), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    private void send(String command) throws IOException {
        synchronized (ioLock) {
            if (writer == null) throw new IOException("引擎 stdin 未打开");
            writer.write(command);
            writer.write('\n');
            writer.flush();
        }
    }

    private void drainOldLines() {
        synchronized (ioLock) {
            List<String> ignored = new ArrayList<String>();
            lineQueue.drainTo(ignored);
        }
    }

    /**
     * stdout 的 reader 与页面切换/进程重建共用 lineQueue。检查代数后再 offer
     * 若不在同一把锁内，hardStop() 清队列后仍可能收到旧进程最后一行输出。
     */
    private boolean offerLineIfCurrent(Process source, long generation, String line) {
        synchronized (ioLock) {
            if (closed || generation != processGeneration || source != process) return false;
            return lineQueue.offer(line == null ? "" : line);
        }
    }

    private void clearLineQueue() {
        synchronized (ioLock) {
            lineQueue.clear();
        }
    }


    private void parseUciOptions(String raw) {
        parsedOptions.clear();
        if (raw == null) return;
        String[] lines = raw.split("\\n");
        for (String line : lines) {
            EngineOption option = parseOptionLine(line);
            if (option != null && option.name.length() > 0) {
                if (pendingOptions.containsKey(option.name)) option.currentValue = pendingOptions.get(option.name);
                parsedOptions.put(option.name, option);
            }
        }
    }

    private EngineOption parseOptionLine(String line) {
        if (line == null || !line.startsWith("option ")) return null;
        String[] t = line.trim().split("\\s+");
        if (t.length < 3) return null;
        EngineOption o = new EngineOption();
        StringBuilder name = new StringBuilder();
        int i = (t.length > 1 && "name".equals(t[1])) ? 2 : 1;
        while (i < t.length && !"type".equals(t[i])) {
            if (name.length() > 0) name.append(' ');
            name.append(t[i++]);
        }
        o.name = name.toString();
        if (i < t.length && "type".equals(t[i]) && i + 1 < t.length) o.type = t[++i];
        i++;
        while (i < t.length) {
            String key = t[i++];
            if ("default".equals(key)) {
                StringBuilder val = new StringBuilder();
                while (i < t.length && !"min".equals(t[i]) && !"max".equals(t[i]) && !"var".equals(t[i])) {
                    if (val.length() > 0) val.append(' ');
                    val.append(t[i++]);
                }
                o.defaultValue = val.toString();
                o.currentValue = o.defaultValue;
            } else if ("min".equals(key) && i < t.length) {
                o.min = safeInt(t[i++], Integer.MIN_VALUE);
            } else if ("max".equals(key) && i < t.length) {
                o.max = safeInt(t[i++], Integer.MAX_VALUE);
            } else if ("var".equals(key)) {
                StringBuilder val = new StringBuilder();
                while (i < t.length && !"var".equals(t[i]) && !"default".equals(t[i]) && !"min".equals(t[i]) && !"max".equals(t[i])) {
                    if (val.length() > 0) val.append(' ');
                    val.append(t[i++]);
                }
                if (val.length() > 0) o.vars.add(val.toString());
            }
        }
        return o;
    }


    private boolean optionExists(String uciText, String name) {
        return findParsedOptionName(name) != null;
    }

    private String findParsedOptionName(String requested) {
        if (requested == null) return null;
        for (String actual : parsedOptions.keySet()) {
            if (actual.equalsIgnoreCase(requested)) return actual;
        }
        // UCCI 3.0 标准名为 hashsize，UCI 通常名为 Hash。
        if (EngineProbe.PROTOCOL_UCCI.equals(activeProtocol) && "Hash".equalsIgnoreCase(requested)) {
            for (String actual : parsedOptions.keySet()) {
                if ("hashsize".equalsIgnoreCase(actual)) return actual;
            }
        }
        return null;
    }

    private static boolean isEvalFileOption(String name) {
        return name != null && OPTION_EVAL_FILE.equalsIgnoreCase(name.trim());
    }

    private void removePendingOptionIgnoreCase(String name) {
        if (name == null) return;
        ArrayList<String> remove = new ArrayList<String>();
        for (String key : pendingOptions.keySet()) {
            if (name.equalsIgnoreCase(key)) remove.add(key);
        }
        for (String key : remove) pendingOptions.remove(key);
    }

    private String evalPreferenceKey(EngineDescriptor descriptor) {
        String identity;
        if (descriptor == null) identity = "@none";
        else identity = descriptor.bundled ? "@bundled" : descriptor.path;
        return PREF_ENGINE_EVAL_PREFIX + shortHash("eval:" + identity);
    }

    /**
     * 返回 null 表示该引擎从未保存过 EvalFile；空串则表示用户明确保存了空值。
     */
    private String loadScopedEvalFile(EngineDescriptor descriptor) {
        try {
            SharedPreferences sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            Object value = sp.getAll().get(evalPreferenceKey(descriptor));
            return value instanceof String ? (String) value : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private void removeLegacyGlobalEvalPreferences(SharedPreferences sp, SharedPreferences.Editor edit) {
        if (sp == null || edit == null) return;
        try {
            for (String key : sp.getAll().keySet()) {
                if (isEvalFileOption(key)) edit.remove(key);
            }
        } catch (Exception ignored) {}
    }

    /**
     * 握手完成后为当前引擎单独决定 EvalFile：
     * 1. 优先解析当前会话/设置页中的 EvalFile，但只接受实际存在的文件；
     * 2. 再读取该引擎自己的隔离保存值，并丢弃覆盖安装后失效的旧路径；
     * 3. APK 内置/虚拟位引擎优先使用 nativeLibraryDir/libpikafish.nnue.so；
     * 4. 外部引擎按 option default 与工作目录中的唯一网络文件自动匹配；
     * 5. 最终把 session/pending 中的 EvalFile 统一为同一个绝对路径，禁止相对名二次覆盖。
     */
    private void configureEvalFileForEngine(EngineDescriptor descriptor, File engine, File workDir) {
        String actualName = findParsedOptionName(OPTION_EVAL_FILE);
        // V15.1: UCI 握手输出被截断或 parser 未识别 EvalFile 时，
        // 回退到字面名称继续解析，确保 setoption 一定下发。
        if (actualName == null) actualName = OPTION_EVAL_FILE;
        removePendingOptionIgnoreCase(OPTION_EVAL_FILE);
        activeEvalFile = "";

        EngineOption option = parsedOptions.get(actualName);
        if (usesEmbeddedDufNetwork()) {
            // 当前 Duffish 2.2 二进制的 .rodata 中已经嵌入完整 xiangqi 网络。
            // 发送绝对 EvalFile 会迫使它绕过内嵌网络并尝试外部加载，部分设备会在首个 go 退出。
            removeSessionOptionIgnoreCase(OPTION_EVAL_FILE);
            removePendingOptionIgnoreCase(OPTION_EVAL_FILE);
            if (option != null) option.currentValue = option.defaultValue;
            lastSentEvalFileCmd = "(duf 使用 libduf.so 内嵌 NNUE，未发送 EvalFile)";
            try {
                SharedPreferences sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
                SharedPreferences.Editor edit = sp.edit();
                removeLegacyGlobalEvalPreferences(sp, edit);
                edit.remove(evalPreferenceKey(descriptor));
                edit.apply();
            } catch (Exception ignored) {}
            return;
        }
        String sessionKey = findKeyIgnoreCase(sessionOptions, OPTION_EVAL_FILE);
        String sessionValue = sessionKey == null ? null : sessionOptions.get(sessionKey);
        String selected = canonicalExistingEvalFile(sessionValue, workDir);

        // 没有有效会话路径时，再读取按引擎隔离的保存值。覆盖安装会改变
        // /data/app/.../lib 路径，因此不存在的旧绝对路径必须立即丢弃。
        String scoped = loadScopedEvalFile(descriptor);
        if (selected == null && scoped != null) {
            selected = canonicalExistingEvalFile(scoped, workDir);
            if (selected == null) {
                try {
                    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                            .edit().remove(evalPreferenceKey(descriptor)).apply();
                } catch (Exception ignored) {}
            }
        }
        if (selected == null) {
            File automatic = resolveAutomaticEvalFile(descriptor, engine, workDir, option);
            if (automatic != null) selected = canonicalPath(automatic);
        }

        if (selected != null) {
            // 关键修复：会话参数在 pendingOptions 之后发送。若保留原始
            // EvalFile=pikafish.nnue，它会覆盖这里解析出的绝对路径。
            if (sessionKey != null) {
                removeSessionOptionIgnoreCase(OPTION_EVAL_FILE);
                sessionOptions.put(actualName, selected);
            } else {
                pendingOptions.put(actualName, selected);
            }
            activeEvalFile = selected;
            if (option != null) option.currentValue = selected;
        } else {
            // V15.3: 内置引擎无法自动解析时，最后尝试以 sessionValue 或 NNUE_FILE 直接匹配。
            File fb = resolveEvalFileNamedByOption(workDir,
                    sessionValue != null && sessionValue.length() > 0 ? sessionValue : NNUE_FILE);
            if (fb != null) {
                removeSessionOptionIgnoreCase(OPTION_EVAL_FILE);
                if (sessionKey != null) sessionOptions.put(actualName, fb.getAbsolutePath());
                else pendingOptions.put(actualName, fb.getAbsolutePath());
                activeEvalFile = fb.getAbsolutePath();
                if (option != null) option.currentValue = fb.getAbsolutePath();
            } else {
                String explicit = sessionValue == null ? "" : sessionValue.trim();
                boolean keepExplicitExternal = explicit.length() > 0 && new File(explicit).isAbsolute()
                        && !isApkNativeEngine(engine);
                if (keepExplicitExternal) {
                    removeSessionOptionIgnoreCase(OPTION_EVAL_FILE);
                    sessionOptions.put(actualName, explicit);
                    activeEvalFile = explicit;
                    if (option != null) option.currentValue = explicit;
                } else {
                    removeSessionOptionIgnoreCase(OPTION_EVAL_FILE);
                    if (option != null) option.currentValue = option.defaultValue;
                }
            }
        }

        // 一次性清理旧版全局 EvalFile，防止降级/重进页面后再次污染。
        try {
            SharedPreferences sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            SharedPreferences.Editor edit = sp.edit();
            removeLegacyGlobalEvalPreferences(sp, edit);
            edit.apply();
        } catch (Exception ignored) {}
    }

    private File resolveAutomaticEvalFile(EngineDescriptor descriptor, File engine, File workDir,
                                          EngineOption option) {
        if (workDir == null || !workDir.isDirectory()) return null;

        // duf 使用 libduf.so 内嵌网络，绝不自动匹配外置 EvalFile。
        if (usesEmbeddedDufNetwork()) return null;
        if (isApkNativeEngine(engine) || (descriptor != null && descriptor.bundled)) {
            for (String name : new String[]{NNUE_FILE, LEGACY_NNUE_FILE, ENGINE_DEFAULT_NNUE_FILE}) {
                File candidate = new File(workDir, name);
                if (candidate.isFile()) return candidate;
            }
        }

        // 大多数 UCI 引擎会在 option default 中给出配套网络的相对文件名。
        File byDefault = resolveEvalFileNamedByOption(workDir, option == null ? "" : option.defaultValue);
        if (byDefault != null) return byDefault;

        File[] listed = workDir.listFiles();
        if (listed == null) return null;
        ArrayList<File> networks = new ArrayList<File>();
        for (File file : listed) {
            if (file != null && file.isFile() && isStrongEvalNetworkName(file.getName())) networks.add(file);
        }
        if (networks.size() == 1) return networks.get(0);
        if (networks.size() <= 1 || engine == null) return null;

        // 多网络目录只做唯一的同名匹配；评分并列时宁可不猜，避免给错网络。
        String engineStem = normalizedEngineStem(engine.getName());
        File matched = null;
        for (File network : networks) {
            String networkStem = normalizedNetworkStem(network.getName());
            boolean related = engineStem.length() > 0 && networkStem.length() > 0
                    && (engineStem.equals(networkStem) || engineStem.contains(networkStem)
                    || networkStem.contains(engineStem));
            if (!related) continue;
            if (matched != null) return null;
            matched = network;
        }
        if (matched != null) return matched;

        for (String fallback : new String[]{NNUE_FILE, LEGACY_NNUE_FILE, ENGINE_DEFAULT_NNUE_FILE}) {
            File fb = new File(workDir, fallback);
            if (fb.isFile()) return fb;
        }
        return null;
    }

    private File resolveEvalFileNamedByOption(File workDir, String optionValue) {
        if (workDir == null || optionValue == null) return null;
        String value = optionValue.trim();
        if (value.length() >= 2 && ((value.startsWith("\"") && value.endsWith("\""))
                || (value.startsWith("'") && value.endsWith("'")))) {
            value = value.substring(1, value.length() - 1).trim();
        }
        if (value.length() == 0 || "<empty>".equalsIgnoreCase(value)
                || "none".equalsIgnoreCase(value)) return null;
        File requested = new File(value);
        if (requested.isAbsolute() && requested.isFile()) return requested;
        String base = requested.getName();
        if (base.length() == 0) return null;

        ArrayList<String> names = new ArrayList<String>();
        addUniqueName(names, base);
        if (!base.toLowerCase(Locale.ROOT).endsWith(".so")) addUniqueName(names, base + ".so");
        if (!base.toLowerCase(Locale.ROOT).startsWith("lib")) {
            addUniqueName(names, "lib" + base);
            if (!base.toLowerCase(Locale.ROOT).endsWith(".so")) addUniqueName(names, "lib" + base + ".so");
        }
        for (String name : names) {
            File candidate = new File(workDir, name);
            if (candidate.isFile()) return candidate;
        }
        return null;
    }

    private static void addUniqueName(List<String> names, String value) {
        if (names == null || value == null || value.length() == 0 || names.contains(value)) return;
        names.add(value);
    }

    private static String findKeyIgnoreCase(Map<String, String> values, String wanted) {
        if (values == null || wanted == null) return null;
        for (String key : values.keySet()) {
            if (key != null && key.equalsIgnoreCase(wanted)) return key;
        }
        return null;
    }

    private void removeSessionOptionIgnoreCase(String wanted) {
        String key;
        while ((key = findKeyIgnoreCase(sessionOptions, wanted)) != null) sessionOptions.remove(key);
    }

    private String canonicalExistingEvalFile(String value, File workDir) {
        if (value == null) return null;
        String v = value.trim();
        if (v.length() == 0) return null;
        File direct = new File(v);
        if (!direct.isAbsolute() && workDir != null) direct = new File(workDir, v);
        if (direct.isFile()) return canonicalPath(direct);
        File resolved = resolveEvalFileNamedByOption(workDir, v);
        return resolved == null ? null : canonicalPath(resolved);
    }

    private static String canonicalPath(File file) {
        if (file == null) return null;
        try { return file.getCanonicalPath(); }
        catch (IOException ignored) { return file.getAbsolutePath(); }
    }

    private String resolveEvalFileValueForCurrentEngine(String value) {
        if (usesEmbeddedDufNetwork()) return "";
        String requested = value == null ? "" : value.trim();
        if (requested.length() == 0) return "";
        EngineDescriptor descriptor = activeEngine != null ? activeEngine : resolveSelectedEngine();
        File engine = null;
        if (descriptor != null) {
            engine = descriptor.bundled ? resolveBundledEngineBinary() : new File(descriptor.path);
        }
        File workDir = engine == null ? getNativeLibraryDir() : engine.getParentFile();
        String resolved = canonicalExistingEvalFile(requested, workDir);
        if (resolved != null) return resolved;
        if (isApkNativeEngine(engine)) {
            File automatic = resolveAutomaticEvalFile(descriptor, engine, workDir,
                    parsedOptions.get(findParsedOptionName(OPTION_EVAL_FILE)));
            if (automatic != null) return canonicalPath(automatic);
        }
        return requested;
    }

    private boolean usesEmbeddedDufNetwork() {
        return VIRTUAL_ENGINE_DUF.equals(virtualEngineSlot);
    }

    private boolean isApkNativeEngine(File engine) {
        if (engine == null) return false;
        try {
            File parent = engine.getCanonicalFile().getParentFile();
            return parent != null && parent.equals(getNativeLibraryDir().getCanonicalFile());
        } catch (IOException ignored) {
            File parent = engine.getAbsoluteFile().getParentFile();
            return parent != null && parent.equals(getNativeLibraryDir().getAbsoluteFile());
        }
    }

    private void sendActiveEvalFileIfConfigured() throws IOException {
        if (activeEvalFile == null || activeEvalFile.length() == 0) return;
        File file = new File(activeEvalFile);
        if (!file.isFile()) {
            throw new IOException("准备搜索时 EvalFile 已不存在：" + activeEvalFile
                    + "；候选=" + describeEvalFileCandidates());
        }
        String cmd = optionCommand(OPTION_EVAL_FILE, canonicalPath(file));
        // 同一路径已经下发过时不重复 setoption。部分引擎会因此重新加载 NNUE，
        // 每个局面都重发会制造明显空窗并丢掉本可用于快速出招的思考时间。
        if (cmd.equals(lastSentEvalFileCmd)) return;
        send(cmd);
        lastSentEvalFileCmd = cmd;
    }

    private String describeEvalFileCandidates() {
        File dir = getNativeLibraryDir();
        StringBuilder out = new StringBuilder(dir.getAbsolutePath())
                .append("; dufNNUE=内嵌于libduf.so(无需外置EvalFile)");
        for (String name : new String[]{
                DUF_NNUE_FILE, LEGACY_DUF_NNUE_FILE, DUF_NNUE_OPTION,
                NNUE_FILE, LEGACY_NNUE_FILE, ENGINE_DEFAULT_NNUE_FILE}) {
            File file = new File(dir, name);
            out.append("; ").append(name).append('=').append(file.isFile() ? "存在" : "缺失");
            if (file.isFile()) out.append('(').append(file.length()).append(" bytes)");
        }
        return out.toString();
    }

    private String searchFailureDiagnostics(Exception error, StringBuilder raw) {
        StringBuilder out = new StringBuilder();
        out.append(error.getClass().getSimpleName()).append(": ").append(error.getMessage());
        out.append("\n[诊断] activeEvalFile=")
                .append(activeEvalFile == null || activeEvalFile.length() == 0 ? "(未设置)" : activeEvalFile);
        if (activeEvalFile != null && activeEvalFile.length() > 0) {
            File active = new File(activeEvalFile);
            out.append("; 文件=").append(active.isFile() ? "存在" : "缺失");
            if (active.isFile()) out.append('(').append(active.length()).append(" bytes)");
        }
        out.append("\n[诊断] 实际 EvalFile 命令=").append(lastSentEvalFileCmd);
        out.append("\n[诊断] 安装目录候选=").append(describeEvalFileCandidates());
        String text = raw == null || raw.length() == 0 ? "(go 后无输出)" : raw.toString();
        if (text.length() > 2400) text = text.substring(text.length() - 2400);
        out.append("\n[诊断] go 后引擎输出=\n").append(text);
        return out.toString();
    }

    private static boolean isStrongEvalNetworkName(String name) {
        if (name == null) return false;
        String n = name.toLowerCase(Locale.ROOT);
        return n.endsWith(".nnue") || n.endsWith(".nnue.so")
                || n.endsWith(".weights") || n.endsWith(".network");
    }

    private static String normalizedEngineStem(String name) {
        String n = name == null ? "" : name.toLowerCase(Locale.ROOT);
        if (n.endsWith(".so")) n = n.substring(0, n.length() - 3);
        n = n.replaceAll("(?i)([-_.](armv8|arm64|aarch64|android|dotprod|neon))+$", "");
        if (n.startsWith("lib") && n.length() > 3) n = n.substring(3);
        return n.replaceAll("[^a-z0-9]+", "");
    }

    private static String normalizedNetworkStem(String name) {
        String n = name == null ? "" : name.toLowerCase(Locale.ROOT);
        String[] suffixes = new String[]{".nnue.so", ".nnue", ".weights", ".network"};
        for (String suffix : suffixes) {
            if (n.endsWith(suffix)) {
                n = n.substring(0, n.length() - suffix.length());
                break;
            }
        }
        if (n.startsWith("lib") && n.length() > 3) n = n.substring(3);
        return n.replaceAll("[^a-z0-9]+", "");
    }

    private String normalizeOptionValue(EngineOption option, String value) {
        String v = value == null ? "" : value.trim();
        if (option == null || !option.isSpin()) return v;
        int parsed = safeInt(v, safeInt(option.defaultValue, 0));
        if (option.min != Integer.MIN_VALUE) parsed = Math.max(option.min, parsed);
        if (option.max != Integer.MAX_VALUE) parsed = Math.min(option.max, parsed);
        return String.valueOf(parsed);
    }

    private String optionCommand(String name, String value) {
        String n = name == null ? "" : name.trim();
        String v = value == null ? "" : value.trim();
        if (EngineProbe.PROTOCOL_UCCI.equals(activeProtocol)) {
            return "setoption " + n + (v.length() == 0 ? "" : " " + v);
        }
        return "setoption name " + n + (v.length() == 0 ? "" : " value " + v);
    }

    private EngineDescriptor resolveSelectedEngine() {
        String slot = normalizeVirtualEngineSlot(virtualEngineSlot);
        if (slot.length() > 0) {
            File file = resolveVirtualEngineBinary(slot);
            String path = file == null ? new File(getNativeLibraryDir(), virtualEngineFileName(slot)).getAbsolutePath() : file.getAbsolutePath();
            return new EngineDescriptor(slot, path, EngineProbe.PROTOCOL_UCI, false);
        }
        try {
            SharedPreferences sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            String path = sp.getString(PREF_SELECTED_ENGINE_PATH, "");
            String protocol = sp.getString(PREF_SELECTED_ENGINE_PROTOCOL, EngineProbe.PROTOCOL_UCI);
            if (path != null && path.length() > 0) {
                File file = new File(path);
                if (file.isFile()) {
                    for (EngineDescriptor d : loadImportedEngines()) {
                        // 扫描记录是协议识别的最新结果，优先于可能残留的旧选择协议。
                        if (d.path.equals(path)) return new EngineDescriptor(d.name, path, d.protocol, false);
                    }
                    return new EngineDescriptor(file.getName(), path, protocol, false);
                }
            }
        } catch (Exception ignored) {}
        File bundled = resolveBundledEngineBinary();
        return bundled == null ? null : new EngineDescriptor("皮卡鱼", "", EngineProbe.PROTOCOL_UCI, true);
    }

    private static String normalizeVirtualEngineSlot(String slot) {
        if (slot == null) return "";
        String value = slot.trim();
        if (VIRTUAL_ENGINE_HCE.equalsIgnoreCase(value)) return VIRTUAL_ENGINE_HCE;
        if (VIRTUAL_ENGINE_DUF.equalsIgnoreCase(value)) return VIRTUAL_ENGINE_DUF;
        if (VIRTUAL_ENGINE_131.equalsIgnoreCase(value)) return VIRTUAL_ENGINE_131;
        return "";
    }

    private static String virtualEngineFileName(String slot) {
        if (VIRTUAL_ENGINE_HCE.equals(slot)) return "libHCE.so";
        if (VIRTUAL_ENGINE_DUF.equals(slot)) return "libduf.so";
        if (VIRTUAL_ENGINE_131.equals(slot)) return "lib131.so";
        return "";
    }

    private File resolveVirtualEngineBinary(String slot) {
        String fileName = virtualEngineFileName(slot);
        if (fileName.length() == 0) return null;
        File dir = getNativeLibraryDir();
        File exact = new File(dir, fileName);
        if (exact.isFile()) return exact;
        // 兼容部分构建链或文件系统把 HCE 文件名改为小写。
        if (VIRTUAL_ENGINE_HCE.equals(slot)) {
            File lower = new File(dir, "libhce.so");
            if (lower.isFile()) return lower;
        }
        return exact;
    }

    private File resolveBundledEngineBinary() {
        File dir = getNativeLibraryDir();
        File safe = new File(dir, ENGINE_ARMV8);
        if (safe.exists()) return safe;
        File dot = new File(dir, ENGINE_ARMV8_DOTPROD);
        if (dot.exists()) return dot;
        return null;
    }

    private List<EngineDescriptor> loadImportedEngines() {
        ArrayList<EngineDescriptor> out = new ArrayList<EngineDescriptor>();
        try {
            Set<String> raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .getStringSet(PREF_IMPORTED_ENGINES, Collections.<String>emptySet());
            if (raw == null) return out;
            for (String item : raw) {
                String[] parts = item.split("\u001f", -1);
                if (parts.length < 3) continue;
                File file = new File(parts[0]);
                if (file.isFile()) out.add(new EngineDescriptor(parts[2], parts[0], parts[1], false));
            }
        } catch (Exception ignored) {}
        return out;
    }

    private void replaceImportedEnginesForFolder(File folder, List<EngineDescriptor> discovered) {
        if (folder == null) return;
        try {
            String canonicalFolder = folder.getCanonicalPath();
            SharedPreferences sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            Set<String> old = sp.getStringSet(PREF_IMPORTED_ENGINES, Collections.<String>emptySet());
            HashSet<String> next = old == null ? new HashSet<String>() : new HashSet<String>(old);
            ArrayList<String> remove = new ArrayList<String>();
            for (String item : next) {
                String[] parts = item.split("\u001f", -1);
                if (parts.length < 1) continue;
                File parent = new File(parts[0]).getParentFile();
                if (parent != null && canonicalFolder.equals(parent.getCanonicalPath())) remove.add(item);
            }
            next.removeAll(remove);
            if (discovered != null) {
                for (EngineDescriptor descriptor : discovered) {
                    if (descriptor == null || descriptor.bundled || descriptor.path.length() == 0) continue;
                    next.add(descriptor.path + "\u001f" + descriptor.protocol + "\u001f" + descriptor.name);
                }
            }
            sp.edit().putStringSet(PREF_IMPORTED_ENGINES, next).apply();
        } catch (Exception ignored) {}
    }

    private boolean isReservedPreference(String key) {
        return PREF_SELECTED_ENGINE_PATH.equals(key)
                || PREF_SELECTED_ENGINE_PROTOCOL.equals(key)
                || PREF_IMPORTED_ENGINES.equals(key)
                || PREF_LAST_ENGINE_FOLDER.equals(key)
                || (key != null && key.startsWith(PREF_ENGINE_EVAL_PREFIX));
    }


    private File getNativeLibraryDir() {
        ApplicationInfo info = context.getApplicationInfo();
        if (info != null && info.nativeLibraryDir != null) return new File(info.nativeLibraryDir);
        return new File(context.getFilesDir(), "lib-fallback");
    }

    private synchronized void hardStop() {
        Process oldProcess;
        Thread oldReader;
        synchronized (ioLock) {
            processGeneration++;
            uciReady = false;
            ucciUsesMillisec = false;
            readyCommandSupported = true;
            activeLaunchMode = "";
            activeEvalFile = "";
            lastSentEvalFileCmd = null;
            analysisRunning = false;
            analysisSearchActive = false;
            analysisSearchStartedAtMs = 0L;
            analysisBestMoveRequested = false;
            analysisStopRequested = false;
            analysisStopRequestedAtMs = 0L;
            timedSearchActive = false;
            timedSearchGeneration = 0L;
            timedSearchStopRequested = false;
            timedSearchStopRequestedAtMs = 0L;
            timedSearchProcess = null;
            oldProcess = process;
            oldReader = readerThread;
            // 与 reader 的“检查当前进程 + 入队”原子配对，彻底丢弃旧 stdout。
            process = null;
            writer = null;
            readerThread = null;
            lineQueue.clear();
        }
        if (oldReader != null) oldReader.interrupt();
        try {
            if (oldProcess != null) {
                oldProcess.destroy();
                // destroy() 是异步的。短暂等待可避免切换时旧、新两个高内存引擎并存。
                long deadline = System.currentTimeMillis() + 220L;
                while (oldProcess.isAlive() && System.currentTimeMillis() < deadline) {
                    try { Thread.sleep(20L); }
                    catch (InterruptedException e) { Thread.currentThread().interrupt(); break; }
                }
                if (oldProcess.isAlive()) {
                    // destroyForcibly 在较老 Android 上并非始终可直接调用，使用反射安全降级。
                    try { Process.class.getMethod("destroyForcibly").invoke(oldProcess); }
                    catch (Exception ignored) { oldProcess.destroy(); }
                }
            }
        } catch (Exception ignored) {}
        clearLineQueue();
    }

    private boolean isUsableBestMove(String move) {
        if (move == null || move.length() < 4) return false;
        String m = move.toLowerCase(Locale.ROOT);
        return m.charAt(0) >= 'a' && m.charAt(0) <= 'i'
                && m.charAt(1) >= '0' && m.charAt(1) <= '9'
                && m.charAt(2) >= 'a' && m.charAt(2) <= 'i'
                && m.charAt(3) >= '0' && m.charAt(3) <= '9';
    }

    private int safeInt(String s, int def) {
        try { return Integer.parseInt(s); } catch (Exception e) { return def; }
    }

    private long safeLong(String s, long def) {
        try { return Long.parseLong(s); } catch (Exception e) { return def; }
    }
}
