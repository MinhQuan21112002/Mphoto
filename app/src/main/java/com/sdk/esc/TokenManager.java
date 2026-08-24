package com.sdk.esc;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;
import android.util.Log;
import org.json.JSONObject;
import java.nio.charset.StandardCharsets;

/**
 * Lưu JWT / thông tin user sau đăng nhập (cùng logic Mphoto-Android, Base64 dùng API Android cho minSdk 21).
 */
public class TokenManager {
    private static final String TAG = "TokenManager";
    /** Tên file prefs cố định; dữ liệu vẫn tách theo applicationId (Lite vs Mono). */
    private static final String PREF_NAME = "MPhotoMphoto_Camera2_Auth";
    private static final String KEY_TOKEN = "auth_token";
    private static final String KEY_USER_ID = "user_id";
    private static final String KEY_EMAIL = "user_email";
    private static final String KEY_USERNAME = "user_name";
    /** Thời điểm đăng nhập gần nhất — phiên theo policy admin. */
    private static final String KEY_LOGIN_EPOCH_MS = "login_epoch_ms";
    /** @deprecated dùng SessionPolicyService */
    private static final long SESSION_MAX_MS = 6L * 24 * 60 * 60 * 1000L;
    /** Dùng app không đăng nhập — không có JWT, không gọi API cloud. */
    private static final String KEY_GUEST_MODE = "guest_mode";
    private static TokenManager instance;
    private final Context appContext;
    private SharedPreferences prefs;

