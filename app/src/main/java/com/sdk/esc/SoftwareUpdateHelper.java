package com.sdk.esc;

import com.mphoto.mono.R;

import android.app.Activity;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.FileProvider;

import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.TimeZone;

/**
 * Cập nhật APK qua M-photo API. Server có 3 kênh: {@code pro} = Windows (.exe), {@code lite} và {@code mono} = Android (.apk).
 * Đừng dùng {@code GET /software-update} (mặc định) — đó là kênh Pro. App này gọi {@code /software-update/mono}.
 */
public final class SoftwareUpdateHelper {
    private static final String TAG = "SoftwareUpdate";
    private static final String PREF = "MPhoto_AppUpdate";
    private static final String KEY_LAST_SERVER_DATE = "last_software_update_date_millis";
    private static final String KEY_PENDING_FILE = "last_downloaded_update_file";
    private static final String KEY_HAS_NEW_UPDATE = "has_new_update_available";
    private static final String KEY_PENDING_VERSION = "pending_update_version_code";
    private static final String KEY_APPLIED_FILES = "applied_update_file_names";
    private static final String KEY_LATEST_SERVER_FILE = "latest_server_update_file";
    private static final String SUBDIR = "MPhotoUpdate";
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static volatile DownloadProgressListener progressListener;
    private static volatile Runnable badgeListener;
    private static volatile int downloadPercent = -1;

    /**
     * Kênh cập nhật: {@code mono} = M-Photo Mono (APK), {@code lite} = M-Photo Lite (APK). Không dùng {@code pro} (file .exe).
     */
    private static final String SOFTWARE_UPDATE_CHANNEL = "mono";

    private static String prefKeyForChannel(String base) {
        return base + "_" + SOFTWARE_UPDATE_CHANNEL;
    }

    /** File APK là ZIP, bắt đầu bằng {@code PK}. Nội dung HTML/JSON/thông báo lỗi từ CDN không thỏa. */
    private static boolean isLikelyApkFile(@Nullable File f) {
        if (f == null || !f.isFile() || f.length() < 2L) {
            return false;
        }
        try (FileInputStream in = new FileInputStream(f)) {
            int a = in.read();
            int b = in.read();
            return a == 0x50 && b == 0x4B;
        } catch (IOException e) {
            return false;
        }
    }

    public interface UpdateCallback {
        void onFinished(boolean success, @Nullable String message);
    }

    public interface DownloadProgressListener {
        /** @param percent 0–99 khi đang tải, -1 khi không còn tải. */
        void onProgress(int percent);
    }

    public static void setDownloadProgressListener(@Nullable DownloadProgressListener listener) {
        progressListener = listener;
    }

    public static void setBadgeListener(@Nullable Runnable listener) {
        badgeListener = listener;
    }

    public static int getDownloadProgressPercent() {
        return downloadPercent;
    }

    public static boolean isDownloading() {
        return downloadPercent >= 0;
    }

    public static boolean hasUnseenUpdate(@NonNull Context context) {
        acknowledgeInstalledBuild(context);
        String serverName = latestServerFileName(context);
        if (serverName.isEmpty()) {
            return false;
        }
        UpdateHistory history = loadHistory(context);
        if (history.appliedFileName.isEmpty()) {
            File pending = new File(getUpdateDir(context), serverName);
            return pending.isFile() && isLikelyApkFile(pending);
        }
        return !serverName.equalsIgnoreCase(history.appliedFileName);
    }

    /** Bản đang chạy được cài sau file đã tải, hoặc version mới hơn: ghi tên bản vào JSON để tắt dấu 1. */
    public static void acknowledgeInstalledBuild(@NonNull Context context) {
        File pending = getPendingApkFile(context);
        String serverName = latestServerFileName(context);
        String pendingName = pending != null && pending.isFile() ? pending.getName() : "";
        String nameToApply = !pendingName.isEmpty() ? pendingName : serverName;
        if (nameToApply.isEmpty()) {
            return;
        }
        UpdateHistory history = loadHistory(context);
        int installed = installedVersionCode(context);
        long pkgUpdated = packageLastUpdateTime(context);
        long fileTime = pending != null && pending.isFile() ? pending.lastModified() : 0L;
        boolean installedAfterDownload = pkgUpdated > 0L && fileTime > 0L && pkgUpdated + 5000L >= fileTime;
        boolean newerVersion = installed > 0 && history.appliedVersionCode > 0 && installed > history.appliedVersionCode;
        if (!installedAfterDownload && !newerVersion) {
            return;
        }
        if (nameToApply.equalsIgnoreCase(history.appliedFileName) && installed == history.appliedVersionCode) {
            return;
        }
        history.appliedFileName = nameToApply;
        if (installed > 0) {
            history.appliedVersionCode = installed;
        }
        rememberHistoryName(history, nameToApply);
        saveHistory(context, history);
        notifyBadge();
    }

