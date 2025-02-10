package com.cj.mobile.myapplication;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.pm.PackageManager;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.YuvImage;
import android.media.Image;
import android.os.Bundle;
import android.util.Log;
import android.util.Pair;
import android.util.Size;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleOwner;

import com.cj.mobile.myapplication.model.SimilarityClassifier;
import com.google.android.gms.tasks.Task;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.face.Face;
import com.google.mlkit.vision.face.FaceDetection;
import com.google.mlkit.vision.face.FaceDetector;
import com.google.mlkit.vision.face.FaceDetectorOptions;

import org.opencv.android.OpenCVLoader;
import org.tensorflow.lite.Interpreter;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.ReadOnlyBufferException;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

/**
 * @ProjectName: TensorFlowAndroid
 * @Package: com.cj.mobile.myapplication
 * @ClassName: HumanJointsActivity
 * @Description: 捕捉人脸
 * @Author: WLY
 * @CreateDate: 2025/1/17 16:29
 */
public class HumanJointsActivity extends AppCompatActivity {
    private final String TAG = "HumanJointsActivity";
    private PreviewView previewView;
    // 轮廓标识、片段
    private ImageView imageOutline, imageFragment;
    private ProcessCameraProvider cameraProvider;
    // 摄像头默认 反面
    private int mCamFace = CameraSelector.LENS_FACING_BACK;

    // 模型
    private final static String MODEL_PATH = "mobile_face_net.tflite";
    private Interpreter tfLite;
    private FaceDetector detector;

    private boolean start = true;
    private boolean flipX = false;

