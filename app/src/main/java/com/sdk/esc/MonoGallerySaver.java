package com.sdk.esc;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.graphics.Bitmap;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Lưu ảnh in (bitmap) vào thư mục ảnh hệ thống {@code Pictures/M-Photo Mono_{userId}/}
 * để xuất hiện trong thư viện / Photos, đồng thời ghi bản lưu qua
 * {@link MPhotoPublicStorage} (dưới {@code M-Photo_Pro/...}) — cùng lớp bộ nhớ
 * với dữ liệu đồng bộ khác; gỡ app thường không xóa thư mục này.
 */
public final class MonoGallerySaver {
    private static final String TAG = "MonoGallerySaver";
    public static final String FOLDER_PREFIX = "M-Photo Mono_";

    public static void savePrintedBitmapToGallery(Context context, Bitmap bitmap) {
        if (bitmap == null || bitmap.isRecycled()) {
            return;
        }
        String fileName = "IMG_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date()) + ".jpg";
        saveBitmapToMonoFolder(context, bitmap, fileName);
    }

    /**
     * Lưu với tên file tùy chọn (vd. chế độ test: {@code yyyyMMdd_HHmmss_xxxxxxxxxx.jpg}).
     */
    public static void saveBitmapToMonoFolder(Context context, Bitmap bitmap, String fileName) {
        if (bitmap == null || bitmap.isRecycled()) {
            return;
        }
        if (fileName == null || fileName.isEmpty()) {
            return;
        }
        String lower = fileName.toLowerCase(Locale.US);
        if (!lower.endsWith(".jpg") && !lower.endsWith(".jpeg")) {
            return;
        }
        String userId = TokenManager.getInstance(context).getUserId();
        if (userId == null || userId.isEmpty()) {
            userId = "unknown";
        }
        String safeId = userId.replaceAll("[^a-zA-Z0-9._-]", "_");
        String relFolder = FOLDER_PREFIX + safeId;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            saveMediaStoreQ(context, bitmap, fileName, relFolder);
        } else {
            saveLegacyFile(context, bitmap, fileName, relFolder);
        }
        saveMirrorInMPhotoProPublicRoot(bitmap, fileName, relFolder);
    }

    private static void saveMediaStoreQ(Context context, Bitmap bitmap, String fileName, String relFolder) {
        ContentValues values = new ContentValues();
        values.put(MediaStore.Images.Media.DISPLAY_NAME, fileName);
        values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
        values.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/" + relFolder);
        values.put(MediaStore.Images.Media.IS_PENDING, 1);
        ContentResolver r = context.getContentResolver();
        Uri uri = r.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
        if (uri == null) {
            Log.e(TAG, "insert MediaStore null");
            return;
        }
        try (OutputStream out = r.openOutputStream(uri)) {
            if (out != null) {
                bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out);
            }
        } catch (IOException e) {
            Log.e(TAG, "write", e);
            r.delete(uri, null, null);
            return;
        }
        values.clear();
        values.put(MediaStore.Images.Media.IS_PENDING, 0);
        r.update(uri, values, null, null);
        Log.d(TAG, "saved " + fileName);
    }

    /**
     * Bản lưu thêm trên ổ dùng chung, tránh phụ thuộc toàn bộ MediaStore/scan trên từng máy.
     */
    public static void saveMirrorInMPhotoProPublicRoot(Bitmap bitmap, String fileName, String relFolder) {
        if (bitmap == null || bitmap.isRecycled() || fileName == null || relFolder == null) {
            return;
        }
        if (!MPhotoPublicStorage.isExternalAvailable()) {
            return;
        }
        File base = MPhotoPublicStorage.getAppPublicRoot();
        File dir = new File(base, relFolder);
        if (!dir.isDirectory() && !dir.mkdirs()) {
            Log.w(TAG, "mirror mkdirs " + dir);
            return;
        }
        File out = new File(dir, fileName);
        try (FileOutputStream fos = new FileOutputStream(out)) {
            bitmap.compress(Bitmap.CompressFormat.JPEG, 95, fos);
            fos.flush();
        } catch (IOException e) {
            Log.w(TAG, "mirror write " + out, e);
            return;
        }
        Log.d(TAG, "mirror " + out.getAbsolutePath());
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    private static void saveLegacyFile(Context context, Bitmap bitmap, String fileName, String relFolder) {
        File base = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
        File dir = new File(base, relFolder);
        if (!dir.exists() && !dir.mkdirs()) {
            Log.e(TAG, "mkdirs fail " + dir);
            return;
        }
        File outFile = new File(dir, fileName);
        try (FileOutputStream fos = new FileOutputStream(outFile)) {
            bitmap.compress(Bitmap.CompressFormat.JPEG, 95, fos);
            fos.flush();
        } catch (IOException e) {
            Log.e(TAG, "file write", e);
            return;
        }
        MediaScannerConnection.scanFile(
            context,
            new String[] { outFile.getAbsolutePath() },
            new String[] { "image/jpeg" },
            null
        );
        Log.d(TAG, "saved " + outFile.getAbsolutePath());
    }
}
