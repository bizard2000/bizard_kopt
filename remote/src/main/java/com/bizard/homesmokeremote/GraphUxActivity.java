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
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;

import java.util.List;
import java.util.Locale;

/**
 * Remote UI refinement plus local operational features: sessions, notifications,
 * event journal and navigation to local export. MQTT/Arduino protocol is untouched.
 */
public class GraphUxActivity extends MainActivity {
    private static final int TEXT=Color.rgb(21,31,47);
    private static final int MUTED=Color.rgb(101,116,139);
    private static final int BORDER=Color.rgb(220,225,232);
    private static final int CARD=Color.WHITE;
    private static final int GREEN=Color.rgb(35,151,83);
    private static final int ORANGE=Color.rgb(231,138,7);
    private static final int BLUE=Color.rgb(31,122,210);
    private static final int CAMERA=Color.rgb(9,47,73);
    private static final int SETPOINT=Color.rgb(31,122,210);
    private static final int PROBE_K=Color.rgb(35,151,83);
    private static final int PROBE_T=Color.rgb(126,87,194);
    private static final int HEATER=Color.rgb(231,138,7);
    private static final long LIVE_MS=10000L;
    private static final long HISTORY_MS=24L*60L*60L*1000L;
    private static final String CHANNEL_ID="homesmoke_remote_alerts";
    private static final int REQ_NOTIFY=1401;

    private final Handler uxHandler=new Handler(Looper.getMainLooper());
    private TelemetryHistoryStore uxHistory;
    private OperationalHistoryStore operational;
    private SharedPreferences remotePrefs;
    private ViewGroup host;
    private boolean graphEnhanced=false,notificationCardAdded=false,historyButtonAdded=false;
    private ViewGroup graphPage;
    private TemperatureChartView graphChartView;
    private TextView graphRecordStatus,graphLiveValues,graphEmptyState,graphHeaterScale,graphBaseSummary;
    private View graphPointCard;
    private boolean liveStateKnown=false,lastLiveState=false,setpointStateKnown=false,withinSetpoint=false;
    private double lastObservedTarget=Double.NaN;

    private final Runnable graphUxTick=new Runnable(){
        @Override public void run(){
            refreshOperationalFeatures();
            refreshGraphUx();
            updateVersionLabels();
            uxHandler.postDelayed(this,2500L);
        }
    };

    @Override protected void onCreate(Bundle state){
        super.onCreate(state);
        remotePrefs=getSharedPreferences("homesmoke_remote",MODE_PRIVATE);
        uxHistory=new TelemetryHistoryStore(this);
        operational=new OperationalHistoryStore(this);
        createNotificationChannel();
        installHostWatcher();
        installHistoryButton();
        updateVersionLabels();
        uxHandler.postDelayed(()->ensureNotificationPermission(false),1200L);
        uxHandler.postDelayed(graphUxTick,700L);
    }

    @Override protected void onDestroy(){
        uxHandler.removeCallbacks(graphUxTick);
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
            @Override public void onChildViewAdded(View parent,View child){child.post(()->{enhanceAttachedPage(child);updateVersionLabels();});}
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
        TextView button=makeText("▤",22,true,Color.WHITE);button.setGravity(Gravity.CENTER);button.setContentDescription("Сеансы и журнал");button.setBackgroundColor(Color.TRANSPARENT);button.setOnClickListener(v->startActivity(new Intent(this,HistoryActivity.class)));
        int index=Math.max(0,bar.getChildCount()-1);bar.addView(button,index,new LinearLayout.LayoutParams(dp(34),dp(42)));historyButtonAdded=true;
    }

    private void enhanceAttachedPage(View attached){
        View page=unwrapScroll(attached);if(page==null)return;
        if(containsText(page,"График температуры")&&findCheck(page,"Камера")!=null)enhanceGraphPage(page);
        else if(containsText(page,"MQTT подключение"))enhanceSettingsPage(page);
    }

    private View unwrapScroll(View v){if(v instanceof ScrollView){ScrollView s=(ScrollView)v;return s.getChildCount()>0?s.getChildAt(0):null;}return v;}

