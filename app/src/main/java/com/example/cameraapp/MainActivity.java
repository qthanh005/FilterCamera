package com.example.cameraapp;

import android.Manifest;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
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
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.common.util.concurrent.ListenableFuture;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static com.example.cameraapp.FilterItem.FilterType.*;
import android.graphics.Matrix;
import android.media.ExifInterface;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.File;

public class MainActivity extends AppCompatActivity {

    private PreviewView previewView;
    private ImageCapture imageCapture;
    private ExecutorService cameraExecutor;

    private ImageButton btnCapture, btnFilter, btnBack, btnSave;
    private ImageView imageView;
    private TextView tvPermissionStatus;
    private RecyclerView rvFilters;

    private Bitmap capturedBitmap = null;
    private Bitmap appliedBitmap = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        previewView = findViewById(R.id.previewView);
        btnCapture = findViewById(R.id.btnCapture);
        btnFilter = findViewById(R.id.btnFilter);
        btnBack = findViewById(R.id.btnBackToCamera);
        btnSave = findViewById(R.id.btnSave);
        imageView = findViewById(R.id.imageView);
        tvPermissionStatus = findViewById(R.id.tvPermissionStatus);
        rvFilters = findViewById(R.id.rvFilters);

        cameraExecutor = Executors.newSingleThreadExecutor();

        updateCameraState();

