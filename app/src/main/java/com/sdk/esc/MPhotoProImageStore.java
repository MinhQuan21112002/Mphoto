package com.sdk.esc;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Set;

/**
 * Ghi/đọc/xóa ảnh dưới {@code Pictures/M-Photo_Pro/...} qua <strong>MediaStore</strong> (Android 10+)
 * thay vì mở {@link java.io.FileOutputStream} thẳng trên đường dẫn tuyệt đối (thường bị
 * {@code EPERM} với scoped storage). API &lt; 29 vẫn dùng file qua
 * {@link MPhotoPublicStorage#getAppPublicRoot()}.
 */
public final class MPhotoProImageStore {
    private static final String TAG = "MPhotoProImageStore";

    private MPhotoProImageStore() {}

    public static boolean useMediaStoreForPublicWrites() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
            && MPhotoPublicStorage.isExternalAvailable();
    }

    /** RELATIVE_PATH trên ổ (MediaStore) — cùng format khi {@link MonoGallerySaver} insert. */
    public static String mediaStoreRelativePathFor(String mphotoPathUnderFolder) {
        return Environment.DIRECTORY_PICTURES + "/"
            + MPhotoPublicStorage.PUBLIC_FOLDER + "/"
            + mphotoPathUnderFolder.replace("\\", "/").replaceAll("^/+", "");
    }

    private static String[] relativePathCandidates(String mphotoPathUnderFolder) {
        String rel = mediaStoreRelativePathFor(mphotoPathUnderFolder);
        if (rel.endsWith("/")) {
            return new String[] { rel, rel.substring(0, rel.length() - 1) };
        }
        return new String[] { rel, rel + "/" };
    }

    public static void replaceWithFile(
        Context context,
        String mphotoPathUnderFolder,
        String displayName,
        File tempContent,
        String mimeType
    ) throws IOException {
        if (displayName == null || displayName.isEmpty()) {
            return;
        }
        String mt = (mimeType == null || mimeType.isEmpty()) ? "image/jpeg" : mimeType;
        if (mt.contains(";")) {
            mt = mt.split(";")[0].trim();
        }
        deleteByDisplayName(context, mphotoPathUnderFolder, displayName);
        ContentResolver r = context.getContentResolver();
        ContentValues v = new ContentValues();
        v.put(MediaStore.Images.Media.DISPLAY_NAME, displayName);
        v.put(MediaStore.Images.Media.MIME_TYPE, mt);
        v.put(MediaStore.Images.Media.RELATIVE_PATH, mediaStoreRelativePathFor(mphotoPathUnderFolder));
        v.put(MediaStore.Images.Media.IS_PENDING, 1);
        Uri collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
        Uri uri = r.insert(collection, v);
        if (uri == null) {
            throw new IOException("MediaStore insert null: " + displayName);
        }
        try (InputStream in = new FileInputStream(tempContent);
             OutputStream out = r.openOutputStream(uri)) {
            if (out == null) {
                r.delete(uri, null, null);
                throw new IOException("openOutputStream null: " + uri);
            }
            byte[] b = new byte[16384];
            int n;
            while ((n = in.read(b)) != -1) {
                out.write(b, 0, n);
            }
            out.flush();
        } catch (Exception e) {
            try {
                r.delete(uri, null, null);
            } catch (Exception ignored) {
            }
            throw new IOException(e);
        }
        v.clear();
        v.put(MediaStore.Images.Media.IS_PENDING, 0);
        r.update(uri, v, null, null);
    }

    public static void saveJpegBitmap(
        Context context,
        String mphotoPathUnderFolder,
        String displayName,
        Bitmap bitmap
    ) throws IOException {
        if (bitmap == null || bitmap.isRecycled()) {
            return;
        }
        deleteByDisplayName(context, mphotoPathUnderFolder, displayName);
        ContentResolver r = context.getContentResolver();
        ContentValues v = new ContentValues();
        v.put(MediaStore.Images.Media.DISPLAY_NAME, displayName);
        v.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
        v.put(MediaStore.Images.Media.RELATIVE_PATH, mediaStoreRelativePathFor(mphotoPathUnderFolder));
        v.put(MediaStore.Images.Media.IS_PENDING, 1);
        Uri collection = MediaStore.Images.Media.getContentUri("external");
        Uri uri = r.insert(collection, v);
        if (uri == null) {
            throw new IOException("MediaStore insert null");
        }
        try (OutputStream out = r.openOutputStream(uri)) {
            if (out != null) {
                bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out);
            }
        } catch (Exception e) {
            try {
                r.delete(uri, null, null);
            } catch (Exception ignored) {
            }
            throw new IOException(e);
        }
        v.clear();
        v.put(MediaStore.Images.Media.IS_PENDING, 0);
        r.update(uri, v, null, null);
    }

    public static boolean exists(Context context, String mphotoPathUnderFolder, String displayName) {
        return queryFirstId(context, mphotoPathUnderFolder, displayName) != null;
    }

    public static Uri queryFirstId(Context context, String mphotoPathUnderFolder, String displayName) {
        String[] rels = relativePathCandidates(mphotoPathUnderFolder);
        ContentResolver r = context.getContentResolver();
        Uri u = queryFirstIdInCollection(r, MediaStore.Images.Media.EXTERNAL_CONTENT_URI, rels, displayName);
        if (u != null) {
            return u;
        }
        u = queryFirstIdInCollection(r, MediaStore.Images.Media.getContentUri("internal"), rels, displayName);
        if (u != null) {
            return u;
        }
        // Fallback cho trường hợp provider tự thêm extension (vd id -> id.jpg)
        u = queryFirstIdByPrefixInCollection(r, MediaStore.Images.Media.EXTERNAL_CONTENT_URI, rels, displayName);
        if (u != null) {
            return u;
        }
        return queryFirstIdByPrefixInCollection(r, MediaStore.Images.Media.getContentUri("internal"), rels, displayName);
    }

    private static Uri queryFirstIdInCollection(
        ContentResolver r,
        Uri col,
        String[] rels,
        String displayName
    ) {
        String[] proj = { MediaStore.Images.Media._ID };
        String sel = MediaStore.Images.Media.DISPLAY_NAME + " = ? AND "
            + "(" + MediaStore.Images.Media.RELATIVE_PATH + " = ? OR "
            + MediaStore.Images.Media.RELATIVE_PATH + " = ?)";
        String[] args = { displayName, rels[0], rels[1] };
        try (Cursor c = r.query(col, proj, sel, args, null)) {
            if (c != null && c.moveToFirst()) {
                long id = c.getLong(0);
                return ContentUris.withAppendedId(col, id);
            }
        } catch (Exception e) {
            Log.w(TAG, "exists " + displayName + " @ " + col, e);
        }
        return null;
    }

    private static Uri queryFirstIdByPrefixInCollection(
        ContentResolver r,
        Uri col,
        String[] rels,
        String displayName
    ) {
        String[] proj = { MediaStore.Images.Media._ID, MediaStore.Images.Media.DISPLAY_NAME };
        String sel = "(" + MediaStore.Images.Media.RELATIVE_PATH + " = ? OR "
            + MediaStore.Images.Media.RELATIVE_PATH + " = ?) AND "
            + MediaStore.Images.Media.DISPLAY_NAME + " LIKE ?";
        String[] args = { rels[0], rels[1], displayName + "%" };
        try (Cursor c = r.query(col, proj, sel, args, MediaStore.Images.Media.DATE_MODIFIED + " DESC")) {
            if (c != null && c.moveToFirst()) {
                long id = c.getLong(0);
                return ContentUris.withAppendedId(col, id);
            }
        } catch (Exception e) {
            Log.w(TAG, "exists prefix " + displayName + " @ " + col, e);
        }
        return null;
    }

    public static Bitmap openBitmap(Context context, String mphotoPathUnderFolder, String displayName) {
        Uri u = queryFirstId(context, mphotoPathUnderFolder, displayName);
        if (u == null) {
            return null;
        }
        ContentResolver r = context.getContentResolver();
        try (InputStream in = r.openInputStream(u)) {
            if (in == null) {
                return null;
            }
            return android.graphics.BitmapFactory.decodeStream(in);
        } catch (Exception e) {
            Log.w(TAG, "openBitmap " + displayName, e);
            return null;
        }
    }

    public static void deleteByDisplayName(
        Context context,
        String mphotoPathUnderFolder,
        String displayName
    ) {
        Uri u = queryFirstId(context, mphotoPathUnderFolder, displayName);
        if (u != null) {
            try {
                int n = context.getContentResolver().delete(u, null, null);
                if (n > 0) {
                    return;
                }
            } catch (Exception e) {
                Log.w(TAG, "delete uri " + u, e);
            }
        }
    }

    /**
     * Xóa mọi ảnh trong cùng “thư mục” (RELATIVE_PATH) mà tên <strong>không</strong> nằm trong {@code keep}.
     */
    public static void pruneNotInSet(
        Context context,
        String mphotoPathUnderFolder,
        Set<String> keep
    ) {
        String[] rels = relativePathCandidates(mphotoPathUnderFolder);
        ContentResolver r = context.getContentResolver();
        pruneInCollection(r, MediaStore.Images.Media.EXTERNAL_CONTENT_URI, rels, keep);
        pruneInCollection(r, MediaStore.Images.Media.getContentUri("internal"), rels, keep);
    }

    private static void pruneInCollection(
        ContentResolver r,
        Uri col,
        String[] rels,
        Set<String> keep
    ) {
        String[] proj = { MediaStore.Images.Media._ID, MediaStore.Images.Media.DISPLAY_NAME };
        String sel = "(" + MediaStore.Images.Media.RELATIVE_PATH + " = ? OR "
            + MediaStore.Images.Media.RELATIVE_PATH + " = ?)";
        String[] args = { rels[0], rels[1] };
        try (Cursor c = r.query(col, proj, sel, args, null)) {
            if (c == null) {
                return;
            }
            int idC = c.getColumnIndexOrThrow(MediaStore.Images.Media._ID);
            int nameC = c.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME);
            while (c.moveToNext()) {
                String name = c.getString(nameC);
                if (name == null) {
                    continue;
                }
                if (keep != null && keep.contains(name)) {
                    continue;
                }
                long id = c.getLong(idC);
                Uri u = ContentUris.withAppendedId(col, id);
                try {
                    r.delete(u, null, null);
                } catch (Exception e) {
                    Log.w(TAG, "prune delete " + u, e);
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "prune @ " + col, e);
        }
    }
}
