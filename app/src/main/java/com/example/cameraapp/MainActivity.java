package com.example.cameraapp;

import android.Manifest;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.cardview.widget.CardView;
import com.google.android.material.switchmaterial.SwitchMaterial;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.face.Face;
import com.google.mlkit.vision.face.FaceDetection;
import com.google.mlkit.vision.face.FaceDetector;
import com.google.mlkit.vision.face.FaceDetectorOptions;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import android.view.View;
import android.view.ViewGroup;
import android.accounts.Account;
import android.accounts.AccountManager;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import android.media.ExifInterface;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;

import static com.example.cameraapp.FilterItem.FilterType.*;
import android.content.ContentUris;
import android.media.MediaScannerConnection;



public class MainActivity extends AppCompatActivity {

    private PreviewView previewView;
    private ImageCapture imageCapture;
    private ExecutorService cameraExecutor;
    private ProcessCameraProvider cameraProvider;
    private int currentCameraFacing = CameraSelector.LENS_FACING_BACK; // Mặc định camera sau

    private ImageButton btnCapture, btnFilter, btnBack, btnSave, btnGallery, btnSwitchCamera, btnGoogleDrive, btnGrid;
    private ImageView imageView;
    private TextView tvPermissionStatus, tvCountdown;
    private RecyclerView rvFilters;
    private FaceOverlayView faceOverlayView;
    private GridOverlayView gridOverlayView;
    private View driveStatusIndicator;
    private View countdownContainer, countdownBg, rippleCircle;
    
    // Countdown timer
    private android.os.CountDownTimer countDownTimer;
    private boolean isCountdownMode = false; // Trạng thái bật/tắt countdown mode
    private boolean isCountdownRunning = false; // Đang chạy countdown

    private Bitmap capturedBitmap = null;
    private Bitmap appliedBitmap = null;
    
    // Helper method để giải phóng bitmap an toàn
    private void recycleBitmap(Bitmap bitmap) {
        if (bitmap != null && !bitmap.isRecycled()) {
            bitmap.recycle();
        }
    }
    
    // Helper method để kiểm tra và lấy bitmap an toàn
    private Bitmap getSafeBitmap(Bitmap bitmap) {
        if (bitmap != null && !bitmap.isRecycled()) {
            return bitmap;
        }
        return null;
    }
    
    // Face Detection
    private FaceDetector faceDetector;
    private Bitmap currentStickerBitmap = null;
    private Integer currentStickerResId = null; // Lưu resId của sticker đang chọn
    private List<android.graphics.Rect> detectedFaces = new ArrayList<>();
    private List<android.graphics.Rect> capturedFaceRects = new ArrayList<>(); // Lưu faces để vẽ lại sau khi filter
    private int lastImageWidth = 0;
    private int lastImageHeight = 0;

    // Google Drive
    private static final int REQUEST_GOOGLE_SIGN_IN = 1001;
    private static final int REQUEST_AUTHORIZE = 1002;
    private static final int REQUEST_PICK_IMAGE = 1003;
    private static final String PREF_ACCOUNT_NAME = "accountName";
    private GoogleDriveHelper driveHelper;
    private Bitmap pendingSyncBitmap = null; // Lưu bitmap đang chờ sync khi cần auth
    private android.app.ProgressDialog uploadProgressDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Khởi tạo views
        previewView = findViewById(R.id.previewView);
        btnCapture = findViewById(R.id.btnCapture);
        btnFilter = findViewById(R.id.btnFilter);
        btnBack = findViewById(R.id.btnBackToCamera);
        btnSave = findViewById(R.id.btnSave);
        btnGallery = findViewById(R.id.btnGallery);
        btnSwitchCamera = findViewById(R.id.btnSwitchCamera);
        btnGoogleDrive = findViewById(R.id.btnGoogleDrive);
        btnGrid = findViewById(R.id.btnGrid);
        imageView = findViewById(R.id.imageView);
        tvPermissionStatus = findViewById(R.id.tvPermissionStatus);
        tvCountdown = findViewById(R.id.tvCountdown);
        countdownContainer = findViewById(R.id.countdownContainer);
        countdownBg = findViewById(R.id.countdownBg);
        rippleCircle = findViewById(R.id.rippleCircle);
        rvFilters = findViewById(R.id.rvFilters);
        faceOverlayView = findViewById(R.id.faceOverlayView);
        gridOverlayView = findViewById(R.id.gridOverlayView);
        driveStatusIndicator = findViewById(R.id.driveStatusIndicator);

        cameraExecutor = Executors.newSingleThreadExecutor();
        
        // Khởi tạo Face Detector
        FaceDetectorOptions options = new FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
                .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
                .build();
        faceDetector = FaceDetection.getClient(options);

        // Hiển thị gallery và switch camera ở chế độ camera preview
        btnGallery.setVisibility(ImageButton.VISIBLE);
        btnSwitchCamera.setVisibility(ImageButton.VISIBLE);

        // Kiểm tra đã đăng nhập Google chưa
        String accountName = getSharedPreferences("camera_prefs", MODE_PRIVATE)
                .getString(PREF_ACCOUNT_NAME, null);
        if (accountName != null) {
            try {
                driveHelper = new GoogleDriveHelper(this, accountName);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        
        // Cập nhật trạng thái nút Google Drive (sẽ được gọi lại sau khi init views)

        updateCameraState();
        loadLatestGalleryImage();

        // Các sự kiện click với animation nhẹ
        btnCapture.setOnClickListener(v -> {
            // Nếu đang chạy countdown, không làm gì
            if (isCountdownRunning) {
                return;
            }
            
            // Nếu countdown mode được bật, bắt đầu countdown
            if (isCountdownMode) {
                startCountdown();
            } else {
                // Chụp ngay lập tức
                Animation clickAnim = AnimationUtils.loadAnimation(this, R.anim.button_click_light);
                v.startAnimation(clickAnim);
                capturePhoto();
            }
        });
        btnBack.setOnClickListener(v -> backToCamera());
        btnFilter.setOnClickListener(v -> {
            if (getSafeBitmap(capturedBitmap) != null) {
                Animation clickAnim = AnimationUtils.loadAnimation(this, R.anim.button_click_light);
                v.startAnimation(clickAnim);
                toggleFilterRecyclerView();
            }
        });
        btnSave.setOnClickListener(v -> {
            Bitmap safeBitmap = getSafeBitmap(appliedBitmap);
            if (safeBitmap != null) {
                Animation clickAnim = AnimationUtils.loadAnimation(this, R.anim.button_click_light);
                v.startAnimation(clickAnim);
                boolean ok = saveBitmapToGallery(safeBitmap);
                Toast.makeText(MainActivity.this, ok ? "Ảnh đã lưu!" : "Lưu ảnh thất bại", Toast.LENGTH_SHORT).show();
                if (ok) {
                    // Tự động đồng bộ lên Google Drive sau khi lưu thành công
                    syncToDrive();
                    backToCamera();
                }
            }
        });
        btnGallery.setOnClickListener(v -> pickImageFromGallery());
        
        // Nút đổi camera (Front/Back)
        btnSwitchCamera.setOnClickListener(v -> {
            switchCamera();
        });
        
        // Nút Grid - hiển thị dropdown menu với Grid và Countdown
        btnGrid.setOnClickListener(v -> showGridMenu(v));
        
        // Long press vào btnCapture để mở sticker selector
        btnCapture.setOnLongClickListener(v -> {
            showStickerSelector();
            return true;
        });
        
        // Nút Google Drive - đăng nhập/đăng xuất
        btnGoogleDrive.setOnClickListener(v -> {
            if (driveHelper == null) {
                // Chưa đăng nhập, mở dialog đăng nhập
                new AlertDialog.Builder(this)
                        .setTitle("Đăng nhập Google Drive")
                        .setMessage("Đăng nhập để đồng bộ ảnh lên Google Drive.\n\nẢnh sẽ được lưu vào thư mục 'FilterCamera' trên Drive của bạn.")
                        .setPositiveButton("Đăng nhập", (dialog, which) -> signInToGoogle())
                        .setNegativeButton("Hủy", null)
                        .show();
            } else {
                // Đã đăng nhập, hiển thị thông tin
                String currentAccountName = getSharedPreferences("camera_prefs", MODE_PRIVATE)
                        .getString(PREF_ACCOUNT_NAME, "Unknown");
                new AlertDialog.Builder(this)
                        .setTitle("Google Drive")
                        .setMessage("Đã đăng nhập với: " + currentAccountName + "\n\nẢnh sẽ tự động đồng bộ lên Drive khi bạn lưu.")
                        .setPositiveButton("Đăng xuất", (dialog, which) -> {
                            // Đăng xuất
                            getSharedPreferences("camera_prefs", MODE_PRIVATE)
                                    .edit()
                                    .remove(PREF_ACCOUNT_NAME)
                                    .apply();
                            driveHelper = null;
                            Toast.makeText(this, "Đã đăng xuất Google Drive", Toast.LENGTH_SHORT).show();
                            updateGoogleDriveButton();
                        })
                        .setNegativeButton("OK", null)
                        .show();
            }
        });
        
        // Cập nhật trạng thái nút Google Drive
        updateGoogleDriveButton();
    }

    // Kiểm tra quyền camera
    private boolean hasCameraPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED;
    }

