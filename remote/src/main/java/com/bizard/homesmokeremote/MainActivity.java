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
import android.text.method.PasswordTransformationMethod;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
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
    private static final int INFO_BG=Color.rgb(240,247,255);
    private static final int SUCCESS_BG=Color.rgb(237,249,242);
    private static final int WARN_BG=Color.rgb(255,247,232);
    private static final int ERROR_BG=Color.rgb(255,239,239);
    private static final long STALE_MS=10000L;
    private static final long TREND_WINDOW_MS=5L*60L*1000L;
    private static final int MAX_COMMAND_HISTORY=5;

    private SharedPreferences prefs;
    private SecretStore secrets;
    private MqttClient mqtt;
    private volatile boolean connecting,wantConnection;
    private long lastTelemetryAt=0;
    private String deviceId="—",pendingId="",pendingLabel="";
    private String lastModeRaw="—";
    private boolean lastAutoRunning=false;
    private double lastCameraValue=Double.NaN,lastSetpointValue=Double.NaN;
    private boolean showTechnicalEnabled=false,passwordVisible=false;

    private final ArrayList<TempSample> tempSamples=new ArrayList<>();
    private final ArrayList<String> commandHistoryItems=new ArrayList<>();

    private LinearLayout host,monitorPage,settingsPage,ackFlow,controllerCommandBlock,advancedMqttBlock,commandHistoryCard;
    private TextView title,subtitle,mqttBadge,deviceBadge;
    private TextView mqttDot,deviceDot,brokerState,deviceState,brokerDetail,deviceDetail,systemState;
    private TextView camera,cameraSummary,tempTrend,k,t,power,mode,lastCommand,autoProgram,autoStage,autoStatus,autoChip,lastUpdate,commandState,commandHistory,controlAvailability;
    private TextView ackRemote,ackHome,ackController;
    private ProgressBar heaterProgress;
    private Button back,settings,setButton,stopButton,disconnectButton,passToggle;
    private EditText setInput,broker,port,statusTopic,commandTopic,ackTopic,user,pass;
    private CheckBox tls,autoConnect,keepScreenOn,showTechnical;
    private final Handler handler=new Handler(Looper.getMainLooper());

    private final Runnable health=new Runnable(){@Override public void run(){
        boolean mq=mqtt!=null&&mqtt.isConnected();
        setBrokerUi(mq,mq?"Брокер подключён":(connecting?"Подключение к брокеру…":"MQTT отключён"));
        boolean fresh=isTelemetryFresh();
        String detail;
        if(lastTelemetryAt==0)detail="Данные от коптильни не получены";
        else if(fresh)detail="Коптильня онлайн · "+deviceId;
        else detail="Последние данные · "+relativeAge(lastTelemetryAt);
        setDeviceUi(fresh,detail);
        updateLastDataCaption();
        updateCameraSummaryAndTrend();
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
        loadCommandHistory();
        applyUiPreferences();
        showMonitor();
        wantConnection=autoConnect.isChecked()&&!broker.getText().toString().trim().isEmpty();
        if(wantConnection)connectMqtt(false);
        handler.postDelayed(health,1500);
    }

    @Override protected void onDestroy(){
        saveSettings();
        saveCommandHistory();
        wantConnection=false;
        handler.removeCallbacks(health);
        disconnectInternal(false);
        super.onDestroy();
    }

    private View buildRoot(){
        LinearLayout root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        root.addView(buildBar(),new LinearLayout.LayoutParams(-1,dp(60)));
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
        back.setTextSize(34);
        back.setVisibility(View.GONE);
        back.setOnClickListener(view->showMonitor());
        bar.addView(back,new LinearLayout.LayoutParams(dp(38),dp(44)));

        LinearLayout titles=new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        titles.setGravity(Gravity.CENTER_VERTICAL);
        titles.setPadding(dp(2),0,dp(3),0);
        title=text("HomeSmoke Remote",18,true,TEXT);
        title.setTextColor(Color.WHITE);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        subtitle=text("Удалённое управление",11,false,MUTED);
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
        settings.setTextSize(20);
        settings.setOnClickListener(view->showSettings());
        bar.addView(settings,new LinearLayout.LayoutParams(dp(38),dp(42)));
        return bar;
    }

    private LinearLayout buildMonitor(){
        LinearLayout p=page();

        LinearLayout healthCard=card();
        LinearLayout healthHeader=new LinearLayout(this);
        healthHeader.setGravity(Gravity.CENTER_VERTICAL);
        healthHeader.addView(sectionTitle("Связь"),new LinearLayout.LayoutParams(0,-2,1));
        systemState=statusChip("ОФЛАЙН",OFF);
        healthHeader.addView(systemState);
        healthCard.addView(healthHeader);
        brokerState=statusRow(healthCard,"MQTT","Отключён",RED,true);
        deviceState=statusRow(healthCard,"Коптильня","Нет данных",ORANGE,false);
        brokerDetail=smallDetail("MQTT отключён");
        deviceDetail=smallDetail("Телеметрия ещё не поступала");
        healthCard.addView(brokerDetail);
        healthCard.addView(deviceDetail);
        p.addView(healthCard,margin(8,8,8,4));

        LinearLayout cam=card();
        cam.addView(label("Камера"));
        camera=center("— °C",44,true,TEXT);
        camera.setPadding(0,dp(2),0,0);
        cam.addView(camera);
        cameraSummary=center("Уставка — °C",14,true,BLUE_DARK);
        cam.addView(cameraSummary);
        tempTrend=center("Тренд —",11,false,MUTED);
        tempTrend.setPadding(0,dp(2),0,0);
        cam.addView(tempTrend);
        p.addView(cam,margin(8,4,8,4));

        LinearLayout probes=new LinearLayout(this);
        probes.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout kc=metricCard("Щуп K");
        k=center("— °C",24,true,TEXT);
        kc.addView(k);
        LinearLayout tc=metricCard("Щуп T");
        t=center("— °C",24,true,TEXT);
        tc.addView(t);
        probes.addView(kc,half(8,4));
        probes.addView(tc,half(4,8));
        p.addView(probes);

        LinearLayout stats=new LinearLayout(this);
        stats.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout heater=metricCard("ТЭН");
        power=center("— %",22,true,ORANGE);
        heater.addView(power);
        heaterProgress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);
        heaterProgress.setMax(100);
        heaterProgress.setProgress(0);
        if(Build.VERSION.SDK_INT>=21)heaterProgress.setProgressTintList(ColorStateList.valueOf(ORANGE));
        LinearLayout.LayoutParams hp=new LinearLayout.LayoutParams(-1,dp(5));
        hp.setMargins(dp(8),dp(4),dp(8),0);
        heater.addView(heaterProgress,hp);
        LinearLayout modeCard=metricCard("Режим");
        mode=center("—",21,true,TEXT);
        mode.setSingleLine(true);
        mode.setEllipsize(TextUtils.TruncateAt.END);
        modeCard.addView(mode);
        stats.addView(heater,half(8,4));
        stats.addView(modeCard,half(4,8));
        p.addView(stats);

        LinearLayout ac=card();
        LinearLayout autoHeader=new LinearLayout(this);
        autoHeader.setGravity(Gravity.CENTER_VERTICAL);
        autoHeader.addView(sectionTitle("Auto"),new LinearLayout.LayoutParams(0,-2,1));
        autoChip=statusChip("ВЫКЛ",OFF);
        autoHeader.addView(autoChip);
        ac.addView(autoHeader);
        autoProgram=info(ac,"Программа","—");
        autoStage=info(ac,"Этап","—");
        ((View)autoProgram.getParent()).setVisibility(View.GONE);
        ((View)autoStage.getParent()).setVisibility(View.GONE);
        autoStatus=text("Программа не запущена",13,false,MUTED);
        autoStatus.setPadding(0,dp(5),0,0);
        ac.addView(autoStatus);
        p.addView(ac,margin(8,4,8,4));

        LinearLayout ctrl=card();
        ctrl.addView(sectionTitle("Удалённое управление"));
        controlAvailability=text("Управление недоступно · нет свежих данных",12,true,MUTED);
        controlAvailability.setPadding(dp(9),dp(6),dp(9),dp(6));
        controlAvailability.setBackground(roundStroke(WARN_BG,10,ORANGE,1));
        LinearLayout.LayoutParams avp=new LinearLayout.LayoutParams(-1,-2);
        avp.setMargins(0,dp(6),0,dp(6));
        ctrl.addView(controlAvailability,avp);

        LinearLayout setRow=new LinearLayout(this);
        setRow.setGravity(Gravity.CENTER_VERTICAL);
        setInput=field("Уставка 0…100 °C",InputType.TYPE_CLASS_NUMBER);
        LinearLayout.LayoutParams ip=new LinearLayout.LayoutParams(0,dp(46),1f);
        ip.setMargins(0,0,dp(7),0);
        setRow.addView(setInput,ip);
        setButton=action("Применить",BLUE);
        setButton.setTextSize(14);
        setButton.setOnClickListener(view->sendSetpoint());
        setRow.addView(setButton,new LinearLayout.LayoutParams(0,dp(46),0.88f));
        ctrl.addView(setRow,new LinearLayout.LayoutParams(-1,-2));

        stopButton=action("STOP · выключить нагрев",RED);
        stopButton.setTextSize(15);
        stopButton.setOnClickListener(view->confirmStop());
        LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(-1,dp(48));
        sp.setMargins(0,dp(7),0,0);
        ctrl.addView(stopButton,sp);

        ackFlow=buildAckFlow();
        ackFlow.setVisibility(View.GONE);
        LinearLayout.LayoutParams afp=new LinearLayout.LayoutParams(-1,-2);
        afp.setMargins(0,dp(8),0,0);
        ctrl.addView(ackFlow,afp);

        commandState=text("Команды ещё не отправлялись",12,false,MUTED);
        commandState.setPadding(dp(9),dp(7),dp(9),dp(7));
        commandState.setBackground(roundStroke(INFO_BG,10,BORDER,1));
        LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,-2);
        cp.setMargins(0,dp(7),0,0);
        ctrl.addView(commandState,cp);
        p.addView(ctrl,margin(8,4,8,4));

        commandHistoryCard=card();
        commandHistoryCard.addView(label("История команд Remote"));
        commandHistory=text("Команд Remote ещё не было",12,false,MUTED);
        commandHistory.setPadding(0,dp(5),0,dp(4));
        commandHistory.setLineSpacing(0,1.08f);
        commandHistoryCard.addView(commandHistory);

        controllerCommandBlock=new LinearLayout(this);
        controllerCommandBlock.setOrientation(LinearLayout.VERTICAL);
        TextView controllerLabel=text("Последняя команда контроллера",11,false,MUTED);
        controllerLabel.setPadding(0,dp(4),0,0);
        controllerCommandBlock.addView(controllerLabel);
        lastCommand=text("—",14,true,TEXT);
        lastCommand.setPadding(0,dp(2),0,0);
        lastCommand.setMaxLines(2);
        lastCommand.setEllipsize(TextUtils.TruncateAt.END);
        controllerCommandBlock.addView(lastCommand);
        commandHistoryCard.addView(controllerCommandBlock);
        p.addView(commandHistoryCard,margin(8,4,8,3));

        lastUpdate=center("Данных ещё нет",11,false,MUTED);
        p.addView(lastUpdate,margin(8,2,8,16));
        return p;
    }

    private LinearLayout buildAckFlow(){
        LinearLayout row=new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        ackRemote=ackPill("Remote");
        ackHome=ackPill("HomeSmoke");
        ackController=ackPill("Arduino");
        row.addView(ackRemote,new LinearLayout.LayoutParams(0,dp(30),1));
        row.addView(arrow("›"));
        row.addView(ackHome,new LinearLayout.LayoutParams(0,dp(30),1.25f));
        row.addView(arrow("›"));
        row.addView(ackController,new LinearLayout.LayoutParams(0,dp(30),1));
        setAckProgress(0,false);
        return row;
    }

    private TextView ackPill(String text){
        TextView t=center(text,10,true,Color.WHITE);
        t.setSingleLine(true);
        t.setPadding(dp(4),0,dp(4),0);
        t.setBackground(round(OFF,10));
        return t;
    }

    private TextView arrow(String s){
        TextView t=center(s,19,true,MUTED);
        t.setPadding(dp(3),0,dp(3),0);
        return t;
    }

    private LinearLayout buildSettings(){
        LinearLayout p=page();

        LinearLayout intro=card();
        intro.addView(sectionTitle("MQTT подключение"));
        TextView versionText=text("HomeSmoke Remote 2.0.8 · Android 5+",12,false,MUTED);
        versionText.setPadding(0,dp(2),0,0);
        intro.addView(versionText);
        p.addView(intro,margin(8,8,8,4));

        LinearLayout form=card();
        broker=labeledField(form,"Broker / IP","Адрес MQTT брокера",InputType.TYPE_CLASS_TEXT);
        port=labeledField(form,"Port","1883",InputType.TYPE_CLASS_NUMBER);

        advancedMqttBlock=new LinearLayout(this);
        advancedMqttBlock.setOrientation(LinearLayout.VERTICAL);
        statusTopic=labeledField(advancedMqttBlock,"Status topic","homesmoke/status",InputType.TYPE_CLASS_TEXT);
        commandTopic=labeledField(advancedMqttBlock,"Command topic","homesmoke/cmd",InputType.TYPE_CLASS_TEXT);
        ackTopic=labeledField(advancedMqttBlock,"ACK topic","homesmoke/ack",InputType.TYPE_CLASS_TEXT);
        form.addView(advancedMqttBlock);

        user=labeledField(form,"Логин","Необязательно",InputType.TYPE_CLASS_TEXT);

        TextView passLabel=text("Пароль",12,true,MUTED);
        passLabel.setPadding(0,dp(6),0,dp(4));
        form.addView(passLabel);
        LinearLayout passRow=new LinearLayout(this);
        passRow.setGravity(Gravity.CENTER_VERTICAL);
        pass=field("Необязательно",InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);
        pass.setTransformationMethod(PasswordTransformationMethod.getInstance());
        LinearLayout.LayoutParams pp=new LinearLayout.LayoutParams(0,dp(46),1f);
        pp.setMargins(0,0,dp(7),0);
        passRow.addView(pass,pp);
        passToggle=action("Показать",OFF);
        passToggle.setTextSize(11);
        passToggle.setOnClickListener(view->setPasswordVisible(!passwordVisible));
        passRow.addView(passToggle,new LinearLayout.LayoutParams(dp(92),dp(46)));
        form.addView(passRow);

        tls=check("Использовать TLS");
        autoConnect=check("Автоподключение и переподключение");
        form.addView(tls,checkParams());
        form.addView(autoConnect,checkParams());
        p.addView(form,margin(8,4,8,4));

        LinearLayout ui=card();
        ui.addView(sectionTitle("Интерфейс"));
        keepScreenOn=check("Не выключать экран при открытом Remote");
        showTechnical=check("Показывать технические данные");
        keepScreenOn.setOnCheckedChangeListener((buttonView,isChecked)->applyUiPreferences());
        showTechnical.setOnCheckedChangeListener((buttonView,isChecked)->{showTechnicalEnabled=isChecked;applyUiPreferences();});
        ui.addView(keepScreenOn,checkParams());
        ui.addView(showTechnical,checkParams());
        TextView uiHint=text("Технические данные включают MQTT topics, подробности соединения/устройства и последнюю команду контроллера.",11,false,MUTED);
        uiHint.setPadding(0,dp(2),0,0);
        ui.addView(uiHint);
        p.addView(ui,margin(8,4,8,4));

        Button c=action("Сохранить и подключить",GREEN);
        c.setOnClickListener(view->{saveSettings();applyUiPreferences();wantConnection=true;connectMqtt(true);});
        p.addView(c,buttonMargin(8,6,8,3));

        disconnectButton=action("Отключить MQTT",OFF);
        disconnectButton.setOnClickListener(view->{wantConnection=false;disconnectInternal(true);});
        p.addView(disconnectButton,buttonMargin(8,3,8,6));

        String sec=secrets.isEncrypted()?"Пароль MQTT хранится через Android Keystore.":"Android 5.0/5.1: защищённое хранилище этой реализации недоступно; используйте доверенную сеть/VPN.";
        TextView n=text(sec+" Команда температуры передаётся только целым значением 0…100 °C — в соответствии с текущим подтверждённым протоколом.",11,false,MUTED);
        n.setPadding(dp(12),dp(6),dp(12),dp(16));
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
        setPasswordVisible(false);
        tls.setChecked(prefs.getBoolean("tls",false));
        autoConnect.setChecked(prefs.getBoolean("auto",true));
        keepScreenOn.setChecked(prefs.getBoolean("keep_screen_on",false));
        showTechnical.setChecked(prefs.getBoolean("show_technical",false));
        showTechnicalEnabled=showTechnical.isChecked();
    }

    private void saveSettings(){
        prefs.edit()
                .putString("broker",s(broker)).putString("port",s(port))
                .putString("status_topic",s(statusTopic)).putString("command_topic",s(commandTopic)).putString("ack_topic",s(ackTopic))
                .putString("user",user.getText().toString()).putBoolean("tls",tls.isChecked()).putBoolean("auto",autoConnect.isChecked())
                .putBoolean("keep_screen_on",keepScreenOn.isChecked()).putBoolean("show_technical",showTechnical.isChecked()).apply();
        try{secrets.put(pass.getText().toString());}catch(Exception e){toast("Не удалось сохранить пароль защищённо");}
    }

    private void setPasswordVisible(boolean visible){
        if(pass==null)return;
        passwordVisible=visible;
        int pos=pass.getSelectionStart();
        pass.setTransformationMethod(visible?null:PasswordTransformationMethod.getInstance());
        if(passToggle!=null)passToggle.setText(visible?"Скрыть":"Показать");
        int len=pass.getText()==null?0:pass.getText().length();
        pass.setSelection(Math.min(Math.max(pos,0),len));
    }

    private void applyUiPreferences(){
        if(keepScreenOn!=null&&keepScreenOn.isChecked())getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        showTechnicalEnabled=showTechnical!=null&&showTechnical.isChecked();
        int v=showTechnicalEnabled?View.VISIBLE:View.GONE;
        if(brokerDetail!=null)brokerDetail.setVisibility(v);
        if(deviceDetail!=null)deviceDetail.setVisibility(v);
        if(controllerCommandBlock!=null)controllerCommandBlock.setVisibility(v);
        if(advancedMqttBlock!=null)advancedMqttBlock.setVisibility(v);
        if(commandHistoryCard!=null)commandHistoryCard.setVisibility((showTechnicalEnabled||!commandHistoryItems.isEmpty())?View.VISIBLE:View.GONE);
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
            long ts=normalizeTelemetryTs(o.optLong("ts",0L));
            double camValue=parseNumber(cam),spValue=parseNumber(sp),powerValue=parseNumber(pw);
            boolean fresh=isFreshAt(ts);
            lastTelemetryAt=ts;
            deviceId=did;
            runOnUiThread(()->{
                lastCameraValue=camValue;
                lastSetpointValue=spValue;
                lastModeRaw=md;
                lastAutoRunning=ar;
                camera.setText(deg(cam));
                k.setText(deg(pk));
                t.setText(deg(pt));
                power.setText(Double.isNaN(powerValue)?"— %":formatPlain(powerValue)+" %");
                if(!Double.isNaN(powerValue))heaterProgress.setProgress(clamp((int)Math.round(powerValue),0,100));
                else heaterProgress.setProgress(0);
                updateModeUi(md);
                lastCommand.setText(lc);
                updateAutoUi(ar,ap,stage,as);
                addTempSample(camValue,ts);
                updateCameraSummaryAndTrend();
                updateLastDataCaption();
                setDeviceUi(fresh,fresh?"Коптильня онлайн · "+did:"Последние данные · "+relativeAge(ts));
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
                if("accepted_waiting_controller".equals(state)){
                    setAckProgress(2,false);
                    setCommandUi("HomeSmoke принял команду · ожидается Arduino",1);
                    return;
                }
                String label=pendingLabel;
                pendingId="";
                pendingLabel="";
                if(ok&&"applied".equals(state)){
                    setAckProgress(3,false);
                    setCommandUi("✓ Arduino применила уставку "+value+" °C",2);
                    if(!label.isEmpty())addCommandHistory(label,"✓ подтверждено");
                }else if(ok&&"stop_sent".equals(state)){
                    setAckProgress(3,true);
                    setCommandUi("✓ HomeSmoke передал STOP контроллеру",2);
                    if(!label.isEmpty())addCommandHistory(label,"✓ передано");
                }else{
                    setAckError(state);
                    String translated=translateState(state);
                    setCommandUi("Не выполнено · "+translated,3);
                    if(!label.isEmpty())addCommandHistory(label,"✕ "+translated);
                }
            });
        }catch(Exception ignored){}
    }

    private void sendSetpoint(){
        String raw=s(setInput);
        if(raw.isEmpty()){toast("Введите температуру");return;}
        double v;
        try{v=Double.parseDouble(raw.replace(',','.'));}catch(Exception e){toast("Неверное значение");return;}
        if(v<0||v>100||Math.abs(v-Math.rint(v))>.000001){toast("Нужно целое число 0…100 °C");return;}
        if(!isTelemetryFresh()){toast("Нет свежей телеметрии от коптильни");return;}
        MqttClient c=mqtt;
        if(c==null||!c.isConnected()){toast("MQTT не подключён");return;}
        try{
            int target=(int)Math.rint(v);
            pendingId=UUID.randomUUID().toString();
            pendingLabel="Уставка "+target+" °C";
            JSONObject o=new JSONObject();
            o.put("v",2);o.put("id",pendingId);o.put("cmd","set_temp");o.put("value",target);o.put("ts",System.currentTimeMillis());
            c.publish(topic(s(commandTopic),"homesmoke/cmd"),o.toString(),false);
            setAckProgress(1,false);
            setCommandUi("Remote отправил команду · ожидается HomeSmoke",1);
        }catch(Exception e){pendingId="";pendingLabel="";setAckError("");toast("Ошибка MQTT: "+safe(e));}
    }

    private void confirmStop(){
        if(!isTelemetryFresh()){toast("Нет свежей телеметрии от коптильни");return;}
        new AlertDialog.Builder(this).setTitle("Удалённый STOP").setMessage("Выключить нагрев на коптильне?").setPositiveButton("STOP",(d,w)->sendStop()).setNegativeButton("Отмена",null).show();
    }

    private void sendStop(){
        MqttClient c=mqtt;
        if(c==null||!c.isConnected()){toast("MQTT не подключён");return;}
        try{
            pendingId=UUID.randomUUID().toString();
            pendingLabel="STOP";
            JSONObject o=new JSONObject();
            o.put("v",2);o.put("id",pendingId);o.put("cmd","stop");o.put("ts",System.currentTimeMillis());
            c.publish(topic(s(commandTopic),"homesmoke/cmd"),o.toString(),false);
            setAckProgress(1,false);
            setCommandUi("Remote отправил STOP · ожидается HomeSmoke",1);
        }catch(Exception e){pendingId="";pendingLabel="";setAckError("");toast("Ошибка MQTT: "+safe(e));}
    }

    private void setAckProgress(int stage,boolean controllerSentOnly){
        if(ackFlow==null)return;
        if(stage<=0){
            ackFlow.setVisibility(View.GONE);
            setAckStage(ackRemote,"Remote",OFF);
            setAckStage(ackHome,"HomeSmoke",OFF);
            setAckStage(ackController,"Arduino",OFF);
            return;
        }
        ackFlow.setVisibility(View.VISIBLE);
        setAckStage(ackRemote,"Remote ✓",GREEN);
        setAckStage(ackHome,stage>=2?"HomeSmoke ✓":"HomeSmoke",stage>=2?GREEN:OFF);
        if(stage>=3){
            if(controllerSentOnly)setAckStage(ackController,"Передано",BLUE);
            else setAckStage(ackController,"Arduino ✓",GREEN);
        }else if(stage==2)setAckStage(ackController,"Arduino …",ORANGE);
        else setAckStage(ackController,"Arduino",OFF);
    }

    private void setAckError(String state){
        if(ackFlow==null)return;
        ackFlow.setVisibility(View.VISIBLE);
        setAckStage(ackRemote,"Remote ✓",GREEN);
        if("controller_ack_timeout".equals(state)){
            setAckStage(ackHome,"HomeSmoke ✓",GREEN);
            setAckStage(ackController,"Arduino ✕",RED);
        }else{
            setAckStage(ackHome,"HomeSmoke ✕",RED);
            setAckStage(ackController,"Arduino",OFF);
        }
    }

    private void setAckStage(TextView view,String text,int color){
        view.setText(text);
        view.setBackground(round(color,10));
        view.setTextColor(Color.WHITE);
    }

    private void updateModeUi(String raw){
        lastModeRaw=raw;
        mode.setText(modeName(raw));
        int color=TEXT;
        if("0".equals(raw))color=ORANGE;
        else if("1".equals(raw))color=GREEN;
        else if("2".equals(raw))color=BLUE;
        else if("3".equals(raw))color=RED;
        mode.setTextColor(isTelemetryFresh()?color:OFF);
    }

    private void updateAutoUi(boolean running,String program,int stage,String status){
        lastAutoRunning=running;
        View pr=(View)autoProgram.getParent(),sr=(View)autoStage.getParent();
        pr.setVisibility(running?View.VISIBLE:View.GONE);
        sr.setVisibility(running?View.VISIBLE:View.GONE);
        if(running){
            autoChip.setText("АКТИВНО");
            autoChip.setBackground(round(isTelemetryFresh()?BLUE:OFF,12));
            autoProgram.setText(program);
            autoStage.setText(stage>0?String.valueOf(stage):"—");
            autoStatus.setText(status==null||status.trim().isEmpty()?"Auto работает":status);
            autoStatus.setTextColor(isTelemetryFresh()?BLUE_DARK:OFF);
        }else{
            autoChip.setText("ВЫКЛ");
            autoChip.setBackground(round(OFF,12));
            autoStatus.setText("Программа не запущена");
            autoStatus.setTextColor(MUTED);
        }
    }

    private void setCommandUi(String txt,int state){
        commandState.setText(txt);
        int bg=INFO_BG,border=BORDER,color=MUTED;
        if(state==1){bg=WARN_BG;border=ORANGE;color=Color.rgb(151,88,0);}
        else if(state==2){bg=SUCCESS_BG;border=GREEN;color=Color.rgb(18,111,58);}
        else if(state==3){bg=ERROR_BG;border=RED;color=Color.rgb(170,30,30);}
        commandState.setTextColor(color);
        commandState.setBackground(roundStroke(bg,10,border,1));
    }

    private void setBrokerUi(boolean connected,String txt){
        int color=connected?GREEN:(connecting?ORANGE:OFF);
        if(txt!=null&&txt.startsWith("MQTT ошибка"))color=RED;
        mqttBadge.setTextColor(Color.WHITE);
        mqttBadge.setBackground(round(color,14));
        mqttDot.setTextColor(color);
        brokerState.setText(connected?"Подключён":(connecting?"Подключение…":(color==RED?"Ошибка":"Отключён")));
        brokerState.setTextColor(connected?GREEN:(color==RED?RED:MUTED));
        brokerDetail.setText(txt==null?"":txt);
        if(disconnectButton!=null){disconnectButton.setEnabled(connected||connecting);disconnectButton.setAlpha((connected||connecting)?1f:.45f);}
        refreshOverallState();
        refreshControlAvailability();
    }

    private void setDeviceUi(boolean online,String txt){
        int color=online?GREEN:ORANGE;
        deviceBadge.setTextColor(Color.WHITE);
        deviceBadge.setBackground(round(color,14));
        deviceDot.setTextColor(color);
        deviceState.setText(online?"Онлайн":(lastTelemetryAt>0?"Данные устарели":"Нет данных"));
        deviceState.setTextColor(online?GREEN:(lastTelemetryAt>0?ORANGE:MUTED));
        deviceDetail.setText(txt==null?"":txt);
        applyTelemetryFreshness(online);
        refreshOverallState();
        refreshControlAvailability();
    }

    private void refreshControlAvailability(){
        if(controlAvailability==null||setButton==null||stopButton==null||setInput==null)return;
        boolean mq=mqtt!=null&&mqtt.isConnected();
        boolean fresh=isTelemetryFresh();
        boolean ready=mq&&fresh;
        String txt;
        int bg,border,color;
        if(ready){txt="Управление доступно";bg=SUCCESS_BG;border=GREEN;color=Color.rgb(18,111,58);}
        else if(!mq){txt="Управление недоступно · MQTT не подключён";bg=INFO_BG;border=BORDER;color=MUTED;}
        else if(lastTelemetryAt<=0){txt="Управление недоступно · нет данных от коптильни";bg=WARN_BG;border=ORANGE;color=Color.rgb(151,88,0);}
        else{txt="Управление недоступно · данные коптильни устарели";bg=WARN_BG;border=ORANGE;color=Color.rgb(151,88,0);}
        controlAvailability.setText(txt);
        controlAvailability.setTextColor(color);
        controlAvailability.setBackground(roundStroke(bg,10,border,1));
        setButton.setEnabled(ready);
        stopButton.setEnabled(ready);
        setInput.setEnabled(ready);
        setButton.setAlpha(ready?1f:.45f);
        stopButton.setAlpha(ready?1f:.45f);
        setInput.setAlpha(ready?1f:.65f);
    }

    private void refreshOverallState(){
        if(systemState==null)return;
        boolean mq=mqtt!=null&&mqtt.isConnected();
        boolean online=isTelemetryFresh();
        if(mq&&online){systemState.setText("ГОТОВО");systemState.setBackground(round(GREEN,12));}
        else if(mq&&lastTelemetryAt>0){systemState.setText("СТАРЫЕ ДАННЫЕ");systemState.setBackground(round(ORANGE,12));}
        else if(mq){systemState.setText("НЕТ ДАННЫХ");systemState.setBackground(round(ORANGE,12));}
        else{systemState.setText("ОФЛАЙН");systemState.setBackground(round(OFF,12));}
    }

    private void applyTelemetryFreshness(boolean fresh){
        int main=fresh?TEXT:OFF;
        int secondary=fresh?BLUE_DARK:OFF;
        camera.setTextColor(main);
        k.setTextColor(main);
        t.setTextColor(main);
        cameraSummary.setTextColor(secondary);
        tempTrend.setTextColor(fresh?MUTED:OFF);
        power.setTextColor(fresh?ORANGE:OFF);
        lastCommand.setTextColor(main);
        autoProgram.setTextColor(main);
        autoStage.setTextColor(main);
        if(heaterProgress!=null&&Build.VERSION.SDK_INT>=21)heaterProgress.setProgressTintList(ColorStateList.valueOf(fresh?ORANGE:OFF));
        if(fresh){
            updateModeUi(lastModeRaw);
            if(lastAutoRunning){autoChip.setBackground(round(BLUE,12));autoStatus.setTextColor(BLUE_DARK);}else{autoChip.setBackground(round(OFF,12));autoStatus.setTextColor(MUTED);}
        }else{
            mode.setTextColor(OFF);
            autoChip.setBackground(round(OFF,12));
            autoStatus.setTextColor(OFF);
        }
    }

    private void updateCameraSummaryAndTrend(){
        boolean fresh=isTelemetryFresh();
        if(lastTelemetryAt<=0){
            cameraSummary.setText("Уставка — °C");
            tempTrend.setText("Нет данных от коптильни");
            return;
        }
        if(!fresh){
            cameraSummary.setText(Double.isNaN(lastSetpointValue)?"Уставка — °C · данные устарели":"Уставка "+oneDecimal(lastSetpointValue)+" °C · данные устарели");
            tempTrend.setText("Последние данные · "+relativeAge(lastTelemetryAt));
            return;
        }

        if(Double.isNaN(lastSetpointValue)){
            cameraSummary.setText("Уставка — °C · Δ —");
        }else if(Double.isNaN(lastCameraValue)){
            cameraSummary.setText("Уставка "+oneDecimal(lastSetpointValue)+" °C · Δ —");
        }else{
            double d=lastSetpointValue-lastCameraValue;
            String delta;
            if(Math.abs(d)<0.05)delta="Δ 0,0 °C";
            else if(d>0)delta="Δ +"+oneDecimal(d)+" °C";
            else delta="Δ −"+oneDecimal(-d)+" °C";
            cameraSummary.setText("Уставка "+oneDecimal(lastSetpointValue)+" °C · "+delta);
        }

        if(tempSamples.size()<2){tempTrend.setText("Тренд накапливается");return;}
        TempSample newest=tempSamples.get(tempSamples.size()-1);
        TempSample base=null;
        for(int i=tempSamples.size()-2;i>=0;i--){
            TempSample x=tempSamples.get(i);
            if(newest.ts-x.ts>=60000L){base=x;if(newest.ts-x.ts>=TREND_WINDOW_MS)break;}
        }
        if(base==null){tempTrend.setText("Тренд накапливается");return;}
        double diff=newest.value-base.value;
        long minutes=Math.max(1,Math.round((newest.ts-base.ts)/60000.0));
        if(Math.abs(diff)<0.15)tempTrend.setText("→ стабильно · "+minutes+" мин");
        else if(diff>0)tempTrend.setText("↗ +"+oneDecimal(diff)+" °C / "+minutes+" мин");
        else tempTrend.setText("↘ −"+oneDecimal(-diff)+" °C / "+minutes+" мин");
    }

    private void addTempSample(double value,long ts){
        if(Double.isNaN(value)||ts<=0)return;
        if(!tempSamples.isEmpty()&&Math.abs(tempSamples.get(tempSamples.size()-1).ts-ts)<1000L)return;
        tempSamples.add(new TempSample(ts,value));
        long cutoff=ts-TREND_WINDOW_MS-60000L;
        while(!tempSamples.isEmpty()&&tempSamples.get(0).ts<cutoff)tempSamples.remove(0);
        while(tempSamples.size()>180)tempSamples.remove(0);
    }

    private void updateLastDataCaption(){
        if(lastUpdate==null)return;
        if(lastTelemetryAt<=0){lastUpdate.setText("Данных ещё нет");return;}
        if(isTelemetryFresh()){
            String time=new SimpleDateFormat("HH:mm:ss",Locale.getDefault()).format(new Date(lastTelemetryAt));
            lastUpdate.setText("Обновлено сейчас · "+time);
        }else{
            String time=new SimpleDateFormat("dd.MM HH:mm",Locale.getDefault()).format(new Date(lastTelemetryAt));
            lastUpdate.setText("Последние данные: "+time+" · "+relativeAge(lastTelemetryAt));
        }
    }

    private boolean isTelemetryFresh(){return isFreshAt(lastTelemetryAt);}
    private boolean isFreshAt(long ts){return ts>0&&Math.abs(System.currentTimeMillis()-ts)<=STALE_MS;}

    private long normalizeTelemetryTs(long ts){
        long now=System.currentTimeMillis();
        if(ts<=0)return now;
        if(ts<100000000000L)ts*=1000L;
        if(ts>now+5L*60L*1000L)return now;
        return ts;
    }

    private String relativeAge(long ts){
        long sec=Math.max(0,(System.currentTimeMillis()-ts)/1000L);
        if(sec<10)return "сейчас";
        if(sec<60)return sec+" сек назад";
        long min=sec/60;
        if(min<60)return min+" мин назад";
        long hours=min/60,mins=min%60;
        if(hours<24)return hours+" ч"+(mins>0?" "+mins+" мин":"")+" назад";
        long days=hours/24,rem=hours%24;
        return days+" д"+(rem>0?" "+rem+" ч":"")+" назад";
    }

    private void loadCommandHistory(){
        commandHistoryItems.clear();
        try{
            JSONArray a=new JSONArray(prefs.getString("command_history","[]"));
            for(int i=0;i<a.length()&&commandHistoryItems.size()<MAX_COMMAND_HISTORY;i++){
                String s=a.optString(i,"");
                if(!s.isEmpty())commandHistoryItems.add(s);
            }
        }catch(Exception ignored){}
        renderCommandHistory();
    }

    private void saveCommandHistory(){
        JSONArray a=new JSONArray();
        for(String s:commandHistoryItems)a.put(s);
        prefs.edit().putString("command_history",a.toString()).apply();
    }

    private void addCommandHistory(String action,String result){
        String stamp=new SimpleDateFormat("HH:mm",Locale.getDefault()).format(new Date());
        commandHistoryItems.add(0,stamp+" · "+action+" · "+result);
        while(commandHistoryItems.size()>MAX_COMMAND_HISTORY)commandHistoryItems.remove(commandHistoryItems.size()-1);
        saveCommandHistory();
        renderCommandHistory();
    }

    private void renderCommandHistory(){
        if(commandHistory==null)return;
        if(commandHistoryItems.isEmpty()){
            commandHistory.setText("Команд Remote ещё не было");
            commandHistory.setTextColor(MUTED);
            if(commandHistoryCard!=null)commandHistoryCard.setVisibility(showTechnicalEnabled?View.VISIBLE:View.GONE);
            return;
        }
        StringBuilder b=new StringBuilder();
        for(int i=0;i<commandHistoryItems.size();i++){if(i>0)b.append('\n');b.append(commandHistoryItems.get(i));}
        commandHistory.setText(b.toString());
        commandHistory.setTextColor(TEXT);
        if(commandHistoryCard!=null)commandHistoryCard.setVisibility(View.VISIBLE);
    }

    private void disconnectInternal(boolean ui){
        MqttClient c=mqtt;mqtt=null;connecting=false;
        if(c!=null)c.close();
        if(ui)setBrokerUi(false,"MQTT отключён");
    }

    private void showMonitor(){
        setPasswordVisible(false);
        setPage(monitorPage);
        title.setText("HomeSmoke Remote");
        subtitle.setText("Удалённое управление");
        back.setVisibility(View.GONE);
        settings.setVisibility(View.VISIBLE);
        applyUiPreferences();
    }

    private void showSettings(){
        setPasswordVisible(false);
        setPage(settingsPage);
        title.setText("Настройки MQTT");
        subtitle.setText("HomeSmoke Remote 2.0.8");
        back.setVisibility(View.VISIBLE);
        settings.setVisibility(View.GONE);
        applyUiPreferences();
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
        p.setPadding(dp(4),0,dp(4),dp(16));
        p.setBackgroundColor(BG);
        return p;
    }

    private LinearLayout card(){
        LinearLayout c=new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(12),dp(10),dp(12),dp(10));
        c.setBackground(roundStroke(CARD,18,BORDER,1));
        if(Build.VERSION.SDK_INT>=21)c.setElevation(dp(1));
        return c;
    }

    private LinearLayout metricCard(String name){
        LinearLayout c=card();
        c.setGravity(Gravity.CENTER_HORIZONTAL);
        c.setPadding(dp(10),dp(8),dp(10),dp(8));
        TextView l=center(name,13,true,MUTED);
        c.addView(l);
        return c;
    }

    private TextView statusRow(LinearLayout parent,String label,String initial,int dotColor,boolean mqttRow){
        LinearLayout row=new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0,dp(5),0,dp(1));
        TextView dot=text("●",12,true,dotColor);
        dot.setPadding(0,0,dp(6),0);
        if(mqttRow)mqttDot=dot;else deviceDot=dot;
        TextView a=text(label,13,true,TEXT);
        a.setPadding(0,0,dp(7),0);
        TextView b=text(initial,13,true,MUTED);
        b.setGravity(Gravity.END);
        b.setSingleLine(true);
        b.setEllipsize(TextUtils.TruncateAt.END);
        row.addView(dot);
        row.addView(a);
        row.addView(b,new LinearLayout.LayoutParams(0,-2,1));
        parent.addView(row);
        return b;
    }

    private TextView smallDetail(String s){
        TextView t=text(s,11,false,MUTED);
        t.setPadding(dp(18),0,0,dp(1));
        t.setMaxLines(2);
        t.setEllipsize(TextUtils.TruncateAt.END);
        return t;
    }

    private TextView statusChip(String s,int color){
        TextView t=center(s,10,true,Color.WHITE);
        t.setSingleLine(true);
        t.setPadding(dp(8),dp(4),dp(8),dp(4));
        t.setBackground(round(color,12));
        return t;
    }

    private TextView info(LinearLayout p,String label,String initial){
        LinearLayout r=new LinearLayout(this);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(0,dp(3),0,dp(3));
        TextView a=text(label,13,false,MUTED),b=text(initial,15,true,TEXT);
        a.setPadding(0,0,dp(7),0);
        b.setGravity(Gravity.END);
        b.setMaxLines(2);
        b.setEllipsize(TextUtils.TruncateAt.END);
        r.addView(a);
        r.addView(b,new LinearLayout.LayoutParams(0,-2,1));
        p.addView(r);
        return b;
    }

    private TextView sectionTitle(String s){return text(s,17,true,TEXT);}
    private TextView label(String s){return text(s,16,true,MUTED);}

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
        t.setMinWidth(dp(42));
        t.setMaxLines(1);
        t.setPadding(dp(6),dp(6),dp(6),dp(6));
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
        b.setText(s);b.setAllCaps(false);b.setTextColor(Color.WHITE);b.setTextSize(15);b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setPadding(dp(7),0,dp(7),0);
        b.setMinWidth(0);b.setMinimumWidth(0);b.setMinHeight(0);b.setMinimumHeight(0);
        if(Build.VERSION.SDK_INT>=21){b.setBackgroundTintList(null);b.setStateListAnimator(null);}
        b.setBackground(round(color,14));
        return b;
    }

    private CheckBox check(String s){
        CheckBox c=new CheckBox(this);
        c.setText(s);c.setTextSize(14);c.setTypeface(Typeface.DEFAULT_BOLD);c.setTextColor(TEXT);
        c.setBackgroundColor(Color.TRANSPARENT);c.setPadding(0,0,0,0);c.setMinHeight(dp(44));
        if(Build.VERSION.SDK_INT>=21){
            int[][] states=new int[][]{new int[]{android.R.attr.state_checked},new int[]{-android.R.attr.state_checked}};
            c.setButtonTintList(new ColorStateList(states,new int[]{BLUE,Color.rgb(151,164,180)}));
            c.setStateListAnimator(null);
        }
        return c;
    }

    private EditText labeledField(LinearLayout parent,String label,String hint,int type){
        TextView l=text(label,12,true,MUTED);
        l.setPadding(0,dp(6),0,dp(4));
        parent.addView(l);
        EditText e=field(hint,type);
        parent.addView(e,new LinearLayout.LayoutParams(-1,dp(46)));
        return e;
    }

    private EditText field(String hint,int type){
        EditText e=new EditText(this);
        e.setHint(hint);e.setHintTextColor(Color.rgb(151,164,180));e.setInputType(type);e.setSingleLine(true);e.setTextSize(15);e.setTextColor(TEXT);
        e.setPadding(dp(11),0,dp(11),0);
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
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(50));p.setMargins(dp(l),dp(t),dp(r),dp(b));return p;
    }

    private LinearLayout.LayoutParams wrapMargin(int l,int t,int r,int b){
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-2,-2);p.setMargins(dp(l),dp(t),dp(r),dp(b));return p;
    }

    private LinearLayout.LayoutParams checkParams(){
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(0,dp(1),0,0);return p;
    }

    private LinearLayout.LayoutParams half(int l,int r){
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-2,1);p.setMargins(dp(l),dp(4),dp(r),dp(4));return p;
    }

    private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}
    private static String s(EditText e){return e.getText().toString().trim();}
    private static String topic(String s,String d){s=s==null?"":s.trim();return s.isEmpty()?d:s;}
    private static String deg(String s){String v=s==null?"":s.trim();return Double.isNaN(parseNumber(v))?"— °C":v+" °C";}
    private static String modeName(String m){if("0".equals(m))return "Ручной";if("1".equals(m))return "PID";if("2".equals(m))return "AUTO";if("3".equals(m))return "STOP";return "—";}
    private static String translateState(String s){if("pid_mode_required".equals(s))return "сначала включите PID режим";if("android_auto_running".equals(s))return "уставкой управляет Auto";if("bluetooth_not_connected".equals(s))return "Bluetooth коптильни отключён";if("controller_ack_timeout".equals(s))return "Arduino не подтвердила уставку";if("stale_command".equals(s))return "команда устарела";return s;}
    private static String safe(Exception e){return e.getMessage()==null?e.getClass().getSimpleName():e.getMessage();}
    private static double parseNumber(String s){try{return Double.parseDouble(s==null?"":s.trim().replace(',','.'));}catch(Exception e){return Double.NaN;}}
    private static String oneDecimal(double v){return String.format(Locale.getDefault(),"%.1f",v);}
    private static String formatPlain(double v){if(Math.abs(v-Math.rint(v))<0.000001)return String.valueOf((long)Math.rint(v));return oneDecimal(v);}
    private static int clamp(int v,int lo,int hi){return Math.max(lo,Math.min(hi,v));}
    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_SHORT).show();}

    private static final class TempSample{
        final long ts;final double value;
        TempSample(long ts,double value){this.ts=ts;this.value=value;}
    }

    @Override public void onBackPressed(){if(back.getVisibility()==View.VISIBLE)showMonitor();else super.onBackPressed();}
}
