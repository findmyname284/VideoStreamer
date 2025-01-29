package kz.findmyname284.udpvideostreamer;

import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.media.Image;

import androidx.annotation.OptIn;
import androidx.camera.core.ExperimentalGetImage;
import androidx.camera.core.ImageProxy;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;

public class YuvToJpegConverter {
    @OptIn(markerClass = ExperimentalGetImage.class)
    public static byte[] convert(ImageProxy imageProxy) {
        Image image = imageProxy.getImage();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        assert image != null;
        Rect crop = image.getCropRect();

        Image.Plane yPlane = image.getPlanes()[0];
        Image.Plane uPlane = image.getPlanes()[1];
        Image.Plane vPlane = image.getPlanes()[2];

        ByteBuffer yBuffer = yPlane.getBuffer();
        ByteBuffer uBuffer = uPlane.getBuffer();
        ByteBuffer vBuffer = vPlane.getBuffer();

        int ySize = yBuffer.remaining();
        int uSize = uBuffer.remaining();
        int vSize = vBuffer.remaining();

        byte[] nv21 = new byte[ySize + uSize + vSize];

        yBuffer.get(nv21, 0, ySize);

        int rowStride = uPlane.getRowStride();
        int pixelStride = uPlane.getPixelStride();

        for (int row = 0; row < crop.height() / 2; row++) {
            for (int col = 0; col < crop.width() / 2; col++) {
                int uvIndex = row * rowStride + col * pixelStride;
                nv21[ySize + (row * crop.width() / 2 + col) * 2] = vBuffer.get(uvIndex);
                nv21[ySize + (row * crop.width() / 2 + col) * 2 + 1] = uBuffer.get(uvIndex);
            }
        }

        YuvImage yuvImage = new YuvImage(
                nv21,
                ImageFormat.NV21,
                crop.width(),
                crop.height(),
                null
        );

        yuvImage.compressToJpeg(crop, 70, out);
        return out.toByteArray();
    }
}