    private TokenManager(Context context) {
        appContext = context.getApplicationContext();
        prefs = appContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public static synchronized TokenManager getInstance(Context context) {
        if (instance == null) {
            instance = new TokenManager(context);
        }
        return instance;
    }

    public void saveToken(String token) {
        long now = System.currentTimeMillis();
        prefs.edit()
                .putString(KEY_TOKEN, token)
                .putLong(KEY_LOGIN_EPOCH_MS, now)
                .remove(KEY_GUEST_MODE)
                .apply();

        try {
            String userId = getUserIdFromToken(token);
            String email = getEmailFromToken(token);
            String username = getUsernameFromToken(token);

            if (userId != null) {
                prefs.edit().putString(KEY_USER_ID, userId).apply();
            }
            if (email != null) {
                prefs.edit().putString(KEY_EMAIL, email).apply();
            }
            if (username != null) {
                prefs.edit().putString(KEY_USERNAME, username).apply();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error parsing token", e);
        }
    }

    public void saveUsername(String username) {
        if (username != null && !username.isEmpty()) {
            prefs.edit().putString(KEY_USERNAME, username).apply();
        }
    }

    /**
     * Lưu rõ từ API login (user._id) nếu JWT không có hoặc cần đồng bộ.
     */
    public void setUserIdFromApi(String userId) {
        if (userId != null && !userId.isEmpty()) {
            prefs.edit().putString(KEY_USER_ID, userId).apply();
        }
    }

    public String getToken() {
        return prefs.getString(KEY_TOKEN, null);
    }

    public String getUserId() {
        return prefs.getString(KEY_USER_ID, null);
    }

    public String getEmail() {
        return prefs.getString(KEY_EMAIL, null);
    }

    public String getUsername() {
        return prefs.getString(KEY_USERNAME, null);
    }

    public boolean isGuestMode() {
        return prefs.getBoolean(KEY_GUEST_MODE, false);
    }

    /** Vào camera khi đã đăng nhập hoặc chọn chế độ khách. */
    public boolean canEnterApp() {
        return isLoggedIn() || isGuestMode();
    }

    /** Upload, sync frame/subphoto, QR gallery server, cập nhật app từ server. */
    public boolean canUseCloudFeatures() {
        return isLoggedIn();
    }

    public void enterGuestMode() {
        prefs.edit()
                .remove(KEY_TOKEN)
                .remove(KEY_USER_ID)
                .remove(KEY_EMAIL)
                .remove(KEY_USERNAME)
                .remove(KEY_LOGIN_EPOCH_MS)
                .putBoolean(KEY_GUEST_MODE, true)
                .apply();
    }

    public boolean isLoggedIn() {
        if (isGuestMode()) {
            return false;
        }
        String token = getToken();
        if (token == null || token.isEmpty()) {
            return false;
        }
        if (isTokenExpired(token)) {
            return false;
        }
        if (!prefs.contains(KEY_LOGIN_EPOCH_MS)) {
            long iatMs = getJwtIatMs(token);
            long start = (iatMs > 0) ? iatMs : System.currentTimeMillis();
            prefs.edit().putLong(KEY_LOGIN_EPOCH_MS, start).apply();
        }
        long started = prefs.getLong(KEY_LOGIN_EPOCH_MS, 0L);
        SessionPolicyService policy = SessionPolicyService.getInstance(appContext);
        if (policy.shouldForceLogout(started)) {
            policy.acknowledgeForceLogout();
            clearToken();
            return false;
        }
        if (started > 0L && policy.isSessionPolicyExpired(started, token)) {
            clearToken();
            return false;
        }
        return true;
    }

    public long getLoginEpochMs() {
        return prefs.getLong(KEY_LOGIN_EPOCH_MS, 0L);
    }

    /** Giây phát hành JWT (iat), đổi ra ms. */
    private static long getJwtIatMs(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length > 1) {
                String payload = jwtPayloadUtf8(parts[1]);
                if (payload == null) {
                    return 0L;
                }
                JSONObject json = new JSONObject(payload);
                long iat = json.optLong("iat", 0L);
                if (iat > 0) {
                    return iat * 1000L;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "getJwtIat", e);
        }
        return 0L;
    }

    public void clearToken() {
        prefs.edit()
            .remove(KEY_TOKEN)
            .remove(KEY_USER_ID)
            .remove(KEY_EMAIL)
            .remove(KEY_USERNAME)
            .remove(KEY_LOGIN_EPOCH_MS)
            .remove(KEY_GUEST_MODE)
            .apply();
    }

    private static String jwtPayloadUtf8(String part) {
        if (part == null || part.isEmpty()) {
            return null;
        }
        try {
            byte[] bytes = Base64.decode(part, Base64.URL_SAFE);
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            try {
                byte[] bytes = Base64.decode(part, Base64.DEFAULT);
                return new String(bytes, StandardCharsets.UTF_8);
            } catch (IllegalArgumentException e2) {
                return null;
            }
        }
    }

    private String getUserIdFromToken(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length > 1) {
                String payload = jwtPayloadUtf8(parts[1]);
                if (payload == null) {
                    return null;
                }
                JSONObject json = new JSONObject(payload);
                return json.optString("userId", null);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error parsing userId from token", e);
        }
        return null;
    }

    private String getEmailFromToken(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length > 1) {
                String payload = jwtPayloadUtf8(parts[1]);
                if (payload == null) {
                    return null;
                }
                JSONObject json = new JSONObject(payload);
                return json.optString("email", null);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error parsing email from token", e);
        }
        return null;
    }

    private String getUsernameFromToken(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length > 1) {
                String payload = jwtPayloadUtf8(parts[1]);
                if (payload == null) {
                    return null;
                }
                JSONObject json = new JSONObject(payload);
                String name = json.optString("name", null);
                if (name != null && !name.isEmpty()) {
                    return name;
                }
                return json.optString("username", null);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error parsing username from token", e);
        }
        return null;
    }

    private boolean isTokenExpired(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length > 1) {
                String payload = jwtPayloadUtf8(parts[1]);
                if (payload == null) {
                    return true;
                }
                JSONObject json = new JSONObject(payload);
                long exp = json.optLong("exp", 0);
                if (exp > 0) {
                    long currentTime = System.currentTimeMillis() / 1000;
                    return currentTime > exp;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error checking token expiration", e);
        }
        return true;
    }
}
