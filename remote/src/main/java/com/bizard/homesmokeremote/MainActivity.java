package com.bizard.homesmokeremote;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.UUID;

/** HomeSmoke Remote 2.0 — MQTT-only monitor/control with correlated controller ACK. */
public class MainActivity extends Activity {
    private static final int BLUE=Color.rgb(7,92,170),BG=Color.rgb(239,241,244),GREEN=Color.rgb(35,145,70),RED=Color.rgb(180,45,45),ORANGE=Color.rgb(220,125,20);
    private static final long STALE_MS=10000L;

    private SharedPreferences prefs;
    private SecretStore secrets;
    private MqttClient mqtt;
    private volatile boolean connecting,wantConnection;
    private long lastTelemetryAt=0;
    private String deviceId="—",pendingId="";

    private LinearLayout host,monitorPage,settingsPage;
    private TextView title,brokerDot,deviceDot,brokerState,deviceState,camera,k,t,setpoint,power,mode,lastCommand,autoProgram,autoStage,autoStatus,lastUpdate,commandState;
    private Button back,settings,setButton,stopButton;
    private EditText setInput,broker,port,statusTopic,commandTopic,ackTopic,user,pass;
    private CheckBox tls,autoConnect;
    private final Handler handler=new Handler(Looper.getMainLooper());

    private final Runnable health=new Runnable(){@Override public void run(){
        boolean mq=mqtt!=null&&mqtt.isConnected();
        setBrokerUi(mq,mq?"MQTT брокер подключён":(connecting?"MQTT: подключение…":"MQTT отключён"));
        boolean fresh=lastTelemetryAt>0&&System.currentTimeMillis()-lastTelemetryAt<=STALE_MS;
        setDeviceUi(fresh,fresh?"Коптильня онлайн · "+deviceId:(lastTelemetryAt==0?"Данные от коптильни не получены":"Коптильня не отвечает >10 сек"));
        if(wantConnection&&!mq&&!connecting&&!broker.getText().toString().trim().isEmpty())connectMqtt(false);
        handler.postDelayed(this,3000);
    }};

    @Override protected void onCreate(Bundle b){super.onCreate(b);prefs=getSharedPreferences("homesmoke_remote",MODE_PRIVATE);secrets=new SecretStore(this);View root=buildRoot();setContentView(root);applyInsets(root);loadSettings();showMonitor();wantConnection=autoConnect.isChecked()&&!broker.getText().toString().trim().isEmpty();if(wantConnection)connectMqtt(false);handler.postDelayed(health,1500);}
    @Override protected void onDestroy(){saveSettings();wantConnection=false;handler.removeCallbacks(health);disconnectInternal(false);super.onDestroy();}

    private View buildRoot(){LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(BG);root.addView(buildBar(),new LinearLayout.LayoutParams(-1,dp(56)));host=new LinearLayout(this);host.setOrientation(LinearLayout.VERTICAL);root.addView(host,new LinearLayout.LayoutParams(-1,0,1));monitorPage=buildMonitor();settingsPage=buildSettings();return root;}
    private View buildBar(){LinearLayout bar=new LinearLayout(this);bar.setGravity(Gravity.CENTER_VERTICAL);bar.setPadding(dp(8),0,dp(8),0);bar.setBackgroundColor(BLUE);back=button("←");back.setTextSize(28);back.setVisibility(View.GONE);back.setTextColor(Color.WHITE);back.setOnClickListener(v->showMonitor());bar.addView(back,new LinearLayout.LayoutParams(dp(46),dp(44)));title=text("HomeSmoke Remote",19,true);title.setTextColor(Color.WHITE);bar.addView(title,new LinearLayout.LayoutParams(0,-1,1));brokerDot=dot();deviceDot=dot();bar.addView(brokerDot);bar.addView(deviceDot);settings=button("⚙");settings.setTextSize(23);settings.setTextColor(Color.WHITE);settings.setOnClickListener(v->showSettings());bar.addView(settings,new LinearLayout.LayoutParams(dp(48),dp(44)));return bar;}