    private static long packageLastUpdateTime(@NonNull Context context) {
        try {
            PackageInfo pi = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            return pi.lastUpdateTime;
        } catch (Exception e) {
            return 0L;
        }
    }

    private SoftwareUpdateHelper() {
    }

    public static File getUpdateDir(Context context) {
        File base = context.getApplicationContext().getFilesDir();
        File d = new File(base, SUBDIR);
        if (!d.exists() && !d.mkdirs()) {
            Log.w(TAG, "getUpdateDir: mkdir failed for " + d.getAbsolutePath());
        }
        return d;
    }

    @Nullable
    public static File findLatestApk(File updateDir) {
        if (updateDir == null || !updateDir.isDirectory()) {
            return null;
        }
        File[] list = updateDir.listFiles((dir, name) -> name != null && name.toLowerCase(Locale.US).endsWith(".apk"));
        if (list == null || list.length == 0) {
            return null;
        }
        File best = null;
        long bestTime = 0L;
        for (File f : list) {
            if (f.isFile() && f.lastModified() > bestTime) {
                bestTime = f.lastModified();
                best = f;
            }
        }
        return best;
    }

    public static void checkAndDownloadInBackground(Context context) {
        new Thread(() -> {
            try {
                Context app = context.getApplicationContext();
                String token = TokenManager.getInstance(app).getToken();
                if (token == null || token.isEmpty()) {
                    return;
                }
                checkAndDownloadInternal(app, token, false, null);
            } catch (Exception e) {
                Log.d(TAG, "background update check: " + e.getMessage());
            }
        }, "mphoto-app-update").start();
    }

    /**
     * @param onUiBefore chạy trên main trước khi tải (ví dụ: hiện ProgressBar trên nút)
     * @param onUiAfter  chạy trên main sau khi xong (ẩn vòng xoay, bật lại nút) — luôn gọi cả lỗi
     */
    public static void checkAndDownloadWithFeedback(@NonNull Activity activity, boolean force,
            @Nullable Runnable onUiBefore, @Nullable Runnable onUiAfter) {
        String token = TokenManager.getInstance(activity).getToken();
        if (token == null || token.isEmpty()) {
            android.widget.Toast.makeText(activity, R.string.app_update_need_login, android.widget.Toast.LENGTH_SHORT).show();
            return;
        }
        if (onUiBefore != null) {
            activity.runOnUiThread(onUiBefore);
        }
        new Thread(() -> {
            try {
                String msg = checkAndDownloadInternal(activity.getApplicationContext(), token, force, null);
                activity.runOnUiThread(() -> {
                    try {
                        android.widget.Toast.makeText(activity, msg, android.widget.Toast.LENGTH_LONG).show();
                    } finally {
                        if (onUiAfter != null) {
                            onUiAfter.run();
                        }
                    }
                });
            } catch (Exception e) {
                String m = e.getMessage() != null ? e.getMessage() : "Error";
                activity.runOnUiThread(() -> {
                    try {
                        android.widget.Toast.makeText(activity, m, android.widget.Toast.LENGTH_LONG).show();
                    } finally {
                        if (onUiAfter != null) {
                            onUiAfter.run();
                        }
                    }
                });
            }
        }, "mphoto-app-update-ui").start();
    }

