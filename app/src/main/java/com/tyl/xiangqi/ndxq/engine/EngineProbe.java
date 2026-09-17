package com.tyl.xiangqi.ndxq.engine;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * 轻量级外部引擎探测与启动工具。
 *
 * Android 10+ 会阻止应用直接 exec 可写目录或共享存储中的二进制。对带 PT_INTERP 的
 * 可写动态 ARM64 ELF 优先由 /system/bin/linker64 读取并启动；对静态 ARM64 ELF 则可选用
 * APK 内置的轻量静态 ELF 映射器；映射器先尝试内核 execveat，受拒后在同一辅助进程中映射 PT_LOAD；对 APK 内置只读引擎仍保留普通 ProcessBuilder
 * 路径。探测和正式运行共用本启动器，避免“扫描成功但切换失败”。
 */
public final class EngineProbe {
    public static final String PROTOCOL_UCCI = "UCCI";
    public static final String PROTOCOL_UCI = "UCI";

    private static final int ELF_MIN_HEADER_SIZE = 20;
    private static final int ELF64_HEADER_SIZE = 64;
    private static final int EM_AARCH64 = 183;
    private static final int ET_EXEC = 2;
    private static final int ET_DYN = 3;
    private static final int PT_INTERP = 3;
    private static final int MAX_PROGRAM_HEADERS = 128;
    private static final String EOF_MARKER = "__ENGINE_PROBE_EOF__";
    private static volatile File configuredStaticExecLoader;
    private static final String IO_MARKER = "__ENGINE_PROBE_IO__ ";

    private EngineProbe() {}

    public static final class Result {
        public final boolean success;
        public final String protocol;
        public final String engineName;
        public final String message;

        private Result(boolean success, String protocol, String engineName, String message) {
            this.success = success;
            this.protocol = protocol == null ? "" : protocol;
            this.engineName = engineName == null ? "" : engineName;
            this.message = message == null ? "" : message;
        }

        public static Result success(String protocol, String engineName, String message) {
            return new Result(true, protocol, engineName, message);
        }

        public static Result failure(String protocol, String message) {
            return new Result(false, protocol, "", message);
        }
    }

    /** 启动结果包含实际采用的方式，便于把 Android 权限问题写进诊断。 */
    public static final class StartedProcess {
        public final Process process;
        public final String launchMode;

        private StartedProcess(Process process, String launchMode) {
            this.process = process;
            this.launchMode = launchMode == null ? "" : launchMode;
        }
    }

    private static final class ElfInfo {
        boolean arm64;
        int type;
        long entryPoint;
        boolean dynamicExecutable;
    }

    /**
     * 配置 APK 内置的静态 ELF 兼容器。该文件必须位于只读 nativeLibraryDir，
     * 由系统安装流程提取并赋予可执行权限；外置可写文件不能充当兼容器。
     */
    static void configureStaticExecLoader(File loader) {
        configuredStaticExecLoader = loader;
    }

    /** 仅接受 64 位小端 AArch64 ELF，避免误启动普通文件、NNUE 或其他 ABI。 */
    public static boolean isArm64Elf(File file) {
        return inspectElf(file).arm64;
    }

    /** 供 SAF 流式扫描使用；只读取 64 字节，不把整个文件载入内存。 */
    public static boolean isArm64Elf(InputStream input) {
        if (input == null) return false;
        byte[] h = new byte[ELF64_HEADER_SIZE];
        int off = 0;
        try {
            while (off < h.length) {
                int n = input.read(h, off, h.length - off);
                if (n < 0) break;
                off += n;
            }
        } catch (IOException e) {
            return false;
        }
        return isArm64Header(h, off);
    }

    /** 是否可由 Android 系统动态链接器直接装载；静态 ELF 没有 PT_INTERP。 */
    static boolean hasProgramInterpreter(File file) {
        return inspectElf(file).dynamicExecutable;
    }

