package com.cj.mobile.myapplication.util;

import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.util.Log;

import com.cj.mobile.myapplication.model.Recognition;

import org.tensorflow.lite.DataType;
import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.support.image.TensorImage;
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

/**
 * @ProjectName: MyApplication
 * @Package: com.cj.mobile.myapplication.util
 * @ClassName: Classifier
 * @Description: TensorFlow Lite 视觉识别，从【tfbook-master】项目移植过来的
 * @Author: WLY
 * @CreateDate: 2025/1/9 16:42
 */
public class Classifier {
    private Interpreter interpreter;
    private List<String> labelList;
    private final int INPUT_SIZE;
    private final int PIXEL_SIZE = 3;
    private final int IMAGE_MEAN = 0;
    private final float IMAGE_STD = 255.0f;
    private final int MAX_RESULTS = 3;
    private final float THRESHOLD = 0.4f;

    /**
     * 初始化模型，不包含标签
     */
    public Classifier(AssetManager assetManager, String modelPath, int inputSize) throws IOException {
        this.INPUT_SIZE = inputSize;
        Interpreter.Options options = new Interpreter.Options();
        options.setNumThreads(5);
        options.setUseNNAPI(true);
        interpreter = new Interpreter(loadModelFile(assetManager, modelPath), options);
    }

    /**
     * 初始化模型，包含标签
     */
    public Classifier(AssetManager assetManager, String modelPath, String labelPath, int inputSize) throws IOException {
        this.INPUT_SIZE = inputSize;
        Interpreter.Options options = new Interpreter.Options();
        options.setNumThreads(5);
        options.setUseNNAPI(true);
        interpreter = new Interpreter(loadModelFile(assetManager, modelPath), options);
        labelList = loadLabelList(assetManager, labelPath);
    }

    /**
     * 加载模型文件
     */
    private MappedByteBuffer loadModelFile(AssetManager assetManager, String modelPath) throws IOException {
        AssetFileDescriptor fileDescriptor = assetManager.openFd(modelPath);
        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel = inputStream.getChannel();
        long startOffset = fileDescriptor.getStartOffset();
        long declaredLength = fileDescriptor.getDeclaredLength();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
    }

