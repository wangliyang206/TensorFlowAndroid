package com.cj.mobile.myapplication.util;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;

import org.tensorflow.lite.DataType;
import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.support.common.FileUtil;
import org.tensorflow.lite.support.image.TensorImage;
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer;

import java.io.IOException;
import java.nio.MappedByteBuffer;

/**
 * @ProjectName: TensorFlowAndroid
 * @Package: com.cj.mobile.myapplication.util
 * @ClassName: TfLiteModel
 * @Description:
 * @Author: WLY
 * @CreateDate: 2025/1/20 10:35
 */

public class TfLiteModel {

    private Interpreter tflite;
    private int inputSize;

    public TfLiteModel(Context context, String modelPath, int inputSize) throws IOException {
        this.inputSize = inputSize;
        // Load the TensorFlow Lite model.
        MappedByteBuffer tfliteModel = FileUtil.loadMappedFile(context, modelPath);
        this.tflite = new Interpreter(tfliteModel);
    }

    // Preprocess the image to be of the shape expected by the model.
    private Bitmap preprocessBitmap(Bitmap bitmap) {
        Bitmap resizedBitmap = Bitmap.createScaledBitmap(bitmap, inputSize, inputSize, true);
        Bitmap rgbBitmap = Bitmap.createBitmap(resizedBitmap.getWidth(), resizedBitmap.getHeight(), Bitmap.Config.ARGB_8888);
        for (int x = 0; x < resizedBitmap.getWidth(); x++) {
            for (int y = 0; y < resizedBitmap.getHeight(); y++) {
                int pixel = resizedBitmap.getPixel(x, y);

                // Convert pixel to float values in range [0, 1]
                final float red = ((pixel >> 16) & 0xFF) / 255.0f;
                final float green = ((pixel >> 8) & 0xFF) / 255.0f;
                final float blue = (pixel & 0xFF) / 255.0f;

                // Normalize to [-1, 1]
                rgbBitmap.setPixel(x, y, Color.rgb((int) (red * 255 - 127),
                        (int) (green * 255 - 127),
                        (int) (blue * 255 - 127)));
            }
        }
        return rgbBitmap;
    }

    public float[][] runInference(Bitmap bitmap) {
        Bitmap processedBitmap = preprocessBitmap(bitmap);

        // Create a TensorImage from the Bitmap
        TensorImage tensorImage = new TensorImage(DataType.UINT8);
        tensorImage.load(processedBitmap);

        // Create an output buffer
        TensorBuffer outputBuffer = TensorBuffer.createFixedSize(new int[]{1, getLabelCount()}, DataType.FLOAT32);

        // Run inference
        tflite.run(tensorImage.getBuffer(), outputBuffer.getBuffer());

        // Get the results
        float[][] results = new float[1][getLabelCount()];
        // 先将数据复制到一维数组
        float[] tempArray = outputBuffer.getFloatArray();
        // 再将一维数组的数据赋值给二维数组的第一个元素
        System.arraycopy(tempArray, 0, results[0], 0, tempArray.length);

        return results;
    }

    private int getLabelCount() {
        // This should be the number of labels your model outputs. Adjust as needed.
        return 1001; // Example: 1000 classes + 1 background for ImageNet-like models
    }

    /**
     * 调用方式
     * @param args
     */
    public static void main(String[] args) {
//        try {
//            // Load the model from the assets folder
//            String modelPath = "path/to/your/model.tflite";
//            int inputSize = 224; // Example input size
//
//            TfLiteModel model = new TfLiteModel(getContext(),modelPath, inputSize);
//
//            // Load an example image
//            Bitmap bitmap = BitmapFactory.decodeFile("path/to/your/image.jpg");
//
//            // Run inference
//            float[][] results = model.runInference(bitmap);
//
//            // Process results (e.g., find the highest probability label)
//            float maxProbability = Float.MIN_VALUE;
//            int bestLabel = -1;
//            for (int i = 0; i < results[0].length; i++) {
//                if (results[0][i] > maxProbability) {
//                    maxProbability = results[0][i];
//                    bestLabel = i;
//                }
//            }
//
//            System.out.println("Best label: " + bestLabel + ", Probability: " + maxProbability);
//
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
    }
}
