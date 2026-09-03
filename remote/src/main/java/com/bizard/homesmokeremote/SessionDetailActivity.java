package com.bizard.homesmokeremote;

import android.app.Activity;
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

/** Detailed view of one locally recorded smoking session. */
public final class SessionDetailActivity extends Activity {
    private static final int NAVY=Color.rgb(9,47,73),BG=Color.rgb(245,247,250),CARD=Color.WHITE,TEXT=Color.rgb(21,31,47),MUTED=Color.rgb(101,116,139),BORDER=Color.rgb(220,225,232),GREEN=Color.rgb(35,151,83),BLUE=Color.rgb(31,122,210),ORANGE=Color.rgb(231,138,7),OFF=Color.rgb(116,129,145);
    private static final int REQ_CSV=4201,REQ_JSON=4202;
    private TelemetryHistoryStore telemetry;
    private OperationalHistoryStore ops;
    private SharedPreferences prefs;
    private OperationalHistoryStore.Session session;
    private TextView pointText;

    @Override protected void onCreate(Bundle state){
        super.onCreate(state);
        prefs=getSharedPreferences("homesmoke_remote",MODE_PRIVATE);
        telemetry=new TelemetryHistoryStore(this);
        ops=new OperationalHistoryStore(this);
        long id=getIntent().getLongExtra("session_id",0L);
        session=ops.querySession(id);
        if(session==null){Toast.makeText(this,"Сеанс не найден",Toast.LENGTH_LONG).show();finish();return;}
        View root=buildRoot();setContentView(root);applyInsets(root);
    }

    @Override protected void onDestroy(){if(telemetry!=null)telemetry.close();if(ops!=null)ops.close();super.onDestroy();}

