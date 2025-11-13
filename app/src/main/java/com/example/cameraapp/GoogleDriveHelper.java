package com.example.cameraapp;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.Log;

import com.google.api.client.extensions.android.http.AndroidHttp;
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;
import com.google.api.client.http.ByteArrayContent;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import com.google.api.services.drive.model.File;
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Collections;

public class GoogleDriveHelper {
    private static final String TAG = "GoogleDriveHelper";
    private static final String APPLICATION_NAME = "FilterCamera";
    private static final java.util.Collection<String> SCOPES = 
        Collections.singleton(DriveScopes.DRIVE_FILE);

    private Context context;
    private GoogleAccountCredential credential;
    private Drive driveService;

    public GoogleDriveHelper(Context context, String accountName) {
        this.context = context;
        credential = GoogleAccountCredential.usingOAuth2(
            context, 
            SCOPES
        );
        credential.setSelectedAccountName(accountName);
        driveService = new Drive.Builder(
            AndroidHttp.newCompatibleTransport(),
            GsonFactory.getDefaultInstance(),
            credential
        ).setApplicationName(APPLICATION_NAME).build();
        
        Log.d(TAG, "GoogleDriveHelper initialized for account: " + accountName);
        Log.d(TAG, "Drive service created: " + (driveService != null ? "OK" : "NULL"));
    }

    public static GoogleAccountCredential getCredential(Context context) {
        return GoogleAccountCredential.usingOAuth2(
            context,
            SCOPES
        );
    }

    public String uploadImage(Bitmap bitmap, String fileName) throws IOException {
        if (driveService == null) {
            Log.e(TAG, "driveService is NULL!");
            throw new IOException("Drive service is not initialized");
        }
        
        if (bitmap == null) {
            Log.e(TAG, "bitmap is NULL!");
            throw new IOException("Bitmap is null");
        }

        try {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.JPEG, 95, outputStream);
            byte[] imageData = outputStream.toByteArray();
            
            Log.d(TAG, "Image data size: " + imageData.length + " bytes");

            File fileMetadata = new File();
            fileMetadata.setName(fileName);
            
            // Upload vào thư mục "FilterCamera" (tạo nếu chưa có)
            String folderId = getOrCreateFolder("FilterCamera");
            if (folderId != null) {
                fileMetadata.setParents(Collections.singletonList(folderId));
                Log.d(TAG, "Uploading to folder: " + folderId);
            } else {
                Log.w(TAG, "Folder ID is null, uploading to root");
            }

            ByteArrayContent mediaContent = new ByteArrayContent(
                "image/jpeg",
                imageData
            );

            Log.d(TAG, "Starting upload to Drive...");
            File file = driveService.files().create(fileMetadata, mediaContent)
                .setFields("id, webViewLink")
                .execute();

            if (file == null || file.getId() == null) {
                Log.e(TAG, "Upload returned null file or null file ID");
                throw new IOException("Upload failed: file or file ID is null");
            }

            Log.d(TAG, "File uploaded successfully to Drive. ID: " + file.getId());
            return file.getId();
        } catch (UserRecoverableAuthIOException e) {
            Log.e(TAG, "UserRecoverableAuthIOException: " + e.getMessage());
            Log.e(TAG, "UserRecoverableAuthIOException - Intent available: " + (e.getIntent() != null));
            // Throw exception với flag đặc biệt để MainActivity có thể xử lý
            // Không gọi initCause vì constructor đã set cause rồi
            IOException ioException = new IOException("AUTH_REQUIRED", e);
            throw ioException;
        } catch (com.google.api.client.googleapis.json.GoogleJsonResponseException e) {
            Log.e(TAG, "Google API Error: " + e.getStatusCode() + " - " + e.getMessage());
            Log.e(TAG, "Error details: " + e.getDetails());
            if (e.getDetails() != null) {
                Log.e(TAG, "Error message: " + e.getDetails().getMessage());
                Log.e(TAG, "Error code: " + e.getDetails().getCode());
            }
            
            if (e.getStatusCode() == 401) {
                throw new IOException("Authentication failed. Please sign in again.", e);
            } else if (e.getStatusCode() == 403) {
                String errorMsg = "Permission denied. ";
                if (e.getDetails() != null && e.getDetails().getMessage() != null) {
                    String detailMsg = e.getDetails().getMessage();
                    if (detailMsg.contains("access_denied") || detailMsg.contains("Access blocked")) {
                        errorMsg += "Email này chưa được thêm vào Test Users trong Google Cloud Console. ";
                        errorMsg += "Vui lòng thêm email vào OAuth consent screen > Test users.";
                    } else if (detailMsg.contains("insufficient permissions")) {
                        errorMsg += "Không đủ quyền truy cập Google Drive. ";
                        errorMsg += "Vui lòng kiểm tra OAuth scopes và permissions.";
                    } else {
                        errorMsg += detailMsg;
                    }
                } else {
                    errorMsg += "Please check Google Drive permissions.";
                }
                throw new IOException(errorMsg, e);
            } else if (e.getStatusCode() == 400) {
                String errorMsg = "Bad request. ";
                if (e.getDetails() != null && e.getDetails().getMessage() != null) {
                    errorMsg += e.getDetails().getMessage();
                }
                throw new IOException(errorMsg, e);
            }
            throw new IOException("Google Drive API error (" + e.getStatusCode() + "): " + 
                    (e.getDetails() != null && e.getDetails().getMessage() != null ? 
                     e.getDetails().getMessage() : e.getMessage()), e);
        } catch (Exception e) {
            Log.e(TAG, "Error uploading image", e);
            Log.e(TAG, "Exception type: " + e.getClass().getName());
            Log.e(TAG, "Exception message: " + e.getMessage());
            if (e.getCause() != null) {
                Log.e(TAG, "Cause: " + e.getCause().getMessage());
            }
            throw new IOException("Failed to upload image: " + e.getMessage(), e);
        }
    }

    private String getOrCreateFolder(String folderName) {
        try {
            // Tìm thư mục đã tồn tại
            String query = "mimeType='application/vnd.google-apps.folder' and name='" + 
                          folderName + "' and trashed=false";
            Drive.Files.List request = driveService.files().list()
                .setQ(query)
                .setSpaces("drive")
                .setFields("files(id, name)");

            var files = request.execute().getFiles();
            if (files != null && !files.isEmpty()) {
                return files.get(0).getId();
            }

            // Tạo thư mục mới nếu chưa có
            File folderMetadata = new File();
            folderMetadata.setName(folderName);
            folderMetadata.setMimeType("application/vnd.google-apps.folder");

            File folder = driveService.files().create(folderMetadata)
                .setFields("id")
                .execute();

            Log.d(TAG, "Created folder: " + folderName);
            return folder.getId();
        } catch (Exception e) {
            Log.e(TAG, "Error getting/creating folder", e);
            return null;
        }
    }
}

