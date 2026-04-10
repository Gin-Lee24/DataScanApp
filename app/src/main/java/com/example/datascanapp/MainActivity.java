package com.example.datascanapp;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.google.android.material.button.MaterialButton;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import retrofit2.converter.scalars.ScalarsConverterFactory;

public class MainActivity extends AppCompatActivity {

    private BluetoothLeScanner bluetoothLeScanner;
    private ScanCallback scanCallback;
    private boolean isScanning = false;
    private TextView txtLog, txtStatus;
    private StringBuilder csvBuffer = new StringBuilder();

    private Retrofit retrofit;

    // 라즈베리파이 이름
    private static final String TARGET_NAME = "Opensrc_team2";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Gson gson = new GsonBuilder().setLenient().create();

        retrofit = new Retrofit.Builder()
                .baseUrl("http://203.255.81.72:10021/")
                .addConverterFactory(ScalarsConverterFactory.create())
                .addConverterFactory(GsonConverterFactory.create(gson))
                .build();

        txtLog = findViewById(R.id.txtLog);
        txtStatus = findViewById(R.id.txtStatus);
        MaterialButton btnScan = findViewById(R.id.btnScan);
        MaterialButton btnStop = findViewById(R.id.btnStop);
        MaterialButton btnSave = findViewById(R.id.btnSave);

        BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter != null) {
            bluetoothLeScanner = bluetoothAdapter.getBluetoothLeScanner();
        }

        checkPermissions();

        btnScan.setOnClickListener(v -> {
            txtLog.setText("");
            csvBuffer.setLength(0);
            startScanning();
            txtStatus.setText("스캔 중...");
        });

        btnStop.setOnClickListener(v -> stopScanning());
        btnSave.setOnClickListener(v -> sendDataToServer());
    }

    private void checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ActivityCompat.requestPermissions(this, new String[]{
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.ACCESS_FINE_LOCATION
            }, 100);
        } else {
            ActivityCompat.requestPermissions(this, new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION
            }, 100);
        }
    }

    private void startScanning() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN)
                != PackageManager.PERMISSION_GRANTED) {
            checkPermissions();
            return;
        }

        if (!isScanning && bluetoothLeScanner != null) {
            scanCallback = new ScanCallback() {
                @Override
                public void onScanResult(int callbackType, ScanResult result) {
                    int rssi = result.getRssi();

                    String address = result.getDevice().getAddress();

                    String record = "";
                    if (result.getScanRecord() != null) {
                        record = result.getScanRecord().toString();
                    }

                    String name = "Unknown Device";
                    if (ActivityCompat.checkSelfPermission(MainActivity.this,
                            Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                        String tempName = result.getDevice().getName();
                        if (tempName != null) {
                            name = tempName;
                        }
                    }

                    // 이름이 Opensrc_team2인 것만 표시
                    if (!TARGET_NAME.equalsIgnoreCase(name)) {
                        return;
                    }

                    String time = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date());

                    String message = name + "\nMAC: " + address +
                            "\nRSSI: " + rssi + " dBm\n" + record + "\n\n";

                    txtLog.setText(message);

                    csvBuffer.setLength(0);
                    csvBuffer.append(time).append(",")
                            .append(name).append(",")
                            .append(address).append(",")
                            .append(rssi).append("\n");
                }
            };

            bluetoothLeScanner.startScan(scanCallback);
            txtStatus.setText("스캔 중...");
            isScanning = true;
        }
    }

    private void stopScanning() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN)
                != PackageManager.PERMISSION_GRANTED) {
            checkPermissions();
            return;
        }

        if (isScanning && bluetoothLeScanner != null) {
            bluetoothLeScanner.stopScan(scanCallback);
            txtStatus.setText("중지됨");
            isScanning = false;
        }
    }

    private void sendDataToServer() {
        comm_data service = retrofit.create(comm_data.class);

        postdata pd = new postdata();

        String senderId = Settings.Secure.getString(
                getContentResolver(),
                Settings.Secure.ANDROID_ID
        );

        pd.setData(
                "opensrc2026",   // 🔥 key (이게 401 원인)
                "team 2",        // 팀명
                "sensor 2",      // 센서명
                "b8:27:eb:b8:72:af",
                28.88,
                25.42,
                1,
                36,
                427,
                System.currentTimeMillis()/1000,
                36.635,
                127.491,
                senderId
        );
        Call<String> call = service.post_json(pd);

        call.enqueue(new Callback<String>() {
            @Override
            public void onResponse(Call<String> call, Response<String> response) {
                if (response.isSuccessful()) {
                    txtStatus.setText("전송 성공");
                    Toast.makeText(MainActivity.this,
                            "성공: " + response.body(),
                            Toast.LENGTH_LONG).show();
                } else {
                    txtStatus.setText("전송 실패: " + response.code());
                    Toast.makeText(MainActivity.this,
                            "실패 코드: " + response.code(),
                            Toast.LENGTH_LONG).show();
                }
            }

            @Override
            public void onFailure(Call<String> call, Throwable t) {
                txtStatus.setText("통신 실패");
                Toast.makeText(MainActivity.this,
                        "에러: " + t.getMessage(),
                        Toast.LENGTH_LONG).show();
            }
        });
    }
}