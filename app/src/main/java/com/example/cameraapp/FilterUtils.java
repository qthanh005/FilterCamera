package com.example.cameraapp;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;

public class FilterUtils {

    public static Bitmap applyColorMatrix(Bitmap src, ColorMatrix cm) {
        Bitmap result = Bitmap.createBitmap(src.getWidth(), src.getHeight(), src.getConfig());
        Canvas canvas = new Canvas(result);
        Paint paint = new Paint();
        paint.setColorFilter(new ColorMatrixColorFilter(cm));
        canvas.drawBitmap(src, 0, 0, paint);
        return result;
    }

    public static Bitmap filterGray(Bitmap src) {
        ColorMatrix cm = new ColorMatrix();
        cm.setSaturation(0);
        return applyColorMatrix(src, cm);
    }

    public static Bitmap filterSepia(Bitmap src) {
        ColorMatrix cm = new ColorMatrix();
        cm.setScale(1f, 0.95f, 0.82f, 1.0f);
        return applyColorMatrix(src, cm);
    }

    public static Bitmap filterBright(Bitmap src, float value) {
        ColorMatrix cm = new ColorMatrix(new float[]{
                value, 0, 0, 0, 0,
                0, value, 0, 0, 0,
                0, 0, value, 0, 0,
                0, 0, 0, 1, 0
        });
        return applyColorMatrix(src, cm);
    }

    public static Bitmap filterInvert(Bitmap src) {
        ColorMatrix cm = new ColorMatrix(new float[]{
                -1, 0, 0, 0, 255,
                0, -1, 0, 0, 255,
                0, 0, -1, 0, 255,
                0, 0, 0, 1, 0
        });
        return applyColorMatrix(src, cm);
    }

    public static Bitmap filterContrast(Bitmap src, float contrast) {
        float scale = contrast;
        float translate = (-0.5f * scale + 0.5f) * 255.f;
        ColorMatrix cm = new ColorMatrix(new float[]{
                scale, 0, 0, 0, translate,
                0, scale, 0, 0, translate,
                0, 0, scale, 0, translate,
                0, 0, 0, 1, 0
        });
        return applyColorMatrix(src, cm);
    }

    public static Bitmap filterHue(Bitmap src, float hue) {
        ColorMatrix cm = new ColorMatrix();
        cm.setRotate(0, hue);
        cm.setRotate(1, hue);
        cm.setRotate(2, hue);
        return applyColorMatrix(src, cm);
    }

    public static Bitmap filterVintage(Bitmap src) {
        ColorMatrix cm = new ColorMatrix(new float[]{
                0.9f, 0.1f, 0, 0, 0,
                0.1f, 0.9f, 0, 0, 0,
                0, 0, 0.8f, 0, 0,
                0, 0, 0, 1, 0
        });
        return applyColorMatrix(src, cm);
    }
}
