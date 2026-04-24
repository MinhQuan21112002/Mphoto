package com.sdk.esc;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Base64;
import android.util.Log;
import com.google.gson.Gson;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Đồng bộ ảnh M-Photo Mono từ {@code GET /api/assets/mono/frames|sub-photos}:
 * tải thiếu, xóa file local không còn trên server, cập nhật SharedPreferences.
 * <p>Khi ổ ngoài sẵn sàng, file được tải <strong>trực tiếp</strong> vào
 * {@code Pictures/M-Photo_Pro/app_user_assets/frames|subs} — cùng kiểu thư mục public với ảnh in,
 * <strong>không bị xóa khi gỡ app</strong> (chỉ mất dữ liệu trong bộ nhớ app nội bộ nếu phải fallback).</p>
 */
public final class MonoCacheSync {
    private static final String TAG = "MonoCacheSync";
    /** Fallback khi không ghi được thư mục public; bị xóa cùng app. */
    private static final String DIR_FRAMES = "mono_cache/frames";
    private static final String DIR_SUBS = "mono_cache/sub_photos";

    /**
     * @param publicRel ví dụ {@link UserAssetFileStore#REL_FRAMES}; null nếu {@link #usePublicTokens} false.
     */
    private static final class DownloadTarget {
        final File dir;
        final String publicRel;
        final boolean usePublicTokens;
        /** true: dùng {@link MPhotoProImageStore} (Android 10+), không mở File trực tiếp dưới Pictures. */
        final boolean useMediaStore;

        private DownloadTarget(File dir, String publicRel, boolean usePublicTokens, boolean useMediaStore) {
            this.dir = dir;
            this.publicRel = publicRel;
            this.usePublicTokens = usePublicTokens;
            this.useMediaStore = useMediaStore;
        }
    }

    public interface Listener {
        void onSuccess();
        void onError(String message);
    }

    /**
     * Bản cũ tải vào {@code getFilesDir()/mono_cache/...} (mất khi gỡ app). Nếu thư mục public
     * tương ứng đang trống, copy bản còn ở nội bộ lên public để sau này trùng với tải trực tiếp.
     */
    public static void migrateLegacyInternalCacheToAppUserAssetsIfNeeded(Context appCtx) {
        Context c = appCtx.getApplicationContext();
        if (!MPhotoPublicStorage.isExternalAvailable()) {
            return;
        }
        tryMigrateDir(c, UserAssetFileStore.REL_FRAMES, DIR_FRAMES);
        tryMigrateDir(c, UserAssetFileStore.REL_SUBS, DIR_SUBS);
    }

    private static void tryMigrateDir(Context c, String publicRel, String internalRel) {
        File inside = new File(c.getFilesDir(), internalRel);
        if (!inside.isDirectory() || inside.list() == null || inside.list().length == 0) {
            return;
        }
        if (MPhotoProImageStore.useMediaStoreForPublicWrites()) {
            for (String name : inside.list()) {
                File f = new File(inside, name);
                if (f == null || !f.isFile() || f.length() == 0) {
                    continue;
                }
                if (MPhotoProImageStore.exists(c, publicRel, f.getName())) {
                    continue;
                }
                try {
                    MPhotoProImageStore.replaceWithFile(c, publicRel, f.getName(), f, "image/jpeg");
                } catch (Exception e) {
                    Log.w(TAG, "migrate to MediaStore " + f, e);
                }
            }
            Log.d(TAG, "Migrated to MediaStore from " + inside);
            return;
        }
        File pub = new File(MPhotoPublicStorage.getAppPublicRoot(), publicRel);
        int nPub = (pub.isDirectory() && pub.list() != null) ? pub.list().length : 0;
        if (nPub > 0) {
            return;
        }
        if (!pub.isDirectory() && !pub.mkdirs()) {
            Log.w(TAG, "migrate mkdir public " + pub);
            return;
        }
        for (File f : inside.listFiles()) {
            if (f == null || !f.isFile() || f.length() == 0) {
                continue;
            }
            File to = new File(pub, f.getName());
            if (to.isFile() && to.length() > 0) {
                continue;
            }
            if (!copyFile(f, to)) {
                Log.w(TAG, "migrate copy fail " + f);
            }
        }
        Log.d(TAG, "Migrated " + inside + " -> " + pub);
    }

    public static void syncFramesInBackground(Context context, String token, Listener listener) {
        execute(context, () -> {
            try {
                syncFrames(context, token);
                runOnMain(context, () -> { if (listener != null) listener.onSuccess(); });
            } catch (Exception e) {
                Log.e(TAG, "syncFrames", e);
                String msg = e.getMessage() != null ? e.getMessage() : "Lỗi đồng bộ khung";
                runOnMain(context, () -> { if (listener != null) listener.onError(msg); });
            }
        });
    }

