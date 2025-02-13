package com.sdk.esc;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Build;
import android.util.Log;

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

    public GoogleDriveService(Context context) {
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

    @SuppressLint("SimpleDateFormat")
    public CompletableFuture<String> createSubFolder() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            return CompletableFuture.supplyAsync(() -> {
                try {
                    com.google.api.services.drive.model.File folderMetadata = new com.google.api.services.drive.model.File();
                    folderMetadata.setName(new SimpleDateFormat("yyyyMMdd").format(new Date())); // Đặt tên theo ngày tháng
                    folderMetadata.setMimeType("application/vnd.google-apps.folder");
                    folderMetadata.setParents(Collections.singletonList("1AtOmGJTgFt4u1e5_wlbvgvSMv96oWQno"));

                    com.google.api.services.drive.model.File folder = driveService.files()
                            .create(folderMetadata)
                            .setFields("id")
                            .execute();

                    return folder.getId(); // Trả về ID thư mục con
                } catch (Exception e) {
                    Log.e(TAG, "Lỗi tạo thư mục con: " + e.getMessage(), e);
                    return null;
                }
            });
        }
        return null;
    }



    public Bitmap generateQRCode(String folderId) {
        try {
            // Tạo link Google Drive của thư mục cha
            String driveFolderLink = "https://drive.google.com/drive/folders/" + folderId;

            // Tạo QR code từ link này
            QRCodeWriter qrCodeWriter = new QRCodeWriter();
            BitMatrix bitMatrix = qrCodeWriter.encode(driveFolderLink, BarcodeFormat.QR_CODE, 500, 500);

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



    public CompletableFuture<String> uploadFileToDrive(String filePath, String folderId) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
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
                    metadata.setName(file.getName());
                    metadata.setMimeType("image/jpeg");
                    metadata.setParents(Collections.singletonList(folderId)); // Đưa vào thư mục con

                    com.google.api.services.drive.model.File uploadedFile = driveService.files()
                            .create(metadata, mediaContent)
                            .setFields("id")
                            .execute();

                    if (uploadedFile != null) {
                        return "https://drive.google.com/file/d/" + uploadedFile.getId() + "/view";
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Lỗi upload ảnh: " + e.getMessage(), e);
                }
                return null;
            });
        }
        return null;
    }

    public CompletableFuture<String> createOrGetSubFolder(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            return CompletableFuture.supplyAsync(() -> {
                SharedPreferences sharedPreferences = context.getSharedPreferences("GoogleDrive", Context.MODE_PRIVATE);
                String savedFolderId = sharedPreferences.getString("NameCardFolderId", null);

                if (savedFolderId != null && !savedFolderId.isEmpty()) {
                    // Kiểm tra xem thư mục có tồn tại trên Google Drive không
                    if (isFolderExists(savedFolderId)) {
                        Log.d(TAG, "Thư mục đã tồn tại: " + savedFolderId);
                        return savedFolderId; // Nếu tồn tại, trả về luôn
                    } else {
                        Log.w(TAG, "Thư mục đã bị xóa, tạo thư mục mới...");
                    }
                }

                try {
                    String folderName = "NameCard_" + new SimpleDateFormat("yyyyMMdd").format(new Date());
                    com.google.api.services.drive.model.File folderMetadata = new com.google.api.services.drive.model.File();
                    folderMetadata.setName(folderName);
                    folderMetadata.setMimeType("application/vnd.google-apps.folder");
                    folderMetadata.setParents(Collections.singletonList("1wo6sRrMqJoA9g7o7yiJHcQgM3FrIyN7R")); // ID thư mục cha

                    com.google.api.services.drive.model.File folder = driveService.files()
                            .create(folderMetadata)
                            .setFields("id")
                            .execute();

                    if (folder != null) {
                        String folderId = folder.getId();
                        sharedPreferences.edit().putString("NameCardFolderId", folderId).apply(); // Lưu vào SharedPreferences
                        Log.d(TAG, "Tạo thư mục mới: " + folderId);
                        return folderId;
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Lỗi tạo thư mục NameCard: " + e.getMessage(), e);
                }
                return null;
            });
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
