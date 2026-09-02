package com.bizard.homesmokemqtt;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;

import com.bizard.homesmokecore.AutoEngine;
import com.bizard.homesmokecore.AutoProgram;
import com.bizard.homesmokecore.AutoStage;
import com.bizard.homesmokecore.Telemetry;
import com.bizard.homesmokecore.TelemetryParser;

import org.json.JSONObject;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Long-running control owner. Activity is now a presentation layer only.
 * Bluetooth, Auto, MQTT and command acknowledgements survive UI recreation.
 */
public class HomeSmokeService extends Service {
    public interface Listener { void onState(State state); }

    public static final class State {
        public final boolean bluetoothConnected,mqttConnected;
        public final String bluetoothName,mqttState,autoStatus,lastError;
        public final Telemetry telemetry;
        public final AutoEngine.State autoState;
        public final int autoStageIndex;
        State(boolean bt,boolean mq,String bn,String ms,String as,String err,Telemetry t,AutoEngine.State a,int idx){bluetoothConnected=bt;mqttConnected=mq;bluetoothName=bn;mqttState=ms;autoStatus=as;lastError=err;telemetry=t;autoState=a;autoStageIndex=idx;}
    }

    private static final UUID SPP_UUID=UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    private static final String TERMINATOR="\\0";
    private static final int NOTIFY_ID=260;
    private static final String CHANNEL="homesmoke_auto";
    private static final long HEARTBEAT_MS=2000L;
    private static final long COMMAND_MAX_AGE_MS=120000L;

    public final class LocalBinder extends Binder { public HomeSmokeService getService(){return HomeSmokeService.this;} }
    private final IBinder binder=new LocalBinder();
    private final Handler main=new Handler(Looper.getMainLooper());
    private final StringBuilder rx=new StringBuilder();
    private final AutoEngine autoEngine=new AutoEngine();
    private final CommandAckManager ackManager=new CommandAckManager();

    private SharedPreferences prefs;
    private volatile BluetoothSocket btSocket;
    private volatile Thread btThread;
    private volatile MqttClient mqtt;
    private volatile boolean mqttWanted=false,mqttConnecting=false;
    private int mqttBackoffSec=5;
    private Listener listener;
    private Telemetry latest;
    private String bluetoothName="",mqttState="MQTT отключен",autoStatus="Auto выключено",lastError="";

    private final Runnable heartbeat=new Runnable(){@Override public void run(){
        if(autoEngine.getState()!=AutoEngine.State.RUNNING)return;
        if(!sendRaw("h")){abortAuto("Bluetooth потерян");return;}
        main.postDelayed(this,HEARTBEAT_MS);
    }};
    private final Runnable mqttReconnect=new Runnable(){@Override public void run(){if(mqttWanted&&!isMqttConnected()&&!mqttConnecting)connectMqtt();}};

    @Override public void onCreate(){super.onCreate();prefs=getSharedPreferences("homesmoke_full",MODE_PRIVATE);ensureDeviceId();}
    @Override public IBinder onBind(Intent intent){return binder;}
    @Override public int onStartCommand(Intent intent,int flags,int startId){return START_STICKY;}

    public void setListener(Listener l){listener=l;emit();}
    public State getState(){return snapshot();}
    public boolean isBluetoothConnected(){BluetoothSocket s=btSocket;return s!=null&&s.isConnected();}
    public boolean isMqttConnected(){MqttClient m=mqtt;return m!=null&&m.isConnected();}
    public boolean isAutoRunning(){return autoEngine.getState()==AutoEngine.State.RUNNING;}

