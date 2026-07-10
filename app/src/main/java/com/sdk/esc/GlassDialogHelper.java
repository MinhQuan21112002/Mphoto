package com.sdk.esc;

import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.view.Window;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

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
            int color = Color.parseColor("#EFA9B8");
            activity.getWindow().setStatusBarColor(color);
            activity.getWindow().setNavigationBarColor(color);
        }
    }
}
