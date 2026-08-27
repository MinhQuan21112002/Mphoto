package com.sdk.esc;

import android.app.Activity;
import android.app.KeyguardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.PowerManager;
import android.util.Log;
import android.view.Window;
import android.view.WindowManager;

/**
 * Đánh thức màn hình / CPU khi Control Page gửi lệnh (máy sleep nhưng socket còn sống).
 */
public final class DeviceWakeHelper {
    private static final String TAG = "DeviceWakeHelper";
    private static final String WAKE_TAG = "MPhoto::WakeFromControl";

    private DeviceWakeHelper() {
    }

    public static void wake(Context context, Activity activity) {
        Context app = context != null
                ? context.getApplicationContext()
                : (activity != null ? activity.getApplicationContext() : null);
        if (app == null && activity == null) {
            Log.w(TAG, "wake: no context");
            return;
        }

        try {
            PowerManager pm = (PowerManager) (app != null ? app : activity)
                    .getSystemService(Context.POWER_SERVICE);
            if (pm != null) {
                @SuppressWarnings("deprecation")
                PowerManager.WakeLock wl = pm.newWakeLock(
                        PowerManager.FULL_WAKE_LOCK
                                | PowerManager.ACQUIRE_CAUSES_WAKEUP
                                | PowerManager.ON_AFTER_RELEASE,
                        WAKE_TAG);
                wl.acquire(4000L);
                Log.d(TAG, "WakeLock acquired briefly");
            }
        } catch (Exception e) {
            Log.w(TAG, "WakeLock failed: " + e.getMessage());
        }

        if (activity == null) {
            Log.w(TAG, "wake: no Activity — chỉ WakeLock");
            return;
        }

        activity.runOnUiThread(() -> {
            try {
                applyScreenOnFlags(activity);
                dismissKeyguard(activity);
                Intent i = new Intent(activity, activity.getClass());
                i.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                        | Intent.FLAG_ACTIVITY_SINGLE_TOP
                        | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                activity.startActivity(i);
                Log.d(TAG, "Brought activity to front: " + activity.getClass().getSimpleName());
            } catch (Exception e) {
                Log.e(TAG, "wake UI failed", e);
            }
        });
    }

    public static void applyScreenOnFlags(Activity activity) {
        if (activity == null) return;
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                activity.setTurnScreenOn(true);
                activity.setShowWhenLocked(true);
            }
            Window w = activity.getWindow();
            if (w != null) {
                w.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O_MR1) {
                    @SuppressWarnings("deprecation")
                    int legacy = WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                            | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                            | WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD;
                    w.addFlags(legacy);
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "applyScreenOnFlags: " + e.getMessage());
        }
    }

    private static void dismissKeyguard(Activity activity) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                KeyguardManager km = (KeyguardManager) activity.getSystemService(Context.KEYGUARD_SERVICE);
                if (km != null && km.isKeyguardLocked()) {
                    km.requestDismissKeyguard(activity, null);
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "dismissKeyguard: " + e.getMessage());
        }
    }
}
