package com.sdk.esc;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

/**
 * Cách upload gallery Mono theo tài khoản admin:
 * {@code server} (multipart qua API) | {@code signedUrl} (PUT thẳng Firebase).
 * Sync từ {@code GET /users/mobile-upload-status} + socket {@code gallery:upload-method-changed}.
 */
public final class GalleryUploadMethodService {
    private static final String TAG = "GalleryUploadMethod";
    private static final String PREF_NAME = "MPhotoMono_GalleryUpload";
    private static final String KEY_METHOD = "gallery_upload_method";

    public static final String METHOD_SERVER = "server";
    public static final String METHOD_SIGNED_URL = "signedUrl";

    private static GalleryUploadMethodService instance;
    private final SharedPreferences prefs;
    private final Object gate = new Object();
    private String method = METHOD_SERVER;

    private GalleryUploadMethodService(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        String saved = prefs.getString(KEY_METHOD, METHOD_SERVER);
        method = METHOD_SIGNED_URL.equalsIgnoreCase(saved) ? METHOD_SIGNED_URL : METHOD_SERVER;
    }

    public static synchronized GalleryUploadMethodService getInstance(Context context) {
        if (instance == null) {
            instance = new GalleryUploadMethodService(context);
        }
        return instance;
    }

    public String getMethod() {
        synchronized (gate) {
            return method;
        }
    }

    public boolean useSignedUrl() {
        synchronized (gate) {
            return METHOD_SIGNED_URL.equalsIgnoreCase(method);
        }
    }

    public void apply(String raw) {
        String next = METHOD_SIGNED_URL.equalsIgnoreCase(raw) ? METHOD_SIGNED_URL : METHOD_SERVER;
        synchronized (gate) {
            if (next.equals(method)) {
                return;
            }
            method = next;
        }
        prefs.edit().putString(KEY_METHOD, next).apply();
        Log.d(TAG, "galleryUploadMethod = " + next);
    }

    /** Gọi API status — chạy trên background thread. */
    public void syncFromServer(String token) {
        if (token == null || token.isEmpty()) {
            return;
        }
        try {
            org.json.JSONObject status = ApiService.getMobileUploadStatus(token);
            if (status != null) {
                apply(status.optString("galleryUploadMethod", METHOD_SERVER));
            }
        } catch (Exception e) {
            Log.w(TAG, "syncFromServer failed: " + e.getMessage());
        }
    }
}
