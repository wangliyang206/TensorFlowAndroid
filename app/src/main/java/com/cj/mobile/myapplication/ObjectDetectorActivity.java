package com.cj.mobile.myapplication;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
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
import com.cj.mobile.myapplication.util.Classifier;
import com.cj.mobile.myapplication.util.ObjectDetectorHelper;
import com.google.common.util.concurrent.ListenableFuture;

import org.opencv.android.OpenCVLoader;
import org.tensorflow.lite.support.label.Category;
import org.tensorflow.lite.task.vision.detector.Detection;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

/**
 * 目标检测
 * <p>
 * 用途：目标检测不仅要识别图像中的主要对象，还要确定这些对象在图像中的位置。
 * 功能：与图像分类不同，目标检测算法会输出一个或多个类别标签，以及每个对象所在的边界框的坐标。这使得算法能够在图像中定位并标识多个对象。
 * 应用：目标检测广泛应用于自动驾驶、视频监控、人脸检测等领域。在这些应用中，除了识别对象类别外，还需要知道它们在图像中的具体位置。例如，在自动驾驶中，目标检测算法可以识别出前方的车辆、行人和其他障碍物，并提供它们的位置信息，以帮助车辆做出正确的驾驶决策。
 */
public class ObjectDetectorActivity extends AppCompatActivity {
    private final String TAG = "ObjectDetectorActivity";
    private PreviewView previewView;
    private RecyclerView resultRecyclerView;
    private RecognitionAdapter viewAdapter;
    // 物体探测器助手
    private ObjectDetectorHelper objectDetectorHelper;
    // 上次分析时间
    private long lastAnalyzeTime = 0;
    // 分析的间隔时间
    private long ANALYZE_INTERVAL = 0;
    private final Object task = new Object();

    @Override
    protected void onDestroy() {
        super.onDestroy();
        synchronized (task) {
            objectDetectorHelper.clearObjectDetector();
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_finger_count);

        previewView = findViewById(R.id.viewFinder);
        resultRecyclerView = findViewById(R.id.recognitionResults);

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

    /**
     * 绑定预览
     */
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

        objectDetectorHelper = new ObjectDetectorHelper(0.5f, 2, 3, 0, 0, this, new ObjectDetectorHelper.DetectorListener() {

            @Override
            public void onError(String error) {
                Log.e(TAG, "###识别错误：" + error);
            }

            @Override
            public void onResults(List<Detection> results, long inferenceTime, int imageHeight, int imageWidth) {
                List<Recognition> items = new ArrayList<>();
                for (Detection detection : results) {
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
                }

                runOnUiThread(() -> {
                    // updating the list of recognised objects
                    viewAdapter.submitList(items);
                });
            }
        });

        imageAnalyzer.setAnalyzer(ContextCompat.getMainExecutor(this), image -> {
            long currentTime = System.currentTimeMillis();
            if (currentTime - lastAnalyzeTime >= ANALYZE_INTERVAL) {
                // 执行检测逻辑
                Log.d(TAG, "#####  TFLite 执行检测逻辑！");

                int imageRotation = image.getImageInfo().getRotationDegrees();
                objectDetectorHelper.detect(imageProxyToBitmap(image), imageRotation);

                lastAnalyzeTime = currentTime; // 更新上一次检测时间
            }

            // 处理完成后关闭 ImageProxy
            image.close();
        });

        // 第一种，设置图像分析器
//        imageAnalyzer.setAnalyzer(ContextCompat.getMainExecutor(this), image -> {
//            // 获取图像数据并进行处理
//            processImage(image);
//            // 处理完成后关闭 ImageProxy
//            image.close();
//        });

        // 第二种，物体探测器
//        imageAnalyzer.setAnalyzer(Executors.newSingleThreadExecutor(), new ImageObjectDetection(this, items -> {
//            runOnUiThread(() -> {
//                // updating the list of recognised objects
//                viewAdapter.submitList(items);
//            });
//
//        }));

        cameraProvider.unbindAll();
        cameraProvider.bindToLifecycle((LifecycleOwner) this, cameraSelector, preview, imageAnalyzer);
    }


    private static final int INPUT_SIZE = 224;
    // 识别 【猫】或【狗】 的模型（缺陷，目前只能识别猫和狗，未检测到时只显示狗）
    private static final String MODEL_PATH = "cats_vs_dogs.tflite";

    /**
     * 这段代码的主要功能是对输入的 ImageProxy 图像进行一系列的处理操作，包括将图像数据转换为 Mat 矩阵，将其转换为灰度图像，进行高斯模糊和 Canny 边缘检测，查找轮廓，统计轮廓数量，最后将轮廓数量更新到 UI 上的 textView 组件中。
     */
    private void processImage(ImageProxy imageProxy) {
        try {
            Classifier classifier = new Classifier(this.getAssets(), MODEL_PATH, "cats_vs_dogs_label.txt", INPUT_SIZE);
            List<Recognition> items = classifier.recognizeImage(true, imageProxyToBitmap(imageProxy));

            if (items != null && items.size() > 0)
                Log.d(TAG, "###识别结果：" + items.get(0).getTitle());

            runOnUiThread(() -> {
                // updating the list of recognised objects
                viewAdapter.submitList(items);
            });
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 100 && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        }
    }

    /**
     * 图像代理到位图
     */
    private Bitmap imageProxyToBitmap(ImageProxy imageProxy) {
        // 获取图像的宽度和高度
        int width = imageProxy.getWidth();
        int height = imageProxy.getHeight();

        // 创建一个与图像大小匹配的缓冲区
        ImageProxy.PlaneProxy[] planes = imageProxy.getPlanes();
        ByteBuffer buffer = planes[0].getBuffer();
        byte[] bytes = new byte[buffer.remaining()];
        buffer.get(bytes);

        // 创建 YuvImage 对象
        YuvImage yuvImage = new YuvImage(bytes, ImageFormat.NV21, width, height, null);

        // 压缩 YuvImage 为 JPEG 格式
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        yuvImage.compressToJpeg(new Rect(0, 0, width, height), 100, out);
        byte[] jpegArray = out.toByteArray();

        // 将 JPEG 数据解码为 Bitmap
        return BitmapFactory.decodeByteArray(jpegArray, 0, jpegArray.length);
    }
}
