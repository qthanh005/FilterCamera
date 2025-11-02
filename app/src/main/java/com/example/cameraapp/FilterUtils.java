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

    // Instagram-like filters
    public static Bitmap filterClarendon(Bitmap src) {
        // High contrast, warm tones
        ColorMatrix cm = new ColorMatrix(new float[]{
                1.2f, 0.1f, 0.1f, 0, 10,
                0.1f, 1.1f, 0.05f, 0, 5,
                0.05f, 0.05f, 1.15f, 0, 0,
                0, 0, 0, 1, 0
        });
        return applyColorMatrix(src, cm);
    }

    public static Bitmap filterGingham(Bitmap src) {
        // Soft, pastel colors
        ColorMatrix cm = new ColorMatrix(new float[]{
                1.05f, 0.05f, -0.05f, 0, 5,
                0.05f, 1.0f, 0.05f, 0, 5,
                -0.05f, 0.05f, 1.05f, 0, 5,
                0, 0, 0, 1, 0
        });
        return applyColorMatrix(src, cm);
    }

    public static Bitmap filterMoon(Bitmap src) {
        // High contrast, desaturated, dark
        ColorMatrix cm = new ColorMatrix();
        cm.setSaturation(0.3f);
        ColorMatrix contrast = new ColorMatrix(new float[]{
                1.4f, 0, 0, 0, -30,
                0, 1.4f, 0, 0, -30,
                0, 0, 1.4f, 0, -30,
                0, 0, 0, 1, 0
        });
        cm.postConcat(contrast);
        return applyColorMatrix(src, cm);
    }

    public static Bitmap filterLark(Bitmap src) {
        // Bright, high saturation, warm
        ColorMatrix cm = new ColorMatrix();
        cm.setSaturation(1.3f);
        ColorMatrix bright = new ColorMatrix(new float[]{
                1.15f, 0.05f, 0.05f, 0, 10,
                0.05f, 1.1f, 0.05f, 0, 10,
                0.05f, 0.05f, 1.05f, 0, 5,
                0, 0, 0, 1, 0
        });
        cm.postConcat(bright);
        return applyColorMatrix(src, cm);
    }

    public static Bitmap filterReyes(Bitmap src) {
        // Warm, soft, vintage feel
        ColorMatrix cm = new ColorMatrix(new float[]{
                1.1f, 0.15f, 0.05f, 0, 15,
                0.15f, 1.05f, 0.1f, 0, 10,
                0.05f, 0.1f, 0.95f, 0, -5,
                0, 0, 0, 1, 0
        });
        return applyColorMatrix(src, cm);
    }

    public static Bitmap filterJuno(Bitmap src) {
        // Cool tones, high contrast
        ColorMatrix cm = new ColorMatrix(new float[]{
                0.95f, 0.05f, 0.1f, 0, 10,
                0.05f, 1.05f, 0.15f, 0, 5,
                0.1f, 0.15f, 1.2f, 0, 10,
                0, 0, 0, 1, 0
        });
        ColorMatrix contrast = new ColorMatrix(new float[]{
                1.2f, 0, 0, 0, -15,
                0, 1.2f, 0, 0, -15,
                0, 0, 1.2f, 0, -15,
                0, 0, 0, 1, 0
        });
        cm.postConcat(contrast);
        return applyColorMatrix(src, cm);
    }

    public static Bitmap filterSlumber(Bitmap src) {
        // Desaturated, warm, soft
        ColorMatrix cm = new ColorMatrix();
        cm.setSaturation(0.6f);
        ColorMatrix warm = new ColorMatrix(new float[]{
                1.05f, 0.1f, 0.05f, 0, 5,
                0.1f, 1.0f, 0.05f, 0, 3,
                0.05f, 0.05f, 0.95f, 0, 0,
                0, 0, 0, 1, 0
        });
        cm.postConcat(warm);
        return applyColorMatrix(src, cm);
    }

    public static Bitmap filterCrema(Bitmap src) {
        // Soft, warm, vintage
        ColorMatrix cm = new ColorMatrix(new float[]{
                1.08f, 0.12f, 0.08f, 0, 8,
                0.12f, 1.05f, 0.08f, 0, 5,
                0.08f, 0.08f, 0.98f, 0, 2,
                0, 0, 0, 1, 0
        });
        return applyColorMatrix(src, cm);
    }

    public static Bitmap filterLudwig(Bitmap src) {
        // High contrast, cool tones
        ColorMatrix cm = new ColorMatrix(new float[]{
                1.0f, 0.05f, 0.1f, 0, 8,
                0.05f, 1.05f, 0.12f, 0, 5,
                0.1f, 0.12f, 1.15f, 0, 10,
                0, 0, 0, 1, 0
        });
        ColorMatrix contrast = new ColorMatrix(new float[]{
                1.25f, 0, 0, 0, -20,
                0, 1.25f, 0, 0, -20,
                0, 0, 1.25f, 0, -20,
                0, 0, 0, 1, 0
        });
        cm.postConcat(contrast);
        return applyColorMatrix(src, cm);
    }

    public static Bitmap filterAden(Bitmap src) {
        // Desaturated, cool, soft
        ColorMatrix cm = new ColorMatrix();
        cm.setSaturation(0.7f);
        ColorMatrix cool = new ColorMatrix(new float[]{
                0.98f, 0.02f, 0.05f, 0, 5,
                0.02f, 1.0f, 0.08f, 0, 3,
                0.05f, 0.08f, 1.1f, 0, 8,
                0, 0, 0, 1, 0
        });
        cm.postConcat(cool);
        return applyColorMatrix(src, cm);
    }

    public static Bitmap filterPerpetua(Bitmap src) {
        // Bright, soft, warm
        ColorMatrix cm = new ColorMatrix(new float[]{
                1.1f, 0.08f, 0.05f, 0, 12,
                0.08f, 1.08f, 0.05f, 0, 8,
                0.05f, 0.05f, 1.05f, 0, 5,
                0, 0, 0, 1, 0
        });
        return applyColorMatrix(src, cm);
    }

    public static Bitmap filterAmaro(Bitmap src) {
        // High saturation, warm, bright
        ColorMatrix cm = new ColorMatrix();
        cm.setSaturation(1.4f);
        ColorMatrix bright = new ColorMatrix(new float[]{
                1.2f, 0.1f, 0.05f, 0, 15,
                0.1f, 1.15f, 0.05f, 0, 10,
                0.05f, 0.05f, 1.1f, 0, 8,
                0, 0, 0, 1, 0
        });
        cm.postConcat(bright);
        return applyColorMatrix(src, cm);
    }

    public static Bitmap filterMayfair(Bitmap src) {
        // Soft pastels, warm tones
        ColorMatrix cm = new ColorMatrix(new float[]{
                1.12f, 0.1f, 0.08f, 0, 10,
                0.1f, 1.08f, 0.1f, 0, 8,
                0.08f, 0.1f, 1.05f, 0, 5,
                0, 0, 0, 1, 0
        });
        return applyColorMatrix(src, cm);
    }

    public static Bitmap filterRise(Bitmap src) {
        // Warm, golden hour feel
        ColorMatrix cm = new ColorMatrix(new float[]{
                1.15f, 0.2f, 0.1f, 0, 20,
                0.2f, 1.1f, 0.1f, 0, 15,
                0.1f, 0.1f, 0.95f, 0, 5,
                0, 0, 0, 1, 0
        });
        return applyColorMatrix(src, cm);
    }

    public static Bitmap filterValencia(Bitmap src) {
        // Warm, vintage, soft
        ColorMatrix cm = new ColorMatrix(new float[]{
                1.1f, 0.15f, 0.1f, 0, 12,
                0.15f, 1.05f, 0.1f, 0, 8,
                0.1f, 0.1f, 0.98f, 0, 3,
                0, 0, 0, 1, 0
        });
        return applyColorMatrix(src, cm);
    }

    public static Bitmap filterXProII(Bitmap src) {
        // High contrast, cross-processed look
        ColorMatrix cm = new ColorMatrix(new float[]{
                1.3f, 0.1f, -0.1f, 0, 15,
                0.1f, 1.25f, 0.05f, 0, 5,
                -0.1f, 0.05f, 1.2f, 0, -5,
                0, 0, 0, 1, 0
        });
        ColorMatrix contrast = new ColorMatrix(new float[]{
                1.3f, 0, 0, 0, -25,
                0, 1.3f, 0, 0, -25,
                0, 0, 1.3f, 0, -25,
                0, 0, 0, 1, 0
        });
        cm.postConcat(contrast);
        return applyColorMatrix(src, cm);
    }

    public static Bitmap filterLoFi(Bitmap src) {
        // High saturation, high contrast, warm
        ColorMatrix cm = new ColorMatrix();
        cm.setSaturation(1.5f);
        ColorMatrix contrast = new ColorMatrix(new float[]{
                1.2f, 0.05f, 0.1f, 0, 10,
                0.05f, 1.15f, 0.1f, 0, 8,
                0.1f, 0.1f, 1.1f, 0, 5,
                0, 0, 0, 1, 0
        });
        cm.postConcat(contrast);
        return applyColorMatrix(src, cm);
    }

    public static Bitmap filterSierra(Bitmap src) {
        // Warm, vintage, soft contrast
        ColorMatrix cm = new ColorMatrix(new float[]{
                1.08f, 0.12f, 0.08f, 0, 10,
                0.12f, 1.06f, 0.08f, 0, 8,
                0.08f, 0.08f, 1.0f, 0, 5,
                0, 0, 0, 1, 0
        });
        return applyColorMatrix(src, cm);
    }

    public static Bitmap filterWillow(Bitmap src) {
        // Monochrome with slight tint, high contrast
        ColorMatrix cm = new ColorMatrix();
        cm.setSaturation(0.2f);
        ColorMatrix contrast = new ColorMatrix(new float[]{
                1.15f, 0, 0, 0, 5,
                0, 1.15f, 0, 0, 5,
                0, 0, 1.2f, 0, 10,
                0, 0, 0, 1, 0
        });
        cm.postConcat(contrast);
        return applyColorMatrix(src, cm);
    }
}
