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
    private final String[] labels = {"손전등 켜기/끄기", "미디어 재생/일시정지", "휴대전화 찾기(소리)", "동작 없음(진단)"};
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
        heading.setText("갤럭시 링 제스처"); heading.setTextSize(24);
        root.addView(heading);
        TextView hint = new TextView(this);
        hint.setText("더블 핀치 동작 (즉시 반영)");
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
        screenOff.setText("휴대전화 화면이 꺼졌을 때만 동작");
        screenOff.setChecked(prefs.getBoolean("screen_off",false));
        screenOff.setOnCheckedChangeListener((b1,c)->prefs.edit().putBoolean("screen_off",c).apply());
        root.addView(screenOff);
        TextView filterLabel = new TextView(this);
        filterLabel.setText("중복 이벤트 필터 (키보드 입력 감지는 아님)");
        root.addView(filterLabel);
        Spinner filter = new Spinner(this);
        String[] filters={"사용 안 함", "250 ms", "500 ms"};
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
        status.setText("감지 시작을 누른 후 제스처를 테스트하세요.");
        status.setTextSize(16);
        root.addView(status);
        Button start=new Button(this);start.setText("감지 시작");
        Button stop=new Button(this);stop.setText("감지 중지");
        Button stats=new Button(this);stats.setText("감지 통계 새로고침");
        Button reset=new Button(this);reset.setText("감지 통계 초기화");
        root.addView(start);root.addView(stop);root.addView(stats);root.addView(reset);
        start.setOnClickListener(v->requestAndStart());
        stop.setOnClickListener(v->{stopService(new Intent(this,RingService.class));status.setText("감지 중지됨");});
        stats.setOnClickListener(v->status.setText("수신: "+prefs.getInt("events",0)+"\n마지막 이벤트(ms): "+prefs.getLong("last_event",0)+"\n실행된 동작: "+prefs.getInt("executed",0)));
        reset.setOnClickListener(v->{prefs.edit().putInt("events",0).putInt("executed",0).putLong("last_event",0).apply();stats.performClick();});
        TextView testHeading=new TextView(this);
        testHeading.setText("트리플 핀치 실험");
        testHeading.setTextSize(20);
        root.addView(testHeading);
        TextView explanation=new TextView(this);
        explanation.setText("실험 모드에서는 손전등 등 동작을 실행하지 않고 링의 BLE 알림을 기록합니다. 2번과 3번 핀치를 각각 5회 수행하고 기록을 비교하세요. 트리플 핀치가 지원된다는 의미는 아닙니다.");
        root.addView(explanation);
        CheckBox tripleTest=new CheckBox(this);
        tripleTest.setText("BLE 실험 모드 사용");
        tripleTest.setChecked(prefs.getBoolean("triple_test",false));
        tripleTest.setOnCheckedChangeListener((button,checked)->prefs.edit().putBoolean("triple_test",checked).apply());
        root.addView(tripleTest);
        TextView rawLog=new TextView(this);
        rawLog.setTextSize(12);
        rawLog.setTextIsSelectable(true);
        root.addView(rawLog);
        Button rawRefresh=new Button(this);
        rawRefresh.setText("BLE 기록 보기");
        root.addView(rawRefresh);
        rawRefresh.setOnClickListener(v->rawLog.setText("전체 패킷: "+prefs.getInt("triple_packets",0)+" / 더블 이벤트 패킷: "+prefs.getInt("triple_gestures",0)+"\n"+prefs.getString("triple_log","기록 없음")));
        Button rawReset=new Button(this);
        rawReset.setText("BLE 기록 초기화");
        root.addView(rawReset);
        rawReset.setOnClickListener(v->{prefs.edit().remove("triple_log").putInt("triple_packets",0).putInt("triple_gestures",0).apply();rawRefresh.performClick();});
        Button copyLog=new Button(this);
        copyLog.setText("BLE 기록 복사");
        root.addView(copyLog);
        copyLog.setOnClickListener(v->{
            android.content.ClipboardManager cb=(android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
            if(cb!=null) cb.setPrimaryClip(android.content.ClipData.newPlainText("Ring BLE log",prefs.getString("triple_log","")));
            Toast.makeText(this,"기록을 복사했습니다",Toast.LENGTH_SHORT).show();
        });
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
    @Override public void onRequestPermissionsResult(int r,String[] p,int[] g){super.onRequestPermissionsResult(r,p,g);if(r==REQ){for(int x:g)if(x!=PackageManager.PERMISSION_GRANTED){status.setText("필수 권한이 거부되었습니다");return;}startRing();}}
    private void startRing(){startForegroundService(new Intent(this,RingService.class));status.setText("감지 시작됨");}
}
