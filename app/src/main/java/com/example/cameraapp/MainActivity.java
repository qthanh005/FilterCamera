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
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;
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

    private ImageButton btnCapture, btnFilter, btnBack, btnSave, btnGallery, btnSwitchCamera;
    private ImageView imageView;
    private TextView tvPermissionStatus;
    private RecyclerView rvFilters;

    private Bitmap capturedBitmap = null;
    private Bitmap appliedBitmap = null;

    // Google Drive
    private static final int REQUEST_GOOGLE_SIGN_IN = 1001;
    private static final int REQUEST_AUTHORIZE = 1002;
    private static final String PREF_ACCOUNT_NAME = "accountName";
    private GoogleDriveHelper driveHelper;
    private Bitmap pendingSyncBitmap = null; // Lưu bitmap đang chờ sync khi cần auth

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
        imageView = findViewById(R.id.imageView);
        tvPermissionStatus = findViewById(R.id.tvPermissionStatus);
        rvFilters = findViewById(R.id.rvFilters);

        cameraExecutor = Executors.newSingleThreadExecutor();

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

        updateCameraState();
        loadLatestGalleryImage();

        // Các sự kiện click
        btnCapture.setOnClickListener(v -> capturePhoto());
        btnBack.setOnClickListener(v -> backToCamera());
        btnFilter.setOnClickListener(v -> {
            if (capturedBitmap != null) {
                toggleFilterRecyclerView();
            }
        });
        btnSave.setOnClickListener(v -> {
            if (appliedBitmap != null) {
                boolean ok = saveBitmapToGallery(appliedBitmap);
                Toast.makeText(MainActivity.this, ok ? "Ảnh đã lưu!" : "Lưu ảnh thất bại", Toast.LENGTH_SHORT).show();
                if (ok) {
                    // Tự động đồng bộ lên Google Drive sau khi lưu thành công
                    syncToDrive();
                    backToCamera();
                }
            }
        });
        btnGallery.setOnClickListener(v -> openGalleryApp());
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
        previewView.setVisibility(PreviewView.GONE);
        btnCapture.setVisibility(ImageButton.GONE);
        btnFilter.setVisibility(ImageButton.VISIBLE);
        btnBack.setVisibility(ImageButton.VISIBLE);
        btnSave.setVisibility(ImageButton.VISIBLE);
        // Ẩn gallery và switch camera ở chế độ xem ảnh đã chụp
        btnGallery.setVisibility(ImageButton.GONE);
        btnSwitchCamera.setVisibility(ImageButton.GONE);
        imageView.setVisibility(ImageView.VISIBLE);

        Glide.with(this).load(uri).into(imageView);

        capturedBitmap = getCorrectBitmap(uri);
        appliedBitmap = capturedBitmap;

        setupFilterRecyclerView();
        stopCamera();
    }

    private void setupFilterRecyclerView() {
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
        imageView.setVisibility(ImageView.GONE);
        btnFilter.setVisibility(ImageButton.GONE);
        btnBack.setVisibility(ImageButton.GONE);
        btnSave.setVisibility(ImageButton.GONE);
        // Hiển thị gallery và switch camera ở chế độ camera preview
        btnGallery.setVisibility(ImageButton.VISIBLE);
        btnSwitchCamera.setVisibility(ImageButton.VISIBLE);
        rvFilters.setVisibility(RecyclerView.GONE);
        previewView.setVisibility(PreviewView.VISIBLE);
        btnCapture.setVisibility(ImageButton.VISIBLE);

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
            ProcessCameraProvider cameraProvider = ProcessCameraProvider.getInstance(this).get();
            cameraProvider.unbindAll();
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

    private void openGalleryApp() {
        Intent intent = new Intent(Intent.ACTION_VIEW, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        intent.setType("image/*");
        if (intent.resolveActivity(getPackageManager()) != null) {
            startActivity(intent);
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
                    Toast.makeText(this, "Đã kết nối với Google Drive: " + accountName, Toast.LENGTH_SHORT).show();
                    
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

        // Upload trong background thread
        new Thread(() -> {
            try {
                android.util.Log.d("MainActivity", "Starting sync to Drive...");
                android.util.Log.d("MainActivity", "driveHelper: " + (driveHelper != null ? "OK" : "NULL"));
                android.util.Log.d("MainActivity", "appliedBitmap: " + (appliedBitmap != null ? "OK" : "NULL"));
                
                if (driveHelper == null) {
                    throw new Exception("Drive helper is null");
                }
                
                if (appliedBitmap == null) {
                    throw new Exception("Bitmap is null");
                }
                
                String fileName = "CameraX_" + System.currentTimeMillis() + ".jpg";
                android.util.Log.d("MainActivity", "Uploading file: " + fileName);
                
                String fileId = driveHelper.uploadImage(appliedBitmap, fileName);
                
                if (fileId == null || fileId.isEmpty()) {
                    throw new Exception("Upload returned null or empty file ID");
                }
                
                android.util.Log.d("MainActivity", "Upload successful. File ID: " + fileId);
                runOnUiThread(() -> {
                    Toast.makeText(this, "Đã đồng bộ lên Google Drive!", Toast.LENGTH_SHORT).show();
                });
            } catch (IOException e) {
                e.printStackTrace();
                android.util.Log.e("MainActivity", "IOException during sync", e);
                String errorMsg = e.getMessage();
                
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
                
                if (errorMsg == null || errorMsg.isEmpty()) {
                    errorMsg = "Lỗi kết nối khi đồng bộ";
                }
                final String finalErrorMsg = errorMsg;
                runOnUiThread(() -> {
                    Toast.makeText(this, " Lỗi đồng bộ: " + finalErrorMsg, Toast.LENGTH_LONG).show();
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
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cameraExecutor.shutdown();
    }
}