    private LinearLayout buildMonitor(){LinearLayout p=page();LinearLayout healthCard=card();brokerState=text("MQTT отключён",14,true);deviceState=text("Данные от коптильни не получены",14,true);healthCard.addView(brokerState);healthCard.addView(deviceState);p.addView(healthCard,margin(8,8,8,5));
        LinearLayout cam=card();cam.addView(center("ТЕМПЕРАТУРА КАМЕРЫ",14,true));camera=center("— °C",50,true);cam.addView(camera);setpoint=center("Уставка: — °C",16,false);cam.addView(setpoint);p.addView(cam,margin(8,5,8,5));
        LinearLayout probes=new LinearLayout(this);LinearLayout kc=card();kc.addView(center("Щуп K",15,false));k=center("— °C",28,true);kc.addView(k);LinearLayout tc=card();tc.addView(center("Щуп T",15,false));t=center("— °C",28,true);tc.addView(t);probes.addView(kc,half(8,4));probes.addView(tc,half(4,8));p.addView(probes);
        LinearLayout info=card();power=info(info,"Мощность ТЭНа","— %");mode=info(info,"Режим Arduino","—");lastCommand=info(info,"Последняя команда","—");p.addView(info,margin(8,5,8,5));
        LinearLayout ac=card();ac.addView(text("AUTO",14,true));autoProgram=info(ac,"Программа","—");autoStage=info(ac,"Этап","—");autoStatus=text("Auto выключено",14,false);autoStatus.setPadding(0,dp(7),0,0);ac.addView(autoStatus);p.addView(ac,margin(8,5,8,5));
        LinearLayout ctrl=card();ctrl.addView(text("УПРАВЛЕНИЕ PID",15,true));setInput=edit("Целая температура 0…100 °C",InputType.TYPE_CLASS_NUMBER);ctrl.addView(setInput);setButton=action("Установить температуру",GREEN);setButton.setOnClickListener(v->sendSetpoint());ctrl.addView(setButton,new LinearLayout.LayoutParams(-1,dp(52)));stopButton=action("СТОП / выключить ТЭН",RED);stopButton.setOnClickListener(v->confirmStop());LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(-1,dp(52));sp.setMargins(0,dp(8),0,0);ctrl.addView(stopButton,sp);commandState=text("Команды ещё не отправлялись",13,false);commandState.setPadding(0,dp(9),0,0);ctrl.addView(commandState);p.addView(ctrl,margin(8,5,8,5));
        lastUpdate=center("Данных ещё нет",12,false);p.addView(lastUpdate,margin(8,2,8,16));return p;}

    private LinearLayout buildSettings(){LinearLayout p=page();p.addView(center("Настройки MQTT",22,true));broker=edit("Broker / IP",InputType.TYPE_CLASS_TEXT);port=edit("Port",InputType.TYPE_CLASS_NUMBER);statusTopic=edit("Status topic",InputType.TYPE_CLASS_TEXT);commandTopic=edit("Command topic",InputType.TYPE_CLASS_TEXT);ackTopic=edit("ACK topic",InputType.TYPE_CLASS_TEXT);user=edit("Логин",InputType.TYPE_CLASS_TEXT);pass=edit("Пароль",InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);p.addView(broker);p.addView(port);p.addView(statusTopic);p.addView(commandTopic);p.addView(ackTopic);p.addView(user);p.addView(pass);tls=check("TLS");autoConnect=check("Автоподключение и переподключение");p.addView(tls);p.addView(autoConnect);Button c=action("Сохранить и подключить",GREEN);c.setOnClickListener(v->{saveSettings();wantConnection=true;connectMqtt(true);});p.addView(c,margin(8,8,8,4));Button d=button("Отключить MQTT");d.setOnClickListener(v->{wantConnection=false;disconnectInternal(true);});p.addView(d,margin(8,2,8,8));String sec=secrets.isEncrypted()?"Пароль MQTT хранится через Android Keystore.":"Android 5.0/5.1: защищённое хранилище этой реализации недоступно; используйте доверенную сеть/VPN.";TextView n=text(sec+" Команда температуры — только целое 0…100 °C, поскольку именно так её принимает текущий контроллер.",12,false);n.setPadding(dp(8),dp(10),dp(8),dp(10));p.addView(n);return p;}

