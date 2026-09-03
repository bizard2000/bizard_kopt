package com.bizard.homesmokeremote;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Locale;

/**
 * Remote UI refinement plus local operational features: sessions, notifications,
 * event journal, export and a strictly local telemetry simulator.
 * MQTT/Arduino protocol is untouched.
 */
public class GraphUxActivity extends MainActivity {
    private static final int TEXT=Color.rgb(21,31,47);
    private static final int MUTED=Color.rgb(101,116,139);
    private static final int BORDER=Color.rgb(220,225,232);
    private static final int CARD=Color.WHITE;
    private static final int GREEN=Color.rgb(35,151,83);
    private static final int ORANGE=Color.rgb(231,138,7);
    private static final int BLUE=Color.rgb(31,122,210);
    private static final int RED=Color.rgb(229,40,40);
    private static final int OFF=Color.rgb(116,129,145);
    private static final int WARN_BG=Color.rgb(255,247,232);
    private static final int CAMERA=Color.rgb(9,47,73);
    private static final int SETPOINT=Color.rgb(31,122,210);
    private static final int PROBE_K=Color.rgb(35,151,83);
    private static final int PROBE_T=Color.rgb(126,87,194);
    private static final int HEATER=Color.rgb(231,138,7);
    private static final long LIVE_MS=10000L;
    private static final long HISTORY_MS=24L*60L*60L*1000L;
    private static final long SESSION_SPLIT_MS=10L*60L*1000L;
    private static final String CHANNEL_ID="homesmoke_remote_alerts";
    private static final int REQ_NOTIFY=1401;
    private static final String[] TEST_SCENARIOS={
            "Полный цикл",
            "Нагрев камеры",
            "Стабилизация PID",
            "Auto-программа",
            "Щуп достигает цели",
            "Потеря и восстановление связи"
    };

    private TelemetryHistoryStore uxHistory;
    private OperationalHistoryStore operational;
    private SharedPreferences remotePrefs;
    private ViewGroup host;
    private boolean graphEnhanced=false,notificationCardAdded=false,testCardAdded=false,historyButtonAdded=false;
    private ViewGroup graphPage;
    private TemperatureChartView graphChartView;
    private TextView graphRecordStatus,graphLiveValues,graphEmptyState,graphHeaterScale,graphBaseSummary;
    private View graphPointCard;
    private boolean liveStateKnown=false,lastLiveState=false,setpointStateKnown=false,withinSetpoint=false;
    private double lastObservedTarget=Double.NaN;

    private Spinner testScenarioSpinner;
    private Button testStartButton,testStopButton;
    private TextView testStatus,testBannerTitle,testBannerDetail;
    private View testBanner;
    private boolean testRunning=false,previousWantConnection=false;
    private int testScenarioIndex=0;
    private long testStartedAt=0L;

    private final Handler graphUxHandler=new Handler(Looper.getMainLooper());

    private final Runnable graphUxTick=new Runnable(){
        @Override public void run(){
            refreshOperationalFeatures();
            refreshGraphUx();
            refreshTestUi();
            updateVersionLabels();
            graphUxHandler.postDelayed(this,2500L);
        }
    };

    private final Runnable simulationTick=new Runnable(){
        @Override public void run(){
            if(!testRunning)return;
            long now=System.currentTimeMillis();
            double elapsed=(now-testStartedAt)/1000.0;
            if(shouldEmitTestFrame(testScenarioIndex,elapsed))injectTestFrame(buildTestFrame(testScenarioIndex,elapsed),now);
            refreshTestUi();
            graphUxHandler.postDelayed(this,2000L);
        }
    };

    @Override protected void onCreate(Bundle state){
        super.onCreate(state);
        remotePrefs=getSharedPreferences("homesmoke_remote",MODE_PRIVATE);
        uxHistory=new TelemetryHistoryStore(this);
        operational=new OperationalHistoryStore(this);
        recoverInterruptedTestInterval();
        createNotificationChannel();
        installHostWatcher();
        installHistoryButton();
        updateVersionLabels();
        graphUxHandler.postDelayed(()->ensureNotificationPermission(false),1200L);
        graphUxHandler.postDelayed(graphUxTick,700L);
    }

    @Override protected void onDestroy(){
        if(testRunning)stopTestScenario(false);
        graphUxHandler.removeCallbacks(graphUxTick);
        graphUxHandler.removeCallbacks(simulationTick);
        if(uxHistory!=null)uxHistory.close();
        if(operational!=null)operational.close();
        super.onDestroy();
    }

    private void installHostWatcher(){
        View content=findViewById(android.R.id.content);
        if(!(content instanceof ViewGroup))return;
        ViewGroup contentGroup=(ViewGroup)content;
        if(contentGroup.getChildCount()==0)return;
        View root=contentGroup.getChildAt(0);
        if(!(root instanceof ViewGroup))return;
        ViewGroup rootGroup=(ViewGroup)root;
        if(rootGroup.getChildCount()<2)return;
        View candidate=rootGroup.getChildAt(1);
        if(!(candidate instanceof ViewGroup))return;
        host=(ViewGroup)candidate;
        host.setOnHierarchyChangeListener(new ViewGroup.OnHierarchyChangeListener(){
            @Override public void onChildViewAdded(View parent,View child){child.post(()->{enhanceAttachedPage(child);updateVersionLabels();refreshTestUi();});}
            @Override public void onChildViewRemoved(View parent,View child){}
        });
        if(host.getChildCount()>0)enhanceAttachedPage(host.getChildAt(0));
    }

