package com.openai.galaxyringtorch;

import android.app.*;
import android.bluetooth.*;
import android.content.*;
import android.hardware.camera2.CameraAccessException;
import android.media.AudioManager;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.view.KeyEvent;
import android.content.SharedPreferences;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.os.*;
import java.util.*;

public class RingService extends Service {
    private static final String CHANNEL = "ring_torch";
    private static final byte[] ENABLE = new byte[]{0x16,0x16,0x00};
    private static final byte[] DISABLE = new byte[]{0x16,0x16,0x01};
    private BluetoothGatt gatt;
    private BluetoothGattCharacteristic tx;
    private final ArrayDeque<BluetoothGattCharacteristic> pending = new ArrayDeque<>();
    private boolean torchOn = false;
    private long lastActionMs = 0;
    private Ringtone finder;
    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override public void onCreate() {
        super.onCreate();
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel(CHANNEL,"Ring Torch",NotificationManager.IMPORTANCE_LOW));
        Notification n = new Notification.Builder(this,CHANNEL).setContentTitle("Ring Torch").setContentText("Galaxy Ring gesture monitoring active").setSmallIcon(android.R.drawable.ic_menu_camera).build();
        startForeground(1,n);
        connectRing();
    }
    @Override public int onStartCommand(Intent i,int f,int id) { if (gatt==null) connectRing(); return START_NOT_STICKY; }
    @Override public IBinder onBind(Intent i) { return null; }
    @Override public void onDestroy() {
        if(finder!=null){ try{finder.stop();}catch(Exception ignored){} finder=null; }
        if(gatt!=null){
            try {
                if(tx!=null) {
                    tx.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE);
                    if(Build.VERSION.SDK_INT>=33) gatt.writeCharacteristic(tx,DISABLE,BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE);
                    else { tx.setValue(DISABLE); gatt.writeCharacteristic(tx); }
                }
                gatt.disconnect();
                gatt.close();
            } catch(Exception ignored){}
        }
        super.onDestroy();
    }

    private void connectRing() {
        BluetoothManager bm = getSystemService(BluetoothManager.class);
        BluetoothAdapter a = bm == null ? null : bm.getAdapter();
        if (a == null || !a.isEnabled()) return;
        try {
            for (BluetoothDevice d : a.getBondedDevices()) {
                String n=d.getName();
                if(n!=null && n.toLowerCase(Locale.ROOT).contains("galaxy ring")) {
                    gatt=d.connectGatt(this,false,cb,BluetoothDevice.TRANSPORT_LE); return;
                }
            }
        } catch(SecurityException ignored) {}
    }

    private final BluetoothGattCallback cb = new BluetoothGattCallback() {
        @Override public void onConnectionStateChange(BluetoothGatt g,int status,int state) {
            if(state==BluetoothProfile.STATE_CONNECTED && status==BluetoothGatt.GATT_SUCCESS) { try{g.discoverServices();}catch(SecurityException ignored){} }
        }
        @Override public void onServicesDiscovered(BluetoothGatt g,int status) {
            if(status!=BluetoothGatt.GATT_SUCCESS) return;
            tx=null; pending.clear();
            for(BluetoothGattService s:g.getServices()) {
                String su=s.getUuid().toString();
                for(BluetoothGattCharacteristic c:s.getCharacteristics()) {
                    int p=c.getProperties();
                    if((su.startsWith("00001b1b")||su.startsWith("00001b1a")) && (p & BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE)!=0) tx=c;
                    if((p & BluetoothGattCharacteristic.PROPERTY_NOTIFY)!=0) pending.add(c);
                }
            }
            subscribeNext(g);
        }
        private void subscribeNext(BluetoothGatt g) {
            BluetoothGattCharacteristic c=pending.poll();
            if(c==null){ enableGesture(g); return; }
            try{ g.setCharacteristicNotification(c,true); }catch(SecurityException e){ subscribeNext(g); return; }
            BluetoothGattDescriptor d=c.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"));
            if(d==null){ subscribeNext(g); return; }
            if(Build.VERSION.SDK_INT>=33) g.writeDescriptor(d,BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
            else { d.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE); g.writeDescriptor(d); }
        }
        @Override public void onDescriptorWrite(BluetoothGatt g,BluetoothGattDescriptor d,int status){ subscribeNext(g); }
        private void handle(byte[] v){ if(v!=null && v.length>=3 && v[0]==0x16 && v[1]==0x16 && v[2]==0x02) onPinch(); }
        @Override public void onCharacteristicChanged(BluetoothGatt g,BluetoothGattCharacteristic c,byte[] v){ handle(v); }
        @SuppressWarnings("deprecation") @Override public void onCharacteristicChanged(BluetoothGatt g,BluetoothGattCharacteristic c){ handle(c.getValue()); }
    };

    private void enableGesture(BluetoothGatt g) {
        if(tx==null) return;
        try {
            tx.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE);
            if(Build.VERSION.SDK_INT>=33) g.writeCharacteristic(tx,ENABLE,BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE);
            else { tx.setValue(ENABLE); g.writeCharacteristic(tx); }
            g.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_LOW_POWER);
        } catch(SecurityException ignored) {}
    }

    private synchronized void onPinch() {
        SharedPreferences p=getSharedPreferences("ring",MODE_PRIVATE);
        long now=SystemClock.elapsedRealtime();
        p.edit().putInt("events",p.getInt("events",0)+1).putLong("last_event",now).apply();
        PowerManager pm=getSystemService(PowerManager.class);
        if(p.getBoolean("screen_off",false) && pm!=null && pm.isInteractive()) return;
        int window=p.getInt("dedupe_ms",0);
        if(window>0 && now-lastActionMs<window) return;
        String action=p.getString("action","torch");
        if("none".equals(action)) return;
        lastActionMs=now;
        switch(action) {
            case "media": mediaToggle(); break;
            case "find": findPhone(); break;
            default: toggleTorch(); break;
        }
        p.edit().putInt("executed",p.getInt("executed",0)+1).apply();
    }
    private void mediaToggle(){
        AudioManager am=getSystemService(AudioManager.class);
        if(am==null)return;
        long t=SystemClock.uptimeMillis();
        am.dispatchMediaKeyEvent(new KeyEvent(t,t,KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,0));
        am.dispatchMediaKeyEvent(new KeyEvent(t,t,KeyEvent.ACTION_UP,KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,0));
    }
    private void findPhone(){
        if(finder!=null){try{finder.stop();}catch(Exception ignored){}finder=null;return;}
        try {
            android.net.Uri uri=RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
            finder=RingtoneManager.getRingtone(this,uri);
            if(finder==null)return;
            if(Build.VERSION.SDK_INT>=28)finder.setAudioAttributes(new android.media.AudioAttributes.Builder().setUsage(android.media.AudioAttributes.USAGE_ALARM).build());
            finder.play();
            Ringtone current=finder;
            handler.postDelayed(()->{if(finder==current){try{finder.stop();}catch(Exception ignored){}finder=null;}},10000);
        }catch(Exception ignored){}
    }

    private synchronized void toggleTorch() {
        CameraManager cm=getSystemService(CameraManager.class);
        try {
            for(String id:cm.getCameraIdList()) {
                CameraCharacteristics ch=cm.getCameraCharacteristics(id);
                Boolean flash=ch.get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
                Integer facing=ch.get(CameraCharacteristics.LENS_FACING);
                if(Boolean.TRUE.equals(flash) && facing!=null && facing==CameraCharacteristics.LENS_FACING_BACK) {
                    torchOn=!torchOn; cm.setTorchMode(id,torchOn); return;
                }
            }
        } catch(CameraAccessException | SecurityException ignored) {}
    }
}
