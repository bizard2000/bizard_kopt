package com.bizard.homesmokeremote;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Local sessions, operational journal, filters and per-session export. */
public final class HistoryActivity extends Activity {
    private static final int NAVY=Color.rgb(9,47,73),BG=Color.rgb(245,247,250),CARD=Color.WHITE,TEXT=Color.rgb(21,31,47),MUTED=Color.rgb(101,116,139),BORDER=Color.rgb(220,225,232),GREEN=Color.rgb(35,151,83),BLUE=Color.rgb(31,122,210),ORANGE=Color.rgb(231,138,7),OFF=Color.rgb(116,129,145),FIELD=Color.rgb(239,243,247);
    private static final int REQ_CSV=4101,REQ_JSON=4102;
    private static final String[] FILTERS={"Все","Связь","Команды","Auto","Температура","Сеансы","TEST"};
    private TelemetryHistoryStore telemetry;
    private OperationalHistoryStore ops;
    private SharedPreferences prefs;
    private OperationalHistoryStore.Session exportSession;
    private String eventFilter="Все";
    private LinearLayout filterBar,journalRows;

    @Override protected void onCreate(Bundle state){
        super.onCreate(state);
        prefs=getSharedPreferences("homesmoke_remote",MODE_PRIVATE);
        telemetry=new TelemetryHistoryStore(this);
        ops=new OperationalHistoryStore(this);
        synchronize();
        View root=buildRoot();
        setContentView(root);
        applyInsets(root);
    }

    @Override protected void onResume(){super.onResume();if(ops!=null){synchronize();renderJournal();}}
    @Override protected void onDestroy(){if(telemetry!=null)telemetry.close();if(ops!=null)ops.close();super.onDestroy();}

    private void synchronize(){
        long now=System.currentTimeMillis(),last=ops.getLastProcessedTs();
        List<TelemetryHistoryStore.Sample> samples=telemetry.query(Math.max(now-24L*60L*60L*1000L,last+1L),now,0);
        ops.processSamples(samples);ops.closeIfInactive(now);
    }

