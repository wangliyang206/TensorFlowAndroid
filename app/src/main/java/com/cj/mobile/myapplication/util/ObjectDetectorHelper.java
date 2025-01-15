package com.cj.mobile.myapplication.util;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.SystemClock;
import android.util.Log;

import org.tensorflow.lite.gpu.CompatibilityList;
import org.tensorflow.lite.support.image.ImageProcessor;
import org.tensorflow.lite.support.image.TensorImage;
import org.tensorflow.lite.support.image.ops.Rot90Op;
import org.tensorflow.lite.task.core.BaseOptions;
import org.tensorflow.lite.task.vision.detector.Detection;
import org.tensorflow.lite.task.vision.detector.ObjectDetector;

import java.io.IOException;
import java.util.List;

/**
 * 物体探测器助手
 */
public class ObjectDetectorHelper {
    public static final int DELEGATE_CPU = 0;
    public static final int DELEGATE_GPU = 1;
    public static final int DELEGATE_NNAPI = 2;
    public static final int MODEL_MOBILENETV1 = 0;
    public static final int MODEL_EFFICIENTDETV0 = 1;
    public static final int MODEL_EFFICIENTDETV1 = 2;
    public static final int MODEL_EFFICIENTDETV2 = 3;

    // 分数阈值
    private float threshold = 0.5f;
    // 线程数
    private int numThreads = 2;
    // 最大结果
    private int maxResults = 3;
    // 类型：0代表委派CPU；1代表委派GPU；2代表Delegate NNAPI
    private int currentDelegate = 0;
    // 目标检测模型：0代表MobileNetV1；1代表EfficientDetV0；2代表EfficientDetV1；3代表EfficientDetV2
    private int currentModel = 0;
    private final Context context;
    private final DetectorListener objectDetectorListener;
    private ObjectDetector objectDetector;

    public ObjectDetectorHelper(float threshold, int numThreads, int maxResults, int currentDelegate, int currentModel, Context context, DetectorListener objectDetectorListener) {
        this.threshold = threshold;
        this.numThreads = numThreads;
        this.maxResults = maxResults;
        this.currentDelegate = currentDelegate;
        this.currentModel = currentModel;
        this.context = context;
        this.objectDetectorListener = objectDetectorListener;
        setupObjectDetector();
    }

    /**
     * 清理物体探测器
     */
    public void clearObjectDetector() {
        objectDetector = null;
    }

    /**
     * 设置对象检测器
     */
    public void setupObjectDetector() {
        ObjectDetector.ObjectDetectorOptions.Builder optionsBuilder =
                ObjectDetector.ObjectDetectorOptions.builder()
                        .setScoreThreshold(threshold)
                        .setMaxResults(maxResults);

        BaseOptions.Builder baseOptionsBuilder = BaseOptions.builder().setNumThreads(numThreads);

        switch (currentDelegate) {
            case DELEGATE_CPU:
                // Default
                break;
            case DELEGATE_GPU:
                if (new CompatibilityList().isDelegateSupportedOnThisDevice()) {
                    baseOptionsBuilder.useGpu();
                } else {
                    objectDetectorListener.onError("GPU is not supported on this device");
                }
                break;
            case DELEGATE_NNAPI:
                baseOptionsBuilder.useNnapi();
                break;
        }

        optionsBuilder.setBaseOptions(baseOptionsBuilder.build());

        String modelName;
        switch (currentModel) {
            case MODEL_MOBILENETV1:
                // 识别能力：SSD_Mobilenet_v1模型能够识别并定位图像中的多种目标对象，如行人、车辆、动物等。其广泛应用于实时目标检测任务，如行人检测、物体识别和人脸检测等。
                modelName = "ssd_mobilenet_v1.tflite";
                break;
            case MODEL_EFFICIENTDETV0:
                /*
                 * 介绍：
                 * 1、EfficientDet-Lite系列模型能够识别并定位图像中的多种目标对象，包括但不限于行人、车辆、动物、家具等。
                 * 2、由于其高效性和准确性，这些模型适用于多种应用场景，如智能安防、自动驾驶、图像搜索与识别等。
                 *
                 * 不同版本的差异：
                 * 1、Lite0、Lite1、Lite2等版本主要在模型大小、计算复杂度和检测精度上有所不同。随着版本号的增加，模型通常会变得更大、更复杂，但也会提供更高的检测精度。
                 * 2、这些Lite版本是为了在资源受限的设备（如移动设备和嵌入式系统）上部署而设计的，它们在保证一定检测精度的同时，降低了计算和存储成本。
                 */
                modelName = "efficientdet-lite0.tflite";
                break;
            case MODEL_EFFICIENTDETV1:
                modelName = "efficientdet-lite1.tflite";
                break;
            case MODEL_EFFICIENTDETV2:
                modelName = "efficientdet-lite2.tflite";
                break;
            default:
                modelName = "ssd_mobilenet_v1.tflite";
                break;
        }

        try {
            objectDetector = ObjectDetector.createFromFileAndOptions(context, modelName, optionsBuilder.build());
        } catch (IllegalStateException | IOException e) {
            objectDetectorListener.onError("Object detector failed to initialize. See error logs for details");
            Log.e("Test", "TFLite failed to load model with error: " + e.getMessage());
        }
    }

    public void detect(Bitmap image, int imageRotation) {
        if (objectDetector == null) {
            setupObjectDetector();
        }

        long inferenceTime = SystemClock.uptimeMillis();

        ImageProcessor imageProcessor =
                new ImageProcessor.Builder()
                        .add(new Rot90Op(-imageRotation / 90))
                        .build();

        TensorImage tensorImage = imageProcessor.process(TensorImage.fromBitmap(image));

        List<Detection> results = objectDetector.detect(tensorImage);
        inferenceTime = SystemClock.uptimeMillis() - inferenceTime;
        objectDetectorListener.onResults(results, inferenceTime, tensorImage.getHeight(), tensorImage.getWidth());
    }

    public interface DetectorListener {
        void onError(String error);

        void onResults(List<Detection> results, long inferenceTime, int imageHeight, int imageWidth);
    }
}

