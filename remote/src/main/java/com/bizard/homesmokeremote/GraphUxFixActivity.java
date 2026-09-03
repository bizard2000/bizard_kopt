package com.bizard.homesmokeremote;

import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Small compatibility refinement layered over GraphUxActivity after real-device testing.
 * It changes presentation/test orchestration only. MQTT and Arduino protocol are untouched.
 */
public final class GraphUxFixActivity extends GraphUxActivity {
    private static final int TEXT=Color.rgb(21,31,47);
    private static final int BORDER=Color.rgb(220,225,232);
    private static final int GREEN=Color.rgb(35,151,83);
    private static final int ORANGE=Color.rgb(231,138,7);
    private static final int OFF=Color.rgb(116,129,145);
    private static final int FIELD=Color.rgb(250,251,252);
    private static final long SESSION_SPLIT_MS=10L*60L*1000L;
    private static final String[] TEST_SCENARIOS={
            "Полный цикл",
            "Нагрев камеры",
            "Стабилизация PID",
            "Auto-программа",
            "Щуп достигает цели",
            "Потеря и восстановление связи"
    };
    private static final long[] TEST_DURATIONS_MS={178000L,100000L,118000L,118000L,100000L,52000L};

    private final Handler fixHandler=new Handler(Looper.getMainLooper());
    private Spinner scenarioSpinner;
    private Button scenarioButton;
    private boolean autoStopIssued=false;
    private boolean previousTestRunning=false;
    private TelemetryHistoryStore fixHistory;

    private final Runnable fixTick=new Runnable(){
        @Override public void run(){
            installScenarioChooser();
            refreshScenarioChooser();
            syncTestSessionBoundary();
            syncLatestGraphSession();
            fixGraphHeader();
            fixGraphSummary();
            fixTestStatus();
            fixModeChipText();
            enforceSinglePassScenario();
            fixHandler.postDelayed(this,350L);
        }
    };

    @Override protected void onCreate(Bundle state){
        super.onCreate(state);
        fixHistory=new TelemetryHistoryStore(this);
        previousTestRunning=isTestRunning();
        fixHandler.postDelayed(fixTick,250L);
    }

    @Override protected void onDestroy(){
        fixHandler.removeCallbacks(fixTick);
        if(fixHistory!=null)fixHistory.close();
        super.onDestroy();
    }

    private void installScenarioChooser(){
        if(scenarioButton!=null&&scenarioButton.getParent()!=null)return;
        View content=findViewById(android.R.id.content);
        Spinner spinner=findSpinner(content);
        if(spinner==null||!(spinner.getParent() instanceof ViewGroup))return;

        scenarioSpinner=spinner;
        ViewGroup parent=(ViewGroup)spinner.getParent();
        int index=parent.indexOfChild(spinner);
        ViewGroup.LayoutParams original=spinner.getLayoutParams();
        parent.removeView(spinner);

        scenarioButton=new Button(this);
        scenarioButton.setAllCaps(false);
        scenarioButton.setTextSize(15);
        scenarioButton.setTypeface(Typeface.DEFAULT);
        scenarioButton.setTextColor(TEXT);
        scenarioButton.setGravity(Gravity.CENTER_VERTICAL|Gravity.START);
        scenarioButton.setPadding(dp(12),0,dp(12),0);
        scenarioButton.setMinWidth(0);
        scenarioButton.setMinimumWidth(0);
        scenarioButton.setMinHeight(0);
        scenarioButton.setMinimumHeight(0);
        if(Build.VERSION.SDK_INT>=21){scenarioButton.setStateListAnimator(null);scenarioButton.setBackgroundTintList(null);}
        scenarioButton.setBackground(roundStroke(FIELD,13,BORDER,1));
        scenarioButton.setOnClickListener(v->showScenarioDialog());
        updateScenarioButtonText();

        if(original==null)original=new ViewGroup.LayoutParams(-1,dp(46));
        parent.addView(scenarioButton,Math.max(0,index),original);
    }

    private void showScenarioDialog(){
        if(scenarioSpinner==null||isTestRunning())return;
        int selected=Math.max(0,Math.min(TEST_SCENARIOS.length-1,scenarioSpinner.getSelectedItemPosition()));
        new AlertDialog.Builder(this)
                .setTitle("Сценарий теста")
                .setSingleChoiceItems(TEST_SCENARIOS,selected,(dialog,which)->{
                    scenarioSpinner.setSelection(which);
                    updateScenarioButtonText();
                    dialog.dismiss();
                })
                .setNegativeButton("Отмена",null)
                .show();
    }

    private void refreshScenarioChooser(){
        if(scenarioButton==null)return;
        boolean running=isTestRunning();
        scenarioButton.setEnabled(!running);
        scenarioButton.setAlpha(running?.55f:1f);
        updateScenarioButtonText();
    }

