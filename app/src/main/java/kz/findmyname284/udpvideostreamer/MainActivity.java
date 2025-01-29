package kz.findmyname284.udpvideostreamer;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;


public class MainActivity extends AppCompatActivity {

    private Button btnUdp, btnWebSocket;
    private EditText ipInput;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        ipInput = findViewById(R.id.etServerIp);
        btnUdp = findViewById(R.id.btnUdp);
        btnWebSocket = findViewById(R.id.btnWebSocket);

        setupButtons();
    }

    private void setupButtons() {
        btnUdp.setOnClickListener(v -> {
            if (checkPermissions()) {
                String ip = ipInput.getText().toString();
                if (ip.isEmpty()) {
                    Toast.makeText(MainActivity.this, "Please enter server IP address", Toast.LENGTH_SHORT).show();
                    return;
                }
                Intent intent = new Intent(MainActivity.this, Udp.class);
                intent.putExtra("ip", ip);
                startActivity(intent);
            }
        });

        btnWebSocket.setOnClickListener(v -> {
            if (checkPermissions()) {
                String ip = ipInput.getText().toString();
                if (ip.isEmpty()) {
                    Toast.makeText(MainActivity.this, "Please enter server IP address", Toast.LENGTH_SHORT).show();
                    return;
                }
                Intent intent = new Intent(MainActivity.this, WebSocket.class);
                intent.putExtra("ip", ip);
                startActivity(intent);
            }
        });
    }

    private boolean checkPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            return true;
        }
        requestPermissions(new String[]{Manifest.permission.CAMERA}, 1);
        return false;
    }
}
