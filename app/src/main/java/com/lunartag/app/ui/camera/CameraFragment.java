package com.lunartag.app.ui.camera;

import android.Manifest;
import android.animation.ArgbEvaluator;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.ImageDecoder;
import android.location.Location;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Looper;
import android.provider.MediaStore;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;

import com.google.common.util.concurrent.ListenableFuture;
import com.lunartag.app.data.AppDatabase;
import com.lunartag.app.data.ManualLocationDao;
import com.lunartag.app.data.PhotoDao;
import com.lunartag.app.databinding.FragmentCameraBinding;
import com.lunartag.app.model.ManualLocation;
import com.lunartag.app.model.Photo;
import com.lunartag.app.ui.admin.ManualLocationDialog;
import com.lunartag.app.utils.GeocodingUtils;
import com.lunartag.app.utils.ImageUtils;
import com.lunartag.app.utils.LocationProvider;
import com.lunartag.app.utils.Scheduler;
import com.lunartag.app.utils.StorageUtils;
import com.lunartag.app.utils.WatermarkUtils;

import org.json.JSONArray;
import org.json.JSONException;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CameraFragment extends Fragment {

    private static final String TAG = "CameraFragment";

    // Preferences for Admin/Schedule Mode
    private static final String PREFS_SCHEDULE = "LunarTagSchedule";
    private static final String KEY_TIMESTAMP_LIST = "timestamp_list";
    private static final String PREFS_TOGGLES = "LunarTagFeatureToggles";
    private static final String KEY_ADMIN_ENABLED = "customTimestampEnabled";

    // Preferences for Settings (Company Name)
    private static final String PREFS_SETTINGS = "LunarTagSettings";
    private static final String KEY_COMPANY_NAME = "company_name";

    private FragmentCameraBinding binding;
    private ImageCapture imageCapture;
    private ExecutorService cameraExecutor;
    private Camera camera; // Reference to control Zoom
    private int lensFacing = CameraSelector.LENS_FACING_BACK; // Default to Back camera

    // Zoom Handling
    private ScaleGestureDetector scaleGestureDetector;

    // Location & Workplace Logic
    private LocationProvider locationProvider;
    private ManualLocationDao manualLocationDao;
    private ObjectAnimator gpsBlinkAnimator;
    private boolean isBlinking = false;

    // NEW: Activity Result Launcher for Image Import
    private ActivityResultLauncher<String> imagePickerLauncher;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Register Photo Picker for gallery import
        imagePickerLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                uri -> {
                    if (uri != null) {
                        logToScreen("Event: Image selected from gallery for watermark.");
                        processImportedImage(uri);
                    } else {
                        logToScreen("Event: Image import cancelled.");
                    }
                }
        );
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentCameraBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        cameraExecutor = Executors.newSingleThreadExecutor();
        locationProvider = new LocationProvider(getContext());
        manualLocationDao = AppDatabase.getDatabase(requireContext()).manualLocationDao();

        // Setup Listener to turn GPS Icon GREEN when locked
        locationProvider.setStatusListener(location -> {
            new android.os.Handler(Looper.getMainLooper()).post(() -> {
                if (binding != null) {
                    binding.buttonGpsStatus.setColorFilter(Color.GREEN);
                    performSmartWorkplaceCheck(location);
                }
            });
        });

        logToScreen("System: Camera View Created.");

        // 1. Initialize Zoom Gesture Detector
        scaleGestureDetector = new ScaleGestureDetector(getContext(), new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScale(ScaleGestureDetector detector) {
                if (camera != null) {
                    float currentZoomRatio = camera.getCameraInfo().getZoomState().getValue().getZoomRatio();
                    float delta = detector.getScaleFactor();
                    camera.getCameraControl().setZoomRatio(currentZoomRatio * delta);
                }
                return true;
            }
        });

        // Attach Touch Listener to Preview for Zoom
        binding.cameraPreview.setOnTouchListener((v, event) -> {
            scaleGestureDetector.onTouchEvent(event);
            return true;
        });

        // 2. Check Permissions and Start
        logToScreen("System: Checking permissions...");
        if (allPermissionsGranted()) {
            logToScreen("System: Permissions OK. Starting CameraX...");
            startCamera();
        } else {
            logToScreen("ERROR: Camera/Location Permissions NOT granted!");
            Toast.makeText(getContext(), "Camera permissions not granted.", Toast.LENGTH_SHORT).show();
        }

        // 3. Shutter Capture Button Logic (Remains 100% Untouched)
        binding.buttonCapture.setOnClickListener(v -> {
            logToScreen("Event: Capture Button Clicked.");
            takePhoto();
        });

        // 4. Flip Camera Button Logic
        binding.buttonFlipCamera.setOnClickListener(v -> toggleCamera());

        // 5. GPS Button Logic (Footer)
        binding.buttonGpsStatus.setOnClickListener(v -> {
            logToScreen("User Command: Force GPS/Workplace Sync.");
            Location loc = locationProvider.getCurrentLocationFast();
            if (loc != null) {
                performSmartWorkplaceCheck(loc);
                Toast.makeText(getContext(), "Syncing Workplace...", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(getContext(), "Refining GPS Signal...", Toast.LENGTH_SHORT).show();
            }
        });

        // 6. Folder Selection Logic (Footer)
        binding.buttonSaveFolder.setOnClickListener(v -> {
            logToScreen("User Command: Opening File Picker...");
            StorageUtils.launchFolderSelector(this);
        });

        // 7. NEW: Import Image Button Logic (Additional Feature)
        binding.buttonImportImage.setOnClickListener(v -> {
            logToScreen("User Command: Opening Gallery to import image...");
            imagePickerLauncher.launch("image/*");
        });

        updateWorkplaceDisplay();
        updateSlotCounter();
    }

    private void performSmartWorkplaceCheck(Location currentGps) {
        if (currentGps == null) return;
        
        SharedPreferences prefs = requireContext().getSharedPreferences(PREFS_SETTINGS, Context.MODE_PRIVATE);
        boolean isManualMode = prefs.getBoolean(ManualLocationDialog.KEY_LOCATION_MODE_MANUAL, false);
        boolean isAutoDetectEnabled = prefs.getBoolean(ManualLocationDialog.KEY_AUTO_WORKPLACE_DETECTION, true);

        if (!isManualMode || !isAutoDetectEnabled) {
            stopGpsWarningBlink();
            return;
        }

        String savedLatStr = prefs.getString(ManualLocationDialog.KEY_MANUAL_LAT, "0.0");
        String savedLonStr = prefs.getString(ManualLocationDialog.KEY_MANUAL_LON, "0.0");
        double savedLat = Double.parseDouble(savedLatStr);
        double savedLon = Double.parseDouble(savedLonStr);

        float distance = locationProvider.calculateDistanceInMeters(
                currentGps.getLatitude(), currentGps.getLongitude(), savedLat, savedLon);

        if (distance > 200) {
            logToScreen("Warning: Workplace Mismatch (" + (int)distance + "m). Starting Blink.");
            startGpsWarningBlink();

            cameraExecutor.execute(() -> {
                ManualLocation closestMatch = manualLocationDao.findClosestLocation(currentGps.getLatitude(), currentGps.getLongitude());
                
                if (closestMatch != null) {
                    logToScreen("Smart Sync: Auto-Switching to workplace: " + closestMatch.locationName);
                    activateWorkplaceProfile(closestMatch);
                } else {
                    logToScreen("Smart Sync: New Workplace detected. Auto-creating...");
                    
                    GeocodingUtils.AddressDetails details = GeocodingUtils.getDetailedAddress(requireContext(), currentGps);
                    
                    ManualLocation newWorkplace = new ManualLocation();
                    newWorkplace.locationName = details.landmark.isEmpty() ? (details.city.isEmpty() ? "New Workplace" : details.city) : details.landmark;
                    newWorkplace.landmark = details.landmark.replace("(", "").replace(")", "");
                    newWorkplace.pincode = details.pincode.replace("(", "").replace(")", "");
                    newWorkplace.state = details.state.replace("(", "").replace(")", "");
                    newWorkplace.country = details.country.replace("(", "").replace(")", "");
                    newWorkplace.latitude = currentGps.getLatitude();
                    newWorkplace.longitude = currentGps.getLongitude();
                    newWorkplace.isActive = true;

                    manualLocationDao.insertLocation(newWorkplace);
                    activateWorkplaceProfile(newWorkplace);
                }
            });
        } else {
            stopGpsWarningBlink();
        }
    }

    private void activateWorkplaceProfile(ManualLocation loc) {
        SharedPreferences prefs = requireContext().getSharedPreferences(PREFS_SETTINGS, Context.MODE_PRIVATE);
        String cleanLandmark = loc.landmark.replace("(", "").replace(")", "");

        prefs.edit()
                .putString(ManualLocationDialog.KEY_MANUAL_LOC_1, loc.locationName.replace("(", "").replace(")", ""))
                .putString(ManualLocationDialog.KEY_MANUAL_LANDMARK, cleanLandmark)
                .putString(ManualLocationDialog.KEY_MANUAL_PINCODE, loc.pincode.replace("(", "").replace(")", ""))
                .putString(ManualLocationDialog.KEY_MANUAL_LAT, String.valueOf(loc.latitude))
                .putString(ManualLocationDialog.KEY_MANUAL_LON, String.valueOf(loc.longitude))
                .putString(ManualLocationDialog.KEY_MANUAL_STATE, loc.state.replace("(", "").replace(")", ""))
                .putString(ManualLocationDialog.KEY_MANUAL_COUNTRY, loc.country.replace("(", "").replace(")", ""))
                .apply();
        
        new android.os.Handler(Looper.getMainLooper()).post(() -> {
            if (binding != null) {
                stopGpsWarningBlink();
                updateWorkplaceDisplay();
            }
            Toast.makeText(getContext(), "Workplace Auto-Sync: " + loc.locationName, Toast.LENGTH_SHORT).show();
        });
    }

    private void startGpsWarningBlink() {
        if (isBlinking || binding == null) return;
        isBlinking = true;
        gpsBlinkAnimator = ObjectAnimator.ofInt(binding.buttonGpsStatus, "colorFilter", Color.GREEN, Color.RED);
        gpsBlinkAnimator.setDuration(600);
        gpsBlinkAnimator.setEvaluator(new ArgbEvaluator());
        gpsBlinkAnimator.setRepeatCount(ValueAnimator.INFINITE);
        gpsBlinkAnimator.setRepeatMode(ValueAnimator.REVERSE);
        gpsBlinkAnimator.start();
    }

    private void stopGpsWarningBlink() {
        if (gpsBlinkAnimator != null) {
            gpsBlinkAnimator.cancel();
            if (binding != null) {
                binding.buttonGpsStatus.setColorFilter(Color.GREEN);
            }
            isBlinking = false;
        }
    }

    private void updateWorkplaceDisplay() {
        if (binding == null) return;
        SharedPreferences prefs = requireContext().getSharedPreferences(PREFS_SETTINGS, Context.MODE_PRIVATE);
        String name = prefs.getString(ManualLocationDialog.KEY_MANUAL_LOC_1, "Automatic");
        binding.textActiveWorkplace.setText("Workplace: " + name);
    }

    @Override
    public void onResume() {
        super.onResume();
        logToScreen("System: Resuming. Starting GPS Engine...");
        if (locationProvider != null) locationProvider.startLocationUpdates();
        updateWorkplaceDisplay();
    }

    @Override
    public void onPause() {
        super.onPause();
        logToScreen("System: Pausing. Stopping GPS Engine.");
        if (locationProvider != null) locationProvider.stopLocationUpdates();
        stopGpsWarningBlink();
    }

    private void logToScreen(String message) {
        if (getContext() == null) return;

        String type = "info";
        String lowerMsg = message.toLowerCase();
        if (lowerMsg.contains("error") || lowerMsg.contains("fail") || lowerMsg.contains("missing") || lowerMsg.contains("warning")) {
            type = "error";
        }

        Intent intent = new Intent("com.lunartag.ACTION_LOG_UPDATE");
        intent.putExtra("log_msg", message);
        intent.putExtra("log_type", type);
        intent.setPackage(requireContext().getPackageName());
        requireContext().sendBroadcast(intent);

        Log.d("LunarTagLive", message); 
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(getContext());

        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(binding.cameraPreview.getSurfaceProvider());
                imageCapture = new ImageCapture.Builder()
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                        .build();
                CameraSelector cameraSelector = new CameraSelector.Builder()
                        .requireLensFacing(lensFacing)
                        .build();
                cameraProvider.unbindAll();
                camera = cameraProvider.bindToLifecycle(
                        getViewLifecycleOwner(), cameraSelector, preview, imageCapture);

                logToScreen("System: Camera Started Successfully.");

            } catch (ExecutionException | InterruptedException e) {
                logToScreen("CRITICAL ERROR: Failed to bind camera: " + e.getMessage());
                Log.e(TAG, "Use case binding failed", e);
            }
        }, ContextCompat.getMainExecutor(getContext()));
    }

    private void toggleCamera() {
        if (lensFacing == CameraSelector.LENS_FACING_BACK) {
            lensFacing = CameraSelector.LENS_FACING_FRONT;
        } else {
            lensFacing = CameraSelector.LENS_FACING_BACK;
        }
        startCamera();
    }

    // --- STANDARD CAMERA CAPTURE (100% PRESERVED) ---
    private void takePhoto() {
        if (imageCapture == null) {
            logToScreen("ERROR: ImageCapture is null (Camera not ready).");
            return;
        }

        Toast.makeText(getContext(), "Capturing...", Toast.LENGTH_SHORT).show();
        logToScreen("System: Requesting image from sensor...");

        imageCapture.takePicture(cameraExecutor, new ImageCapture.OnImageCapturedCallback() {
            @Override
            public void onCaptureSuccess(@NonNull ImageProxy image) {
                logToScreen("System: Image sensor capture SUCCESS.");
                processAndSaveImage(image);
            }

            @Override
            public void onError(@NonNull ImageCaptureException exception) {
                logToScreen("CRITICAL ERROR: Image Sensor Failed: " + exception.getMessage());
                Log.e(TAG, "Photo capture failed: " + exception.getMessage(), exception);
            }
        });
    }

    private void processAndSaveImage(ImageProxy imageProxy) {
        try {
            logToScreen("System: Converting YUV to Bitmap...");
            Bitmap bitmap = ImageUtils.imageProxyToBitmap(imageProxy);
            imageProxy.close();

            if (bitmap == null) {
                logToScreen("ERROR: Failed to convert image to bitmap.");
                return;
            }

            processBitmapAndSave(bitmap, "LunarTag_");

        } catch (Exception e) {
            logToScreen("CRITICAL ERROR Top Level: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // --- NEW: PROCESS IMPORTED GALLERY IMAGE ---
    private void processImportedImage(Uri imageUri) {
        Toast.makeText(getContext(), "Importing image...", Toast.LENGTH_SHORT).show();
        cameraExecutor.execute(() -> {
            try {
                logToScreen("System: Decoding imported image URI...");
                Bitmap bitmap = decodeBitmapFromUri(imageUri);
                if (bitmap == null) {
                    logToScreen("ERROR: Could not decode imported image.");
                    new android.os.Handler(Looper.getMainLooper()).post(() ->
                            Toast.makeText(getContext(), "Failed to load selected image.", Toast.LENGTH_SHORT).show());
                    return;
                }

                logToScreen("System: Imported bitmap ready. Proceeding to watermark...");
                processBitmapAndSave(bitmap, "LunarTag_Import_");

            } catch (Exception e) {
                logToScreen("CRITICAL ERROR Importing: " + e.getMessage());
                e.printStackTrace();
            }
        });
    }

    private Bitmap decodeBitmapFromUri(Uri uri) {
        Context context = getContext();
        if (context == null || uri == null) return null;
        try {
            Bitmap bitmap;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                ImageDecoder.Source source = ImageDecoder.createSource(context.getContentResolver(), uri);
                bitmap = ImageDecoder.decodeBitmap(source, (decoder, info, s) -> {
                    decoder.setMutableRequired(true);
                    decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);
                });
            } else {
                try (InputStream is = context.getContentResolver().openInputStream(uri)) {
                    bitmap = BitmapFactory.decodeStream(is);
                }
            }
            return bitmap;
        } catch (Exception e) {
            logToScreen("Decode Exception: " + e.getMessage());
            return null;
        }
    }

    /**
     * UNIFIED PROCESSING ENGINE
     * Handles watermarking, storage, DB, clipboard, and scheduler identically
     * for both captured camera frames and imported images.
     */
    private void processBitmapAndSave(Bitmap sourceBitmap, String filenamePrefix) {
        try {
            Bitmap bitmap = sourceBitmap;

            logToScreen("System: Checking Location mode...");
            SharedPreferences settingsPrefs = requireContext().getSharedPreferences(PREFS_SETTINGS, Context.MODE_PRIVATE);
            boolean isManualMode = settingsPrefs.getBoolean(ManualLocationDialog.KEY_LOCATION_MODE_MANUAL, false);
            boolean isQrEnabled = settingsPrefs.getBoolean(ManualLocationDialog.KEY_MANUAL_QR_ENABLED, false);

            Location sensorLoc = locationProvider.getCurrentLocationFast();
            double finalLat = 0.0;
            double finalLon = 0.0;
            String finalAddress;
            String finalManualSubLine = "";
            String gpsString;
            
            String qrLat;
            String qrLon;

            if (isManualMode) {
                logToScreen("System: Manual Override detected.");
                String locName = settingsPrefs.getString(ManualLocationDialog.KEY_MANUAL_LOC_1, "No Address").replace("(", "").replace(")", "");
                String landmark = settingsPrefs.getString(ManualLocationDialog.KEY_MANUAL_LANDMARK, "").replace("(", "").replace(")", "");
                
                finalAddress = locName + (landmark.isEmpty() ? "" : ", " + landmark);

                finalManualSubLine = settingsPrefs.getString(ManualLocationDialog.KEY_MANUAL_STATE, "").replace("(", "").replace(")", "") + ", " +
                                     settingsPrefs.getString(ManualLocationDialog.KEY_MANUAL_COUNTRY, "").replace("(", "").replace(")", "") + " - " +
                                     settingsPrefs.getString(ManualLocationDialog.KEY_MANUAL_PINCODE, "").replace("(", "").replace(")", "");

                qrLat = settingsPrefs.getString(ManualLocationDialog.KEY_MANUAL_LAT, "0.0");
                qrLon = settingsPrefs.getString(ManualLocationDialog.KEY_MANUAL_LON, "0.0");
                gpsString = "Lat: " + qrLat + " Lon: " + qrLon;

                try {
                    finalLat = Double.parseDouble(qrLat);
                    finalLon = Double.parseDouble(qrLon);
                } catch (Exception e) {
                    logToScreen("Error: Manual Lat/Lon parse failed.");
                }
            } else {
                logToScreen("System: Grabbing Location immediately...");
                Location location = sensorLoc;

                if (location == null) {
                    logToScreen("WARNING: Location is NULL/Waiting. Saving anyway (Safety Mode).");
                } else {
                    logToScreen("System: Location Locked (Lat: " + location.getLatitude() + ")");
                    finalLat = location.getLatitude();
                    finalLon = location.getLongitude();
                }

                finalAddress = getAddressFromLocation(location);
                qrLat = String.valueOf(finalLat);
                qrLon = String.valueOf(finalLon);
                gpsString = "Lat: " + finalLat + " Lon: " + finalLon;
            }

            try {
                long realTime = System.currentTimeMillis();
                long assignedTime = realTime;

                SharedPreferences togglePrefs = requireContext().getSharedPreferences(PREFS_TOGGLES, Context.MODE_PRIVATE);
                if (togglePrefs.getBoolean(KEY_ADMIN_ENABLED, false)) {
                    assignedTime = getNextScheduledTimestamp(realTime);
                }

                String companyName = settingsPrefs.getString(KEY_COMPANY_NAME, "My Company"); 
                SimpleDateFormat sdf = new SimpleDateFormat("dd-MMM-yyyy hh:mm a", Locale.US);
                String timeString = sdf.format(new Date(assignedTime));

                ArrayList<String> linesList = new ArrayList<>();
                linesList.add("GPS Map Camera");
                linesList.add(companyName);
                linesList.add(finalAddress);
                if (isManualMode && !finalManualSubLine.isEmpty()) {
                    linesList.add(finalManualSubLine);
                }
                linesList.add(gpsString);
                linesList.add(timeString);

                String[] watermarkLines = linesList.toArray(new String[0]);

                logToScreen("System: Applying Watermark...");
                bitmap = WatermarkUtils.addWatermark(getContext(), bitmap, null, watermarkLines, qrLat, qrLon, isQrEnabled);

                String absolutePath = null;
                logToScreen("System: Saving File...");

                String fileBaseName = filenamePrefix + realTime;

                if (StorageUtils.hasCustomFolder(getContext())) {
                    logToScreen("Storage: Using User-Selected Folder (SD/External).");
                    absolutePath = StorageUtils.saveImageToCustomFolder(getContext(), bitmap, fileBaseName);
                } else {
                    logToScreen("Storage: Using Default Internal Storage.");
                    absolutePath = saveImageToInternalStorage(getContext(), bitmap, fileBaseName);
                    if (absolutePath != null) {
                        logToScreen("Storage: Exporting copy to Public Gallery...");
                        exportToPublicGallery(getContext(), absolutePath, fileBaseName);
                    }
                }

                if (absolutePath != null) {
                    logToScreen("SUCCESS: File Written. (" + absolutePath + ")");

                    Location dbLocation = new Location("temp");
                    dbLocation.setLatitude(finalLat);
                    dbLocation.setLongitude(finalLon);
                    if (!isManualMode && sensorLoc != null) {
                        dbLocation.setAccuracy(sensorLoc.getAccuracy());
                    }

                    savePhotoToDatabase(absolutePath, realTime, assignedTime, dbLocation);
                    logToScreen("System: Database Updated.");

                    copyImageToClipboard(absolutePath);

                    new android.os.Handler(Looper.getMainLooper()).post(() -> {
                        Toast.makeText(getContext(), "Photo Processed and Saved!", Toast.LENGTH_SHORT).show();
                        updateSlotCounter();
                    });
                } else {
                    logToScreen("CRITICAL ERROR: File Write Failed! Check permissions.");
                    new android.os.Handler(Looper.getMainLooper()).post(() ->
                            Toast.makeText(getContext(), "Save Failed!", Toast.LENGTH_SHORT).show());
                }

            } catch (Exception e) {
                logToScreen("CRITICAL ERROR inside Processing: " + e.getMessage());
                e.printStackTrace();
            }

        } catch (Exception e) {
            logToScreen("CRITICAL ERROR inside processBitmapAndSave: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == StorageUtils.REQUEST_CODE_PICK_FOLDER) {
            if (resultCode == Activity.RESULT_OK && data != null) {
                Uri treeUri = data.getData();
                logToScreen("User Selected Folder URI: " + treeUri);
                StorageUtils.saveFolderPermission(getContext(), treeUri);
                logToScreen("System: Permission Saved Permanently.");
            } else {
                logToScreen("User Cancelled Folder Selection.");
            }
        }
    }

    private long getNextScheduledTimestamp(long fallbackTime) {
        SharedPreferences prefs = requireContext().getSharedPreferences(PREFS_SCHEDULE, Context.MODE_PRIVATE);
        String json = prefs.getString(KEY_TIMESTAMP_LIST, "[]");
        List<Long> list = new ArrayList<>();

        try {
            JSONArray jsonArray = new JSONArray(json);
            for (int i = 0; i < jsonArray.length(); i++) {
                list.add(jsonArray.getLong(i));
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }

        if (list.isEmpty()) {
            return fallbackTime;
        }
        long assigned = list.remove(0);
        JSONArray updatedArray = new JSONArray();
        for (Long ts : list) {
            updatedArray.put(ts);
        }
        prefs.edit().putString(KEY_TIMESTAMP_LIST, updatedArray.toString()).apply();

        return assigned;
    }

    private void updateSlotCounter() {
        SharedPreferences togglePrefs = requireContext().getSharedPreferences(PREFS_TOGGLES, Context.MODE_PRIVATE);
        if (!togglePrefs.getBoolean(KEY_ADMIN_ENABLED, false)) {
            binding.textSlotCounter.setVisibility(View.GONE);
            return;
        }
        SharedPreferences prefs = requireContext().getSharedPreferences(PREFS_SCHEDULE, Context.MODE_PRIVATE);
        String json = prefs.getString(KEY_TIMESTAMP_LIST, "[]");
        try {
            JSONArray jsonArray = new JSONArray(json);
            int count = jsonArray.length();
            binding.textSlotCounter.setText(count + " Slots Left");
            binding.textSlotCounter.setVisibility(View.VISIBLE);
        } catch (JSONException e) {
            binding.textSlotCounter.setVisibility(View.GONE);
        }
    }

    private String saveImageToInternalStorage(Context context, Bitmap bitmap, String filename) {
        File directory = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        if (directory == null) {
            logToScreen("ERROR: External Files Dir is null!");
            return null;
        }
        File file = new File(directory, filename + ".jpg");
        try (OutputStream fos = new FileOutputStream(file)) {
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, fos);
            return file.getAbsolutePath();
        } catch (IOException e) {
            logToScreen("ERROR Saving IO: " + e.getMessage());
            return null;
        }
    }

    private void exportToPublicGallery(Context context, String internalPath, String filename) {
        if (internalPath == null) return;
        try {
            File internalFile = new File(internalPath);
            if (!internalFile.exists()) return;

            ContentResolver resolver = context.getContentResolver();
            ContentValues contentValues = new ContentValues();
            contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, filename + ".jpg");
            contentValues.put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg");
            contentValues.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + File.separator + "LunarTag");

            Uri imageUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues);

            if (imageUri != null) {
                try (OutputStream out = resolver.openOutputStream(imageUri);
                     InputStream in = new FileInputStream(internalFile)) {
                    byte[] buffer = new byte[1024];
                    int len;
                    while ((len = in.read(buffer)) > 0) {
                        out.write(buffer, 0, len);
                    }
                    logToScreen("Export: Copy Success.");
                }
            }
        } catch (Exception e) {
            logToScreen("Export EXCEPTION: " + e.getMessage());
        }
    }

    private void savePhotoToDatabase(String filePath, long realTime, long assignedTime, Location loc) {
        try {
            Photo photo = new Photo();
            photo.setFilePath(filePath); 
            photo.setCaptureTimestampReal(realTime);
            photo.setAssignedTimestamp(assignedTime);
            photo.setCreatedAt(System.currentTimeMillis());
            photo.setStatus("PENDING");
            if (loc != null) {
                photo.setLat(loc.getLatitude());
                photo.setLon(loc.getLongitude());
                photo.setAccuracyMeters(loc.getAccuracy());
            }
            AppDatabase db = AppDatabase.getDatabase(getContext());
            PhotoDao dao = db.photoDao();

            long id = dao.insertPhoto(photo);

            logToScreen("System: Scheduling Alarm for Photo ID: " + id);
            Scheduler.schedulePhotoSend(
                requireContext(),
                id,
                filePath,
                assignedTime
            );

        } catch (Exception e) {
            logToScreen("DB ERROR: " + e.getMessage());
        }
    }

    private String getAddressFromLocation(Location location) {
        return GeocodingUtils.getAddressWithFallback(requireContext(), location);
    }

    private boolean allPermissionsGranted() {
        String[] requiredPermissions = {Manifest.permission.CAMERA, Manifest.permission.ACCESS_FINE_LOCATION};
        for (String permission : requiredPermissions) {
            if (ContextCompat.checkSelfPermission(getContext(), permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    private void copyImageToClipboard(String absolutePath) {
        Context context = getContext();
        if (context == null || absolutePath == null) return;

        try {
            Uri uri;
            if (absolutePath.startsWith("content://")) {
                uri = Uri.parse(absolutePath);
            } else {
                File file = new File(absolutePath);
                String authority = context.getPackageName() + ".fileprovider"; 
                uri = FileProvider.getUriForFile(context, authority, file);
            }

            ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard != null) {
                ClipData clip = ClipData.newUri(context.getContentResolver(), "Captured Image", uri);
                clipboard.setPrimaryClip(clip);
                logToScreen("System: Image copied to Clipboard.");
            }
        } catch (Exception e) {
            logToScreen("Clipboard Error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
        if (cameraExecutor != null) {
            cameraExecutor.shutdown();
        }
    }
}