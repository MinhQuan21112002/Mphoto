package com.sdk.esc;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONObject;

/**
 * Thời gian đăng nhập + đăng xuất từ xa theo kênh (Mono = {@code mono}).
 * Đối chiếu thời gian đã dùng token với giới hạn admin; hỗ trợ vô hạn (null days).
 */
public final class SessionPolicyService {
    private static final String TAG = "SessionPolicy";
    private static final String PREF_NAME = "MPhotoMono_SessionPolicy";
    private static final String CHANNEL = "mono";
    /** -1 = vô hạn */
    private static final int UNLIMITED = -1;

    private static SessionPolicyService instance;
    private final Context appContext;
    private final SharedPreferences prefs;

    public interface LogoutCallback {
        void onForceLogout(String reason);
    }

    private LogoutCallback logoutCallback;

    private SessionPolicyService(Context context) {
        appContext = context.getApplicationContext();
        prefs = appContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public static synchronized SessionPolicyService getInstance(Context context) {
        if (instance == null) {
            instance = new SessionPolicyService(context);
        }
        return instance;
    }

    public void setLogoutCallback(LogoutCallback callback) {
        this.logoutCallback = callback;
    }

    public void onLoginFromUserJson(JSONObject user) {
        if (user == null) {
            return;
        }
        Integer days = parseDaysFromUserJson(user, CHANNEL);
        int stored = days == null ? UNLIMITED : days;
        prefs.edit()
                .putInt(keyCurrentDays(), stored)
                .putInt(keyInitialDays(), stored)
                .apply();
        Log.d(TAG, "onLogin mono days=" + (days == null ? "unlimited" : days));
    }

    public void syncFromStatusJson(JSONObject status) {
        if (status == null) {
            return;
        }
        JSONObject daysMap = status.optJSONObject("loginDurationDays");
        if (daysMap != null) {
            applyLoginDuration(CHANNEL, parseDaysFromMap(daysMap, CHANNEL));
        }
        JSONObject forceMap = status.optJSONObject("forceLogoutAt");
        if (forceMap != null) {
            long forceMs = parseIsoToMs(forceMap.optString(CHANNEL, null));
            if (forceMs > 0) {
                applyForceLogout(CHANNEL, forceMs);
            }
        }
    }

    public void applyLoginDuration(String channel, Integer days) {
        if (!CHANNEL.equalsIgnoreCase(channel)) {
            return;
        }
        int stored = days == null ? UNLIMITED : Math.max(1, days);
        prefs.edit().putInt(keyCurrentDays(), stored).apply();
        Log.d(TAG, "applyLoginDuration mono=" + (days == null ? "unlimited" : days));
        maybeLogoutAfterPolicyChange();
    }

    public void applyForceLogout(String channel, long forceLogoutAtMs) {
        if (!CHANNEL.equalsIgnoreCase(channel) || forceLogoutAtMs <= 0) {
            return;
        }
        prefs.edit().putLong(keyForceLogoutMs(), forceLogoutAtMs).apply();
        Log.d(TAG, "applyForceLogout mono at=" + forceLogoutAtMs);
        maybeLogoutForForce(forceLogoutAtMs);
    }

    /** @return true nếu phiên hết hạn theo policy (không tính JWT). */
    public boolean isSessionPolicyExpired(long sessionStartedMs, String jwtToken) {
        if (sessionStartedMs <= 0) {
            return false;
        }
        long jwtExpMs = getJwtExpMs(jwtToken);
        long now = System.currentTimeMillis();

        int initialDays = prefs.getInt(keyInitialDays(), UNLIMITED);
        int currentDays = prefs.getInt(keyCurrentDays(), UNLIMITED);

        long initialEnd = endMs(sessionStartedMs, initialDays);
        long policyEnd = endMs(sessionStartedMs, currentDays);
        long effectiveEnd = Math.min(jwtExpMs > 0 ? jwtExpMs : Long.MAX_VALUE,
                Math.min(initialEnd, policyEnd));

        return now >= effectiveEnd;
    }

    public boolean shouldForceLogout(long sessionStartedMs) {
        long forceMs = prefs.getLong(keyForceLogoutMs(), 0L);
        long ackMs = prefs.getLong(keyAckForceLogoutMs(), 0L);
        if (forceMs <= 0 || forceMs <= ackMs) {
            return false;
        }
        return sessionStartedMs <= 0 || forceMs > sessionStartedMs;
    }

    public void acknowledgeForceLogout() {
        long forceMs = prefs.getLong(keyForceLogoutMs(), 0L);
        if (forceMs > 0) {
            prefs.edit().putLong(keyAckForceLogoutMs(), forceMs).apply();
        }
    }

    private void maybeLogoutAfterPolicyChange() {
        TokenManager tm = TokenManager.getInstance(appContext);
        long started = tm.getLoginEpochMs();
        String token = tm.getToken();
        if (token != null && !token.isEmpty() && isSessionPolicyExpired(started, token)) {
            triggerLogout("session_policy");
        }
    }

    private void maybeLogoutForForce(long forceLogoutAtMs) {
        TokenManager tm = TokenManager.getInstance(appContext);
        if (shouldForceLogout(tm.getLoginEpochMs())) {
            triggerLogout("force_logout");
        }
    }

    private void triggerLogout(String reason) {
        new Handler(Looper.getMainLooper()).post(() -> {
            try {
                TokenManager tm = TokenManager.getInstance(appContext);
                if (tm.getToken() == null || tm.getToken().isEmpty()) {
                    return;
                }
                Log.w(TAG, "Force logout mono reason=" + reason);
                tm.clearToken();
                acknowledgeForceLogout();
                try {
                    SocketService.getInstance().disconnect();
                } catch (Exception ignored) {
                }
                if (logoutCallback != null) {
                    logoutCallback.onForceLogout(reason);
                }
                Intent intent = new Intent(appContext, LoginActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                appContext.startActivity(intent);
            } catch (Exception e) {
                Log.e(TAG, "triggerLogout", e);
            }
        });
    }

    private static long endMs(long sessionStartedMs, int days) {
        if (days == UNLIMITED || days < 1) {
            return Long.MAX_VALUE;
        }
        return sessionStartedMs + days * 24L * 60 * 60 * 1000L;
    }

    private static Integer parseDaysFromUserJson(JSONObject user, String ch) {
        JSONObject map = user.optJSONObject("loginDurationDays");
        return parseDaysFromMap(map, ch);
    }

    private static Integer parseDaysFromMap(JSONObject map, String ch) {
        if (map == null || map.isNull(ch)) {
            return null;
        }
        if (map.opt(ch) instanceof String && "null".equals(map.optString(ch))) {
            return null;
        }
        int n = map.optInt(ch, UNLIMITED);
        return n < 1 ? null : n;
    }

    private static long parseIsoToMs(String iso) {
        if (iso == null || iso.isEmpty() || "null".equals(iso)) {
            return 0L;
        }
        // minSdk 21: không dùng java.time
        String[] patterns = {
                "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
                "yyyy-MM-dd'T'HH:mm:ss'Z'",
                "yyyy-MM-dd'T'HH:mm:ss.SSSX",
                "yyyy-MM-dd'T'HH:mm:ssX"
        };
        for (String pattern : patterns) {
            try {
                java.text.SimpleDateFormat sdf =
                        new java.text.SimpleDateFormat(pattern, java.util.Locale.US);
                sdf.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
                java.util.Date d = sdf.parse(iso);
                if (d != null) {
                    return d.getTime();
                }
            } catch (Exception ignored) {
            }
        }
        return 0L;
    }

    /** Dùng từ SocketService khi parse payload. */
    public static long parseIsoToMsPublic(String iso) {
        return parseIsoToMs(iso);
    }

    static long getJwtExpMs(String token) {
        if (token == null || token.isEmpty()) {
            return 0L;
        }
        try {
            String[] parts = token.split("\\.");
            if (parts.length < 2) {
                return 0L;
            }
            byte[] bytes = android.util.Base64.decode(parts[1], android.util.Base64.URL_SAFE);
            JSONObject json = new JSONObject(new String(bytes, java.nio.charset.StandardCharsets.UTF_8));
            long exp = json.optLong("exp", 0L);
            return exp > 0 ? exp * 1000L : 0L;
        } catch (Exception e) {
            return 0L;
        }
    }

    private String keyCurrentDays() {
        return "current_days_" + CHANNEL;
    }

    private String keyInitialDays() {
        return "initial_days_" + CHANNEL;
    }

    private String keyForceLogoutMs() {
        return "force_logout_ms_" + CHANNEL;
    }

    private String keyAckForceLogoutMs() {
        return "ack_force_logout_ms_" + CHANNEL;
    }
}
