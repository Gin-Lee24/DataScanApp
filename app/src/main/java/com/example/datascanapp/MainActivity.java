package com.example.datascanapp;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanRecord;
import android.bluetooth.le.ScanResult;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.SparseArray;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
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
    private FusedLocationProviderClient fusedLocationClient;

    private static final String TARGET_NAME = "Opensrc_team2";

    private String lastMac = "";
    private double lastTemp = 0.0;
    private double lastHumidity = 0.0;
    private int lastAQI = 0;
    private int lastTVOC = 0;
    private int lastECO2 = 0;
    private long lastTimestamp = 0L;
    private boolean hasParsedData = false;

    private double currentLat = 0.0;
    private double currentLon = 0.0;

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

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

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
        updateCurrentLocation();

        btnScan.setOnClickListener(v -> {
            txtLog.setText("");
            csvBuffer.setLength(0);
            hasParsedData = false;
            startScanning();
            txtStatus.setText("스캔 중...");
        });

        btnStop.setOnClickListener(v -> stopScanning());

        btnSave.setOnClickListener(v -> {
            updateCurrentLocation();
            sendDataToServer();
        });
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

    private void updateCurrentLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        fusedLocationClient.getLastLocation()
                .addOnSuccessListener(location -> {
                    if (location != null) {
                        currentLat = location.getLatitude();
                        currentLon = location.getLongitude();
                    }
                });
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

                    String name = "Unknown Device";
                    if (ActivityCompat.checkSelfPermission(MainActivity.this,
                            Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                        String tempName = result.getDevice().getName();
                        if (tempName != null) {
                            name = tempName;
                        }
                    }

                    if (!TARGET_NAME.equalsIgnoreCase(name)) {
                        return;
                    }

                    lastMac = address == null ? "" : address;

                    ScanRecord scanRecord = result.getScanRecord();
                    byte[] payload = extractManufacturerPayload(scanRecord);

                    if (payload == null || payload.length < 12) {
                        txtLog.setText(name + "\nMAC: " + lastMac + "\nRSSI: " + rssi +
                                " dBm\nPayload 파싱 실패");
                        return;
                    }

                    parsePayload(payload);
                    hasParsedData = true;

                    String humanTime = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                            .format(new Date(lastTimestamp * 1000L));

                    String message =
                            name + "\n" +
                                    "MAC: " + lastMac + "\n" +
                                    "RSSI: " + rssi + " dBm\n" +
                                    "Temp: " + String.format(Locale.getDefault(), "%.2f", lastTemp) + " °C\n" +
                                    "Hum: " + String.format(Locale.getDefault(), "%.2f", lastHumidity) + " %\n" +
                                    "AQI: " + lastAQI + "\n" +
                                    "TVOC: " + lastTVOC + " ppb\n" +
                                    "eCO2: " + lastECO2 + " ppm\n" +
                                    "Time: " + humanTime + "\n" +
                                    "Lat: " + currentLat + "\n" +
                                    "Lon: " + currentLon + "\n";

                    txtLog.setText(message);

                    csvBuffer.setLength(0);
                    csvBuffer.append(humanTime).append(",")
                            .append(name).append(",")
                            .append(lastMac).append(",")
                            .append(lastTemp).append(",")
                            .append(lastHumidity).append(",")
                            .append(lastAQI).append(",")
                            .append(lastTVOC).append(",")
                            .append(lastECO2).append(",")
                            .append(currentLat).append(",")
                            .append(currentLon).append("\n");
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

    private byte[] extractManufacturerPayload(ScanRecord scanRecord) {
        if (scanRecord == null) return null;

        SparseArray<byte[]> manufacturerData = scanRecord.getManufacturerSpecificData();
        if (manufacturerData == null || manufacturerData.size() == 0) return null;

        for (int i = 0; i < manufacturerData.size(); i++) {
            byte[] data = manufacturerData.valueAt(i);
            if (data != null && data.length >= 12) {
                byte[] payload = new byte[12];
                System.arraycopy(data, 0, payload, 0, 12);
                return payload;
            }
        }
        return null;
    }

    private void parsePayload(byte[] payload) {
        java.nio.ByteBuffer buffer = java.nio.ByteBuffer.wrap(payload)
                .order(java.nio.ByteOrder.LITTLE_ENDIAN);

        short tempRaw = buffer.getShort();
        int humRaw = buffer.get() & 0xFF;
        int aqiRaw = buffer.get() & 0xFF;
        int tvocRaw = buffer.getShort() & 0xFFFF;
        int eco2Raw = buffer.getShort() & 0xFFFF;
        long tsRaw = buffer.getInt() & 0xFFFFFFFFL;

        lastTemp = tempRaw / 100.0;
        lastHumidity = humRaw;
        lastAQI = aqiRaw;
        lastTVOC = tvocRaw;
        lastECO2 = eco2Raw;
        lastTimestamp = tsRaw;
    }

    private void sendDataToServer() {
        if (!hasParsedData) {
            Toast.makeText(this, "먼저 BLE 스캔으로 데이터를 받아주세요.", Toast.LENGTH_SHORT).show();
            return;
        }

        comm_data service = retrofit.create(comm_data.class);
        postdata pd = new postdata();

        String senderId = Settings.Secure.getString(
                getContentResolver(),
                Settings.Secure.ANDROID_ID
        );

        pd.setData(
                "opensrc2026",
                "team 2",
                "sensor 2",
                lastMac,
                lastTemp,
                lastHumidity,
                lastAQI,
                lastTVOC,
                lastECO2,
                lastTimestamp,
                currentLat,
                currentLon,
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