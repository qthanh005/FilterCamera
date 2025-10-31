package com.example.cameraapp;

import android.graphics.Bitmap;

public class FilterItem {

    public enum FilterType {
        NORMAL,
        GRAY,
        SEPIA,
        BRIGHT,
        INVERT,
        CONTRAST,
        HUE,
        VINTAGE
    }

    public String name;
    public Bitmap previewBitmap;
    public FilterType type;

    public FilterItem(String name, Bitmap previewBitmap, FilterType type) {
        this.name = name;
        this.previewBitmap = previewBitmap;
        this.type = type;
    }
}
