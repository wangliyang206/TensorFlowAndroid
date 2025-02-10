package com.cj.mobile.myapplication;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleOwner;
import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.os.Bundle;
import android.util.Log;
import android.util.Size;
import android.widget.TextView;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.mlkit.vision.common.InputImage;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * @ProjectName: TensorFlowAndroid
 * @Package: com.cj.mobile.myapplication
 * @ClassName: CountNumbersActivity
 * @Description: 数一数
 * @Author: WLY
 * @CreateDate: 2025/2/10 15:25
 */
public class CountNumbersActivity extends AppCompatActivity {
    private static final int REQUEST_CODE_PERMISSIONS = 10;
    private static final String[] REQUIRED_PERMISSIONS = {Manifest.permission.CAMERA};
    private PreviewView previewView;
    private TextView resultTextView;
    private ExecutorService cameraExecutor;
//    private ObjectDetector objectDetector;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_count_number);
        previewView = findViewById(R.id.previewView);
        resultTextView = findViewById(R.id.resultTextView);
        cameraExecutor = Executors.newSingleThreadExecutor();
        // 配置 ML Kit 物体检测器
//        ObjectDetectorOptions options =
//                new ObjectDetectorOptions.Builder()
//                        .setDetectorMode(ObjectDetectorOptions.STREAM_MODE)
//                        .enableClassification()
//                        .build();
//        objectDetector = ObjectDetection.getClient(options);
        if (allPermissionsGranted()) {
            startCamera();
        } else {
            ActivityCompat.requestPermissions(
                    this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS);
        }
    }
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera();
            } else {
                finish();
            }
        }
    }
    private boolean allPermissionsGranted() {
        for (String permission : REQUIRED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }
    private void startCamera() {
        ProcessCameraProvider.getInstance(this).addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = ProcessCameraProvider.getInstance(this).get();
                // 配置预览用例
                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());
                // 配置图像分析用例
                ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                        .setTargetResolution(new Size(1280, 720))
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build();
                imageAnalysis.setAnalyzer(cameraExecutor, new ImageAnalysis.Analyzer() {
                    @Override
                    public void analyze(@NonNull ImageProxy imageProxy) {
                        InputImage inputImage = convertImageProxyToInputImage(imageProxy);
                        if (inputImage != null) {
//                            objectDetector.process(inputImage)
//                                    .addOnSuccessListener(new OnSuccessListener<List<DetectedObject>>() {
//                                        @Override
//                                        public void onSuccess(List<DetectedObject> detectedObjects) {
//                                            int objectCount = detectedObjects.size();
//                                            runOnUiThread(() -> {
//                                                resultTextView.setText("Detected Objects: " + objectCount);
//                                            });
//                                        }
//                                    })
//                                    .addOnFailureListener(new OnFailureListener() {
//                                        @Override
//                                        public void onFailure(@NonNull Exception e) {
//                                            Log.e("ObjectDetection", "Object detection failed: " + e.getMessage());
//                                        }
//                                    })
//                                    .addOnCompleteListener(task -> imageProxy.close());
                        } else {
                            imageProxy.close();
                        }
                    }
                });
                // 选择后置摄像头
                CameraSelector cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA;
                // 绑定用例到生命周期
                cameraProvider.unbindAll();
                Camera camera = cameraProvider.bindToLifecycle((LifecycleOwner) this, cameraSelector, preview, imageAnalysis);
            } catch (Exception e) {
                Log.e("CameraX", "Use case binding failed: " + e.getMessage());
            }
        }, ContextCompat.getMainExecutor(this));
    }
    private InputImage convertImageProxyToInputImage(ImageProxy imageProxy) {
        ImageProxy.PlaneProxy[] planes = imageProxy.getPlanes();
        ByteBuffer yBuffer = planes[0].getBuffer();
        ByteBuffer uBuffer = planes[1].getBuffer();
        ByteBuffer vBuffer = planes[2].getBuffer();
        int ySize = yBuffer.remaining();
        int uSize = uBuffer.remaining();
        int vSize = vBuffer.remaining();
        byte[] nv21 = new byte[ySize + uSize + vSize];
        yBuffer.get(nv21, 0, ySize);
        vBuffer.get(nv21, ySize, vSize);
        uBuffer.get(nv21, ySize + vSize, uSize);
        int width = imageProxy.getWidth();
        int height = imageProxy.getHeight();
        Bitmap bitmap = BitmapFactory.decodeByteArray(nv21, 0, nv21.length);
        Matrix matrix = new Matrix();
        matrix.postRotate(imageProxy.getImageInfo().getRotationDegrees());
        bitmap = Bitmap.createBitmap(bitmap, 0, 0, width, height, matrix, true);
        return InputImage.fromBitmap(bitmap, 0);
    }
    @Override
    protected void onDestroy() {
        super.onDestroy();
        cameraExecutor.shutdown();
    }
}
