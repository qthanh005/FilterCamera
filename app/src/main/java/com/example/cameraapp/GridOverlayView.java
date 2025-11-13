package com.example.cameraapp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

public class GridOverlayView extends View {
    private Paint gridPaint;
    private boolean showGrid = false;

    public GridOverlayView(Context context) {
        super(context);
        init();
    }

    public GridOverlayView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public GridOverlayView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        gridPaint = new Paint();
        gridPaint.setColor(0x80FFFFFF); // White with 50% opacity
        gridPaint.setStrokeWidth(1.5f);
        gridPaint.setAntiAlias(true);
    }

    public void setShowGrid(boolean show) {
        this.showGrid = show;
        invalidate();
    }

    public boolean isGridVisible() {
        return showGrid;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        
        if (!showGrid) {
            return;
        }

        int width = getWidth();
        int height = getHeight();

        if (width == 0 || height == 0) {
            return;
        }

        // Draw 3x3 grid (rule of thirds)
        float thirdWidth = width / 3f;
        float thirdHeight = height / 3f;

        // Vertical lines
        canvas.drawLine(thirdWidth, 0, thirdWidth, height, gridPaint);
        canvas.drawLine(thirdWidth * 2, 0, thirdWidth * 2, height, gridPaint);

        // Horizontal lines
        canvas.drawLine(0, thirdHeight, width, thirdHeight, gridPaint);
        canvas.drawLine(0, thirdHeight * 2, width, thirdHeight * 2, gridPaint);
    }
}