    private void loadSettings(){broker.setText(prefs.getString("broker",""));port.setText(prefs.getString("port","1883"));statusTopic.setText(prefs.getString("status_topic","homesmoke/status"));commandTopic.setText(prefs.getString("command_topic","homesmoke/cmd"));ackTopic.setText(prefs.getString("ack_topic","homesmoke/ack"));user.setText(prefs.getString("user",""));pass.setText(secrets.get());tls.setChecked(prefs.getBoolean("tls",false));autoConnect.setChecked(prefs.getBoolean("auto",true));}
    private void saveSettings(){prefs.edit().putString("broker",s(broker)).putString("port",s(port)).putString("status_topic",s(statusTopic)).putString("command_topic",s(commandTopic)).putString("ack_topic",s(ackTopic)).putString("user",user.getText().toString()).putBoolean("tls",tls.isChecked()).putBoolean("auto",autoConnect.isChecked()).apply();try{secrets.put(pass.getText().toString());}catch(Exception e){toast("Не удалось сохранить пароль защищённо");}}

    private void connectMqtt(boolean force){if(connecting)return;saveSettings();String h=s(broker);if(h.isEmpty()){if(force)toast("Укажите MQTT broker");return;}int po;try{po=Integer.parseInt(s(port));}catch(Exception e){if(force)toast("Неверный port");return;}String st=topic(s(statusTopic),"homesmoke/status"),at=topic(s(ackTopic),"homesmoke/ack");disconnectInternal(false);connecting=true;setBrokerUi(false,"MQTT: подключение…");final MqttClient c=new MqttClient(h,po,tls.isChecked(),user.getText().toString(),secrets.get());mqtt=c;c.setMessageListener(this::message);new Thread(()->{try{c.connect();c.subscribe(st);c.subscribe(at);connecting=false;runOnUiThread(()->setBrokerUi(true,"MQTT брокер подключён: "+h+":"+po));}catch(Exception e){c.close();if(mqtt==c)mqtt=null;connecting=false;runOnUiThread(()->setBrokerUi(false,"MQTT ошибка: "+safe(e)));}},"HomeSmokeRemote-connect").start();}
    private void message(String topic,String payload){String st=prefs.getString("status_topic","homesmoke/status"),at=prefs.getString("ack_topic","homesmoke/ack");if(topic.equals(st))status(payload);else if(topic.equals(at))ack(payload);}

    private void status(String payload){try{JSONObject o=new JSONObject(payload);String cam=o.optString("temp_ds","—"),pk=o.optString("temp_tip_k","—"),pt=o.optString("temp_tip_t","—"),sp=o.optString("temp_k","—"),pw=o.optString("heater_power","—"),md=o.optString("mode","—"),lc=o.optString("last_command",o.optString("status","—"));String ap=o.optString("android_auto_program","—"),as=o.optString("android_auto_status","Auto выключено");int stage=o.optInt("android_auto_stage",0);boolean ar=o.optBoolean("android_auto_running",false);String did=o.optString("device_id",deviceId);long ts=o.optLong("ts",System.currentTimeMillis());lastTelemetryAt=System.currentTimeMillis();deviceId=did;runOnUiThread(()->{camera.setText(deg(cam));k.setText(deg(pk));t.setText(deg(pt));setpoint.setText("Уставка: "+deg(sp));power.setText(pw+" %");mode.setText(modeName(md));lastCommand.setText(lc);autoProgram.setText(ar?ap:"—");autoStage.setText(ar&&stage>0?String.valueOf(stage):"—");autoStatus.setText(as);lastUpdate.setText("Последние данные: "+new SimpleDateFormat("HH:mm:ss",Locale.getDefault()).format(new Date(ts)));setDeviceUi(true,"Коптильня онлайн · "+did);});}catch(Exception ignored){}}
    private void ack(String payload){try{JSONObject o=new JSONObject(payload);String id=o.optString("id","");boolean ok=o.optBoolean("ok",false);String state=o.optString("state",o.optString("message",""));String value=o.has("value")?o.optString("value",""):"";runOnUiThread(()->{if(!pendingId.isEmpty()&&!id.isEmpty()&&!pendingId.equals(id))return;if("accepted_waiting_controller".equals(state)){commandState.setText("Команда принята HomeSmoke, ожидается Arduino…");return;}pendingId="";if(ok&&"applied".equals(state))commandState.setText("✓ Arduino применила уставку "+value+" °C");else if(ok&&"stop_sent".equals(state))commandState.setText("✓ STOP отправлен контроллеру");else commandState.setText("Команда не выполнена: "+translateState(state));});}catch(Exception ignored){}}