    public static void syncSubPhotosInBackground(Context context, String token, Listener listener) {
        execute(context, () -> {
            try {
                syncSubPhotos(context, token);
                runOnMain(context, () -> { if (listener != null) listener.onSuccess(); });
            } catch (Exception e) {
                Log.e(TAG, "syncSubPhotos", e);
                String msg = e.getMessage() != null ? e.getMessage() : "Lỗi đồng bộ ảnh phụ";
                runOnMain(context, () -> { if (listener != null) listener.onError(msg); });
            }
        });
    }

    private static void execute(Context appCtx, Runnable work) {
        Context ctx = appCtx.getApplicationContext();
        ExecutorService ex = Executors.newSingleThreadExecutor();
        ex.execute(() -> {
            try {
                work.run();
            } finally {
                ex.shutdown();
            }
        });
    }

    private static void runOnMain(Context context, Runnable r) {
        if (context instanceof android.app.Activity) {
            ((android.app.Activity) context).runOnUiThread(r);
        } else {
            new android.os.Handler(android.os.Looper.getMainLooper()).post(r);
        }
    }

    private static DownloadTarget frameDownloadTarget(Context context) {
        if (MPhotoProImageStore.useMediaStoreForPublicWrites()) {
            File st = new File(context.getCacheDir(), "mono_sync/frames");
            if (!st.isDirectory() && !st.mkdirs()) {
                Log.w(TAG, "cache staging frames: " + st);
            }
            return new DownloadTarget(st, UserAssetFileStore.REL_FRAMES, true, true);
        }
        if (MPhotoPublicStorage.isExternalAvailable() && Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            File d = new File(MPhotoPublicStorage.getAppPublicRoot(), UserAssetFileStore.REL_FRAMES);
            if (d.isDirectory() || d.mkdirs()) {
                return new DownloadTarget(d, UserAssetFileStore.REL_FRAMES, true, false);
            }
            Log.w(TAG, "cannot mkdir public frames, fallback internal: " + d);
        }
        File in = new File(context.getFilesDir(), DIR_FRAMES);
        if (!in.isDirectory() && !in.mkdirs()) {
            Log.w(TAG, "mkdirs internal frames: " + in);
        }
        MPhotoPublicStorage.migratePublicMonoSubdirToFilesDirIfEmpty(context, DIR_FRAMES);
        return new DownloadTarget(in, null, false, false);
    }

    private static DownloadTarget subDownloadTarget(Context context) {
        if (MPhotoProImageStore.useMediaStoreForPublicWrites()) {
            File st = new File(context.getCacheDir(), "mono_sync/subs");
            if (!st.isDirectory() && !st.mkdirs()) {
                Log.w(TAG, "cache staging subs: " + st);
            }
            return new DownloadTarget(st, UserAssetFileStore.REL_SUBS, true, true);
        }
        if (MPhotoPublicStorage.isExternalAvailable() && Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            File d = new File(MPhotoPublicStorage.getAppPublicRoot(), UserAssetFileStore.REL_SUBS);
            if (d.isDirectory() || d.mkdirs()) {
                return new DownloadTarget(d, UserAssetFileStore.REL_SUBS, true, false);
            }
            Log.w(TAG, "cannot mkdir public subs, fallback internal: " + d);
        }
        File in = new File(context.getFilesDir(), DIR_SUBS);
        if (!in.isDirectory() && !in.mkdirs()) {
            Log.w(TAG, "mkdirs internal subs: " + in);
        }
        MPhotoPublicStorage.migratePublicMonoSubdirToFilesDirIfEmpty(context, DIR_SUBS);
        return new DownloadTarget(in, null, false, false);
    }

