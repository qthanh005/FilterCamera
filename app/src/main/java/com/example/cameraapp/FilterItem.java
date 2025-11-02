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
        VINTAGE,
        // Instagram-like filters
        CLARENDON,
        GINGHAM,
        MOON,
        LARK,
        REYES,
        JUNO,
        SLUMBER,
        CREMA,
        LUDWIG,
        ADEN,
        PERPETUA,
        AMARO,
        MAYFAIR,
        RISE,
        VALENCIA,
        XPROII,
        LOFI,
        SIERRA,
        WILLOW
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
