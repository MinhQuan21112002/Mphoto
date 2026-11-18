package com.sdk.esc;

import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.view.Window;
import android.view.View;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.mphoto.mono.R;

/** Pink system bars + transparent dialog window (khớp Lite). */
public final class GlassDialogHelper {

    private GlassDialogHelper() {
    }

    public static void applyGlassWindow(Dialog dialog) {
        Window window = dialog.getWindow();
        if (window == null) {
            return;
        }
        window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        window.setDimAmount(0.28f);
    }

    public static void applyGlassWindow(AlertDialog dialog) {
        applyGlassWindow((Dialog) dialog);
    }

    public static void applyPinkSystemBars(AppCompatActivity activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            int color = ContextCompat.getColor(activity, R.color.mp_brand_screen);
            Window window = activity.getWindow();
            window.setStatusBarColor(color);
            window.setNavigationBarColor(color);

            int visibility = window.getDecorView().getSystemUiVisibility();
            if (MonoThemeManager.isDark(activity)) {
                visibility &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    visibility &= ~View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
                }
            } else {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    visibility |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    visibility |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
                }
            }
            window.getDecorView().setSystemUiVisibility(visibility);
        }
    }
}