    private void sendSetpoint(){String raw=s(setInput);if(raw.isEmpty()){toast("Введите температуру");return;}double v;try{v=Double.parseDouble(raw.replace(',','.'));}catch(Exception e){toast("Неверное значение");return;}if(v<0||v>100||Math.abs(v-Math.rint(v))>.000001){toast("Нужно целое число 0…100 °C");return;}MqttClient c=mqtt;if(c==null||!c.isConnected()){toast("MQTT не подключён");return;}try{pendingId=UUID.randomUUID().toString();JSONObject o=new JSONObject();o.put("v",2);o.put("id",pendingId);o.put("cmd","set_temp");o.put("value",(int)Math.rint(v));o.put("ts",System.currentTimeMillis());c.publish(topic(s(commandTopic),"homesmoke/cmd"),o.toString(),false);commandState.setText("Команда отправлена, ожидается HomeSmoke…");}catch(Exception e){pendingId="";toast("Ошибка MQTT: "+safe(e));}}
    private void confirmStop(){new AlertDialog.Builder(this).setTitle("Удалённый СТОП").setMessage("Выключить ТЭН на коптильне?").setPositiveButton("СТОП",(d,w)->sendStop()).setNegativeButton("Отмена",null).show();}
    private void sendStop(){MqttClient c=mqtt;if(c==null||!c.isConnected()){toast("MQTT не подключён");return;}try{pendingId=UUID.randomUUID().toString();JSONObject o=new JSONObject();o.put("v",2);o.put("id",pendingId);o.put("cmd","stop");o.put("ts",System.currentTimeMillis());c.publish(topic(s(commandTopic),"homesmoke/cmd"),o.toString(),false);commandState.setText("STOP отправлен, ожидается подтверждение…");}catch(Exception e){pendingId="";toast("Ошибка MQTT: "+safe(e));}}

    private void setBrokerUi(boolean connected,String txt){brokerDot.setTextColor(connected?GREEN:RED);brokerState.setText(txt);}
    private void setDeviceUi(boolean online,String txt){deviceDot.setTextColor(online?GREEN:ORANGE);deviceState.setText(txt);setButton.setEnabled(online);stopButton.setEnabled(online);}
    private void disconnectInternal(boolean ui){MqttClient c=mqtt;mqtt=null;connecting=false;if(c!=null)c.close();if(ui)setBrokerUi(false,"MQTT отключён");}
    private void showMonitor(){setPage(monitorPage);title.setText("HomeSmoke Remote");back.setVisibility(View.GONE);settings.setVisibility(View.VISIBLE);}
    private void showSettings(){setPage(settingsPage);title.setText("Настройки MQTT");back.setVisibility(View.VISIBLE);settings.setVisibility(View.GONE);}
    private void setPage(View p){host.removeAllViews();if(p.getParent() instanceof ViewGroup)((ViewGroup)p.getParent()).removeView(p);ScrollView s=new ScrollView(this);s.setFillViewport(true);s.addView(p,new ScrollView.LayoutParams(-1,-2));host.addView(s,new LinearLayout.LayoutParams(-1,-1));}

