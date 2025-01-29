package kz.findmyname284.udpvideostreamer;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;

import com.google.common.util.concurrent.ListenableFuture;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocketListener;
import okio.ByteString;

public class WebSocket extends AppCompatActivity {
    private ExecutorService cameraExecutor;
    private okhttp3.WebSocket webSocket;
    private ProcessCameraProvider cameraProvider;
    private final AtomicBoolean isStreaming = new AtomicBoolean(false);
    private String WS_URL;

    private EditText ipInput;
    private Button btnStart, btnStop;
    private ProgressBar progressBar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        ipInput = findViewById(R.id.etServerIp);
        btnStart = findViewById(R.id.btnStart);
        btnStop = findViewById(R.id.btnStop);
        progressBar = findViewById(R.id.progressBar);


        setupButtons();
        cameraExecutor = Executors.newSingleThreadExecutor();
    }

    private void setupButtons() {
        btnStart.setOnClickListener(v -> {
            if (checkPermissions()) {
                progressBar.setVisibility(View.VISIBLE);
                btnStart.setEnabled(false);

                String ip = ipInput.getText().toString();
                if (ip.isEmpty()) {
                    Toast.makeText(WebSocket.this, "Please enter server IP address", Toast.LENGTH_SHORT).show();
                    progressBar.setVisibility(View.GONE);
                    btnStart.setEnabled(true);
                    return;
                }

                WS_URL = "ws://" + ip + ":8080/ws";
                startStreaming();
            }
        });

        btnStop.setOnClickListener(v -> stopStreaming());
    }

    private void startStreaming() {
        btnStart.setEnabled(false);
        btnStop.setEnabled(true);
        isStreaming.set(true);

        connectWebSocket();
    }

    private void stopStreaming() {
        ipInput.setVisibility(View.VISIBLE);
        progressBar.setVisibility(View.GONE);
        btnStart.setEnabled(true);
        btnStop.setEnabled(false);
        isStreaming.set(false);
        stopCamera();

        if (webSocket != null) {
            webSocket.close(1000, "User requested");
            webSocket = null;
        }
    }

    private void connectWebSocket() {
        OkHttpClient client = new OkHttpClient.Builder()
                .retryOnConnectionFailure(false)
                .build();

        Request request = new Request.Builder().url(WS_URL).build();
        client.newWebSocket(request, new WebSocketListener() {
            @Override
            public void onOpen(@NonNull okhttp3.WebSocket webSocket, @NonNull Response response) {
                runOnUiThread(() -> {
                    WebSocket.this.webSocket = webSocket;
                    btnStop.setEnabled(true);
                    ipInput.setVisibility(View.GONE);
                    progressBar.setVisibility(View.GONE);
                    startCamera();
                });
            }

            @Override
            public void onClosed(@NonNull okhttp3.WebSocket webSocket, int code, @NonNull String reason) {
                runOnUiThread(() -> stopStreaming());
            }

            @Override
            public void onFailure(@NonNull okhttp3.WebSocket webSocket,
                                  @NonNull Throwable t,
                                  Response response) {
                runOnUiThread(() -> stopStreaming());
            }
        });
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
            if (isStreaming.get() && webSocket != null) {
                byte[] jpegData = YuvToJpegConverter.convert(imageProxy);
                webSocket.send(ByteString.of(jpegData));
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

    private boolean checkPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            return true;
        }
        requestPermissions(new String[]{Manifest.permission.CAMERA}, 1);
        return false;
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