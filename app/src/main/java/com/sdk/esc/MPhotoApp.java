package com.sdk.esc;

import android.app.Application;

/**
 * Khôi phục cấu hình ảnh / Drive từ file công khai trước mọi activity.
 */
public class MPhotoApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        // Mỗi lần mở lại app: hiện lại nút chụp giữa (ẩn chỉ giữ trong phiên hiện tại)
        getSharedPreferences("settings", MODE_PRIVATE)
                .edit()
                .putBoolean("click_button_hidden", false)
                .apply();
        MPhotoUserDataBackup.restoreIfPrefsEmpty(this);
        MonoCacheSync.migrateLegacyInternalCacheToAppUserAssetsIfNeeded(this);
        UserAssetFileStore.migrateListToFileTokensIfNeeded(this);
        MPhotoUserDataBackup.scheduleSave(this);
        MonoDriveServerSync.requestSyncIfLoggedIn(this);
    }
}
