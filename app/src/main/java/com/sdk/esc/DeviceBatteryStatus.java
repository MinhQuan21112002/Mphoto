package com.sdk.esc;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import org.json.JSONObject;

/** Reads the latest Android battery broadcast without requesting permissions. */
final class DeviceBatteryStatus {
    static void apply(Context context, JSONObject payload) throws org.json.JSONException {
        Intent battery = context == null ? null : context.getApplicationContext()
                .registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        int level = battery == null ? -1 : battery.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = battery == null ? -1 : battery.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
        boolean available = battery != null && level >= 0 && scale > 0
                && battery.getBooleanExtra(BatteryManager.EXTRA_PRESENT, true);
        int percent = available ? Math.max(0, Math.min(100, Math.round(level * 100f / scale))) : 0;
        boolean plugged = battery != null && battery.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0;
        payload.put("batteryPercent", percent);
        payload.put("isBatteryAvailable", available);
        payload.put("isBatteryCharging", plugged);
    }
}