    private boolean developerMode = false;
    private float distance = 1.0f;
    // saved Faces
    private HashMap<String, SimilarityClassifier.Recognition> registered = new HashMap<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_face);

        previewView = findViewById(R.id.view_faceactivity_finder);
        imageOutline = findViewById(R.id.image_faceactivity_outline);
        imageFragment = findViewById(R.id.image_faceactivity_fragment);
        findViewById(R.id.btn_faceactivity_reversal).setOnClickListener(v -> {
            // 翻转摄像头
            if (mCamFace == CameraSelector.LENS_FACING_BACK) {
                mCamFace = CameraSelector.LENS_FACING_FRONT;
                flipX = true;
            } else {
                mCamFace = CameraSelector.LENS_FACING_BACK;
                flipX = false;
            }
            cameraProvider.unbindAll();
            startCamera();
        });

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

        // 加载模型
        try {
            tfLite = new Interpreter(loadModelFile());
        } catch (IOException e) {
            e.printStackTrace();
        }
        // 初始化人脸检测器
        FaceDetectorOptions highAccuracyOpts =
                new FaceDetectorOptions.Builder()
                        .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
                        .build();
        detector = FaceDetection.getClient(highAccuracyOpts);
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);

        cameraProviderFuture.addListener(() -> {
            try {
                cameraProvider = cameraProviderFuture.get();
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
                .requireLensFacing(mCamFace)
                .build();

        // 图像分析
        ImageAnalysis imageAnalyzer = new ImageAnalysis.Builder()
                .setTargetResolution(new Size(640, 480))
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build();

        imageAnalyzer.setAnalyzer(ContextCompat.getMainExecutor(this), imageProxy -> {
            // 执行检测逻辑
            Log.d(TAG, "#####  TFLite 执行检测逻辑！");

            InputImage image = null;

            // Camera Feed-->Analyzer-->ImageProxy-->mediaImage-->InputImage(needed for ML kit face detection)
            @SuppressLint("UnsafeOptInUsageError") Image mediaImage = imageProxy.getImage();

            if (mediaImage != null) {
                image = InputImage.fromMediaImage(mediaImage, imageProxy.getImageInfo().getRotationDegrees());
            }

            // 处理采集的图像以检测人脸
            Task<List<Face>> result = detector.process(image)
                    .addOnSuccessListener(faces -> {
                        Log.d(TAG, "#####  faces=" + faces.size());
                        if (faces.size() != 0) {

                            // 从检测到的人脸中获取第一张人脸
                            Face face = faces.get(0);

                            // 媒体图像到位图
                            Bitmap mFrameBmp = toBitmap(mediaImage);

                            int rot = imageProxy.getImageInfo().getRotationDegrees();

                            // 调整面部方向
                            Bitmap frame_bmp1 = rotateBitmap(mFrameBmp, rot, false, false);

                            // 获取人脸的边界框
                            RectF boundingBox = new RectF(face.getBoundingBox());

                            // 从整个位图（图像）中裁剪出边界框
                            Bitmap mCroppedFace = getCropBitmapByCPU(frame_bmp1, boundingBox);

                            // 翻转图像以匹配人脸
                            if (flipX)
                                mCroppedFace = rotateBitmap(mCroppedFace, 0, flipX, false);

                            // 显示人脸轮廓
                            displayFacialContours(boundingBox);

                            // 将获取的人脸缩放到112*112，这是模型所需的输入
                            Bitmap scaled = getResizedBitmap(mCroppedFace, 112, 112);

                            if (start)
                                // 发送缩放位图以创建面部嵌入
                                recognizeImage(scaled);

                        } else {
                            imageOutline.setImageBitmap(null);
                        }
                    })
                    .addOnFailureListener(e -> {
                        start = true;
                        Log.e(TAG, "#####  TFLite 检测失败=" + e.getMessage());
                    })
                    .addOnCompleteListener(task -> {
                        Log.d(TAG, "#####  TFLite 检测完成！");

                        // 处理完成后关闭 ImageProxy
                        imageProxy.close();
                    });
        });

        cameraProvider.unbindAll();
        cameraProvider.bindToLifecycle((LifecycleOwner) this, cameraSelector, preview, imageAnalyzer);
    }

    /**
     * 显示面部轮廓
     */
    private void displayFacialContours(RectF boundingBox) {
        // 创建一个 Bitmap 对象
        Bitmap bitmap = Bitmap.createBitmap(previewView.getWidth(), previewView.getHeight(), Bitmap.Config.ARGB_8888);
        // 创建一个 Canvas 对象
        Canvas canvas = new Canvas(bitmap);
        // 将原始 Canvas 的内容绘制到新的 Canvas 上
        canvas.drawBitmap(bitmap, 0, 0, null);
        imageOutline.setImageBitmap(bitmap);
        Paint paint = new Paint();
        paint.setColor(Color.GREEN);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(5);
        canvas.drawRect(
                flipX ? (previewView.getWidth() - boundingBox.left) : boundingBox.left,
                boundingBox.top,
                flipX ? (previewView.getWidth() - boundingBox.right) : boundingBox.right,
                boundingBox.bottom,
                paint);
        paint.setStrokeWidth(2);
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
     * 重要。如果转换未完成，则toBitmap转换在某些设备上不起作用。
     */
    private static byte[] YUV_420_888toNV21(Image image) {

        int width = image.getWidth();
        int height = image.getHeight();
        int ySize = width * height;
        int uvSize = width * height / 4;

        byte[] nv21 = new byte[ySize + uvSize * 2];

        ByteBuffer yBuffer = image.getPlanes()[0].getBuffer(); // Y
        ByteBuffer uBuffer = image.getPlanes()[1].getBuffer(); // U
        ByteBuffer vBuffer = image.getPlanes()[2].getBuffer(); // V

        int rowStride = image.getPlanes()[0].getRowStride();
        assert (image.getPlanes()[0].getPixelStride() == 1);

        int pos = 0;

        if (rowStride == width) { // likely
            yBuffer.get(nv21, 0, ySize);
            pos += ySize;
        } else {
            long yBufferPos = -rowStride; // not an actual position
            for (; pos < ySize; pos += width) {
                yBufferPos += rowStride;
                yBuffer.position((int) yBufferPos);
                yBuffer.get(nv21, pos, width);
            }
        }

        rowStride = image.getPlanes()[2].getRowStride();
        int pixelStride = image.getPlanes()[2].getPixelStride();

        assert (rowStride == image.getPlanes()[1].getRowStride());
        assert (pixelStride == image.getPlanes()[1].getPixelStride());

        if (pixelStride == 2 && rowStride == width && uBuffer.get(0) == vBuffer.get(1)) {
            // maybe V an U planes overlap as per NV21, which means vBuffer[1] is alias of uBuffer[0]
            byte savePixel = vBuffer.get(1);
            try {
                vBuffer.put(1, (byte) ~savePixel);
                if (uBuffer.get(0) == (byte) ~savePixel) {
                    vBuffer.put(1, savePixel);
                    vBuffer.position(0);
                    uBuffer.position(0);
                    vBuffer.get(nv21, ySize, 1);
                    uBuffer.get(nv21, ySize + 1, uBuffer.remaining());

                    return nv21; // shortcut
                }
            } catch (ReadOnlyBufferException ex) {
                // unfortunately, we cannot check if vBuffer and uBuffer overlap
            }

            // unfortunately, the check failed. We must save U and V pixel by pixel
            vBuffer.put(1, savePixel);
        }

        // other optimizations could check if (pixelStride == 1) or (pixelStride == 2),
        // but performance gain would be less significant

        for (int row = 0; row < height / 2; row++) {
            for (int col = 0; col < width / 2; col++) {
                int vuPos = col * pixelStride + row * rowStride;
                nv21[pos++] = vBuffer.get(vuPos);
                nv21[pos++] = uBuffer.get(vuPos);
            }
        }

        return nv21;
    }

    /**
     * 转成Bitmap格式
     */
    private Bitmap toBitmap(Image image) {

        byte[] nv21 = YUV_420_888toNV21(image);

        YuvImage yuvImage = new YuvImage(nv21, ImageFormat.NV21, image.getWidth(), image.getHeight(), null);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        yuvImage.compressToJpeg(new Rect(0, 0, yuvImage.getWidth(), yuvImage.getHeight()), 75, out);

        byte[] imageBytes = out.toByteArray();
        //System.out.println("bytes"+ Arrays.toString(imageBytes));

        //System.out.println("FORMAT"+image.getFormat());

        return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);
    }

    /**
     * 旋转位图
     */
    private static Bitmap rotateBitmap(Bitmap bitmap, int rotationDegrees, boolean flipX, boolean flipY) {
        Matrix matrix = new Matrix();

        // Rotate the image back to straight.
        matrix.postRotate(rotationDegrees);

        // Mirror the image along the X or Y axis.
        matrix.postScale(flipX ? -1.0f : 1.0f, flipY ? -1.0f : 1.0f);
        Bitmap rotatedBitmap =
                Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);

        // Recycle the old bitmap if it has changed.
        if (rotatedBitmap != bitmap) {
            bitmap.recycle();
        }
        return rotatedBitmap;
    }

    /**
     * CPU获取裁剪位图
     */
    private static Bitmap getCropBitmapByCPU(Bitmap source, RectF cropRectF) {
        Bitmap resultBitmap = Bitmap.createBitmap((int) cropRectF.width(),
                (int) cropRectF.height(), Bitmap.Config.ARGB_8888);
        Canvas cavas = new Canvas(resultBitmap);

        // draw background
        Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);
        paint.setColor(Color.WHITE);
        cavas.drawRect(
                new RectF(0, 0, cropRectF.width(), cropRectF.height()),
                paint);

        Matrix matrix = new Matrix();
        matrix.postTranslate(-cropRectF.left, -cropRectF.top);

        cavas.drawBitmap(source, matrix, paint);

        if (source != null && !source.isRecycled()) {
            source.recycle();
        }

        return resultBitmap;
    }

    /**
     * 获取调整大小的位图
     */
    public Bitmap getResizedBitmap(Bitmap bm, int newWidth, int newHeight) {
        int width = bm.getWidth();
        int height = bm.getHeight();
        float scaleWidth = ((float) newWidth) / width;
        float scaleHeight = ((float) newHeight) / height;
        // CREATE A MATRIX FOR THE MANIPULATION
        Matrix matrix = new Matrix();
        // RESIZE THE BIT MAP
        matrix.postScale(scaleWidth, scaleHeight);

        // "RECREATE" THE NEW BITMAP
        Bitmap resizedBitmap = Bitmap.createBitmap(
                bm, 0, 0, width, height, matrix, false);
        bm.recycle();
        return resizedBitmap;
    }


    int[] intValues;
    int inputSize = 112;
    float IMAGE_MEAN = 128.0f;
    float IMAGE_STD = 128.0f;
    boolean isModelQuantized = false;


    // Output size of model
    int OUTPUT_SIZE = 192;
    float[][] embeedings;

    public void recognizeImage(final Bitmap bitmap) {

        // set Face to Preview
        imageFragment.setImageBitmap(bitmap);

        // 创建ByteBuffer以存储规范化图像

        ByteBuffer imgData = ByteBuffer.allocateDirect(1 * inputSize * inputSize * 3 * 4);

        imgData.order(ByteOrder.nativeOrder());

        intValues = new int[inputSize * inputSize];

        // get pixel values from Bitmap to normalize
        bitmap.getPixels(intValues, 0, bitmap.getWidth(), 0, 0, bitmap.getWidth(), bitmap.getHeight());

        imgData.rewind();

        for (int i = 0; i < inputSize; ++i) {
            for (int j = 0; j < inputSize; ++j) {
                int pixelValue = intValues[i * inputSize + j];
                if (isModelQuantized) {
                    // 量化模型
                    imgData.put((byte) ((pixelValue >> 16) & 0xFF));
                    imgData.put((byte) ((pixelValue >> 8) & 0xFF));
                    imgData.put((byte) (pixelValue & 0xFF));
                } else {
                    // 浮子模型
                    imgData.putFloat((((pixelValue >> 16) & 0xFF) - IMAGE_MEAN) / IMAGE_STD);
                    imgData.putFloat((((pixelValue >> 8) & 0xFF) - IMAGE_MEAN) / IMAGE_STD);
                    imgData.putFloat(((pixelValue & 0xFF) - IMAGE_MEAN) / IMAGE_STD);

                }
            }
        }

        //img数据被输入到我们的模型中
        Object[] inputArray = {imgData};

        Map<Integer, Object> outputMap = new HashMap<>();

        // 模型的输出将存储在此变量中
        embeedings = new float[1][OUTPUT_SIZE];

        outputMap.put(0, embeedings);

        // 运行模型
        tfLite.runForMultipleInputsOutputs(inputArray, outputMap);

        Log.d(TAG, "##### run: " + outputMap.size());

//        float distance_local = Float.MAX_VALUE;
//        String id = "0";
//        String label = "?";
//
//        //将新面孔与保存的面孔进行比较
//        if (registered.size() > 0) {
//
//            // 找到2个最匹配的人脸
//            final List<Pair<String, Float>> nearest = findNearest(embeedings[0]);
//
//            if (nearest.get(0) != null) {
//                // 获取最接近匹配人脸的名称和距离
//                final String name = nearest.get(0).first;
//                // label = name;
//                distance_local = nearest.get(0).second;
//                if (developerMode) {
//                    if (distance_local < distance) //If distance between Closest found face is more than 1.000 ,then output UNKNOWN face.
//                        reco_name.setText("Nearest: " + name + "\nDist: " + String.format("%.3f", distance_local) + "\n2nd Nearest: " + nearest.get(1).first + "\nDist: " + String.format("%.3f", nearest.get(1).second));
//                    else
//                        reco_name.setText("Unknown " + "\nDist: " + String.format("%.3f", distance_local) + "\nNearest: " + name + "\nDist: " + String.format("%.3f", distance_local) + "\n2nd Nearest: " + nearest.get(1).first + "\nDist: " + String.format("%.3f", nearest.get(1).second));
//
////                    System.out.println("nearest: " + name + " - distance: " + distance_local);
//                } else {
//                    if (distance_local < distance) //If distance between Closest found face is more than 1.000 ,then output UNKNOWN face.
//                        reco_name.setText(name);
//                    else
//                        reco_name.setText("Unknown");
////                    System.out.println("nearest: " + name + " - distance: " + distance_local);
//                }
//
//
//            }
//        }
    }

    private List<Pair<String, Float>> findNearest(float[] emb) {
        List<Pair<String, Float>> neighbour_list = new ArrayList<Pair<String, Float>>();
        Pair<String, Float> ret = null; //to get closest match
        Pair<String, Float> prev_ret = null; //to get second closest match
        for (Map.Entry<String, SimilarityClassifier.Recognition> entry : registered.entrySet()) {

            final String name = entry.getKey();
            final float[] knownEmb = ((float[][]) entry.getValue().getExtra())[0];

            float distance = 0;
            for (int i = 0; i < emb.length; i++) {
                float diff = emb[i] - knownEmb[i];
                distance += diff * diff;
            }
            distance = (float) Math.sqrt(distance);
            if (ret == null || distance < ret.second) {
                prev_ret = ret;
                ret = new Pair<>(name, distance);
            }
        }
        if (prev_ret == null) prev_ret = ret;
        neighbour_list.add(ret);
        neighbour_list.add(prev_ret);

        return neighbour_list;

    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 100 && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        }
    }
}
