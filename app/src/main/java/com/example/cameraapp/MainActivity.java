package com.example.cameraapp;

import android.Manifest;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;

import com.bumptech.glide.Glide;
import com.google.common.util.concurrent.ListenableFuture;

import java.io.InputStream;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private PreviewView previewView;
    private ImageCapture imageCapture;
    private ExecutorService cameraExecutor;

    private ImageButton btnCapture, btnFilter, btnBack;
    private ImageView imageView;
    private TextView tvPermissionStatus;

    private Bitmap capturedBitmap = null;
    private boolean isGray = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        previewView = findViewById(R.id.previewView);
        btnCapture = findViewById(R.id.btnCapture);
        btnFilter = findViewById(R.id.btnFilter);
        btnBack = findViewById(R.id.btnBackToCamera);
        imageView = findViewById(R.id.imageView);
        tvPermissionStatus = findViewById(R.id.tvPermissionStatus);

        cameraExecutor = Executors.newSingleThreadExecutor();

        updateCameraState();

        btnCapture.setOnClickListener(v -> capturePhoto());
        btnFilter.setOnClickListener(v -> applyGrayFilter());
        btnBack.setOnClickListener(v -> backToCamera());
    }

    // ✅ Chỉ kiểm tra, không bao giờ xin quyền
    private boolean hasCameraPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED;
    }

    // Cập nhật giao diện tùy theo trạng thái quyền
    private void updateCameraState() {
        new android.os.Handler().postDelayed(() -> {
            if (hasCameraPermission()) {
                tvPermissionStatus.setVisibility(View.GONE);
                previewView.setVisibility(View.VISIBLE);
                btnCapture.setVisibility(View.VISIBLE);
                startCamera();

                // ✅ Đánh dấu là đã có quyền (reset flag)
                getSharedPreferences("camera_prefs", MODE_PRIVATE)
                        .edit().putBoolean("dialog_shown", false).apply();

            } else {
                stopCamera();
                tvPermissionStatus.setVisibility(View.VISIBLE);
                tvPermissionStatus.setText("⚠ Quyền camera chưa được bật.\nVui lòng vào Cài đặt → Quyền → Bật Camera cho ứng dụng này.");
                previewView.setVisibility(View.GONE);
                btnCapture.setVisibility(View.GONE);

                // ⚠️ Kiểm tra xem đã hiện dialog trước đó chưa
                boolean shownBefore = getSharedPreferences("camera_prefs", MODE_PRIVATE)
                        .getBoolean("dialog_shown", false);

                if (!isFinishing() && !shownBefore) {
                    new AlertDialog.Builder(this)
                            .setTitle("Cần quyền Camera")
                            .setMessage("Ứng dụng cần quyền Camera để chụp ảnh.\n\nHãy mở Cài đặt → Quyền → Bật Camera cho ứng dụng này.")
                            .setPositiveButton("Mở Cài đặt", (dialog, which) -> openAppSettings())
                            .setNegativeButton("Thoát", (dialog, which) -> finish())
                            .setCancelable(false)
                            .show();

                    // ✅ Ghi nhớ rằng popup này đã được hiển thị
                    getSharedPreferences("camera_prefs", MODE_PRIVATE)
                            .edit().putBoolean("dialog_shown", true).apply();
                }
            }
        }, 300);
    }


    private void openAppSettings() {
        Intent intent = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        Uri uri = Uri.fromParts("package", getPackageName(), null);
        intent.setData(uri);
        startActivity(intent);
        Toast.makeText(this, "Bật quyền Camera rồi quay lại ứng dụng.", Toast.LENGTH_LONG).show();
    }

    private void startCamera() {
        try {
            ListenableFuture<ProcessCameraProvider> cameraProviderFuture =
                    ProcessCameraProvider.getInstance(this);

            cameraProviderFuture.addListener(() -> {
                try {
                    ProcessCameraProvider cameraProvider = cameraProviderFuture.get();

                    Preview preview = new Preview.Builder().build();
                    imageCapture = new ImageCapture.Builder().build();

                    CameraSelector cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA;

                    preview.setSurfaceProvider(previewView.getSurfaceProvider());

                    cameraProvider.unbindAll();
                    cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture);

                } catch (ExecutionException | InterruptedException e) {
                    e.printStackTrace();
                }
            }, ContextCompat.getMainExecutor(this));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void stopCamera() {
        try {
            ProcessCameraProvider cameraProvider = ProcessCameraProvider.getInstance(this).get();
            cameraProvider.unbindAll();
        } catch (Exception ignored) {}
    }

    private void capturePhoto() {
        if (imageCapture == null) return;

        ContentValues contentValues = new ContentValues();
        contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, "CameraX_" + System.currentTimeMillis());
        contentValues.put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg");

        ImageCapture.OutputFileOptions outputOptions =
                new ImageCapture.OutputFileOptions.Builder(
                        getContentResolver(),
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        contentValues
                ).build();

        imageCapture.takePicture(outputOptions, cameraExecutor,
                new ImageCapture.OnImageSavedCallback() {
                    @Override
                    public void onImageSaved(@NonNull ImageCapture.OutputFileResults outputFileResults) {
                        Uri savedUri = outputFileResults.getSavedUri();
                        runOnUiThread(() -> {
                            Toast.makeText(MainActivity.this, "Ảnh đã lưu!", Toast.LENGTH_SHORT).show();
                            showCapturedImage(savedUri);
                        });
                    }

                    @Override
                    public void onError(@NonNull ImageCaptureException exception) {
                        runOnUiThread(() ->
                                Toast.makeText(MainActivity.this, "Lỗi khi chụp: " + exception.getMessage(), Toast.LENGTH_SHORT).show()
                        );
                    }
                });
    }

    private void showCapturedImage(Uri uri) {
        previewView.setVisibility(View.GONE);
        btnCapture.setVisibility(View.GONE);
        btnFilter.setVisibility(View.VISIBLE);
        btnBack.setVisibility(View.VISIBLE);
        imageView.setVisibility(View.VISIBLE);

        Glide.with(this).load(uri).into(imageView);

        try (InputStream inputStream = getContentResolver().openInputStream(uri)) {
            capturedBitmap = BitmapFactory.decodeStream(inputStream);
            stopCamera();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void applyGrayFilter() {
        if (capturedBitmap == null) return;

        Bitmap filtered = Bitmap.createBitmap(
                capturedBitmap.getWidth(),
                capturedBitmap.getHeight(),
                capturedBitmap.getConfig()
        );

        android.graphics.Canvas canvas = new android.graphics.Canvas(filtered);
        Paint paint = new Paint();
        ColorMatrix cm = new ColorMatrix();
        cm.setSaturation(0);
        paint.setColorFilter(new ColorMatrixColorFilter(cm));
        canvas.drawBitmap(capturedBitmap, 0, 0, paint);

        imageView.setImageBitmap(filtered);
        isGray = true;
        Toast.makeText(this, "Đã áp filter xám!", Toast.LENGTH_SHORT).show();
    }

    private void backToCamera() {
        imageView.setVisibility(View.GONE);
        btnFilter.setVisibility(View.GONE);
        btnBack.setVisibility(View.GONE);
        previewView.setVisibility(View.VISIBLE);
        btnCapture.setVisibility(View.VISIBLE);

        if (hasCameraPermission()) startCamera();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateCameraState(); // 🔄 Tự kiểm tra lại quyền mỗi khi quay lại app
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cameraExecutor.shutdown();
    }
}