    /**
     * @return thông điệp cho Toast
     */
    @NonNull
    private static String checkAndDownloadInternal(Context app, String token, boolean force, @Nullable UpdateCallback callback) throws Exception {
        Log.i(TAG, "checkAndDownload: bắt đầu, force=" + force + ", channel=" + SOFTWARE_UPDATE_CHANNEL);
        JSONObject raw = ApiService.getJsonObjectAuthed(
                "/software-update/" + SOFTWARE_UPDATE_CHANNEL, token);
        boolean hasFile = raw.optBoolean("hasFile", false);
        String nameFromServer = raw.optString("fileName", "");
        if (nameFromServer.toLowerCase(Locale.US).endsWith(".exe")) {
            String m = app.getString(R.string.app_update_is_windows_exe);
            if (callback != null) {
                callback.onFinished(false, m);
            }
            return m;
        }
        String fileName = normalizeApkName(nameFromServer);
        cacheLatestServerFile(app, fileName);
        String fileUrl = raw.optString("fileUrl", null);
        long fileSizeFromApi = readJsonLong(raw, "fileSize");
        long serverDate = parseDateUploadMillis(raw);

        if (!hasFile || fileUrl == null || fileUrl.isEmpty() || serverDate <= 0) {
            String m = app.getString(R.string.app_update_no_file_server);
            if (callback != null) callback.onFinished(false, m);
            return m;
        }

        SharedPreferences sp = app.getSharedPreferences(PREF, Context.MODE_PRIVATE);
        long lastKnown = sp.getLong(prefKeyForChannel(KEY_LAST_SERVER_DATE), 0L);
        File updateDir = getUpdateDir(app);
        File localApk = findLatestApk(updateDir);
        boolean hasLocalApk = localApk != null && localApk.isFile();

        File fileForThisUpdate = new File(updateDir, fileName);
        boolean haveGoodLocal = localApkMatchesServer(fileForThisUpdate, fileSizeFromApi);
        boolean shouldDownload;
        if (force) {
            shouldDownload = true;
        } else if (!hasLocalApk) {
            shouldDownload = true;
        } else if (lastKnown > 0L && lastKnown == serverDate) {
            shouldDownload = !haveGoodLocal;
        } else if (lastKnown == 0L) {
            if (fileName != null && localApk.getName().equalsIgnoreCase(fileName)
                    && localApkMatchesServer(localApk, fileSizeFromApi)) {
                sp.edit().putLong(prefKeyForChannel(KEY_LAST_SERVER_DATE), serverDate).apply();
                shouldDownload = false;
            } else {
                shouldDownload = true;
            }
        } else {
            shouldDownload = lastKnown < serverDate;
        }
        if (!shouldDownload && fileSizeFromApi > 0L && fileForThisUpdate.isFile()
                && fileForThisUpdate.length() != fileSizeFromApi) {
            Log.i(TAG, "dung lượng local " + fileForThisUpdate.length() + " lệch server " + fileSizeFromApi + ", tải lại");
            shouldDownload = true;
        }

        if (!shouldDownload) {
            Log.i(TAG, "checkAndDownload: không cần tải (đã cập nhật hoặc cùng bản).");
            if (haveGoodLocal) {
                syncPendingUpdateBadge(app, fileForThisUpdate);
            }
            String m = app.getString(R.string.app_update_up_to_date);
            if (callback != null) callback.onFinished(true, m);
            return m;
        }

        deleteOtherApks(updateDir, null);
        File dest = new File(updateDir, fileName);
        if (dest.exists() && !dest.delete()) {
            Log.w(TAG, "could not delete partial file");
        }
        Log.i(TAG, "bắt đầu tải: " + fileName + (fileSizeFromApi > 0 ? (", fileSize API=" + fileSizeFromApi) : ""));
        Log.i(TAG, "link file (fileUrl từ API): " + fileUrl);
        String urlTaiVe = CdnHelper.rewriteToCdn(fileUrl);
        if (!urlTaiVe.equals(fileUrl)) {
            Log.i(TAG, "link tải thật (sau rewrite CDN): " + urlTaiVe);
        } else {
            Log.i(TAG, "link tải thật (giống API, không đổi qua CDN): " + urlTaiVe);
        }
        final int[] lastPct = new int[]{-1};
        reportProgress(0);
        try {
            ApiService.downloadToFile(fileUrl, dest, (downloaded, totalStream) -> {
                long total = totalStream > 0L ? totalStream : fileSizeFromApi;
                int pct;
                if (total > 0L) {
                    pct = (int) Math.min(99L, downloaded * 100L / total);
                    if (downloaded > 0L && pct < 1) pct = 1;
                } else if (downloaded > 0L) {
                    pct = (int) Math.min(95L, 1L + downloaded / (512L * 1024L));
                } else {
                    pct = 0;
                }
                if (pct > lastPct[0]) {
                    lastPct[0] = pct;
                    reportProgress(pct);
                }
            });
        } finally {
            reportProgress(-1);
        }
        Log.i(TAG, "kết thúc tải, bytes=" + dest.length());
        if (fileSizeFromApi > 0L && (!dest.isFile() || dest.length() != fileSizeFromApi)) {
            Log.w(TAG, "dung lượng lệch server, bỏ file: " + (dest.isFile() ? dest.length() : 0) + " vs " + fileSizeFromApi);
            try { /*noinspection ResultOfMethodCallIgnored*/ dest.delete(); } catch (Exception ignored) { }
            String m = app.getString(R.string.app_update_download_failed);
            if (callback != null) callback.onFinished(false, m);
            return m;
        }
        if (!dest.isFile() || dest.length() < 4L) {
            String m = app.getString(R.string.app_update_download_failed);
            if (callback != null) callback.onFinished(false, m);
            return m;
        }
        if (!isLikelyApkFile(dest)) {
            try { /*noinspection ResultOfMethodCallIgnored*/ dest.delete(); } catch (Exception ignored) { }
            String m = app.getString(R.string.app_update_invalid_file);
            if (callback != null) callback.onFinished(false, m);
            return m;
        }
        sp.edit()
                .putLong(prefKeyForChannel(KEY_LAST_SERVER_DATE), serverDate)
                .putString(prefKeyForChannel(KEY_PENDING_FILE), fileName)
                .apply();
        syncPendingUpdateBadge(app, dest);
        String m = app.getString(R.string.app_update_downloaded, fileName);
        if (callback != null) callback.onFinished(true, m);
        return m;
    }