    private void updateScenarioButtonText(){
        if(scenarioButton==null)return;
        int selected=scenarioSpinner==null?readGraphInt("testScenarioIndex",0):scenarioSpinner.getSelectedItemPosition();
        selected=Math.max(0,Math.min(TEST_SCENARIOS.length-1,selected));
        scenarioButton.setText(TEST_SCENARIOS[selected]+"   ▾");
    }

    /**
     * A test start is an explicit local session boundary even when two tests are launched
     * less than ten minutes apart. The graph therefore never joins different test runs.
     */
    private void syncTestSessionBoundary(){
        boolean running=isTestRunning();
        if(running&&!previousTestRunning){
            long started=readGraphLong("testStartedAt",System.currentTimeMillis());
            if(fixHistory!=null)fixHistory.markSessionBoundary(started);
            writeMainBoolean("graphSessionMode",true);
            writeMainLong("graphSessionStartAt",started);
            invokeMain("refreshGraph",new Class<?>[0]);
        }
        previousTestRunning=running;
    }

    /** Makes the Session range mean the latest logical session, including after app restart. */
    private void syncLatestGraphSession(){
        if(fixHistory==null||!readMainBoolean("graphVisible",false)||!readMainBoolean("graphSessionMode",false))return;
        long latest=fixHistory.latestTimestamp();
        if(latest<=0)return;
        long start=isTestRunning()?readGraphLong("testStartedAt",latest):fixHistory.latestSessionStart(latest,SESSION_SPLIT_MS);
        long current=readMainLong("graphSessionStartAt",0L);
        if(start>0&&Math.abs(start-current)>1000L){
            writeMainLong("graphSessionStartAt",start);
            invokeMain("refreshGraph",new Class<?>[0]);
        }
    }

    /** Adds session count to multi-session hour ranges without replacing the base summary. */
    private void fixGraphSummary(){
        if(fixHistory==null||!readMainBoolean("graphVisible",false)||readMainBoolean("graphSessionMode",false))return;
        Object raw=readMainObject("graphSummary");
        if(!(raw instanceof TextView))return;
        TextView summary=(TextView)raw;
        String current=String.valueOf(summary.getText());
        if(current.isEmpty()||current.contains("сеанс")||current.contains("данных")&&current.contains("нет"))return;
        long window=readMainLong("graphWindowMs",3L*60L*60L*1000L);
        long now=System.currentTimeMillis();
        int sessions=fixHistory.countSessions(now-window,now,SESSION_SPLIT_MS);
        if(sessions>1)summary.setText(current+" · "+sessions+" "+sessionWord(sessions));
    }

    private static String sessionWord(int n){
        int mod100=n%100,mod10=n%10;
        if(mod100>=11&&mod100<=14)return "сеансов";
        if(mod10==1)return "сеанс";
        if(mod10>=2&&mod10<=4)return "сеанса";
        return "сеансов";
    }

    private void fixGraphHeader(){
        TextView title=findVisibleExact(findViewById(android.R.id.content),"График температуры");
        if(title!=null)title.setText("График");
    }

    private void fixTestStatus(){
        View content=findViewById(android.R.id.content);
        boolean running=isTestRunning();
        TextView status=findVisibleAny(content,"ОФЛАЙН","ГОТОВО","СТАРЫЕ ДАННЫЕ","НЕТ ДАННЫХ","ТЕСТ");
        TextView availability=findVisibleStartsWith(content,"Управление недоступно");
        Object rawDevice=readMainObject("deviceState");
        TextView device=rawDevice instanceof TextView?(TextView)rawDevice:null;
        if(running){
            if(status!=null){status.setText("ТЕСТ");status.setTextColor(Color.WHITE);status.setBackground(round(ORANGE,12));}
            if(device!=null){device.setText("Тестовые данные");device.setTextColor(GREEN);}
            if(availability!=null)availability.setText("Управление недоступно · тестовый режим");
        }else if(status!=null&&"ТЕСТ".contentEquals(status.getText())){
            status.setText("ОФЛАЙН");status.setTextColor(Color.WHITE);status.setBackground(round(OFF,12));
        }
    }

    private void fixModeChipText(){forceModeChipText(findViewById(android.R.id.content));}

    private void forceModeChipText(View root){
        if(root instanceof TextView&&root.getVisibility()==View.VISIBLE){
            String s=String.valueOf(((TextView)root).getText()).trim();
            if("● PID".equals(s)||"● AUTO".equals(s)||"● Ручной".equals(s)||"● STOP".equals(s))((TextView)root).setTextColor(Color.WHITE);
        }
        if(root instanceof ViewGroup){ViewGroup g=(ViewGroup)root;for(int i=0;i<g.getChildCount();i++)forceModeChipText(g.getChildAt(i));}
    }

