package com.cj.mobile.myapplication.util;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.media.Image;
import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.RenderScript;
import android.renderscript.ScriptIntrinsicYuvToRGB;
import android.renderscript.Type;
import androidx.annotation.NonNull;

import com.cj.mobile.myapplication.BuildConfig;

import java.nio.ByteBuffer;

/**
 * 从【TFL Classify】项目移植过来的
 * Helper class used to efficiently convert a [Media.Image] object from
 * [ImageFormat.YUV_420_888] format to an RGB [Bitmap] object.
 *
 * The [yuvToRgb] method is able to achieve the same FPS as the CameraX image
 * analysis use case on a Pixel 3 XL device at the default analyzer resolution,
 * which is 30 FPS with 640x480.
 *
 * NOTE: This has been tested in a limited number of devices and is not
 * considered production-ready code. It was created for illustration purposes,
 * since this is not an efficient camera pipeline due to the multiple copies
 * required to convert each frame.
 */
public class YuvToRgbConverter {

    private final RenderScript rs;
    private final ScriptIntrinsicYuvToRGB scriptYuvToRgb;

    private int pixelCount = -1;
    private ByteBuffer yuvBuffer;
    private Allocation inputAllocation;
    private Allocation outputAllocation;

    public YuvToRgbConverter(Context context) {
        rs = RenderScript.create(context);
        scriptYuvToRgb = ScriptIntrinsicYuvToRGB.create(rs, Element.U8_4(rs));
    }

    public synchronized void yuvToRgb(@NonNull Image image, @NonNull Bitmap output) {

        // Ensure that the intermediate output byte buffer is allocated
        if (yuvBuffer == null) {
            pixelCount = image.getCropRect().width() * image.getCropRect().height();
            // Bits per pixel is an average for the whole image, so it's useful to compute the size
            // of the full buffer but should not be used to determine pixel offsets
            int pixelSizeBits = ImageFormat.getBitsPerPixel(ImageFormat.YUV_420_888);
            yuvBuffer = ByteBuffer.allocateDirect(pixelCount * pixelSizeBits / 8);
        }

        // Rewind the buffer; no need to clear it since it will be filled
        yuvBuffer.rewind();

        // Get the YUV data in byte array form using NV21 format
        imageToByteBuffer(image, yuvBuffer.array());

        // Ensure that the RenderScript inputs and outputs are allocated
        if (inputAllocation == null) {
            // Explicitly create an element with type NV21, since that's the pixel format we use
            Type.Builder elemType = new Type.Builder(rs, Element.YUV(rs)).setYuvFormat(ImageFormat.NV21);
            inputAllocation = Allocation.createSized(rs, elemType.create().getElement(), yuvBuffer.array().length);
        }
        if (outputAllocation == null) {
            outputAllocation = Allocation.createFromBitmap(rs, output);
        }

        // Convert NV21 format YUV to RGB
        inputAllocation.copyFrom(yuvBuffer.array());
        scriptYuvToRgb.setInput(inputAllocation);
        scriptYuvToRgb.forEach(outputAllocation);
        outputAllocation.copyTo(output);
    }

    private void imageToByteBuffer(@NonNull Image image, @NonNull byte[] outputBuffer) {
        if (BuildConfig.DEBUG && image.getFormat() != ImageFormat.YUV_420_888) {
            throw new AssertionError("Assertion failed");
        }

        Rect imageCrop = image.getCropRect();
        Image.Plane[] imagePlanes = image.getPlanes();

        for (int planeIndex = 0; planeIndex < imagePlanes.length; planeIndex++) {
            Image.Plane plane = imagePlanes[planeIndex];

            // How many values are read in input for each output value written
            // Only the Y plane has a value for every pixel, U and V have half the resolution i.e.
            //
            // Y Plane            U Plane    V Plane
            // ===============    =======    =======
            // Y Y Y Y Y Y Y Y    U U U U    V V V V
            // Y Y Y Y Y Y Y Y    U U U U    V V V V
            // Y Y Y Y Y Y Y Y    U U U U    V V V V
            // Y Y Y Y Y Y Y Y    U U U U    V V V V
            // Y Y Y Y Y Y Y Y
            // Y Y Y Y Y Y Y Y
            // Y Y Y Y Y Y Y Y
            int outputStride;

            // The index in the output buffer the next value will be written at
            // For Y it's zero, for U and V we start at the end of Y and interleave them i.e.
            //
            // First chunk        Second chunk
            // ===============    ===============
            // Y Y Y Y Y Y Y Y    V U V U V U V U
            // Y Y Y Y Y Y Y Y    V U V U V U V U
            // Y Y Y Y Y Y Y Y    V U V U V U V U
            // Y Y Y Y Y Y Y Y    V U V U V U V U
            // Y Y Y Y Y Y Y Y
            // Y Y Y Y Y Y Y Y
            // Y Y Y Y Y Y Y Y
            int outputOffset;

            switch (planeIndex) {
                case 0:
                    outputStride = 1;
                    outputOffset = 0;
                    break;
                case 1:
                    outputStride = 2;
                    // For NV21 format, U is in odd-numbered indices
                    outputOffset = pixelCount + 1;
                    break;
                case 2:
                    outputStride = 2;
                    // For NV21 format, V is in even-numbered indices
                    outputOffset = pixelCount;
                    break;
                default:
                    // Image contains more than 3 planes, something strange is going on
                    continue;
            }

            ByteBuffer planeBuffer = plane.getBuffer();
            int rowStride = plane.getRowStride();
            int pixelStride = plane.getPixelStride();

            // We have to divide the width and height by two if it's not the Y plane
            Rect planeCrop;
            if (planeIndex == 0) {
                planeCrop = imageCrop;
            } else {
                planeCrop = new Rect(
                        imageCrop.left / 2,
                        imageCrop.top / 2,
                        imageCrop.right / 2,
                        imageCrop.bottom / 2
                );
            }

            int planeWidth = planeCrop.width();
            int planeHeight = planeCrop.height();

            // Intermediate buffer used to store the bytes of each row
            byte[] rowBuffer = new byte[plane.getRowStride()];

            // Size of each row in bytes
            int rowLength;
            if (pixelStride == 1 && outputStride == 1) {
                rowLength = planeWidth;
            } else {
                // Take into account that the stride may include data from pixels other than this
                // particular plane and row, and that could be between pixels and not after every
                // pixel:
                //
                // |---- Pixel stride ----|                    Row ends here --> |
                // | Pixel 1 | Other Data | Pixel 2 | Other Data | ... | Pixel N |
                //
                // We need to get (N-1) * (pixel stride bytes) per row + 1 byte for the last pixel
                rowLength = (planeWidth - 1) * pixelStride + 1;
            }

            for (int row = 0; row < planeHeight; row++) {
                // Move buffer position to the beginning of this row
                planeBuffer.position(
                        (row + planeCrop.top) * rowStride + planeCrop.left * pixelStride
                );

                if (pixelStride == 1 && outputStride == 1) {
                    // When there is a single stride value for pixel and output, we can just copy
                    // the entire row in a single step
                    planeBuffer.get(outputBuffer, outputOffset, rowLength);
                    outputOffset += rowLength;
                } else {
                    // When either pixel or output have a stride > 1 we must copy pixel by pixel
                    planeBuffer.get(rowBuffer, 0, rowLength);
                    for (int col = 0; col < planeWidth; col++) {
                        outputBuffer[outputOffset] = rowBuffer[col * pixelStride];
                        outputOffset += outputStride;
                    }
                }
            }
        }
    }
}