    public void connectBluetooth(String address){
        closeBluetooth(false);lastError="";emit();
        btThread=new Thread(()->{
            try{
                if(Build.VERSION.SDK_INT>=31&&checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)!=PackageManager.PERMISSION_GRANTED)throw new SecurityException("Нет разрешения Bluetooth");
                BluetoothAdapter adapter=BluetoothAdapter.getDefaultAdapter();if(adapter==null)throw new IOException("Bluetooth не поддерживается");
                BluetoothDevice d=adapter.getRemoteDevice(address);BluetoothSocket s=d.createRfcommSocketToServiceRecord(SPP_UUID);btSocket=s;s.connect();
                try{bluetoothName=d.getName()==null?address:d.getName();}catch(Exception e){bluetoothName=address;}
                main.post(()->{lastError="";emit();});readLoop(s.getInputStream());
            }catch(Exception e){main.post(()->{lastError="Bluetooth: "+safe(e);emit();});}
            finally{closeBluetoothInternal();if(autoEngine.getState()==AutoEngine.State.RUNNING)abortAuto("Bluetooth соединение потеряно");ackManager.failAll("bluetooth_disconnected",this::publishCommandAck);main.post(this::emit);}
        },"HomeSmoke-Bluetooth");btThread.start();
    }

    public void disconnectBluetooth(){if(isAutoRunning())stopAuto("Bluetooth отключён пользователем");closeBluetooth(true);}

    public boolean selectManual(){if(isAutoRunning())stopAuto("Переход в ручной режим");return sendRaw("a0");}
    public boolean selectPid(){if(isAutoRunning())stopAuto("Переход в PID режим");return sendRaw("a1");}
    public boolean stopHeating(){main.removeCallbacks(heartbeat);AutoEngine.Update u=autoEngine.stop("СТОП");sendCommands(u.commands);boolean ok=sendRaw("a3");sendRaw("x0");autoStatus="ТЭН выключен";leaveForeground();emit();return ok;}
    public boolean setManualPower(double value){return valid0100(value)&&sendRaw("v"+num(value));}
    public boolean setChamberSetpoint(double value){return valid0100(value)&&sendRaw("k"+num(value));}
    public boolean setPidCoefficient(String prefix,String value){if(prefix==null||!"pidz".contains(prefix)||value==null||value.trim().isEmpty())return false;try{String scaled=new BigDecimal(value.trim().replace(',','.')).multiply(new BigDecimal("100")).stripTrailingZeros().toPlainString();return sendRaw(prefix+scaled);}catch(Exception e){return false;}}

    public boolean startAuto(AutoProgram program){
        if(!isBluetoothConnected()){lastError="Для Auto нужен Bluetooth";emit();return false;}
        try{
            AutoEngine.Update u=autoEngine.start(program,SystemClock.elapsedRealtime());
            if(!sendCommands(u.commands)){autoEngine.stop("Ошибка запуска");sendRaw("a3");sendRaw("x0");return false;}
            autoStatus=u.message;enterForeground();main.removeCallbacks(heartbeat);main.postDelayed(heartbeat,HEARTBEAT_MS);emit();return true;
        }catch(Exception e){lastError=safe(e);emit();return false;}
    }

    public void stopAuto(String reason){
        main.removeCallbacks(heartbeat);AutoEngine.Update u=autoEngine.stop(reason);sendCommands(u.commands);sendRaw("a3");sendRaw("x0");autoStatus=reason;leaveForeground();emit();
    }

    public void configureMqtt(String host,String port,String status,String command,String ack,String user,String pass,boolean tls,boolean autoConnect){
        prefs.edit().putString("broker",trim(host)).putString("port",trim(port)).putString("topic",trim(status)).putString("cmd_topic",trim(command)).putString("ack_topic",trim(ack)).putString("user",user==null?"":user).putString("pass",pass==null?"":pass).putBoolean("tls",tls).putBoolean("mqtt_auto",autoConnect).apply();
    }
    public void startMqtt(){mqttWanted=true;mqttBackoffSec=5;connectMqtt();}
    public void stopMqtt(){mqttWanted=false;main.removeCallbacks(mqttReconnect);MqttClient m=mqtt;mqtt=null;if(m!=null){try{m.publish(onlineTopic(),onlinePayload(false),true,0);}catch(Exception ignored){}m.close();}mqttConnecting=false;mqttState="MQTT отключен";emit();}

    private void connectMqtt(){
        if(mqttConnecting||isMqttConnected())return;String host=prefs.getString("broker","").trim();if(host.isEmpty()){mqttState="MQTT: broker не задан";emit();return;}int port;try{port=Integer.parseInt(prefs.getString("port","1883").trim());}catch(Exception e){mqttState="MQTT: неверный port";emit();return;}
        mqttConnecting=true;mqttState="MQTT: подключение…";emit();final int p=port;
        new Thread(()->{
            MqttClient c=new MqttClient(host,p,prefs.getBoolean("tls",false),prefs.getString("user",""),prefs.getString("pass",""));
            c.setClientId("HomeSmoke_"+deviceId());c.setWill(onlineTopic(),onlinePayload(false),true);c.setMessageListener(this::handleMqttMessage);
            try{c.connect();c.subscribe(commandTopic());mqtt=c;mqttConnecting=false;mqttBackoffSec=5;mqttState="MQTT подключен";c.publish(onlineTopic(),onlinePayload(true),true,0);main.post(this::emit);}
            catch(Exception e){c.close();mqttConnecting=false;mqttState="MQTT ошибка: "+safe(e);main.post(()->{emit();scheduleMqttReconnect();});}
        },"HomeSmoke-MQTT-connect").start();
    }

    private void scheduleMqttReconnect(){if(!mqttWanted)return;main.removeCallbacks(mqttReconnect);main.postDelayed(mqttReconnect,mqttBackoffSec*1000L);mqttBackoffSec=Math.min(60,mqttBackoffSec*2);}

    private void handleMqttMessage(String topic,String payload){
        if(!commandTopic().equals(topic))return;
        try{
            JSONObject o=new JSONObject(payload);String cmd=o.optString("cmd","");String requestId=o.optString("id","legacy-"+System.currentTimeMillis());long ts=o.optLong("ts",System.currentTimeMillis());
            if(Math.abs(System.currentTimeMillis()-ts)>COMMAND_MAX_AGE_MS){publishAck(requestId,cmd,false,"stale_command",Double.NaN);return;}
            if("set_temp".equals(cmd)){
                double v=o.getDouble("value");
                if(!valid0100(v)){publishAck(requestId,cmd,false,"out_of_range",v);return;}
                if(isAutoRunning()){publishAck(requestId,cmd,false,"android_auto_running",v);return;}
                if(!isBluetoothConnected()){publishAck(requestId,cmd,false,"bluetooth_not_connected",v);return;}
                if(latest==null||latest.mode!=1){publishAck(requestId,cmd,false,"pid_mode_required",v);return;}
                if(sendRaw("k"+num(v))){ackManager.track(requestId,v,SystemClock.elapsedRealtime());publishAck(requestId,cmd,true,"accepted_waiting_controller",v);}else publishAck(requestId,cmd,false,"bluetooth_send_error",v);
            } else if("stop".equals(cmd)) {
                stopHeating();publishAck(requestId,cmd,true,"stop_sent",Double.NaN);
            } else publishAck(requestId,cmd,false,"unsupported_command",Double.NaN);
        }catch(Exception e){publishAck("unknown","unknown",false,"bad_command",Double.NaN);}
    }

    private void readLoop(InputStream in)throws IOException{
        byte[] b=new byte[512];while(!Thread.currentThread().isInterrupted()){int n=in.read(b);if(n<0)throw new EOFException();if(n==0)continue;synchronized(rx){rx.append(new String(b,0,n,StandardCharsets.UTF_8));extractFrames();}}
    }
    private void extractFrames(){
        while(true){int end=rx.indexOf("end");if(end<0){if(rx.length()>8192)rx.delete(0,rx.length()-4096);return;}String frame=rx.substring(0,end+3);rx.delete(0,end+3);while(rx.length()>0&&(rx.charAt(0)=='\r'||rx.charAt(0)=='\n'))rx.deleteCharAt(0);processFrame(frame);}
    }
    private void processFrame(String frame){
        try{
            Telemetry t=TelemetryParser.parse(frame,System.currentTimeMillis());latest=t;long now=SystemClock.elapsedRealtime();
            ackManager.onTelemetry(t,now,this::publishCommandAck);
            if(autoEngine.getState()==AutoEngine.State.RUNNING){AutoEngine.Update u=autoEngine.onTelemetry(t,now);autoStatus=u.message;if(!u.commands.isEmpty())sendCommands(u.commands);if(autoEngine.getState()!=AutoEngine.State.RUNNING){main.removeCallbacks(heartbeat);leaveForeground();}}
            publishTelemetry(t);main.post(this::emit);
        }catch(Exception e){lastError="Пакет Arduino: "+safe(e);main.post(this::emit);}
    }

    private boolean sendCommands(List<String> commands){for(String c:commands)if(!sendRaw(c))return false;return true;}
    private boolean sendRaw(String command){BluetoothSocket s=btSocket;if(s==null||!s.isConnected())return false;try{synchronized(this){s.getOutputStream().write((command+TERMINATOR).getBytes(StandardCharsets.UTF_8));s.getOutputStream().flush();}return true;}catch(Exception e){lastError="Bluetooth send: "+safe(e);closeBluetoothInternal();main.post(this::emit);return false;}}

    private void publishTelemetry(Telemetry t){MqttClient m=mqtt;if(m==null||!m.isConnected())return;try{JSONObject o=new JSONObject();o.put("v",2);o.put("device_id",deviceId());o.put("ts",t.receivedAtMs);o.put("temp_ds",t.chamber);o.put("temp_tip_k",t.probeK);o.put("temp_tip_t",t.probeT);o.put("temp_k",t.chamberSetpoint);o.put("temp_p",t.productSetpoint);o.put("heater_power",t.heaterPower);o.put("mode",t.mode);o.put("last_command",t.lastCommand);o.put("status",t.lastCommand);o.put("kP",t.kP);o.put("kI",t.kI);o.put("kD",t.kD);o.put("zP",t.zP);o.put("android_auto_running",isAutoRunning());o.put("android_auto_stage",autoEngine.getStageIndex()+1);AutoProgram p=autoEngine.getProgram();o.put("android_auto_program",p==null?"":p.name);o.put("android_auto_status",autoStatus);m.publish(statusTopic(),o.toString(),true,0);}catch(Exception e){mqttState="MQTT publish: "+safe(e);}}
    private void publishCommandAck(String requestId,double value,boolean ok,String reason){publishAck(requestId,"set_temp",ok,reason,value);}
    private void publishAck(String id,String cmd,boolean ok,String state,double value){MqttClient m=mqtt;if(m==null||!m.isConnected())return;try{JSONObject o=new JSONObject();o.put("v",2);o.put("id",id);o.put("ts",System.currentTimeMillis());o.put("cmd",cmd);o.put("ok",ok);o.put("state",state);o.put("message",state);if(!Double.isNaN(value))o.put("value",value);m.publish(ackTopic(),o.toString(),false,0);}catch(Exception ignored){}}

    private void abortAuto(String reason){main.removeCallbacks(heartbeat);AutoEngine.Update u=autoEngine.stop(reason);sendCommands(u.commands);autoStatus=reason;leaveForeground();main.post(this::emit);}
    private void closeBluetooth(boolean update){closeBluetoothInternal();bluetoothName="";ackManager.failAll("bluetooth_disconnected",this::publishCommandAck);if(update)emit();}
    private void closeBluetoothInternal(){BluetoothSocket s=btSocket;btSocket=null;if(s!=null)try{s.close();}catch(Exception ignored){}Thread t=btThread;btThread=null;if(t!=null&&t!=Thread.currentThread())t.interrupt();}

    private void enterForeground(){
        if(Build.VERSION.SDK_INT>=26){NotificationChannel c=new NotificationChannel(CHANNEL,"HomeSmoke Auto",NotificationManager.IMPORTANCE_LOW);((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(c);}startForeground(NOTIFY_ID,buildNotification());
    }
    private Notification buildNotification(){Intent i=new Intent(this,MainActivity.class);PendingIntent pi=PendingIntent.getActivity(this,0,i,Build.VERSION.SDK_INT>=23?PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT:PendingIntent.FLAG_UPDATE_CURRENT);Notification.Builder b=Build.VERSION.SDK_INT>=26?new Notification.Builder(this,CHANNEL):new Notification.Builder(this);b.setSmallIcon(android.R.drawable.ic_lock_idle_alarm).setContentTitle("HomeSmoke Auto").setContentText(autoStatus).setOngoing(true).setContentIntent(pi);return b.build();}
    private void refreshNotification(){if(isAutoRunning())((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).notify(NOTIFY_ID,buildNotification());}
    private void leaveForeground(){if(Build.VERSION.SDK_INT>=24)stopForeground(STOP_FOREGROUND_REMOVE);else stopForeground(true);}

    private State snapshot(){return new State(isBluetoothConnected(),isMqttConnected(),bluetoothName,mqttState,autoStatus,lastError,latest,autoEngine.getState(),autoEngine.getStageIndex());}
    private void emit(){refreshNotification();Listener l=listener;if(l!=null)l.onState(snapshot());}

    private String statusTopic(){return topic("topic","homesmoke/status");}
    private String commandTopic(){return topic("cmd_topic","homesmoke/cmd");}
    private String ackTopic(){return topic("ack_topic","homesmoke/ack");}
    private String onlineTopic(){return "homesmoke/"+deviceId()+"/online";}
    private String onlinePayload(boolean online){try{JSONObject o=new JSONObject();o.put("v",2);o.put("device_id",deviceId());o.put("online",online);o.put("ts",System.currentTimeMillis());return o.toString();}catch(Exception e){return online?"true":"false";}}
    private String topic(String key,String def){String s=prefs.getString(key,def);return s==null||s.trim().isEmpty()?def:s.trim();}
    private void ensureDeviceId(){if(prefs.getString("device_id","").trim().isEmpty())prefs.edit().putString("device_id",UUID.randomUUID().toString().substring(0,8)).apply();}
    public String deviceId(){return prefs.getString("device_id","homesmoke");}
    private static boolean valid0100(double v){return !Double.isNaN(v)&&v>=0&&v<=100;}
    private static String num(double v){return BigDecimal.valueOf(v).stripTrailingZeros().toPlainString();}
    private static String trim(String s){return s==null?"":s.trim();}
    private static String safe(Exception e){String m=e.getMessage();return m==null?e.getClass().getSimpleName():m;}

    @Override public void onDestroy(){main.removeCallbacks(heartbeat);main.removeCallbacks(mqttReconnect);if(isAutoRunning()){sendRaw("a3");sendRaw("x0");}closeBluetoothInternal();MqttClient m=mqtt;mqtt=null;if(m!=null)m.close();super.onDestroy();}
}
