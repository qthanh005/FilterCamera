package com.example.cameraapp;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.View;
import androidx.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

public class FaceOverlayView extends View {
    
    private List<FaceRect> faceRects = new ArrayList<>();
    private Bitmap stickerBitmap;
    private Matrix transformMatrix;
    private StickerType stickerType = StickerType.FACE;
    
    public enum StickerType {
        FACE,   // Sticker ở mặt
        HAT,    // Sticker ở đầu
        NECK    // Sticker ở cổ
    }
    
    public FaceOverlayView(Context context) {
        super(context);
    }
    
    public FaceOverlayView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }
    
    public void setSticker(Bitmap sticker) {
        this.stickerBitmap = sticker;
        this.stickerType = StickerType.FACE; // Reset về FACE khi gọi phương thức cũ
        invalidate();
    }
    
    public void setSticker(Bitmap sticker, boolean isHat) {
        this.stickerBitmap = sticker;
        this.stickerType = isHat ? StickerType.HAT : StickerType.FACE;
        invalidate();
    }
    
    public void setSticker(Bitmap sticker, StickerType type) {
        this.stickerBitmap = sticker;
        this.stickerType = type;
        invalidate();
    }
    
    public void updateFaces(List<Rect> faces, Matrix matrix) {
        faceRects.clear();
        this.transformMatrix = matrix;
        
        if (faces != null) {
            for (Rect face : faces) {
                faceRects.add(new FaceRect(face));
            }
        }
        
        invalidate();
    }
    
    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        
        if (stickerBitmap != null && !faceRects.isEmpty() && transformMatrix != null) {
            for (FaceRect faceRect : faceRects) {
                Rect rect = faceRect.rect;
                
                // Transform rect coordinates từ ImageProxy sang PreviewView
                float[] points = {
                    rect.left, rect.top,
                    rect.right, rect.bottom
                };
                transformMatrix.mapPoints(points);
                
                int left = (int) points[0];
                int top = (int) points[1];
                int right = (int) points[2];
                int bottom = (int) points[3];
                
                // Tính kích thước sticker dựa trên kích thước face (lớn hơn một chút)
                int faceWidth = Math.abs(right - left);
                int faceHeight = Math.abs(bottom - top);
                int stickerSize = (int) (Math.max(faceWidth, faceHeight) * 1.2f);
                
                // Vẽ sticker tùy theo loại
                int x = Math.min(left, right) + (faceWidth - stickerSize) / 2;
                int y;
                switch (stickerType) {
                    case HAT:
                        // Đặt sticker hat ở trên đầu (trên top của face, offset lên một phần)
                        y = Math.min(top, bottom) - (int) (stickerSize * 0.6f);
                        break;
                    case NECK:
                        // Đặt sticker necklace ở cổ (dưới bottom của face, offset xuống một phần)
                        y = Math.max(top, bottom) + (int) (faceHeight * 0.3f);
                        break;
                    case FACE:
                    default:
                        // Sticker thông thường ở giữa mặt
                        y = Math.min(top, bottom) + (faceHeight - stickerSize) / 2;
                        break;
                }
                
                if (stickerBitmap != null && !stickerBitmap.isRecycled()) {
                    Bitmap scaled = Bitmap.createScaledBitmap(
                        stickerBitmap, 
                        stickerSize, 
                        stickerSize, 
                        true
                    );
                    canvas.drawBitmap(scaled, x, y, null);
                }
            }
        }
    }
    
    private static class FaceRect {
        Rect rect;
        
        FaceRect(Rect rect) {
            this.rect = rect;
        }
    }
}