    /** 接受传统静态 ET_EXEC 和无 PT_INTERP、带入口点的静态 PIE ET_DYN。 */
    static boolean isStaticArm64Executable(File file) {
        ElfInfo info = inspectElf(file);
        return info.arm64 && !info.dynamicExecutable && info.entryPoint != 0L
                && (info.type == ET_EXEC || info.type == ET_DYN);
    }

    /**
     * 识别同目录中的 ARM64 依赖库，避免把 libxxx.so 当成引擎逐个握手并提前删除。
     * 普通共享库通常是 ET_DYN、没有 PT_INTERP；仅对 .so/.so.* 名称作此判定，
     * 无后缀的静态可执行文件仍会进入静态兼容探测。
     */
    static boolean isArm64DependencyLibrary(File file) {
        ElfInfo info = inspectElf(file);
        if (!info.arm64 || info.dynamicExecutable || info.type != ET_DYN
                || info.entryPoint != 0L || file == null) return false;
        String name = file.getName().toLowerCase(Locale.ROOT);
        return name.endsWith(".so") || name.contains(".so.");
    }

    /**
     * 启动引擎：普通 exec 优先；失败后，动态 ARM64 ELF 使用 linker64，静态 ARM64
     * ELF 使用 APK 内置静态 ELF 映射器。该方法同时供探测和正式运行使用，
     * 避免“扫描成功、切换时却失败”的双重实现。
     */
    public static StartedProcess startEngineProcess(File engine, File workDir) throws IOException {
        if (engine == null || !engine.isFile()) throw new IOException("引擎文件不存在");
        File executable;
        try {
            executable = engine.getCanonicalFile();
        } catch (IOException e) {
            executable = engine.getAbsoluteFile();
        }
        File directory = workDir;
        if (directory == null || !directory.isDirectory()) directory = executable.getParentFile();

        ElfInfo earlyElf = inspectElf(executable);
        IOException preferredCompatError = null;
        // SAF 导入后的外置文件位于应用私有可写目录。Android 10+ 对这种文件直接
        // exec 通常会触发 W^X；因此动态目标优先 linker64，静态目标优先 APK
        // 内置静态 ELF 映射器。这样探测和正式切换不会先创建一个必然早退的进程。
        if (earlyElf.arm64 && (executable.canWrite() || !earlyElf.dynamicExecutable)) {
            try {
                if (earlyElf.dynamicExecutable) {
                    return startEngineProcessWithLinker(executable, directory);
                }
                return startEngineProcessWithStaticLoader(executable, directory);
            } catch (IOException e) {
                preferredCompatError = e;
            }
        }
        IOException directError;
        try {
            if (!executable.canExecute()) {
                try { executable.setExecutable(true, false); } catch (SecurityException ignored) {}
            }
            return startCommand(singleton(executable.getAbsolutePath()), directory, false, "直接 exec");
        } catch (IOException e) {
            directError = e;
        } catch (SecurityException e) {
            directError = new IOException("直接 exec 被系统拒绝: " + safeMessage(e), e);
        }

        ElfInfo elf = earlyElf.arm64 ? earlyElf : inspectElf(executable);
        if (!elf.arm64) {
            throw combinedLaunchError("直接启动失败，且文件不是 64 位 ARMv8 ELF", directError, preferredCompatError);
        }
        if (!elf.dynamicExecutable) {
            if (preferredCompatError != null) {
                throw combinedStaticLaunchError("静态映射器优先启动和直接启动均失败",
                        directError, preferredCompatError);
            }
            try {
                return startEngineProcessWithStaticLoader(executable, directory);
            } catch (IOException e) {
                throw combinedStaticLaunchError("静态 ARM64 ELF 的直接启动和静态映射兼容启动均失败",
                        directError, e);
            }
        }
        if (preferredCompatError != null) {
            throw combinedLaunchError("linker64 优先启动和直接启动均失败", directError, preferredCompatError);
        }
        try {
            return startEngineProcessWithLinker(executable, directory);
        } catch (IOException e) {
            throw combinedLaunchError("直接启动和 linker64 兼容启动均失败", directError, e);
        }
    }

