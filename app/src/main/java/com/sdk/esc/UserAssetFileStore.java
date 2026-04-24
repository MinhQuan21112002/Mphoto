package com.sdk.esc;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Base64;
import android.util.Log;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Frame + ảnh nền (ảnh phụ): lưu file dưới {@code Pictures/M-Photo_Pro/app_user_assets/}
 * thay vì Base64 toàn bộ trong SharedPreferences. Gỡ app cài lại, file vẫn còn; prefs trong app
 * khôi phục từ {@link MPhotoUserDataBackup} chỉ cần token nhẹ {@code F:app_user_assets/...}.
 * Mục cũ Base64 (không bắt đầu bằng {@code F:}) vẫn đọc được; có thể chuyển bằng
 * {@link #migrateListToFileTokensIfNeeded}.
 */
public final class UserAssetFileStore {
    private static final String TAG = "UserAssetFileStore";
    public static final String REL_FRAMES = "app_user_assets/frames";
    public static final String REL_SUBS = "app_user_assets/subs";
    private static final String TOKEN_PREFIX = "F:";

    private UserAssetFileStore() {}

    public static boolean isFileToken(String s) {
        return s != null && s.startsWith(TOKEN_PREFIX) && s.length() > TOKEN_PREFIX.length() + 1;
    }

    public static String tokenForRelativePath(String relPath) {
        if (relPath == null || relPath.isEmpty()) {
            return null;
        }
        String t = relPath.replace('\\', '/').replaceAll("^/+", "");
        return TOKEN_PREFIX + t;
    }

    public static String relativePathFromToken(String token) {
        if (!isFileToken(token)) {
            return null;
        }
        return token.substring(TOKEN_PREFIX.length());
    }

    public static File fileForToken(Context c, String token) {
        if (!isFileToken(token)) {
            return null;
        }
        return new File(MPhotoPublicStorage.getAppPublicRoot(), relativePathFromToken(token));
    }

    /**
     * Lưu bitmap → file JPEG trong thư mục công khai; trả về token hoặc null nếu không ghi được.
     */
    public static String saveBitmapAsFileToken(Context ctx, Bitmap bitmap, boolean isFrame) {
        if (bitmap == null || bitmap.isRecycled()) {
            return null;
        }
        if (!MPhotoPublicStorage.isExternalAvailable()) {
            return null;
        }
        String mphoto = isFrame ? REL_FRAMES : REL_SUBS;
        String display = "u_" + System.currentTimeMillis()
            + "_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8) + ".jpg";
        String rel = mphoto + "/" + display;
        if (MPhotoProImageStore.useMediaStoreForPublicWrites()) {
            try {
                MPhotoProImageStore.saveJpegBitmap(ctx, mphoto, display, bitmap);
            } catch (IOException e) {
                Log.w(TAG, "MediaStore write " + rel, e);
                return null;
            }
            return tokenForRelativePath(rel);
        }
        File out = new File(MPhotoPublicStorage.getAppPublicRoot(), rel);
        File parent = out.getParentFile();
        if (parent == null) {
            return null;
        }
        if (!parent.isDirectory() && !parent.mkdirs()) {
            Log.w(TAG, "mkdirs: " + parent);
            return null;
        }
        try (FileOutputStream fos = new FileOutputStream(out)) {
            if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 95, fos)) {
                return null;
            }
            fos.flush();
        } catch (IOException e) {
            Log.w(TAG, "write " + out, e);
            return null;
        }
        return tokenForRelativePath(rel);
    }

    public static Bitmap decodeListEntryToBitmap(Context ctx, String entry) {
        if (entry == null || entry.isEmpty()) {
            return null;
        }
        if (isFileToken(entry)) {
            File f = fileForToken(ctx, entry);
            if (f != null && f.isFile() && f.length() > 0) {
                Bitmap b = BitmapFactory.decodeFile(f.getAbsolutePath());
                if (b != null) {
                    return b;
                }
            }
            if (MPhotoProImageStore.useMediaStoreForPublicWrites()) {
                String rp = relativePathFromToken(entry);
                if (rp != null) {
                    int ls = rp.lastIndexOf('/');
                    if (ls > 0) {
                        String mphoto = rp.substring(0, ls);
                        String name = rp.substring(ls + 1);
                        Bitmap b = MPhotoProImageStore.openBitmap(ctx, mphoto, name);
                        if (b != null) {
                            return b;
                        }
                    }
                }
            }
            Log.w(TAG, "Thiếu file cho token: " + entry);
            return null;
        }
        try {
            byte[] b = Base64.decode(entry, Base64.DEFAULT);
            if (b == null || b.length == 0) {
                return null;
            }
            return BitmapFactory.decodeByteArray(b, 0, b.length);
        } catch (Exception e) {
            Log.w(TAG, "decode base64", e);
            return null;
        }
    }

    public static void deleteFileForListEntry(Context ctx, String entry) {
        if (!isFileToken(entry)) {
            return;
        }
        File f = fileForToken(ctx, entry);
        if (f != null && f.isFile() && f.delete()) {
            return;
        }
        if (MPhotoProImageStore.useMediaStoreForPublicWrites()) {
            String rp = relativePathFromToken(entry);
            if (rp != null) {
                int ls = rp.lastIndexOf('/');
                if (ls > 0) {
                    MPhotoProImageStore.deleteByDisplayName(ctx, rp.substring(0, ls), rp.substring(ls + 1));
                }
            }
        } else if (f != null && f.isFile() && !f.delete()) {
            Log.w(TAG, "delete fail: " + f);
        }
    }

    public static void migrateListToFileTokensIfNeeded(Context appCtx) {
        Context c = appCtx.getApplicationContext();
        if (!MPhotoPublicStorage.isExternalAvailable()) {
            return;
        }
        boolean a = migrateOnePref(c, "FrameImage", "bitmap_list", true);
        boolean b = migrateOnePref(c, "MyAppPrefs2", "ImageViewList", false);
        if (a || b) {
            MPhotoUserDataBackup.scheduleSave(c);
        }
    }

    private static boolean migrateOnePref(Context c, String prefName, String key, boolean isFrame) {
        SharedPreferences p = c.getSharedPreferences(prefName, Context.MODE_PRIVATE);
        String json = p.getString(key, "[]");
        Gson gson = new Gson();
        List<String> list;
        try {
            list = gson.fromJson(json, new TypeToken<List<String>>() {}.getType());
        } catch (Exception e) {
            return false;
        }
        if (list == null || list.isEmpty()) {
            return false;
        }
        List<String> newList = new ArrayList<>();
        boolean changed = false;
        for (String item : list) {
            if (isFileToken(item)) {
                newList.add(item);
                continue;
            }
            if (item == null || item.isEmpty()) {
                continue;
            }
            Bitmap bm = null;
            try {
                byte[] b = Base64.decode(item, Base64.DEFAULT);
                if (b != null && b.length > 0) {
                    bm = BitmapFactory.decodeByteArray(b, 0, b.length);
                }
            } catch (Exception e) {
                Log.w(TAG, "migrate base64", e);
            }
            if (bm != null) {
                String t = saveBitmapAsFileToken(c, bm, isFrame);
                bm.recycle();
                if (t != null) {
                    newList.add(t);
                    changed = true;
                } else {
                    newList.add(item);
                }
            } else {
                newList.add(item);
            }
        }
        if (changed) {
            p.edit().putString(key, gson.toJson(newList)).apply();
            Log.d(TAG, "Migrated to file tokens: " + prefName + "/" + key);
        }
        return changed;
    }
}