    private void installHistoryButton(){
        if(historyButtonAdded)return;
        View content=findViewById(android.R.id.content);
        if(!(content instanceof ViewGroup))return;
        ViewGroup cg=(ViewGroup)content;if(cg.getChildCount()==0||!(cg.getChildAt(0) instanceof ViewGroup))return;
        ViewGroup root=(ViewGroup)cg.getChildAt(0);if(root.getChildCount()==0||!(root.getChildAt(0) instanceof LinearLayout))return;
        LinearLayout bar=(LinearLayout)root.getChildAt(0);
        TextView button=makeText("▤",20,true,Color.WHITE);
        button.setGravity(Gravity.CENTER);
        button.setPadding(0,0,0,0);
        button.setContentDescription("Сеансы и журнал");
        button.setBackgroundColor(Color.TRANSPARENT);
        button.setOnClickListener(v->startActivity(new Intent(this,HistoryActivity.class)));
        int index=Math.max(0,bar.getChildCount()-1);
        bar.addView(button,index,new LinearLayout.LayoutParams(dp(36),dp(42)));
        normalizeNavigationIcons(bar);
        historyButtonAdded=true;
    }

    private void normalizeNavigationIcons(LinearLayout bar){
        if(bar==null)return;
        int first=Math.max(0,bar.getChildCount()-3);
        for(int i=first;i<bar.getChildCount();i++){
            View child=bar.getChildAt(i);
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(dp(36),dp(42));
            lp.setMargins(0,0,0,0);
            child.setLayoutParams(lp);
            child.setPadding(0,0,0,0);
            if(child instanceof TextView)((TextView)child).setGravity(Gravity.CENTER);
        }
    }

    private void enhanceAttachedPage(View attached){
        View page=unwrapScroll(attached);if(page==null)return;
        if(containsText(page,"График температуры")&&findCheck(page,"Камера")!=null)enhanceGraphPage(page);
        else if(containsText(page,"MQTT подключение"))enhanceSettingsPage(page);
        else if(containsText(page,"Связь")&&containsText(page,"Удалённое управление"))enhanceMonitorPage(page);
    }

    private View unwrapScroll(View v){if(v instanceof ScrollView){ScrollView s=(ScrollView)v;return s.getChildCount()>0?s.getChildAt(0):null;}return v;}

    private void enhanceMonitorPage(View pageView){
        if(testBanner!=null||!(pageView instanceof LinearLayout))return;
        LinearLayout page=(LinearLayout)pageView;
        LinearLayout card=new LinearLayout(this);card.setOrientation(LinearLayout.VERTICAL);card.setPadding(dp(12),dp(8),dp(12),dp(8));card.setBackground(roundStroke(WARN_BG,16,ORANGE,1));
        testBannerTitle=makeText("ТЕСТОВЫЕ ДАННЫЕ",14,true,ORANGE);card.addView(testBannerTitle);
        testBannerDetail=makeText("Локальная симуляция · MQTT-команды заблокированы",11,false,MUTED);testBannerDetail.setPadding(0,dp(2),0,0);card.addView(testBannerDetail);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(dp(8),dp(8),dp(8),dp(4));
        page.addView(card,0,lp);testBanner=card;testBanner.setVisibility(testRunning?View.VISIBLE:View.GONE);
    }

    private void enhanceSettingsPage(View page){
        if(!(page instanceof ViewGroup))return;
        TextView interfaceTitle=findText(page,"Интерфейс");
        if(interfaceTitle==null||!(interfaceTitle.getParent() instanceof View)||!(((View)interfaceTitle.getParent()).getParent() instanceof ViewGroup))return;
        View interfaceCard=(View)interfaceTitle.getParent();ViewGroup parent=(ViewGroup)interfaceCard.getParent();

        if(!notificationCardAdded){
            LinearLayout card=new LinearLayout(this);card.setOrientation(LinearLayout.VERTICAL);card.setPadding(dp(12),dp(10),dp(12),dp(10));card.setBackground(roundStroke(CARD,18,BORDER,1));if(Build.VERSION.SDK_INT>=21)card.setElevation(dp(1));
            card.addView(makeText("Уведомления",18,true,TEXT));
            CheckBox connection=settingCheck("Потеря и восстановление связи","notify_connection",true);
            CheckBox target=settingCheck("Камера достигла уставки","notify_setpoint",true);
            CheckBox sessions=settingCheck("Начало и завершение сеанса","notify_session",false);
            card.addView(connection,checkParams());card.addView(target,checkParams());card.addView(sessions,checkParams());
            TextView hint=makeText("Уведомления формируются локально по уже существующей телеметрии. Для Android 13+ требуется системное разрешение.",11,false,MUTED);hint.setPadding(0,dp(3),0,0);card.addView(hint);
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(dp(8),dp(4),dp(8),dp(4));parent.addView(card,parent.indexOfChild(interfaceCard)+1,lp);notificationCardAdded=true;
        }
        if(!testCardAdded){
            View notificationCard=null;TextView nt=findText(page,"Уведомления");if(nt!=null&&nt.getParent() instanceof View)notificationCard=(View)nt.getParent();
            int insert=notificationCard!=null?parent.indexOfChild(notificationCard)+1:parent.indexOfChild(interfaceCard)+1;
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(dp(8),dp(4),dp(8),dp(4));parent.addView(buildTestCard(),Math.max(0,insert),lp);testCardAdded=true;
        }
        refreshTestUi();
    }

