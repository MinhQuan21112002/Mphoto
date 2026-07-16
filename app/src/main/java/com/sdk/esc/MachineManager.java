package com.sdk.esc;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Environment;
import android.util.Log;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Random;

/**
 * MachineManager - Quản lý Machine Code và Random Code
 * Tương tự MachineService + MachineStorageService trong mlite
 * 
 * Lưu trữ:
 * - machineCode: 6 ký tự (do app tạo, dùng để định danh máy)
 * - randomCode: 3 ký tự (do server tạo, dùng để xác thực)
 * - machineName: Tên máy (optional)
 * - lastUpdated: Thời gian cập nhật cuối
 * - previousRandomCode: Mã random cũ (để so sánh thay đổi)
 */
public class MachineManager {
    private static final String TAG = "MachineManager";
    private static final String PREF_NAME = "M_Photo_Machine";
    private static final String KEY_MACHINE_CODE = "machineCode";
    private static final String KEY_RANDOM_CODE = "randomCode";
    private static final String KEY_MACHINE_NAME = "machineName";
    private static final String KEY_LAST_UPDATED = "lastUpdated";
    private static final String KEY_PREVIOUS_RANDOM_CODE = "previousRandomCode";
    
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
     * Lấy file JSON lưu thông tin machine ngoài bộ nhớ
     * Ví dụ: /sdcard/MPhotoConfig/machine.json
     */
    private File getMachineJsonFile() {
        try {
            File root = Environment.getExternalStorageDirectory();
            File dir = new File(root, "MPhotoConfig");
            if (!dir.exists()) {
                // mkdirs() có thể trả false nếu không tạo được nhưng không cần crash app
                dir.mkdirs();
            }
            return new File(dir, "machine.json");
        } catch (Exception e) {
            Log.e(TAG, "Error getting machine.json file path", e);
            return null;
        }
    }

    /**
     * Load thông tin machine từ file JSON ngoài bộ nhớ (nếu có)
     * Dùng cho trường hợp cài lại app nhưng vẫn muốn giữ lại machineCode/randomCode cũ
     */
    private void loadFromJsonFile() {
        try {
            File file = getMachineJsonFile();
            if (file == null || !file.exists()) {
                return;
            }

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
            String fileRandomCode = obj.optString("randomCode", null);
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

            // Lưu lại vào SharedPreferences để dùng thống nhất trong app
            if (machineCode != null && !machineCode.isEmpty()
                    && randomCode != null && !randomCode.isEmpty()) {
                saveToStorage();
                Log.d(TAG, "Loaded machine info from JSON file");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error loading machine info from JSON file", e);
        }
    }

    /**
     * Lưu thông tin machine ra file JSON ngoài bộ nhớ
     * Để nếu app bị gỡ cài đặt, thông tin vẫn còn trên máy
     */
    private void saveToJsonFile() {
        try {
            if (machineCode == null || machineCode.isEmpty()
                    || randomCode == null || randomCode.isEmpty()) {
                return;
            }

            File file = getMachineJsonFile();
            if (file == null) {
                return;
            }

            JSONObject obj = new JSONObject();
            obj.put("machineCode", machineCode);
            obj.put("randomCode", randomCode);
            obj.put("machineName", machineName != null ? machineName : "");
            obj.put("lastUpdated", System.currentTimeMillis());

            FileOutputStream fos = new FileOutputStream(file);
            fos.write(obj.toString().getBytes(StandardCharsets.UTF_8));
            fos.flush();
            fos.close();

            Log.d(TAG, "Saved machine info to JSON file: " + file.getAbsolutePath());
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
            
            // BƯỚC 1: Kiểm tra hoặc tạo Machine Code
            // ❗️MachineCode là của MÁY, không phụ thuộc tài khoản
            // Chỉ tạo mới nếu local (SharedPreferences + JSON) hoàn toàn chưa có
            if (machineCode == null || machineCode.isEmpty()) {
                machineCode = generateMachineCode();
                lastUpdated = System.currentTimeMillis();
                saveToStorage();
                Log.d(TAG, "Generated new machineCode (local-only): " + machineCode);
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
            
            // BƯỚC 3: Đã có thông tin local → đảm bảo machine đang linked với user hiện tại
            try {
                TokenManager tokenManager = TokenManager.getInstance(context);
                String currentUserId = tokenManager != null ? tokenManager.getUserId() : null;
                
                if (currentUserId != null
                        && machineCode != null && !machineCode.isEmpty()
                        && randomCode != null && !randomCode.isEmpty()) {
                    boolean linked = ApiService.linkUserToMachine(machineCode, randomCode, currentUserId);
                    Log.d(TAG, "Link user to machine result: " + linked 
                            + " (userId=" + currentUserId + ", machineCode=" + machineCode + ")");
                } else {
                    Log.d(TAG, "Skip linkUserToMachine: missing currentUserId or machine info");
                }
            } catch (Exception ex) {
                Log.e(TAG, "Error while linking user to machine", ex);
            }
            
            // BƯỚC 3: Đã có thông tin local → kiểm tra server
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
                    
                    // Lưu mã cũ
                    previousRandomCode = randomCode;
                    randomCode = serverRandomCode;
                    lastUpdated = System.currentTimeMillis();
                    saveToStorage();
                    
                    // Notify listener
                    if (changeListener != null) {
                        changeListener.onRandomCodeChanged(previousRandomCode, randomCode);
                    }
                } else {
                    Log.d(TAG, "✅ RandomCode unchanged: " + randomCode);
                }
                
                return true;
            } else {
                // Machine không tồn tại trên server → tạo lại
                Log.w(TAG, "Machine not found on server, creating new one...");
                randomCode = null;
                saveToStorage();
                return checkAndUpdateMachine(token); // Recursive call để tạo mới
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
    
    public void setChangeListener(RandomCodeChangeListener listener) {
        this.changeListener = listener;
    }
    
    /**
     * Xóa toàn bộ thông tin machine (reset)
     */
    public void clearMachineInfo() {
        machineCode = null;
        randomCode = null;
        machineName = "";
        lastUpdated = 0;
        previousRandomCode = null;
        
        prefs.edit().clear().apply();
        Log.d(TAG, "Machine info cleared");

        // Xóa luôn file JSON ngoài bộ nhớ nếu có
        try {
            File file = getMachineJsonFile();
            if (file != null && file.exists()) {
                boolean deleted = file.delete();
                Log.d(TAG, "External machine.json deleted: " + deleted);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error deleting external machine.json", e);
        }
    }
}

