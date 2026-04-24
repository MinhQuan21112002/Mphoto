package com.sdk.esc;

import android.app.Activity;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
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
import java.util.Date;
import java.util.Locale;
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
    private static final String SUBDIR = "MPhotoUpdate";

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
        String fileUrl = raw.optString("fileUrl", null);
        long fileSizeFromApi = raw.optLong("fileSize", 0L);
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
        boolean haveGoodLocal =
                fileForThisUpdate.isFile()
                        && isLikelyApkFile(fileForThisUpdate);
        boolean shouldDownload;
        if (force) {
            shouldDownload = true;
        } else if (!hasLocalApk) {
            shouldDownload = true;
        } else if (lastKnown > 0L && lastKnown == serverDate) {
            shouldDownload = !haveGoodLocal;
        } else if (lastKnown == 0L) {
            if (fileName != null && localApk.getName().equalsIgnoreCase(fileName) && isLikelyApkFile(localApk)) {
                sp.edit().putLong(prefKeyForChannel(KEY_LAST_SERVER_DATE), serverDate).apply();
                shouldDownload = false;
            } else {
                shouldDownload = true;
            }
        } else {
            shouldDownload = lastKnown < serverDate;
        }

        if (!shouldDownload) {
            Log.i(TAG, "checkAndDownload: không cần tải (đã cập nhật hoặc cùng bản).");
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
        ApiService.downloadToFile(fileUrl, dest);
        Log.i(TAG, "kết thúc tải, bytes=" + dest.length());
        if (fileSizeFromApi > 0L && dest.length() != fileSizeFromApi) {
            Log.w(TAG, "dung lượng tải về lệch fileSize trên API (chỉ log, dùng kiểm tra PK): " + dest.length() + " vs " + fileSizeFromApi);
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
        String m = app.getString(R.string.app_update_downloaded, fileName);
        if (callback != null) callback.onFinished(true, m);
        return m;
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
