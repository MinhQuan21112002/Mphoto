package com.sdk.esc;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import org.json.JSONObject;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Đồng bộ thư mục Google Drive Mono với <b>tài khoản trên server</b> (một user — một link folder dùng chung mọi máy).
 * <p><b>Luồng khi mở app (vào app Mono):</b>
 * <ol>
 *   <li><b>GET</b> {@code /user/mono-drive-folder} — hỏi server user hiện tại <i>đã có</i> link/folder chưa.</li>
 *   <li>Nếu <b>đã có</b> với {@code folderId} hợp lệ và còn tồn tại trên Drive → ghi vào SharedPreferences, dùng cho màn Mono; xong (không PUT).</li>
 *   <li>Nếu <b>chưa có</b> (404) hoặc id trên server mất trên Drive → tạo/lấy folder trên tài khoản Drive (service), <b>PUT</b> lên server, rồi lưu local cho app Mono.</li>
 * </ol>
 * <b>API:</b> {@code GET/PUT} {@code /user/mono-drive-folder} với Bearer (xem {@link ApiService#BASE_URL}).
 */
public final class MonoDriveServerSync {
    private static final String TAG = "MonoDriveServerSync";
    private static final String API_PATH = "/user/mono-drive-folder";
    private static final ExecutorService EXEC = Executors.newSingleThreadExecutor();
    /** Gộp MPhotoApp + onResume/Login cách vài trăm ms tránh chạy sync 2 lần liên tiếp (log trùng). */
    private static final long MIN_ENQUEUE_INTERVAL_MS = 1_500L;
    private static final Object ENQUEUE_LOCK = new Object();
    private static long lastEnqueueWallClockMs;

    private MonoDriveServerSync() {}

    public static void requestSyncIfLoggedIn(Context anyContext) {
        Context app = anyContext.getApplicationContext();
        if (!TokenManager.getInstance(app).isLoggedIn()) {
            return;
        }
        String token = TokenManager.getInstance(app).getToken();
        if (token == null || token.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        synchronized (ENQUEUE_LOCK) {
            if (lastEnqueueWallClockMs > 0L && (now - lastEnqueueWallClockMs) < MIN_ENQUEUE_INTERVAL_MS) {
                Log.d(TAG, "Bỏ qua gọi sync trùng (cách " + (now - lastEnqueueWallClockMs) + "ms)");
                return;
            }
            lastEnqueueWallClockMs = now;
        }
        EXEC.execute(() -> {
            try {
                runSync(app, token);
            } catch (Exception e) {
                Log.w(TAG, "sync", e);
            }
        });
    }

    private static void runSync(Context app, String token) {
        String userId = TokenManager.getInstance(app).getUserId();
        if (userId == null || userId.isEmpty()) {
            userId = "unknown";
        }
        String spKey = "MonoDriveUserFolderMphoto_" + userId;
        SharedPreferences sp = app.getSharedPreferences("GoogleDrive", Context.MODE_PRIVATE);

        GoogleDriveService drive = new GoogleDriveService(app);
        if (!drive.isDriveReady()) {
            Log.w(TAG, "Drive API không khởi tạo, bỏ qua sync server");
            return;
        }

        JSONObject fromServer = null;
        try {
            fromServer = ApiService.getJsonObjectAuthedOrNullOn404(API_PATH, token);
        } catch (Exception e) {
            String msg = e.getMessage() != null ? e.getMessage() : "";
            if (isUnauthorizedError(msg)) {
                Log.w(TAG, "GET mono-drive-folder: hết phiên, bỏ qua");
                return;
            }
            // Mạng chưa sẵn sàng lúc mở app, timeout, 5xx… — vẫn tạo/cập nhật folder trên Drive và
            // thử PUT sau (giống trường hợp 404: server chưa lưu).
            Log.w(TAG, "GET mono-drive-folder lỗi, vẫn tạo/lưu local + PUT: " + msg);
        }

        if (fromServer != null) {
            String serverId = fromServer.optString("folderId", null);
            if (serverId != null && !serverId.isEmpty() && !"null".equalsIgnoreCase(serverId)) {
                if (drive.isDriveFolderStillThere(serverId)) {
                    sp.edit().putString(spKey, serverId).apply();
                    MPhotoUserDataBackup.scheduleSave(app);
                    Log.d(TAG, "Đã áp dụng folderId từ server: " + serverId);
                    return;
                }
                Log.w(TAG, "Server folderId không còn trên Drive, tạo lại…");
            }
        }

        String localOrNew = drive.getOrCreateMonoUserFolderId(app);
        if (localOrNew == null || localOrNew.isEmpty()) {
            Log.w(TAG, "Không tạo/lấy được thư mục Mono trên Drive");
            return;
        }
        sp.edit().putString(spKey, localOrNew).apply();
        MPhotoUserDataBackup.scheduleSave(app);
        String web = GoogleDriveService.folderIdToWebLink(localOrNew);
        try {
            JSONObject body = new JSONObject();
            body.put("folderId", localOrNew);
            if (web != null) {
                body.put("webLink", web);
            }
            ApiService.putJsonObjectAuthed(API_PATH, token, body);
            Log.d(TAG, "Đã lưu folderId lên server");
        } catch (Exception e) {
            Log.w(TAG, "PUT mono-drive-folder: " + e.getMessage());
        }
    }

    private static boolean isUnauthorizedError(String message) {
        if (message == null || message.isEmpty()) {
            return false;
        }
        if (message.contains("401") || message.contains("HTTP 401")) {
            return true;
        }
        return message.contains("hết hạn") && message.contains("đăng nhập");
    }
}
