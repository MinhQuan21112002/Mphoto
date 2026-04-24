package com.sdk.esc;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Build;
import android.util.Log;

import androidx.annotation.Nullable;

import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.http.FileContent;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import com.google.api.services.drive.model.Permission;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;

import com.google.api.services.drive.model.FileList;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class GoogleDriveService {
    private static final String TAG = "GoogleDriveService";
    private Drive driveService;
    private Context context;

    public GoogleDriveService(Context context) {
        this.context = context;
        try {
            InputStream inputStream = context.getResources().openRawResource(R.raw.setting);
            GoogleCredential credential = GoogleCredential.fromStream(inputStream)
                    .createScoped(Collections.singleton(DriveScopes.DRIVE));

            driveService = new Drive.Builder(
                    new NetHttpTransport(),
                    JacksonFactory.getDefaultInstance(),
                    credential)
                    .setApplicationName("My Drive App")
                    .build();
            Log.d(TAG, "Đã kết nối Google Drive thành công!");
        } catch (Exception e) {
            Log.e(TAG, "Lỗi kết nối Google Drive: " + e.getMessage(), e);
        }
    }

    /**
     * Thiết lập quyền xem công khai cho file/folder
     */
    private void setPublicPermission(String fileId) {
        try {
            Permission permission = new Permission();
            permission.setType("anyone");
            permission.setRole("reader"); // Chỉ cho phép xem, không chỉnh sửa

            driveService.permissions()
                    .create(fileId, permission)
                    .setFields("id")
                    .execute();

            Log.d(TAG, "Đã thiết lập quyền xem công khai cho: " + fileId);
        } catch (Exception e) {
            Log.e(TAG, "Lỗi thiết lập quyền công khai: " + e.getMessage(), e);
        }
    }

    /**
     * Thiết lập quyền chỉnh sửa công khai cho folder (dùng cho folder cha)
     */
    private void setPublicEditorPermission(String folderId) {
        try {
            Permission permission = new Permission();
            permission.setType("anyone");
            permission.setRole("writer"); // Cho phép xem và chỉnh sửa

            driveService.permissions()
                    .create(folderId, permission)
                    .setFields("id")
                    .execute();

            Log.d(TAG, "Đã thiết lập quyền chỉnh sửa công khai cho folder: " + folderId);
        } catch (Exception e) {
            Log.e(TAG, "Lỗi thiết lập quyền chỉnh sửa công khai: " + e.getMessage(), e);
        }
    }

    /**
     * Share folder với email cụ thể
     */
    private void shareFolderWithEmail(String folderId, String email) {
        try {
            Permission permission = new Permission();
            permission.setType("user");
            permission.setRole("writer"); // Cho phép xem và chỉnh sửa
            permission.setEmailAddress(email);

            driveService.permissions()
                    .create(folderId, permission)
                    .setFields("id")
                    .execute();

            Log.d(TAG, "Đã share folder với email: " + email);
        } catch (Exception e) {
            Log.e(TAG, "Lỗi share folder với email: " + e.getMessage(), e);
        }
    }

    /**
     * Tạo hoặc lấy ID của folder cha cho upload ảnh từ SharedPreferences
     * Nếu không tồn tại, tự động tạo folder mới
     */
    private String getOrCreateUploadParentFolder(Context context) {
        SharedPreferences sharedPreferences = context.getSharedPreferences("GoogleDrive", Context.MODE_PRIVATE);
        String parentFolderId = sharedPreferences.getString("UploadParentFolderId", null);

        // Kiểm tra xem folder cha có tồn tại không
        if (parentFolderId != null && !parentFolderId.isEmpty()) {
            if (isFolderExists(parentFolderId)) {
                String folderLink = "https://drive.google.com/drive/folders/" + parentFolderId;
                Log.d(TAG, "Folder cha Upload đã tồn tại: " + parentFolderId);
                Log.d(TAG, "🔗 Link folder: " + folderLink);
                Log.d(TAG, "📁 Tên folder: MPhoto_Upload_Folder");
                return parentFolderId;
            } else {
                Log.w(TAG, "Folder cha Upload đã bị xóa, tạo folder mới...");
            }
        }

        // Tạo folder cha mới nếu không tồn tại
        try {
            com.google.api.services.drive.model.File parentFolderMetadata = new com.google.api.services.drive.model.File();
            parentFolderMetadata.setName("MPhoto_Upload_Folder");
            parentFolderMetadata.setMimeType("application/vnd.google-apps.folder");

            com.google.api.services.drive.model.File parentFolder = driveService.files()
                    .create(parentFolderMetadata)
                    .setFields("id")
                    .execute();

            if (parentFolder != null) {
                String newParentFolderId = parentFolder.getId();
                // Thiết lập quyền chỉnh sửa công khai cho folder cha
                setPublicEditorPermission(newParentFolderId);
                // Share folder với email chủ sở hữu
                shareFolderWithEmail(newParentFolderId, "noelhomehcm@gmail.com");
                sharedPreferences.edit().putString("UploadParentFolderId", newParentFolderId).apply();
                MPhotoUserDataBackup.scheduleSave(context.getApplicationContext());
                String folderLink = "https://drive.google.com/drive/folders/" + newParentFolderId;
                Log.d(TAG, "Đã tạo folder cha Upload mới: " + newParentFolderId);
                Log.d(TAG, "🔗 Link folder: " + folderLink);
                Log.d(TAG, "📁 Tên folder: MPhoto_Upload_Folder");
                return newParentFolderId;
            }
        } catch (Exception e) {
            Log.e(TAG, "Lỗi tạo folder cha Upload: " + e.getMessage(), e);
        }
        return null;
    }

    @SuppressLint("SimpleDateFormat")
    public CompletableFuture<String> createSubFolder() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            return CompletableFuture.supplyAsync(() -> getOrCreateMonoUserUploadFolderId(context));
        }
        return null;
    }

    public boolean isDriveReady() {
        return driveService != null;
    }

    /** Thư mục M-Photo Mono còn tồn tại trên Drive (dùng khi server trả về {@code folderId}). */
    public boolean isDriveFolderStillThere(String folderId) {
        if (folderId == null || folderId.isEmpty() || driveService == null) {
            return false;
        }
        return isFolderExists(folderId);
    }

    /**
     * Tạo hoặc lấy thư mục {@code M-Photo Mono_{userId}} dưới MPhoto_Upload (local SP; đồng bộ server xem
     * {@link MonoDriveServerSync}).
     */
    public String getOrCreateMonoUserFolderId(Context ctx) {
        return getOrCreateMonoUserUploadFolderId(ctx);
    }

    /**
     * URL web mở thư mục trên trình duyệt: {@code https://drive.google.com/drive/folders/...}
     */
    public static String folderIdToWebLink(@Nullable String folderId) {
        if (folderId == null || folderId.isEmpty()) {
            return null;
        }
        return "https://drive.google.com/drive/folders/" + folderId;
    }

    /**
     * Thư mục con chứa ảnh upload (M-Photo Mono_userId) — đã lưu ID sau lần in/tải/đồng bộ đầu.
     */
    @Nullable
    public String getMonoUserFolderLink(Context ctx) {
        String userId = TokenManager.getInstance(ctx).getUserId();
        if (userId == null || userId.isEmpty()) {
            userId = "unknown";
        }
        SharedPreferences sp = ctx.getSharedPreferences("GoogleDrive", Context.MODE_PRIVATE);
        String id = sp.getString("MonoDriveUserFolderMphoto_" + userId, null);
        return folderIdToWebLink(id);
    }

    /**
     * Thư mục tổng trên Drive (MPhoto_Upload_Folder) — cấp cha của thư mục Mono.
     */
    @Nullable
    public String getUploadRootFolderLink(Context ctx) {
        String id = ctx.getSharedPreferences("GoogleDrive", Context.MODE_PRIVATE)
            .getString("UploadParentFolderId", null);
        return folderIdToWebLink(id);
    }



    /**
     * QR từ URL đầy đủ (cùng nội dung quét trên thiết bị / web).
     */
    @Nullable
    public Bitmap generateQRCodeForUrl(String fullUrl) {
        if (fullUrl == null || fullUrl.trim().isEmpty()) {
            return null;
        }
        try {
            String u = fullUrl.trim();
            QRCodeWriter qrCodeWriter = new QRCodeWriter();
            BitMatrix bitMatrix = qrCodeWriter.encode(u, BarcodeFormat.QR_CODE, 500, 500);

            int width = bitMatrix.getWidth();
            int height = bitMatrix.getHeight();
            Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565);

            for (int x = 0; x < width; x++) {
                for (int y = 0; y < height; y++) {
                    bitmap.setPixel(x, y, bitMatrix.get(x, y) ? Color.BLACK : Color.WHITE);
                }
            }

            Log.d(TAG, "QR code đã được tạo.");
            return bitmap;
        } catch (Exception e) {
            Log.e(TAG, "Lỗi khi tạo QR code: " + e.getMessage(), e);
            return null;
        }
    }

    public Bitmap generateQRCode(String folderId) {
        if (folderId == null || folderId.isEmpty()) {
            return null;
        }
        return generateQRCodeForUrl("https://drive.google.com/drive/folders/" + folderId);
    }



    public CompletableFuture<String> uploadFileToDrive(String filePath, String folderId) {
        return uploadFileToDrive(filePath, folderId, null);
    }

    /**
     * @param displayName tên file trên Drive; null thì dùng tên file gốc trên ổ đĩa
     */
    public CompletableFuture<String> uploadFileToDrive(String filePath, String folderId, String displayName) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            if (driveService == null) {
                Log.e(TAG, "uploadFileToDrive: Drive API chưa khởi tạo (kiểm tra res/raw/setting — service account JSON).");
                return CompletableFuture.completedFuture(null);
            }
            return CompletableFuture.supplyAsync(() -> {
                try {
                    File file = new File(filePath); // Chuyển đường dẫn thành File
                    if (!file.exists()) {
                        Log.e(TAG, "File không tồn tại: " + filePath);
                        return null;
                    }

                    Log.d(TAG, "folderid ảnh: " + folderId);
                    FileContent mediaContent = new FileContent("image/jpeg", file);

                    com.google.api.services.drive.model.File metadata = new com.google.api.services.drive.model.File();
                    metadata.setName((displayName != null && !displayName.isEmpty()) ? displayName : file.getName());
                    metadata.setMimeType("image/jpeg");
                    metadata.setParents(Collections.singletonList(folderId)); // Đưa vào thư mục con

                    com.google.api.services.drive.model.File uploadedFile = driveService.files()
                            .create(metadata, mediaContent)
                            .setFields("id")
                            .execute();

                    if (uploadedFile != null) {
                        String fileId = uploadedFile.getId();
                        // Thiết lập quyền xem công khai cho file
                        setPublicPermission(fileId);
                        return "https://drive.google.com/file/d/" + fileId + "/view";
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Lỗi upload ảnh: " + e.getMessage(), e);
                }
                return null;
            });
        }
        return null;
    }

    /**
     * Tạo hoặc lấy ID của folder cha NameCard từ SharedPreferences
     * Nếu không tồn tại, tự động tạo folder mới
     */
    private String getOrCreateParentFolder(Context context) {
        SharedPreferences sharedPreferences = context.getSharedPreferences("GoogleDrive", Context.MODE_PRIVATE);
        String parentFolderId = sharedPreferences.getString("NameCardParentFolderId", null);

        // Kiểm tra xem folder cha có tồn tại không
        if (parentFolderId != null && !parentFolderId.isEmpty()) {
            if (isFolderExists(parentFolderId)) {
                String folderLink = "https://drive.google.com/drive/folders/" + parentFolderId;
                Log.d(TAG, "Folder cha NameCard đã tồn tại: " + parentFolderId);
                Log.d(TAG, "🔗 Link folder: " + folderLink);
                Log.d(TAG, "📁 Tên folder: NameCard_Folder");
                return parentFolderId;
            } else {
                Log.w(TAG, "Folder cha đã bị xóa, tạo folder mới...");
            }
        }

        // Tạo folder cha mới nếu không tồn tại
        try {
            com.google.api.services.drive.model.File parentFolderMetadata = new com.google.api.services.drive.model.File();
            parentFolderMetadata.setName("NameCard_Folder");
            parentFolderMetadata.setMimeType("application/vnd.google-apps.folder");

            com.google.api.services.drive.model.File parentFolder = driveService.files()
                    .create(parentFolderMetadata)
                    .setFields("id")
                    .execute();

            if (parentFolder != null) {
                String newParentFolderId = parentFolder.getId();
                // Thiết lập quyền chỉnh sửa công khai cho folder cha
                setPublicEditorPermission(newParentFolderId);
                // Share folder với email chủ sở hữu
                shareFolderWithEmail(newParentFolderId, "noelhomehcm@gmail.com");
                sharedPreferences.edit().putString("NameCardParentFolderId", newParentFolderId).apply();
                MPhotoUserDataBackup.scheduleSave(context.getApplicationContext());
                String folderLink = "https://drive.google.com/drive/folders/" + newParentFolderId;
                Log.d(TAG, "Đã tạo folder cha NameCard mới: " + newParentFolderId);
                Log.d(TAG, "🔗 Link folder: " + folderLink);
                Log.d(TAG, "📁 Tên folder: NameCard_Folder");
                return newParentFolderId;
            }
        } catch (Exception e) {
            Log.e(TAG, "Lỗi tạo folder cha NameCard: " + e.getMessage(), e);
        }
        return null;
    }

    /**
     * Cùng thư mục với chụp in M-Photo Mono: dưới {@code MPhoto_Upload_Folder} → {@code M-Photo Mono_{userId}}.
     */
    public CompletableFuture<String> createOrGetSubFolder(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            return CompletableFuture.supplyAsync(() -> getOrCreateMonoUserUploadFolderId(context));
        }
        return null;
    }

    private String getOrCreateMonoUserUploadFolderId(Context ctx) {
        String userId = TokenManager.getInstance(ctx).getUserId();
        if (userId == null || userId.isEmpty()) {
            userId = "unknown";
        }
        String safe = userId.replaceAll("[^a-zA-Z0-9._-]", "_");
        String displayName = "M-Photo Mono_" + safe;
        SharedPreferences sp = ctx.getSharedPreferences("GoogleDrive", Context.MODE_PRIVATE);
        String cacheKey = "MonoDriveUserFolderMphoto_" + userId;
        String cached = sp.getString(cacheKey, null);
        if (cached != null && !cached.isEmpty() && isFolderExists(cached)) {
            Log.d(TAG, "Thư mục Mono user đã cache: " + displayName);
            return cached;
        }
        String uploadRoot = getOrCreateUploadParentFolder(ctx);
        if (uploadRoot == null) {
            Log.e(TAG, "Không có folder cha MPhoto_Upload");
            return null;
        }
        String existing = findChildFolderIdByName(uploadRoot, displayName);
        if (existing != null) {
            setPublicPermission(existing);
            sp.edit().putString(cacheKey, existing).apply();
            MPhotoUserDataBackup.scheduleSave(ctx.getApplicationContext());
            Log.d(TAG, "Đã tìm thư mục Mono: " + displayName);
            return existing;
        }
        try {
            com.google.api.services.drive.model.File folderMetadata = new com.google.api.services.drive.model.File();
            folderMetadata.setName(displayName);
            folderMetadata.setMimeType("application/vnd.google-apps.folder");
            folderMetadata.setParents(Collections.singletonList(uploadRoot));
            com.google.api.services.drive.model.File created = driveService.files()
                    .create(folderMetadata)
                    .setFields("id")
                    .execute();
            if (created != null) {
                String id = created.getId();
                setPublicPermission(id);
                sp.edit().putString(cacheKey, id).apply();
                MPhotoUserDataBackup.scheduleSave(ctx.getApplicationContext());
                Log.d(TAG, "Đã tạo thư mục Mono: " + displayName);
                return id;
            }
        } catch (Exception e) {
            Log.e(TAG, "Lỗi tạo thư mục Mono: " + e.getMessage(), e);
        }
        return null;
    }

    private String findChildFolderIdByName(String parentId, String name) {
        try {
            String q = "mimeType = 'application/vnd.google-apps.folder' and '"
                + parentId
                + "' in parents and trashed = false and name = '"
                + name.replace("'", "\\'")
                + "'";
            FileList r = driveService.files().list()
                    .setQ(q)
                    .setFields("files(id, name)")
                    .setPageSize(5)
                    .execute();
            if (r.getFiles() != null && !r.getFiles().isEmpty()) {
                return r.getFiles().get(0).getId();
            }
        } catch (Exception e) {
            Log.e(TAG, "findChildFolder: " + e.getMessage(), e);
        }
        return null;
    }

    /**
     * Kiểm tra xem thư mục có thể truy cập hay không, hoặc đã bị xóa vĩnh viễn.
     */
    private boolean isFolderExists(String folderId) {
        try {
            Drive.Files.Get request = driveService.files().get(folderId).setFields("id");
            com.google.api.services.drive.model.File file = request.execute();
            return file != null;
        } catch (Exception e) {
            Log.e(TAG, "Thư mục không tồn tại hoặc bị lỗi: " + e.getMessage(), e);
            return false;
        }
    }
    public CompletableFuture<List<String>> getImagesFromFolder(String folderId) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            return CompletableFuture.supplyAsync(() -> {
                try {
                    List<String> imageUrls = new ArrayList<>();

                    String query = String.format("'%s' in parents and mimeType contains 'image/' and trashed=false", folderId);
                    Drive.Files.List request = driveService.files().list()
                            .setQ(query)
                            .setFields("files(id, name, mimeType)");

                    List<com.google.api.services.drive.model.File> files = request.execute().getFiles();

                    if (files == null || files.isEmpty()) {
                        Log.d(TAG, "Không có ảnh nào trong thư mục.");
                        return Collections.emptyList();
                    }

                    for (com.google.api.services.drive.model.File file : files) {
                        String fileUrl = "https://drive.google.com/uc?id=" + file.getId();
                        imageUrls.add(fileUrl);
                        Log.d(TAG, "Tìm thấy ảnh: " + file.getName() + " - " + fileUrl);
                    }

                    return imageUrls;
                } catch (Exception e) {
                    Log.e(TAG, "Lỗi khi lấy danh sách ảnh từ thư mục: " + e.getMessage(), e);
                    return Collections.emptyList();
                }
            });
        }
        return null;
    }


}