    /**
     * 使用随 APK 安装到 nativeLibraryDir 的小型静态 ELF 映射器启动外置静态 ARM64 ELF。
     * 映射器先把目标复制到匿名 memfd 并尝试 execveat；若设备以 EACCES 拒绝，
     * 则按 ELF 程序头映射 PT_LOAD、修复初始栈/auxv 后进入原入口。外部引擎仍不打包进 APK。
     */
    private static StartedProcess startEngineProcessWithStaticLoader(File engine, File workDir) throws IOException {
        if (engine == null || !engine.isFile()) throw new IOException("引擎文件不存在");
        File executable;
        try {
            executable = engine.getCanonicalFile();
        } catch (IOException e) {
            executable = engine.getAbsoluteFile();
        }
        ElfInfo elf = inspectElf(executable);
        if (!elf.arm64) throw new IOException("文件不是 64 位 ARMv8 ELF");
        if (elf.dynamicExecutable) throw new IOException("目标包含 PT_INTERP，应使用 linker64 而不是静态 ELF 映射器");
        if (elf.entryPoint == 0L || (elf.type != ET_EXEC && elf.type != ET_DYN)) {
            throw new IOException("目标不是可执行的静态 ARM64 ELF（仅支持 ET_EXEC 或带入口点的静态 PIE）");
        }
        File loader = resolveStaticExecLoader();
        if (loader == null) throw new IOException("APK 静态 ELF 映射器不存在或不可执行");
        File directory = workDir;
        if (directory == null || !directory.isDirectory()) directory = executable.getParentFile();
        ArrayList<String> command = new ArrayList<String>(2);
        command.add(loader.getAbsolutePath());
        command.add(executable.getAbsolutePath());
        try {
            return startCommand(command, directory, false, "APK 静态 ELF 映射器（execveat/PT_LOAD）");
        } catch (SecurityException e) {
            throw new IOException("静态 ELF 映射器被系统安全策略拒绝: " + safeMessage(e), e);
        }
    }

    /** 强制使用 linker64，供探测时处理“直接进程刚启动便退出”的设备差异。 */
    private static StartedProcess startEngineProcessWithLinker(File engine, File workDir) throws IOException {
        if (engine == null || !engine.isFile()) throw new IOException("引擎文件不存在");
        File executable;
        try {
            executable = engine.getCanonicalFile();
        } catch (IOException e) {
            executable = engine.getAbsoluteFile();
        }
        File directory = workDir;
        if (directory == null || !directory.isDirectory()) directory = executable.getParentFile();
        ElfInfo elf = inspectElf(executable);
        if (!elf.arm64) throw new IOException("文件不是 64 位 ARMv8 ELF");
        if (!elf.dynamicExecutable) {
            throw new IOException("静态 ARM64 ELF 无法通过 Android linker64 启动，请使用动态 PIE 版本");
        }
        File linker = resolveSystemLinker64();
        if (linker == null) throw new IOException("系统未找到 linker64");
        ArrayList<String> command = new ArrayList<String>(2);
        command.add(linker.getAbsolutePath());
        command.add(executable.getAbsolutePath());
        try {
            return startCommand(command, directory, true, "系统 linker64");
        } catch (SecurityException e) {
            throw new IOException("linker64 被系统安全策略拒绝: " + safeMessage(e), e);
        }
    }

    /** 按需求先试 UCCI，失败后再以全新进程试 UCI。 */
    public static Result probe(File engine, long timeoutPerProtocolMs) {
        return probe(engine, engine == null ? null : engine.getParentFile(), timeoutPerProtocolMs);
    }

    public static Result probe(File engine, File workDir, long timeoutPerProtocolMs) {
        Result ucci = probeProtocol(engine, workDir, PROTOCOL_UCCI, timeoutPerProtocolMs);
        if (ucci.success) return ucci;
        Result uci = probeProtocol(engine, workDir, PROTOCOL_UCI, timeoutPerProtocolMs);
        if (uci.success) return uci;
        return Result.failure("", "UCCI: " + compact(ucci.message) + "；UCI: " + compact(uci.message));
    }

