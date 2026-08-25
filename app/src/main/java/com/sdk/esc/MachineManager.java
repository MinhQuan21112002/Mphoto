package com.sdk.esc;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.Settings;
import android.util.Log;
import android.view.WindowManager;

import com.mphoto.mono.R;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * MachineManager - Quản lý Machine Code và Random Code
 * Tương tự MachineService + MachineStorageService trong mlite
 *
 * Lưu trữ:
 * - SharedPreferences (cache trong app — mất khi gỡ cài đặt)
 * - File JSON công khai trên máy (sống sót khi gỡ/cài lại), ví dụ:
 *   /sdcard/MPhotoConfig/machine-mono.json
 *   /sdcard/Documents/M-Photo/machine-mono.json
 *   /sdcard/Download/M-Photo/machine-mono.json
 *
 * Android 10+ cần quyền "All files access" (MANAGE_EXTERNAL_STORAGE) để ghi/đọc
 * file JSON ngoài MediaStore một cách ổn định.
 */
public class MachineManager {
    private static final String TAG = "MachineManager";
    private static final String PREF_NAME = "M_Photo_Machine";
    private static final String KEY_MACHINE_CODE = "machineCode";
    private static final String KEY_RANDOM_CODE = "randomCode";
    private static final String KEY_MACHINE_NAME = "machineName";
    private static final String KEY_LAST_UPDATED = "lastUpdated";
    private static final String KEY_PREVIOUS_RANDOM_CODE = "previousRandomCode";
    private static final String KEY_ASKED_ALL_FILES = "askedAllFilesAccess";
    private static final String KEY_PENDING_DURABLE_GRANT = "pendingDurableGrantReturn";

    /** Dialog chặn app khi chưa cấp All files access (không dismiss được). */
    private AlertDialog durableAccessBlockDialog;

    /** File riêng Mono — không dùng chung với Lite. */
    private static final String DURABLE_FILE_NAME = "machine-mono.json";
    private static final String LEGACY_FILE_NAME = "machine.json";
    private static final String DURABLE_DIR_LEGACY = "MPhotoConfig";
    private static final String DURABLE_DIR_APP = "M-Photo";
    
    private static MachineManager instance;
    private final Context context;
    private final SharedPreferences prefs;
    
    // Cache
    private String machineCode;
    private String randomCode;
    private String machineName;
    private long lastUpdated;
    private String previousRandomCode;
    
    // Listener cho thay đổi random code
    public interface RandomCodeChangeListener {
        void onRandomCodeChanged(String oldCode, String newCode);
    }
    private RandomCodeChangeListener changeListener;
    
    private MachineManager(Context context) {
        this.context = context.getApplicationContext();
        this.prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        loadFromStorage();

        // Nếu SharedPreferences chưa có đủ thông tin, thử load từ file JSON ngoài bộ nhớ
        if (machineCode == null || machineCode.isEmpty()
                || randomCode == null || randomCode.isEmpty()) {
            loadFromJsonFile();
        }
    }
    
    public static synchronized MachineManager getInstance(Context context) {
        if (instance == null) {
            instance = new MachineManager(context);
        }
        return instance;
    }
    
    /**
     * Load thông tin từ SharedPreferences
     */
    private void loadFromStorage() {
        machineCode = prefs.getString(KEY_MACHINE_CODE, null);
        randomCode = prefs.getString(KEY_RANDOM_CODE, null);
        machineName = prefs.getString(KEY_MACHINE_NAME, "");
        lastUpdated = prefs.getLong(KEY_LAST_UPDATED, 0);
        previousRandomCode = prefs.getString(KEY_PREVIOUS_RANDOM_CODE, null);
        
        Log.d(TAG, "Loaded from storage - MachineCode: " + machineCode + ", RandomCode: " + randomCode);
    }
    
