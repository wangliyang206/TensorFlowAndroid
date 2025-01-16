package com.cj.mobile.myapplication.util;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ImageFormat;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.YuvImage;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;

import com.cj.mobile.myapplication.listener.RecognitionListener;
import com.cj.mobile.myapplication.model.Recognition;

import org.tensorflow.lite.support.image.TensorImage;
import org.tensorflow.lite.support.label.Category;
import org.tensorflow.lite.task.vision.detector.Detection;
import org.tensorflow.lite.task.vision.detector.ObjectDetector;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * @ProjectName: MyApplication
 * @Package: com.cj.mobile.myapplication
 * @ClassName: ImageAnalyzer
 * @Description: 物体探测
 * @Author: WLY
 * @CreateDate: 2025/1/9 10:25
 */
public class ImageObjectDetection implements ImageAnalysis.Analyzer {
    // 日志记录名称
    private final String TAG = "ImageObjectDetection";

    // TODO 1: Add class variable TensorFlow Lite Model
    private final Context ctx;
    // 回调对象
    private RecognitionListener listener;

    // 物体探测器 模型
    private final static String MODEL_PATH = "ssd_mobilenet_v1.tflite";

    // 显示的最大结果数
    private static final int MAX_RESULT_DISPLAY = 3;
    // 上次分析时间
    private long lastAnalyzeTime = 0;
    // 分析的间隔时间
    private long ANALYZE_INTERVAL = 0;

    // 物体探测器对象
    private ObjectDetector objectDetector;
    // 识别结果列表
    private List<Recognition> items = null;

    public ImageObjectDetection(Context ctx, RecognitionListener listener) {
        this.ctx = ctx;
        this.listener = listener;

        // 间隔30秒
        this.ANALYZE_INTERVAL = TimeUnit.SECONDS.toMillis(5);

        try {
            // Initialization
            ObjectDetector.ObjectDetectorOptions options = ObjectDetector.ObjectDetectorOptions.builder().setMaxResults(MAX_RESULT_DISPLAY).build();
            objectDetector = ObjectDetector.createFromFileAndOptions(ctx, MODEL_PATH, options);
        } catch (IOException e) {
            Log.e(TAG, "TFLite failed to load model with error: " + e.getMessage());
        }
    }

    @Override
    public void analyze(@NonNull ImageProxy imageProxy) {
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastAnalyzeTime >= ANALYZE_INTERVAL) {
            // 执行检测逻辑
            Log.d(TAG, "#####  TFLite 执行检测逻辑！");
            performAnalysis(imageProxy);
            lastAnalyzeTime = currentTime; // 更新上一次检测时间
        }

        // 关闭图像，此操作会告诉CameraX将下一张图像提供给分析仪
        imageProxy.close();
    }

    /**
     * 检测逻辑
     */
    private void performAnalysis(@NonNull ImageProxy imageProxy) {
        // 读取本地图片测试
//        Bitmap image = BitmapFactory.decodeResource(ctx.getResources(), R.drawable.s2);
        // 读取相机图像
        Bitmap image = imageProxyToBitmap(imageProxy);

        TensorImage tensorImage = TensorImage.fromBitmap(image);
        Bitmap bitmap = Bitmap.createBitmap(image.getWidth(), image.getHeight(), Bitmap.Config.ARGB_8888);

        // 创建画布以绘制检测结果
        Canvas canvas = new Canvas(bitmap);
        canvas.drawBitmap(image, 0, 0, null);
//        imageView.setImageBitmap(bitmap);

        // 初始化识别结果列表
        items = new ArrayList<>();
        // Run inference
        List<Detection> results = objectDetector.detect(tensorImage);
        for (Detection detection : results) {
//            Log.i(TAG, detection.getBoundingBox() + "," + detection.getCategories());
            drawRectangle(canvas, detection);
        }

        if (listener != null) {
            listener.onRecognition(items);
        }
    }

    /**
     * 绘制矩形
     */
    private void drawRectangle(Canvas canvas, Detection detection) {
        RectF bBox = detection.getBoundingBox();

        Paint paint = new Paint();
        paint.setColor(Color.GREEN);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(10);
        canvas.drawRect(bBox.left, bBox.top, bBox.right, bBox.bottom, paint);

        paint.setStrokeWidth(3);
        paint.setTextSize(60);

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
        canvas.drawText(title.toString(), bBox.left, bBox.top - 10, paint);
        Log.i(TAG, "####结果=" + title.toString());
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