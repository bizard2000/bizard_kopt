package com.bizard.homesmokeremote;

import android.app.Activity;
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

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final int BLUE = Color.rgb(7,92,170);
    private static final int BG = Color.rgb(232,232,232);
    private static final int GREEN = Color.rgb(38,145,70);
    private static final int RED = Color.rgb(170,50,50);

    private SharedPreferences prefs;
    private MqttClient mqtt;
    private volatile boolean connecting;
    private volatile boolean wantConnection;

    private LinearLayout pageHost, monitorPage, settingsPage;
    private TextView title, mqttDot, mqttState;
    private Button backButton, settingsButton, setTempButton;

    private TextView cameraValue, probeKValue, probeTValue, setpointValue;
    private TextView productSetpointValue, heaterValue, modeValue, statusValue;
    private TextView lastUpdate, ackState;
    private EditText setpointInput;

    private EditText broker, port, statusTopic, commandTopic, ackTopic, user, pass;
    private CheckBox tls, autoConnect;
    private TextView settingsState;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable healthTask = new Runnable() {
        @Override public void run() {
            boolean connected = mqtt != null && mqtt.isConnected();
            setConnectionUi(connected, connected ? "MQTT подключен" : (connecting ? "MQTT: подключение…" : "MQTT отключен"));
            if(wantConnection && !connected && !connecting && broker != null && !broker.getText().toString().trim().isEmpty()) {
                connectMqtt(false);
            }
            handler.postDelayed(this, 5000);
        }
    };

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = getSharedPreferences("homesmoke_remote", MODE_PRIVATE);
        View root = buildRoot();
        setContentView(root);
        applySystemInsets(root);
        loadSettings();
        showMonitor();
        wantConnection = autoConnect.isChecked() && !broker.getText().toString().trim().isEmpty();
        if(wantConnection) connectMqtt(false);
        handler.postDelayed(healthTask, 3000);
    }

    private View buildRoot() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        root.addView(buildActionBar(), new LinearLayout.LayoutParams(-1, dp(56)));
        pageHost = new LinearLayout(this);
        pageHost.setOrientation(LinearLayout.VERTICAL);
        pageHost.setBackgroundColor(BG);
        root.addView(pageHost, new LinearLayout.LayoutParams(-1,0,1));
        monitorPage = buildMonitor();
        settingsPage = buildSettings();
        return root;
    }

    private View buildActionBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(8),0,dp(8),0);
        bar.setBackgroundColor(BLUE);

        backButton = button("←");
        backButton.setTextSize(28);
        backButton.setVisibility(View.GONE);
        backButton.setOnClickListener(v -> showMonitor());
        bar.addView(backButton, new LinearLayout.LayoutParams(dp(48),dp(44)));

        title = text("HomeSmoke Remote",20,true);
        title.setTextColor(Color.WHITE);
        bar.addView(title,new LinearLayout.LayoutParams(0,-1,1));

        mqttDot = text("●",18,true);
        mqttDot.setTextColor(RED);
        mqttDot.setGravity(Gravity.CENTER);
        bar.addView(mqttDot,new LinearLayout.LayoutParams(dp(30),-1));

        settingsButton = button("⚙");
        settingsButton.setTextSize(24);
        settingsButton.setOnClickListener(v -> showSettings());
        bar.addView(settingsButton,new LinearLayout.LayoutParams(dp(48),dp(44)));
        return bar;
    }

    private LinearLayout buildMonitor() {
        LinearLayout root = page();

        mqttState = center("MQTT отключен",14,true);
        mqttState.setPadding(dp(8),dp(8),dp(8),dp(8));
        root.addView(mqttState);

        LinearLayout cameraCard = card();
        cameraCard.addView(center("Температура в камере",20,false));
        cameraValue = center("— °C",58,true);
        cameraValue.setPadding(0,dp(8),0,dp(8));
        cameraCard.addView(cameraValue);
        root.addView(cameraCard, margins(8,8,8,6));

        LinearLayout probes = new LinearLayout(this);
        probes.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout kCard = card();
        kCard.addView(center("Щуп K",16,false));
        probeKValue = center("— °C",30,true); kCard.addView(probeKValue);
        LinearLayout tCard = card();
        tCard.addView(center("Щуп T",16,false));
        probeTValue = center("— °C",30,true); tCard.addView(probeTValue);
        LinearLayout.LayoutParams half = new LinearLayout.LayoutParams(0,-2,1);
        half.setMargins(dp(8),dp(4),dp(4),dp(4)); probes.addView(kCard,half);
        half = new LinearLayout.LayoutParams(0,-2,1);
        half.setMargins(dp(4),dp(4),dp(8),dp(4)); probes.addView(tCard,half);
        root.addView(probes);

        LinearLayout info = card();
        setpointValue = infoLine(info,"Уставка камеры","— °C");
        productSetpointValue = infoLine(info,"Т° продукта / Т° п","— °C");
        heaterValue = infoLine(info,"Мощность ТЭНа","— %");
        modeValue = infoLine(info,"Режим","—");
        statusValue = infoLine(info,"Статус","—");
        root.addView(info,margins(8,6,8,8));

        LinearLayout control = card();
        TextView h = center("Изменить температуру камеры",19,true);
        h.setPadding(0,0,0,dp(8)); control.addView(h);
        setpointInput = edit("0…100 °C", InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);
        control.addView(setpointInput,new LinearLayout.LayoutParams(-1,dp(58)));
        setTempButton = button("Установить температуру");
        setTempButton.setTextSize(17);
        setTempButton.setTextColor(Color.WHITE);
        setTempButton.setBackground(round(GREEN,12));
        setTempButton.setOnClickListener(v -> sendSetpoint());
        LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(-1,dp(56)); bp.setMargins(0,dp(10),0,0); control.addView(setTempButton,bp);
        ackState = center("Команды ещё не отправлялись",13,false);
        ackState.setPadding(0,dp(10),0,0); control.addView(ackState);
        root.addView(control,margins(8,6,8,8));

        lastUpdate = center("Данные ещё не получены",12,false);
        lastUpdate.setPadding(dp(8),dp(6),dp(8),dp(16)); root.addView(lastUpdate);
        return root;
    }

    private LinearLayout buildSettings() {
        LinearLayout root = page();
        TextView h=center("Настройки MQTT",22,true); h.setPadding(0,dp(8),0,dp(12)); root.addView(h);
        broker=edit("Broker / IP",InputType.TYPE_CLASS_TEXT);
        port=edit("Port",InputType.TYPE_CLASS_NUMBER);
        statusTopic=edit("Status topic",InputType.TYPE_CLASS_TEXT);
        commandTopic=edit("Command topic",InputType.TYPE_CLASS_TEXT);
        ackTopic=edit("ACK topic",InputType.TYPE_CLASS_TEXT);
        user=edit("Логин",InputType.TYPE_CLASS_TEXT);
        pass=edit("Пароль",InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);
        root.addView(broker); root.addView(port); root.addView(statusTopic); root.addView(commandTopic); root.addView(ackTopic); root.addView(user); root.addView(pass);
        tls=check("TLS"); autoConnect=check("Автоподключение / переподключение");
        root.addView(tls); root.addView(autoConnect);

        settingsState=text("",13,false); settingsState.setPadding(dp(8),dp(8),dp(8),dp(8)); root.addView(settingsState);
        Button connect=button("Сохранить и подключить"); connect.setTextSize(17); connect.setOnClickListener(v->{saveSettings();wantConnection=true;connectMqtt(true);});
        root.addView(connect,new LinearLayout.LayoutParams(-1,dp(56)));
        Button disconnect=button("Отключить MQTT"); disconnect.setTextSize(16); disconnect.setOnClickListener(v->{wantConnection=false;disconnectMqtt();});
        LinearLayout.LayoutParams dpv=new LinearLayout.LayoutParams(-1,dp(54)); dpv.setMargins(0,dp(8),0,0); root.addView(disconnect,dpv);

        TextView note=text("Удалённая уставка отправляется без Retain в Command topic как JSON set_temp. Основной HomeSmoke проверяет диапазон 0–100 °C и передаёт контроллеру команду k<значение>\\0.",13,false);
        note.setPadding(dp(6),dp(16),dp(6),dp(12)); root.addView(note);
        return root;
    }

    private void loadSettings() {
        broker.setText(prefs.getString("broker",""));
        port.setText(prefs.getString("port","1883"));
        statusTopic.setText(prefs.getString("status_topic","homesmoke/status"));
        commandTopic.setText(prefs.getString("command_topic","homesmoke/cmd"));
        ackTopic.setText(prefs.getString("ack_topic","homesmoke/ack"));
        user.setText(prefs.getString("user",""));
        pass.setText(prefs.getString("pass",""));
        tls.setChecked(prefs.getBoolean("tls",false));
        autoConnect.setChecked(prefs.getBoolean("auto",true));
    }

    private void saveSettings() {
        prefs.edit()
                .putString("broker",broker.getText().toString().trim())
                .putString("port",port.getText().toString().trim())
                .putString("status_topic",statusTopic.getText().toString().trim())
                .putString("command_topic",commandTopic.getText().toString().trim())
                .putString("ack_topic",ackTopic.getText().toString().trim())
                .putString("user",user.getText().toString())
                .putString("pass",pass.getText().toString())
                .putBoolean("tls",tls.isChecked())
                .putBoolean("auto",autoConnect.isChecked())
                .apply();
    }

    private void connectMqtt(boolean force) {
        if(connecting)return;
        saveSettings();
        String host=broker.getText().toString().trim();
        if(host.isEmpty()){ if(force)toast("Укажите MQTT broker"); return; }
        int p;
        try{p=Integer.parseInt(port.getText().toString().trim());}catch(Exception e){if(force)toast("Неверный MQTT port");return;}
        String st=topicOrDefault(statusTopic.getText().toString(),"homesmoke/status");
        String at=topicOrDefault(ackTopic.getText().toString(),"homesmoke/ack");

        disconnectMqttInternal(false);
        connecting=true;
        setConnectionUi(false,"MQTT: подключение…");
        final MqttClient c=new MqttClient(host,p,tls.isChecked(),user.getText().toString(),pass.getText().toString());
        mqtt=c;
        c.setMessageListener(this::handleMessage);
        new Thread(()->{
            try{
                c.connect(); c.subscribe(st); c.subscribe(at);
                connecting=false;
                runOnUiThread(()->setConnectionUi(true,"MQTT подключен: "+host+":"+p));
            }catch(Exception e){
                c.close(); if(mqtt==c)mqtt=null; connecting=false;
                runOnUiThread(()->setConnectionUi(false,"MQTT ошибка: "+e.getMessage()));
            }
        },"HomeSmokeRemote-connect").start();
    }

    private void handleMessage(String topic,String payload) {
        String st=prefs.getString("status_topic","homesmoke/status");
        String at=prefs.getString("ack_topic","homesmoke/ack");
        if(topic.equals(st)) handleStatus(payload);
        else if(topic.equals(at)) handleAck(payload);
    }

    private void handleStatus(String payload) {
        try{
            JSONObject o=new JSONObject(payload);
            String camera=o.optString("temp_ds","—");
            String k=o.optString("temp_tip_k","—");
            String t=o.optString("temp_tip_t","—");
            String set=o.optString("temp_k","—");
            String product=o.optString("temp_p","—");
            String heater=o.optString("heater_power","—");
            String mode=o.optString("mode","—");
            String status=o.optString("status","—");
            long ts=o.optLong("ts",System.currentTimeMillis());
            runOnUiThread(()->{
                cameraValue.setText(deg(camera)); probeKValue.setText(deg(k)); probeTValue.setText(deg(t));
                setpointValue.setText(deg(set)); productSetpointValue.setText(deg(product)); heaterValue.setText(heater+" %");
                modeValue.setText(mode); statusValue.setText(status);
                lastUpdate.setText("Последние данные: "+new SimpleDateFormat("HH:mm:ss",Locale.getDefault()).format(new Date(ts)));
            });
        }catch(Exception ignored){}
    }

    private void handleAck(String payload) {
        try{
            JSONObject o=new JSONObject(payload);
            boolean ok=o.optBoolean("ok",false);
            String message=o.optString("message","");
            String value=o.has("value")?o.optString("value",""):"";
            runOnUiThread(()->ackState.setText(ok?"✓ Контроллеру отправлена уставка "+value+" °C":"Команда не выполнена: "+message));
        }catch(Exception ignored){}
    }

    private void sendSetpoint() {
        String raw=setpointInput.getText().toString().trim().replace(',','.');
        if(raw.isEmpty()){toast("Введите температуру");return;}
        double v;
        try{v=Double.parseDouble(raw);}catch(Exception e){toast("Неверная температура");return;}
        if(v<0||v>100){toast("Допустимо 0–100 °C");return;}
        MqttClient c=mqtt;
        if(c==null||!c.isConnected()){toast("MQTT не подключен");return;}
        try{
            String normalized=BigDecimal.valueOf(v).stripTrailingZeros().toPlainString();
            JSONObject o=new JSONObject();
            o.put("cmd","set_temp"); o.put("value",Double.parseDouble(normalized)); o.put("ts",System.currentTimeMillis());
            String ct=topicOrDefault(commandTopic.getText().toString(),"homesmoke/cmd");
            c.publish(ct,o.toString(),false);
            ackState.setText("Команда отправлена: "+normalized+" °C, ожидается подтверждение");
        }catch(Exception e){toast("Ошибка MQTT: "+e.getMessage());}
    }

    private void setConnectionUi(boolean connected,String text) {
        if(mqttDot!=null)mqttDot.setTextColor(connected?GREEN:RED);
        if(mqttState!=null)mqttState.setText(text);
        if(settingsState!=null)settingsState.setText(text);
    }

    private void disconnectMqtt(){ disconnectMqttInternal(true); }
    private void disconnectMqttInternal(boolean ui){
        MqttClient c=mqtt; mqtt=null; connecting=false; if(c!=null)c.close();
        if(ui)setConnectionUi(false,"MQTT отключен");
    }

    private void showMonitor(){
        pageHost.removeAllViews(); pageHost.addView(scroll(monitorPage),new LinearLayout.LayoutParams(-1,-1));
        title.setText("HomeSmoke Remote"); backButton.setVisibility(View.GONE); settingsButton.setVisibility(View.VISIBLE);
    }
    private void showSettings(){
        pageHost.removeAllViews(); pageHost.addView(scroll(settingsPage),new LinearLayout.LayoutParams(-1,-1));
        title.setText("Настройки MQTT"); backButton.setVisibility(View.VISIBLE); settingsButton.setVisibility(View.GONE);
    }

    @Override public void onBackPressed(){ if(backButton.getVisibility()==View.VISIBLE)showMonitor(); else super.onBackPressed(); }
    @Override protected void onDestroy(){ saveSettings(); wantConnection=false; handler.removeCallbacks(healthTask); disconnectMqttInternal(false); super.onDestroy(); }

    private void applySystemInsets(View root) {
        root.setOnApplyWindowInsetsListener((v,insets)->{
            int l,t,r,b;
            if(Build.VERSION.SDK_INT>=30){
                android.graphics.Insets x=insets.getInsets(WindowInsets.Type.systemBars()); l=x.left;t=x.top;r=x.right;b=x.bottom;
            }else{
                l=insets.getSystemWindowInsetLeft();t=insets.getSystemWindowInsetTop();r=insets.getSystemWindowInsetRight();b=insets.getSystemWindowInsetBottom();
            }
            v.setPadding(l,t,r,b); return insets;
        });
        root.requestApplyInsets();
        getWindow().setStatusBarColor(BLUE); getWindow().setNavigationBarColor(BG);
    }

    private LinearLayout page(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);l.setBackgroundColor(BG);l.setPadding(dp(4),dp(4),dp(4),dp(20));return l;}
    private LinearLayout card(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);l.setPadding(dp(14),dp(14),dp(14),dp(14));l.setBackground(round(Color.WHITE,12));return l;}
    private TextView infoLine(LinearLayout parent,String label,String initial){
        LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(0,dp(5),0,dp(5));
        TextView a=text(label,15,false); TextView b=text(initial,17,true); b.setGravity(Gravity.END);
        row.addView(a,new LinearLayout.LayoutParams(0,-2,1));row.addView(b,new LinearLayout.LayoutParams(-2,-2));parent.addView(row);return b;
    }
    private TextView text(String s,int sp,boolean bold){TextView t=new TextView(this);t.setText(s);t.setTextSize(sp);t.setTextColor(Color.BLACK);if(bold)t.setTypeface(Typeface.DEFAULT_BOLD);return t;}
    private TextView center(String s,int sp,boolean bold){TextView t=text(s,sp,bold);t.setGravity(Gravity.CENTER);return t;}
    private Button button(String s){Button b=new Button(this);b.setText(s);b.setAllCaps(false);return b;}
    private CheckBox check(String s){CheckBox c=new CheckBox(this);c.setText(s);c.setTextSize(17);c.setTextColor(Color.BLACK);c.setPadding(dp(8),dp(5),dp(8),dp(5));return c;}
    private EditText edit(String hint,int type){EditText e=new EditText(this);e.setHint(hint);e.setTextSize(17);e.setInputType(type);e.setSingleLine(true);e.setBackgroundColor(Color.WHITE);e.setPadding(dp(12),0,dp(12),0);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(56));p.setMargins(dp(6),dp(4),dp(6),dp(4));e.setLayoutParams(p);return e;}
    private ScrollView scroll(View child){if(child.getParent() instanceof ViewGroup)((ViewGroup)child.getParent()).removeView(child);ScrollView s=new ScrollView(this);s.setFillViewport(true);s.addView(child,new ScrollView.LayoutParams(-1,-2));return s;}
    private GradientDrawable round(int color,int radius){GradientDrawable g=new GradientDrawable();g.setColor(color);g.setCornerRadius(dp(radius));return g;}
    private LinearLayout.LayoutParams margins(int l,int t,int r,int b){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(dp(l),dp(t),dp(r),dp(b));return p;}
    private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}
    private String deg(String s){return (s==null||s.trim().isEmpty()?"—":s.trim())+" °C";}
    private String topicOrDefault(String s,String d){s=s==null?"":s.trim();return s.isEmpty()?d:s;}
    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_SHORT).show();}
}