    /** 包可见，供纯 Java 回归测试直接验证握手状态机。 */
    static Result probeProtocol(File engine, String protocol, long timeoutMs) {
        return probeProtocol(engine, engine == null ? null : engine.getParentFile(), protocol, timeoutMs);
    }

    static Result probeProtocol(File engine, File workDir, String protocol, long timeoutMs) {
        Result first = probeProtocolAttempt(engine, workDir, protocol, timeoutMs, 0);
        if (first.success) return first;
        if (shouldRetryWithLinker(engine, first)) {
            Result linker = probeProtocolAttempt(engine, workDir, protocol, timeoutMs, 1);
            if (linker.success) return linker;
            return Result.failure(protocol, "直接启动=" + compact(first.message)
                    + "；linker64重试=" + compact(linker.message));
        }
        if (shouldRetryWithStaticLoader(engine, first)) {
            Result loader = probeProtocolAttempt(engine, workDir, protocol, timeoutMs, 2);
            if (loader.success) return loader;
            return Result.failure(protocol, "直接启动=" + compact(first.message)
                    + "；静态映射器重试=" + compact(loader.message));
        }
        return first;
    }

    /**
     * 某些系统会让 ProcessBuilder 暂时返回一个进程对象，但装载器随后立即因 noexec/EACCES
     * 退出。探测阶段遇到这种“已启动但握手前退出”的动态 ARM64 ELF 时，再用 linker64
     * 新建进程重试一次；不在同一进程内重复发送协议握手。
     */
    private static boolean shouldRetryWithLinker(File engine, Result first) {
        if (first == null || first.success || !hasProgramInterpreter(engine)) return false;
        String message = first.message == null ? "" : first.message;
        if (!message.contains("启动方式=直接 exec")) return false;
        return isEarlyLaunchFailure(message);
    }

    /**
     * 某些 Linux/Android 组合不会在 ProcessBuilder.start() 当场报告错误，而是返回一个
     * 随即退出的进程。静态 ARM64 ELF 遇到这种情况时，必须再以全新进程强制走 APK
     * 内置静态 ELF 映射器，否则扫描阶段会把本可尝试兼容的文件误判为不可用。
     */
    private static boolean shouldRetryWithStaticLoader(File engine, Result first) {
        if (first == null || first.success || engine == null || !isStaticArm64Executable(engine)) return false;
        String message = first.message == null ? "" : first.message;
        if (!message.contains("启动方式=直接 exec")) return false;
        return isEarlyLaunchFailure(message);
    }

    private static boolean isEarlyLaunchFailure(String message) {
        if (message == null) return false;
        return message.contains("进程退出码=")
                || message.contains(EOF_MARKER)
                || message.contains("Stream closed")
                || message.contains("未收到")
                || message.contains("readyok");
    }

    private static Result probeProtocolAttempt(File engine, File workDir, String protocol,
                                               long timeoutMs, int forcedLaunchMode) {
        if (engine == null || !engine.isFile()) return Result.failure(protocol, "文件不存在");
        Process process = null;
        BufferedWriter writer = null;
        Thread reader = null;
        StartedProcess started = null;
        final LinkedBlockingQueue<String> queue = new LinkedBlockingQueue<String>(256);
        final StringBuilder raw = new StringBuilder();
        String engineName = "";
        try {
            if (forcedLaunchMode == 1) {
                started = startEngineProcessWithLinker(engine, workDir);
            } else if (forcedLaunchMode == 2) {
                started = startEngineProcessWithStaticLoader(engine, workDir);
            } else {
                started = startEngineProcess(engine, workDir);
            }
            process = started.process;
            final Process readerProcess = process;
            reader = new Thread(new Runnable() {
                @Override public void run() {
                    try (BufferedReader br = new BufferedReader(new InputStreamReader(readerProcess.getInputStream(), StandardCharsets.UTF_8))) {
                        String line;
                        while ((line = br.readLine()) != null) queue.offer(cleanLine(line));
                    } catch (Exception e) {
                        queue.offer(IO_MARKER + safeMessage(e));
                    } finally {
                        queue.offer(EOF_MARKER);
                    }
                }
            }, "Engine-probe-reader");
            reader.setDaemon(true);
            reader.start();

            writer = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8));
            String handshake = PROTOCOL_UCCI.equals(protocol) ? "ucci" : "uci";
            String ok = PROTOCOL_UCCI.equals(protocol) ? "ucciok" : "uciok";
            writeLine(writer, handshake);