    public static void syncFrames(Context context, String token) throws Exception {
        JSONArray list = ApiService.getJsonArrayAuthed("/assets/mono/frames", token);
        if (list == null) {
            list = new JSONArray();
        }
        DownloadTarget t = frameDownloadTarget(context);
        Set<String> remoteFileNames = new HashSet<>();
        for (int i = 0; i < list.length(); i++) {
            JSONObject o = list.getJSONObject(i);
            String id = itemId(o);
            String u = o.optString("url", null);
            if (id == null || u == null || u.isEmpty()) {
                continue;
            }
            remoteFileNames.add(fileNameForId(id));
        }
        if (!t.useMediaStore) {
            pruneDir(t.dir, remoteFileNames);
        }
        for (int i = 0; i < list.length(); i++) {
            JSONObject o = list.getJSONObject(i);
            String id = itemId(o);
            String u = o.optString("url", null);
            if (id == null || u == null || u.isEmpty()) {
                continue;
            }
            String fn = fileNameForId(id);
            if (t.useMediaStore) {
                // Không phụ thuộc query MediaStore vì một số máy trả rỗng dù vừa insert.
                downloadAndStore(context, t, u, fn);
            } else {
                File f = new File(t.dir, fn);
                if (f.isFile() && f.length() > 0) {
                    continue;
                }
                downloadAndStore(context, t, u, fn);
            }
        }
        List<String> listEntries = new ArrayList<>();
        for (int i = 0; i < list.length(); i++) {
            JSONObject o = list.getJSONObject(i);
            String id = itemId(o);
            if (id == null) {
                continue;
            }
            String fn = fileNameForId(id);
            if (t.useMediaStore) {
                listEntries.add(UserAssetFileStore.tokenForRelativePath(
                    t.publicRel + "/" + fn));
            } else {
                File f = new File(t.dir, fn);
                if (!f.isFile() || f.length() == 0) {
                    continue;
                }
                if (t.usePublicTokens && t.publicRel != null) {
                    listEntries.add(UserAssetFileStore.tokenForRelativePath(
                        t.publicRel + "/" + fn));
                } else {
                    listEntries.add(Base64.encodeToString(readAllBytes(new File(t.dir, fn)), Base64.DEFAULT));
                }
            }
        }
        SharedPreferences p = context.getSharedPreferences("FrameImage", Context.MODE_PRIVATE);
        SharedPreferences.Editor ed = p.edit();
        ed.putString("bitmap_list", new Gson().toJson(listEntries));
        int idx = p.getInt("current_index", 0);
        if (listEntries.isEmpty()) {
            idx = 0;
        } else {
            if (idx >= listEntries.size()) {
                idx = listEntries.size() - 1;
            }
            if (idx < 0) {
                idx = 0;
            }
        }
        ed.putInt("current_index", idx);
        ed.apply();
        MPhotoUserDataBackup.scheduleSave(context.getApplicationContext());
    }

    public static void syncSubPhotos(Context context, String token) throws Exception {
        JSONArray list = ApiService.getJsonArrayAuthed("/assets/mono/sub-photos", token);
        if (list == null) {
            list = new JSONArray();
        }
        DownloadTarget t = subDownloadTarget(context);
        Set<String> remoteFileNames = new HashSet<>();
        for (int i = 0; i < list.length(); i++) {
            JSONObject o = list.getJSONObject(i);
            String id = itemId(o);
            String u = o.optString("url", null);
            if (id == null || u == null || u.isEmpty()) {
                continue;
            }
            remoteFileNames.add(fileNameForId(id));
        }
        if (!t.useMediaStore) {
            pruneDir(t.dir, remoteFileNames);
        }
        for (int i = 0; i < list.length(); i++) {
            JSONObject o = list.getJSONObject(i);
            String id = itemId(o);
            String u = o.optString("url", null);
            if (id == null || u == null || u.isEmpty()) {
                continue;
            }
            String fn = fileNameForId(id);
            if (t.useMediaStore) {
                // Không phụ thuộc query MediaStore vì một số máy trả rỗng dù vừa insert.
                downloadAndStore(context, t, u, fn);
            } else {
                File f = new File(t.dir, fn);
                if (f.isFile() && f.length() > 0) {
                    continue;
                }
                downloadAndStore(context, t, u, fn);
            }
        }
        List<String> subEntries = new ArrayList<>();
        for (int i = 0; i < list.length(); i++) {
            JSONObject o = list.getJSONObject(i);
            String id = itemId(o);
            if (id == null) {
                continue;
            }
            String fn = fileNameForId(id);
            if (t.useMediaStore) {
                subEntries.add(UserAssetFileStore.tokenForRelativePath(
                    t.publicRel + "/" + fn));
            } else {
                File f = new File(t.dir, fn);
                if (!f.isFile() || f.length() == 0) {
                    continue;
                }
                if (t.usePublicTokens && t.publicRel != null) {
                    subEntries.add(UserAssetFileStore.tokenForRelativePath(
                        t.publicRel + "/" + fn));
                } else {
                    subEntries.add(Base64.encodeToString(readAllBytes(new File(t.dir, fn)), Base64.DEFAULT));
                }
            }
        }
        SharedPreferences p = context.getSharedPreferences("MyAppPrefs2", Context.MODE_PRIVATE);
        SharedPreferences.Editor ed = p.edit();
        ed.putString("ImageViewList", new Gson().toJson(subEntries));
        int idx = p.getInt("indexImageView2", 0);
        if (subEntries.isEmpty()) {
            idx = 0;
        } else {
            if (idx >= subEntries.size()) {
                idx = subEntries.size() - 1;
            }
            if (idx < 0) {
                idx = 0;
            }
        }
        ed.putInt("indexImageView2", idx);
        ed.apply();
        MPhotoUserDataBackup.scheduleSave(context.getApplicationContext());
    }

