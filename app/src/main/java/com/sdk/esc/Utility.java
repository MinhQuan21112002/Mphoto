package com.sdk.esc;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.Paint;

/**
 * Created by NO on 2017/9/14.
 */

public class Utility {

    public static Bitmap Tobitmap(Bitmap bitmap, int width, int height) {
        // 입력 Bitmap 의 DPI 및 포맷 확인
        int dpi = bitmap.getDensity();
        bitmap.getConfig();
        Bitmap.Config config = bitmap.getConfig();

        // 새 Bitmap 생성
        Bitmap target = Bitmap.createBitmap(width, height, config);

        // DPI 설정 유지
        target.setDensity(dpi);

        // Canvas 초기화 및 그리기
        Canvas canvas = new Canvas(target);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        paint.setDither(true); // Dithering 활성화
        canvas.drawBitmap(bitmap, null, new Rect(0, 0, target.getWidth(), target.getHeight()), paint);

        // Bitmap 반환
        return target;
    }

    //width：目标宽度，pageWidthPoint：初始宽度，pageHeightPoint：初始高度
    public static int getHeight(int width, int pageWidthPoint, int pageHeightPoint) {
        double bili = width / (double) pageWidthPoint;
        return (int) (pageHeightPoint * bili);
    }

    public static Bitmap Tobitmap90(Bitmap bitmap) {
        Matrix matrix = new Matrix();
        // 设置旋转角度
        matrix.setRotate(90);
        // 重新绘制Bitmap
        bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
        return bitmap;
    }

}



