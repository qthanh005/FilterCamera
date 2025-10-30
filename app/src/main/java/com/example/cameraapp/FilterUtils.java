package com.example.cameraapp;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.net.Uri;
import android.media.ExifInterface;

import java.io.IOException;
import java.io.InputStream;

public class FilterUtils {

    // 🩶 Grayscale filter
    public static Bitmap toGrayScale(Bitmap original) {
        Bitmap result = Bitmap.createBitmap(original.getWidth(), original.getHeight(), original.getConfig());
        Canvas canvas = new Canvas(result);
        Paint paint = new Paint();
        ColorMatrix cm = new ColorMatrix();
        cm.setSaturation(0);
        paint.setColorFilter(new ColorMatrixColorFilter(cm));
        canvas.drawBitmap(original, 0, 0, paint);
        return result;
    }

    // 🟤 Sepia filter
    public static Bitmap toSepia(Bitmap original) {
        Bitmap result = Bitmap.createBitmap(original.getWidth(), original.getHeight(), original.getConfig());
        Canvas canvas = new Canvas(result);
        Paint paint = new Paint();

        ColorMatrix cm = new ColorMatrix();
        cm.setScale(1f, 1f, 0.8f, 1f); // tone vàng nhẹ
        paint.setColorFilter(new ColorMatrixColorFilter(cm));

        canvas.drawBitmap(original, 0, 0, paint);
        return result;
    }

    // 🔵 Invert (âm bản)
    public static Bitmap toInvert(Bitmap original) {
        Bitmap result = Bitmap.createBitmap(original.getWidth(), original.getHeight(), original.getConfig());
        Canvas canvas = new Canvas(result);
        Paint paint = new Paint();

        ColorMatrix cm = new ColorMatrix(new float[]{
                -1.0f, 0, 0, 0, 255,
                0, -1.0f, 0, 0, 255,
                0, 0, -1.0f, 0, 255,
                0, 0, 0, 1.0f, 0
        });
        paint.setColorFilter(new ColorMatrixColorFilter(cm));

        canvas.drawBitmap(original, 0, 0, paint);
        return result;
    }

    // 🧭 Fix ảnh xoay sai hướng theo EXIF
    public static Bitmap fixImageRotation(Context context, Uri uri, Bitmap bitmap) {
        try (InputStream input = context.getContentResolver().openInputStream(uri)) {
            ExifInterface exif = new ExifInterface(input);
            int rotation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
            int degrees = 0;

            switch (rotation) {
                case ExifInterface.ORIENTATION_ROTATE_90:
                    degrees = 90;
                    break;
                case ExifInterface.ORIENTATION_ROTATE_180:
                    degrees = 180;
                    break;
                case ExifInterface.ORIENTATION_ROTATE_270:
                    degrees = 270;
                    break;
            }

            if (degrees != 0) {
                Matrix matrix = new Matrix();
                matrix.postRotate(degrees);
                bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
        return bitmap;
    }
}
