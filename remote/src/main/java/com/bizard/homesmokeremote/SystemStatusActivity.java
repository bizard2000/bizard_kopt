package com.bizard.homesmokeremote;

import android.Manifest;
import android.app.Activity;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Read-only diagnostics assembled from Remote runtime state and local stores. */
public final class SystemStatusActivity extends Activity {
    private static final int NAVY=Color.rgb(9,47,73),BG=Color.rgb(245,247,250),CARD=Color.WHITE,TEXT=Color.rgb(21,31,47),MUTED=Color.rgb(101,116,139),BORDER=Color.rgb(220,225,232),GREEN=Color.rgb(35,151,83),ORANGE=Color.rgb(231,138,7);
    private static final long LIVE_MS=10000L,HISTORY_MS=24L*60L*60L*1000L;
    private SharedPreferences prefs;
    private TelemetryHistoryStore telemetry;
    private OperationalHistoryStore ops;
    private TextView connectionBody,telemetryBody,historyBody,notificationsBody,remoteBody;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final Runnable tick=new Runnable(){@Override public void run(){refresh();handler.postDelayed(this,1000L);}};

    @Override protected void onCreate(Bundle state){
        super.onCreate(state);
        prefs=getSharedPreferences("homesmoke_remote",MODE_PRIVATE);
        telemetry=new TelemetryHistoryStore(this);
        ops=new OperationalHistoryStore(this);
        View root=buildRoot();setContentView(root);applyInsets(root);refresh();handler.postDelayed(tick,1000L);
    }

    @Override protected void onDestroy(){handler.removeCallbacks(tick);if(telemetry!=null)telemetry.close();if(ops!=null)ops.close();super.onDestroy();}

