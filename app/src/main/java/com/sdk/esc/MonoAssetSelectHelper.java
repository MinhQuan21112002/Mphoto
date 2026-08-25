package com.sdk.esc;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.util.List;

/**
 * Chọn khung / ảnh phụ theo Mongo asset id — apply local ngay nếu đã có,
 * chỉ sync khi local chưa có id đó.
 */
public final class MonoAssetSelectHelper {
    private static final String TAG = "MonoAssetSelect";

    private MonoAssetSelectHelper() {
    }

    public interface AfterSelect {
        void onApplied(int index);
        void onError(String message);
    }

    public static void selectFrameById(Activity activity, String frameId, AfterSelect cb) {
        if (activity == null || frameId == null || frameId.trim().isEmpty()) {
            if (cb != null) cb.onError("frameId trống");
            return;
        }
        final String id = frameId.trim();
        Runnable applyLocal = () -> {
            int idx = findIndexContainingId(activity, "FrameImage", "bitmap_list", id);
            if (idx < 0) {
                if (cb != null) cb.onError("Không tìm thấy khung sau đồng bộ");
                return;
            }
            SharedPreferences p = activity.getSharedPreferences("FrameImage", Context.MODE_PRIVATE);
            p.edit().putInt("current_index", idx).apply();
            if (cb != null) cb.onApplied(idx);
        };

        // Fast path: đã có local → apply ngay, không chờ sync
        int localIdx = findIndexContainingId(activity, "FrameImage", "bitmap_list", id);
        if (localIdx >= 0) {
            activity.runOnUiThread(applyLocal);
            return;
        }

        String token = TokenManager.getInstance(activity).getToken();
        if (token == null || token.isEmpty()) {
            activity.runOnUiThread(applyLocal);
            return;
        }
        MonoCacheSync.syncFramesInBackground(activity, token, new MonoCacheSync.Listener() {
            @Override
            public void onSuccess() {
                activity.runOnUiThread(applyLocal);
            }

            @Override
            public void onError(String message) {
                Log.w(TAG, "sync frames: " + message);
                activity.runOnUiThread(applyLocal);
            }
        });
    }

    public static void selectSubById(Activity activity, String subId, AfterSelect cb) {
        if (activity == null || subId == null || subId.trim().isEmpty()) {
            if (cb != null) cb.onError("subId trống");
            return;
        }
        final String id = subId.trim();
        Runnable applyLocal = () -> {
            int idx = findIndexContainingId(activity, "MyAppPrefs2", "ImageViewList", id);
            if (idx < 0) {
                if (cb != null) cb.onError("Không tìm thấy ảnh phụ sau đồng bộ");
                return;
            }
            SharedPreferences p = activity.getSharedPreferences("MyAppPrefs2", Context.MODE_PRIVATE);
            p.edit().putInt("indexImageView2", idx).apply();
            if (cb != null) cb.onApplied(idx);
        };

        // Fast path: đã có local → apply ngay
        int localIdx = findIndexContainingId(activity, "MyAppPrefs2", "ImageViewList", id);
        if (localIdx >= 0) {
            activity.runOnUiThread(applyLocal);
            return;
        }

        String token = TokenManager.getInstance(activity).getToken();
        if (token == null || token.isEmpty()) {
            activity.runOnUiThread(applyLocal);
            return;
        }
        MonoCacheSync.syncSubPhotosInBackground(activity, token, new MonoCacheSync.Listener() {
            @Override
            public void onSuccess() {
                activity.runOnUiThread(applyLocal);
            }

            @Override
            public void onError(String message) {
                Log.w(TAG, "sync subs: " + message);
                activity.runOnUiThread(applyLocal);
            }
        });
    }

    private static int findIndexContainingId(Context ctx, String pref, String listKey, String id) {
        SharedPreferences p = ctx.getSharedPreferences(pref, Context.MODE_PRIVATE);
        String json = p.getString(listKey, "[]");
        List<String> list;
        try {
            list = new Gson().fromJson(json, new TypeToken<List<String>>() {}.getType());
        } catch (Exception e) {
            return -1;
        }
        if (list == null || list.isEmpty()) return -1;
        String raw = id.trim();
        String needle = raw.replaceAll("[^a-zA-Z0-9._-]", "_");
        String needleNoExt = needle.contains(".")
                ? needle.substring(0, needle.lastIndexOf('.'))
                : needle;
        for (int i = 0; i < list.size(); i++) {
            String entry = list.get(i);
            if (entry == null) continue;
            if (entry.contains(raw) || entry.contains(needle) || entry.contains(needleNoExt)) {
                return i;
            }
            // Token dạng F:.../id.jpg — so khớp tên file
            int slash = entry.lastIndexOf('/');
            String filePart = slash >= 0 ? entry.substring(slash + 1) : entry;
            if (filePart.startsWith(needleNoExt + ".") || filePart.equals(needle) || filePart.equals(needleNoExt)) {
                return i;
            }
        }
        return -1;
    }
}