    private View buildRoot(){
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(BG);
        root.addView(buildBar(),new LinearLayout.LayoutParams(-1,dp(60)));
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setVerticalScrollBarEnabled(false);
        LinearLayout page=new LinearLayout(this);page.setOrientation(LinearLayout.VERTICAL);page.setPadding(dp(4),dp(4),dp(4),dp(18));page.setBackgroundColor(BG);
        page.addView(buildSummary(),margin(8,4,8,4));
        page.addView(buildAnalytics(),margin(8,4,8,4));
        page.addView(buildGraph(),margin(8,4,8,4));
        page.addView(buildEvents(),margin(8,4,8,4));
        scroll.addView(page,new ScrollView.LayoutParams(-1,-2));root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));return root;
    }

    private View buildBar(){
        LinearLayout bar=new LinearLayout(this);bar.setGravity(Gravity.CENTER_VERTICAL);bar.setPadding(dp(8),0,dp(10),0);bar.setBackgroundColor(NAVY);
        TextView back=text("‹",34,false,Color.WHITE);back.setGravity(Gravity.CENTER);back.setOnClickListener(v->finish());bar.addView(back,new LinearLayout.LayoutParams(dp(44),dp(46)));
        LinearLayout titles=new LinearLayout(this);titles.setOrientation(LinearLayout.VERTICAL);titles.setGravity(Gravity.CENTER_VERTICAL);
        titles.addView(text("Сеанс",18,true,Color.WHITE));titles.addView(text(session.title(),11,false,Color.rgb(211,222,232)));bar.addView(titles,new LinearLayout.LayoutParams(0,-1,1));return bar;
    }

    private void applyInsets(View root){
        if(Build.VERSION.SDK_INT<21)return;
        root.setOnApplyWindowInsetsListener((view,i)->{int l,t,r,b;if(Build.VERSION.SDK_INT>=30){android.graphics.Insets x=i.getInsets(WindowInsets.Type.systemBars());l=x.left;t=x.top;r=x.right;b=x.bottom;}else{l=i.getSystemWindowInsetLeft();t=i.getSystemWindowInsetTop();r=i.getSystemWindowInsetRight();b=i.getSystemWindowInsetBottom();}view.setPadding(l,t,r,b);return i;});
        root.requestApplyInsets();getWindow().setStatusBarColor(NAVY);getWindow().setNavigationBarColor(BG);
    }

    private View buildSummary(){
        LinearLayout c=card();boolean test=isTestSession(session);
        LinearLayout head=new LinearLayout(this);head.setGravity(Gravity.CENTER_VERTICAL);head.addView(text(session.title(),18,true,TEXT),new LinearLayout.LayoutParams(0,-2,1));
        String label=test?"ТЕСТ":(session.active()?"АКТИВЕН":"ЗАВЕРШЁН");int color=test?ORANGE:(session.active()?GREEN:OFF);
        TextView chip=text(label,10,true,Color.WHITE);chip.setGravity(Gravity.CENTER);chip.setPadding(dp(8),dp(4),dp(8),dp(4));chip.setBackground(round(color,12));head.addView(chip);c.addView(head);
        if(test)c.addView(detail("Источник · локальная симуляция"));
        long end=session.active()?System.currentTimeMillis():session.effectiveEnd();
        c.addView(detail("Начало · "+dateTime(session.startTs)));
        c.addView(detail("Конец · "+(session.active()?"идёт сейчас":dateTime(end))));
        c.addView(detail("Длительность · "+duration(Math.max(0,end-session.startTs))+" · точек "+session.samples));
        c.addView(detail("Камера · "+range(session.cameraMin,session.cameraMax," °C")));
        c.addView(detail("Щуп K · "+range(session.kMin,session.kMax," °C")+" · Щуп T · "+range(session.tMin,session.tMax," °C")));
        c.addView(detail("Макс. ТЭН · "+value(session.heaterMax," %")+(session.outageMs>0?" · потери связи ≈ "+durationDetailed(session.outageMs):" · потерь связи не зафиксировано")));
        LinearLayout row=new LinearLayout(this);Button csv=action("Экспорт CSV",BLUE),json=action("Экспорт JSON",ORANGE);csv.setOnClickListener(v->requestExport("csv"));json.setOnClickListener(v->requestExport("json"));
        LinearLayout.LayoutParams a=new LinearLayout.LayoutParams(0,dp(42),1);a.setMargins(0,dp(8),dp(4),0);LinearLayout.LayoutParams b=new LinearLayout.LayoutParams(0,dp(42),1);b.setMargins(dp(4),dp(8),0,0);row.addView(csv,a);row.addView(json,b);c.addView(row);return c;
    }

    private View buildAnalytics(){
        LinearLayout c=card();c.addView(text("Итог сеанса",18,true,TEXT));
        long end=session.active()?System.currentTimeMillis():session.effectiveEnd();
        List<TelemetryHistoryStore.Sample> samples=telemetry.query(session.startTs,end,0);
        SessionAnalytics.Result a=SessionAnalytics.analyze(samples);
        if(!a.available){
            TextView note=detail("Расширенная аналитика недоступна: сырые точки этого сеанса уже удалены из 24-часовой локальной истории. Базовая сводка выше сохранена.");note.setPadding(0,dp(8),0,dp(8));c.addView(note);return c;
        }
        if(isTestSession(session)){TextView test=detail("ТЕСТ · синтетические данные проверяют расчёты; это не оценка реальной коптильни.");test.setTextColor(ORANGE);test.setTypeface(Typeface.DEFAULT_BOLD);c.addView(test);}
        c.addView(detail("Средняя камера · "+num(a.averageCamera)+" °C · средний ТЭН · "+num(a.averageHeater)+" %"));
        c.addView(detail("Стабильность · ±1 °C "+pct(a.stability1)+" · ±2 °C "+pct(a.stability2)+" · ±3 °C "+pct(a.stability3)));
        c.addView(detail("Среднее отклонение · "+num(a.averageAbsoluteError)+" °C · макс. перегрев · "+signed(a.maxOvershoot)+" °C"));
        String target=Double.isNaN(a.initialSetpoint)?"нет корректной уставки":num(a.initialSetpoint)+" °C · "+(a.timeToFirstTargetMs>=0?"достигнута через "+durationDetailed(a.timeToFirstTargetMs):"не достигнута в пределах ±1 °C");
        c.addView(detail("Первая уставка · "+target));
        c.addView(detail("Смен уставки · "+a.setpointChanges+" · полезной телеметрии · "+durationDetailed(a.validTelemetryMs)));
        c.addView(detail("Изменение щупов · K "+signed(a.probeKDelta)+" °C · T "+signed(a.probeTDelta)+" °C"));
        c.addView(detail("Потери телеметрии · "+a.outageEpisodes+" эпиз. · ≈ "+durationDetailed(a.outageMs)));
        TextView method=detail("Расчёт по сохранённым точкам. Интервалы между точками >15 сек считаются потерей телеметрии и исключаются из средних и показателей стабильности. Интервал смены уставки также не смешивается со стабильностью.");method.setPadding(0,dp(7),0,0);c.addView(method);
        return c;
    }

    private View buildGraph(){
        LinearLayout c=card();c.addView(text("График сеанса",18,true,TEXT));
        long end=session.active()?System.currentTimeMillis():session.effectiveEnd();
        List<TelemetryHistoryStore.Sample> samples=telemetry.query(session.startTs,end,700);
        if(samples.isEmpty()){
            TextView note=detail("Сырые точки этого сеанса уже недоступны. Локальная телеметрия хранится до 24 часов, но сводка сеанса остаётся в журнале.");note.setPadding(0,dp(12),0,dp(12));c.addView(note);return c;
        }
        TextView current=detail("Точек на графике · "+samples.size()+" · "+time(samples.get(0).ts)+"—"+time(samples.get(samples.size()-1).ts));current.setPadding(0,dp(5),0,dp(4));c.addView(current);
        TemperatureChartView chart=new TemperatureChartView(this);chart.setSeries(true,true,true,true);chart.setData(samples);c.addView(chart,new LinearLayout.LayoutParams(-1,dp(310)));
        pointText=detail("Коснитесь графика, чтобы увидеть значения точки.");pointText.setPadding(0,dp(5),0,0);c.addView(pointText);
        chart.setOnSelectionListener(s->pointText.setText(dateTime(s.ts)+"\nКамера "+num(s.camera)+" °C · Уставка "+num(s.setpoint)+" °C\nK "+num(s.probeK)+" °C · T "+num(s.probeT)+" °C · ТЭН "+num(s.heater)+" %"));return c;
    }

    private View buildEvents(){
        LinearLayout c=card();c.addView(text("События этого сеанса",18,true,TEXT));
        long end=session.active()?System.currentTimeMillis():session.effectiveEnd()+5000L;List<OperationalHistoryStore.Event> events=ops.queryEvents(Math.max(0,session.startTs-2000L),end,200);
        if(events.isEmpty()){c.addView(detail("Событий для этого сеанса не найдено"));return c;}
        SimpleDateFormat f=new SimpleDateFormat("HH:mm:ss",Locale.getDefault());for(OperationalHistoryStore.Event e:events){TextView row=text(f.format(new Date(e.ts))+" · "+e.message,12,false,eventColor(e.type));row.setPadding(0,dp(5),0,dp(5));c.addView(row);}return c;
    }

    private void requestExport(String format){Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("csv".equals(format)?"text/csv":"application/json");String stamp=new SimpleDateFormat("yyyyMMdd_HHmm",Locale.US).format(new Date(session.startTs));i.putExtra(Intent.EXTRA_TITLE,"HomeSmoke_Remote_session_"+stamp+"."+format);startActivityForResult(i,"csv".equals(format)?REQ_CSV:REQ_JSON);}

    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data){
        super.onActivityResult(requestCode,resultCode,data);if(resultCode!=RESULT_OK||data==null||data.getData()==null)return;String format=requestCode==REQ_CSV?"csv":"json";
        try{writeExport(data.getData(),format);Toast.makeText(this,"Экспорт сохранён",Toast.LENGTH_SHORT).show();ops.addEvent(System.currentTimeMillis(),"info","Экспортирован сеанс · "+format.toUpperCase(Locale.ROOT));}catch(Exception e){Toast.makeText(this,"Ошибка экспорта: "+safe(e),Toast.LENGTH_LONG).show();}
    }

    private void writeExport(Uri uri,String format)throws Exception{
        long end=session.active()?System.currentTimeMillis():session.effectiveEnd();List<TelemetryHistoryStore.Sample> samples=telemetry.query(session.startTs,end,0);SessionAnalytics.Result analytics=SessionAnalytics.analyze(samples);
        OutputStream os=getContentResolver().openOutputStream(uri);if(os==null)throw new IllegalStateException("Не удалось открыть файл");try(OutputStreamWriter w=new OutputStreamWriter(os,"UTF-8")){if("csv".equals(format))writeCsv(w,samples,analytics);else writeJson(w,samples,analytics);}
    }

    private void writeCsv(OutputStreamWriter w,List<TelemetryHistoryStore.Sample> samples,SessionAnalytics.Result a)throws Exception{
        SimpleDateFormat iso=new SimpleDateFormat("yyyy-MM-dd HH:mm:ss",Locale.US);
        w.write("source,"+(isTestSession(session)?"test":"live")+"\n");w.write("session_start,"+iso.format(new Date(session.startTs))+"\n");w.write("session_end,"+iso.format(new Date(session.effectiveEnd()))+"\n");w.write("duration_minutes,"+(session.durationMs()/60000L)+"\n");w.write("outage_seconds,"+(session.outageMs/1000L)+"\n");
        if(a.available){w.write("analytics_average_camera_c,"+csv(a.averageCamera)+"\n");w.write("analytics_average_heater_pct,"+csv(a.averageHeater)+"\n");w.write("analytics_average_abs_error_c,"+csv(a.averageAbsoluteError)+"\n");w.write("analytics_max_overshoot_c,"+csv(a.maxOvershoot)+"\n");w.write("analytics_stability_1c_pct,"+csv(a.stability1)+"\n");w.write("analytics_stability_2c_pct,"+csv(a.stability2)+"\n");w.write("analytics_stability_3c_pct,"+csv(a.stability3)+"\n");w.write("analytics_initial_setpoint_c,"+csv(a.initialSetpoint)+"\n");w.write("analytics_time_to_first_target_s,"+(a.timeToFirstTargetMs>=0?a.timeToFirstTargetMs/1000L:"")+"\n");w.write("analytics_setpoint_changes,"+a.setpointChanges+"\n");w.write("analytics_probe_k_delta_c,"+csv(a.probeKDelta)+"\n");w.write("analytics_probe_t_delta_c,"+csv(a.probeTDelta)+"\n");w.write("analytics_outage_episodes,"+a.outageEpisodes+"\n");w.write("analytics_outage_seconds,"+(a.outageMs/1000L)+"\n");}
        w.write("\ntimestamp,camera_c,setpoint_c,probe_k_c,probe_t_c,heater_pct\n");for(TelemetryHistoryStore.Sample x:samples)w.write(iso.format(new Date(x.ts))+","+csv(x.camera)+","+csv(x.setpoint)+","+csv(x.probeK)+","+csv(x.probeT)+","+csv(x.heater)+"\n");if(samples.isEmpty())w.write("# raw samples unavailable (local telemetry is retained for 24 hours)\n");
    }

    private void writeJson(OutputStreamWriter w,List<TelemetryHistoryStore.Sample> samples,SessionAnalytics.Result a)throws Exception{
        JSONObject root=new JSONObject(),summary=new JSONObject();summary.put("source",isTestSession(session)?"test":"live");summary.put("start_ts",session.startTs);summary.put("end_ts",session.effectiveEnd());summary.put("duration_ms",session.durationMs());summary.put("samples",session.samples);summary.put("outage_ms",session.outageMs);put(summary,"camera_min",session.cameraMin);put(summary,"camera_max",session.cameraMax);put(summary,"probe_k_min",session.kMin);put(summary,"probe_k_max",session.kMax);put(summary,"probe_t_min",session.tMin);put(summary,"probe_t_max",session.tMax);put(summary,"heater_max",session.heaterMax);root.put("session",summary);
        if(a.available){JSONObject x=new JSONObject();put(x,"average_camera_c",a.averageCamera);put(x,"average_heater_pct",a.averageHeater);put(x,"average_abs_error_c",a.averageAbsoluteError);put(x,"max_overshoot_c",a.maxOvershoot);put(x,"stability_1c_pct",a.stability1);put(x,"stability_2c_pct",a.stability2);put(x,"stability_3c_pct",a.stability3);put(x,"initial_setpoint_c",a.initialSetpoint);if(a.timeToFirstTargetMs>=0)x.put("time_to_first_target_ms",a.timeToFirstTargetMs);else x.put("time_to_first_target_ms",JSONObject.NULL);x.put("setpoint_changes",a.setpointChanges);put(x,"probe_k_delta_c",a.probeKDelta);put(x,"probe_t_delta_c",a.probeTDelta);x.put("outage_episodes",a.outageEpisodes);x.put("outage_ms",a.outageMs);x.put("valid_telemetry_ms",a.validTelemetryMs);root.put("analytics",x);}
        JSONArray arr=new JSONArray();for(TelemetryHistoryStore.Sample p:samples){JSONObject o=new JSONObject();o.put("ts",p.ts);put(o,"camera",p.camera);put(o,"setpoint",p.setpoint);put(o,"probe_k",p.probeK);put(o,"probe_t",p.probeT);put(o,"heater",p.heater);arr.put(o);}root.put("telemetry",arr);w.write(root.toString(2));
    }

    private boolean isTestSession(OperationalHistoryStore.Session s){long start=s.startTs,end=s.active()?System.currentTimeMillis():s.effectiveEnd();if(prefs.getBoolean("test_mode_active",false)){long active=prefs.getLong("test_active_start",0L);if(active>0&&start<=System.currentTimeMillis()&&end>=active)return true;}try{JSONArray a=new JSONArray(prefs.getString("test_intervals","[]"));for(int i=0;i<a.length();i++){JSONObject x=a.optJSONObject(i);if(x==null)continue;long ts=x.optLong("start",0),te=x.optLong("end",0);if(ts>0&&te>=ts&&start<=te&&end>=ts)return true;}}catch(Exception ignored){}return false;}

    private int eventColor(String type){if("error".equals(type))return Color.rgb(190,40,40);if("connection".equals(type)||"test".equals(type))return ORANGE;if("command".equals(type))return BLUE;if("session".equals(type))return GREEN;return TEXT;}
    private static void put(JSONObject o,String k,double v)throws Exception{if(Double.isNaN(v)||Double.isInfinite(v))o.put(k,JSONObject.NULL);else o.put(k,v);}
    private static String csv(double v){return Double.isNaN(v)||Double.isInfinite(v)?"":String.format(Locale.US,"%.3f",v);}
    private static String num(double v){return Double.isNaN(v)||Double.isInfinite(v)?"—":String.format(Locale.getDefault(),"%.1f",v);}
    private static String pct(double v){return Double.isNaN(v)||Double.isInfinite(v)?"—":String.format(Locale.getDefault(),"%.0f %%",v);}
    private static String signed(double v){if(Double.isNaN(v)||Double.isInfinite(v))return "—";return String.format(Locale.getDefault(),v>=0?"+%.1f":"%.1f",v);}
    private static String range(double min,double max,String suffix){if(Double.isNaN(min)||Double.isNaN(max))return "—";return num(min)+"…"+num(max)+suffix;}
    private static String value(double v,String suffix){return Double.isNaN(v)||Double.isInfinite(v)?"—":num(v)+suffix;}
    private static String duration(long ms){long m=Math.max(0,ms/60000L),h=m/60,r=m%60;return h>0?h+" ч "+r+" мин":m+" мин";}
    private static String durationDetailed(long ms){long sec=Math.max(0,ms/1000L),h=sec/3600,m=(sec%3600)/60,s=sec%60;if(h>0)return h+" ч "+m+" мин";if(m>0)return m+" мин "+s+" сек";return s+" сек";}
    private static String dateTime(long ts){return new SimpleDateFormat("dd.MM.yyyy HH:mm:ss",Locale.getDefault()).format(new Date(ts));}
    private static String time(long ts){return new SimpleDateFormat("HH:mm:ss",Locale.getDefault()).format(new Date(ts));}
    private static String safe(Throwable t){String s=t==null?"":t.getMessage();return s==null||s.trim().isEmpty()?t.getClass().getSimpleName():s;}

    private LinearLayout card(){LinearLayout c=new LinearLayout(this);c.setOrientation(LinearLayout.VERTICAL);c.setPadding(dp(12),dp(10),dp(12),dp(10));c.setBackground(roundStroke(CARD,18,BORDER,1));if(Build.VERSION.SDK_INT>=21)c.setElevation(dp(1));return c;}
    private TextView detail(String s){TextView t=text(s,12,false,MUTED);t.setPadding(0,dp(4),0,0);t.setLineSpacing(0,1.06f);return t;}
    private Button action(String s,int color){Button b=new Button(this);b.setText(s);b.setTextSize(12);b.setTypeface(Typeface.DEFAULT_BOLD);b.setTextColor(Color.WHITE);b.setAllCaps(false);b.setGravity(Gravity.CENTER);b.setPadding(dp(4),0,dp(4),0);b.setBackground(round(color,13));return b;}
    private TextView text(String s,int sp,boolean bold,int color){TextView t=new TextView(this);t.setText(s);t.setTextSize(sp);t.setTextColor(color);if(bold)t.setTypeface(Typeface.DEFAULT_BOLD);return t;}
    private GradientDrawable round(int color,int radius){GradientDrawable g=new GradientDrawable();g.setColor(color);g.setCornerRadius(dp(radius));return g;}
    private GradientDrawable roundStroke(int color,int radius,int stroke,int width){GradientDrawable g=round(color,radius);g.setStroke(dp(width),stroke);return g;}
    private LinearLayout.LayoutParams margin(int l,int t,int r,int b){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(dp(l),dp(t),dp(r),dp(b));return p;}
    private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}
}
