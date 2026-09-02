package com.bizard.homesmokeremote;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.text.TextUtils;
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

/** HomeSmoke Remote — MQTT-only monitor/control with correlated controller ACK. */
public class MainActivity extends Activity {
    private static final int NAVY=Color.rgb(9,47,73);
    private static final int BLUE=Color.rgb(31,122,210);
    private static final int BLUE_DARK=Color.rgb(26,91,164);
    private static final int BG=Color.rgb(245,247,250);
    private static final int CARD=Color.WHITE;
    private static final int TEXT=Color.rgb(21,31,47);
    private static final int MUTED=Color.rgb(101,116,139);
    private static final int BORDER=Color.rgb(220,225,232);
    private static final int GREEN=Color.rgb(35,151,83);
    private static final int RED=Color.rgb(229,40,40);
    private static final int ORANGE=Color.rgb(231,138,7);
    private static final int OFF=Color.rgb(116,129,145);
    private static final long STALE_MS=10000L;

    private SharedPreferences prefs;
    private SecretStore secrets;
    private MqttClient mqtt;
    private volatile boolean connecting,wantConnection;
    private long lastTelemetryAt=0;
    private String deviceId="—",pendingId="";

    private LinearLayout host,monitorPage,settingsPage;
    private TextView title,subtitle,mqttBadge,deviceBadge,brokerState,deviceState,camera,k,t,setpoint,power,mode,lastCommand,autoProgram,autoStage,autoStatus,lastUpdate,commandState;
    private Button back,settings,setButton,stopButton,disconnectButton;
    private EditText setInput,broker,port,statusTopic,commandTopic,ackTopic,user,pass;
    private CheckBox tls,autoConnect;
    private final Handler handler=new Handler(Looper.getMainLooper());

    private final Runnable health=new Runnable(){@Override public void run(){
        boolean mq=mqtt!=null&&mqtt.isConnected();
        setBrokerUi(mq,mq?"Брокер подключён":(connecting?"Подключение к брокеру…":"MQTT отключён"));
        boolean fresh=lastTelemetryAt>0&&System.currentTimeMillis()-lastTelemetryAt<=STALE_MS;
        setDeviceUi(fresh,fresh?"Коптильня онлайн · "+deviceId:(lastTelemetryAt==0?"Данные от коптильни не получены":"Коптильня не отвечает более 10 сек"));
        if(wantConnection&&!mq&&!connecting&&!broker.getText().toString().trim().isEmpty())connectMqtt(false);
        handler.postDelayed(this,3000);
    }};

    @Override protected void onCreate(Bundle b){
        super.onCreate(b);
        prefs=getSharedPreferences("homesmoke_remote",MODE_PRIVATE);
        secrets=new SecretStore(this);
        View root=buildRoot();
        setContentView(root);
        applyInsets(root);
        loadSettings();
        showMonitor();
        wantConnection=autoConnect.isChecked()&&!broker.getText().toString().trim().isEmpty();
        if(wantConnection)connectMqtt(false);
        handler.postDelayed(health,1500);
    }

    @Override protected void onDestroy(){
        saveSettings();
        wantConnection=false;
        handler.removeCallbacks(health);
        disconnectInternal(false);
        super.onDestroy();
    }

    private View buildRoot(){
        LinearLayout root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        root.addView(buildBar(),new LinearLayout.LayoutParams(-1,dp(66)));
        host=new LinearLayout(this);
        host.setOrientation(LinearLayout.VERTICAL);
        root.addView(host,new LinearLayout.LayoutParams(-1,0,1));
        monitorPage=buildMonitor();
        settingsPage=buildSettings();
        return root;
    }

    private View buildBar(){
        LinearLayout bar=new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(8),0,dp(6),0);
        bar.setBackgroundColor(NAVY);

        back=iconButton("‹");
        back.setTextSize(36);
        back.setVisibility(View.GONE);
        back.setOnClickListener(view->showMonitor());
        bar.addView(back,new LinearLayout.LayoutParams(dp(40),dp(46)));

