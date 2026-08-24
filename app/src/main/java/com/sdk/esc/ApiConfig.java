package com.sdk.esc;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.mphoto.mono.BuildConfig;

/**
 * Cấu hình máy chủ API — giống mlite {@code ApiConfig}.
 * Debug: đổi Local / Railway (lưu prefs). Release: luôn Railway.
 */
public final class ApiConfig {
    private static final String TAG = "ApiConfig";
    public static final String PRODUCTION_BASE_URL = "https://mphoto.up.railway.app";
    /** Emulator → host PC. Máy thật: đổi IP LAN trong dialog login (debug). */
    public static final String DEFAULT_LOCAL_BASE_URL = "http://10.0.2.2:5000";
    public static final int LOCAL_PORT = 5000;
    public static final String ENV_PRODUCTION = "production";
    public static final String ENV_LOCAL = "local";

    private static final String PREF = "MPhotoMono_ApiConfig";
    private static final String KEY_ENV = "environment";
    private static final String KEY_LOCAL_BASE = "local_base_url";

    private static SharedPreferences prefs;
    private static String environment = ENV_PRODUCTION;
    private static String localBaseUrl = DEFAULT_LOCAL_BASE_URL;

    private ApiConfig() {}

    public static synchronized void init(Context context) {
        if (context == null) return;
        prefs = context.getApplicationContext().getSharedPreferences(PREF, Context.MODE_PRIVATE);
        localBaseUrl = normalizeLocalBaseUrl(prefs.getString(KEY_LOCAL_BASE, DEFAULT_LOCAL_BASE_URL));
        if (!allowEnvironmentSwitch()) {
            environment = ENV_PRODUCTION;
            return;
        }
        String saved = prefs.getString(KEY_ENV, ENV_PRODUCTION);
        environment = ENV_LOCAL.equalsIgnoreCase(saved) ? ENV_LOCAL : ENV_PRODUCTION;
        Log.d(TAG, "init env=" + environment + " base=" + getBaseUrl());
    }

    /** Chỉ bản debug mới được đổi máy chủ. */
    public static boolean allowEnvironmentSwitch() {
        return BuildConfig.DEBUG;
    }

    public static boolean isLocal() {
        return allowEnvironmentSwitch() && ENV_LOCAL.equalsIgnoreCase(environment);
    }

    public static String getEnvironment() {
        return isLocal() ? ENV_LOCAL : ENV_PRODUCTION;
    }

    public static String getEnvironmentDisplayName() {
        return isLocal() ? "LOCAL" : "RAILWAY";
    }

    /** Origin không có /api — dùng cho Socket.IO / trang web. */
    public static String getBaseUrl() {
        if (isLocal()) {
            String u = localBaseUrl != null ? localBaseUrl.trim() : DEFAULT_LOCAL_BASE_URL;
            while (u.endsWith("/")) u = u.substring(0, u.length() - 1);
            return u.isEmpty() ? DEFAULT_LOCAL_BASE_URL : u;
        }
        return PRODUCTION_BASE_URL;
    }

    /** {@code .../api} — dùng cho REST. */
    public static String getApiUrl() {
        return getBaseUrl() + "/api";
    }

    public static String getLocalBaseUrl() {
        return localBaseUrl != null ? localBaseUrl : DEFAULT_LOCAL_BASE_URL;
    }

    /** Chỉ IP để hiện trong ô nhập (vd: 192.168.0.5). */
    public static String getLocalHostIp() {
        return extractHostIp(getLocalBaseUrl());
    }

    public static void applyEnvironment(String env, boolean persist) {
        if (!allowEnvironmentSwitch()) {
            environment = ENV_PRODUCTION;
            return;
        }
        environment = ENV_LOCAL.equalsIgnoreCase(env) ? ENV_LOCAL : ENV_PRODUCTION;
        if (persist && prefs != null) {
            prefs.edit().putString(KEY_ENV, environment).apply();
        }
        Log.d(TAG, "applyEnvironment → " + environment + " base=" + getBaseUrl());
    }

    /**
     * User chỉ cần nhập IP (vd: 192.168.0.5). Tự gắn http:// và :5000.
     */
    public static void setLocalBaseUrl(String input, boolean persist) {
        localBaseUrl = normalizeLocalBaseUrl(input);
        if (persist && prefs != null) {
            prefs.edit().putString(KEY_LOCAL_BASE, localBaseUrl).apply();
        }
    }

    public static String normalizeLocalBaseUrl(String input) {
        if (input == null || input.trim().isEmpty()) {
            return DEFAULT_LOCAL_BASE_URL;
        }
        String u = input.trim();
        while (u.endsWith("/")) {
            u = u.substring(0, u.length() - 1);
        }
        if (u.regionMatches(true, 0, "http://", 0, 7)) {
            u = u.substring(7);
        } else if (u.regionMatches(true, 0, "https://", 0, 8)) {
            u = u.substring(8);
        }
        int slash = u.indexOf('/');
        if (slash >= 0) {
            u = u.substring(0, slash);
        }
        int colon = u.lastIndexOf(':');
        if (colon > 0) {
            String maybePort = u.substring(colon + 1);
            if (maybePort.matches("\\d+")) {
                u = u.substring(0, colon);
            }
        }
        u = u.trim();
        if (u.isEmpty()) {
            return DEFAULT_LOCAL_BASE_URL;
        }
        return "http://" + u + ":" + LOCAL_PORT;
    }

    public static String extractHostIp(String baseUrl) {
        if (baseUrl == null || baseUrl.isEmpty()) {
            return "10.0.2.2";
        }
        String u = baseUrl.trim();
        if (u.regionMatches(true, 0, "http://", 0, 7)) {
            u = u.substring(7);
        } else if (u.regionMatches(true, 0, "https://", 0, 8)) {
            u = u.substring(8);
        }
        int slash = u.indexOf('/');
        if (slash >= 0) u = u.substring(0, slash);
        int colon = u.lastIndexOf(':');
        if (colon > 0) {
            String maybePort = u.substring(colon + 1);
            if (maybePort.matches("\\d+")) {
                u = u.substring(0, colon);
            }
        }
        return u.isEmpty() ? "10.0.2.2" : u;
    }
}