    /** File đã tải đủ khi dung lượng trùng server. Không có fileSize thì chỉ cần đúng header APK. */
    private static boolean localApkMatchesServer(@Nullable File file, long serverSize) {
        if (file == null || !file.isFile() || !isLikelyApkFile(file)) return false;
        if (serverSize <= 0L) return true;
        return file.length() == serverSize;
    }

    private static long readJsonLong(JSONObject raw, String key) {
        if (raw == null || !raw.has(key) || raw.isNull(key)) return 0L;
        try {
            Object o = raw.get(key);
            if (o instanceof Number) return ((Number) o).longValue();
            String s = String.valueOf(o).trim();
            if (s.isEmpty()) return 0L;
            return Long.parseLong(s);
        } catch (Exception e) {
            return 0L;
        }
    }

    private static void reportProgress(int percent) {
        downloadPercent = percent;
        DownloadProgressListener listener = progressListener;
        if (listener != null) {
            MAIN.post(() -> listener.onProgress(percent));
        }
    }

    private static void notifyBadge() {
        Runnable listener = badgeListener;
        if (listener != null) {
            MAIN.post(listener);
        }
    }

    private static void syncPendingUpdateBadge(@NonNull Context app, @Nullable File apk) {
        if (apk == null || !isLikelyApkFile(apk)) {
            return;
        }
        SharedPreferences sp = app.getSharedPreferences(PREF, Context.MODE_PRIVATE);
        int apkVc = readApkVersionCode(app, apk);
        sp.edit()
                .putInt(prefKeyForChannel(KEY_PENDING_VERSION), apkVc)
                .putString(prefKeyForChannel(KEY_PENDING_FILE), apk.getName())
                .apply();
        cacheLatestServerFile(app, apk.getName());
        notifyBadge();
    }

    private static String latestServerFileName(@NonNull Context context) {
        String name = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
                .getString(prefKeyForChannel(KEY_LATEST_SERVER_FILE), "");
        return name == null ? "" : name.trim();
    }

    private static void cacheLatestServerFile(@NonNull Context context, @Nullable String fileName) {
        if (fileName == null || fileName.trim().isEmpty()) return;
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
                .edit()
                .putString(prefKeyForChannel(KEY_LATEST_SERVER_FILE), fileName.trim())
                .apply();
        notifyBadge();
    }

    private static final class UpdateHistory {
        String appliedFileName = "";
        int appliedVersionCode = 0;
        final ArrayList<String> names = new ArrayList<>();
    }

    private static File historyFile(@NonNull Context context) {
        return new File(getUpdateDir(context), "update_history.json");
    }