    private View buildTestCard(){
        LinearLayout card=new LinearLayout(this);card.setOrientation(LinearLayout.VERTICAL);card.setPadding(dp(12),dp(10),dp(12),dp(10));card.setBackground(roundStroke(CARD,18,BORDER,1));if(Build.VERSION.SDK_INT>=21)card.setElevation(dp(1));
        card.addView(makeText("Тестовый режим",18,true,TEXT));
        TextView warning=makeText("Только локальная симуляция. При запуске MQTT отключается, команды HomeSmoke/Arduino не публикуются.",11,true,ORANGE);warning.setPadding(0,dp(4),0,dp(6));warning.setLineSpacing(0,1.05f);card.addView(warning);
        TextView scenarioLabel=makeText("Сценарий",12,true,MUTED);scenarioLabel.setPadding(0,dp(2),0,dp(3));card.addView(scenarioLabel);
        testScenarioSpinner=new Spinner(this);
        ArrayAdapter<String> adapter=new ArrayAdapter<>(this,android.R.layout.simple_spinner_item,TEST_SCENARIOS);adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);testScenarioSpinner.setAdapter(adapter);
        testScenarioIndex=Math.max(0,Math.min(TEST_SCENARIOS.length-1,remotePrefs.getInt("test_scenario",0)));testScenarioSpinner.setSelection(testScenarioIndex);
        testScenarioSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){@Override public void onItemSelected(AdapterView<?> parent,View view,int position,long id){testScenarioIndex=position;remotePrefs.edit().putInt("test_scenario",position).apply();}@Override public void onNothingSelected(AdapterView<?> parent){}});
        card.addView(testScenarioSpinner,new LinearLayout.LayoutParams(-1,dp(46)));
        LinearLayout buttons=new LinearLayout(this);buttons.setGravity(Gravity.CENTER_VERTICAL);
        testStartButton=testButton("Запустить тест",BLUE);testStopButton=testButton("Остановить",OFF);
        testStartButton.setOnClickListener(v->startTestScenario());testStopButton.setOnClickListener(v->stopTestScenario(true));
        LinearLayout.LayoutParams left=new LinearLayout.LayoutParams(0,dp(46),1);left.setMargins(0,dp(7),dp(4),0);LinearLayout.LayoutParams right=new LinearLayout.LayoutParams(0,dp(46),1);right.setMargins(dp(4),dp(7),0,0);buttons.addView(testStartButton,left);buttons.addView(testStopButton,right);card.addView(buttons);
        testStatus=makeText("Тестовый режим выключен",11,false,MUTED);testStatus.setPadding(0,dp(6),0,0);card.addView(testStatus);
        TextView hint=makeText("Симуляция проверяет главный экран, график, сеансы, журнал и локальные уведомления. Тестовые сеансы помечаются в истории.",11,false,MUTED);hint.setPadding(0,dp(5),0,0);hint.setLineSpacing(0,1.05f);card.addView(hint);
        return card;
    }

    private Button testButton(String text,int color){Button b=new Button(this);b.setText(text);b.setTextSize(12);b.setTextColor(Color.WHITE);b.setTypeface(Typeface.DEFAULT_BOLD);b.setAllCaps(false);b.setGravity(Gravity.CENTER);b.setPadding(dp(5),0,dp(5),0);b.setBackground(roundStroke(color,13,color,1));if(Build.VERSION.SDK_INT>=21)b.setStateListAnimator(null);return b;}

    private CheckBox settingCheck(String label,String key,boolean def){
        CheckBox c=new CheckBox(this);c.setText(label);c.setTextSize(13);c.setTextColor(TEXT);c.setTypeface(Typeface.DEFAULT_BOLD);c.setGravity(Gravity.CENTER_VERTICAL);c.setChecked(remotePrefs.getBoolean(key,def));
        c.setOnCheckedChangeListener((b,checked)->{remotePrefs.edit().putBoolean(key,checked).apply();if(checked)ensureNotificationPermission(true);});return c;
    }

    private LinearLayout.LayoutParams checkParams(){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(42));p.setMargins(0,dp(2),0,0);return p;}

    private void enhanceGraphPage(View pageView){
        if(graphEnhanced||!(pageView instanceof ViewGroup))return;graphPage=(ViewGroup)pageView;
        TextView duplicateTitle=findText(graphPage,"График температуры");
        if(duplicateTitle!=null&&duplicateTitle.getParent() instanceof View){View intro=(View)duplicateTitle.getParent();if(intro.getParent() instanceof ViewGroup){ViewGroup parent=(ViewGroup)intro.getParent();int index=parent.indexOfChild(intro);parent.removeView(intro);parent.addView(buildRecordCard(),Math.max(0,index));}}
        graphChartView=findChart(graphPage);
        if(graphChartView!=null&&graphChartView.getParent() instanceof ViewGroup){
            ViewGroup chartCard=(ViewGroup)graphChartView.getParent();int chartIndex=chartCard.indexOfChild(graphChartView);
            graphLiveValues=makeText("",11,true,TEXT);graphLiveValues.setLineSpacing(0,1.04f);graphLiveValues.setPadding(0,0,0,dp(5));graphLiveValues.setVisibility(View.GONE);chartCard.addView(graphLiveValues,Math.max(0,chartIndex));
            graphEmptyState=makeText("История пока пуста\nГрафик появится автоматически после получения свежих данных.",13,false,MUTED);graphEmptyState.setGravity(Gravity.CENTER);graphEmptyState.setLineSpacing(0,1.10f);graphEmptyState.setPadding(dp(12),dp(14),dp(12),dp(14));chartCard.addView(graphEmptyState,Math.max(0,chartIndex+1));
            graphHeaterScale=makeText("",11,true,MUTED);graphHeaterScale.setGravity(Gravity.CENTER);graphHeaterScale.setPadding(0,dp(4),0,0);setHeaterScaleText();chartCard.addView(graphHeaterScale);
            graphBaseSummary=findTextStarting(chartCard,"Свежих данных");if(graphBaseSummary==null)graphBaseSummary=findTextStarting(chartCard,"В текущем сеансе");
        }
        decorateSeriesCheck(findCheck(graphPage,"Камера"),CAMERA,"Камера");decorateSeriesCheck(findCheck(graphPage,"Уставка"),SETPOINT,"Уставка");decorateSeriesCheck(findCheck(graphPage,"Щуп K"),PROBE_K,"Щуп K");decorateSeriesCheck(findCheck(graphPage,"Щуп T"),PROBE_T,"Щуп T");
        TextView heaterHint=findTextStarting(graphPage,"Мощность ТЭНа отображается");if(heaterHint!=null)heaterHint.setText("Цвет = линия графика · ТЭН — отдельная шкала 0–100 %");
        TextView pointTitle=findText(graphPage,"Точка графика");if(pointTitle!=null&&pointTitle.getParent() instanceof View)graphPointCard=(View)pointTitle.getParent();
        graphEnhanced=true;refreshGraphUx();
    }

    private View buildRecordCard(){
        LinearLayout card=new LinearLayout(this);card.setOrientation(LinearLayout.VERTICAL);card.setPadding(dp(12),dp(8),dp(12),dp(8));card.setBackground(roundStroke(CARD,18,BORDER,1));if(Build.VERSION.SDK_INT>=21)card.setElevation(dp(1));
        graphRecordStatus=makeText("Ожидание телеметрии",14,true,ORANGE);graphRecordStatus.setPadding(0,0,0,dp(1));card.addView(graphRecordStatus);
        card.addView(makeText("Локальная история · до 24 ч · старые данные брокера не сохраняются",11,false,MUTED));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(dp(8),dp(8),dp(8),dp(4));card.setLayoutParams(lp);return card;
    }

    private void startTestScenario(){
        if(testRunning)return;
        long now=System.currentTimeMillis();
        long latest=uxHistory==null?0L:uxHistory.latestTimestamp();
        if(latest>0&&now-latest<SESSION_SPLIT_MS&&!isTestTimestamp(latest)){
            toast("Есть недавняя реальная телеметрия. Тестовый режим сейчас не запускается, чтобы не смешивать данные.");
            return;
        }
        testScenarioIndex=testScenarioSpinner==null?testScenarioIndex:testScenarioSpinner.getSelectedItemPosition();
        previousWantConnection=readMainBoolean("wantConnection",false);
        writeMainBoolean("wantConnection",false);
        invokeMain("disconnectInternal",new Class<?>[]{boolean.class},true);
        testRunning=true;testStartedAt=now;
        liveStateKnown=false;setpointStateKnown=false;withinSetpoint=false;lastObservedTarget=Double.NaN;
        remotePrefs.edit().putBoolean("test_mode_active",true).putLong("test_active_start",now).putString("test_active_name",TEST_SCENARIOS[testScenarioIndex]).putInt("test_scenario",testScenarioIndex).apply();
        if(operational!=null)operational.addEvent(now,"test","ТЕСТ · сценарий «"+TEST_SCENARIOS[testScenarioIndex]+"» запущен");
        refreshTestUi();
        graphUxHandler.removeCallbacks(simulationTick);graphUxHandler.post(simulationTick);
    }

    private void stopTestScenario(boolean reconnect){
        if(!testRunning)return;
        graphUxHandler.removeCallbacks(simulationTick);
        long now=System.currentTimeMillis();
        refreshOperationalFeatures();
        if(operational!=null){
            List<OperationalHistoryStore.Session> sessions=operational.querySessions(1);
            if(!sessions.isEmpty()){OperationalHistoryStore.Session s=sessions.get(0);if(s.active()&&s.startTs>=testStartedAt-1000L){OperationalHistoryStore.Transition tr=operational.closeIfInactive(now+SESSION_SPLIT_MS+1000L);if(tr!=null)handleSessionTransition(tr);}}
        }
        String name=TEST_SCENARIOS[testScenarioIndex];
        testRunning=false;
        recordTestInterval(testStartedAt,now,name);
        remotePrefs.edit().putBoolean("test_mode_active",false).remove("test_active_start").remove("test_active_name").apply();
        if(operational!=null)operational.addEvent(now,"test","ТЕСТ · сценарий «"+name+"» завершён");
        writeMainLong("lastTelemetryAt",0L);
        invokeMain("applyTelemetryFreshness",new Class<?>[]{boolean.class},false);
        invokeMain("updateLastDataCaption",new Class<?>[0]);
        refreshTestUi();refreshGraphUx();
        if(reconnect&&previousWantConnection){writeMainBoolean("wantConnection",true);graphUxHandler.postDelayed(()->invokeMain("connectMqtt",new Class<?>[]{boolean.class},false),600L);}
    }

    private boolean shouldEmitTestFrame(int scenario,double elapsed){
        if(scenario!=5)return true;
        double phase=elapsed%55.0;
        return phase<20.0||phase>=38.0;
    }

    private TestFrame buildTestFrame(int scenario,double sec){
        if(scenario==1){
            double p=Math.min(1.0,(sec%110.0)/90.0);double cam=25.0+45.0*p;return new TestFrame(cam,70.0,25.0+31.0*p,24.0+25.0*p,Math.max(18.0,100.0-72.0*p),"1",false,"—",0,"PID нагрев");
        }
        if(scenario==2){
            double decay=Math.exp(-(sec%120.0)/24.0);double cam=65.0-4.2*decay*Math.cos(sec/5.0);double heater=28.0+18.0*Math.max(0.0,Math.sin(sec/7.0));return new TestFrame(cam,65.0,54.0+0.4*Math.sin(sec/9.0),52.5+0.3*Math.sin(sec/11.0),heater,"1",false,"—",0,"PID стабилизация");
        }
        if(scenario==3){
            double phase=sec%120.0;int stage=phase<40?1:(phase<80?2:3);double set=stage==1?45.0:(stage==2?60.0:70.0);double cam=set-2.0+1.2*Math.sin(sec/8.0);double pk=stage==1?34.0:(stage==2?48.0:61.0);return new TestFrame(cam,set,pk,pk-2.0,32.0+18.0*Math.max(0,Math.sin(sec/6.0)),"2",true,"Тестовая Auto",stage,"Тест · этап "+stage);
        }
        if(scenario==4){
            double p=Math.min(1.0,(sec%110.0)/85.0);double pk=35.0+33.0*p;return new TestFrame(69.3+0.5*Math.sin(sec/8.0),70.0,pk,pk-3.0,24.0+8.0*Math.sin(sec/10.0),"1",false,"—",0,pk>=67.5?"Щуп у цели":"Нагрев продукта");
        }
        if(scenario==5){
            return new TestFrame(60.0+0.3*Math.sin(sec/6.0),60.0,50.0+0.2*Math.sin(sec/8.0),48.0+0.2*Math.sin(sec/9.0),28.0,"1",false,"—",0,"Тест связи");
        }
        double phase=sec%180.0;
        if(phase<60.0){double p=phase/60.0;return new TestFrame(25.0+35.0*p,60.0,25.0+21.0*p,24.0+18.0*p,96.0-45.0*p,"1",false,"—",0,"Разогрев");}
        if(phase<120.0){double x=phase-60.0;return new TestFrame(60.0+0.7*Math.sin(x/7.0),60.0,46.0+0.18*x,43.0+0.15*x,30.0+12.0*Math.max(0,Math.sin(x/6.0)),"1",false,"—",0,"Стабилизация");}
        double x=phase-120.0;int stage=x<30?1:2;double set=stage==1?65.0:70.0;return new TestFrame(set-2.0+0.8*Math.sin(x/7.0),set,57.0+0.16*x,54.0+0.14*x,36.0,"2",true,"Демо-копчение",stage,"Тест · Auto этап "+stage);
    }

    private void injectTestFrame(TestFrame f,long ts){
        try{
            JSONObject o=new JSONObject();
            o.put("temp_ds",round1(f.camera));o.put("temp_tip_k",round1(f.probeK));o.put("temp_tip_t",round1(f.probeT));o.put("temp_k",round1(f.setpoint));o.put("heater_power",round1(f.heater));o.put("mode",f.mode);o.put("last_command","TEST_LOCAL");
            o.put("android_auto_program",f.program);o.put("android_auto_status",f.autoStatus);o.put("android_auto_stage",f.stage);o.put("android_auto_running",f.autoRunning);o.put("device_id","TEST");o.put("ts",ts);
            invokeMain("status",new Class<?>[]{String.class},o.toString());
        }catch(Exception e){if(operational!=null)operational.addEvent(System.currentTimeMillis(),"test","ТЕСТ · ошибка симуляции: "+e.getClass().getSimpleName());}
    }

    private static double round1(double v){return Math.round(v*10.0)/10.0;}

    private String testPhaseText(){
        if(!testRunning)return "Тестовый режим выключен";
        long sec=Math.max(0,(System.currentTimeMillis()-testStartedAt)/1000L);
        if(testScenarioIndex==5){long phase=sec%55L;if(phase>=20&&phase<38)return "ТЕСТ · потеря телеметрии · "+(38-phase)+" сек до восстановления";}
        return "ТЕСТ · "+TEST_SCENARIOS[testScenarioIndex]+" · "+sec+" сек";
    }

    private void refreshTestUi(){
        if(testStatus!=null){testStatus.setText(testPhaseText());testStatus.setTextColor(testRunning?ORANGE:MUTED);}
        if(testStartButton!=null){testStartButton.setEnabled(!testRunning);testStartButton.setAlpha(testRunning?.45f:1f);}
        if(testStopButton!=null){testStopButton.setEnabled(testRunning);testStopButton.setAlpha(testRunning?1f:.45f);testStopButton.setBackground(roundStroke(testRunning?RED:OFF,13,testRunning?RED:OFF,1));}
        if(testScenarioSpinner!=null)testScenarioSpinner.setEnabled(!testRunning);
        if(testBanner!=null)testBanner.setVisibility(testRunning?View.VISIBLE:View.GONE);
        if(testBannerTitle!=null&&testRunning)testBannerTitle.setText("ТЕСТОВЫЕ ДАННЫЕ · "+TEST_SCENARIOS[testScenarioIndex]);
        if(testBannerDetail!=null&&testRunning)testBannerDetail.setText("Локальная симуляция · MQTT отключён · команды не отправляются");
        if(testRunning){TextView availability=findTextStarting(findViewById(android.R.id.content),"Управление недоступно");if(availability!=null)availability.setText("Управление недоступно · тестовый режим");}
    }

    private void refreshOperationalFeatures(){
        if(uxHistory==null||operational==null)return;
        long now=System.currentTimeMillis(),processed=operational.getLastProcessedTs();
        List<TelemetryHistoryStore.Sample> pending=uxHistory.query(Math.max(now-HISTORY_MS,processed+1L),now,0);
        List<OperationalHistoryStore.Transition> transitions=operational.processSamples(pending);
        OperationalHistoryStore.Transition closed=operational.closeIfInactive(now);
        for(OperationalHistoryStore.Transition t:transitions)handleSessionTransition(t);
        if(closed!=null)handleSessionTransition(closed);
        List<TelemetryHistoryStore.Sample> lastList=uxHistory.query(now-HISTORY_MS,now,2);
        TelemetryHistoryStore.Sample last=lastList.isEmpty()?null:lastList.get(lastList.size()-1);
        handleConnectionAndSetpoint(last,now);
        recordCommandEvents();
    }

    private void handleSessionTransition(OperationalHistoryStore.Transition transition){
        if(transition==null||transition.session==null)return;
        if(!remotePrefs.getBoolean("notify_session",false))return;
        boolean test=testRunning||isTestSession(transition.session.startTs,transition.session.effectiveEnd());
        String prefix=test?"ТЕСТ · ":"";
        if(transition.kind==OperationalHistoryStore.Transition.START)postNotification(2203,"HomeSmoke Remote",prefix+"Начат новый сеанс копчения");
        else postNotification(2203,"HomeSmoke Remote",prefix+"Сеанс завершён · "+duration(transition.session.durationMs()));
    }

    private void handleConnectionAndSetpoint(TelemetryHistoryStore.Sample last,long now){
        boolean testSource=last!=null&&isTestTimestamp(last.ts);
        if(testSource&&!testRunning){liveStateKnown=false;setpointStateKnown=false;withinSetpoint=false;return;}
        boolean live=last!=null&&Math.abs(now-last.ts)<=LIVE_MS;
        if(!liveStateKnown){liveStateKnown=true;lastLiveState=live;}
        else if(live!=lastLiveState){
            String message=live?"Связь с коптильней восстановлена":"Коптильня перестала передавать свежие данные";
            if(testRunning)message="ТЕСТ · "+message;
            operational.addEvent(now,testRunning?"test":"connection",message);
            if(remotePrefs.getBoolean("notify_connection",true))postNotification(2201,"HomeSmoke Remote",message);
            lastLiveState=live;
        }
        if(!live||last==null||Double.isNaN(last.camera)||Double.isNaN(last.setpoint)||last.setpoint<=0){setpointStateKnown=false;withinSetpoint=false;return;}
        double diff=Math.abs(last.camera-last.setpoint);
        if(!setpointStateKnown){setpointStateKnown=true;lastObservedTarget=last.setpoint;withinSetpoint=diff<=1.0;return;}
        if(Double.isNaN(lastObservedTarget)||Math.abs(lastObservedTarget-last.setpoint)>0.1){lastObservedTarget=last.setpoint;withinSetpoint=false;}
        if(diff<=1.0&&!withinSetpoint){
            withinSetpoint=true;String message=(testRunning?"ТЕСТ · ":"")+"Камера достигла уставки "+String.format(Locale.getDefault(),"%.1f",last.setpoint)+" °C";operational.addEvent(now,testRunning?"test":"info",message);if(remotePrefs.getBoolean("notify_setpoint",true))postNotification(2202,"HomeSmoke Remote",message);
        }else if(diff>2.0)withinSetpoint=false;
    }

    private void recordCommandEvents(){
        if(remotePrefs==null||operational==null||testRunning)return;
        String raw=remotePrefs.getString("command_history","[]"),first="";
        try{JSONArray a=new JSONArray(raw);if(a.length()>0)first=a.optString(0,"");}catch(Exception ignored){}
        boolean initialized=remotePrefs.getBoolean("ops_command_initialized",false);
        if(!initialized){remotePrefs.edit().putBoolean("ops_command_initialized",true).putString("ops_last_command_seen",first).apply();return;}
        String seen=remotePrefs.getString("ops_last_command_seen","");
        if(!first.isEmpty()&&!first.equals(seen)){operational.addEvent(System.currentTimeMillis(),"command","Команда Remote · "+first);remotePrefs.edit().putString("ops_last_command_seen",first).apply();}
    }

    private void refreshGraphUx(){
        if(!graphEnhanced||uxHistory==null)return;
        long now=System.currentTimeMillis();List<TelemetryHistoryStore.Sample> samples=uxHistory.query(now-HISTORY_MS,now,2);TelemetryHistoryStore.Sample last=samples.isEmpty()?null:samples.get(samples.size()-1);boolean live=last!=null&&Math.abs(now-last.ts)<=LIVE_MS;boolean test=last!=null&&isTestTimestamp(last.ts);
        if(graphRecordStatus!=null){if(testRunning){graphRecordStatus.setText("● ТЕСТОВАЯ ЗАПИСЬ");graphRecordStatus.setTextColor(ORANGE);}else if(last==null){graphRecordStatus.setText("Ожидание телеметрии");graphRecordStatus.setTextColor(ORANGE);}else if(test){graphRecordStatus.setText("Тестовая запись завершена");graphRecordStatus.setTextColor(MUTED);}else if(live){graphRecordStatus.setText("● Запись данных");graphRecordStatus.setTextColor(GREEN);}else{graphRecordStatus.setText("Нет свежих данных");graphRecordStatus.setTextColor(MUTED);}}
        if(graphLiveValues!=null){if(last==null)graphLiveValues.setVisibility(View.GONE);else{String prefix=test?(live?"Тест: ":"Последний тест: "):(live?"Сейчас: ":"Последние: ");graphLiveValues.setText(prefix+"Камера "+value(last.camera,"°")+" · Уставка "+value(last.setpoint,"°")+" · K "+value(last.probeK,"°")+" · T "+value(last.probeT,"°")+" · ТЭН "+value(last.heater,"%"));graphLiveValues.setTextColor(test?ORANGE:(live?TEXT:MUTED));graphLiveValues.setVisibility(View.VISIBLE);}}
        boolean any=last!=null;if(graphChartView!=null){graphChartView.setVisibility(any?View.VISIBLE:View.GONE);ViewGroup.LayoutParams lp=graphChartView.getLayoutParams();if(lp!=null){lp.height=dp(300);graphChartView.setLayoutParams(lp);}}
        if(graphEmptyState!=null)graphEmptyState.setVisibility(any?View.GONE:View.VISIBLE);if(graphHeaterScale!=null)graphHeaterScale.setVisibility(any?View.VISIBLE:View.GONE);if(graphBaseSummary!=null)graphBaseSummary.setVisibility(any?View.VISIBLE:View.GONE);if(graphPointCard!=null)graphPointCard.setVisibility(any?View.VISIBLE:View.GONE);
    }

    private void recordTestInterval(long start,long end,String name){
        if(start<=0||end<start)return;
        try{
            JSONArray old=new JSONArray(remotePrefs.getString("test_intervals","[]"));JSONArray out=new JSONArray();int from=Math.max(0,old.length()-19);for(int i=from;i<old.length();i++)out.put(old.get(i));JSONObject x=new JSONObject();x.put("start",start);x.put("end",end);x.put("name",name==null?"Тест":name);out.put(x);remotePrefs.edit().putString("test_intervals",out.toString()).apply();
        }catch(Exception ignored){}
    }

    private void recoverInterruptedTestInterval(){
        if(!remotePrefs.getBoolean("test_mode_active",false))return;
        long start=remotePrefs.getLong("test_active_start",0L);String name=remotePrefs.getString("test_active_name","Тест");if(start>0)recordTestInterval(start,System.currentTimeMillis(),name+" · прерван");remotePrefs.edit().putBoolean("test_mode_active",false).remove("test_active_start").remove("test_active_name").apply();
    }

    private boolean isTestTimestamp(long ts){
        if(ts<=0)return false;if(testRunning&&ts>=testStartedAt)return true;
        try{JSONArray a=new JSONArray(remotePrefs.getString("test_intervals","[]"));for(int i=0;i<a.length();i++){JSONObject x=a.optJSONObject(i);if(x==null)continue;long s=x.optLong("start",0),e=x.optLong("end",0);if(s>0&&e>=s&&ts>=s&&ts<=e)return true;}}catch(Exception ignored){}
        return false;
    }

    private boolean isTestSession(long start,long end){
        if(testRunning&&end>=testStartedAt&&start<=System.currentTimeMillis())return true;
        try{JSONArray a=new JSONArray(remotePrefs.getString("test_intervals","[]"));for(int i=0;i<a.length();i++){JSONObject x=a.optJSONObject(i);if(x==null)continue;long s=x.optLong("start",0),e=x.optLong("end",0);if(s>0&&e>=s&&start<=e&&end>=s)return true;}}catch(Exception ignored){}
        return false;
    }

    private void invokeMain(String name,Class<?>[] types,Object... args){try{Method m=MainActivity.class.getDeclaredMethod(name,types);m.setAccessible(true);m.invoke(this,args);}catch(Exception e){if(operational!=null&&testRunning)operational.addEvent(System.currentTimeMillis(),"test","ТЕСТ · внутренняя ошибка "+name);}}
    private boolean readMainBoolean(String name,boolean def){try{Field f=MainActivity.class.getDeclaredField(name);f.setAccessible(true);return f.getBoolean(this);}catch(Exception e){return def;}}
    private void writeMainBoolean(String name,boolean value){try{Field f=MainActivity.class.getDeclaredField(name);f.setAccessible(true);f.setBoolean(this,value);}catch(Exception ignored){}}
    private void writeMainLong(String name,long value){try{Field f=MainActivity.class.getDeclaredField(name);f.setAccessible(true);f.setLong(this,value);}catch(Exception ignored){}}

    private void createNotificationChannel(){
        if(Build.VERSION.SDK_INT<26)return;NotificationManager nm=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);if(nm==null)return;NotificationChannel ch=new NotificationChannel(CHANNEL_ID,"HomeSmoke Remote",NotificationManager.IMPORTANCE_DEFAULT);ch.setDescription("Состояние коптильни и температурные события");nm.createNotificationChannel(ch);
    }

    private void ensureNotificationPermission(boolean userAction){
        if(Build.VERSION.SDK_INT<33)return;
        if(checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)==PackageManager.PERMISSION_GRANTED)return;
        boolean asked=remotePrefs.getBoolean("notify_permission_asked",false);
        if(userAction||!asked){remotePrefs.edit().putBoolean("notify_permission_asked",true).apply();requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},REQ_NOTIFY);}
    }

    private void postNotification(int id,String title,String body){
        if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)return;
        NotificationManager nm=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);if(nm==null)return;
        Intent open=new Intent(this,GraphUxActivity.class);open.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);int flags=Build.VERSION.SDK_INT>=23?PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE:PendingIntent.FLAG_UPDATE_CURRENT;PendingIntent pi=PendingIntent.getActivity(this,id,open,flags);
        Notification.Builder b=Build.VERSION.SDK_INT>=26?new Notification.Builder(this,CHANNEL_ID):new Notification.Builder(this);b.setSmallIcon(android.R.drawable.stat_notify_more).setContentTitle(title).setContentText(body).setAutoCancel(true).setContentIntent(pi);if(Build.VERSION.SDK_INT<26)b.setPriority(Notification.PRIORITY_DEFAULT);nm.notify(id,b.build());
    }

    private void setHeaterScaleText(){if(graphHeaterScale==null)return;String text="━  ТЭН, %   ·   0 — 50 — 100";SpannableString span=new SpannableString(text);span.setSpan(new ForegroundColorSpan(HEATER),0,1,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);graphHeaterScale.setText(span);}
    private void decorateSeriesCheck(CheckBox box,int color,String label){if(box==null)return;SpannableString span=new SpannableString("━  "+label);span.setSpan(new ForegroundColorSpan(color),0,1,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);box.setText(span);}

    private void updateVersionLabels(){View content=findViewById(android.R.id.content);if(content!=null)replaceVersionText(content);}
    private void replaceVersionText(View root){
        if(root instanceof TextView){TextView tv=(TextView)root;CharSequence cs=tv.getText();if(cs!=null){String s=cs.toString();if(s.startsWith("HomeSmoke Remote ")&&s.matches("HomeSmoke Remote \\d+\\.\\d+\\.\\d+.*")){String replacement=s.replaceFirst("HomeSmoke Remote \\d+\\.\\d+\\.\\d+","HomeSmoke Remote "+appVersion());if(!replacement.equals(s))tv.setText(replacement);}}}
        if(root instanceof ViewGroup){ViewGroup g=(ViewGroup)root;for(int i=0;i<g.getChildCount();i++)replaceVersionText(g.getChildAt(i));}
    }
    private String appVersion(){try{String version=getPackageManager().getPackageInfo(getPackageName(),0).versionName;return version==null||version.trim().isEmpty()?"2.0.18":version;}catch(Exception ignored){return "2.0.18";}}

    private boolean containsText(View root,String exact){return findText(root,exact)!=null;}
    private TextView findText(View root,String exact){if(root instanceof TextView&&exact.contentEquals(((TextView)root).getText()))return (TextView)root;if(root instanceof ViewGroup){ViewGroup g=(ViewGroup)root;for(int i=0;i<g.getChildCount();i++){TextView found=findText(g.getChildAt(i),exact);if(found!=null)return found;}}return null;}
    private TextView findTextStarting(View root,String prefix){if(root instanceof TextView){CharSequence cs=((TextView)root).getText();if(cs!=null&&cs.toString().startsWith(prefix))return (TextView)root;}if(root instanceof ViewGroup){ViewGroup g=(ViewGroup)root;for(int i=0;i<g.getChildCount();i++){TextView found=findTextStarting(g.getChildAt(i),prefix);if(found!=null)return found;}}return null;}
    private CheckBox findCheck(View root,String exact){if(root instanceof CheckBox&&exact.contentEquals(((CheckBox)root).getText()))return (CheckBox)root;if(root instanceof ViewGroup){ViewGroup g=(ViewGroup)root;for(int i=0;i<g.getChildCount();i++){CheckBox found=findCheck(g.getChildAt(i),exact);if(found!=null)return found;}}return null;}
    private TemperatureChartView findChart(View root){if(root instanceof TemperatureChartView)return (TemperatureChartView)root;if(root instanceof ViewGroup){ViewGroup g=(ViewGroup)root;for(int i=0;i<g.getChildCount();i++){TemperatureChartView found=findChart(g.getChildAt(i));if(found!=null)return found;}}return null;}
    private TextView makeText(String text,int sp,boolean bold,int color){TextView tv=new TextView(this);tv.setText(text);tv.setTextSize(sp);tv.setTextColor(color);if(bold)tv.setTypeface(Typeface.DEFAULT_BOLD);return tv;}
    private GradientDrawable roundStroke(int color,int radius,int stroke,int width){GradientDrawable g=new GradientDrawable();g.setColor(color);g.setCornerRadius(dp(radius));g.setStroke(dp(width),stroke);return g;}
    private int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}
    private void toast(String text){Toast.makeText(this,text,Toast.LENGTH_LONG).show();}
    private static String value(double v,String suffix){if(Double.isNaN(v)||Double.isInfinite(v))return "—";return String.format(Locale.getDefault(),"%.1f",v)+suffix;}
    private static String duration(long ms){long m=Math.max(0,ms/60000L),h=m/60,r=m%60;return h>0?h+" ч "+r+" мин":m+" мин";}

    private static final class TestFrame{
        final double camera,setpoint,probeK,probeT,heater;final String mode;final boolean autoRunning;final String program;final int stage;final String autoStatus;
        TestFrame(double camera,double setpoint,double probeK,double probeT,double heater,String mode,boolean autoRunning,String program,int stage,String autoStatus){this.camera=camera;this.setpoint=setpoint;this.probeK=probeK;this.probeT=probeT;this.heater=heater;this.mode=mode;this.autoRunning=autoRunning;this.program=program;this.stage=stage;this.autoStatus=autoStatus;}
    }
}