        btnCapture.setOnClickListener(v -> capturePhoto());
        btnBack.setOnClickListener(v -> backToCamera());
        btnFilter.setOnClickListener(v -> {
            if (capturedBitmap != null) {
                rvFilters.setVisibility(rvFilters.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE);
            }
        });
        btnSave.setOnClickListener(v -> {
            if (appliedBitmap != null) {
                boolean ok = saveBitmapToGallery(appliedBitmap);
                Toast.makeText(MainActivity.this, ok ? "Ảnh đã lưu!" : "Lưu ảnh thất bại", Toast.LENGTH_SHORT).show();
                if (ok) {
                    backToCamera();
                }
            }
        });
    }

    private boolean hasCameraPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED;
    }

    private Bitmap getCorrectBitmap(Uri uri) {
        try (InputStream inputStream = getContentResolver().openInputStream(uri)) {
            Bitmap bitmap = BitmapFactory.decodeStream(inputStream);

            // Đọc Exif để biết orientation
            InputStream exifInputStream = getContentResolver().openInputStream(uri);
            ExifInterface exif = new ExifInterface(exifInputStream);
            int orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);

            Matrix matrix = new Matrix();
            switch (orientation) {
                case ExifInterface.ORIENTATION_ROTATE_90: matrix.postRotate(90); break;
                case ExifInterface.ORIENTATION_ROTATE_180: matrix.postRotate(180); break;
                case ExifInterface.ORIENTATION_ROTATE_270: matrix.postRotate(270); break;
                default: break;
            }

            return Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
    private void updateCameraState() {
        new android.os.Handler().postDelayed(() -> {
            if (hasCameraPermission()) {
                tvPermissionStatus.setVisibility(View.GONE);
                previewView.setVisibility(View.VISIBLE);
                btnCapture.setVisibility(View.VISIBLE);
                startCamera();
            } else {
                tvPermissionStatus.setVisibility(View.VISIBLE);
                tvPermissionStatus.setText("⚠ Quyền camera chưa được bật.");
                previewView.setVisibility(View.GONE);
                btnCapture.setVisibility(View.GONE);

                boolean shownBefore = getSharedPreferences("camera_prefs", MODE_PRIVATE)
                        .getBoolean("dialog_shown", false);

                if (!isFinishing() && !shownBefore) {
                    new AlertDialog.Builder(this)
                            .setTitle("Cần quyền Camera")
                            .setMessage("Ứng dụng cần quyền Camera để chụp ảnh.\nHãy mở Cài đặt → Quyền → Bật Camera.")
                            .setPositiveButton("Mở Cài đặt", (dialog, which) -> openAppSettings())
                            .setNegativeButton("Thoát", (dialog, which) -> finish())
                            .setCancelable(false)
                            .show();
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
        } catch (Exception e) { e.printStackTrace(); }
    }

    private void capturePhoto() {
        if (imageCapture == null) return;

        try {
            File tempFile = new File(getCacheDir(), "capture_" + System.currentTimeMillis() + ".jpg");
            ImageCapture.OutputFileOptions outputOptions =
                    new ImageCapture.OutputFileOptions.Builder(tempFile).build();

            imageCapture.takePicture(outputOptions, cameraExecutor,
                    new ImageCapture.OnImageSavedCallback() {
                        @Override
                        public void onImageSaved(@NonNull ImageCapture.OutputFileResults outputFileResults) {
                            Uri uri = Uri.fromFile(tempFile);
                            runOnUiThread(() -> showCapturedImage(uri));
                        }

                        @Override
                        public void onError(@NonNull ImageCaptureException exception) {
                            runOnUiThread(() ->
                                    Toast.makeText(MainActivity.this, "Lỗi khi chụp: " + exception.getMessage(), Toast.LENGTH_SHORT).show()
                            );
                        }
                    });
        } catch (Exception e) {
            Toast.makeText(this, "Lỗi khi chụp: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void showCapturedImage(Uri uri) {
        previewView.setVisibility(View.GONE);
        btnCapture.setVisibility(View.GONE);
        btnFilter.setVisibility(View.VISIBLE);
        btnBack.setVisibility(View.VISIBLE);
        btnSave.setVisibility(View.VISIBLE);
        imageView.setVisibility(View.VISIBLE);

        // Glide vẫn load để hiển thị nhanh
        Glide.with(this).load(uri).into(imageView);

        // Load bitmap đúng hướng
        capturedBitmap = getCorrectBitmap(uri);
        appliedBitmap = capturedBitmap;

        setupFilterRecyclerView();
        stopCamera();
    }

    private void setupFilterRecyclerView() {
        // Luôn dùng ảnh nền mặc định để preview filter
        Bitmap previewSource = BitmapFactory.decodeResource(getResources(), R.drawable.default_preview);

        Bitmap preview = Bitmap.createScaledBitmap(previewSource, 100, 100, true);

        List<FilterItem> filterList = new ArrayList<>();
        filterList.add(new FilterItem("Normal", preview, NORMAL));
        filterList.add(new FilterItem("Gray", FilterUtils.filterGray(preview), GRAY));
        filterList.add(new FilterItem("Sepia", FilterUtils.filterSepia(preview), SEPIA));
        filterList.add(new FilterItem("Bright", FilterUtils.filterBright(preview, 1.2f), BRIGHT));
        filterList.add(new FilterItem("Invert", FilterUtils.filterInvert(preview), INVERT));
        filterList.add(new FilterItem("Contrast", FilterUtils.filterContrast(preview, 1.3f), CONTRAST));
        filterList.add(new FilterItem("Hue", FilterUtils.filterHue(preview, 45f), HUE));
        filterList.add(new FilterItem("Vintage", FilterUtils.filterVintage(preview), VINTAGE));

        FilterAdapter adapter = new FilterAdapter(this, filterList, filter -> {
            if (capturedBitmap != null) {
                Bitmap filteredBitmap = capturedBitmap;
                switch (filter.type) {
                    case GRAY: filteredBitmap = FilterUtils.filterGray(capturedBitmap); break;
                    case SEPIA: filteredBitmap = FilterUtils.filterSepia(capturedBitmap); break;
                    case BRIGHT: filteredBitmap = FilterUtils.filterBright(capturedBitmap, 1.2f); break;
                    case INVERT: filteredBitmap = FilterUtils.filterInvert(capturedBitmap); break;
                    case CONTRAST: filteredBitmap = FilterUtils.filterContrast(capturedBitmap, 1.3f); break;
                    case HUE: filteredBitmap = FilterUtils.filterHue(capturedBitmap, 45f); break;
                    case VINTAGE: filteredBitmap = FilterUtils.filterVintage(capturedBitmap); break;
                    case NORMAL: filteredBitmap = capturedBitmap; break;
                }
                appliedBitmap = filteredBitmap;
                imageView.setImageBitmap(filteredBitmap);
            }
        });

        rvFilters.setAdapter(adapter);
        rvFilters.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        rvFilters.setVisibility(View.VISIBLE);
    }


    private void backToCamera() {
        imageView.setVisibility(View.GONE);
        btnFilter.setVisibility(View.GONE);
        btnBack.setVisibility(View.GONE);
        btnSave.setVisibility(View.GONE);
        rvFilters.setVisibility(View.GONE);
        previewView.setVisibility(View.VISIBLE);
        btnCapture.setVisibility(View.VISIBLE);

        if (hasCameraPermission()) startCamera();
    }

    private boolean saveBitmapToGallery(@NonNull Bitmap bitmap) {
        try {
            ContentValues contentValues = new ContentValues();
            contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, "CameraX_" + System.currentTimeMillis());
            contentValues.put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg");
            Uri uri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues);
            if (uri == null) return false;
            OutputStream os = getContentResolver().openOutputStream(uri);
            if (os == null) return false;
            boolean ok = bitmap.compress(Bitmap.CompressFormat.JPEG, 95, os);
            os.flush();
            os.close();
            return ok;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    private void stopCamera() {
        try {
            ProcessCameraProvider cameraProvider = ProcessCameraProvider.getInstance(this).get();
            cameraProvider.unbindAll();
        } catch (Exception ignored) {}
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateCameraState();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cameraExecutor.shutdown();
    }
}
