package com.sdk.esc;

import android.content.ContentResolver;
import android.content.Context;
import android.content.ContentUris;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.media.MediaScannerConnection;
import android.os.Environment;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.util.Log;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Đọc ảnh trong thư mục hệ thống {@code Pictures/M-Photo Mono_{userId}} (MediaStore / file) và
 * bản lưu phụ dưới {@code Pictures/M-Photo_Pro/...} từ {@link MonoGallerySaver#saveMirrorInMPhotoProPublicRoot}.
 */
public final class MonoFolderImages {
    private static final String TAG = "MonoFolderImages";
    public static final int PAGE_SIZE = 10;
    /** 12 lần × 10 ảnh */
    public static final int MAX_ITEMS = 120;

    private MonoFolderImages() {}

    public static String getFolderName(Context context) {
        String userId = TokenManager.getInstance(context).getUserId();
        if (userId == null || userId.isEmpty()) {
            userId = "unknown";
        }
        String safeId = userId.replaceAll("[^a-zA-Z0-9._-]", "_");
        return MonoGallerySaver.FOLDER_PREFIX + safeId;
    }

    /**
     * Một trang: tối đa {@link #PAGE_SIZE} URI, bắt đầu từ offset 0. Không vượt {@link #MAX_ITEMS}.
     */
    public static List<Uri> loadPage(Context context, int offset) {
        if (offset < 0 || offset >= MAX_ITEMS) {
            return Collections.emptyList();
        }
        int take = Math.min(PAGE_SIZE, MAX_ITEMS - offset);
        String folderName = getFolderName(context);
        try {
            List<Uri> merged = listMergedUrisUpTo120(context, folderName);
            if (offset < 0 || offset >= merged.size()) {
                return Collections.emptyList();
            }
            int end = Math.min(offset + take, merged.size());
            return new ArrayList<>(merged.subList(offset, end));
        } catch (Exception e) {
            Log.e(TAG, "loadPage", e);
            return Collections.emptyList();
        }
    }

    /**
     * Gộp ảnh từ thư mục mirror (M-Photo_Pro/…), thư mục Pictures truyền thống, và MediaStore.
     */
    private static List<Uri> listMergedUrisUpTo120(Context context, String folderName) {
        Map<String, DatedUri> byName = new HashMap<>();
        addFilesFromDir(new File(MPhotoPublicStorage.getAppPublicRoot(), folderName), byName);
        addFilesFromDir(new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), folderName), byName);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            addFromMediaStoreQ(context, folderName, byName);
        }
        List<DatedUri> list = new ArrayList<>(byName.values());
        list.sort(Comparator.comparingLong((DatedUri d) -> d.time).reversed());
        List<Uri> out = new ArrayList<>();
        for (DatedUri d : list) {
            if (out.size() >= MAX_ITEMS) {
                break;
            }
            out.add(d.uri);
        }
        return out;
    }

    private static final class DatedUri {
        final long time;
        final Uri uri;

        DatedUri(long time, Uri uri) {
            this.time = time;
            this.uri = uri;
        }
    }

    private static void addFilesFromDir(File dir, Map<String, DatedUri> byName) {
        if (dir == null || !dir.isDirectory()) {
            return;
        }
        File[] all = dir.listFiles((d, n) -> {
            String l = n.toLowerCase(Locale.US);
            return l.endsWith(".jpg") || l.endsWith(".jpeg");
        });
        if (all == null) {
            return;
        }
        for (File f : all) {
            if (f == null || !f.isFile()) {
                continue;
            }
            String name = f.getName();
            long t = f.lastModified();
            DatedUri o = byName.get(name);
            if (o == null || t > o.time) {
                byName.put(name, new DatedUri(t, Uri.fromFile(f)));
            }
        }
    }

    @SuppressWarnings("deprecation")
    private static void addFromMediaStoreQ(Context context, String folderName, Map<String, DatedUri> byName) {
        String rel = Environment.DIRECTORY_PICTURES + "/" + folderName;
        String relSlash = rel + "/";
        ContentResolver r = context.getContentResolver();
        String[] projection = {
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.DATE_MODIFIED
        };
        String sel = "(" + MediaStore.Images.Media.RELATIVE_PATH + " = ? OR "
            + MediaStore.Images.Media.RELATIVE_PATH + " = ?)";
        String[] args = { rel, relSlash };
        String sort = MediaStore.Images.Media.DATE_MODIFIED + " DESC";
        try (Cursor c = r.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI, projection, sel, args, sort)) {
            if (c == null) {
                return;
            }
            int idCol = c.getColumnIndexOrThrow(MediaStore.Images.Media._ID);
            int nameCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME);
            int dateCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_MODIFIED);
            while (c.moveToNext() && byName.size() < MAX_ITEMS + 200) {
                long id = c.getLong(idCol);
                String name = c.getString(nameCol);
                long t = c.getLong(dateCol) * 1000L;
                if (name == null) {
                    continue;
                }
                Uri uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id);
                DatedUri o = byName.get(name);
                if (o == null || t > o.time) {
                    byName.put(name, new DatedUri(t, uri));
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "addFromMediaStoreQ", e);
        }
    }

    public static Uri getLatestImageUri(Context context) {
        List<Uri> first = loadPage(context, 0);
        return first.isEmpty() ? null : first.get(0);
    }

    /**
     * Xóa mọi bản cùng tên: ảnh có thể trùng trên ổ (MediaStore + mirror M-Photo_Pro + thư mục Pictures/…)
     * nên chỉ xóa URI một lần thường vẫn còn file kia.
     */
    public static boolean deleteImage(Context context, Uri uri) {
        if (uri == null) {
            return false;
        }
        String folder = getFolderName(context);
        String fileName = resolveImageFileName(context, uri);
        int n = 0;
        n += tryDeleteUriEntry(context, uri, folder);
        if (fileName != null && !fileName.isEmpty()) {
            n += tryDeleteFile(new File(new File(MPhotoPublicStorage.getAppPublicRoot(), folder), fileName));
            n += tryDeleteFile(new File(
                new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), folder),
                fileName
            ));
            n += deleteMediaStoreByDisplayNameInMono(context, fileName, folder);
        }
        if (n == 0) {
            Log.w(TAG, "deleteImage: thất bại, uri=" + uri + " fileName=" + fileName);
        }
        if (n > 0) {
            MediaScannerConnection.scanFile(
                context,
                new String[] {
                    new File(MPhotoPublicStorage.getAppPublicRoot(), folder).getAbsolutePath(),
                    new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), folder)
                        .getAbsolutePath()
                },
                null,
                null
            );
        }
        return n > 0;
    }

    private static int tryDeleteFile(File f) {
        if (f == null) {
            return 0;
        }
        if (f.isFile() && f.exists() && f.delete()) {
            return 1;
        }
        return 0;
    }

    private static int tryDeleteUriEntry(Context context, Uri uri, String folder) {
        try {
            if (ContentResolver.SCHEME_CONTENT.equals(uri.getScheme())) {
                return context.getContentResolver().delete(uri, null, null) > 0 ? 1 : 0;
            }
            if (ContentResolver.SCHEME_FILE.equals(uri.getScheme())) {
                String path = uri.getPath();
                if (path == null || !isMonoRelatedDiskPath(path, folder)) {
                    return 0;
                }
                return tryDeleteFile(new File(path)) > 0 ? 1 : 0;
            }
        } catch (Exception e) {
            Log.w(TAG, "tryDeleteUriEntry", e);
        }
        return 0;
    }

    private static boolean isMonoRelatedDiskPath(String path, String folder) {
        if (path == null) {
            return false;
        }
        if (path.contains(folder)) {
            return true;
        }
        return path.contains(MPhotoPublicStorage.PUBLIC_FOLDER) && path.contains(folder);
    }

    /**
     * Xóa theo tên: query {@code DISPLAY_NAME} rồi lọc theo thư mục (RELATIVE_PATH / DATA) —
     * nhiều máy dùng dạng rel path hơi khác so với so khớp bằng.
     */
    @SuppressWarnings("deprecation")
    private static int deleteMediaStoreByDisplayNameInMono(
        Context context, String fileName, String folder
    ) {
        if (fileName == null || fileName.isEmpty()) {
            return 0;
        }
        ContentResolver r = context.getContentResolver();
        Uri collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
        String[] projection;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            projection = new String[]{
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.RELATIVE_PATH
            };
        } else {
            projection = new String[]{
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DATA
            };
        }
        String sel = MediaStore.Images.Media.DISPLAY_NAME + " = ?";
        String[] args = { fileName };
        int removed = 0;
        try (Cursor c = r.query(collection, projection, sel, args, null)) {
            if (c == null) {
                return 0;
            }
            int idCol = c.getColumnIndexOrThrow(MediaStore.Images.Media._ID);
            int pathCol = c.getColumnIndexOrThrow(
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                    ? MediaStore.Images.Media.RELATIVE_PATH
                    : MediaStore.Images.Media.DATA
            );
            while (c.moveToNext()) {
                long id = c.getLong(idCol);
                String p = c.getString(pathCol);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    if (!isMonoRelatedRelPath(p, folder)) {
                        continue;
                    }
                } else {
                    if (p == null || !isMonoRelatedDiskPath(p, folder)) {
                        continue;
                    }
                }
                Uri u = ContentUris.withAppendedId(collection, id);
                if (r.delete(u, null, null) > 0) {
                    removed++;
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "deleteMediaStoreByDisplayNameInMono", e);
        }
        return removed;
    }

    private static boolean isMonoRelatedRelPath(String relPath, String folder) {
        if (relPath == null) {
            return false;
        }
        if (relPath.contains(folder)) {
            return true;
        }
        return relPath.contains(MPhotoPublicStorage.PUBLIC_FOLDER) && relPath.contains(folder);
    }

    private static String resolveImageFileName(Context context, Uri uri) {
        if (ContentResolver.SCHEME_FILE.equals(uri.getScheme())) {
            String p = uri.getPath();
            if (p == null) {
                return null;
            }
            return new File(p).getName();
        }
        if (ContentResolver.SCHEME_CONTENT.equals(uri.getScheme())) {
            String fromOpenable = null;
            try (Cursor c = context.getContentResolver().query(
                uri, new String[]{OpenableColumns.DISPLAY_NAME, MediaStore.Images.Media.DISPLAY_NAME},
                null, null, null
            )) {
                if (c != null && c.moveToFirst()) {
                    int o = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (o >= 0) {
                        fromOpenable = c.getString(o);
                    }
                    if (fromOpenable == null) {
                        int m = c.getColumnIndex(MediaStore.Images.Media.DISPLAY_NAME);
                        if (m >= 0) {
                            fromOpenable = c.getString(m);
                        }
                    }
                }
            } catch (Exception e) {
                Log.w(TAG, "resolveImageFileName openable", e);
            }
            if (fromOpenable != null && !fromOpenable.isEmpty()) {
                return fromOpenable;
            }
            try {
                long id = ContentUris.parseId(uri);
                if (id > 0) {
                    return queryDisplayNameByImageRowId(context, id);
                }
            } catch (Exception e) {
                Log.w(TAG, "resolveImageFileName parseId", e);
            }
        }
        return null;
    }

    private static String queryDisplayNameByImageRowId(Context context, long id) {
        try (Cursor c = context.getContentResolver().query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            new String[]{MediaStore.Images.Media.DISPLAY_NAME},
            MediaStore.Images.Media._ID + " = ?",
            new String[]{String.valueOf(id)},
            null
        )) {
            if (c != null && c.moveToFirst()) {
                return c.getString(0);
            }
        } catch (Exception e) {
            Log.w(TAG, "queryDisplayNameByImageRowId", e);
        }
        return null;
    }
}
