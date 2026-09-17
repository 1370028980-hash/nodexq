package com.tyl.xiangqi.ndxq;

import android.content.Context;
import android.media.MediaScannerConnection;
import android.os.Environment;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/** nodexq 公共目录及外部皮肤文件定位，不处理 Android 权限申请。 */
final class NodeStorageManager {
    private static final int MEDIA_SCAN_BATCH_SIZE = 64;

    static final class EnsureResult {
        final boolean ready;
        final String error;

        EnsureResult(boolean ready, String error) {
            this.ready = ready;
            this.error = error;
        }
    }

    private final String rootName;
    private final String recentName;
    private final String correctionName;
    private final String pictureName;
    private final Context appContext;
    private boolean pictureMediaRefreshRequested;

    NodeStorageManager(Context context, String rootName, String recentName,
                       String correctionName, String pictureName) {
        this.appContext = context == null ? null : context.getApplicationContext();
        this.rootName = rootName;
        this.recentName = recentName;
        this.correctionName = correctionName;
        this.pictureName = pictureName;
    }

    File rootDirectory() {
        return new File(Environment.getExternalStorageDirectory(), rootName);
    }

    File recentDirectory() {
        return new File(rootDirectory(), recentName);
    }

    File correctionDirectory() {
        return new File(rootDirectory(), correctionName);
    }

    File pictureDirectory() {
        return new File(rootDirectory(), pictureName);
    }

    File skinDirectory(String safeSkinName) {
        return new File(pictureDirectory(), safeSkinName);
    }

    File findImage(File directory, String baseName) {
        if (directory == null || baseName == null) return null;
        String[] extensions = new String[]{".png", ".webp", ".jpg", ".jpeg"};
        for (String extension : extensions) {
            File file = new File(directory, baseName + extension);
            if (file.isFile()) return file;
        }
        return null;
    }

    EnsureResult ensureDirectories() {
        try {
            File root = rootDirectory();
            boolean rootReady = root.isDirectory() || root.mkdirs();
            boolean recentReady = recentDirectory().isDirectory() || recentDirectory().mkdirs();
            boolean correctionReady = correctionDirectory().isDirectory() || correctionDirectory().mkdirs();
            boolean pictureReady = pictureDirectory().isDirectory() || pictureDirectory().mkdirs();
            if (!rootReady || !recentReady || !correctionReady || !pictureReady) {
                return new EnsureResult(false, root.getAbsolutePath());
            }
            ensurePictureDirectoryMediaIgnored();
            return new EnsureResult(true, "");
        } catch (Exception e) {
            return new EnsureResult(false, e.getMessage());
        }
    }

    /**
     * 皮肤图不应被系统媒体扫描器当作用户相册内容。
     *
     * 旧版皮肤可能已经被 MediaStore 记录：除 pic 根目录外，也给每个既有皮肤
     * 目录补 marker，并在本次进程首个存储就绪时主动重扫 marker 和图片路径。
     * 这不会迁移、改名或删除用户文件。
     */
    private void ensurePictureDirectoryMediaIgnored() {
        ArrayList<String> scanPaths = new ArrayList<String>();
        try {
            File directory = pictureDirectory();
            if (!directory.isDirectory()) return;
            collectPictureMediaIgnorePaths(directory, scanPaths);
        } catch (Exception ignored) {
            // 外部存储的媒体索引抑制是附加能力，不能因此阻止皮肤目录正常使用。
        }
        requestPictureMediaRefresh(scanPaths);
    }

    private void collectPictureMediaIgnorePaths(File directory, List<String> scanPaths) {
        if (directory == null || !directory.isDirectory()) return;
        try {
            File marker = new File(directory, ".nomedia");
            if (!marker.exists()) marker.createNewFile();
            if (marker.isFile()) scanPaths.add(marker.getAbsolutePath());
        } catch (Exception ignored) {}

        File[] children;
        try {
            children = directory.listFiles();
        } catch (SecurityException ignored) {
            return;
        }
        if (children == null) return;
        for (File child : children) {
            if (child == null) continue;
            if (child.isDirectory()) {
                collectPictureMediaIgnorePaths(child, scanPaths);
            } else if (isSkinImage(child)) {
                scanPaths.add(child.getAbsolutePath());
            }
        }
    }

    private boolean isSkinImage(File file) {
        if (file == null || !file.isFile()) return false;
        String name = file.getName().toLowerCase(java.util.Locale.ROOT);
        return name.endsWith(".png") || name.endsWith(".webp")
                || name.endsWith(".jpg") || name.endsWith(".jpeg");
    }

    private void requestPictureMediaRefresh(List<String> paths) {
        if (pictureMediaRefreshRequested || appContext == null || paths == null || paths.isEmpty()) {
            return;
        }
        pictureMediaRefreshRequested = true;
        for (int start = 0; start < paths.size(); start += MEDIA_SCAN_BATCH_SIZE) {
            int end = Math.min(paths.size(), start + MEDIA_SCAN_BATCH_SIZE);
            String[] batch = paths.subList(start, end).toArray(new String[end - start]);
            try {
                MediaScannerConnection.scanFile(appContext, batch, null, null);
            } catch (Exception ignored) {
                // 个别设备的媒体扫描服务不可用时，.nomedia 仍会在后续系统扫描时生效。
            }
        }
    }
}