    private static String fileNameForId(String id) {
        String s = id.replaceAll("[^a-zA-Z0-9._-]", "_");
        if (!s.contains(".")) {
            // MediaStore/decoder ổn định hơn khi tên có extension ảnh.
            s = s + ".jpg";
        }
        return s;
    }

    private static void pruneDir(File dir, Set<String> remoteIds) {
        File[] list = dir.listFiles();
        if (list == null) {
            return;
        }
        for (File f : list) {
            if (!f.isFile()) {
                continue;
            }
            String name = f.getName();
            if (!remoteIds.contains(name)) {
                if (!f.delete()) {
                    Log.w(TAG, "delete fail " + f.getAbsolutePath());
                }
            }
        }
    }

    /**
     * Tải tạm (cache) rồi ghi bằng File API hoặc MediaStore — <strong>không</strong> tạo .part dưới
     * {@code /Pictures/...} (sẽ bị {@code EPERM} trên Android 10+).
     */
    private static void downloadAndStore(Context context, DownloadTarget t, String imageUrl, String fileName)
        throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(imageUrl).openConnection();
        c.setRequestMethod("GET");
        c.setConnectTimeout(60000);
        c.setReadTimeout(120000);
        c.setInstanceFollowRedirects(true);
        int code = c.getResponseCode();
        if (code < 200 || code >= 300) {
            c.disconnect();
            throw new Exception("Tải ảnh lỗi HTTP " + code);
        }
        String mime = c.getContentType();
        if (mime != null) {
            int sc = mime.indexOf(';');
            if (sc > 0) {
                mime = mime.substring(0, sc).trim();
            }
        }
        if (mime == null || mime.isEmpty()) {
            mime = "image/jpeg";
        }
        File part = new File(t.dir, fileName + ".part");
        try (InputStream in = c.getInputStream();
             FileOutputStream out = new FileOutputStream(part)) {
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) != -1) {
                out.write(buf, 0, n);
            }
            out.flush();
        } finally {
            c.disconnect();
        }
        try {
            if (t.useMediaStore) {
                MPhotoProImageStore.replaceWithFile(context, t.publicRel, fileName, part, mime);
            } else {
                File dest = new File(t.dir, fileName);
                if (dest.exists() && !dest.delete()) {
                    Log.w(TAG, "cannot remove old " + dest);
                }
                if (!part.renameTo(dest)) {
                    if (!copyFile(part, dest)) {
                        throw new Exception("Không lưu được file tạm");
                    }
                    if (!part.delete()) {
                        Log.w(TAG, "tmp delete");
                    }
                }
            }
        } finally {
            if (part.exists() && !part.delete()) {
                Log.w(TAG, "stale part: " + part);
            }
        }
    }

    private static boolean copyFile(File from, File to) {
        try (FileInputStream in = new FileInputStream(from);
             FileOutputStream out = new FileOutputStream(to)) {
            byte[] b = new byte[8192];
            int n;
            while ((n = in.read(b)) != -1) {
                out.write(b, 0, n);
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static byte[] readAllBytes(File f) throws Exception {
        try (FileInputStream in = new FileInputStream(f);
             java.io.ByteArrayOutputStream b = new java.io.ByteArrayOutputStream()) {
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) != -1) {
                b.write(buf, 0, n);
            }
            return b.toByteArray();
        }
    }

    private static String itemId(JSONObject o) {
        if (o == null) {
            return null;
        }
        try {
            if (o.has("_id") && !o.isNull("_id")) {
                Object idVal = o.get("_id");
                if (idVal instanceof String) {
                    return (String) idVal;
                }
                if (idVal instanceof JSONObject) {
                    String oid = ((JSONObject) idVal).optString("$oid", null);
                    if (oid != null && !oid.isEmpty()) {
                        return oid;
                    }
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "itemId", e);
        }
        String id = o.optString("_id", null);
        if (id == null || id.isEmpty()) {
            id = o.optString("id", null);
        }
        return (id == null || id.isEmpty()) ? null : id;
    }
}