            long deadline = System.currentTimeMillis() + Math.max(1200L, timeoutMs);
            boolean handshakeOk = false;
            while (System.currentTimeMillis() < deadline) {
                String line = queue.poll(Math.min(180L, Math.max(1L, deadline - System.currentTimeMillis())), TimeUnit.MILLISECONDS);
                if (line == null) continue;
                appendRaw(raw, line);
                String lower = line.toLowerCase(Locale.ROOT);
                if (lower.startsWith("id name ")) engineName = line.substring(Math.min(8, line.length())).trim();
                if (containsToken(lower, ok)) {
                    handshakeOk = true;
                    break;
                }
                if (EOF_MARKER.equals(line)) break;
            }
            if (!handshakeOk) return Result.failure(protocol,
                    diagnostic(raw, process, started, "未收到 " + ok));

            writeLine(writer, "isready");
            long readyWait = PROTOCOL_UCCI.equals(protocol)
                    ? Math.min(1800L, Math.max(900L, timeoutMs))
                    : Math.min(5000L, Math.max(1500L, timeoutMs));
            long readyDeadline = System.currentTimeMillis() + readyWait;
            boolean ready = false;
            while (System.currentTimeMillis() < readyDeadline) {
                String line = queue.poll(Math.min(180L, Math.max(1L, readyDeadline - System.currentTimeMillis())), TimeUnit.MILLISECONDS);
                if (line == null) continue;
                appendRaw(raw, line);
                if (containsToken(line.toLowerCase(Locale.ROOT), "readyok")) {
                    ready = true;
                    break;
                }
                if (EOF_MARKER.equals(line)) break;
            }
            if (!ready && PROTOCOL_UCCI.equals(protocol) && process != null && process.isAlive()) {
                // UCCI 联赛的最小必需子集并不要求 isready。对这类旧引擎做一次 depth 1
                // 实际搜索，只有能返回 bestmove/nobestmove 才按兼容模式加入列表。
                writeLine(writer, "position fen rnbakabnr/9/1c5c1/p1p1p1p1p/9/9/P1P1P1P1P/1C5C1/9/RNBAKABNR w - - 0 1");
                writeLine(writer, "go depth 1");
                long smokeDeadline = System.currentTimeMillis() + 3500L;
                boolean searched = false;
                while (System.currentTimeMillis() < smokeDeadline) {
                    String line = queue.poll(Math.min(180L, Math.max(1L, smokeDeadline - System.currentTimeMillis())), TimeUnit.MILLISECONDS);
                    if (line == null) continue;
                    appendRaw(raw, line);
                    String lower = line.toLowerCase(Locale.ROOT);
                    if (lower.startsWith("bestmove") || lower.startsWith("nobestmove")) {
                        searched = true;
                        break;
                    }
                    if (EOF_MARKER.equals(line)) break;
                }
                if (searched) {
                    return Result.success(protocol, engineName, "启动方式=" + started.launchMode
                            + "；未实现 readyok，已通过 depth 1 搜索兼容验证"
                            + (raw.length() == 0 ? "" : "；" + compact(raw.toString())));
                }
            }
            if (!ready) return Result.failure(protocol,
                    diagnostic(raw, process, started, "握手成功但未收到 readyok"));
            return Result.success(protocol, engineName,
                    "启动方式=" + started.launchMode + (raw.length() == 0 ? "" : "；" + compact(raw.toString())));
        } catch (Exception e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            String prefix = e.getClass().getSimpleName() + ": " + safeMessage(e);
            if (started != null) {
                return Result.failure(protocol, diagnostic(raw, process, started, prefix));
            }
            return Result.failure(protocol, prefix);
        } finally {
            try { if (writer != null) writeLine(writer, "quit"); } catch (Exception ignored) {}
            try { if (writer != null) writer.close(); } catch (Exception ignored) {}
            if (process != null) {
                try { process.destroy(); } catch (Exception ignored) {}
                try {
                    if (!process.waitFor(350L, TimeUnit.MILLISECONDS)) process.destroyForcibly();
                } catch (Exception ignored) {}
            }
            if (reader != null) reader.interrupt();
        }
    }

    private static StartedProcess startCommand(List<String> command, File workDir,
                                               boolean linkerMode, String mode) throws IOException {
        ProcessBuilder pb = new ProcessBuilder(command);
        if (workDir != null && workDir.isDirectory()) pb.directory(workDir);
        pb.redirectErrorStream(true);
        if (workDir != null && workDir.isDirectory()) {
            Map<String, String> env = pb.environment();
            String old = env.get("LD_LIBRARY_PATH");
            String local = workDir.getAbsolutePath();
            // 同目录依赖库既要覆盖 linker64 兼容路径，也要覆盖旧系统可直接 exec 的路径。
            env.put("LD_LIBRARY_PATH", old == null || old.length() == 0 ? local : local + ":" + old);
            env.put("HOME", local);
            env.put("PWD", local);
            env.put("TMPDIR", local);
        }
        return new StartedProcess(pb.start(), mode);
    }

    private static List<String> singleton(String value) {
        ArrayList<String> out = new ArrayList<String>(1);
        out.add(value);
        return out;
    }

    private static File resolveStaticExecLoader() {
        String testOverride = System.getProperty("xiangqi.engine.static_loader", "");
        if (testOverride.length() > 0) {
            File test = new File(testOverride);
            if (test.isFile() && test.canExecute()) return test;
        }
        File configured = configuredStaticExecLoader;
        if (configured != null && configured.isFile() && configured.canExecute()) return configured;
        return null;
    }

    private static File resolveSystemLinker64() {
        // 仅供纯 Java 回归测试覆盖兼容分支；Android 正常运行时不会设置该属性。
        String testOverride = System.getProperty("xiangqi.engine.linker64", "");
        if (testOverride.length() > 0) {
            File test = new File(testOverride);
            if (test.isFile() && test.canExecute()) return test;
        }
        String[] paths = new String[]{
                "/system/bin/linker64",
                "/apex/com.android.runtime/bin/linker64"
        };
        for (String path : paths) {
            File f = new File(path);
            if (f.isFile() && f.canExecute()) return f;
        }
        return null;
    }

    private static ElfInfo inspectElf(File file) {
        ElfInfo info = new ElfInfo();
        if (file == null || !file.isFile() || !file.canRead() || file.length() < ELF_MIN_HEADER_SIZE) return info;
        byte[] h = new byte[ELF64_HEADER_SIZE];
        int off = 0;
        try (FileInputStream in = new FileInputStream(file)) {
            while (off < h.length) {
                int n = in.read(h, off, h.length - off);
                if (n < 0) break;
                off += n;
            }
        } catch (IOException e) {
            return info;
        }
        info.arm64 = isArm64Header(h, off);
        if (!info.arm64 || off < ELF64_HEADER_SIZE) return info;
        info.type = le16(h, 16);
        info.entryPoint = le64(h, 24);

        long phoff = le64(h, 32);
        int phentsize = le16(h, 54);
        int phnum = le16(h, 56);
        if (phoff < ELF64_HEADER_SIZE || phentsize < 8 || phentsize > 256
                || phnum <= 0 || phnum > MAX_PROGRAM_HEADERS) return info;
        long tableEnd = phoff + (long) phentsize * phnum;
        if (tableEnd < phoff || tableEnd > file.length()) return info;
        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            byte[] type = new byte[4];
            for (int i = 0; i < phnum; i++) {
                raf.seek(phoff + (long) i * phentsize);
                raf.readFully(type);
                int pType = (type[0] & 0xff) | ((type[1] & 0xff) << 8)
                        | ((type[2] & 0xff) << 16) | ((type[3] & 0xff) << 24);
                if (pType == PT_INTERP) {
                    info.dynamicExecutable = true;
                    break;
                }
            }
        } catch (IOException ignored) {}
        return info;
    }

    private static boolean isArm64Header(byte[] h, int length) {
        if (h == null || length < ELF_MIN_HEADER_SIZE) return false;
        if ((h[0] & 0xff) != 0x7f || h[1] != 'E' || h[2] != 'L' || h[3] != 'F') return false;
        if ((h[4] & 0xff) != 2 || (h[5] & 0xff) != 1) return false;
        int machine = (h[18] & 0xff) | ((h[19] & 0xff) << 8);
        return machine == EM_AARCH64;
    }

    private static int le16(byte[] b, int off) {
        return (b[off] & 0xff) | ((b[off + 1] & 0xff) << 8);
    }

    private static long le64(byte[] b, int off) {
        long v = 0L;
        for (int i = 7; i >= 0; i--) v = (v << 8) | (b[off + i] & 0xffL);
        return v;
    }

    private static IOException combinedStaticLaunchError(String prefix, IOException direct, IOException compat) {
        StringBuilder sb = new StringBuilder(prefix);
        if (direct != null) sb.append("；直接 exec=").append(safeMessage(direct));
        if (compat != null) sb.append("；静态 ELF 映射器=").append(safeMessage(compat));
        sb.append("。该方式仅提高兼容率；若目标依赖 glibc、使用不兼容的 16K 页布局、需要特殊内核功能，或设备拒绝可执行映射，仍需 Android/bionic 动态 PIE 版本");
        return new IOException(sb.toString(), compat != null ? compat : direct);
    }

    private static IOException combinedLaunchError(String prefix, IOException direct, IOException linker) {
        StringBuilder sb = new StringBuilder(prefix == null ? "引擎启动失败" : prefix);
        if (direct != null) sb.append("；直接启动=").append(safeMessage(direct));
        if (linker != null) sb.append("；linker64=").append(safeMessage(linker));
        return new IOException(sb.toString(), linker != null ? linker : direct);
    }

    private static void writeLine(BufferedWriter writer, String line) throws IOException {
        writer.write(line);
        writer.write('\n');
        writer.flush();
    }

    private static void appendRaw(StringBuilder raw, String line) {
        if (raw.length() >= 8192) return;
        raw.append(line).append('\n');
    }

    private static String diagnostic(StringBuilder raw, Process process, StartedProcess started, String prefix) {
        String state = "";
        try {
            if (process != null && !process.isAlive()) state = "，进程退出码=" + process.exitValue();
        } catch (Exception ignored) {}
        String mode = started == null ? "" : "，启动方式=" + started.launchMode;
        String tail = compact(raw == null ? "" : raw.toString());
        return prefix + mode + state + (tail.length() == 0 ? "" : "，输出=" + tail);
    }

    private static boolean containsToken(String line, String token) {
        if (line == null || token == null) return false;
        String s = line.trim();
        if (s.equals(token)) return true;
        int at = s.indexOf(token);
        if (at < 0) return false;
        boolean left = at == 0 || Character.isWhitespace(s.charAt(at - 1));
        int end = at + token.length();
        boolean right = end >= s.length() || Character.isWhitespace(s.charAt(end));
        return left && right;
    }

    private static String cleanLine(String line) {
        if (line == null) return "";
        String s = line.trim();
        if (s.length() > 0 && s.charAt(0) == '\ufeff') s = s.substring(1).trim();
        return s;
    }

    private static String safeMessage(Throwable t) {
        if (t == null) return "未知错误";
        String m = t.getMessage();
        return m == null || m.trim().length() == 0 ? t.getClass().getSimpleName() : m.trim();
    }

    private static String compact(String text) {
        if (text == null) return "";
        String s = text.replace('\r', ' ').replace('\n', ' ').trim().replaceAll("\\s+", " ");
        return s.length() <= 420 ? s : s.substring(0, 420) + "…";
    }
}