    private View buildRoot(){
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(BG);
        root.addView(buildBar(),new LinearLayout.LayoutParams(-1,dp(60)));
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setVerticalScrollBarEnabled(false);
        LinearLayout page=new LinearLayout(this);page.setOrientation(LinearLayout.VERTICAL);page.setPadding(dp(4),dp(4),dp(4),dp(18));page.setBackgroundColor(BG);
        LinearLayout intro=card();intro.addView(text("Состояние системы",18,true,TEXT));intro.addView(detail("Только диагностика Remote. Здесь нет новых команд или полей протокола."));page.addView(intro,margin(8,4,8,4));
        connectionBody=addStatusCard(page,"Связь");telemetryBody=addStatusCard(page,"Телеметрия");historyBody=addStatusCard(page,"Локальная история");notificationsBody=addStatusCard(page,"Уведомления");remoteBody=addStatusCard(page,"Remote");
        scroll.addView(page,new ScrollView.LayoutParams(-1,-2));root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));return root;
    }

    private View buildBar(){
        LinearLayout bar=new LinearLayout(this);bar.setGravity(Gravity.CENTER_VERTICAL);bar.setPadding(dp(8),0,dp(10),0);bar.setBackgroundColor(NAVY);
        TextView back=text("‹",34,false,Color.WHITE);back.setGravity(Gravity.CENTER);back.setOnClickListener(v->finish());bar.addView(back,new LinearLayout.LayoutParams(dp(44),dp(46)));
        LinearLayout titles=new LinearLayout(this);titles.setOrientation(LinearLayout.VERTICAL);titles.setGravity(Gravity.CENTER_VERTICAL);titles.addView(text("Состояние системы",18,true,Color.WHITE));titles.addView(text("Диагностика HomeSmoke Remote",11,false,Color.rgb(211,222,232)));bar.addView(titles,new LinearLayout.LayoutParams(0,-1,1));return bar;
    }

    private TextView addStatusCard(LinearLayout page,String title){
        LinearLayout c=card();c.addView(text(title,18,true,TEXT));TextView body=detail("Обновление…");body.setTextColor(TEXT);body.setPadding(0,dp(7),0,0);c.addView(body);page.addView(c,margin(8,4,8,4));return body;
    }

    private void refresh(){
        if(prefs==null||telemetry==null||ops==null)return;
        long now=System.currentTimeMillis(),diagAt=prefs.getLong("diag_updated_at",0L),diagAge=diagAt>0?Math.max(0,now-diagAt):Long.MAX_VALUE;
        boolean diagFresh=diagAge<=5000L,test=prefs.getBoolean("test_mode_active",false)||prefs.getBoolean("diag_test_running",false);
        boolean mqttConnected=diagFresh&&prefs.getBoolean("diag_mqtt_connected",false),mqttConnecting=diagFresh&&prefs.getBoolean("diag_mqtt_connecting",false);
        String mqtt=mqttConnected?"Подключён":(mqttConnecting?"Подключение…":(diagFresh?"Отключён":"Нет актуального снимка"));
        String runtime=diagAt>0?"Снимок Remote · "+age(diagAt,now)+" назад":"Снимок Remote ещё не получен";
        connectionBody.setText("MQTT · "+mqtt+"\nИсточник данных · "+(test?"ТЕСТ · локальная симуляция":"Коптильня / MQTT")+"\n"+runtime);
        connectionBody.setTextColor(mqttConnected?GREEN:(test?ORANGE:(mqttConnecting?ORANGE:MUTED)));

        long latest=telemetry.latestTimestamp();boolean live=latest>0&&Math.abs(now-latest)<=LIVE_MS,latestTest=latest>0&&isTestTimestamp(latest);
        String device=prefs.getString("diag_device_id","—"),mode=prefs.getString("diag_mode_display","—"),auto=prefs.getString("diag_auto_display","—"),heater=prefs.getString("diag_heater_display","—");
        telemetryBody.setText("Последняя точка · "+(latest>0?dateTime(latest)+" · "+age(latest,now)+" назад":"нет данных")+"\nСвежесть · "+(live?"LIVE":"не свежая")+" · "+(latestTest?"тестовая запись":"обычные данные")+"\nУстройство · "+empty(device)+"\nРежим · "+empty(mode)+" · Auto "+empty(auto)+"\nТЭН · "+empty(heater));
        telemetryBody.setTextColor(live?(latestTest?ORANGE:GREEN):MUTED);

        int points=telemetry.countSamples(now-HISTORY_MS,now);List<OperationalHistoryStore.Session> sessions=ops.querySessions(1000);List<OperationalHistoryStore.Event> events=ops.queryEvents(500);OperationalHistoryStore.Session last=sessions.isEmpty()?null:sessions.get(0);
        String lastSession=last==null?"нет":last.title()+" · "+(last.active()?"активен":duration(last.durationMs()));
        historyBody.setText("Точки телеметрии за 24 ч · "+points+"\nСеансов сохранено · "+sessions.size()+"\nСобытий в журнале · "+events.size()+" / 500\nПоследний сеанс · "+lastSession+"\nСырые точки хранятся до 24 ч; сводки сеансов сохраняются отдельно.");historyBody.setTextColor(TEXT);

        boolean nConnection=prefs.getBoolean("notify_connection",true),nSet=prefs.getBoolean("notify_setpoint",true),nSession=prefs.getBoolean("notify_session",false);String permission;
        if(Build.VERSION.SDK_INT>=33)permission=checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)==PackageManager.PERMISSION_GRANTED?"разрешены системой":"нет системного разрешения";else permission="разрешение системы не требуется";
        notificationsBody.setText("Связь · "+onOff(nConnection)+"\nКамера достигла уставки · "+onOff(nSet)+"\nНачало/завершение сеанса · "+onOff(nSession)+"\nAndroid · "+permission);notificationsBody.setTextColor(permission.startsWith("нет")?ORANGE:TEXT);

        String command=prefs.getString("diag_command_display","—"),testName=prefs.getString("test_active_name","");
        remoteBody.setText("Версия · "+versionName()+"\nТестовый режим · "+(test?("активен"+(testName.isEmpty()?"":" · "+testName)):"выключен")+"\nПоследняя команда/статус · "+empty(command)+"\nAndroid · "+Build.VERSION.RELEASE+" (API "+Build.VERSION.SDK_INT+")");remoteBody.setTextColor(test?ORANGE:TEXT);
    }

    private boolean isTestTimestamp(long ts){
        if(prefs.getBoolean("test_mode_active",false)){long start=prefs.getLong("test_active_start",0L);if(start>0&&ts>=start)return true;}
        try{JSONArray a=new JSONArray(prefs.getString("test_intervals","[]"));for(int i=0;i<a.length();i++){JSONObject x=a.optJSONObject(i);if(x==null)continue;long s=x.optLong("start",0L),e=x.optLong("end",0L);if(s>0&&e>=s&&ts>=s&&ts<=e)return true;}}catch(Exception ignored){}
        return false;
    }

    private String versionName(){try{return getPackageManager().getPackageInfo(getPackageName(),0).versionName;}catch(Exception ignored){return "—";}}
    private static String onOff(boolean v){return v?"включено":"выключено";}
    private static String empty(String v){return v==null||v.trim().isEmpty()?"—":v.trim();}
    private static String dateTime(long ts){return new SimpleDateFormat("dd.MM HH:mm:ss",Locale.getDefault()).format(new Date(ts));}
    private static String age(long ts,long now){long sec=Math.max(0,(now-ts)/1000L);if(sec<60)return sec+" сек";long min=sec/60;if(min<60)return min+" мин";long h=min/60;if(h<24)return h+" ч "+(min%60)+" мин";return (h/24)+" д "+(h%24)+" ч";}
    private static String duration(long ms){long m=Math.max(0,ms/60000L),h=m/60,r=m%60;return h>0?h+" ч "+r+" мин":m+" мин";}

    private void applyInsets(View root){
        if(Build.VERSION.SDK_INT<21)return;
        root.setOnApplyWindowInsetsListener((view,i)->{int l,t,r,b;if(Build.VERSION.SDK_INT>=30){android.graphics.Insets x=i.getInsets(WindowInsets.Type.systemBars());l=x.left;t=x.top;r=x.right;b=x.bottom;}else{l=i.getSystemWindowInsetLeft();t=i.getSystemWindowInsetTop();r=i.getSystemWindowInsetRight();b=i.getSystemWindowInsetBottom();}view.setPadding(l,t,r,b);return i;});
        root.requestApplyInsets();getWindow().setStatusBarColor(NAVY);getWindow().setNavigationBarColor(BG);
    }

    private LinearLayout card(){LinearLayout c=new LinearLayout(this);c.setOrientation(LinearLayout.VERTICAL);c.setPadding(dp(12),dp(10),dp(12),dp(10));c.setBackground(roundStroke(CARD,18,BORDER,1));if(Build.VERSION.SDK_INT>=21)c.setElevation(dp(1));return c;}
    private TextView detail(String s){TextView t=text(s,12,false,MUTED);t.setLineSpacing(0,1.08f);return t;}
    private TextView text(String s,int sp,boolean bold,int color){TextView t=new TextView(this);t.setText(s);t.setTextSize(sp);t.setTextColor(color);if(bold)t.setTypeface(Typeface.DEFAULT_BOLD);return t;}
    private GradientDrawable round(int color,int radius){GradientDrawable g=new GradientDrawable();g.setColor(color);g.setCornerRadius(dp(radius));return g;}
    private GradientDrawable roundStroke(int color,int radius,int stroke,int width){GradientDrawable g=round(color,radius);g.setStroke(dp(width),stroke);return g;}
    private LinearLayout.LayoutParams margin(int l,int t,int r,int b){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(dp(l),dp(t),dp(r),dp(b));return p;}
    private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}
}