    // Lấy bitmap đúng hướng
    private Bitmap getCorrectBitmap(Uri uri) {
        try (InputStream inputStream = getContentResolver().openInputStream(uri)) {
            Bitmap bitmap = BitmapFactory.decodeStream(inputStream);
            InputStream exifInputStream = getContentResolver().openInputStream(uri);
            ExifInterface exif = new ExifInterface(exifInputStream);
            int orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);

            Matrix matrix = new Matrix();
            switch (orientation) {
                case ExifInterface.ORIENTATION_ROTATE_90: matrix.postRotate(90); break;
                case ExifInterface.ORIENTATION_ROTATE_180: matrix.postRotate(180); break;
                case ExifInterface.ORIENTATION_ROTATE_270: matrix.postRotate(270); break;
            }
            return Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    // Cập nhật trạng thái camera
    private void updateCameraState() {
        // Kiểm tra xem có đang ở chế độ xem ảnh không (imageView đang visible)
        // Nếu đang xem ảnh thì không khởi động lại camera
        if (imageView != null && imageView.getVisibility() == ImageView.VISIBLE) {
            return;
        }
        
        new android.os.Handler().postDelayed(() -> {
            if (hasCameraPermission()) {
                tvPermissionStatus.setVisibility(TextView.GONE);
                previewView.setVisibility(PreviewView.VISIBLE);
                btnCapture.setVisibility(ImageButton.VISIBLE);
                startCamera();
            } else {
                tvPermissionStatus.setVisibility(TextView.VISIBLE);
                tvPermissionStatus.setText("⚠ Quyền camera chưa được bật.");
                previewView.setVisibility(PreviewView.GONE);
                btnCapture.setVisibility(ImageButton.GONE);

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
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture =
                ProcessCameraProvider.getInstance(this);

        cameraProviderFuture.addListener(() -> {
            try {
                cameraProvider = cameraProviderFuture.get();
                bindCameraUseCases();
            } catch (ExecutionException | InterruptedException e) {
                e.printStackTrace();
            }
        }, ContextCompat.getMainExecutor(this));
    }
    
    // Bind camera use cases với camera selector hiện tại
    private void bindCameraUseCases() {
        if (cameraProvider == null) return;
        
        try {
            // Unbind tất cả use cases trước
            cameraProvider.unbindAll();
            
            // Tạo camera selector dựa trên currentCameraFacing
            CameraSelector cameraSelector;
            if (currentCameraFacing == CameraSelector.LENS_FACING_FRONT) {
                cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA;
            } else {
                cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA;
            }
            
            Preview preview = new Preview.Builder().build();
            preview.setSurfaceProvider(previewView.getSurfaceProvider());
            
            imageCapture = new ImageCapture.Builder()
                    .setTargetRotation(previewView.getDisplay().getRotation())
                    .build();
            
            // ImageAnalysis để detect faces
            ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build();
            
            imageAnalysis.setAnalyzer(cameraExecutor, imageProxy -> {
                processImageProxy(imageProxy);
            });
            
            // Bind tất cả use cases
            cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture, imageAnalysis);
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Lỗi khởi động camera: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
    
    // Đổi camera (Front/Back)
    private void switchCamera() {
        if (cameraProvider == null) {
            startCamera();
            return;
        }
        
        // Đổi camera facing
        if (currentCameraFacing == CameraSelector.LENS_FACING_BACK) {
            currentCameraFacing = CameraSelector.LENS_FACING_FRONT;
        } else {
            currentCameraFacing = CameraSelector.LENS_FACING_BACK;
        }
        
        // Bind lại với camera mới
        bindCameraUseCases();
    }
    
    private void processImageProxy(ImageProxy imageProxy) {
        // Lưu image dimensions để dùng khi capture
        lastImageWidth = imageProxy.getWidth();
        lastImageHeight = imageProxy.getHeight();
        
        Bitmap safeSticker = getSafeBitmap(currentStickerBitmap);
        if (safeSticker == null) {
            imageProxy.close();
            return;
        }
        
        InputImage image = InputImage.fromMediaImage(imageProxy.getImage(), imageProxy.getImageInfo().getRotationDegrees());
        
        faceDetector.process(image)
                .addOnSuccessListener(faces -> {
                    // Lưu detected faces để dùng khi capture
                    detectedFaces.clear();
                    for (Face face : faces) {
                        detectedFaces.add(face.getBoundingBox());
                    }
                    
                    if (!faces.isEmpty() && faceOverlayView != null) {
                        // Tính toán transform matrix từ ImageProxy coordinates sang PreviewView coordinates
                        Matrix matrix = calculateTransformMatrix(
                            imageProxy.getWidth(),
                            imageProxy.getHeight(),
                            previewView.getWidth(),
                            previewView.getHeight(),
                            imageProxy.getImageInfo().getRotationDegrees()
                        );
                        
                        runOnUiThread(() -> {
                            faceOverlayView.updateFaces(detectedFaces, matrix);
                        });
                    } else {
                        runOnUiThread(() -> {
                            faceOverlayView.updateFaces(new ArrayList<>(), null);
                        });
                    }
                    imageProxy.close();
                })
                .addOnFailureListener(e -> {
                    android.util.Log.e("MainActivity", "Face detection failed", e);
                    detectedFaces.clear();
                    imageProxy.close();
                });
    }
    
    private Matrix calculateTransformMatrix(int imageWidth, int imageHeight, int viewWidth, int viewHeight, int rotation) {
        Matrix matrix = new Matrix();
        
        // Tính scale factor
        float scaleX = (float) viewWidth / imageWidth;
        float scaleY = (float) viewHeight / imageHeight;
        float scale = Math.max(scaleX, scaleY); // fitCenter
        
        // Tính offset để center
        float dx = (viewWidth - imageWidth * scale) / 2f;
        float dy = (viewHeight - imageHeight * scale) / 2f;
        
        matrix.postScale(scale, scale);
        matrix.postTranslate(dx, dy);
        
        // Xử lý rotation nếu cần
        if (rotation == 90 || rotation == 270) {
            matrix.postRotate(rotation, viewWidth / 2f, viewHeight / 2f);
        }
        
        return matrix;
    }

    private void capturePhoto() {
        if (imageCapture == null) return;
        
        // Nếu đang chạy countdown, không cho chụp
        if (isCountdownRunning) return;

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
    
    // Hiển thị menu dropdown cho nút Grid với BottomSheetDialog đẹp hơn
    private void showGridMenu(View anchor) {
        BottomSheetDialog bottomSheet = new BottomSheetDialog(this);
        View sheetView = getLayoutInflater().inflate(R.layout.bottom_sheet_grid_menu, null);
        bottomSheet.setContentView(sheetView);
        
        // Lấy các views
        CardView cardGrid = sheetView.findViewById(R.id.cardGrid);
        CardView cardCountdown = sheetView.findViewById(R.id.cardCountdown);
        SwitchMaterial switchGrid = sheetView.findViewById(R.id.switchGrid);
        SwitchMaterial switchCountdown = sheetView.findViewById(R.id.switchCountdown);
        ImageButton btnCloseMenu = sheetView.findViewById(R.id.btnCloseMenu);
        
        // Cập nhật trạng thái ban đầu
        boolean isGridVisible = gridOverlayView.isGridVisible();
        switchGrid.setChecked(isGridVisible);
        switchCountdown.setChecked(isCountdownMode);
        
        // Nút đóng
        btnCloseMenu.setOnClickListener(v -> bottomSheet.dismiss());
        
        // Grid toggle
        cardGrid.setOnClickListener(v -> {
            boolean newGridState = !isGridVisible;
            switchGrid.setChecked(newGridState);
            gridOverlayView.setShowGrid(newGridState);
            btnGrid.setAlpha(newGridState ? 1.0f : 0.6f);
            
            // Animation feedback
            cardGrid.animate()
                    .scaleX(0.95f)
                    .scaleY(0.95f)
                    .setDuration(100)
                    .withEndAction(() -> cardGrid.animate()
                            .scaleX(1.0f)
                            .scaleY(1.0f)
                            .setDuration(100)
                            .start())
                    .start();
        });
        
        // Countdown toggle
        cardCountdown.setOnClickListener(v -> {
            isCountdownMode = !isCountdownMode;
            switchCountdown.setChecked(isCountdownMode);
            
            // Nếu đang chạy countdown, dừng lại
            if (isCountdownRunning) {
                stopCountdown();
            }
            
            // Animation feedback
            cardCountdown.animate()
                    .scaleX(0.95f)
                    .scaleY(0.95f)
                    .setDuration(100)
                    .withEndAction(() -> cardCountdown.animate()
                            .scaleX(1.0f)
                            .scaleY(1.0f)
                            .setDuration(100)
                            .start())
                    .start();
            
            Toast.makeText(this, isCountdownMode ? "Đã bật Countdown" : "Đã tắt Countdown", Toast.LENGTH_SHORT).show();
        });
        
        // Switch listeners (backup)
        switchGrid.setOnCheckedChangeListener((buttonView, isChecked) -> {
            gridOverlayView.setShowGrid(isChecked);
            btnGrid.setAlpha(isChecked ? 1.0f : 0.6f);
        });
        
        switchCountdown.setOnCheckedChangeListener((buttonView, isChecked) -> {
            isCountdownMode = isChecked;
            if (isCountdownRunning) {
                stopCountdown();
            }
        });
        
        bottomSheet.show();
    }
    
    // Bắt đầu countdown timer với animation đẹp hơn
    private void startCountdown() {
        if (isCountdownRunning || imageCapture == null) return;
        
        isCountdownRunning = true;
        countdownContainer.setVisibility(View.VISIBLE);
        countdownContainer.setAlpha(0f);
        countdownContainer.setScaleX(0.5f);
        countdownContainer.setScaleY(0.5f);
        
        // Animation xuất hiện
        countdownContainer.animate()
                .alpha(1.0f)
                .scaleX(1.0f)
                .scaleY(1.0f)
                .setDuration(300)
                .setInterpolator(new android.view.animation.OvershootInterpolator())
                .start();
        
        countDownTimer = new android.os.CountDownTimer(3000, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                int seconds = (int) (millisUntilFinished / 1000);
                tvCountdown.setText(String.valueOf(seconds));
                
                // Animation scale với bounce effect
                tvCountdown.setScaleX(0.3f);
                tvCountdown.setScaleY(0.3f);
                tvCountdown.setAlpha(0.5f);
                
                tvCountdown.animate()
                        .scaleX(1.0f)
                        .scaleY(1.0f)
                        .alpha(1.0f)
                        .setDuration(300)
                        .setInterpolator(new android.view.animation.OvershootInterpolator(1.5f))
                        .start();
                
                // Ripple effect
                rippleCircle.setScaleX(0.5f);
                rippleCircle.setScaleY(0.5f);
                rippleCircle.setAlpha(0.6f);
                rippleCircle.setVisibility(View.VISIBLE);
                
                rippleCircle.animate()
                        .scaleX(1.5f)
                        .scaleY(1.5f)
                        .alpha(0f)
                        .setDuration(600)
                        .setInterpolator(new android.view.animation.DecelerateInterpolator())
                        .withEndAction(() -> {
                            rippleCircle.setScaleX(0.5f);
                            rippleCircle.setScaleY(0.5f);
                            rippleCircle.setAlpha(0f);
                            rippleCircle.setVisibility(View.GONE);
                        })
                        .start();
                
                // Pulse background
                countdownBg.animate()
                        .scaleX(1.1f)
                        .scaleY(1.1f)
                        .setDuration(200)
                        .withEndAction(() -> countdownBg.animate()
                                .scaleX(1.0f)
                                .scaleY(1.0f)
                                .setDuration(200)
                                .start())
                        .start();
            }
            
            @Override
            public void onFinish() {
                tvCountdown.setText("0");
                
                // Final animation với flash effect
                countdownContainer.animate()
                        .scaleX(1.3f)
                        .scaleY(1.3f)
                        .alpha(0f)
                        .setDuration(300)
                        .setInterpolator(new android.view.animation.AccelerateInterpolator())
                        .withEndAction(() -> {
                            countdownContainer.setVisibility(View.GONE);
                            countdownContainer.setAlpha(1.0f);
                            countdownContainer.setScaleX(1.0f);
                            countdownContainer.setScaleY(1.0f);
                            tvCountdown.setAlpha(1.0f);
                            tvCountdown.setScaleX(1.0f);
                            tvCountdown.setScaleY(1.0f);
                            countdownBg.setScaleX(1.0f);
                            countdownBg.setScaleY(1.0f);
                            isCountdownRunning = false;
                            
                            // Tự động chụp ảnh sau khi countdown kết thúc
                            capturePhoto();
                        })
                        .start();
            }
        };
        
        countDownTimer.start();
    }
    
    // Dừng countdown timer
    private void stopCountdown() {
        if (countDownTimer != null) {
            countDownTimer.cancel();
            countDownTimer = null;
        }
        isCountdownRunning = false;
        
        // Animation ẩn đi
        if (countdownContainer.getVisibility() == View.VISIBLE) {
            countdownContainer.animate()
                    .alpha(0f)
                    .scaleX(0.5f)
                    .scaleY(0.5f)
                    .setDuration(200)
                    .withEndAction(() -> {
                        countdownContainer.setVisibility(View.GONE);
                        countdownContainer.setAlpha(1.0f);
                        countdownContainer.setScaleX(1.0f);
                        countdownContainer.setScaleY(1.0f);
                        tvCountdown.setAlpha(1.0f);
                        tvCountdown.setScaleX(1.0f);
                        tvCountdown.setScaleY(1.0f);
                        countdownBg.setScaleX(1.0f);
                        countdownBg.setScaleY(1.0f);
                        rippleCircle.setVisibility(View.GONE);
                        rippleCircle.setAlpha(0f);
                        rippleCircle.setScaleX(0.5f);
                        rippleCircle.setScaleY(0.5f);
                    })
                    .start();
        } else {
            countdownContainer.setVisibility(View.GONE);
            tvCountdown.setAlpha(1.0f);
            tvCountdown.setScaleX(1.0f);
            tvCountdown.setScaleY(1.0f);
            countdownBg.setScaleX(1.0f);
            countdownBg.setScaleY(1.0f);
            rippleCircle.setVisibility(View.GONE);
        }
    }

    private void showCapturedImage(Uri uri) {
        previewView.setVisibility(PreviewView.GONE);
        btnCapture.setVisibility(ImageButton.GONE);
        btnFilter.setVisibility(ImageButton.VISIBLE);
        btnBack.setVisibility(ImageButton.VISIBLE);
        btnSave.setVisibility(ImageButton.VISIBLE);
        // Ẩn gallery, switch camera và grid ở chế độ xem ảnh đã chụp
        btnGallery.setVisibility(ImageButton.GONE);
        btnSwitchCamera.setVisibility(ImageButton.GONE);
        btnGrid.setVisibility(ImageButton.GONE);
        imageView.setVisibility(ImageView.VISIBLE);

        // Giải phóng bitmap cũ trước khi gán bitmap mới
        recycleBitmap(capturedBitmap);
        recycleBitmap(appliedBitmap);
        
        capturedBitmap = getCorrectBitmap(uri);
        
        // Nếu có sticker được chọn, detect faces lại trên bitmap đã chụp và vẽ sticker
        Bitmap safeSticker = getSafeBitmap(currentStickerBitmap);
        if (capturedBitmap != null && safeSticker != null) {
            detectFacesAndDrawSticker(capturedBitmap);
        } else {
            appliedBitmap = capturedBitmap;
            Glide.with(this).load(appliedBitmap != null ? appliedBitmap : uri).into(imageView);
            setupFilterRecyclerView();
        }
        
        stopCamera();
    }
    
    // Hiển thị ảnh đã chọn từ gallery để chỉnh sửa
    private void showSelectedImage(Uri uri) {
        previewView.setVisibility(PreviewView.GONE);
        btnCapture.setVisibility(ImageButton.GONE);
        btnFilter.setVisibility(ImageButton.VISIBLE);
        btnBack.setVisibility(ImageButton.VISIBLE);
        btnSave.setVisibility(ImageButton.VISIBLE);
        // Ẩn gallery, switch camera và grid ở chế độ xem ảnh đã chọn
        btnGallery.setVisibility(ImageButton.GONE);
        btnSwitchCamera.setVisibility(ImageButton.GONE);
        btnGrid.setVisibility(ImageButton.GONE);
        imageView.setVisibility(ImageView.VISIBLE);

        // Giải phóng bitmap cũ trước khi gán bitmap mới
        recycleBitmap(capturedBitmap);
        recycleBitmap(appliedBitmap);
        
        capturedBitmap = getCorrectBitmap(uri);
        
        // Nếu có sticker được chọn, detect faces lại trên bitmap đã chọn và vẽ sticker
        Bitmap safeSticker = getSafeBitmap(currentStickerBitmap);
        if (capturedBitmap != null && safeSticker != null) {
            detectFacesAndDrawSticker(capturedBitmap);
        } else {
            appliedBitmap = capturedBitmap;
            Glide.with(this).load(appliedBitmap != null ? appliedBitmap : uri).into(imageView);
            setupFilterRecyclerView();
        }
        
        stopCamera();
    }
    
    private void detectFacesAndDrawSticker(Bitmap bitmap) {
        // Kiểm tra bitmap an toàn
        Bitmap safeBitmap = getSafeBitmap(bitmap);
        if (safeBitmap == null) {
            appliedBitmap = null;
            return;
        }
        
        // Detect faces lại trên bitmap đã chụp để đảm bảo chính xác
        InputImage image = InputImage.fromBitmap(safeBitmap, 0);
        
        faceDetector.process(image)
                .addOnSuccessListener(faces -> {
                    capturedFaceRects.clear(); // Clear trước
                    List<android.graphics.Rect> faceRects = new ArrayList<>();
                    for (Face face : faces) {
                        faceRects.add(face.getBoundingBox());
                        capturedFaceRects.add(face.getBoundingBox()); // Lưu lại để dùng sau
                    }
                    
                    Bitmap safeSticker = getSafeBitmap(currentStickerBitmap);
                    if (!faceRects.isEmpty() && safeSticker != null) {
                        appliedBitmap = drawStickerOnBitmap(safeBitmap, faceRects);
                    } else {
                        appliedBitmap = safeBitmap;
                    }
                    
                    Glide.with(this).load(appliedBitmap).into(imageView);
                    setupFilterRecyclerView();
                })
                .addOnFailureListener(e -> {
                    android.util.Log.e("MainActivity", "Face detection on captured image failed", e);
                    capturedFaceRects.clear();
                    // Sử dụng safeBitmap đã được định nghĩa ở đầu method
                    appliedBitmap = safeBitmap;
                    Glide.with(this).load(appliedBitmap).into(imageView);
                    setupFilterRecyclerView();
                });
    }
    
    private Bitmap drawStickerOnBitmap(Bitmap originalBitmap, List<android.graphics.Rect> faceRects) {
        Bitmap safeOriginal = getSafeBitmap(originalBitmap);
        Bitmap safeSticker = getSafeBitmap(currentStickerBitmap);
        
        if (safeOriginal == null || safeSticker == null || faceRects == null || faceRects.isEmpty()) {
            return safeOriginal;
        }
        
        // Tạo bitmap mới để vẽ
        Bitmap resultBitmap = Bitmap.createBitmap(
            safeOriginal.getWidth(),
            safeOriginal.getHeight(),
            Bitmap.Config.ARGB_8888
        );
        
        android.graphics.Canvas canvas = new android.graphics.Canvas(resultBitmap);
        
        // Vẽ ảnh gốc
        canvas.drawBitmap(safeOriginal, 0, 0, null);
        
        // Vẽ sticker lên từng khuôn mặt (faceRects đã ở đúng coordinates của bitmap)
        for (android.graphics.Rect faceRect : faceRects) {
            int left = faceRect.left;
            int top = faceRect.top;
            int right = faceRect.right;
            int bottom = faceRect.bottom;
            
            // Tính kích thước sticker (lớn hơn face 20%)
            int faceWidth = Math.abs(right - left);
            int faceHeight = Math.abs(bottom - top);
            int stickerSize = (int) (Math.max(faceWidth, faceHeight) * 1.2f);
            
            // Vị trí vẽ sticker tùy theo loại
            int x = Math.min(left, right) + (faceWidth - stickerSize) / 2;
            int y;
            if (currentStickerResId != null) {
                FaceOverlayView.StickerType stickerType = getStickerType(currentStickerResId);
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
            } else {
                // Sticker thông thường ở giữa mặt
                y = Math.min(top, bottom) + (faceHeight - stickerSize) / 2;
            }
            
            // Đảm bảo không vẽ ngoài bounds
            if (x < 0) x = 0;
            if (y < 0) y = 0;
            if (x + stickerSize > safeOriginal.getWidth()) {
                stickerSize = safeOriginal.getWidth() - x;
            }
            if (y + stickerSize > safeOriginal.getHeight()) {
                stickerSize = safeOriginal.getHeight() - y;
            }
            
            if (stickerSize > 0) {
                // Scale sticker bitmap
                Bitmap scaledSticker = Bitmap.createScaledBitmap(
                    safeSticker,
                    stickerSize,
                    stickerSize,
                    true
                );
                
                // Vẽ sticker lên canvas
                canvas.drawBitmap(scaledSticker, x, y, null);
                
                // Giải phóng scaled sticker sau khi dùng (nếu không phải là bitmap gốc)
                if (scaledSticker != safeSticker) {
                    recycleBitmap(scaledSticker);
                }
            }
        }
        
        return resultBitmap;
    }

    private void setupFilterRecyclerView() {
        Bitmap previewSource = BitmapFactory.decodeResource(getResources(), R.drawable.default_preview);
        Bitmap preview = Bitmap.createScaledBitmap(previewSource, 100, 100, true);

        List<FilterItem> filterList = new ArrayList<>();
        // Basic filters
        filterList.add(new FilterItem("Normal", preview, NORMAL));
        filterList.add(new FilterItem("Gray", FilterUtils.filterGray(preview), GRAY));
        
        // Instagram-like filters
        filterList.add(new FilterItem("Clarendon", FilterUtils.filterClarendon(preview), CLARENDON));
        filterList.add(new FilterItem("Gingham", FilterUtils.filterGingham(preview), GINGHAM));
        filterList.add(new FilterItem("Moon", FilterUtils.filterMoon(preview), MOON));
        filterList.add(new FilterItem("Lark", FilterUtils.filterLark(preview), LARK));
        filterList.add(new FilterItem("Reyes", FilterUtils.filterReyes(preview), REYES));
        filterList.add(new FilterItem("Juno", FilterUtils.filterJuno(preview), JUNO));
        filterList.add(new FilterItem("Slumber", FilterUtils.filterSlumber(preview), SLUMBER));
        filterList.add(new FilterItem("Crema", FilterUtils.filterCrema(preview), CREMA));
        filterList.add(new FilterItem("Ludwig", FilterUtils.filterLudwig(preview), LUDWIG));
        filterList.add(new FilterItem("Aden", FilterUtils.filterAden(preview), ADEN));
        filterList.add(new FilterItem("Perpetua", FilterUtils.filterPerpetua(preview), PERPETUA));
        filterList.add(new FilterItem("Amaro", FilterUtils.filterAmaro(preview), AMARO));
        filterList.add(new FilterItem("Mayfair", FilterUtils.filterMayfair(preview), MAYFAIR));
        filterList.add(new FilterItem("Rise", FilterUtils.filterRise(preview), RISE));
        filterList.add(new FilterItem("Valencia", FilterUtils.filterValencia(preview), VALENCIA));
        filterList.add(new FilterItem("X-Pro II", FilterUtils.filterXProII(preview), XPROII));
        filterList.add(new FilterItem("Lo-Fi", FilterUtils.filterLoFi(preview), LOFI));
        filterList.add(new FilterItem("Sierra", FilterUtils.filterSierra(preview), SIERRA));
        filterList.add(new FilterItem("Willow", FilterUtils.filterWillow(preview), WILLOW));
        
        // Classic filters
        filterList.add(new FilterItem("Sepia", FilterUtils.filterSepia(preview), SEPIA));
        filterList.add(new FilterItem("Bright", FilterUtils.filterBright(preview, 1.2f), BRIGHT));
        filterList.add(new FilterItem("Invert", FilterUtils.filterInvert(preview), INVERT));
        filterList.add(new FilterItem("Contrast", FilterUtils.filterContrast(preview, 1.3f), CONTRAST));
        filterList.add(new FilterItem("Vintage", FilterUtils.filterVintage(preview), VINTAGE));

        FilterAdapter adapter = new FilterAdapter(this, filterList, filter -> {
            Bitmap safeCaptured = getSafeBitmap(capturedBitmap);
            if (safeCaptured != null) {
                Bitmap filteredBitmap = safeCaptured;
                switch (filter.type) {
                    case NORMAL: filteredBitmap = safeCaptured; break;
                    case GRAY: filteredBitmap = FilterUtils.filterGray(safeCaptured); break;
                    case SEPIA: filteredBitmap = FilterUtils.filterSepia(safeCaptured); break;
                    case BRIGHT: filteredBitmap = FilterUtils.filterBright(safeCaptured, 1.2f); break;
                    case INVERT: filteredBitmap = FilterUtils.filterInvert(safeCaptured); break;
                    case CONTRAST: filteredBitmap = FilterUtils.filterContrast(safeCaptured, 1.3f); break;
                    case HUE: filteredBitmap = FilterUtils.filterHue(safeCaptured, 45f); break;
                    case VINTAGE: filteredBitmap = FilterUtils.filterVintage(safeCaptured); break;
                    // Instagram-like filters
                    case CLARENDON: filteredBitmap = FilterUtils.filterClarendon(safeCaptured); break;
                    case GINGHAM: filteredBitmap = FilterUtils.filterGingham(safeCaptured); break;
                    case MOON: filteredBitmap = FilterUtils.filterMoon(safeCaptured); break;
                    case LARK: filteredBitmap = FilterUtils.filterLark(safeCaptured); break;
                    case REYES: filteredBitmap = FilterUtils.filterReyes(safeCaptured); break;
                    case JUNO: filteredBitmap = FilterUtils.filterJuno(safeCaptured); break;
                    case SLUMBER: filteredBitmap = FilterUtils.filterSlumber(safeCaptured); break;
                    case CREMA: filteredBitmap = FilterUtils.filterCrema(safeCaptured); break;
                    case LUDWIG: filteredBitmap = FilterUtils.filterLudwig(safeCaptured); break;
                    case ADEN: filteredBitmap = FilterUtils.filterAden(safeCaptured); break;
                    case PERPETUA: filteredBitmap = FilterUtils.filterPerpetua(safeCaptured); break;
                    case AMARO: filteredBitmap = FilterUtils.filterAmaro(safeCaptured); break;
                    case MAYFAIR: filteredBitmap = FilterUtils.filterMayfair(safeCaptured); break;
                    case RISE: filteredBitmap = FilterUtils.filterRise(safeCaptured); break;
                    case VALENCIA: filteredBitmap = FilterUtils.filterValencia(safeCaptured); break;
                    case XPROII: filteredBitmap = FilterUtils.filterXProII(safeCaptured); break;
                    case LOFI: filteredBitmap = FilterUtils.filterLoFi(safeCaptured); break;
                    case SIERRA: filteredBitmap = FilterUtils.filterSierra(safeCaptured); break;
                    case WILLOW: filteredBitmap = FilterUtils.filterWillow(safeCaptured); break;
                    default: filteredBitmap = safeCaptured; break;
                }
                
                // Giải phóng appliedBitmap cũ trước khi gán mới
                if (appliedBitmap != null && appliedBitmap != capturedBitmap && appliedBitmap != filteredBitmap) {
                    recycleBitmap(appliedBitmap);
                }
                
                // Nếu có sticker và faces đã detect, vẽ lại sticker lên bitmap đã filter
                Bitmap safeSticker = getSafeBitmap(currentStickerBitmap);
                if (safeSticker != null && !capturedFaceRects.isEmpty()) {
                    appliedBitmap = drawStickerOnBitmap(filteredBitmap, capturedFaceRects);
                } else {
                    appliedBitmap = filteredBitmap;
                }
                
                imageView.setImageBitmap(appliedBitmap);
            }
        });

        rvFilters.setAdapter(adapter);
        rvFilters.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        
        rvFilters.setVisibility(RecyclerView.VISIBLE);
        
        // Trigger animation bằng cách notify adapter (animation sẽ chạy trong onBindViewHolder)
        adapter.notifyDataSetChanged();
    }

    // Toggle filter RecyclerView với animation
    private void toggleFilterRecyclerView() {
        boolean isVisible = rvFilters.getVisibility() == RecyclerView.VISIBLE;
        
        if (isVisible) {
            // Ẩn với animation slide out
            Animation slideOut = AnimationUtils.loadAnimation(this, R.anim.slide_out_bottom);
            slideOut.setAnimationListener(new Animation.AnimationListener() {
                @Override
                public void onAnimationStart(Animation animation) {}

                @Override
                public void onAnimationEnd(Animation animation) {
                    rvFilters.setVisibility(RecyclerView.GONE);
                }

                @Override
                public void onAnimationRepeat(Animation animation) {}
            });
            rvFilters.startAnimation(slideOut);
        } else {
            // Hiển thị với animation slide in
            rvFilters.setVisibility(RecyclerView.VISIBLE);
            Animation slideIn = AnimationUtils.loadAnimation(this, R.anim.slide_in_bottom);
            slideIn.setAnimationListener(new Animation.AnimationListener() {
                @Override
                public void onAnimationStart(Animation animation) {
                    // Trigger animation cho từng filter item
                    if (rvFilters.getAdapter() != null) {
                        rvFilters.getAdapter().notifyDataSetChanged();
                    }
                }

                @Override
                public void onAnimationEnd(Animation animation) {}

                @Override
                public void onAnimationRepeat(Animation animation) {}
            });
            rvFilters.startAnimation(slideIn);
        }
    }

    private void backToCamera() {
        // Dừng countdown nếu đang chạy
        if (isCountdownRunning) {
            stopCountdown();
        }
        
        imageView.setVisibility(ImageView.GONE);
        btnFilter.setVisibility(ImageButton.GONE);
        btnBack.setVisibility(ImageButton.GONE);
        btnSave.setVisibility(ImageButton.GONE);
        // Hiển thị gallery, switch camera và grid ở chế độ camera preview
        btnGallery.setVisibility(ImageButton.VISIBLE);
        btnSwitchCamera.setVisibility(ImageButton.VISIBLE);
        btnGrid.setVisibility(ImageButton.VISIBLE);
        rvFilters.setVisibility(RecyclerView.GONE);
        previewView.setVisibility(PreviewView.VISIBLE);
        btnCapture.setVisibility(ImageButton.VISIBLE);
        
        // Giải phóng bitmap khi quay lại camera (giữ lại capturedBitmap để có thể filter lại)
        // Chỉ giải phóng appliedBitmap nếu nó khác capturedBitmap
        if (appliedBitmap != null && appliedBitmap != capturedBitmap) {
            recycleBitmap(appliedBitmap);
            appliedBitmap = null;
        }

        if (hasCameraPermission()) startCamera();
    }

    // Lưu ảnh vào thư viện và tự động refresh gallery
    private boolean saveBitmapToGallery(@NonNull Bitmap bitmap) {
        try {
            ContentValues contentValues = new ContentValues();
            contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, "CameraX_" + System.currentTimeMillis() + ".jpg");
            contentValues.put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg");

            Uri uri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues);
            if (uri == null) return false;

            OutputStream os = getContentResolver().openOutputStream(uri);
            if (os == null) return false;

            boolean ok = bitmap.compress(Bitmap.CompressFormat.JPEG, 95, os);
            os.flush();
            os.close();

            if (ok) {
                // 🔄 Quét lại file mới để cập nhật MediaStore ngay lập tức
                MediaScannerConnection.scanFile(
                        this,
                        new String[]{ uri.getPath() },
                        new String[]{"image/jpeg"},
                        (path, scannedUri) -> runOnUiThread(() -> {
                            loadLatestGalleryImage(); // load lại ảnh mới nhất ngay
                            Toast.makeText(this, "Ảnh đã lưu và cập nhật vào thư viện!", Toast.LENGTH_SHORT).show();
                        })
                );
            }

            return ok;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    private void stopCamera() {
        try {
            if (cameraProvider != null) {
                cameraProvider.unbindAll();
            } else {
                ProcessCameraProvider provider = ProcessCameraProvider.getInstance(this).get();
                provider.unbindAll();
            }
        } catch (Exception ignored) {}
    }

    // -------------------- GALLERY LOGIC --------------------

    // Lấy ảnh mới nhất trong thư viện và hiển thị lên nút gallery
    private void loadLatestGalleryImage() {
        new Thread(() -> {
            Uri collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
            String[] projection = { MediaStore.Images.Media._ID };
            String sortOrder = MediaStore.Images.Media.DATE_ADDED + " DESC";

            try (Cursor cursor = getContentResolver().query(
                    collection,
                    projection,
                    null,
                    null,
                    sortOrder
            )) {
                if (cursor != null && cursor.moveToFirst()) {
                    int idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID);
                    long imageId = cursor.getLong(idColumn);
                    Uri imageUri = ContentUris.withAppendedId(
                            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                            imageId
                    );

                    Uri finalImageUri = imageUri;
                    runOnUiThread(() -> {
                        // Tính toán kích thước pixel từ dp - bằng với kích thước background (60dp)
                        float density = getResources().getDisplayMetrics().density;
                        int buttonSizeInPixels = Math.round(60 * density); // 60dp = kích thước background
                        
                        // Bỏ padding để ảnh fill toàn bộ background
                        btnGallery.setPadding(0, 0, 0, 0);
                        btnGallery.setScaleType(android.widget.ImageView.ScaleType.CENTER_CROP);
                        
                        Glide.with(MainActivity.this)
                                .load(finalImageUri)
                                .override(buttonSizeInPixels, buttonSizeInPixels) // Scale về đúng kích thước background (60dp)
                                .circleCrop() // Crop thành hình tròn để khớp với background oval
                                .skipMemoryCache(true)
                                .diskCacheStrategy(com.bumptech.glide.load.engine.DiskCacheStrategy.NONE)
                                .into(btnGallery);
                    });
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    private void pickImageFromGallery() {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        intent.setType("image/*");
        if (intent.resolveActivity(getPackageManager()) != null) {
            startActivityForResult(intent, REQUEST_PICK_IMAGE);
        } else {
            Toast.makeText(this, "Không tìm thấy ứng dụng Thư viện", Toast.LENGTH_SHORT).show();
        }
    }

    // Mở Google Account Picker để chọn tài khoản
    private void signInToGoogle() {
        GoogleAccountCredential credential = GoogleDriveHelper.getCredential(this);
        Intent intent = credential.newChooseAccountIntent();
        startActivityForResult(intent, REQUEST_GOOGLE_SIGN_IN);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        
        // Xử lý kết quả chọn ảnh từ gallery
        if (requestCode == REQUEST_PICK_IMAGE && resultCode == RESULT_OK && data != null) {
            Uri selectedImageUri = data.getData();
            if (selectedImageUri != null) {
                showSelectedImage(selectedImageUri);
            }
            return;
        }
        
        // Xử lý kết quả authorization từ UserRecoverableAuthIOException
        if (requestCode == REQUEST_AUTHORIZE && resultCode == RESULT_OK) {
            android.util.Log.d("MainActivity", "Authorization successful, retrying sync");
            // Thử lại sync với bitmap đã lưu
            if (pendingSyncBitmap != null) {
                Bitmap tempBitmap = pendingSyncBitmap;
                pendingSyncBitmap = null;
                appliedBitmap = tempBitmap;
                syncToDrive();
            }
            return;
        }
        
        if (requestCode == REQUEST_GOOGLE_SIGN_IN && resultCode == RESULT_OK) {
            String accountName = null;
            
            if (data != null) {
                // Cách 1: Lấy từ Account object trong Intent với key "account"
                Account account = data.getParcelableExtra("account");
                if (account != null) {
                    accountName = account.name;
                }
                
                // Cách 2: Thử với key khác
                if (accountName == null) {
                    account = data.getParcelableExtra("android.accounts.Account");
                    if (account != null) {
                        accountName = account.name;
                    }
                }
                
                // Cách 3: Lấy từ string extra với key chính xác
                if (accountName == null) {
                    accountName = data.getStringExtra(AccountManager.KEY_ACCOUNT_NAME);
                }
                
                // Cách 4: Thử với các key string khác
                if (accountName == null) {
                    accountName = data.getStringExtra("account_name");
                }
                if (accountName == null) {
                    accountName = data.getStringExtra("android.accounts.AccountManager.KEY_ACCOUNT_NAME");
                }
            }
            
            // Cách 5: Nếu không có trong data, tạo lại credential và set account từ data
            if (accountName == null && data != null) {
                try {
                    GoogleAccountCredential tempCredential = GoogleDriveHelper.getCredential(this);
                    Account account = data.getParcelableExtra("account");
                    if (account == null) {
                        account = data.getParcelableExtra("android.accounts.Account");
                    }
                    if (account != null) {
                        tempCredential.setSelectedAccountName(account.name);
                        accountName = account.name;
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            
            // Cách 6: Lấy từ credential đã được set trước đó (nếu có)
            if (accountName == null) {
                GoogleAccountCredential tempCredential = GoogleDriveHelper.getCredential(this);
                accountName = tempCredential.getSelectedAccountName();
            }
            
            if (accountName != null && !accountName.isEmpty()) {
                getSharedPreferences("camera_prefs", MODE_PRIVATE)
                        .edit()
                        .putString(PREF_ACCOUNT_NAME, accountName)
                        .apply();
                try {
                    driveHelper = new GoogleDriveHelper(this, accountName);
                    Toast.makeText(this, " Đã kết nối Google Drive: " + accountName, Toast.LENGTH_SHORT).show();
                    
                    // Cập nhật trạng thái nút Google Drive
                    updateGoogleDriveButton();
                    
                    // Nếu có bitmap đang chờ sync, tự động sync lại
                    if (pendingSyncBitmap != null) {
                        android.util.Log.d("MainActivity", "Auto-retrying sync after successful login");
                        Bitmap tempBitmap = pendingSyncBitmap;
                        pendingSyncBitmap = null;
                        appliedBitmap = tempBitmap;
                        // Delay một chút để đảm bảo driveHelper đã sẵn sàng
                        new android.os.Handler().postDelayed(() -> syncToDrive(), 500);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    Toast.makeText(this, "Lỗi kết nối Google Drive: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    updateGoogleDriveButton();
                }
            } else {
                // Debug: Log để xem data có gì
                android.util.Log.e("MainActivity", "Không thể lấy account name. Data: " + (data != null ? data.toString() : "null"));
                if (data != null && data.getExtras() != null) {
                    android.util.Log.e("MainActivity", "Extras keys: " + data.getExtras().keySet().toString());
                    // Log tất cả các values
                    for (String key : data.getExtras().keySet()) {
                        android.util.Log.e("MainActivity", "Key: " + key + " = " + data.getExtras().get(key));
                    }
                }
                Toast.makeText(this, "Không thể lấy thông tin tài khoản. Vui lòng thử lại.", Toast.LENGTH_LONG).show();
            }
        }
    }

    // Đồng bộ ảnh lên Google Drive
    private void syncToDrive() {
        if (driveHelper == null) {
            // Chưa đăng nhập, yêu cầu đăng nhập
            new AlertDialog.Builder(this)
                    .setTitle("Đăng nhập Google Drive")
                    .setMessage("Bạn cần đăng nhập Google để đồng bộ ảnh lên Drive.\nẢnh sẽ tự động đồng bộ sau khi đăng nhập.")
                    .setPositiveButton("Đăng nhập", (dialog, which) -> signInToGoogle())
                    .setNegativeButton("Bỏ qua", null)
                    .show();
            return;
        }

        if (appliedBitmap == null) {
            return;
        }

        // Hiển thị ProgressDialog
        runOnUiThread(() -> {
            if (uploadProgressDialog == null || !uploadProgressDialog.isShowing()) {
                uploadProgressDialog = new android.app.ProgressDialog(this);
                uploadProgressDialog.setMessage("Đang tải lên Google Drive...");
                uploadProgressDialog.setProgressStyle(android.app.ProgressDialog.STYLE_SPINNER);
                uploadProgressDialog.setIndeterminate(true);
                uploadProgressDialog.setCancelable(false);
                uploadProgressDialog.show();
            }
        });

        // Upload trong background thread
        new Thread(() -> {
            try {
                android.util.Log.d("MainActivity", "Starting sync to Drive...");
                android.util.Log.d("MainActivity", "driveHelper: " + (driveHelper != null ? "OK" : "NULL"));
                android.util.Log.d("MainActivity", "appliedBitmap: " + (appliedBitmap != null ? "OK" : "NULL"));
                
                if (driveHelper == null) {
                    throw new Exception("Drive helper is null");
                }
                
                Bitmap safeBitmap = getSafeBitmap(appliedBitmap);
                if (safeBitmap == null) {
                    throw new Exception("Bitmap is null or recycled");
                }
                
                String fileName = "CameraX_" + System.currentTimeMillis() + ".jpg";
                android.util.Log.d("MainActivity", "Uploading file: " + fileName);
                
                String fileId = driveHelper.uploadImage(safeBitmap, fileName);
                
                if (fileId == null || fileId.isEmpty()) {
                    throw new Exception("Upload returned null or empty file ID");
                }
                
                android.util.Log.d("MainActivity", "Upload successful. File ID: " + fileId);
                runOnUiThread(() -> {
                    // Đóng ProgressDialog
                    if (uploadProgressDialog != null && uploadProgressDialog.isShowing()) {
                        uploadProgressDialog.dismiss();
                    }
                    Toast.makeText(this, " Đã đồng bộ lên Google Drive!", Toast.LENGTH_SHORT).show();
                });
            } catch (IOException e) {
                e.printStackTrace();
                android.util.Log.e("MainActivity", "IOException during sync", e);
                String errorMsg = e.getMessage();
                
                // Đóng ProgressDialog khi có lỗi
                runOnUiThread(() -> {
                    if (uploadProgressDialog != null && uploadProgressDialog.isShowing()) {
                        uploadProgressDialog.dismiss();
                    }
                });
                
                // Kiểm tra nếu là lỗi authentication
                if (errorMsg != null && (errorMsg.contains("AUTH_REQUIRED") || 
                                         errorMsg.contains("Authentication required") ||
                                         errorMsg.contains("Authentication failed"))) {
                    android.util.Log.d("MainActivity", "Authentication required, triggering auth flow");
                    pendingSyncBitmap = appliedBitmap; // Lưu bitmap để sync lại sau khi auth
                    
                    // Lấy UserRecoverableAuthIOException từ cause
                    Throwable cause = e.getCause();
                    if (cause instanceof com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException) {
                        com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException authException = 
                            (com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException) cause;
                        
                        android.content.Intent authIntent = authException.getIntent();
                        if (authIntent != null) {
                            runOnUiThread(() -> {
                                startActivityForResult(authIntent, REQUEST_AUTHORIZE);
                                Toast.makeText(this, "Đang xác thực Google Drive...", Toast.LENGTH_SHORT).show();
                            });
                            return;
                        }
                    }
                    
                    // Nếu không có Intent, yêu cầu đăng nhập lại
                    runOnUiThread(() -> {
                        new AlertDialog.Builder(this)
                                .setTitle("Cần xác thực")
                                .setMessage("Vui lòng đăng nhập lại Google Drive để tiếp tục đồng bộ.")
                                .setPositiveButton("Đăng nhập", (dialog, which) -> {
                                    pendingSyncBitmap = appliedBitmap;
                                    signInToGoogle();
                                })
                                .setNegativeButton("Hủy", null)
                                .show();
                    });
                    return;
                }
                
                // Kiểm tra lỗi 403 (Permission denied) - thường do email chưa được thêm vào Test Users
                if (errorMsg != null && errorMsg.contains("Permission denied")) {
                    final String finalErrorMsg = errorMsg; // Tạo biến final để dùng trong lambda
                    runOnUiThread(() -> {
                        new AlertDialog.Builder(this)
                                .setTitle("Lỗi quyền truy cập")
                                .setMessage(finalErrorMsg + "\n\n" +
                                        "HƯỚNG DẪN KHẮC PHỤC:\n\n" +
                                        "1. Vào Google Cloud Console\n" +
                                        "2. APIs & Services > OAuth consent screen\n" +
                                        "3. Thêm email vào mục 'Test users'\n" +
                                        "4. Hoặc publish app để tất cả users có thể dùng\n\n" +
                                        "Nếu đã thêm email, vui lòng đăng xuất và đăng nhập lại.")
                                .setPositiveButton("Đăng xuất", (dialog, which) -> {
                                    // Đăng xuất
                                    getSharedPreferences("camera_prefs", MODE_PRIVATE)
                                            .edit()
                                            .remove(PREF_ACCOUNT_NAME)
                                            .apply();
                                    driveHelper = null;
                                    updateGoogleDriveButton();
                                    Toast.makeText(this, "Đã đăng xuất. Vui lòng đăng nhập lại sau khi thêm email vào Test Users.", Toast.LENGTH_LONG).show();
                                })
                                .setNegativeButton("OK", null)
                                .show();
                    });
                    return;
                }
                
                if (errorMsg == null || errorMsg.isEmpty()) {
                    errorMsg = "Lỗi kết nối khi đồng bộ";
                }
                final String finalErrorMsg = errorMsg;
                runOnUiThread(() -> {
                    // Hiển thị dialog thay vì Toast để người dùng có thể đọc kỹ hơn
                    new AlertDialog.Builder(this)
                            .setTitle("Lỗi đồng bộ")
                            .setMessage(finalErrorMsg)
                            .setPositiveButton("OK", null)
                            .show();
                });
            } catch (Exception e) {
                e.printStackTrace();
                android.util.Log.e("MainActivity", "Exception during sync", e);
                String errorMsg = e.getMessage();
                if (errorMsg == null || errorMsg.isEmpty()) {
                    errorMsg = "Lỗi không xác định";
                }
                final String finalErrorMsg = errorMsg;
                runOnUiThread(() -> {
                    // Đóng ProgressDialog khi có lỗi
                    if (uploadProgressDialog != null && uploadProgressDialog.isShowing()) {
                        uploadProgressDialog.dismiss();
                    }
                    Toast.makeText(this, " Lỗi: " + finalErrorMsg, Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateCameraState();
        loadLatestGalleryImage();
        // Cập nhật trạng thái nút Google Drive khi quay lại activity
        updateGoogleDriveButton();
    }

    // ==================== STICKER METHODS ====================
    
    // Kiểm tra xem sticker có phải là hat không
    private boolean isHatSticker(int resId) {
        if (resId == R.drawable.chinese_long_hat_sticker || 
            resId == R.drawable.chinese_silk_hat_sticker) {
            return true;
        }
        return false;
    }
    
    // Xác định loại sticker
    private FaceOverlayView.StickerType getStickerType(int resId) {
        if (resId == R.drawable.chinese_long_hat_sticker || 
            resId == R.drawable.chinese_silk_hat_sticker) {
            return FaceOverlayView.StickerType.HAT;
        } else if (resId == R.drawable.necklace_sticker) {
            return FaceOverlayView.StickerType.NECK;
        } else {
            return FaceOverlayView.StickerType.FACE;
        }
    }
    
    private void showStickerSelector() {
        BottomSheetDialog bottomSheet = new BottomSheetDialog(this);
        View sheetView = getLayoutInflater().inflate(R.layout.bottom_sheet_sticker, null);
        bottomSheet.setContentView(sheetView);
        
        RecyclerView rvStickers = sheetView.findViewById(R.id.rvStickers);
        ImageButton btnClose = sheetView.findViewById(R.id.btnCloseSticker);
        
        btnClose.setOnClickListener(v -> bottomSheet.dismiss());
        
        // Danh sách sticker
        List<Integer> stickerList = new ArrayList<>();
        stickerList.add(R.drawable.man_face);
        stickerList.add(R.drawable.sunglass_sticker);
        stickerList.add(R.drawable.chinese_long_hat_sticker);
        stickerList.add(R.drawable.chinese_silk_hat_sticker);
        stickerList.add(R.drawable.dog_sticker);
        stickerList.add(R.drawable.necklace_sticker);
        
        StickerAdapter adapter = new StickerAdapter(this, stickerList, stickerResId -> {
            // Nếu click lại sticker đang active thì tắt nó đi
            if (currentStickerResId != null && currentStickerResId.equals(stickerResId)) {
                // Tắt sticker - giải phóng bitmap cũ
                recycleBitmap(currentStickerBitmap);
                currentStickerBitmap = null;
                currentStickerResId = null;
                faceOverlayView.setSticker(null);
                
                // Nếu đang xem ảnh đã chụp, cập nhật lại ảnh không có sticker
                Bitmap safeCaptured = getSafeBitmap(capturedBitmap);
                if (safeCaptured != null) {
                    // Giữ lại filter hiện tại nhưng bỏ sticker
                    // Nếu appliedBitmap khác capturedBitmap (có filter), giữ filter đó
                    // Nếu không, dùng capturedBitmap gốc
                    Bitmap safeApplied = getSafeBitmap(appliedBitmap);
                    if (safeApplied != null && safeApplied != safeCaptured) {
                        // Có filter đang áp dụng, chỉ cần bỏ sticker đi
                        // Sẽ cần detect lại filter, nhưng để đơn giản thì dùng capturedBitmap
                        if (appliedBitmap != safeCaptured) {
                            recycleBitmap(appliedBitmap);
                        }
                        appliedBitmap = safeCaptured;
                    } else {
                        appliedBitmap = safeCaptured;
                    }
                    imageView.setImageBitmap(appliedBitmap);
                }
                
                bottomSheet.dismiss();
                Toast.makeText(this, "Đã tắt sticker", Toast.LENGTH_SHORT).show();
            } else {
                // Chọn sticker mới - giải phóng sticker cũ trước
                recycleBitmap(currentStickerBitmap);
                
                currentStickerResId = stickerResId;
                currentStickerBitmap = BitmapFactory.decodeResource(getResources(), stickerResId);
                FaceOverlayView.StickerType stickerType = getStickerType(stickerResId);
                faceOverlayView.setSticker(currentStickerBitmap, stickerType);
                
                // Nếu đang xem ảnh đã chụp, vẽ lại sticker lên ảnh
                Bitmap safeCaptured = getSafeBitmap(capturedBitmap);
                if (safeCaptured != null && !capturedFaceRects.isEmpty()) {
                    // Giải phóng appliedBitmap cũ nếu khác capturedBitmap
                    if (appliedBitmap != null && appliedBitmap != safeCaptured) {
                        recycleBitmap(appliedBitmap);
                    }
                    appliedBitmap = drawStickerOnBitmap(safeCaptured, capturedFaceRects);
                    imageView.setImageBitmap(appliedBitmap);
                }
                
                bottomSheet.dismiss();
                Toast.makeText(this, "Đã chọn sticker", Toast.LENGTH_SHORT).show();
            }
        });
        
        rvStickers.setAdapter(adapter);
        rvStickers.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        
        bottomSheet.show();
    }

    // Cập nhật trạng thái nút Google Drive
    private void updateGoogleDriveButton() {
        if (btnGoogleDrive == null) return;
        
        boolean isLoggedIn = driveHelper != null;
        
        // Cập nhật độ mờ của nút
        if (isLoggedIn) {
            btnGoogleDrive.setAlpha(1.0f); // Hiển thị đầy đủ khi đã đăng nhập
        } else {
            btnGoogleDrive.setAlpha(0.6f); // Mờ hơn khi chưa đăng nhập
        }
        
        // Cập nhật indicator
        if (driveStatusIndicator != null) {
            driveStatusIndicator.setVisibility(isLoggedIn ? View.VISIBLE : View.GONE);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        
        // Dừng countdown nếu đang chạy
        stopCountdown();
        
        // Đóng ProgressDialog nếu đang hiển thị
        if (uploadProgressDialog != null && uploadProgressDialog.isShowing()) {
            uploadProgressDialog.dismiss();
        }
        
        // Giải phóng tất cả bitmap để tránh memory leak
        recycleBitmap(capturedBitmap);
        recycleBitmap(appliedBitmap);
        recycleBitmap(currentStickerBitmap);
        recycleBitmap(pendingSyncBitmap);
        
        capturedBitmap = null;
        appliedBitmap = null;
        currentStickerBitmap = null;
        pendingSyncBitmap = null;
        
        // Stop camera
        stopCamera();
        
        cameraExecutor.shutdown();
        if (faceDetector != null) {
            faceDetector.close();
        }
    }
}