    /**
     * Lưu thông tin vào SharedPreferences và file JSON ngoài
     */
    private void saveToStorage() {
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(KEY_MACHINE_CODE, machineCode);
        editor.putString(KEY_RANDOM_CODE, randomCode);
        editor.putString(KEY_MACHINE_NAME, machineName);
        editor.putLong(KEY_LAST_UPDATED, lastUpdated);
        editor.putString(KEY_PREVIOUS_RANDOM_CODE, previousRandomCode);
        editor.apply();
        
        Log.d(TAG, "Saved to storage - MachineCode: " + machineCode + ", RandomCode: " + randomCode);

        // Ghi thêm ra file JSON ngoài bộ nhớ để không bị mất khi gỡ app
        saveToJsonFile();
    }

    /**
     * Các vị trí file JSON bền trên máy (sống sót khi gỡ app).
     * Ghi vào mọi path viết được; đọc path đầu tiên có dữ liệu hợp lệ.
     */
    private List<File> getDurableCandidateFiles(boolean includeLegacyShared) {
        Set<File> files = new LinkedHashSet<>();
        try {
            File root = Environment.getExternalStorageDirectory();
            if (root != null) {
                files.add(new File(new File(root, DURABLE_DIR_LEGACY), DURABLE_FILE_NAME));
                if (includeLegacyShared) {
                    files.add(new File(new File(root, DURABLE_DIR_LEGACY), LEGACY_FILE_NAME));
                }
            }
            File documents = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS);
            if (documents != null) {
                files.add(new File(new File(documents, DURABLE_DIR_APP), DURABLE_FILE_NAME));
            }
            File downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            if (downloads != null) {
                files.add(new File(new File(downloads, DURABLE_DIR_APP), DURABLE_FILE_NAME));
            }
            File pictures = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
            if (pictures != null) {
                files.add(new File(new File(pictures, DURABLE_DIR_APP), DURABLE_FILE_NAME));
            }
        } catch (Exception e) {
            Log.e(TAG, "Error building durable candidate paths", e);
        }
        return new ArrayList<>(files);
    }

    /** True nếu Android 11+ đã cấp All files access (cần để ghi JSON bền). */
    public static boolean hasDurableStorageAccess() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return Environment.isExternalStorageManager();
        }
        return true;
    }

    /**
     * Bắt buộc All files access — chưa bật thì chặn app (dialog không tắt được).
     * @return true nếu đã có quyền / không cần (Android &lt; 11); false nếu đang chặn.
     */
    public boolean enforceDurableStorageAccessRequired(Activity activity) {
        if (activity == null || activity.isFinishing()) {
            return hasDurableStorageAccess();
        }
        if (hasDurableStorageAccess()) {
            dismissDurableAccessBlockDialog();
            if (machineCode != null && !machineCode.isEmpty()) {
                saveToJsonFile();
            } else {
                loadFromJsonFile();
            }
            // Vừa cấp quyền xong → đóng app để user mở lại sạch (init machine/socket/camera).
            if (consumePendingDurableGrantReturn()) {
                closeAppForFreshStart(activity);
                return false;
            }
            return true;
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return true;
        }
        showDurableAccessBlockDialog(activity);
        return false;
    }

    /**
     * Xin quyền All files access — mở Settings. Dùng từ nút trong dialog chặn.
     */
    public void requestDurableStorageAccessIfNeeded(Activity activity) {
        if (activity == null) return;
        if (hasDurableStorageAccess()) {
            if (machineCode != null && !machineCode.isEmpty()) {
                saveToJsonFile();
            } else {
                loadFromJsonFile();
            }
            return;
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return;
        }
        openAllFilesAccessSettings(activity);
    }

    private void openAllFilesAccessSettings(Activity activity) {
        boolean asked = prefs.getBoolean(KEY_ASKED_ALL_FILES, false);
        try {
            prefs.edit()
                    .putBoolean(KEY_ASKED_ALL_FILES, true)
                    .putBoolean(KEY_PENDING_DURABLE_GRANT, true)
                    .apply();
            Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
            intent.setData(Uri.parse("package:" + activity.getPackageName()));
            activity.startActivity(intent);
            Log.d(TAG, "Opened All files access settings (askedBefore=" + asked + ")");
        } catch (Exception e) {
            try {
                Intent intent = new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                activity.startActivity(intent);
            } catch (Exception e2) {
                Log.e(TAG, "Cannot open All files access settings", e2);
            }
        }
    }

    private void showDurableAccessBlockDialog(Activity activity) {
        try {
            if (durableAccessBlockDialog != null) {
                if (durableAccessBlockDialog.isShowing()) {
                    // Cùng activity đang hiện → giữ nguyên
                    if (durableAccessBlockDialog.getOwnerActivity() == activity) {
                        return;
                    }
                }
                dismissDurableAccessBlockDialog();
            }
            AlertDialog dialog = new AlertDialog.Builder(activity)
                    .setTitle(R.string.durable_storage_required_title)
                    .setMessage(R.string.durable_storage_required_message)
                    .setCancelable(false)
                    .setPositiveButton(R.string.durable_storage_required_open_settings, null)
                    .setNegativeButton(R.string.durable_storage_required_exit, null)
                    .create();
            dialog.setCanceledOnTouchOutside(false);
            dialog.setOwnerActivity(activity);
            dialog.setOnShowListener(d -> {
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v ->
                        openAllFilesAccessSettings(activity));
                dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener(v -> {
                    try {
                        activity.finishAffinity();
                    } catch (Exception ignored) {
                        activity.finish();
                    }
                });
            });
            dialog.show();
            try {
                if (dialog.getWindow() != null) {
                    dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
                }
            } catch (Exception ignored) { }
            durableAccessBlockDialog = dialog;
        } catch (Exception e) {
            Log.e(TAG, "Cannot show durable access block dialog", e);
            // Fallback: vẫn mở Settings
            openAllFilesAccessSettings(activity);
        }
    }

    private void dismissDurableAccessBlockDialog() {
        try {
            if (durableAccessBlockDialog != null && durableAccessBlockDialog.isShowing()) {
                durableAccessBlockDialog.dismiss();
            }
        } catch (Exception ignored) { }
        durableAccessBlockDialog = null;
    }

    private boolean consumePendingDurableGrantReturn() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return false;
        }
        boolean pending = prefs.getBoolean(KEY_PENDING_DURABLE_GRANT, false);
        if (!pending) {
            return false;
        }
        prefs.edit().putBoolean(KEY_PENDING_DURABLE_GRANT, false).apply();
        return true;
    }

    private void closeAppForFreshStart(Activity activity) {
        if (activity == null || activity.isFinishing()) {
            return;
        }
        Log.d(TAG, "All files access granted — closing app for fresh restart");
        try {
            activity.finishAffinity();
        } catch (Exception e) {
            activity.finish();
        }
    }

    /**
     * Gọi lại sau khi user quay từ Settings — load file bền nếu prefs trống, hoặc ghi lại file.
     */
    public void reloadDurableStorageAfterPermission() {
        if (!hasDurableStorageAccess()) {
            return;
        }
        // Ưu tiên đọc file bền trước khi ghi — quan trọng sau gỡ/cài lại
        if (machineCode == null || machineCode.isEmpty()) {
            loadFromJsonFile();
        }
        if (machineCode != null && !machineCode.isEmpty()) {
            saveToJsonFile();
        }
    }

    /**
     * Load thông tin machine từ file JSON ngoài bộ nhớ (nếu có)
     * Dùng cho trường hợp cài lại app nhưng vẫn muốn giữ lại machineCode/randomCode cũ
     */
    private void loadFromJsonFile() {
        try {
            for (File file : getDurableCandidateFiles(true)) {
                if (file == null || !file.exists() || !file.canRead()) {
                    continue;
                }
                if (applyMachineJsonFromFile(file)) {
                    Log.d(TAG, "Loaded machine info from JSON: " + file.getAbsolutePath());
                    return;
                }
            }
            Log.d(TAG, "No durable machine.json found on device");
        } catch (Exception e) {
            Log.e(TAG, "Error loading machine info from JSON file", e);
        }
    }

    private boolean applyMachineJsonFromFile(File file) {
        try {
            FileInputStream fis = new FileInputStream(file);
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buffer = new byte[1024];
            int len;
            while ((len = fis.read(buffer)) > 0) {
                bos.write(buffer, 0, len);
            }
            fis.close();

            String jsonStr = bos.toString(StandardCharsets.UTF_8.name());
            JSONObject obj = new JSONObject(jsonStr);

            String fileMachineCode = obj.optString("machineCode", null);
            if (fileMachineCode == null || fileMachineCode.trim().isEmpty()) {
                return false;
            }
            fileMachineCode = fileMachineCode.trim().toUpperCase();

            String fileRandomCode = obj.optString("randomCode", null);
            if (fileRandomCode != null) {
                fileRandomCode = fileRandomCode.trim();
                if (fileRandomCode.isEmpty()) {
                    fileRandomCode = null;
                }
            }
            String fileMachineName = obj.optString("machineName", "");
            long fileLastUpdated = obj.optLong("lastUpdated", System.currentTimeMillis());

            // Chỉ override nếu SharedPreferences đang trống
            if (machineCode == null || machineCode.isEmpty()) {
                machineCode = fileMachineCode;
            }
            if (randomCode == null || randomCode.isEmpty()) {
                randomCode = fileRandomCode;
            }
            if (machineName == null || machineName.isEmpty()) {
                machineName = fileMachineName;
            }
            if (lastUpdated == 0) {
                lastUpdated = fileLastUpdated;
            }

            // Đồng bộ vào prefs + ghi lại các path mới (migrate legacy)
            if (machineCode != null && !machineCode.isEmpty()) {
                SharedPreferences.Editor editor = prefs.edit();
                editor.putString(KEY_MACHINE_CODE, machineCode);
                if (randomCode != null) {
                    editor.putString(KEY_RANDOM_CODE, randomCode);
                }
                editor.putString(KEY_MACHINE_NAME, machineName != null ? machineName : "");
                editor.putLong(KEY_LAST_UPDATED, lastUpdated);
                editor.apply();
                saveToJsonFile();
                return true;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error reading " + file.getAbsolutePath(), e);
        }
        return false;
    }

    /**
     * Lưu thông tin machine ra file JSON ngoài bộ nhớ
     * Để nếu app bị gỡ cài đặt, thông tin vẫn còn trên máy
     */
    private void saveToJsonFile() {
        try {
            if (machineCode == null || machineCode.isEmpty()) {
                return;
            }

            JSONObject obj = new JSONObject();
            obj.put("machineCode", machineCode);
            obj.put("randomCode", randomCode != null ? randomCode : "");
            obj.put("machineName", machineName != null ? machineName : "");
            obj.put("lastUpdated", System.currentTimeMillis());
            obj.put("product", "mono");
            byte[] bytes = obj.toString().getBytes(StandardCharsets.UTF_8);

            int saved = 0;
            for (File file : getDurableCandidateFiles(false)) {
                if (file == null) continue;
                try {
                    File parent = file.getParentFile();
                    if (parent != null && !parent.exists()) {
                        //noinspection ResultOfMethodCallIgnored
                        parent.mkdirs();
                    }
                    FileOutputStream fos = new FileOutputStream(file);
                    fos.write(bytes);
                    fos.flush();
                    fos.close();
                    saved++;
                    Log.d(TAG, "Saved machine info to JSON: " + file.getAbsolutePath());
                } catch (Exception e) {
                    Log.w(TAG, "Cannot write " + file.getAbsolutePath() + ": " + e.getMessage());
                }
            }
            if (saved == 0) {
                Log.e(TAG, "Failed to save durable machine.json anywhere — "
                        + "grant All files access or machine code will reset after reinstall");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error saving machine info to JSON file", e);
        }
    }
    
    /**
     * Tạo Machine Code 6 ký tự ngẫu nhiên (A-Z, 0-9)
     */
    public static String generateMachineCode() {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        Random random = new Random();
        StringBuilder code = new StringBuilder();

        for (int i = 0; i < 6; i++) {
            code.append(chars.charAt(random.nextInt(chars.length())));
        }

        return code.toString();
    }

    /**
     * Đảm bảo có machineCode local (prefs / JSON) trước khi tạo folderId gallery.
     * Không gọi API — chỉ cần 6 ký tự cố định của máy.
     */
    public String ensureLocalMachineCode() {
        if (machineCode != null && !machineCode.isEmpty()) {
            return machineCode;
        }
        loadFromStorage();
        if (machineCode != null && !machineCode.isEmpty()) {
            return machineCode;
        }
        loadFromJsonFile();
        if (machineCode != null && !machineCode.isEmpty()) {
            return machineCode;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !hasDurableStorageAccess()) {
            Log.w(TAG, "ensureLocalMachineCode: no All files access yet — skip generate");
            return null;
        }
        machineCode = generateMachineCode();
        lastUpdated = System.currentTimeMillis();
        saveToStorage();
        return machineCode;
    }
    
    /**
     * Kiểm tra và cập nhật Machine (phương thức chính)
     * Tương tự CheckAndUpdateMachineAsync() trong mlite
     * 
     * Luồng:
     * 1. Nếu chưa có machineCode → tạo mới
     * 2. Nếu chưa có randomCode → gọi API tạo machine trên server
     * 3. Nếu đã có → gọi API kiểm tra randomCode có thay đổi không
     * 
     * @param token Auth token
     * @return true nếu thành công, false nếu thất bại
     */
    public boolean checkAndUpdateMachine(String token) {
        try {
            Log.d(TAG, "=== CHECK AND UPDATE MACHINE ===");
            
            // BƯỚC 0: Thử đọc lại file bền trên máy (sau gỡ/cài lại hoặc vừa cấp All files access)
            if (machineCode == null || machineCode.isEmpty()) {
                loadFromJsonFile();
            }

            // BƯỚC 1: Kiểm tra hoặc tạo Machine Code
            // ❗️MachineCode là của MÁY, không phụ thuộc tài khoản
            // Chỉ tạo mới nếu local (SharedPreferences + JSON) hoàn toàn chưa có
            if (machineCode == null || machineCode.isEmpty()) {
                // Chưa có quyền đọc file bền → đừng tạo mã mới (tránh ghi đè mã cũ sau khi cấp quyền)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !hasDurableStorageAccess()) {
                    Log.w(TAG, "Defer machineCode generation until All files access is granted");
                    return false;
                }
                machineCode = generateMachineCode();
                lastUpdated = System.currentTimeMillis();
                saveToStorage();
                Log.d(TAG, "Generated new machineCode (local-only): " + machineCode);
            } else {
                // Đảm bảo đã ghi ra file bền (kể cả mã cũ từ prefs)
                saveToJsonFile();
            }
            
            // BƯỚC 2: Nếu chưa có randomCode → kiểm tra server hoặc tạo mới
            if (randomCode == null || randomCode.isEmpty()) {
                Log.d(TAG, "No randomCode locally, checking server...");
                
                // Trước tiên, kiểm tra xem machineCode đã tồn tại trên server chưa
                JSONObject existingMachine = ApiService.getMachine(token, machineCode);
                
                if (existingMachine != null && existingMachine.has("machine")) {
                    // ✅ Đã tồn tại trên server → Lấy randomCode từ server
                    JSONObject machine = existingMachine.getJSONObject("machine");
                    randomCode = machine.getString("randomCode");
                    machineName = machine.optString("machineName", "");
                    lastUpdated = System.currentTimeMillis();
                    saveToStorage();
                    
                    Log.d(TAG, "✅ Found existing machine on server!");
                    Log.d(TAG, "   MachineCode: " + machineCode);
                    Log.d(TAG, "   RandomCode: " + randomCode);
                    return true;
                }
                
                // Chưa tồn tại trên server → Tạo mới
                Log.d(TAG, "Machine not found on server, creating new...");
                
                int maxRetries = 3;
                boolean created = false;
                
                for (int i = 0; i < maxRetries; i++) {
                    JSONObject createResponse = ApiService.createMachine(token, machineCode);
                    
                    if (createResponse != null && createResponse.has("machine")) {
                        JSONObject machine = createResponse.getJSONObject("machine");
                        randomCode = machine.getString("randomCode");
                        machineName = machine.optString("machineName", "");
                        lastUpdated = System.currentTimeMillis();
                        saveToStorage();
                        
                        Log.d(TAG, "✅ Machine created successfully!");
                        Log.d(TAG, "   MachineCode: " + machineCode);
                        Log.d(TAG, "   RandomCode: " + randomCode);
                        created = true;
                        break;
                    } else {
                        Log.e(TAG, "Create failed, try " + (i+1) + "/" + maxRetries);
                        // Chờ một chút trước khi retry
                        try { Thread.sleep(1000); } catch (Exception e) {}
                    }
                }
                
                if (!created) {
                    Log.e(TAG, "❌ Failed to create machine after " + maxRetries + " retries");
                    return false;
                }
                
                return true;
            }
            
            // BƯỚC 3: Link machine với user đang đăng nhập (đổi tài khoản A→B)
            boolean linked = false;
            try {
                TokenManager tokenManager = TokenManager.getInstance(context);
                String currentUserId = tokenManager != null ? tokenManager.getUserId() : null;
                
                if (currentUserId != null
                        && machineCode != null && !machineCode.isEmpty()
                        && randomCode != null && !randomCode.isEmpty()) {
                    linked = ApiService.linkUserToMachine(machineCode, randomCode, currentUserId);
                    Log.d(TAG, "Link user to machine result: " + linked
                            + " (userId=" + currentUserId
                            + ", machineCode=" + machineCode
                            + ", randomCode=" + randomCode + ")");

                    // Reclaim: mã local lệch server → đẩy mã local lên rồi link lại
                    // (update-random-code chỉ cần JWT, không check owner)
                    if (!linked) {
                        Log.d(TAG, "Reclaim: push local randomCode then re-link: " + randomCode);
                        boolean pushed = ApiService.updateMachineRandomCode(
                                token, machineCode, randomCode);
                        if (pushed) {
                            linked = ApiService.linkUserToMachine(
                                    machineCode, randomCode, currentUserId);
                            Log.d(TAG, "Reclaim link after push result: " + linked);
                        } else {
                            Log.w(TAG, "Reclaim: failed to push randomCode to server");
                        }
                    }
                } else {
                    Log.d(TAG, "Skip linkUserToMachine: missing currentUserId or machine info");
                }
            } catch (Exception ex) {
                Log.e(TAG, "Error while linking user to machine", ex);
            }
            
            // BƯỚC 4: Đồng bộ từ server (sau khi đã link — tránh 403 khi máy còn thuộc user cũ)
            Log.d(TAG, "Checking server for updates...");
            JSONObject serverResponse = ApiService.getMachine(token, machineCode);
            
            if (serverResponse != null && serverResponse.has("machine")) {
                JSONObject serverMachine = serverResponse.getJSONObject("machine");
                String serverRandomCode = serverMachine.getString("randomCode");
                
                // So sánh randomCode
                if (!randomCode.equals(serverRandomCode)) {
                    Log.d(TAG, "⚠️ RandomCode changed!");
                    Log.d(TAG, "   Old: " + randomCode);
                    Log.d(TAG, "   New: " + serverRandomCode);
                    
                    updateRandomCode(serverRandomCode);
                    
                    // Notify listener
                    if (changeListener != null) {
                        changeListener.onRandomCodeChanged(previousRandomCode, randomCode);
                    }
                } else {
                    Log.d(TAG, "✅ RandomCode unchanged: " + randomCode);
                }
                
                return true;
            } else {
                // 404 thật → tạo lại. 403 (máy thuộc user khác + link fail) → giữ mã local
                int status = ApiService.getLastGetMachineHttpStatus();
                if (status == 404) {
                    Log.w(TAG, "Machine not found on server (404), creating new one...");
                    randomCode = null;
                    saveToStorage();
                    return checkAndUpdateMachine(token);
                }
                Log.w(TAG, "Machine not accessible on server (HTTP " + status
                        + "), keeping local codes. linked=" + linked);
                return linked || hasMachineInfo();
            }
            
        } catch (Exception e) {
            Log.e(TAG, "❌ Error in checkAndUpdateMachine: " + e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * Kiểm tra xem đã có đầy đủ thông tin machine chưa
     */
    public boolean hasMachineInfo() {
        return machineCode != null && !machineCode.isEmpty()
            && randomCode != null && !randomCode.isEmpty();
    }
    
    /**
     * Kiểm tra xem randomCode có thay đổi không
     */
    public boolean hasRandomCodeChanged() {
        return previousRandomCode != null && !previousRandomCode.isEmpty()
            && !previousRandomCode.equals(randomCode);
    }
    
    // Getters
    public String getMachineCode() { return machineCode; }
    public String getRandomCode() { return randomCode; }
    public String getMachineName() { return machineName; }
    public long getLastUpdated() { return lastUpdated; }
    public String getPreviousRandomCode() { return previousRandomCode; }
    
    /**
     * Lấy mã hiển thị đầy đủ: MACHINE_CODE + RANDOM_CODE
     * VD: "ABC123 + XY1"
     */
    public String getFullCode() {
        if (machineCode == null || randomCode == null) {
            return "Not registered";
        }
        return machineCode + " + " + randomCode;
    }
    
    /**
     * Lấy Machine ID để dùng cho QR code (machineCode)
     */
    public String getMachineId() {
        return machineCode;
    }
    
    // Setters
    public void setMachineName(String name) {
        this.machineName = name;
        saveToStorage();
    }

    /**
     * Cập nhật randomCode local (prefs + machine.json).
     */
    public void updateRandomCode(String newCode) {
        if (newCode == null || newCode.isEmpty()) {
            return;
        }
        String normalized = newCode.trim().toUpperCase();
        if (normalized.equals(randomCode)) {
            return;
        }
        previousRandomCode = randomCode;
        randomCode = normalized;
        lastUpdated = System.currentTimeMillis();
        saveToStorage();
        Log.d(TAG, "✅ RandomCode updated locally: " + previousRandomCode + " -> " + randomCode);
    }
    
    public void setChangeListener(RandomCodeChangeListener listener) {
        this.changeListener = listener;
    }
    
    /**
     * Xóa toàn bộ thông tin machine (reset có chủ đích)
     */
    public void clearMachineInfo() {
        machineCode = null;
        randomCode = null;
        machineName = "";
        lastUpdated = 0;
        previousRandomCode = null;
        
        prefs.edit().clear().apply();
        Log.d(TAG, "Machine info cleared");

        // Xóa mọi file JSON bền (kể cả legacy)
        try {
            for (File file : getDurableCandidateFiles(true)) {
                if (file != null && file.exists()) {
                    boolean deleted = file.delete();
                    Log.d(TAG, "External machine file deleted (" + deleted + "): " + file.getAbsolutePath());
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error deleting external machine.json", e);
        }
    }
}

