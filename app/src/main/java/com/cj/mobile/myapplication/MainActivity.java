package com.cj.mobile.myapplication;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.util.Log;
import android.widget.ImageView;
import android.widget.TextView;
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

import com.google.common.util.concurrent.ListenableFuture;
import com.wayz.location.WzException;
import com.wayz.location.WzLocation;
import com.wayz.location.WzLocationClient;
import com.wayz.location.WzLocationClientOption;
import com.wayz.location.WzLocationListener;

import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.CvException;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfInt;
import org.opencv.core.MatOfInt4;
import org.opencv.core.MatOfPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutionException;

public class MainActivity extends AppCompatActivity {
    private WzLocationClientOption option;

    private PreviewView previewView;
    private TextView textView;
    private ImageView imageView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        previewView = findViewById(R.id.previewView);
        textView = findViewById(R.id.textView);
        imageView = findViewById(R.id.imageView);

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

        // #以下是定位的逻辑
        option = new WzLocationClientOption();
        option.setInterval(30000);
        // 设置间隔秒定位
        option.setNeedPosition(true);

        WzLocationClient client = new WzLocationClient(getApplicationContext(), option);
        client.startLocation(new WzLocationListener() {
            @Override
            public void onLocationReceived(WzLocation wzLocation) {
                System.out.println("定位成功：" + wzLocation.getLatitude() + "," + wzLocation.getLongitude());
            }

            @Override
            public void onLocationError(WzException e) {
                System.out.println("定位失败：" + e.getErrorMessage());
            }
        });
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

        // 设置图像分析器
        imageAnalyzer.setAnalyzer(ContextCompat.getMainExecutor(this), image -> {
            // 获取图像数据并进行处理
            processImage(image);
            // 处理完成后关闭 ImageProxy
            image.close();
        });

        cameraProvider.unbindAll();
        cameraProvider.bindToLifecycle((LifecycleOwner) this, cameraSelector, preview, imageAnalyzer);
    }

    /**
     * 手指计数
     */
    private int fingerCount = 0;

    /**
     * 这段代码的主要功能是对输入的 ImageProxy 图像进行一系列的处理操作，包括将图像数据转换为 Mat 矩阵，将其转换为灰度图像，进行高斯模糊和 Canny 边缘检测，查找轮廓，统计轮廓数量，最后将轮廓数量更新到 UI 上的 textView 组件中。
     *
     * @param image
     */
    private void processImage(ImageProxy image) {
        ImageProxy.PlaneProxy[] planes = image.getPlanes();
        ByteBuffer buffer = planes[0].getBuffer();
        byte[] data = new byte[buffer.capacity()];
        buffer.get(data);

        Mat mat = new Mat(image.getHeight(), image.getWidth(), CvType.CV_8UC1);
        mat.put(0, 0, data);

        // Convert to grayscale
        Mat grayMat = new Mat();
        Imgproc.cvtColor(mat, grayMat, Imgproc.COLOR_YUV2GRAY_NV21);

        // Enhance contrast using CLAHE
        Mat claheMat = new Mat();
        Imgproc.createCLAHE().apply(grayMat, claheMat);

        // Apply Gaussian Blur
        Mat blurredMat = new Mat();
        Imgproc.GaussianBlur(grayMat, blurredMat, new org.opencv.core.Size(5, 5), 0);

        // Apply Canny edge detection
        Mat edgesMat = new Mat();
        Imgproc.Canny(blurredMat, edgesMat, 100, 200);

        // Find contours
        List<MatOfPoint> contours = new ArrayList<>();
        Mat hierarchy = new Mat();
        Imgproc.findContours(edgesMat, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);

        // Count objects
//        int objectCount = contours.size();

        // 创建原始图像的副本以绘制轮廓
        Mat resultMat = new Mat();
        mat.copyTo(resultMat);

        fingerCount = 0;
        // 迭代轮廓并检测手指
        for (MatOfPoint contour : contours) {
            // 近似轮廓
            MatOfPoint2f approxCurve = new MatOfPoint2f();
            MatOfPoint2f contour2f = new MatOfPoint2f(contour.toArray());
            Imgproc.approxPolyDP(contour2f, approxCurve, Imgproc.arcLength(contour2f, true) * 0.02, true);

            // 检查近似轮廓是否为简单轮廓
            if (approxCurve.toArray().length >= 5) { // 至少5个点才能形成凸包缺陷
                // 计算凸包
                MatOfInt hull = new MatOfInt();
                Imgproc.convexHull(new MatOfPoint(approxCurve.toArray()), hull);

                // 检查凸包是否有效
                if (isConvexHullValid(hull)) {
                    MatOfInt4 convexityDefects = new MatOfInt4();
                    try {
                        Imgproc.convexityDefects(new MatOfPoint(approxCurve.toArray()), hull, convexityDefects);

                        int defectCount = 0;
                        for (int i = 0; i < convexityDefects.size().height; i++) {
                            double[] defect = convexityDefects.get(i, 0);
                            double startIdx = defect[0];
                            double endIdx = defect[1];
                            double farIdx = defect[2];
                            double depth = defect[3];

                            if (depth > 30 * 256) { // 根据实际情况调整阈值
                                defectCount++;
                            }
                        }

                        if (defectCount >= 2) { // 至少有两个缺陷才认为是手指
                            fingerCount++;
                            Imgproc.drawContours(resultMat, Arrays.asList(contour), -1, new Scalar(0, 255, 0), 2);
                        }
                    } catch (CvException e) {
                        Log.e("processImage","凸包计算失败: " + e.getMessage());
                    }
                }
            }
        }

        // Update UI with object count
        runOnUiThread(() -> {
            textView.setText("手指数量: " + fingerCount);

            // Update UI with the processed image
            Bitmap resultBitmap = Bitmap.createBitmap(resultMat.cols(), resultMat.rows(), Bitmap.Config.ARGB_8888);
            Utils.matToBitmap(resultMat, resultBitmap);
            imageView.setImageBitmap(resultBitmap);
        });
    }

    /**
     * 凸包有效吗
     */
    private boolean isConvexHullValid(MatOfInt hull) {
        int[] indices = hull.toArray();
        for (int i = 0; i < indices.length - 1; i++) {
            if (indices[i] >= indices[i + 1]) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 100 && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        }
    }
}