    /**
     * 加载标签列表
     */
    private List<String> loadLabelList(AssetManager assetManager, String labelPath) throws IOException {
        List<String> labelList = new ArrayList<>();
        java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(assetManager.open(labelPath)));
        String line;
        while ((line = reader.readLine()) != null) {
            labelList.add(line);
        }
        reader.close();
        return labelList;
    }

    /**
     * 识别图像
     *
     * @param isLabel 是否包含标签
     * @param bitmap  图像
     * @return 结果
     */
    public List<Recognition> recognizeImage(boolean isLabel, Bitmap bitmap) {
        Bitmap scaledBitmap = Bitmap.createScaledBitmap(bitmap, INPUT_SIZE, INPUT_SIZE, false);
        ByteBuffer byteBuffer = convertBitmapToByteBuffer(isLabel, scaledBitmap);

        if (isLabel) {
            float[][] result = new float[2][labelList.size()];
            interpreter.run(byteBuffer, result);
            return getSortedResult(result);
        } else {
            // 运行推理
            // #1
            Object[] inputArray = {byteBuffer};
            TensorBuffer outputBuffer = TensorBuffer.createFixedSize(new int[]{1, 1001}, DataType.FLOAT32);  // 假设输出是 1000 个类别概率，根据实际模型调整
            interpreter.run(inputArray, outputBuffer.getBuffer());

            // 处理输出结果
            Map<Integer, Float> labeledProbabilities = getLabelProbabilities(outputBuffer);
            List<Map.Entry<Integer, Float>> sortedLabels = sortLabels(labeledProbabilities);

            // 打印或显示分类结果
            for (Map.Entry<Integer, Float> entry : sortedLabels) {
                Log.e("####", entry.getKey() + ": " + entry.getValue());
            }
            return null;


            // #2
//            // 创建输入和输出张量缓冲区
//            TensorImage tensorImage = TensorImage.fromBitmap(scaledBitmap);
//            TensorBuffer inputBuffer = TensorBuffer.createFixedSize(new int[]{1, 224, 224, 3}, DataType.FLOAT32);
//            inputBuffer.loadBuffer(tensorImage.getBuffer());
//            float[][] outputArray = new float[1][1001]; // 假设输出是一个 1x10 的矩阵，根据实际模型修改
//            interpreter.run(inputBuffer.getBuffer(), outputArray);
//            return getSortedResult(outputArray);
        }
    }

    /**
     * 获取标签概率
     */
    private Map<Integer, Float> getLabelProbabilities(TensorBuffer outputBuffer) {
        // 假设输出是 softmax 结果，每个元素代表一个类别的概率
        float[] scores = outputBuffer.getFloatArray();
        Map<Integer, Float> labelProbabilities = new HashMap<>();
        for (int i = 0; i < scores.length; i++) {
            labelProbabilities.put(i, scores[i]);
        }
        return labelProbabilities;
    }

    /**
     * 排序标签
     */
    private List<Map.Entry<Integer, Float>> sortLabels(Map<Integer, Float> labelProbabilities) {
        List<Map.Entry<Integer, Float>> list = new ArrayList<>(labelProbabilities.entrySet());
        list.sort((entry1, entry2) -> Float.compare(entry2.getValue(), entry1.getValue()));
        return list;
    }

    /**
     * 将bitmap转换成Bytebuffer
     * 将位图转换为字节缓冲区
     */
    private ByteBuffer convertBitmapToByteBuffer(boolean isLabel, Bitmap bitmap) {
        ByteBuffer byteBuffer = ByteBuffer.allocateDirect(4 * INPUT_SIZE * INPUT_SIZE * PIXEL_SIZE);
        byteBuffer.order(ByteOrder.nativeOrder());

        int[] intValues = new int[INPUT_SIZE * INPUT_SIZE];
        bitmap.getPixels(intValues, 0, bitmap.getWidth(), 0, 0, bitmap.getWidth(), bitmap.getHeight());

        if (isLabel) {
            // 有标签
            int pixel = 0;
            for (int i = 0; i < INPUT_SIZE; ++i) {
                for (int j = 0; j < INPUT_SIZE; ++j) {
                    final int val = intValues[pixel++];
                    byteBuffer.putFloat((((val >> 16) & 0xFF) - IMAGE_MEAN) / IMAGE_STD);
                    byteBuffer.putFloat((((val >> 8) & 0xFF) - IMAGE_MEAN) / IMAGE_STD);
                    byteBuffer.putFloat(((val & 0xFF) - IMAGE_MEAN) / IMAGE_STD);
                }
            }
        } else {
            // 无标签
            for (int value : intValues) {
                byteBuffer.putFloat(((value >> 16) & IMAGE_MEAN) / IMAGE_STD);
                byteBuffer.putFloat(((value >> 8) & IMAGE_MEAN) / IMAGE_STD);
                byteBuffer.putFloat((value & IMAGE_MEAN) / IMAGE_STD);
            }
        }

        return byteBuffer;
    }

    /**
     * 获取排序结果
     */
    private List<Recognition> getSortedResult(float[][] labelProbArray) {
        Log.d("Classifier", String.format("List Size:(%d, %d, %d)", labelProbArray.length, labelProbArray[0].length, labelList.size()));

        PriorityQueue<Recognition> pq = new PriorityQueue<>(
                MAX_RESULTS,
                (o1, o2) -> Float.compare(o2.getConfidence(), o1.getConfidence())
        );

        for (int i = 0; i < labelList.size(); ++i) {
            float confidence = labelProbArray[0][i];
            if (confidence >= THRESHOLD) {
                pq.add(new Recognition("" + i, labelList.size() > i ? labelList.get(i) : "Unknown", confidence));
            }
        }
        Log.d("Classifier", String.format("pqsize:(%d)", pq.size()));

        List<Recognition> recognitions = new ArrayList<>();
        int recognitionsSize = Math.min(pq.size(), MAX_RESULTS);
        for (int i = 0; i < recognitionsSize; ++i) {
            recognitions.add(pq.poll());
        }
        return recognitions;
    }
}

