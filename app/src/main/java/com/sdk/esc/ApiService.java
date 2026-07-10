package com.sdk.esc;

import android.os.Build;
import android.util.Log;
import org.json.JSONArray;
import org.json.JSONObject;

import androidx.annotation.Nullable;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * API đăng nhập giống Mphoto-Android (POST /api/auth/login, GET /api/auth/validate).
 * Đổi {@link #BASE_URL} nếu server thay đổi.
 */
public class ApiService {
    private static final String TAG = "ApiService";
    public static final String BASE_URL = "https://mphoto.up.railway.app/api";

    public static JSONObject login(String email, String password) {
        try {
            URL url = new URL(BASE_URL + "/auth/login");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);
            conn.setConnectTimeout(30000);
            conn.setReadTimeout(30000);

            JSONObject jsonBody = new JSONObject();
            jsonBody.put("email", email);
            jsonBody.put("password", password);

            OutputStream os = conn.getOutputStream();
            os.write(jsonBody.toString().getBytes(StandardCharsets.UTF_8));
            os.close();

            int responseCode = conn.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) {
                    response.append(line);
                }
                br.close();
                return new JSONObject(response.toString());
            } else {
                BufferedReader br = new BufferedReader(
                    new InputStreamReader(conn.getErrorStream() != null
                        ? conn.getErrorStream() : conn.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) {
                    response.append(line);
                }
                br.close();
                Log.e(TAG, "Login failed: " + response);
                return null;
            }
        } catch (Exception e) {
            Log.e(TAG, "Login error", e);
            return null;
        }
    }

    /**
     * GET JSON array (mảng JSON gốc), dùng cho {@code /assets/mono/frames}, {@code /assets/mono/sub-photos}, …
     */
    public static JSONArray getJsonArrayAuthed(String pathAfterApi, String token) throws Exception {
        String path = pathAfterApi.startsWith("/") ? pathAfterApi : ("/" + pathAfterApi);
        URL url = new URL(BASE_URL + path);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Authorization", "Bearer " + token);
        conn.setRequestProperty("Accept", "application/json");
        conn.setConnectTimeout(30000);
        conn.setReadTimeout(30000);
        int code = conn.getResponseCode();
        java.io.InputStream in = (code >= 200 && code < 300) ? conn.getInputStream() : conn.getErrorStream();
        if (in == null) {
            throw new Exception("Lỗi mạng (HTTP " + code + ")");
        }
        StringBuilder body = new StringBuilder();
        try (java.io.BufferedReader br = new java.io.BufferedReader(
            new java.io.InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                body.append(line);
            }
        }
        conn.disconnect();
        if (code < 200 || code >= 300) {
            if (code == 401) {
                throw new Exception("Phiên đăng nhập hết hạn, vui lòng đăng nhập lại");
            }
            throw new Exception("API lỗi HTTP " + code + ": " + body);
        }
        String s = body.toString().trim();
        if (s.isEmpty() || s.equals("null")) {
            return new JSONArray();
        }
        return new JSONArray(s);
    }

    /**
     * GET JSON object (dùng cho {@code /software-update/...}, ví dụ kênh {@code mono} / {@code lite} / {@code pro}).
     */
    public static JSONObject getJsonObjectAuthed(String pathAfterApi, String token) throws Exception {
        String path = pathAfterApi.startsWith("/") ? pathAfterApi : ("/" + pathAfterApi);
        URL url = new URL(BASE_URL + path);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Authorization", "Bearer " + token);
        conn.setRequestProperty("Accept", "application/json");
        conn.setConnectTimeout(30000);
        conn.setReadTimeout(120000);
        int code = conn.getResponseCode();
        java.io.InputStream in = (code >= 200 && code < 300) ? conn.getInputStream() : conn.getErrorStream();
        if (in == null) {
            throw new Exception("Lỗi mạng (HTTP " + code + ")");
        }
        StringBuilder body = new StringBuilder();
        try (java.io.BufferedReader br = new java.io.BufferedReader(
                new java.io.InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                body.append(line);
            }
        }
        conn.disconnect();
        if (code < 200 || code >= 300) {
            if (code == 401) {
                throw new Exception("Phiên đăng nhập hết hạn, vui lòng đăng nhập lại");
            }
            throw new Exception("API lỗi HTTP " + code + ": " + body);
        }
        String s = body.toString().trim();
        if (s.isEmpty() || s.equals("null")) {
            return new JSONObject();
        }
        return new JSONObject(s);
    }

    /**
     * GET object; HTTP 404 trả về {@code null} (chưa cấu hình trên server).
     */
    @Nullable
    public static JSONObject getJsonObjectAuthedOrNullOn404(String pathAfterApi, String token) throws Exception {
        String path = pathAfterApi.startsWith("/") ? pathAfterApi : ("/" + pathAfterApi);
        URL url = new URL(BASE_URL + path);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Authorization", "Bearer " + token);
        conn.setRequestProperty("Accept", "application/json");
        conn.setConnectTimeout(30000);
        conn.setReadTimeout(30000);
        int code = conn.getResponseCode();
        if (code == HttpURLConnection.HTTP_NOT_FOUND) {
            conn.disconnect();
            return null;
        }
        java.io.InputStream in = (code >= 200 && code < 300) ? conn.getInputStream() : conn.getErrorStream();
        if (in == null) {
            conn.disconnect();
            if (code == 401) {
                throw new Exception("Phiên đăng nhập hết hạn, vui lòng đăng nhập lại");
            }
            throw new Exception("API lỗi HTTP " + code);
        }
        StringBuilder body = new StringBuilder();
        try (java.io.BufferedReader br = new java.io.BufferedReader(
                new java.io.InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                body.append(line);
            }
        }
        conn.disconnect();
        if (code < 200 || code >= 300) {
            if (code == 401) {
                throw new Exception("Phiên đăng nhập hết hạn, vui lòng đăng nhập lại");
            }
            throw new Exception("API lỗi HTTP " + code + ": " + body);
        }
        String s = body.toString().trim();
        if (s.isEmpty() || s.equals("null")) {
            return new JSONObject();
        }
        return new JSONObject(s);
    }

    /**
     * PUT JSON (Bearer), dùng lưu thông tin thư mục Drive theo tài khoản.
     */
    public static void putJsonObjectAuthed(String pathAfterApi, String token, JSONObject body) throws Exception {
        String path = pathAfterApi.startsWith("/") ? pathAfterApi : ("/" + pathAfterApi);
        URL url = new URL(BASE_URL + path);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("PUT");
        conn.setRequestProperty("Authorization", "Bearer " + token);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("Accept", "application/json");
        conn.setDoOutput(true);
        conn.setConnectTimeout(30000);
        conn.setReadTimeout(30000);
        if (body != null) {
            byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
            try (OutputStream os = conn.getOutputStream()) {
                os.write(bytes);
            }
        }
        int code = conn.getResponseCode();
        java.io.InputStream in = (code >= 200 && code < 300) ? conn.getInputStream() : conn.getErrorStream();
        if (in != null) {
            try (java.io.BufferedReader br = new java.io.BufferedReader(
                    new java.io.InputStreamReader(in, StandardCharsets.UTF_8))) {
                while (br.readLine() != null) {
                    // drain
                }
            }
        }
        conn.disconnect();
        if (code < 200 || code >= 300) {
            if (code == 401) {
                throw new Exception("Phiên đăng nhập hết hạn, vui lòng đăng nhập lại");
            }
            throw new Exception("API PUT lỗi HTTP " + code);
        }
    }

    /**
     * Tải file: giống mlite {@code DownloadFileAsync} — gọi {@link CdnHelper#rewriteToCdn}
     * (link đã là CDN {@code mphoto.mphotovn.online} thì giữ nguyên; link Firebase mới đổi).
     * GET trực tiếp, không gửi Bearer. Theo redirect. Không fail cứng theo Content-Length (CDN/proxy
     * chunked hay số lệch header — tin phần đọc hết stream + kiểm tra APK phía gọi).
     *
     * @return số byte đã ghi xuống file
     */
    public static long downloadToFile(String fileUrl, java.io.File dest) throws Exception {
        String u = CdnHelper.rewriteToCdn(fileUrl);
        java.net.HttpURLConnection c = (java.net.HttpURLConnection) new java.net.URL(u).openConnection();
        c.setRequestMethod("GET");
        c.setInstanceFollowRedirects(true);
        c.setRequestProperty("Accept", "*/*");
        c.setConnectTimeout(30000);
        c.setReadTimeout(300000);
        int code = c.getResponseCode();
        if (code < 200 || code >= 300) {
            c.disconnect();
            throw new Exception("Tải file thất bại, HTTP " + code);
        }
        long contentLenHint = -1L;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            contentLenHint = c.getContentLengthLong();
        } else {
            int cl = c.getContentLength();
            if (cl >= 0) {
                contentLenHint = cl;
            }
        }
        long written;
        try (java.io.InputStream in = c.getInputStream();
             java.io.FileOutputStream out = new java.io.FileOutputStream(dest)) {
            byte[] buf = new byte[8192];
            int n;
            long total = 0L;
            while ((n = in.read(buf)) >= 0) {
                if (n > 0) {
                    out.write(buf, 0, n);
                    total += n;
                }
            }
            out.flush();
            try {
                out.getFD().sync();
            } catch (Exception ignored) {
            }
            written = total;
        } finally {
            c.disconnect();
        }
        if (contentLenHint > 0L && written != contentLenHint) {
            Log.w(TAG, "downloadToFile: ghi " + written + " byte, header Content-Length=" + contentLenHint
                    + " (bỏ qua — nhiều CDN/proxy lệch); kiểm tra PK/APK ở SoftwareUpdateHelper.");
        }
        return written;
    }

    public static boolean validateToken(String token) {
        try {
            URL url = new URL(BASE_URL + "/auth/validate");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Authorization", "Bearer " + token);
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(15000);
            return conn.getResponseCode() == HttpURLConnection.HTTP_OK;
        } catch (Exception e) {
            Log.e(TAG, "Validate token error", e);
            return false;
        }
    }

    public static JSONObject uploadMonoPhotoWithName(String token, String folderName, File photoFile, String uploadFileName)
        throws Exception {
        if (token == null || token.isEmpty()) {
            throw new Exception("Thiếu token");
        }
        if (folderName == null || folderName.trim().isEmpty()) {
            throw new Exception("Thiếu folderName");
        }
        if (photoFile == null || !photoFile.exists()) {
            throw new Exception("Ảnh upload không tồn tại");
        }
        String boundary = "----MphotoMonoBoundary" + UUID.randomUUID();
        URL url = new URL(BASE_URL + "/mono-results/upload-with-name");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Authorization", "Bearer " + token);
        conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
        conn.setRequestProperty("Accept", "application/json");
        conn.setDoOutput(true);
        conn.setConnectTimeout(30000);
        conn.setReadTimeout(120000);

        try (DataOutputStream out = new DataOutputStream(conn.getOutputStream())) {
            writeFormField(out, boundary, "folderName", folderName);
            String fileName = (uploadFileName == null || uploadFileName.isEmpty()) ? "1.jpg" : uploadFileName;
            writeFilePart(out, boundary, "photos", fileName, "image/jpeg", photoFile);
            out.writeBytes("--" + boundary + "--\r\n");
            out.flush();
        }

        int code = conn.getResponseCode();
        java.io.InputStream in = (code >= 200 && code < 300) ? conn.getInputStream() : conn.getErrorStream();
        if (in == null) {
            conn.disconnect();
            throw new Exception("Upload Mono lỗi HTTP " + code);
        }
        StringBuilder body = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                body.append(line);
            }
        }
        conn.disconnect();
        if (code < 200 || code >= 300) {
            throw new Exception("Upload Mono lỗi HTTP " + code + ": " + body);
        }
        String s = body.toString().trim();
        return s.isEmpty() ? new JSONObject() : new JSONObject(s);
    }

    public static JSONArray getMonoAllGalleryIds(String token) throws Exception {
        return getJsonArrayAuthed("/mono-results/all-gallery-ids", token);
    }

    /** Kết quả {@code POST /mono-results/delete-galleries} — giống Pro {@code delete-galleries}. */
    public static final class MonoDeleteGalleriesResult {
        public final Set<String> deleted = new HashSet<>();
        public final Set<String> skipped = new HashSet<>();
        public final int failedCount;

        MonoDeleteGalleriesResult(Set<String> deleted, Set<String> skipped, int failedCount) {
            if (deleted != null) {
                this.deleted.addAll(deleted);
            }
            if (skipped != null) {
                this.skipped.addAll(skipped);
            }
            this.failedCount = failedCount;
        }
    }

    /**
     * Xóa nhiều gallery Mono trên server (Firebase + DB) — một request.
     * {@code POST /mono-results/delete-galleries} body {@code { galleryIds: string[] }}.
     */
    public static MonoDeleteGalleriesResult deleteMonoGalleries(String token, List<String> galleryIds)
        throws Exception {
        if (token == null || token.isEmpty()) {
            throw new Exception("Thiếu token");
        }
        if (galleryIds == null || galleryIds.isEmpty()) {
            return new MonoDeleteGalleriesResult(null, null, 0);
        }

        JSONArray idsJson = new JSONArray();
        for (String id : galleryIds) {
            if (id != null) {
                String t = id.trim();
                if (!t.isEmpty()) {
                    idsJson.put(t);
                }
            }
        }
        if (idsJson.length() == 0) {
            return new MonoDeleteGalleriesResult(null, null, 0);
        }

        JSONObject body = new JSONObject();
        body.put("galleryIds", idsJson);

        URL url = new URL(BASE_URL + "/mono-results/delete-galleries");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Authorization", "Bearer " + token);
        conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
        conn.setRequestProperty("Accept", "application/json");
        conn.setDoOutput(true);
        conn.setConnectTimeout(30000);
        conn.setReadTimeout(300000);

        byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
        try (OutputStream os = conn.getOutputStream()) {
            os.write(bytes);
        }

        int code = conn.getResponseCode();
        java.io.InputStream in = (code >= 200 && code < 300) ? conn.getInputStream() : conn.getErrorStream();
        StringBuilder sb = new StringBuilder();
        if (in != null) {
            try (java.io.BufferedReader br = new java.io.BufferedReader(
                    new java.io.InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line;
                while ((line = br.readLine()) != null) {
                    sb.append(line);
                }
            }
        }
        conn.disconnect();

        if (code < 200 || code >= 300) {
            throw new Exception("Xóa batch Mono lỗi HTTP " + code + ": " + sb);
        }

        Set<String> deleted = new HashSet<>();
        Set<String> skipped = new HashSet<>();
        int failedCount = 0;
        String raw = sb.toString().trim();
        if (!raw.isEmpty()) {
            JSONObject res = new JSONObject(raw);
            JSONArray deletedArr = res.optJSONArray("deleted");
            if (deletedArr != null) {
                for (int i = 0; i < deletedArr.length(); i++) {
                    String id = deletedArr.optString(i, "").trim();
                    if (!id.isEmpty()) {
                        deleted.add(id);
                    }
                }
            }
            JSONArray skippedArr = res.optJSONArray("skipped");
            if (skippedArr != null) {
                for (int i = 0; i < skippedArr.length(); i++) {
                    String id = skippedArr.optString(i, "").trim();
                    if (!id.isEmpty()) {
                        skipped.add(id);
                    }
                }
            }
            JSONArray failedArr = res.optJSONArray("failed");
            if (failedArr != null) {
                failedCount = failedArr.length();
            }
        }
        return new MonoDeleteGalleriesResult(deleted, skipped, failedCount);
    }

    /**
     * Xóa một gallery Mono — wrapper gọi batch (giữ tương thích chỗ gọi cũ).
     */
    public static boolean deleteMonoGallery(String token, String folderId) {
        if (token == null || token.isEmpty() || folderId == null || folderId.trim().isEmpty()) {
            return false;
        }
        try {
            List<String> one = new ArrayList<>();
            one.add(folderId.trim());
            MonoDeleteGalleriesResult r = deleteMonoGalleries(token, one);
            return r.deleted.contains(folderId.trim());
        } catch (Exception e) {
            Log.e(TAG, "deleteMonoGallery " + folderId, e);
            return false;
        }
    }

    private static void writeFormField(DataOutputStream out, String boundary, String name, String value) throws Exception {
        out.writeBytes("--" + boundary + "\r\n");
        out.writeBytes("Content-Disposition: form-data; name=\"" + name + "\"\r\n\r\n");
        out.write(value.getBytes(StandardCharsets.UTF_8));
        out.writeBytes("\r\n");
    }

    private static void writeFilePart(
        DataOutputStream out,
        String boundary,
        String fieldName,
        String fileName,
        String contentType,
        File file
    ) throws Exception {
        out.writeBytes("--" + boundary + "\r\n");
        out.writeBytes(
            "Content-Disposition: form-data; name=\"" + fieldName + "\"; filename=\"" + fileName + "\"\r\n"
        );
        out.writeBytes("Content-Type: " + contentType + "\r\n\r\n");
        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] buffer = new byte[8192];
            int n;
            while ((n = fis.read(buffer)) != -1) {
                out.write(buffer, 0, n);
            }
        }
        out.writeBytes("\r\n");
    }
}
