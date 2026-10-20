package com.openai.galaxyringtorch;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.widget.*;

public class MainActivity extends Activity {
    private TextView status;
    private static final int REQ = 42;
    private final String[] labels = {"Flashlight toggle", "Media play / pause", "Find phone (sound)", "No action (diagnostics)"};
    private final String[] values = {"torch", "media", "find", "none"};
    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        SharedPreferences prefs = getSharedPreferences("ring", MODE_PRIVATE);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(36, 42, 36, 30);
        ScrollView scroll = new ScrollView(this);
        scroll.addView(root);
        TextView heading = new TextView(this);
        heading.setText("Galaxy Ring Actions"); heading.setTextSize(24);
        root.addView(heading);
        TextView hint = new TextView(this);
        hint.setText("Double pinch action (changes take effect immediately)");
        root.addView(hint);
        Spinner action = new Spinner(this);
        action.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, labels));
        String saved = prefs.getString("action", "torch");
        for(int j=0;j<values.length;j++) if(values[j].equals(saved)) action.setSelection(j);
        root.addView(action);
        action.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            public void onNothingSelected(android.widget.AdapterView<?> p) {}
            public void onItemSelected(android.widget.AdapterView<?> p, android.view.View v,int position,long id) { prefs.edit().putString("action", values[position]).apply(); }
        });
        CheckBox screenOff = new CheckBox(this);
        screenOff.setText("Only act when phone screen is off (optional)");
        screenOff.setChecked(prefs.getBoolean("screen_off",false));
        screenOff.setOnCheckedChangeListener((b1,c)->prefs.edit().putBoolean("screen_off",c).apply());
        root.addView(screenOff);
        TextView filterLabel = new TextView(this);
        filterLabel.setText("Duplicate-event filter (not a typing detector)");
        root.addView(filterLabel);
        Spinner filter = new Spinner(this);
        String[] filters={"Off", "250 ms", "500 ms"};
        int[] delays={0,250,500};
        filter.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,filters));
        int old=prefs.getInt("dedupe_ms",0);
        filter.setSelection(old==250?1:old==500?2:0);
        filter.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){
            public void onNothingSelected(android.widget.AdapterView<?> p){}
            public void onItemSelected(android.widget.AdapterView<?> p,android.view.View v,int pos,long id){prefs.edit().putInt("dedupe_ms",delays[pos]).apply();}
        });
        root.addView(filter);
        status = new TextView(this);
        status.setText("Start monitoring, then double pinch.\nThe diagnostic counter tracks received gestures.");
        status.setTextSize(16);
        root.addView(status);
        Button start=new Button(this);start.setText("Start monitoring");
        Button stop=new Button(this);stop.setText("Stop monitoring");
        Button stats=new Button(this);stats.setText("Refresh gesture diagnostics");
        Button reset=new Button(this);reset.setText("Reset diagnostic counter");
        root.addView(start);root.addView(stop);root.addView(stats);root.addView(reset);
        start.setOnClickListener(v->requestAndStart());
        stop.setOnClickListener(v->{stopService(new Intent(this,RingService.class));status.setText("Stopped");});
        stats.setOnClickListener(v->status.setText("Received: "+prefs.getInt("events",0)+"\nLast event (elapsed ms): "+prefs.getLong("last_event",0)+"\nActions executed: "+prefs.getInt("executed",0)));
        reset.setOnClickListener(v->{prefs.edit().putInt("events",0).putInt("executed",0).putLong("last_event",0).apply();stats.performClick();});
        setContentView(scroll);
    }
    private void requestAndStart(){
        java.util.ArrayList<String> list=new java.util.ArrayList<>();
        list.add(Manifest.permission.BLUETOOTH_CONNECT);
        list.add(Manifest.permission.BLUETOOTH_SCAN);
        if("torch".equals(getSharedPreferences("ring",MODE_PRIVATE).getString("action","torch"))) list.add(Manifest.permission.CAMERA);
        if(android.os.Build.VERSION.SDK_INT>=33) list.add(Manifest.permission.POST_NOTIFICATIONS);
        java.util.ArrayList<String> missing=new java.util.ArrayList<>();
        for(String p:list) if(checkSelfPermission(p)!=PackageManager.PERMISSION_GRANTED) missing.add(p);
        if(!missing.isEmpty()) requestPermissions(missing.toArray(new String[0]),REQ);
        else startRing();
    }
    @Override public void onRequestPermissionsResult(int r,String[] p,int[] g){super.onRequestPermissionsResult(r,p,g);if(r==REQ){for(int x:g)if(x!=PackageManager.PERMISSION_GRANTED){status.setText("Required permission denied");return;}startRing();}}
    private void startRing(){startForegroundService(new Intent(this,RingService.class));status.setText("Monitoring started");}
}
