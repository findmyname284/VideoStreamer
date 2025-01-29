package kz.findmyname284.udpvideostreamer;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;

import com.google.common.util.concurrent.ListenableFuture;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public class Udp extends AppCompatActivity {
    private ExecutorService cameraExecutor;
    private ProcessCameraProvider cameraProvider;
    private final AtomicBoolean isStreaming = new AtomicBoolean(false);
    private String ip;
    private Button btnStart, btnStop;
    private ProgressBar progressBar;
    private DatagramSocket udpSocket;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_connection);

        progressBar = findViewById(R.id.progressBar);

        btnStart = findViewById(R.id.btnStart);
        btnStop = findViewById(R.id.btnStop);

        Intent intent = getIntent();
        if (intent != null) {
            String receivedData = intent.getStringExtra("ip");
            if (receivedData != null) {
                this.ip = receivedData;
            } else {
                finish();
            }
        } else {
            finish();
        }

        setupButtons();
        cameraExecutor = Executors.newSingleThreadExecutor();
    }

    private void setupButtons() {
        btnStart.setOnClickListener(v -> {
            progressBar.setVisibility(View.VISIBLE);
            btnStart.setEnabled(false);

            if (ip.isEmpty()) {
                Toast.makeText(Udp.this, "Please enter server IP address", Toast.LENGTH_SHORT).show();
                progressBar.setVisibility(View.GONE);
                btnStart.setEnabled(true);
                finish();
                return;
            }

            startStreaming();
        });

        btnStop.setOnClickListener(v -> stopStreaming());
    }

    private void startStreaming() {
        btnStart.setEnabled(false);
        btnStop.setEnabled(true);
        isStreaming.set(true);

        try {
            udpSocket = new DatagramSocket(); // Create a UDP socket
            startCamera();
        } catch (IOException e) {
            Log.e("UDP", "Failed to create UDP socket", e);
            stopStreaming();
        }
    }

    private void stopStreaming() {
        progressBar.setVisibility(View.GONE);
        btnStart.setEnabled(true);
        btnStop.setEnabled(false);
        isStreaming.set(false);
        stopCamera();

        if (udpSocket != null) {
            udpSocket.close();
            udpSocket = null;
        }
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture =
                ProcessCameraProvider.getInstance(this);

        cameraProviderFuture.addListener(() -> {
            try {
                cameraProvider = cameraProviderFuture.get();
                bindCameraUseCases();
            } catch (Exception e) {
                Log.e("Main", "Use case binding failed", e);
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void stopCamera() {
        if (cameraProvider != null) {
            cameraProvider.unbindAll();
        }
    }

    private void bindCameraUseCases() {
        Preview preview = new Preview.Builder()
                .build();

        ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                .build();

        imageAnalysis.setAnalyzer(cameraExecutor, imageProxy -> {
            if (isStreaming.get() && udpSocket != null) {
                byte[] jpegData = YuvToJpegConverter.convert(imageProxy);
                sendUdpData(jpegData); // Send the JPEG data over UDP
            }
            imageProxy.close();
        });

        CameraSelector cameraSelector = new CameraSelector.Builder()
                .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                .build();

        cameraProvider.bindToLifecycle(
                this, cameraSelector, preview, imageAnalysis);

        preview.setSurfaceProvider(
                ((PreviewView) findViewById(R.id.previewView)).getSurfaceProvider());
    }

    private void sendUdpData(byte[] data) {
        try {
            InetAddress serverAddress = InetAddress.getByName(ip);
            int port = 8080;
            DatagramPacket packet = new DatagramPacket(data, data.length, serverAddress, port);
            udpSocket.send(packet); // Send the packet
        } catch (IOException e) {
            Log.e("UDP", "Failed to send UDP packet", e);
            stopStreaming();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cameraExecutor.shutdown();
        stopStreaming();
        if (cameraProvider != null) {
            cameraProvider.unbindAll();
        }
    }
}