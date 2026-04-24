package com.sdk.esc;

import android.app.Application;

/**
 * Khôi phục cấu hình ảnh / Drive từ file công khai trước mọi activity.
 */
public class MPhotoApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        MPhotoUserDataBackup.restoreIfPrefsEmpty(this);
        MonoCacheSync.migrateLegacyInternalCacheToAppUserAssetsIfNeeded(this);
        UserAssetFileStore.migrateListToFileTokensIfNeeded(this);
        MPhotoUserDataBackup.scheduleSave(this);
        MonoDriveServerSync.requestSyncIfLoggedIn(this);
    }
}