        LinearLayout titles=new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        titles.setGravity(Gravity.CENTER_VERTICAL);
        titles.setPadding(dp(2),0,dp(3),0);
        title=text("HomeSmoke Remote",19,true,TEXT);
        title.setTextColor(Color.WHITE);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        subtitle=text("Удалённое управление",12,false,MUTED);
        subtitle.setTextColor(Color.rgb(211,222,232));
        subtitle.setSingleLine(true);
        subtitle.setEllipsize(TextUtils.TruncateAt.END);
        titles.addView(title);
        titles.addView(subtitle);
        bar.addView(titles,new LinearLayout.LayoutParams(0,-1,1));

        mqttBadge=badge("MQTT");
        deviceBadge=badge("SMOKE");
        bar.addView(mqttBadge,wrapMargin(2,0,2,0));
        bar.addView(deviceBadge,wrapMargin(2,0,3,0));

        settings=iconButton("⚙");
        settings.setTextSize(21);
        settings.setOnClickListener(view->showSettings());
        bar.addView(settings,new LinearLayout.LayoutParams(dp(40),dp(44)));
        return bar;
    }

    private LinearLayout buildMonitor(){
        LinearLayout p=page();

        LinearLayout healthCard=card();
        healthCard.addView(sectionTitle("Состояние связи"));
        brokerState=statusRow(healthCard,"MQTT","MQTT отключён",RED);
        deviceState=statusRow(healthCard,"Коптильня","Данные не получены",ORANGE);
        p.addView(healthCard,margin(8,10,8,5));

        LinearLayout cam=card();
        cam.addView(label("Камера"));
        camera=center("— °C",52,true,TEXT);
        camera.setPadding(0,dp(6),0,0);
        cam.addView(camera);
        setpoint=center("Уставка — °C",17,true,BLUE_DARK);
        setpoint.setPadding(0,0,0,dp(4));
        cam.addView(setpoint);
        p.addView(cam,margin(8,5,8,5));

        LinearLayout probes=new LinearLayout(this);
        probes.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout kc=metricCard("Щуп K");
        k=center("— °C",28,true,TEXT);
        kc.addView(k);
        LinearLayout tc=metricCard("Щуп T");
        t=center("— °C",28,true,TEXT);
        tc.addView(t);
        probes.addView(kc,half(8,4));
        probes.addView(tc,half(4,8));
        p.addView(probes);

        LinearLayout stats=new LinearLayout(this);
        stats.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout heater=metricCard("ТЭН");
        power=center("— %",25,true,ORANGE);
        heater.addView(power);
        LinearLayout modeCard=metricCard("Режим");
        mode=center("—",23,true,TEXT);
        modeCard.addView(mode);
        stats.addView(heater,half(8,4));
        stats.addView(modeCard,half(4,8));
        p.addView(stats);

        LinearLayout ac=card();
        ac.addView(sectionTitle("Auto"));
        autoProgram=info(ac,"Программа","—");
        autoStage=info(ac,"Этап","—");
        autoStatus=text("Auto выключено",14,false,MUTED);
        autoStatus.setPadding(0,dp(9),0,0);
        ac.addView(autoStatus);
        p.addView(ac,margin(8,5,8,5));

        LinearLayout ctrl=card();
        ctrl.addView(sectionTitle("Удалённое управление"));
        TextView hint=text("Уставка PID, °C",13,true,MUTED);
        hint.setPadding(0,dp(8),0,dp(5));
        ctrl.addView(hint);
        LinearLayout setRow=new LinearLayout(this);
        setRow.setGravity(Gravity.CENTER_VERTICAL);
        setInput=field("0…100",InputType.TYPE_CLASS_NUMBER);
        LinearLayout.LayoutParams ip=new LinearLayout.LayoutParams(0,dp(52),1f);
        ip.setMargins(0,0,dp(7),0);
        setRow.addView(setInput,ip);
        setButton=action("Применить",BLUE);
        setButton.setOnClickListener(view->sendSetpoint());
        setRow.addView(setButton,new LinearLayout.LayoutParams(0,dp(52),0.92f));
        ctrl.addView(setRow,new LinearLayout.LayoutParams(-1,-2));

        stopButton=action("STOP · выключить нагрев",RED);
        stopButton.setOnClickListener(view->confirmStop());
        LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(-1,dp(54));
        sp.setMargins(0,dp(10),0,0);
        ctrl.addView(stopButton,sp);

        commandState=text("Команды ещё не отправлялись",13,false,MUTED);
        commandState.setPadding(0,dp(10),0,0);
        ctrl.addView(commandState);
        p.addView(ctrl,margin(8,5,8,5));

        LinearLayout commandCard=card();
        lastCommand=info(commandCard,"Последняя команда","—");
        p.addView(commandCard,margin(8,5,8,4));

        lastUpdate=center("Данных ещё нет",12,false,MUTED);
        p.addView(lastUpdate,margin(8,3,8,20));
        return p;
    }

    private LinearLayout buildSettings(){
        LinearLayout p=page();

        LinearLayout intro=card();
        intro.addView(sectionTitle("MQTT подключение"));
        TextView versionText=text("HomeSmoke Remote 2.0.2 · Android 5+",13,false,MUTED);
        versionText.setPadding(0,dp(3),0,0);
        intro.addView(versionText);
        p.addView(intro,margin(8,10,8,5));

        LinearLayout form=card();
        broker=labeledField(form,"Broker / IP","Адрес MQTT брокера",InputType.TYPE_CLASS_TEXT);
        port=labeledField(form,"Port","1883",InputType.TYPE_CLASS_NUMBER);
        statusTopic=labeledField(form,"Status topic","homesmoke/status",InputType.TYPE_CLASS_TEXT);
        commandTopic=labeledField(form,"Command topic","homesmoke/cmd",InputType.TYPE_CLASS_TEXT);
        ackTopic=labeledField(form,"ACK topic","homesmoke/ack",InputType.TYPE_CLASS_TEXT);
        user=labeledField(form,"Логин","Необязательно",InputType.TYPE_CLASS_TEXT);
        pass=labeledField(form,"Пароль","Необязательно",InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);
        tls=check("Использовать TLS");
        autoConnect=check("Автоподключение и переподключение");
        form.addView(tls,checkParams());
        form.addView(autoConnect,checkParams());
        p.addView(form,margin(8,5,8,5));

        Button c=action("Сохранить и подключить",GREEN);
        c.setOnClickListener(view->{saveSettings();wantConnection=true;connectMqtt(true);});
        p.addView(c,buttonMargin(8,7,8,4));

        disconnectButton=action("Отключить MQTT",OFF);
        disconnectButton.setOnClickListener(view->{wantConnection=false;disconnectInternal(true);});
        p.addView(disconnectButton,buttonMargin(8,4,8,8));

        String sec=secrets.isEncrypted()?"Пароль MQTT хранится через Android Keystore.":"Android 5.0/5.1: защищённое хранилище этой реализации недоступно; используйте доверенную сеть/VPN.";
        TextView n=text(sec+" Команда температуры передаётся только целым значением 0…100 °C — в соответствии с текущим подтверждённым протоколом.",12,false,MUTED);
        n.setPadding(dp(12),dp(8),dp(12),dp(20));
        p.addView(n);
        return p;
    }

    private void loadSettings(){
        broker.setText(prefs.getString("broker",""));
        port.setText(prefs.getString("port","1883"));
        statusTopic.setText(prefs.getString("status_topic","homesmoke/status"));
        commandTopic.setText(prefs.getString("command_topic","homesmoke/cmd"));
        ackTopic.setText(prefs.getString("ack_topic","homesmoke/ack"));
        user.setText(prefs.getString("user",""));
        pass.setText(secrets.get());
        tls.setChecked(prefs.getBoolean("tls",false));
        autoConnect.setChecked(prefs.getBoolean("auto",true));
    }

    private void saveSettings(){
        prefs.edit().putString("broker",s(broker)).putString("port",s(port)).putString("status_topic",s(statusTopic)).putString("command_topic",s(commandTopic)).putString("ack_topic",s(ackTopic)).putString("user",user.getText().toString()).putBoolean("tls",tls.isChecked()).putBoolean("auto",autoConnect.isChecked()).apply();
        try{secrets.put(pass.getText().toString());}catch(Exception e){toast("Не удалось сохранить пароль защищённо");}
    }

    private void connectMqtt(boolean force){
        if(connecting)return;
        saveSettings();
        String h=s(broker);
        if(h.isEmpty()){if(force)toast("Укажите MQTT broker");return;}
        int po;
        try{po=Integer.parseInt(s(port));}catch(Exception e){if(force)toast("Неверный port");return;}
        String st=topic(s(statusTopic),"homesmoke/status"),at=topic(s(ackTopic),"homesmoke/ack");
        disconnectInternal(false);
        connecting=true;
        setBrokerUi(false,"Подключение к брокеру…");
        final MqttClient c=new MqttClient(h,po,tls.isChecked(),user.getText().toString(),secrets.get());
        mqtt=c;
        c.setMessageListener(this::message);
        new Thread(()->{
            try{
                c.connect();
                c.subscribe(st);
                c.subscribe(at);
                connecting=false;
                runOnUiThread(()->setBrokerUi(true,"Брокер подключён · "+h+":"+po));
            }catch(Exception e){
                c.close();
                if(mqtt==c)mqtt=null;
                connecting=false;
                runOnUiThread(()->setBrokerUi(false,"MQTT ошибка: "+safe(e)));
            }
        },"HomeSmokeRemote-connect").start();
    }

    private void message(String topic,String payload){
        String st=prefs.getString("status_topic","homesmoke/status"),at=prefs.getString("ack_topic","homesmoke/ack");
        if(topic.equals(st))status(payload);else if(topic.equals(at))ack(payload);
    }

    private void status(String payload){
        try{
            JSONObject o=new JSONObject(payload);
            String cam=o.optString("temp_ds","—"),pk=o.optString("temp_tip_k","—"),pt=o.optString("temp_tip_t","—"),sp=o.optString("temp_k","—"),pw=o.optString("heater_power","—"),md=o.optString("mode","—"),lc=o.optString("last_command",o.optString("status","—"));
            String ap=o.optString("android_auto_program","—"),as=o.optString("android_auto_status","Auto выключено");
            int stage=o.optInt("android_auto_stage",0);
            boolean ar=o.optBoolean("android_auto_running",false);
            String did=o.optString("device_id",deviceId);
            long ts=o.optLong("ts",System.currentTimeMillis());
            lastTelemetryAt=System.currentTimeMillis();
            deviceId=did;
            runOnUiThread(()->{
                camera.setText(deg(cam));
                k.setText(deg(pk));
                t.setText(deg(pt));
                setpoint.setText("Уставка "+deg(sp));
                power.setText(pw+" %");
                mode.setText(modeName(md));
                lastCommand.setText(lc);
                autoProgram.setText(ar?ap:"—");
                autoStage.setText(ar&&stage>0?String.valueOf(stage):"—");
                autoStatus.setText(as);
                lastUpdate.setText("Обновлено "+new SimpleDateFormat("HH:mm:ss",Locale.getDefault()).format(new Date(ts)));
                setDeviceUi(true,"Коптильня онлайн · "+did);
            });
        }catch(Exception ignored){}
    }

    private void ack(String payload){
        try{
            JSONObject o=new JSONObject(payload);
            String id=o.optString("id","");
            boolean ok=o.optBoolean("ok",false);
            String state=o.optString("state",o.optString("message",""));
            String value=o.has("value")?o.optString("value",""):"";
            runOnUiThread(()->{
                if(!pendingId.isEmpty()&&!id.isEmpty()&&!pendingId.equals(id))return;
                if("accepted_waiting_controller".equals(state)){commandState.setText("Команда принята HomeSmoke, ожидается Arduino…");return;}
                pendingId="";
                if(ok&&"applied".equals(state))commandState.setText("✓ Arduino применила уставку "+value+" °C");
                else if(ok&&"stop_sent".equals(state))commandState.setText("✓ STOP отправлен контроллеру");
                else commandState.setText("Команда не выполнена: "+translateState(state));
            });
        }catch(Exception ignored){}
    }

    private void sendSetpoint(){
        String raw=s(setInput);
        if(raw.isEmpty()){toast("Введите температуру");return;}
        double v;
        try{v=Double.parseDouble(raw.replace(',','.'));}catch(Exception e){toast("Неверное значение");return;}
        if(v<0||v>100||Math.abs(v-Math.rint(v))>.000001){toast("Нужно целое число 0…100 °C");return;}
        MqttClient c=mqtt;
        if(c==null||!c.isConnected()){toast("MQTT не подключён");return;}
        try{
            pendingId=UUID.randomUUID().toString();
            JSONObject o=new JSONObject();
            o.put("v",2);o.put("id",pendingId);o.put("cmd","set_temp");o.put("value",(int)Math.rint(v));o.put("ts",System.currentTimeMillis());
            c.publish(topic(s(commandTopic),"homesmoke/cmd"),o.toString(),false);
            commandState.setText("Команда отправлена, ожидается HomeSmoke…");
        }catch(Exception e){pendingId="";toast("Ошибка MQTT: "+safe(e));}
    }

    private void confirmStop(){
        new AlertDialog.Builder(this).setTitle("Удалённый STOP").setMessage("Выключить нагрев на коптильне?").setPositiveButton("STOP",(d,w)->sendStop()).setNegativeButton("Отмена",null).show();
    }

    private void sendStop(){
        MqttClient c=mqtt;
        if(c==null||!c.isConnected()){toast("MQTT не подключён");return;}
        try{
            pendingId=UUID.randomUUID().toString();
            JSONObject o=new JSONObject();
            o.put("v",2);o.put("id",pendingId);o.put("cmd","stop");o.put("ts",System.currentTimeMillis());
            c.publish(topic(s(commandTopic),"homesmoke/cmd"),o.toString(),false);
            commandState.setText("STOP отправлен, ожидается подтверждение…");
        }catch(Exception e){pendingId="";toast("Ошибка MQTT: "+safe(e));}
    }

    private void setBrokerUi(boolean connected,String txt){
        mqttBadge.setTextColor(Color.WHITE);
        mqttBadge.setBackground(round(connected?GREEN:(connecting?ORANGE:OFF),14));
        brokerState.setText(txt);
        if(disconnectButton!=null){disconnectButton.setEnabled(connected||connecting);disconnectButton.setAlpha((connected||connecting)?1f:.45f);}
    }

    private void setDeviceUi(boolean online,String txt){
        deviceBadge.setTextColor(Color.WHITE);
        deviceBadge.setBackground(round(online?GREEN:ORANGE,14));
        deviceState.setText(txt);
        setButton.setEnabled(online);
        stopButton.setEnabled(online);
        setButton.setAlpha(online?1f:.42f);
        stopButton.setAlpha(online?1f:.42f);
    }

    private void disconnectInternal(boolean ui){
        MqttClient c=mqtt;mqtt=null;connecting=false;
        if(c!=null)c.close();
        if(ui)setBrokerUi(false,"MQTT отключён");
    }

    private void showMonitor(){
        setPage(monitorPage);
        title.setText("HomeSmoke Remote");
        subtitle.setText("Удалённое управление");
        back.setVisibility(View.GONE);
        settings.setVisibility(View.VISIBLE);
    }

    private void showSettings(){
        setPage(settingsPage);
        title.setText("Настройки MQTT");
        subtitle.setText("HomeSmoke Remote 2.0.2");
        back.setVisibility(View.VISIBLE);
        settings.setVisibility(View.GONE);
    }

    private void setPage(View p){
        host.removeAllViews();
        if(p.getParent() instanceof ViewGroup)((ViewGroup)p.getParent()).removeView(p);
        ScrollView s=new ScrollView(this);
        s.setFillViewport(true);
        s.setClipToPadding(false);
        s.setVerticalScrollBarEnabled(false);
        s.setHorizontalScrollBarEnabled(false);
        s.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);
        s.addView(p,new ScrollView.LayoutParams(-1,-2));
        host.addView(s,new LinearLayout.LayoutParams(-1,-1));
        s.post(()->s.scrollTo(0,0));
    }

    private void applyInsets(View root){
        if(Build.VERSION.SDK_INT<21)return;
        root.setOnApplyWindowInsetsListener((view,i)->{
            int l,t,r,b;
            if(Build.VERSION.SDK_INT>=30){android.graphics.Insets x=i.getInsets(WindowInsets.Type.systemBars());l=x.left;t=x.top;r=x.right;b=x.bottom;}
            else{l=i.getSystemWindowInsetLeft();t=i.getSystemWindowInsetTop();r=i.getSystemWindowInsetRight();b=i.getSystemWindowInsetBottom();}
            view.setPadding(l,t,r,b);return i;
        });
        root.requestApplyInsets();
        getWindow().setStatusBarColor(NAVY);
        getWindow().setNavigationBarColor(BG);
    }

    private LinearLayout page(){
        LinearLayout p=new LinearLayout(this);
        p.setOrientation(LinearLayout.VERTICAL);
        p.setPadding(dp(4),0,dp(4),dp(20));
        p.setBackgroundColor(BG);
        return p;
    }

    private LinearLayout card(){
        LinearLayout c=new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(15),dp(13),dp(15),dp(13));
        c.setBackground(roundStroke(CARD,18,BORDER,1));
        if(Build.VERSION.SDK_INT>=21)c.setElevation(dp(1));
        return c;
    }

    private LinearLayout metricCard(String name){
        LinearLayout c=card();
        c.setGravity(Gravity.CENTER_HORIZONTAL);
        TextView l=center(name,14,true,MUTED);
        c.addView(l);
        return c;
    }

    private TextView statusRow(LinearLayout parent,String label,String initial,int dotColor){
        LinearLayout row=new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0,dp(8),0,dp(2));
        TextView dot=text("●",13,true,dotColor);
        dot.setPadding(0,0,dp(7),0);
        TextView a=text(label,14,true,TEXT);
        a.setPadding(0,0,dp(8),0);
        TextView b=text(initial,14,false,MUTED);
        b.setGravity(Gravity.END);
        b.setMaxLines(2);
        b.setEllipsize(TextUtils.TruncateAt.END);
        row.addView(dot);
        row.addView(a);
        row.addView(b,new LinearLayout.LayoutParams(0,-2,1));
        parent.addView(row);
        return b;
    }

    private TextView info(LinearLayout p,String label,String initial){
        LinearLayout r=new LinearLayout(this);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(0,dp(5),0,dp(5));
        TextView a=text(label,14,false,MUTED),b=text(initial,16,true,TEXT);
        a.setPadding(0,0,dp(8),0);
        b.setGravity(Gravity.END);
        b.setMaxLines(2);
        b.setEllipsize(TextUtils.TruncateAt.END);
        r.addView(a);
        r.addView(b,new LinearLayout.LayoutParams(0,-2,1));
        p.addView(r);
        return b;
    }

    private TextView sectionTitle(String s){return text(s,18,true,TEXT);}
    private TextView label(String s){return text(s,17,true,MUTED);}

    private TextView text(String s,int sp,boolean bold,int color){
        TextView t=new TextView(this);
        t.setText(s);t.setTextSize(sp);t.setTextColor(color);
        if(bold)t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private TextView center(String s,int sp,boolean bold,int color){
        TextView t=text(s,sp,bold,color);t.setGravity(Gravity.CENTER);return t;
    }

    private TextView badge(String s){
        TextView t=center(s,10,true,Color.WHITE);
        t.setMinWidth(dp(44));
        t.setMaxLines(1);
        t.setPadding(dp(7),dp(7),dp(7),dp(7));
        t.setBackground(round(OFF,14));
        return t;
    }

    private Button iconButton(String s){
        Button b=new Button(this);
        b.setText(s);b.setTextColor(Color.WHITE);b.setAllCaps(false);
        b.setPadding(0,0,0,0);b.setBackgroundColor(Color.TRANSPARENT);
        b.setMinWidth(0);b.setMinimumWidth(0);b.setMinHeight(0);b.setMinimumHeight(0);
        if(Build.VERSION.SDK_INT>=21){b.setBackgroundTintList(null);b.setStateListAnimator(null);}
        return b;
    }

    private Button action(String s,int color){
        Button b=new Button(this);
        b.setText(s);b.setAllCaps(false);b.setTextColor(Color.WHITE);b.setTextSize(16);b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setPadding(dp(8),0,dp(8),0);
        b.setMinWidth(0);b.setMinimumWidth(0);b.setMinHeight(0);b.setMinimumHeight(0);
        if(Build.VERSION.SDK_INT>=21){b.setBackgroundTintList(null);b.setStateListAnimator(null);}
        b.setBackground(round(color,14));
        return b;
    }

    private CheckBox check(String s){
        CheckBox c=new CheckBox(this);
        c.setText(s);c.setTextSize(15);c.setTypeface(Typeface.DEFAULT_BOLD);c.setTextColor(TEXT);
        c.setBackgroundColor(Color.TRANSPARENT);c.setPadding(0,0,0,0);c.setMinHeight(dp(48));
        if(Build.VERSION.SDK_INT>=21){
            int[][] states=new int[][]{new int[]{android.R.attr.state_checked},new int[]{-android.R.attr.state_checked}};
            c.setButtonTintList(new ColorStateList(states,new int[]{BLUE,Color.rgb(151,164,180)}));
            c.setStateListAnimator(null);
        }
        return c;
    }

    private EditText labeledField(LinearLayout parent,String label,String hint,int type){
        TextView l=text(label,13,true,MUTED);
        l.setPadding(0,dp(8),0,dp(5));
        parent.addView(l);
        EditText e=field(hint,type);
        parent.addView(e,new LinearLayout.LayoutParams(-1,dp(50)));
        return e;
    }

    private EditText field(String hint,int type){
        EditText e=new EditText(this);
        e.setHint(hint);e.setHintTextColor(Color.rgb(151,164,180));e.setInputType(type);e.setSingleLine(true);e.setTextSize(16);e.setTextColor(TEXT);
        e.setPadding(dp(12),0,dp(12),0);
        e.setMinWidth(0);e.setMinimumWidth(0);
        if(Build.VERSION.SDK_INT>=21)e.setBackgroundTintList(null);
        e.setBackground(roundStroke(Color.rgb(250,251,252),13,BORDER,1));
        return e;
    }

    private GradientDrawable round(int color,int radius){
        GradientDrawable g=new GradientDrawable();g.setColor(color);g.setCornerRadius(dp(radius));return g;
    }

    private GradientDrawable roundStroke(int color,int radius,int stroke,int width){
        GradientDrawable g=round(color,radius);g.setStroke(dp(width),stroke);return g;
    }

    private LinearLayout.LayoutParams margin(int l,int t,int r,int b){
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(dp(l),dp(t),dp(r),dp(b));return p;
    }

    private LinearLayout.LayoutParams buttonMargin(int l,int t,int r,int b){
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(54));p.setMargins(dp(l),dp(t),dp(r),dp(b));return p;
    }

    private LinearLayout.LayoutParams wrapMargin(int l,int t,int r,int b){
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-2,-2);p.setMargins(dp(l),dp(t),dp(r),dp(b));return p;
    }

    private LinearLayout.LayoutParams checkParams(){
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(0,dp(2),0,0);return p;
    }

    private LinearLayout.LayoutParams half(int l,int r){
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-2,1);p.setMargins(dp(l),dp(5),dp(r),dp(5));return p;
    }

    private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}
    private static String s(EditText e){return e.getText().toString().trim();}
    private static String topic(String s,String d){s=s==null?"":s.trim();return s.isEmpty()?d:s;}
    private static String deg(String s){return (s==null||s.trim().isEmpty()?"—":s.trim())+" °C";}
    private static String modeName(String m){if("0".equals(m))return "Ручной";if("1".equals(m))return "PID";if("3".equals(m))return "STOP";return m;}
    private static String translateState(String s){if("pid_mode_required".equals(s))return "сначала включите PID режим";if("android_auto_running".equals(s))return "уставкой управляет Auto";if("bluetooth_not_connected".equals(s))return "Bluetooth коптильни отключён";if("controller_ack_timeout".equals(s))return "Arduino не подтвердила уставку";if("stale_command".equals(s))return "команда устарела";return s;}
    private static String safe(Exception e){return e.getMessage()==null?e.getClass().getSimpleName():e.getMessage();}
    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_SHORT).show();}

    @Override public void onBackPressed(){if(back.getVisibility()==View.VISIBLE)showMonitor();else super.onBackPressed();}
}
