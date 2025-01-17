package com.cj.mobile.myapplication;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.media.Image;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleOwner;
import androidx.recyclerview.widget.RecyclerView;

import com.cj.mobile.myapplication.adapter.RecognitionAdapter;
import com.cj.mobile.myapplication.model.Recognition;
import com.cj.mobile.myapplication.util.ImageClassifierHelper;
import com.cj.mobile.myapplication.util.YuvToRgbConverter;
import com.google.common.util.concurrent.ListenableFuture;

import org.opencv.android.OpenCVLoader;
import org.tensorflow.lite.support.label.Category;
import org.tensorflow.lite.task.vision.classifier.Classifications;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

/**
 * 图像分类
 *
 * 用途：图像分类的主要用途是识别图像中的主要对象或场景，并将其归类到预定义的类别中。
 * 功能：给定一张图像，图像分类算法会分析图像内容，并输出一个或多个类别标签，表示图像中包含的对象或场景。例如，算法可能会将一张猫的图像归类为“猫”这个类别。
 * 应用：图像分类常用于识别整个图像的内容，例如在照片中识别物体、人物或场景。它广泛应用于各种领域，如社交媒体内容过滤、医学影像分析、自动驾驶中的路标识别等。
 */
public class ImageClassifierActivity extends AppCompatActivity {
    private final String TAG = "ImageClassifierActivity";
    private PreviewView previewView;
    private RecyclerView resultRecyclerView;
    private RecognitionAdapter viewAdapter;
    // 图像分析器
    private ImageClassifierHelper imageClassifierHelper;
    private Bitmap bitmapBuffer;
    private final Object task = new Object();
    // 上次分析时间
    private long lastAnalyzeTime = 0;
    // 分析的间隔时间
    private long ANALYZE_INTERVAL = 0;
    // Yuv转Rgb转换器
    private YuvToRgbConverter yuvToRgbConverter;

    @Override
    protected void onDestroy() {
        super.onDestroy();
        synchronized (task) {
            imageClassifierHelper.clearImageClassifier();
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_finger_count);

        previewView = findViewById(R.id.view_fingercount_finder);
        resultRecyclerView = findViewById(R.id.view_fingercount_recognitionResults);

        // 请求相机权限
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(this, new String[]{
                    Manifest.permission.CAMERA,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.ACCESS_FINE_LOCATION}, 100);
        } else {
            startCamera();
        }

        viewAdapter = new RecognitionAdapter(this);
        resultRecyclerView.setAdapter(viewAdapter);

        // Disable recycler view animation to reduce flickering, otherwise items can move, fade in
        // and out as the list change
        resultRecyclerView.setItemAnimator(null);

        ANALYZE_INTERVAL = TimeUnit.SECONDS.toMillis(5);
        yuvToRgbConverter = new YuvToRgbConverter(this);
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);

        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                if (!OpenCVLoader.initDebug()) {
                    Log.e("OpenCV", "无法加载OpenCV库");
                } else {
                    Log.d("OpenCV", "OpenCV 加载成功！");
                    bindPreview(cameraProvider);
                }

            } catch (ExecutionException | InterruptedException e) {
                // Handle any errors here
                Log.e("CameraX", "Failed to bind camera", e);
                Toast.makeText(this, "无法启动相机，请稍后再试", Toast.LENGTH_SHORT).show();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void bindPreview(ProcessCameraProvider cameraProvider) {
        Preview preview = new Preview.Builder()
//                .setTargetResolution(new Size(640, 480))
                .build();
        preview.setSurfaceProvider(previewView.getSurfaceProvider());

        // 相机选择器(后置摄像头)
        CameraSelector cameraSelector = new CameraSelector.Builder()
                .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                .build();

        // 图像分析
        ImageAnalysis imageAnalyzer = new ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build();

        imageClassifierHelper = ImageClassifierHelper.create(this, new ImageClassifierHelper.ClassifierListener() {
            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    Toast.makeText(ImageClassifierActivity.this, error, Toast.LENGTH_SHORT).show();
                });
            }

            @Override
            public void onResults(List<Classifications> results, long inferenceTime) {
                runOnUiThread(() -> {
                    List<Recognition> items = new ArrayList<>();
                    for (Classifications detection : results) {
                        StringBuilder title = new StringBuilder();
                        for (Category category : detection.getCategories()) {
                            // 图片中的识别结果
                            title.append(category.getLabel());
                            title.append("(").append(category.getScore()).append(")");
                            title.append(";");

                            // 列表中的识别结果
                            Recognition info = new Recognition();
                            info.setTitle(category.getLabel());
                            info.setConfidence(category.getScore());
                            items.add(info);
                        }

                        Log.d(TAG, "###识别结果：" + title);
                        // updating the list of recognised objects
                        viewAdapter.submitList(items);
                    }
                });
            }
        });

        imageAnalyzer.setAnalyzer(ContextCompat.getMainExecutor(this), image -> {
            long currentTime = System.currentTimeMillis();
            if (currentTime - lastAnalyzeTime >= ANALYZE_INTERVAL) {
                // 执行检测逻辑
                Log.d(TAG, "#####  TFLite 执行检测逻辑！");
                Bitmap mBitmap = toBitmap(image);

                int imageRotation = image.getImageInfo().getRotationDegrees();
                synchronized (task) {
                    // Pass Bitmap and rotation to the image classifier helper for
                    // processing and classification
                    imageClassifierHelper.classify(mBitmap, imageRotation);
                }

                lastAnalyzeTime = currentTime; // 更新上一次检测时间
            }

            // 处理完成后关闭 ImageProxy
            image.close();
        });

        cameraProvider.unbindAll();
        cameraProvider.bindToLifecycle((LifecycleOwner) this, cameraSelector, preview, imageAnalyzer);
    }

    private Bitmap toBitmap(ImageProxy imageProxy) {
        @SuppressLint("UnsafeOptInUsageError") Image image = imageProxy.getImage();
        if (image == null) return null;

        Matrix rotationMatrix = null;
        // Initialise Buffer
        if (bitmapBuffer == null) {
            // The image rotation and RGB image buffer are initialized only once
            Log.d(TAG, "Initalise toBitmap()");
            rotationMatrix = new Matrix();
            rotationMatrix.postRotate(imageProxy.getImageInfo().getRotationDegrees());
            bitmapBuffer = Bitmap.createBitmap(
                    imageProxy.getWidth(), imageProxy.getHeight(), Bitmap.Config.ARGB_8888
            );
        }

        // Pass image to an image analyser
        yuvToRgbConverter.yuvToRgb(image, bitmapBuffer);

        // Create the Bitmap in the correct orientation
        return Bitmap.createBitmap(
                bitmapBuffer,
                0,
                0,
                bitmapBuffer.getWidth(),
                bitmapBuffer.getHeight(),
                rotationMatrix,
                false
        );
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 100 && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        }
    }

}