    private void enhanceSettingsPage(View page){
        if(notificationCardAdded||!(page instanceof ViewGroup))return;
        TextView interfaceTitle=findText(page,"Интерфейс");
        if(interfaceTitle==null||!(interfaceTitle.getParent() instanceof View)||!(((View)interfaceTitle.getParent()).getParent() instanceof ViewGroup))return;
        View interfaceCard=(View)interfaceTitle.getParent();ViewGroup parent=(ViewGroup)interfaceCard.getParent();
        LinearLayout card=new LinearLayout(this);card.setOrientation(LinearLayout.VERTICAL);card.setPadding(dp(12),dp(10),dp(12),dp(10));card.setBackground(roundStroke(CARD,18,BORDER,1));if(Build.VERSION.SDK_INT>=21)card.setElevation(dp(1));
        card.addView(makeText("Уведомления",18,true,TEXT));
        CheckBox connection=settingCheck("Потеря и восстановление связи","notify_connection",true);
        CheckBox target=settingCheck("Камера достигла уставки","notify_setpoint",true);
        CheckBox sessions=settingCheck("Начало и завершение сеанса","notify_session",false);
        card.addView(connection,checkParams());card.addView(target,checkParams());card.addView(sessions,checkParams());
        TextView hint=makeText("Уведомления формируются локально по уже существующей телеметрии. Для Android 13+ требуется системное разрешение.",11,false,MUTED);hint.setPadding(0,dp(3),0,0);card.addView(hint);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(dp(8),dp(4),dp(8),dp(4));parent.addView(card,parent.indexOfChild(interfaceCard)+1,lp);notificationCardAdded=true;
    }

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
        if(transition.kind==OperationalHistoryStore.Transition.START)postNotification(2203,"HomeSmoke Remote","Начат новый сеанс копчения");
        else postNotification(2203,"HomeSmoke Remote","Сеанс завершён · "+duration(transition.session.durationMs()));
    }

    private void handleConnectionAndSetpoint(TelemetryHistoryStore.Sample last,long now){
        boolean live=last!=null&&Math.abs(now-last.ts)<=LIVE_MS;
        if(!liveStateKnown){liveStateKnown=true;lastLiveState=live;}
        else if(live!=lastLiveState){
            String message=live?"Связь с коптильней восстановлена":"Коптильня перестала передавать свежие данные";
            operational.addEvent(now,"connection",message);
            if(remotePrefs.getBoolean("notify_connection",true))postNotification(2201,"HomeSmoke Remote",message);
            lastLiveState=live;
        }
        if(!live||last==null||Double.isNaN(last.camera)||Double.isNaN(last.setpoint)||last.setpoint<=0){setpointStateKnown=false;withinSetpoint=false;return;}
        double diff=Math.abs(last.camera-last.setpoint);
        if(!setpointStateKnown){setpointStateKnown=true;lastObservedTarget=last.setpoint;withinSetpoint=diff<=1.0;return;}
        if(Double.isNaN(lastObservedTarget)||Math.abs(lastObservedTarget-last.setpoint)>0.1){lastObservedTarget=last.setpoint;withinSetpoint=false;}
        if(diff<=1.0&&!withinSetpoint){
            withinSetpoint=true;String message="Камера достигла уставки "+String.format(Locale.getDefault(),"%.1f",last.setpoint)+" °C";operational.addEvent(now,"info",message);if(remotePrefs.getBoolean("notify_setpoint",true))postNotification(2202,"HomeSmoke Remote",message);
        }else if(diff>2.0)withinSetpoint=false;
    }

    private void recordCommandEvents(){
        if(remotePrefs==null||operational==null)return;
        String raw=remotePrefs.getString("command_history","[]"),first="";
        try{JSONArray a=new JSONArray(raw);if(a.length()>0)first=a.optString(0,"");}catch(Exception ignored){}
        boolean initialized=remotePrefs.getBoolean("ops_command_initialized",false);
        if(!initialized){remotePrefs.edit().putBoolean("ops_command_initialized",true).putString("ops_last_command_seen",first).apply();return;}
        String seen=remotePrefs.getString("ops_last_command_seen","");
        if(!first.isEmpty()&&!first.equals(seen)){operational.addEvent(System.currentTimeMillis(),"command","Команда Remote · "+first);remotePrefs.edit().putString("ops_last_command_seen",first).apply();}
    }

    private void refreshGraphUx(){
        if(!graphEnhanced||uxHistory==null)return;
        long now=System.currentTimeMillis();List<TelemetryHistoryStore.Sample> samples=uxHistory.query(now-HISTORY_MS,now,2);TelemetryHistoryStore.Sample last=samples.isEmpty()?null:samples.get(samples.size()-1);boolean live=last!=null&&Math.abs(now-last.ts)<=LIVE_MS;
        if(graphRecordStatus!=null){if(last==null){graphRecordStatus.setText("Ожидание телеметрии");graphRecordStatus.setTextColor(ORANGE);}else if(live){graphRecordStatus.setText("● Запись данных");graphRecordStatus.setTextColor(GREEN);}else{graphRecordStatus.setText("Нет свежих данных");graphRecordStatus.setTextColor(MUTED);}}
        if(graphLiveValues!=null){if(last==null)graphLiveValues.setVisibility(View.GONE);else{String prefix=live?"Сейчас: ":"Последние: ";graphLiveValues.setText(prefix+"Камера "+value(last.camera,"°")+" · Уставка "+value(last.setpoint,"°")+" · K "+value(last.probeK,"°")+" · T "+value(last.probeT,"°")+" · ТЭН "+value(last.heater,"%"));graphLiveValues.setTextColor(live?TEXT:MUTED);graphLiveValues.setVisibility(View.VISIBLE);}}
        boolean any=last!=null;if(graphChartView!=null){graphChartView.setVisibility(any?View.VISIBLE:View.GONE);ViewGroup.LayoutParams lp=graphChartView.getLayoutParams();if(lp!=null){lp.height=dp(300);graphChartView.setLayoutParams(lp);}}
        if(graphEmptyState!=null)graphEmptyState.setVisibility(any?View.GONE:View.VISIBLE);if(graphHeaterScale!=null)graphHeaterScale.setVisibility(any?View.VISIBLE:View.GONE);if(graphBaseSummary!=null)graphBaseSummary.setVisibility(any?View.VISIBLE:View.GONE);if(graphPointCard!=null)graphPointCard.setVisibility(any?View.VISIBLE:View.GONE);
    }

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
    private String appVersion(){try{String version=getPackageManager().getPackageInfo(getPackageName(),0).versionName;return version==null||version.trim().isEmpty()?"2.0.16":version;}catch(Exception ignored){return "2.0.16";}}

    private boolean containsText(View root,String exact){return findText(root,exact)!=null;}
    private TextView findText(View root,String exact){if(root instanceof TextView&&exact.contentEquals(((TextView)root).getText()))return (TextView)root;if(root instanceof ViewGroup){ViewGroup g=(ViewGroup)root;for(int i=0;i<g.getChildCount();i++){TextView found=findText(g.getChildAt(i),exact);if(found!=null)return found;}}return null;}
    private TextView findTextStarting(View root,String prefix){if(root instanceof TextView){CharSequence cs=((TextView)root).getText();if(cs!=null&&cs.toString().startsWith(prefix))return (TextView)root;}if(root instanceof ViewGroup){ViewGroup g=(ViewGroup)root;for(int i=0;i<g.getChildCount();i++){TextView found=findTextStarting(g.getChildAt(i),prefix);if(found!=null)return found;}}return null;}
    private CheckBox findCheck(View root,String exact){if(root instanceof CheckBox&&exact.contentEquals(((CheckBox)root).getText()))return (CheckBox)root;if(root instanceof ViewGroup){ViewGroup g=(ViewGroup)root;for(int i=0;i<g.getChildCount();i++){CheckBox found=findCheck(g.getChildAt(i),exact);if(found!=null)return found;}}return null;}
    private TemperatureChartView findChart(View root){if(root instanceof TemperatureChartView)return (TemperatureChartView)root;if(root instanceof ViewGroup){ViewGroup g=(ViewGroup)root;for(int i=0;i<g.getChildCount();i++){TemperatureChartView found=findChart(g.getChildAt(i));if(found!=null)return found;}}return null;}
    private TextView makeText(String text,int sp,boolean bold,int color){TextView tv=new TextView(this);tv.setText(text);tv.setTextSize(sp);tv.setTextColor(color);if(bold)tv.setTypeface(Typeface.DEFAULT_BOLD);return tv;}
    private GradientDrawable roundStroke(int color,int radius,int stroke,int width){GradientDrawable g=new GradientDrawable();g.setColor(color);g.setCornerRadius(dp(radius));g.setStroke(dp(width),stroke);return g;}
    private int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}
    private static String value(double v,String suffix){if(Double.isNaN(v)||Double.isInfinite(v))return "—";return String.format(Locale.getDefault(),"%.1f",v)+suffix;}
    private static String duration(long ms){long m=Math.max(0,ms/60000L),h=m/60,r=m%60;return h>0?h+" ч "+r+" мин":m+" мин";}
}