    private void applyInsets(View root){if(Build.VERSION.SDK_INT<21)return;root.setOnApplyWindowInsetsListener((v,i)->{int l,t,r,b;if(Build.VERSION.SDK_INT>=30){android.graphics.Insets x=i.getInsets(WindowInsets.Type.systemBars());l=x.left;t=x.top;r=x.right;b=x.bottom;}else{l=i.getSystemWindowInsetLeft();t=i.getSystemWindowInsetTop();r=i.getSystemWindowInsetRight();b=i.getSystemWindowInsetBottom();}v.setPadding(l,t,r,b);return i;});root.requestApplyInsets();getWindow().setStatusBarColor(BLUE);getWindow().setNavigationBarColor(BG);}
    private LinearLayout page(){LinearLayout p=new LinearLayout(this);p.setOrientation(LinearLayout.VERTICAL);p.setPadding(dp(4),dp(4),dp(4),dp(22));p.setBackgroundColor(BG);return p;}
    private LinearLayout card(){LinearLayout c=new LinearLayout(this);c.setOrientation(LinearLayout.VERTICAL);c.setPadding(dp(14),dp(13),dp(14),dp(13));c.setBackground(round(Color.WHITE,13));return c;}
    private TextView info(LinearLayout p,String label,String initial){LinearLayout r=new LinearLayout(this);r.setGravity(Gravity.CENTER_VERTICAL);TextView a=text(label,15,false),b=text(initial,17,true);b.setGravity(Gravity.END);r.addView(a,new LinearLayout.LayoutParams(0,-2,1));r.addView(b);p.addView(r);return b;}
    private TextView text(String s,int sp,boolean bold){TextView t=new TextView(this);t.setText(s);t.setTextSize(sp);t.setTextColor(Color.rgb(25,25,25));if(bold)t.setTypeface(Typeface.DEFAULT_BOLD);return t;}
    private TextView center(String s,int sp,boolean bold){TextView t=text(s,sp,bold);t.setGravity(Gravity.CENTER);return t;}
    private TextView dot(){TextView t=text("●",17,true);t.setTextColor(RED);t.setGravity(Gravity.CENTER);t.setPadding(dp(3),0,dp(3),0);return t;}
    private Button button(String s){Button b=new Button(this);b.setText(s);b.setAllCaps(false);return b;}
    private Button action(String s,int color){Button b=button(s);b.setTextColor(Color.WHITE);b.setTextSize(16);b.setBackground(round(color,10));return b;}
    private CheckBox check(String s){CheckBox c=new CheckBox(this);c.setText(s);c.setTextSize(16);c.setTextColor(Color.BLACK);return c;}
    private EditText edit(String hint,int type){EditText e=new EditText(this);e.setHint(hint);e.setInputType(type);e.setSingleLine(true);e.setTextSize(16);e.setBackgroundColor(Color.WHITE);e.setPadding(dp(12),0,dp(12),0);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(54));p.setMargins(dp(7),dp(4),dp(7),dp(4));e.setLayoutParams(p);return e;}
    private GradientDrawable round(int color,int radius){GradientDrawable g=new GradientDrawable();g.setColor(color);g.setCornerRadius(dp(radius));return g;}
    private LinearLayout.LayoutParams margin(int l,int t,int r,int b){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(dp(l),dp(t),dp(r),dp(b));return p;}
    private LinearLayout.LayoutParams half(int l,int r){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-2,1);p.setMargins(dp(l),dp(4),dp(r),dp(4));return p;}
    private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}
    private static String s(EditText e){return e.getText().toString().trim();}
    private static String topic(String s,String d){s=s==null?"":s.trim();return s.isEmpty()?d:s;}
    private static String deg(String s){return (s==null||s.trim().isEmpty()?"—":s.trim())+" °C";}
    private static String modeName(String m){if("0".equals(m))return "Ручной";if("1".equals(m))return "PID";if("3".equals(m))return "СТОП";return m;}
    private static String translateState(String s){if("pid_mode_required".equals(s))return "сначала включите PID режим";if("android_auto_running".equals(s))return "уставкой управляет Auto";if("bluetooth_not_connected".equals(s))return "Bluetooth коптильни отключён";if("controller_ack_timeout".equals(s))return "Arduino не подтвердила уставку";if("stale_command".equals(s))return "команда устарела";return s;}
    private static String safe(Exception e){return e.getMessage()==null?e.getClass().getSimpleName():e.getMessage();}
    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_SHORT).show();}
    @Override public void onBackPressed(){if(back.getVisibility()==View.VISIBLE)showMonitor();else super.onBackPressed();}
}