    private static UpdateHistory loadHistory(@NonNull Context context) {
        UpdateHistory history = new UpdateHistory();
        File file = historyFile(context);
        if (!file.isFile()) return history;
        try {
            byte[] buf = new byte[(int) Math.min(file.length(), 256 * 1024)];
            int n;
            try (FileInputStream in = new FileInputStream(file)) {
                n = in.read(buf);
            }
            if (n <= 0) return history;
            JSONObject o = new JSONObject(new String(buf, 0, n, java.nio.charset.StandardCharsets.UTF_8));
            history.appliedFileName = o.optString("appliedFileName", "");
            history.appliedVersionCode = o.optInt("appliedVersionCode", 0);
            org.json.JSONArray arr = o.optJSONArray("DownloadedUpdateFileNames");
            if (arr != null) {
                for (int i = 0; i < arr.length(); i++) {
                    String name = arr.optString(i, "").trim();
                    if (!name.isEmpty()) history.names.add(name);
                }
            }
            if (history.appliedFileName.isEmpty() && !history.names.isEmpty()) {
                history.appliedFileName = history.names.get(history.names.size() - 1);
            }
        } catch (Exception e) {
            Log.w(TAG, "load update_history: " + e.getMessage());
        }
        return history;
    }

    private static void rememberHistoryName(@NonNull UpdateHistory history, @NonNull String fileName) {
        for (String name : history.names) {
            if (name.equalsIgnoreCase(fileName)) return;
        }
        history.names.add(fileName);
    }

