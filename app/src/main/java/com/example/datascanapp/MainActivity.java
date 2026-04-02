package com.example.datascanapp;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import com.google.android.material.button.MaterialButton;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private BluetoothLeScanner bluetoothLeScanner; // [cite: 410]
    private ScanCallback scanCallback;
    private boolean isScanning = false;
    private TextView txtLog, txtStatus;
    private StringBuilder csvBuffer = new StringBuilder();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main); // [cite: 715]

        // 1. UI 요소 연결
        txtLog = findViewById(R.id.txtLog);
        txtStatus = findViewById(R.id.txtStatus);
        MaterialButton btnScan = findViewById(R.id.btnScan);
        MaterialButton btnStop = findViewById(R.id.btnStop);
        MaterialButton btnSave = findViewById(R.id.btnSave);

        // 2. 블루투스 어댑터 초기화 [cite: 411]
        BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter != null) {
            bluetoothLeScanner = bluetoothAdapter.getBluetoothLeScanner();
        }

        // 3. 실행 시 권한 요청 [cite: 361, 390]
        checkPermissions();

        // 4. 버튼 클릭 이벤트 설정
        btnScan.setOnClickListener(v -> {
            txtLog.setText(""); // 1. 로그 창에 있던 "아직 수집된 데이터가 없어요"를 지운다.
            startScanning();    // 2. 실제 스캔 기능을 시작한다.
            txtStatus.setText("스캔 중..."); // 3. 상태 표시를 바꾼다.
        });

        btnStop.setOnClickListener(v -> stopScanning());
        btnSave.setOnClickListener(v -> saveToCsv());
    }

    private void checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ActivityCompat.requestPermissions(this, new String[]{
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.ACCESS_FINE_LOCATION
            }, 100); // [cite: 371-375]
        } else {
            ActivityCompat.requestPermissions(this, new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION
            }, 100);
        }
    }

    private void startScanning() {
        // 실행 전 권한이 진짜 있는지 최종 확인 (에러 방지용) [cite: 386-389]
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
            checkPermissions(); // 권한 없으면 다시 요청 [cite: 390]
            return;
        }

        if (!isScanning && bluetoothLeScanner != null) {
            scanCallback = new ScanCallback() {
                @Override
                public void onScanResult(int callbackType, ScanResult result) {
                    String address = result.getDevice().getAddress();
                    int rssi = result.getRssi();
                    String record = result.getScanRecord().toString(); // 상세 정보
                    String name = result.getDevice().getName();        // 기기 이름
                    if (name == null) name = "Unknown Device";

                    // [필터링 시작] 라즈베리파이 주소거나 UUID에 181a가 포함된 경우만!
                    if (address.equalsIgnoreCase("b8:27:eb:b8:72:af") || record.contains("181a")) {
                        String time = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date());

                        // 강의 자료처럼 구성: 이름, MAC, RSSI, 상세정보 순서
                        String message = name + "\nMAC: " + address + "\nRSSI: " + rssi + " dBm\n" + record + "\n\n";

                        txtLog.append(message); // 화면에 출력
                        csvBuffer.append(time).append(",").append(name).append(",").append(address).append(",").append(rssi).append("\n"); // 데이터 저장
                    }                    }
                //}
            };
            bluetoothLeScanner.startScan(scanCallback); // [cite: 417]
            txtStatus.setText("스캔 중...");
            isScanning = true;
        }
    }

    private void stopScanning() {
        // 실행 전 권한이 진짜 있는지 최종 확인 (에러 방지용) [cite: 386-389]
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
            checkPermissions(); // 권한 없으면 다시 요청 [cite: 390]
            return;
        }

        if (isScanning && bluetoothLeScanner != null) {
            bluetoothLeScanner.stopScan(scanCallback); // [cite: 406]
            txtStatus.setText("중지됨");
            isScanning = false;
        }
    }

    private void saveToCsv() {
        try {
            File path = getExternalFilesDir(null);
            File file = new File(path, "ble_data.csv");

            FileWriter writer = new FileWriter(file, true);
            if (file.length() == 0) {
                writer.append("Time,Address,RSSI\n");
            }
            writer.append(csvBuffer.toString());
            writer.flush();
            writer.close();

            csvBuffer.setLength(0); // 저장 후 비우기
            Toast.makeText(this, "저장 성공: " + file.getAbsolutePath(), Toast.LENGTH_SHORT).show();
        } catch (IOException e) {
            Toast.makeText(this, "저장 실패", Toast.LENGTH_SHORT).show();
        }
    }
}