    private View buildRoot(){
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(BG);
        root.addView(buildBar(),new LinearLayout.LayoutParams(-1,dp(60)));
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setVerticalScrollBarEnabled(false);
        LinearLayout page=new LinearLayout(this);page.setOrientation(LinearLayout.VERTICAL);page.setPadding(dp(4),dp(4),dp(4),dp(18));page.setBackgroundColor(BG);
        buildSessions(page);buildEvents(page);
        scroll.addView(page,new ScrollView.LayoutParams(-1,-2));root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));return root;
    }

    private View buildBar(){
        LinearLayout bar=new LinearLayout(this);bar.setGravity(Gravity.CENTER_VERTICAL);bar.setPadding(dp(8),0,dp(8),0);bar.setBackgroundColor(NAVY);
        TextView back=text("‹",34,false,Color.WHITE);back.setGravity(Gravity.CENTER);back.setOnClickListener(v->finish());bar.addView(back,new LinearLayout.LayoutParams(dp(44),dp(46)));
        LinearLayout titles=new LinearLayout(this);titles.setOrientation(LinearLayout.VERTICAL);titles.setGravity(Gravity.CENTER_VERTICAL);
        TextView title=text("Сеансы и журнал",18,true,Color.WHITE);TextView sub=text("Локальная история Remote",11,false,Color.rgb(211,222,232));titles.addView(title);titles.addView(sub);bar.addView(titles,new LinearLayout.LayoutParams(0,-1,1));
        TextView info=text("ⓘ",22,true,Color.WHITE);info.setGravity(Gravity.CENTER);info.setContentDescription("Состояние системы");info.setOnClickListener(v->startActivity(new Intent(this,SystemStatusActivity.class)));bar.addView(info,new LinearLayout.LayoutParams(dp(44),dp(46)));
        return bar;
    }

    private void applyInsets(View root){
        if(Build.VERSION.SDK_INT<21)return;
        root.setOnApplyWindowInsetsListener((view,i)->{
            int l,t,r,b;
            if(Build.VERSION.SDK_INT>=30){android.graphics.Insets x=i.getInsets(WindowInsets.Type.systemBars());l=x.left;t=x.top;r=x.right;b=x.bottom;}
            else{l=i.getSystemWindowInsetLeft();t=i.getSystemWindowInsetTop();r=i.getSystemWindowInsetRight();b=i.getSystemWindowInsetBottom();}
            view.setPadding(l,t,r,b);return i;
        });
        root.requestApplyInsets();getWindow().setStatusBarColor(NAVY);getWindow().setNavigationBarColor(BG);
    }

    private void buildSessions(LinearLayout page){
        LinearLayout intro=card();intro.addView(text("Сеансы копчения",18,true,TEXT));intro.addView(detail("Сеанс начинается автоматически по свежей телеметрии. Нажмите карточку, чтобы открыть подробности и график сеанса."));page.addView(intro,margin(8,4,8,4));
        List<OperationalHistoryStore.Session> sessions=ops.querySessions(30);
        if(sessions.isEmpty()){
            LinearLayout empty=card();TextView t=text("Сеансов пока нет\nПервая запись появится после получения свежих данных от коптильни.",13,false,MUTED);t.setGravity(Gravity.CENTER);t.setPadding(0,dp(14),0,dp(14));empty.addView(t);page.addView(empty,margin(8,4,8,4));return;
        }
        for(OperationalHistoryStore.Session s:sessions)page.addView(sessionCard(s),margin(8,4,8,4));
    }

    private View sessionCard(OperationalHistoryStore.Session s){
        LinearLayout c=card();c.setClickable(true);c.setOnClickListener(v->openSession(s));
        boolean test=isTestSession(s);
        LinearLayout header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);header.addView(text(s.title(),16,true,TEXT),new LinearLayout.LayoutParams(0,-2,1));
        String chipText=test?"ТЕСТ":(s.active()?"АКТИВЕН":"ЗАВЕРШЁН");int chipColor=test?ORANGE:(s.active()?GREEN:OFF);TextView chip=text(chipText,10,true,Color.WHITE);chip.setGravity(Gravity.CENTER);chip.setPadding(dp(8),dp(4),dp(8),dp(4));chip.setBackground(round(chipColor,12));header.addView(chip);c.addView(header);
        if(test)c.addView(detail("Локальная симуляция · данные не от коптильни"));
        long end=s.active()?System.currentTimeMillis():s.endTs;
        c.addView(detail("Длительность · "+duration(Math.max(0,end-s.startTs))+" · точек "+s.samples));
        c.addView(detail("Камера "+range(s.cameraMin,s.cameraMax," °C")+" · K "+range(s.kMin,s.kMax," °C")+" · T "+range(s.tMin,s.tMax," °C")));
        c.addView(detail("Макс. ТЭН "+value(s.heaterMax," %")+(s.outageMs>0?" · потери связи ≈ "+duration(s.outageMs):"")));
        TextView more=text("Подробнее о сеансе  ›",12,true,BLUE);more.setPadding(0,dp(7),0,0);more.setOnClickListener(v->openSession(s));c.addView(more);
        LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);Button csv=action("Экспорт CSV",BLUE);Button json=action("Экспорт JSON",ORANGE);csv.setOnClickListener(v->requestExport(s,"csv"));json.setOnClickListener(v->requestExport(s,"json"));LinearLayout.LayoutParams a=new LinearLayout.LayoutParams(0,dp(42),1);a.setMargins(0,dp(8),dp(4),0);LinearLayout.LayoutParams b=new LinearLayout.LayoutParams(0,dp(42),1);b.setMargins(dp(4),dp(8),0,0);row.addView(csv,a);row.addView(json,b);c.addView(row);return c;
    }

    private void openSession(OperationalHistoryStore.Session s){Intent i=new Intent(this,SessionDetailActivity.class);i.putExtra("session_id",s.id);startActivity(i);}

    private void buildEvents(LinearLayout page){
        LinearLayout c=card();
        LinearLayout head=new LinearLayout(this);head.setGravity(Gravity.CENTER_VERTICAL);head.addView(text("Журнал событий Remote",18,true,TEXT),new LinearLayout.LayoutParams(0,-2,1));
        TextView clear=text("Очистить",11,true,BLUE);clear.setGravity(Gravity.CENTER);clear.setPadding(dp(8),dp(5),dp(8),dp(5));clear.setOnClickListener(v->confirmClearJournal());head.addView(clear);c.addView(head);
        HorizontalScrollView hs=new HorizontalScrollView(this);hs.setHorizontalScrollBarEnabled(false);filterBar=new LinearLayout(this);filterBar.setOrientation(LinearLayout.HORIZONTAL);filterBar.setPadding(0,dp(7),0,dp(3));hs.addView(filterBar,new HorizontalScrollView.LayoutParams(-2,-2));c.addView(hs);
        journalRows=new LinearLayout(this);journalRows.setOrientation(LinearLayout.VERTICAL);c.addView(journalRows);rebuildFilterBar();renderJournal();page.addView(c,margin(8,8,8,8));
    }

    private void rebuildFilterBar(){
        if(filterBar==null)return;filterBar.removeAllViews();
        for(String filter:FILTERS){
            boolean selected=filter.equals(eventFilter);TextView chip=text(filter,11,true,selected?Color.WHITE:MUTED);chip.setGravity(Gravity.CENTER);chip.setPadding(dp(10),dp(6),dp(10),dp(6));chip.setBackground(round(selected?BLUE:FIELD,13));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-2,-2);lp.setMargins(0,0,dp(6),0);filterBar.addView(chip,lp);chip.setOnClickListener(v->{eventFilter=filter;rebuildFilterBar();renderJournal();});
        }
    }

    private void renderJournal(){
        if(journalRows==null||ops==null)return;journalRows.removeAllViews();List<OperationalHistoryStore.Event> events=ops.queryEvents(500);int shown=0;SimpleDateFormat f=new SimpleDateFormat("dd.MM HH:mm:ss",Locale.getDefault());
        for(OperationalHistoryStore.Event e:events){if(!matchesFilter(e,eventFilter))continue;TextView row=text(f.format(new Date(e.ts))+" · "+e.message,12,false,eventColor(e.type));row.setPadding(0,dp(5),0,dp(5));journalRows.addView(row);shown++;if(shown>=150)break;}
        if(shown==0)journalRows.addView(detail("Событий по этому фильтру пока нет"));
    }

    private boolean matchesFilter(OperationalHistoryStore.Event e,String filter){
        if("Все".equals(filter))return true;String type=e.type==null?"":e.type.toLowerCase(Locale.ROOT),msg=e.message==null?"":e.message.toLowerCase(Locale.ROOT);
        if("Связь".equals(filter))return "connection".equals(type)||msg.contains("связ")||msg.contains("mqtt");
        if("Команды".equals(filter))return "command".equals(type);
        if("Auto".equals(filter))return msg.contains("auto")||msg.contains("программ");
        if("Температура".equals(filter))return msg.contains("устав")||msg.contains("температур")||msg.contains("камера достиг");
        if("Сеансы".equals(filter))return "session".equals(type);
        if("TEST".equals(filter))return "test".equals(type)||msg.contains("тест");return true;
    }

    private void confirmClearJournal(){new AlertDialog.Builder(this).setTitle("Очистить журнал?").setMessage("Сеансы и их сводки останутся. Будут удалены только события Remote.").setPositiveButton("Очистить",(d,w)->{ops.clearEvents();renderJournal();Toast.makeText(this,"Журнал очищен",Toast.LENGTH_SHORT).show();}).setNegativeButton("Отмена",null).show();}
    private int eventColor(String type){if("error".equals(type))return Color.rgb(190,40,40);if("connection".equals(type)||"test".equals(type))return ORANGE;if("command".equals(type))return BLUE;if("session".equals(type))return GREEN;return TEXT;}

    private boolean isTestSession(OperationalHistoryStore.Session s){
        long start=s.startTs,end=s.active()?System.currentTimeMillis():s.effectiveEnd();
        if(prefs.getBoolean("test_mode_active",false)){long active=prefs.getLong("test_active_start",0L);if(active>0&&start<=System.currentTimeMillis()&&end>=active)return true;}
        try{JSONArray a=new JSONArray(prefs.getString("test_intervals","[]"));for(int i=0;i<a.length();i++){JSONObject x=a.optJSONObject(i);if(x==null)continue;long ts=x.optLong("start",0),te=x.optLong("end",0);if(ts>0&&te>=ts&&start<=te&&end>=ts)return true;}}catch(Exception ignored){}return false;
    }

    private void requestExport(OperationalHistoryStore.Session session,String format){
        exportSession=session;Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("csv".equals(format)?"text/csv":"application/json");String stamp=new SimpleDateFormat("yyyyMMdd_HHmm",Locale.US).format(new Date(session.startTs));i.putExtra(Intent.EXTRA_TITLE,"HomeSmoke_Remote_session_"+stamp+"."+format);startActivityForResult(i,"csv".equals(format)?REQ_CSV:REQ_JSON);
    }

    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data){
        super.onActivityResult(requestCode,resultCode,data);if(resultCode!=RESULT_OK||data==null||data.getData()==null||exportSession==null)return;
        try{writeExport(data.getData(),exportSession,requestCode==REQ_CSV?"csv":"json");Toast.makeText(this,"Экспорт сохранён",Toast.LENGTH_SHORT).show();ops.addEvent(System.currentTimeMillis(),"info","Экспортирован сеанс · "+(requestCode==REQ_CSV?"CSV":"JSON"));renderJournal();}catch(Exception e){Toast.makeText(this,"Ошибка экспорта: "+safe(e),Toast.LENGTH_LONG).show();}
    }

    private void writeExport(Uri uri,OperationalHistoryStore.Session s,String format)throws Exception{
        long end=s.active()?System.currentTimeMillis():s.endTs;List<TelemetryHistoryStore.Sample> samples=telemetry.query(s.startTs,end,0);OutputStream os=getContentResolver().openOutputStream(uri);if(os==null)throw new IllegalStateException("Не удалось открыть файл");try(OutputStreamWriter w=new OutputStreamWriter(os,"UTF-8")){if("csv".equals(format))writeCsv(w,s,samples);else writeJson(w,s,samples);}
    }

    private void writeCsv(OutputStreamWriter w,OperationalHistoryStore.Session s,List<TelemetryHistoryStore.Sample> samples)throws Exception{
        SimpleDateFormat iso=new SimpleDateFormat("yyyy-MM-dd HH:mm:ss",Locale.US);w.write("source,"+(isTestSession(s)?"test":"live")+"\n");w.write("session_start,"+iso.format(new Date(s.startTs))+"\n");w.write("session_end,"+iso.format(new Date(s.effectiveEnd()))+"\n");w.write("duration_minutes,"+(s.durationMs()/60000L)+"\n");w.write("outage_seconds,"+(s.outageMs/1000L)+"\n\n");w.write("timestamp,camera_c,setpoint_c,probe_k_c,probe_t_c,heater_pct\n");for(TelemetryHistoryStore.Sample x:samples)w.write(iso.format(new Date(x.ts))+","+csv(x.camera)+","+csv(x.setpoint)+","+csv(x.probeK)+","+csv(x.probeT)+","+csv(x.heater)+"\n");if(samples.isEmpty())w.write("# raw samples unavailable (local telemetry is retained for 24 hours)\n");
    }

    private void writeJson(OutputStreamWriter w,OperationalHistoryStore.Session s,List<TelemetryHistoryStore.Sample> samples)throws Exception{
        JSONObject root=new JSONObject(),summary=new JSONObject();summary.put("source",isTestSession(s)?"test":"live");summary.put("start_ts",s.startTs);summary.put("end_ts",s.effectiveEnd());summary.put("duration_ms",s.durationMs());summary.put("samples",s.samples);summary.put("outage_ms",s.outageMs);put(summary,"camera_min",s.cameraMin);put(summary,"camera_max",s.cameraMax);put(summary,"probe_k_min",s.kMin);put(summary,"probe_k_max",s.kMax);put(summary,"probe_t_min",s.tMin);put(summary,"probe_t_max",s.tMax);put(summary,"heater_max",s.heaterMax);root.put("session",summary);JSONArray a=new JSONArray();for(TelemetryHistoryStore.Sample x:samples){JSONObject o=new JSONObject();o.put("ts",x.ts);put(o,"camera",x.camera);put(o,"setpoint",x.setpoint);put(o,"probe_k",x.probeK);put(o,"probe_t",x.probeT);put(o,"heater",x.heater);a.put(o);}root.put("telemetry",a);w.write(root.toString(2));
    }

    private static void put(JSONObject o,String k,double v)throws Exception{if(Double.isNaN(v)||Double.isInfinite(v))o.put(k,JSONObject.NULL);else o.put(k,v);}private static String csv(double v){return Double.isNaN(v)||Double.isInfinite(v)?"":String.format(Locale.US,"%.3f",v);}private static String range(double min,double max,String suffix){if(Double.isNaN(min)||Double.isNaN(max))return "—";return one(min)+"…"+one(max)+suffix;}private static String value(double v,String suffix){return Double.isNaN(v)||Double.isInfinite(v)?"—":one(v)+suffix;}private static String one(double v){return String.format(Locale.getDefault(),"%.1f",v);}private static String duration(long ms){long m=Math.max(0,ms/60000L),h=m/60,r=m%60;return h>0?h+" ч "+r+" мин":m+" мин";}private static String safe(Throwable t){String s=t==null?"":t.getMessage();return s==null||s.trim().isEmpty()?t.getClass().getSimpleName():s;}
    private LinearLayout card(){LinearLayout c=new LinearLayout(this);c.setOrientation(LinearLayout.VERTICAL);c.setPadding(dp(12),dp(10),dp(12),dp(10));c.setBackground(roundStroke(CARD,18,BORDER,1));if(Build.VERSION.SDK_INT>=21)c.setElevation(dp(1));return c;}private TextView detail(String s){TextView t=text(s,12,false,MUTED);t.setPadding(0,dp(4),0,0);t.setLineSpacing(0,1.06f);return t;}private Button action(String s,int color){Button b=new Button(this);b.setText(s);b.setTextSize(12);b.setTypeface(Typeface.DEFAULT_BOLD);b.setTextColor(Color.WHITE);b.setAllCaps(false);b.setGravity(Gravity.CENTER);b.setPadding(dp(4),0,dp(4),0);b.setBackground(round(color,13));return b;}private TextView text(String s,int sp,boolean bold,int color){TextView t=new TextView(this);t.setText(s);t.setTextSize(sp);t.setTextColor(color);if(bold)t.setTypeface(Typeface.DEFAULT_BOLD);return t;}private GradientDrawable round(int color,int radius){GradientDrawable g=new GradientDrawable();g.setColor(color);g.setCornerRadius(dp(radius));return g;}private GradientDrawable roundStroke(int color,int radius,int stroke,int width){GradientDrawable g=round(color,radius);g.setStroke(dp(width),stroke);return g;}private LinearLayout.LayoutParams margin(int l,int t,int r,int b){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(dp(l),dp(t),dp(r),dp(b));return p;}private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}
}
