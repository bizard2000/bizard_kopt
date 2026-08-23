package com.bizard.homesmokemqtt;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.EOFException;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/** Native rebuild based on the original Homesmoke.aia. */
public class MainActivity extends Activity {
    private static final UUID SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    private static final int REQ_BT = 1001;
    private static final String TERMINATOR = "\\0"; // literal backslash + zero, exactly as Screen1.bky
    private static final int BLUE = Color.rgb(2,94,187);
    private static final int BG = Color.rgb(204,204,204);
    private static final int BT_BG = Color.rgb(181,175,216);
    private static final int GREY = Color.rgb(136,136,136);

    private final StringBuilder btBuffer = new StringBuilder();
    private BluetoothSocket btSocket;
    private Thread btThread;
    private MqttClient mqtt;
    private SharedPreferences prefs;

    private LinearLayout pageHost, monitorPage, configPage, pidPage, mqttPage;
    private LinearLayout probeKBox, probeTBox, rawBox;
    private Button menuButton, backButton, btButton;
    private TextView titleView, btDot, configBtStatus;
    private TextView tempK, tempT, tempCamera, line1, line2, line3, line4, statusLine;
    private TextView curP, curI, curD, curZ;
    private EditText inP, inI, inD, inZ;
    private CheckBox keepOn, showK, showT, showRaw, fileLog;
    private TextView fileLogStatus;
    private EditText broker, port, topic, user, pass;
    private CheckBox tls, retain;
    private TextView mqttStatus;
    private int modeState = 9;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = getSharedPreferences("homesmoke_full", MODE_PRIVATE);
        setContentView(buildRoot());
        loadSettings();
        showMonitor(" Домашняя коптильня");
        if (Build.VERSION.SDK_INT >= 31 && !hasBtPermission())
            requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT}, REQ_BT);
    }

    private View buildRoot() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        root.addView(buildActionBar(), new LinearLayout.LayoutParams(-1, dp(52)));
        pageHost = new LinearLayout(this);
        pageHost.setOrientation(LinearLayout.VERTICAL);
        pageHost.setBackgroundColor(BG);
        root.addView(pageHost, new LinearLayout.LayoutParams(-1,0,1));
        monitorPage = buildMonitor();
        configPage = buildConfig();
        pidPage = buildPid();
        mqttPage = buildMqtt();
        return root;
    }

    private View buildActionBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(8),0,dp(8),0);
        bar.setBackgroundColor(BLUE);

        backButton = smallButton("←");
        backButton.setTextSize(30);
        backButton.setVisibility(View.GONE);
        backButton.setOnClickListener(v -> backToMonitor());
        bar.addView(backButton, new LinearLayout.LayoutParams(dp(42),dp(42)));

        menuButton = smallButton("☰");
        menuButton.setTextSize(28);
        menuButton.setOnClickListener(v -> showMenu());
        bar.addView(menuButton, new LinearLayout.LayoutParams(dp(42),dp(42)));

        titleView = txt(" Домашняя коптильня",21,false);
        titleView.setTextColor(Color.WHITE);
        titleView.setTypeface(Typeface.MONOSPACE);
        titleView.setGravity(Gravity.CENTER_VERTICAL);
        bar.addView(titleView,new LinearLayout.LayoutParams(0,-1,1));

        btDot = txt("○ ",11,true);
        btDot.setTextColor(Color.WHITE);
        bar.addView(btDot);

        btButton = smallButton("BT");
        btButton.setTextSize(12);
        btButton.setTextColor(Color.WHITE);
        setBtButton(false);
        btButton.setOnClickListener(v -> {
            if (isBtConnected()) new AlertDialog.Builder(this).setTitle("Bluetooth")
                    .setMessage("Отключиться от коптильни?")
                    .setPositiveButton("Отключить",(d,w)->closeBluetooth())
                    .setNegativeButton("Отмена",null).show();
            else chooseBluetooth();
        });
        bar.addView(btButton,new LinearLayout.LayoutParams(dp(42),dp(42)));
        return bar;
    }

    private LinearLayout buildMonitor() {
        LinearLayout root = page();
        probeKBox = tempPanel("Температура продукта внутри щуп К", true);
        tempK = (TextView)probeKBox.getChildAt(1);
        root.addView(probeKBox, mw());
        probeTBox = tempPanel("Температура продукта внутри щуп Т", true);
        tempT = (TextView)probeTBox.getChildAt(1);
        root.addView(probeTBox, mw());
        View div = new View(this); div.setBackgroundColor(Color.BLACK);
        root.addView(div,new LinearLayout.LayoutParams(-1,dp(4)));

        LinearLayout cam = tempPanel("Температура в камере", false);
        tempCamera = (TextView)cam.getChildAt(1);
        tempCamera.setPadding(dp(16),dp(8),dp(16),dp(8));
        tempCamera.setBackground(round(Color.WHITE,Color.TRANSPARENT,8));
        tempCamera.setOnClickListener(v -> cameraClick());
        root.addView(cam,mw());

        rawBox = new LinearLayout(this);
        rawBox.setOrientation(LinearLayout.VERTICAL);
        rawBox.setPadding(dp(10),dp(8),dp(10),dp(8));
        rawBox.setBackgroundColor(GREY);
        line1 = mon("Т° к - — С°.   Т° п - — С°.");
        line2 = mon("Мощность ТЭНа - — %.   Реж. работы - —.");
        line3 = mon("kP —   kI —   kD —   zP —");
        statusLine = mon("Статус: —");
        line4 = mon("Данные Arduino: —");
        rawBox.addView(line1); rawBox.addView(line2); rawBox.addView(line3); rawBox.addView(statusLine); rawBox.addView(line4);
        root.addView(rawBox,mw());
        TextView hint = center("Нажмите на температуру камеры для задания температуры/мощности в выбранном режиме",13);
        hint.setPadding(dp(12),dp(12),dp(12),dp(12)); root.addView(hint);
        return root;
    }

    private LinearLayout tempPanel(String label, boolean large) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL); box.setGravity(Gravity.CENTER_HORIZONTAL);
        box.setPadding(0,dp(5),0,dp(5)); box.addView(center(label,20));
        box.addView(center("0000", large ? 64 : 64));
        return box;
    }

    private LinearLayout buildConfig() {
        LinearLayout root = page();
        LinearLayout b = new LinearLayout(this); b.setOrientation(LinearLayout.VERTICAL);
        b.setPadding(dp(10),dp(10),dp(10),dp(10)); b.setBackgroundColor(BT_BG);
        b.addView(txt("Список устройств Bluetooth",16,false));
        Button choose = wide("Выбрать устройство Bluetooth"); choose.setOnClickListener(v -> chooseBluetooth()); b.addView(choose);
        configBtStatus = txt("Bluetooth: не подключен",14,false); b.addView(configBtStatus); root.addView(b,mw());
        keepOn = check(" Окно мониторинга без затухания");
        showK = check(" Щуп Температуры пр. внутри щуп К");
        showT = check(" Щуп Температуры пр. внутри щуп Т");
        showRaw = check(" Строка данных от ардуино");
        fileLog = check(" Запись показаний темп. в файл");
        root.addView(keepOn); root.addView(showK); root.addView(showT); root.addView(showRaw); root.addView(fileLog);
        fileLogStatus = txt("",12,false); fileLogStatus.setPadding(dp(12),dp(4),dp(12),dp(10)); root.addView(fileLogStatus);
        TextView note = txt("Протокол команд восстановлен непосредственно из Homesmoke.aia. Окончание команды — буквальные символы \\0.",12,false);
        note.setPadding(dp(12),dp(12),dp(12),dp(12)); root.addView(note);
        return root;
    }

    private LinearLayout buildPid() {
        LinearLayout root = page();
        TextView h = center("Настройка ПИД регулятора",20); h.setPadding(0,dp(8),0,dp(10)); root.addView(h);
        curP=pidLabel("kP-0000"); inP=pidEdit(); root.addView(pidRow(curP,inP,"p"));
        curI=pidLabel("kI-0000"); inI=pidEdit(); root.addView(pidRow(curI,inI,"i"));
        curD=pidLabel("kD-0000"); inD=pidEdit(); root.addView(pidRow(curD,inD,"d"));
        curZ=pidLabel("zP-0000"); inZ=pidEdit(); root.addView(pidRow(curZ,inZ,"z"));
        TextView note=txt("Каждая зелёная кнопка отправляет только свой коэффициент. Значение умножается на 100, как в исходном проекте.",13,false);
        note.setPadding(dp(12),dp(14),dp(12),dp(14)); root.addView(note);
        return root;
    }

    private View pidRow(TextView current, EditText input, String prefix) {
        LinearLayout row = new LinearLayout(this); row.setGravity(Gravity.CENTER_VERTICAL); row.setPadding(dp(4),dp(4),dp(4),dp(4));
        row.addView(current,new LinearLayout.LayoutParams(dp(145),dp(70)));
        row.addView(input,new LinearLayout.LayoutParams(0,dp(70),1));
        Button ok=button("✓"); ok.setTextSize(34); ok.setTextColor(Color.WHITE); ok.setTypeface(Typeface.DEFAULT_BOLD);
        ok.setBackground(round(Color.rgb(48,177,54),Color.rgb(70,70,70),14)); ok.setOnClickListener(v -> sendPid(prefix,input));
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(dp(72),dp(64)); p.setMargins(dp(12),0,dp(8),0); row.addView(ok,p);
        return row;
    }

    private LinearLayout buildMqtt() {
        LinearLayout root=page(); TextView h=center("MQTT",22); h.setPadding(0,dp(8),0,dp(8)); root.addView(h);
        broker=edit("Broker / IP",InputType.TYPE_CLASS_TEXT); port=edit("Port",InputType.TYPE_CLASS_NUMBER);
        topic=edit("Topic",InputType.TYPE_CLASS_TEXT); user=edit("Логин",InputType.TYPE_CLASS_TEXT);
        pass=edit("Пароль",InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);
        root.addView(broker); root.addView(port); root.addView(topic); root.addView(user); root.addView(pass);
        LinearLayout f=new LinearLayout(this); tls=check(" TLS"); retain=check(" Retain");
        f.addView(tls,new LinearLayout.LayoutParams(0,-2,1)); f.addView(retain,new LinearLayout.LayoutParams(0,-2,1)); root.addView(f);
        mqttStatus=txt("MQTT: отключен",15,true); mqttStatus.setPadding(dp(8),dp(8),dp(8),dp(8)); root.addView(mqttStatus);
        LinearLayout bs=new LinearLayout(this); Button c=wide("Подключить MQTT"), d=wide("Отключить");
        c.setOnClickListener(v -> connectMqtt()); d.setOnClickListener(v -> disconnectMqtt());
        bs.addView(c,new LinearLayout.LayoutParams(0,-2,1)); bs.addView(d,new LinearLayout.LayoutParams(0,-2,1)); root.addView(bs);
        TextView n=txt("Каждый полный Bluetooth-пакет автоматически публикуется в MQTT как JSON.",13,false); n.setPadding(dp(10),dp(12),dp(10),dp(12)); root.addView(n);
        return root;
    }

    private void showMenu() {
        String[] items={"Настройка","Настройка ПИД","Ручной режим","Режим ПИД","Авто режим","MQTT","Выход"};
        new AlertDialog.Builder(this).setTitle("HomeSmoke").setItems(items,(d,w)->{
            switch(w){
                case 0: showPage(configPage,"Настройка"); break;
                case 1: showPage(pidPage,"Настройка ПИД"); break;
                case 2: selectMode(0,"Ручной режим"); break;
                case 3: selectMode(1,"Режим ПИД"); break;
                case 4: selectMode(2,"Авто режим"); break;
                case 5: showPage(mqttPage,"MQTT"); break;
                case 6: finish(); break;
            }
        }).show();
    }

    private void selectMode(int mode,String title) {
        String cmd=mode==0?"a0":mode==1?"a1":"a2";
        if(!sendBt(cmd+TERMINATOR)) return;
        modeState=mode; showMonitor(title); toast(title+" выбран");
    }

    private void cameraClick() {
        if(modeState==9) toast("Температура");
        else if(modeState==2) toast("Выбран авто режим работы (обсушка, обжарка, варка.)");
        else if(modeState==1) valueDialog("Температура от 0 до 100","k");
        else if(modeState==0) valueDialog("Мощность от 0 до 100","v");
    }

    private void valueDialog(String title,String prefix) {
        EditText e=edit("0..100",InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL|InputType.TYPE_NUMBER_FLAG_SIGNED);
        new AlertDialog.Builder(this).setTitle(title).setView(e).setPositiveButton("применить",(d,w)->{
            String raw=e.getText().toString().trim();
            if(!valid0to100(raw)||!isBtConnected()) { toast("неверно введено значение или нет подключения к коптильне"); return; }
            sendBt(prefix+raw+TERMINATOR);
        }).setNegativeButton("отменить",null).show();
    }

    private boolean valid0to100(String s) {
        if(s==null||s.trim().isEmpty()) s="0";
        try { double v=Double.parseDouble(s.trim().replace(',','.')); return v>=0&&v<=100; }
        catch(Exception e){ return false; }
    }

    private void sendPid(String prefix,EditText input) {
        if(!isBtConnected()){ toast("Нет подключения к коптильне"); return; }
        String raw=input.getText().toString().trim(); if(raw.isEmpty()){ toast("Поле пустое"); return; }
        try {
            String scaled=new BigDecimal(raw.replace(',','.')).multiply(new BigDecimal("100")).stripTrailingZeros().toPlainString();
            sendBt(prefix+scaled+TERMINATOR);
        } catch(Exception e){ toast("Неверное значение"); }
    }

    private boolean sendBt(String command) {
        BluetoothSocket s=btSocket;
        if(s==null||!s.isConnected()){ toast("Нет подключения к коптильне"); return false; }
        try { synchronized(this){ s.getOutputStream().write(command.getBytes(StandardCharsets.UTF_8)); s.getOutputStream().flush(); } return true; }
        catch(Exception e){ toast("Ошибка Bluetooth: "+e.getMessage()); closeBluetooth(); return false; }
    }

    private void showPage(LinearLayout page,String title) {
        saveSettings(); pageHost.removeAllViews(); pageHost.addView(scroll(page),new LinearLayout.LayoutParams(-1,-1));
        titleView.setText(title); menuButton.setVisibility(View.GONE); backButton.setVisibility(View.VISIBLE);
    }
    private void showMonitor(String title) {
        applyDisplay(); pageHost.removeAllViews(); pageHost.addView(scroll(monitorPage),new LinearLayout.LayoutParams(-1,-1));
        titleView.setText(title); menuButton.setVisibility(View.VISIBLE); backButton.setVisibility(View.GONE);
    }
    private void backToMonitor(){ saveSettings(); applyDisplay(); showMonitor(" Домашняя коптильня"); }

    private View scroll(View child) {
        if(child.getParent() instanceof ViewGroup) ((ViewGroup)child.getParent()).removeView(child);
        ScrollView s=new ScrollView(this); s.setFillViewport(true); s.addView(child,new ScrollView.LayoutParams(-1,-2)); return s;
    }

    private void loadSettings() {
        keepOn.setChecked(prefs.getBoolean("keep",false)); showK.setChecked(prefs.getBoolean("show_k",true));
        showT.setChecked(prefs.getBoolean("show_t",true)); showRaw.setChecked(prefs.getBoolean("raw",true)); fileLog.setChecked(prefs.getBoolean("log",false));
        broker.setText(prefs.getString("broker","")); port.setText(prefs.getString("port","1883")); topic.setText(prefs.getString("topic","homesmoke/status"));
        user.setText(prefs.getString("user","")); pass.setText(prefs.getString("pass","")); tls.setChecked(prefs.getBoolean("tls",false)); retain.setChecked(prefs.getBoolean("retain",true));
        updateLogStatus(); applyDisplay();
    }
    private void saveSettings() {
        prefs.edit().putBoolean("keep",keepOn.isChecked()).putBoolean("show_k",showK.isChecked()).putBoolean("show_t",showT.isChecked())
                .putBoolean("raw",showRaw.isChecked()).putBoolean("log",fileLog.isChecked()).putString("broker",broker.getText().toString().trim())
                .putString("port",port.getText().toString().trim()).putString("topic",topic.getText().toString().trim()).putString("user",user.getText().toString())
                .putString("pass",pass.getText().toString()).putBoolean("tls",tls.isChecked()).putBoolean("retain",retain.isChecked()).apply();
        updateLogStatus();
    }
    private void applyDisplay() {
        probeKBox.setVisibility(showK.isChecked()?View.VISIBLE:View.GONE); probeTBox.setVisibility(showT.isChecked()?View.VISIBLE:View.GONE);
        rawBox.setVisibility(showRaw.isChecked()?View.VISIBLE:View.GONE);
        if(keepOn.isChecked()) getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON); else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }
    private void updateLogStatus() {
        File dir=getExternalFilesDir(null); File f=new File(dir==null?getFilesDir():dir,"homesmoke_log.csv");
        fileLogStatus.setText(fileLog.isChecked()?"Запись включена: "+f.getAbsolutePath():"Запись выключена");
    }

    private boolean hasBtPermission(){ return Build.VERSION.SDK_INT<31||checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)==PackageManager.PERMISSION_GRANTED; }
    private void chooseBluetooth() {
        if(!hasBtPermission()){ requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT},REQ_BT); return; }
        BluetoothAdapter a=BluetoothAdapter.getDefaultAdapter(); if(a==null){ toast("Bluetooth не поддерживается"); return; } if(!a.isEnabled()){ toast("Включите Bluetooth"); return; }
        Set<BluetoothDevice> bonded=a.getBondedDevices(); if(bonded==null||bonded.isEmpty()){ toast("Нет спаренных Bluetooth-устройств"); return; }
        ArrayList<BluetoothDevice> ds=new ArrayList<>(bonded); String[] names=new String[ds.size()];
        for(int i=0;i<ds.size();i++) names[i]=safeName(ds.get(i))+"\n"+ds.get(i).getAddress();
        new AlertDialog.Builder(this).setTitle("Список устройств Bluetooth").setItems(names,(d,w)->connectBluetooth(ds.get(w))).setNegativeButton("Отмена",null).show();
    }
    private void connectBluetooth(BluetoothDevice device) {
        closeBluetooth(); setBluetoothUi("подключение…",false);
        btThread=new Thread(()->{
            try {
                if(!hasBtPermission()) throw new SecurityException("Нет разрешения Bluetooth");
                BluetoothSocket s=device.createRfcommSocketToServiceRecord(SPP_UUID); btSocket=s; s.connect();
                runOnUiThread(()->{ setBluetoothUi(safeName(device),true); toast("Устройство подключено"); });
                readBluetooth(s.getInputStream());
            } catch(Exception e){ runOnUiThread(()->{ setBluetoothUi("ошибка",false); toast("Ошибка подключения: "+e.getMessage()); }); closeBluetoothInternal(); }
        },"HomeSmoke-Bluetooth"); btThread.start();
    }
    private String safeName(BluetoothDevice d){ try{return d.getName()==null?d.getAddress():d.getName();}catch(SecurityException e){return "устройство";} }
    private void readBluetooth(InputStream in)throws IOException {
        byte[] b=new byte[512];
        while(!Thread.currentThread().isInterrupted()){
            int n=in.read(b); if(n<0) throw new EOFException("соединение закрыто"); if(n==0)continue;
            synchronized(btBuffer){ btBuffer.append(new String(b,0,n,StandardCharsets.UTF_8)); extractFrames(); }
        }
    }
    private void extractFrames(){
        while(true){ int end=btBuffer.indexOf("end"); if(end<0){ if(btBuffer.length()>8192)btBuffer.delete(0,btBuffer.length()-4096); return; }
            String frame=btBuffer.substring(0,end+3); btBuffer.delete(0,end+3); while(btBuffer.length()>0&&(btBuffer.charAt(0)=='\r'||btBuffer.charAt(0)=='\n'))btBuffer.deleteCharAt(0); processFrame(frame); }
    }
    private void processFrame(String frame) {
        String[] a=frame.split("\\|",-1); runOnUiThread(()->updateMonitor(frame,a));
        if(prefs.getBoolean("log",false)) appendLog(frame,a);
        MqttClient m=mqtt; if(m!=null&&m.isConnected()) try{ m.publish(prefs.getString("topic","homesmoke/status"),json(frame,a),prefs.getBoolean("retain",true)); }
        catch(Exception e){ runOnUiThread(()->mqttStatus.setText("MQTT: ошибка публикации — "+e.getMessage())); }
    }
    private void updateMonitor(String raw,String[] a) {
        tempCamera.setText(display(get(a,2))); tempK.setText(display(get(a,3))); tempT.setText(display(get(a,13)));
        line1.setText("Т° к - "+get(a,4)+" С°.   Т° п - "+get(a,5)+" С°.");
        line2.setText("Мощность ТЭНа - "+get(a,6)+" %.   Реж. работы - "+get(a,7)+".");
        line3.setText("kP "+get(a,9)+"   kI "+get(a,10)+"   kD "+get(a,11)+"   zP "+get(a,12)); statusLine.setText("Статус: "+get(a,8)); line4.setText("Данные Arduino: "+raw);
        curP.setText("kP-"+get(a,9)); curI.setText("kI-"+get(a,10)); curD.setText("kD-"+get(a,11)); curZ.setText("zP-"+get(a,12));
    }
    private String display(String s){ return (s==null||s.isEmpty()?"0000":s)+" С°"; }
    private String get(String[] a,int i){ return i>=0&&i<a.length?a[i].trim():""; }

    private void appendLog(String raw,String[] a){
        try{
            File dir=getExternalFilesDir(null), f=new File(dir==null?getFilesDir():dir,"homesmoke_log.csv"); boolean fresh=!f.exists()||f.length()==0;
            try(FileOutputStream o=new FileOutputStream(f,true)){
                if(fresh)o.write("timestamp;temp_ds;temp_tip_k;temp_k;temp_p;heater_power;mode;status;kP;kI;kD;zP;temp_tip_t;raw\n".getBytes(StandardCharsets.UTF_8));
                String ts=new SimpleDateFormat("yyyy-MM-dd HH:mm:ss",Locale.US).format(new Date());
                String x=ts+";"+get(a,2)+";"+get(a,3)+";"+get(a,4)+";"+get(a,5)+";"+get(a,6)+";"+get(a,7)+";"+get(a,8)+";"+get(a,9)+";"+get(a,10)+";"+get(a,11)+";"+get(a,12)+";"+get(a,13)+";\""+raw.replace("\"","\"\"")+"\"\n";
                o.write(x.getBytes(StandardCharsets.UTF_8));
            }
        }catch(Exception ignored){}
    }
    private String json(String raw,String[] a){ return "{\"ts\":"+System.currentTimeMillis()+",\"raw\":\""+esc(raw)+"\",\"temp_ds\":\""+esc(get(a,2))+"\",\"temp_tip_k\":\""+esc(get(a,3))+"\",\"temp_k\":\""+esc(get(a,4))+"\",\"temp_p\":\""+esc(get(a,5))+"\",\"heater_power\":\""+esc(get(a,6))+"\",\"mode\":\""+esc(get(a,7))+"\",\"status\":\""+esc(get(a,8))+"\",\"kP\":\""+esc(get(a,9))+"\",\"kI\":\""+esc(get(a,10))+"\",\"kD\":\""+esc(get(a,11))+"\",\"zP\":\""+esc(get(a,12))+"\",\"temp_tip_t\":\""+esc(get(a,13))+"\"}"; }
    private String esc(String s){ return s==null?"":s.replace("\\","\\\\").replace("\"","\\\"").replace("\r","\\r").replace("\n","\\n"); }

    private void connectMqtt(){
        saveSettings(); String host=broker.getText().toString().trim(); if(host.isEmpty()){toast("Укажите MQTT broker");return;} int p;
        try{p=Integer.parseInt(port.getText().toString().trim());}catch(Exception e){toast("Неверный MQTT port");return;}
        disconnectMqtt(); mqttStatus.setText("MQTT: подключение…"); final MqttClient c=new MqttClient(host,p,tls.isChecked(),user.getText().toString(),pass.getText().toString()); mqtt=c;
        new Thread(()->{try{c.connect();runOnUiThread(()->mqttStatus.setText("MQTT: подключен к "+host+":"+p));}catch(Exception e){c.close();if(mqtt==c)mqtt=null;runOnUiThread(()->mqttStatus.setText("MQTT: ошибка — "+e.getMessage()));}},"HomeSmoke-MQTT-connect").start();
    }
    private void disconnectMqtt(){ MqttClient m=mqtt; mqtt=null; if(m!=null)m.close(); if(mqttStatus!=null)mqttStatus.setText("MQTT: отключен"); }

    private boolean isBtConnected(){ BluetoothSocket s=btSocket; return s!=null&&s.isConnected(); }
    private void setBluetoothUi(String s,boolean connected){ btDot.setText(connected?"● ":"○ "); if(configBtStatus!=null)configBtStatus.setText("Bluetooth: "+(connected?"подключен — ":"")+s); setBtButton(connected); }
    private void setBtButton(boolean connected){ if(btButton!=null)btButton.setBackground(round(connected?Color.rgb(0,140,70):Color.rgb(145,40,40),Color.WHITE,18)); }
    private void closeBluetooth(){ closeBluetoothInternal(); runOnUiThread(()->setBluetoothUi("не подключен",false)); }
    private void closeBluetoothInternal(){ BluetoothSocket s=btSocket; btSocket=null; if(s!=null)try{s.close();}catch(Exception ignored){} Thread t=btThread;btThread=null;if(t!=null&&t!=Thread.currentThread())t.interrupt(); }

    @Override public void onBackPressed(){ if(backButton!=null&&backButton.getVisibility()==View.VISIBLE)backToMonitor();else super.onBackPressed(); }
    @Override protected void onDestroy(){ saveSettings();closeBluetoothInternal();disconnectMqtt();super.onDestroy(); }

    private LinearLayout page(){ LinearLayout v=new LinearLayout(this);v.setOrientation(LinearLayout.VERTICAL);v.setBackgroundColor(BG);v.setPadding(dp(4),dp(4),dp(4),dp(20));return v; }
    private TextView txt(String s,int sp,boolean bold){TextView t=new TextView(this);t.setText(s);t.setTextSize(sp);t.setTextColor(Color.BLACK);if(bold)t.setTypeface(Typeface.DEFAULT_BOLD);return t;}
    private TextView center(String s,int sp){TextView t=txt(s,sp,false);t.setGravity(Gravity.CENTER);return t;}
    private TextView mon(String s){TextView t=txt(s,14,false);t.setTextColor(Color.WHITE);t.setPadding(dp(4),dp(2),dp(4),dp(2));return t;}
    private Button button(String s){Button b=new Button(this);b.setText(s);b.setAllCaps(false);return b;}
    private Button wide(String s){Button b=button(s);b.setTextSize(16);b.setMinHeight(dp(48));return b;}
    private Button smallButton(String s){Button b=button(s);b.setPadding(0,0,0,0);b.setMinWidth(dp(42));b.setMinHeight(dp(42));return b;}
    private CheckBox check(String s){CheckBox c=new CheckBox(this);c.setText(s);c.setTextSize(18);c.setTextColor(Color.BLACK);c.setPadding(dp(6),dp(5),dp(6),dp(5));return c;}
    private EditText edit(String hint,int type){EditText e=new EditText(this);e.setHint(hint);e.setTextSize(18);e.setInputType(type);e.setSingleLine(true);e.setBackgroundColor(Color.WHITE);e.setPadding(dp(10),dp(8),dp(10),dp(8));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(54));p.setMargins(dp(8),dp(4),dp(8),dp(4));e.setLayoutParams(p);return e;}
    private TextView pidLabel(String s){TextView t=txt(s,25,false);t.setTextColor(Color.rgb(136,136,136));return t;}
    private EditText pidEdit(){EditText e=new EditText(this);e.setHint("Hint for Text");e.setTextSize(25);e.setSingleLine(true);e.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL|InputType.TYPE_NUMBER_FLAG_SIGNED);e.setBackgroundColor(Color.WHITE);e.setPadding(dp(10),dp(4),dp(10),dp(4));return e;}
    private GradientDrawable round(int fill,int stroke,int r){GradientDrawable g=new GradientDrawable();g.setColor(fill);g.setCornerRadius(dp(r));if(Color.alpha(stroke)!=0)g.setStroke(dp(2),stroke);return g;}
    private LinearLayout.LayoutParams mw(){return new LinearLayout.LayoutParams(-1,-2);}
    private int dp(int v){return (int)(v*getResources().getDisplayMetrics().density+0.5f);}
    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
}
