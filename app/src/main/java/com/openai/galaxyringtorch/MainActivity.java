package com.openai.galaxyringtorch;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public class MainActivity extends Activity {
    private TextView status;
    private static final int REQ = 42;

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(48, 64, 48, 48);
        status = new TextView(this);
        status.setText("Galaxy Ring double pinch → flashlight\n\nTap Start, then double-pinch.");
        status.setTextSize(18);
        Button start = new Button(this); start.setText("Start ring monitoring");
        Button stop = new Button(this); stop.setText("Stop");
        root.addView(status); root.addView(start); root.addView(stop);
        setContentView(root);
        start.setOnClickListener(v -> requestAndStart());
        stop.setOnClickListener(v -> { stopService(new Intent(this, RingService.class)); status.setText("Stopped"); });
    }

    private void requestAndStart() {
        String[] p = android.os.Build.VERSION.SDK_INT >= 33
            ? new String[]{Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.CAMERA, Manifest.permission.POST_NOTIFICATIONS}
            : new String[]{Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.CAMERA};
        boolean ok = true;
        for (String x: p) if (checkSelfPermission(x) != PackageManager.PERMISSION_GRANTED) ok = false;
        if (!ok) requestPermissions(p, REQ); else startRing();
    }
    @Override public void onRequestPermissionsResult(int r, String[] p, int[] g) { super.onRequestPermissionsResult(r,p,g); if (r==REQ) startRing(); }
    private void startRing() {
        Intent i = new Intent(this, RingService.class);
        startForegroundService(i);
        status.setText("Monitoring started. Double-pinch the Galaxy Ring.");
    }
}