    private static void saveHistory(@NonNull Context context, @NonNull UpdateHistory history) {
        try {
            JSONObject o = new JSONObject();
            o.put("appliedFileName", history.appliedFileName);
            o.put("appliedVersionCode", history.appliedVersionCode);
            org.json.JSONArray arr = new org.json.JSONArray();
            for (String name : history.names) arr.put(name);
            o.put("DownloadedUpdateFileNames", arr);
            File file = historyFile(context);
            try (java.io.FileOutputStream out = new java.io.FileOutputStream(file)) {
                out.write(o.toString(2).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }
        } catch (Exception e) {
            Log.w(TAG, "save update_history: " + e.getMessage());
        }
    }

    private static void markUpdateApplied(@NonNull SharedPreferences sp, @Nullable String fileName) {
        Set<String> applied = new HashSet<>(sp.getStringSet(
                prefKeyForChannel(KEY_APPLIED_FILES), Collections.emptySet()));
        if (fileName != null && !fileName.isEmpty()) {
            applied.add(fileName);
        }
        sp.edit()
                .putStringSet(prefKeyForChannel(KEY_APPLIED_FILES), applied)
                .putBoolean(prefKeyForChannel(KEY_HAS_NEW_UPDATE), false)
                .remove(prefKeyForChannel(KEY_PENDING_VERSION))
                .apply();
        notifyBadge();
    }

    private static int installedVersionCode(@NonNull Context context) {
        try {
            PackageInfo pi = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            if (Build.VERSION.SDK_INT >= 28) {
                return (int) Math.min(Integer.MAX_VALUE, pi.getLongVersionCode());
            }
            return pi.versionCode;
        } catch (Exception e) {
            return 0;
        }
    }

    private static int readApkVersionCode(@NonNull Context context, @NonNull File apk) {
        try {
            PackageManager pm = context.getPackageManager();
            PackageInfo pi;
            if (Build.VERSION.SDK_INT >= 33) {
                pi = pm.getPackageArchiveInfo(apk.getAbsolutePath(), PackageManager.PackageInfoFlags.of(0));
            } else {
                pi = pm.getPackageArchiveInfo(apk.getAbsolutePath(), 0);
            }
            if (pi == null) {
                return 0;
            }
            if (Build.VERSION.SDK_INT >= 28) {
                return (int) Math.min(Integer.MAX_VALUE, pi.getLongVersionCode());
            }
            return pi.versionCode;
        } catch (Exception e) {
            return 0;
        }
    }

    private static void deleteOtherApks(@Nullable File updateDir, @Nullable String keepName) {
        if (updateDir == null || !updateDir.isDirectory()) {
            return;
        }
        File[] list = updateDir.listFiles();
        if (list == null) {
            return;
        }
        for (File f : list) {
            if (!f.getName().toLowerCase(Locale.US).endsWith(".apk")) {
                continue;
            }
            if (keepName != null && f.getName().equals(keepName)) {
                continue;
            }
            try {
                f.delete();
            } catch (Exception ignored) {
            }
        }
    }

    private static long parseDateUploadMillis(JSONObject raw) {
        if (raw.isNull("dateUpload")) {
            return 0L;
        }
        if (raw.has("dateUpload")) {
            try {
                Object o = raw.get("dateUpload");
                if (o instanceof Number) {
                    long v = ((Number) o).longValue();
                    if (v < 1_000_000_000_000L) {
                        return v * 1000L;
                    }
                    return v;
                }
            } catch (Exception ignored) {
            }
        }
        String s = raw.optString("dateUpload", "");
        if (s.isEmpty()) {
            return 0L;
        }
        String[] patterns = {
                "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
                "yyyy-MM-dd'T'HH:mm:ss'Z'",
                "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
                "yyyy-MM-dd'T'HH:mm:ssXXX",
                "yyyy-MM-dd HH:mm:ss"
        };
        for (String p : patterns) {
            try {
                SimpleDateFormat f = new SimpleDateFormat(p, Locale.US);
                f.setTimeZone(TimeZone.getTimeZone("UTC"));
                ParsePosition pos = new ParsePosition(0);
                Date d;
                if (p.contains("XXX")) {
                    f.setTimeZone(TimeZone.getDefault());
                }
                d = f.parse(s, pos);
                if (d != null && pos.getIndex() > 0) {
                    return d.getTime();
                }
            } catch (Exception ignored) {
            }
        }
        return 0L;
    }

    @NonNull
    private static String normalizeApkName(String fileName) {
        String n = (fileName == null || fileName.trim().isEmpty()) ? "MPhoto_update.apk" : fileName.trim();
        String lower = n.toLowerCase(Locale.US);
        if (lower.endsWith(".exe")) {
            return n;
        }
        if (!lower.endsWith(".apk")) {
            n += ".apk";
        }
        return n;
    }

    public static boolean hasPendingApk(@NonNull Context context) {
        File f = getPendingApkFile(context);
        return f != null && f.isFile() && f.length() > 0;
    }

    @Nullable
    public static File getPendingApkFile(@NonNull Context context) {
        SharedPreferences sp = context.getSharedPreferences(PREF, Context.MODE_PRIVATE);
        String name = sp.getString(prefKeyForChannel(KEY_PENDING_FILE), null);
        if (name == null || name.isEmpty()) {
            return findLatestApk(getUpdateDir(context));
        }
        File f = new File(getUpdateDir(context), name);
        if (f.isFile()) {
            return f;
        }
        return findLatestApk(getUpdateDir(context));
    }

    /**
     * Tên file APK cập nhật đang có trên máy (để hiển thị trong dialog cài đặt).
     */
    @Nullable
    public static String getPendingApkFileNameForDisplay(@NonNull Context context) {
        File f = getPendingApkFile(context);
        if (f == null || !f.isFile() || f.length() < 1L) {
            return null;
        }
        return f.getName();
    }

    public static void tryInstallPending(@NonNull Activity activity) {
        if (isDownloading()) {
            return;
        }
        File apk = getPendingApkFile(activity);
        if (apk == null || !apk.isFile()) {
            android.widget.Toast.makeText(activity, R.string.app_update_no_local_apk, android.widget.Toast.LENGTH_SHORT).show();
            return;
        }
        if (!isLikelyApkFile(apk)) {
            android.widget.Toast.makeText(activity, R.string.app_update_install_broken, android.widget.Toast.LENGTH_LONG).show();
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!activity.getPackageManager().canRequestPackageInstalls()) {
                Intent s = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:" + activity.getPackageName()));
                if (s.resolveActivity(activity.getPackageManager()) != null) {
                    activity.startActivity(s);
                }
                android.widget.Toast.makeText(activity, R.string.app_update_allow_unknown_sources, android.widget.Toast.LENGTH_LONG).show();
                return;
            }
        }
        try {
            Uri uri = FileProvider.getUriForFile(
                    activity,
                    activity.getPackageName() + ".fileprovider",
                    apk);
            Intent i = new Intent(Intent.ACTION_VIEW);
            i.setDataAndType(uri, "application/vnd.android.package-archive");
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            i.setClipData(ClipData.newUri(activity.getContentResolver(), "mphoto", uri));
            activity.startActivity(i);
        } catch (Exception e) {
            Log.e(TAG, "install", e);
            android.widget.Toast.makeText(activity, e.getMessage() != null ? e.getMessage() : "Install error",
                    android.widget.Toast.LENGTH_LONG).show();
        }
    }
}