    private void enforceSinglePassScenario(){
        if(!isTestRunning()){autoStopIssued=false;return;}
        long started=readGraphLong("testStartedAt",0L);
        int scenario=Math.max(0,Math.min(TEST_DURATIONS_MS.length-1,readGraphInt("testScenarioIndex",0)));
        if(started<=0||System.currentTimeMillis()-started<TEST_DURATIONS_MS[scenario]||autoStopIssued)return;
        autoStopIssued=true;
        String name=TEST_SCENARIOS[scenario];
        invokeGraph("stopTestScenario",new Class<?>[]{boolean.class},true);
        Toast.makeText(this,"Тест «"+name+"» завершён",Toast.LENGTH_SHORT).show();
    }

    private boolean isTestRunning(){return readGraphBoolean("testRunning",false);}

    private Spinner findSpinner(View root){
        if(root instanceof Spinner&&root.getVisibility()==View.VISIBLE)return (Spinner)root;
        if(root instanceof ViewGroup){ViewGroup g=(ViewGroup)root;for(int i=0;i<g.getChildCount();i++){Spinner x=findSpinner(g.getChildAt(i));if(x!=null)return x;}}
        return null;
    }

    private TextView findVisibleExact(View root,String exact){
        if(root instanceof TextView&&root.getVisibility()==View.VISIBLE&&exact.contentEquals(((TextView)root).getText()))return (TextView)root;
        if(root instanceof ViewGroup){ViewGroup g=(ViewGroup)root;for(int i=0;i<g.getChildCount();i++){TextView x=findVisibleExact(g.getChildAt(i),exact);if(x!=null)return x;}}
        return null;
    }

    private TextView findVisibleStartsWith(View root,String prefix){
        if(root instanceof TextView&&root.getVisibility()==View.VISIBLE){CharSequence s=((TextView)root).getText();if(s!=null&&s.toString().startsWith(prefix))return (TextView)root;}
        if(root instanceof ViewGroup){ViewGroup g=(ViewGroup)root;for(int i=0;i<g.getChildCount();i++){TextView x=findVisibleStartsWith(g.getChildAt(i),prefix);if(x!=null)return x;}}
        return null;
    }

    private TextView findVisibleAny(View root,String... values){
        if(root instanceof TextView&&root.getVisibility()==View.VISIBLE){String s=String.valueOf(((TextView)root).getText());for(String value:values)if(value.equals(s))return (TextView)root;}
        if(root instanceof ViewGroup){ViewGroup g=(ViewGroup)root;for(int i=0;i<g.getChildCount();i++){TextView x=findVisibleAny(g.getChildAt(i),values);if(x!=null)return x;}}
        return null;
    }

    private void invokeGraph(String name,Class<?>[] types,Object... args){
        try{Method m=GraphUxActivity.class.getDeclaredMethod(name,types);m.setAccessible(true);m.invoke(this,args);}catch(Exception ignored){}
    }

    private void invokeMain(String name,Class<?>[] types,Object... args){
        try{Method m=MainActivity.class.getDeclaredMethod(name,types);m.setAccessible(true);m.invoke(this,args);}catch(Exception ignored){}
    }

    private boolean readGraphBoolean(String name,boolean def){
        try{Field f=GraphUxActivity.class.getDeclaredField(name);f.setAccessible(true);return f.getBoolean(this);}catch(Exception e){return def;}
    }

    private int readGraphInt(String name,int def){
        try{Field f=GraphUxActivity.class.getDeclaredField(name);f.setAccessible(true);return f.getInt(this);}catch(Exception e){return def;}
    }

    private long readGraphLong(String name,long def){
        try{Field f=GraphUxActivity.class.getDeclaredField(name);f.setAccessible(true);return f.getLong(this);}catch(Exception e){return def;}
    }

    private boolean readMainBoolean(String name,boolean def){
        try{Field f=MainActivity.class.getDeclaredField(name);f.setAccessible(true);return f.getBoolean(this);}catch(Exception e){return def;}
    }

    private long readMainLong(String name,long def){
        try{Field f=MainActivity.class.getDeclaredField(name);f.setAccessible(true);return f.getLong(this);}catch(Exception e){return def;}
    }

    private Object readMainObject(String name){
        try{Field f=MainActivity.class.getDeclaredField(name);f.setAccessible(true);return f.get(this);}catch(Exception e){return null;}
    }

    private void writeMainBoolean(String name,boolean value){
        try{Field f=MainActivity.class.getDeclaredField(name);f.setAccessible(true);f.setBoolean(this,value);}catch(Exception ignored){}
    }

    private void writeMainLong(String name,long value){
        try{Field f=MainActivity.class.getDeclaredField(name);f.setAccessible(true);f.setLong(this,value);}catch(Exception ignored){}
    }

    private GradientDrawable round(int color,int radius){GradientDrawable g=new GradientDrawable();g.setColor(color);g.setCornerRadius(dp(radius));return g;}
    private GradientDrawable roundStroke(int color,int radius,int stroke,int width){GradientDrawable g=round(color,radius);g.setStroke(dp(width),stroke);return g;}
    private int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}
}
