package com.cj.mobile.myapplication;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.pm.PackageManager;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.YuvImage;
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
import com.cj.mobile.myapplication.util.TfLiteModel;
import com.cj.mobile.myapplication.util.YuvToRgbConverter;
import com.google.common.util.concurrent.ListenableFuture;

import org.opencv.android.OpenCVLoader;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

/**
 * @ProjectName: TensorFlowAndroid
 * @Package: com.cj.mobile.myapplication
 * @ClassName: HumanJointsActivity
 * @Description: 人体关节功能
 * @Author: WLY
 * @CreateDate: 2025/1/17 16:29
 */
public class HumanJointsActivity extends AppCompatActivity {
    private final String TAG = "HumanJointsActivity";
    private PreviewView previewView;
    private RecyclerView resultRecyclerView;
    private RecognitionAdapter viewAdapter;

    // 上次分析时间
    private long lastAnalyzeTime = 0;
    // 分析的间隔时间
    private long ANALYZE_INTERVAL = 0;

    // Yuv转Rgb转换器
    private YuvToRgbConverter yuvToRgbConverter;
    private Bitmap bitmapBuffer;
    // 模型
    private final static String MODEL_PATH = "mobile_face_net.tflite";
    private TfLiteModel tflite;

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

    private final int INPUT_SIZE = 112;
    private final int PIXEL_SIZE = 3;
    private final int IMAGE_MEAN = 0;
    private final float IMAGE_STD = 255.0f;

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

        try {
            tflite = new TfLiteModel(getApplicationContext(), MODEL_PATH, INPUT_SIZE);
        } catch (IOException e) {
            e.printStackTrace();
        }

        imageAnalyzer.setAnalyzer(ContextCompat.getMainExecutor(this), image -> {
            long currentTime = System.currentTimeMillis();
            if (currentTime - lastAnalyzeTime >= ANALYZE_INTERVAL) {
                // 执行检测逻辑
                Log.d(TAG, "#####  TFLite 执行检测逻辑！");

                // 假设你有一个Bitmap图像
//                Bitmap bitmap = BitmapFactory.decodeResource(getResources(), R.drawable.sample_image);
                Bitmap bitmap = imageProxyToBitmap(image);
                // 开始分析
                float[][] results = tflite.runInference(bitmap);

                // Process results (e.g., find the highest probability label)
                float maxProbability = Float.MIN_VALUE;
                int bestLabel = -1;
                for (int i = 0; i < results[0].length; i++) {
                    if (results[0][i] > maxProbability) {
                        maxProbability = results[0][i];
                        bestLabel = i;
                    }
                }

                Log.d(TAG, "Best label: " + bestLabel + ", Probability: " + maxProbability);

                lastAnalyzeTime = currentTime; // 更新上一次检测时间
            }

            // 处理完成后关闭 ImageProxy
            image.close();
        });

        cameraProvider.unbindAll();
        cameraProvider.bindToLifecycle((LifecycleOwner) this, cameraSelector, preview, imageAnalyzer);
    }


    /**
     * 将bitmap转换成Bytebuffer
     * 将位图转换为字节缓冲区
     */
    private ByteBuffer convertBitmapToByteBuffer(Bitmap bitmap) {
        ByteBuffer byteBuffer = ByteBuffer.allocateDirect(4 * INPUT_SIZE * INPUT_SIZE * PIXEL_SIZE);
        byteBuffer.order(ByteOrder.nativeOrder());

        int[] intValues = new int[INPUT_SIZE * INPUT_SIZE];
        bitmap.getPixels(intValues, 0, bitmap.getWidth(), 0, 0, bitmap.getWidth(), bitmap.getHeight());

        for (int value : intValues) {
            byteBuffer.putFloat(((value >> 16) & IMAGE_MEAN) / IMAGE_STD);
            byteBuffer.putFloat(((value >> 8) & IMAGE_MEAN) / IMAGE_STD);
            byteBuffer.putFloat((value & IMAGE_MEAN) / IMAGE_STD);
        }

        return byteBuffer;
    }

    private MappedByteBuffer loadModelFile() throws IOException {
        AssetFileDescriptor fileDescriptor = this.getAssets().openFd(MODEL_PATH);
        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel = inputStream.getChannel();
        long startOffset = fileDescriptor.getStartOffset();
        long declaredLength = fileDescriptor.getDeclaredLength();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
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
