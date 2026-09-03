package com.bizard.homesmokeremote;

import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
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

import java.util.List;
import java.util.Locale;

/**
 * Presentation-only refinement for the Remote graph page.
 *
 * The base MainActivity keeps MQTT, telemetry, ACK and protocol behavior unchanged.
 * This class only improves graph-page composition and reads the existing local
 * TelemetryHistoryStore to show current/last values above the chart.
 */
public class GraphUxActivity extends MainActivity {
    private static final int TEXT=Color.rgb(21,31,47);
    private static final int MUTED=Color.rgb(101,116,139);
    private static final int BORDER=Color.rgb(220,225,232);
    private static final int CARD=Color.WHITE;
    private static final int GREEN=Color.rgb(35,151,83);
    private static final int ORANGE=Color.rgb(231,138,7);
    private static final int CAMERA=Color.rgb(9,47,73);
    private static final int SETPOINT=Color.rgb(31,122,210);
    private static final int PROBE_K=Color.rgb(35,151,83);
    private static final int PROBE_T=Color.rgb(126,87,194);
    private static final int HEATER=Color.rgb(231,138,7);
    private static final long LIVE_MS=10000L;
    private static final long HISTORY_MS=24L*60L*60L*1000L;

    private final Handler uxHandler=new Handler(Looper.getMainLooper());
    private TelemetryHistoryStore uxHistory;
    private ViewGroup host;
    private boolean graphEnhanced=false;
    private ViewGroup graphPage;
    private TemperatureChartView graphChartView;
    private TextView graphRecordStatus;
    private TextView graphLiveValues;
    private TextView graphEmptyState;
    private TextView graphHeaterScale;
    private TextView graphBaseSummary;
    private View graphPointCard;

    private final Runnable graphUxTick=new Runnable(){
        @Override public void run(){
            refreshGraphUx();
            updateVersionLabels();
            uxHandler.postDelayed(this,2500L);
        }
    };

    @Override protected void onCreate(Bundle state){
        super.onCreate(state);
        uxHistory=new TelemetryHistoryStore(this);
        installHostWatcher();
        updateVersionLabels();
        uxHandler.postDelayed(graphUxTick,700L);
    }

    @Override protected void onDestroy(){
        uxHandler.removeCallbacks(graphUxTick);
        if(uxHistory!=null)uxHistory.close();
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
            @Override public void onChildViewAdded(View parent,View child){
                child.post(()->{
                    enhanceAttachedPage(child);
                    updateVersionLabels();
                });
            }
            @Override public void onChildViewRemoved(View parent,View child){}
        });
        if(host.getChildCount()>0)enhanceAttachedPage(host.getChildAt(0));
    }

    private void enhanceAttachedPage(View attached){
        View page=unwrapScroll(attached);
        if(page==null)return;
        if(containsText(page,"График температуры")&&findCheck(page,"Камера")!=null){
            enhanceGraphPage(page);
        }
    }

    private View unwrapScroll(View v){
        if(v instanceof ScrollView){
            ScrollView s=(ScrollView)v;
            return s.getChildCount()>0?s.getChildAt(0):null;
        }
        return v;
    }

    private void enhanceGraphPage(View pageView){
        if(graphEnhanced)return;
        if(!(pageView instanceof ViewGroup))return;
        graphPage=(ViewGroup)pageView;

        TextView duplicateTitle=findText(graphPage,"График температуры");
        if(duplicateTitle!=null&&duplicateTitle.getParent() instanceof View){
            View intro=(View)duplicateTitle.getParent();
            if(intro.getParent() instanceof ViewGroup){
                ViewGroup parent=(ViewGroup)intro.getParent();
                int index=parent.indexOfChild(intro);
                parent.removeView(intro);
                parent.addView(buildRecordCard(),Math.max(0,index));
            }
        }

        graphChartView=findChart(graphPage);
        if(graphChartView!=null&&graphChartView.getParent() instanceof ViewGroup){
            ViewGroup chartCard=(ViewGroup)graphChartView.getParent();
            int chartIndex=chartCard.indexOfChild(graphChartView);

            graphLiveValues=makeText("",11,true,TEXT);
            graphLiveValues.setLineSpacing(0,1.04f);
            graphLiveValues.setPadding(0,0,0,dp(5));
            graphLiveValues.setVisibility(View.GONE);
            chartCard.addView(graphLiveValues,Math.max(0,chartIndex));

            graphEmptyState=makeText("История пока пуста\nГрафик появится автоматически после получения свежих данных.",13,false,MUTED);
            graphEmptyState.setGravity(Gravity.CENTER);
            graphEmptyState.setLineSpacing(0,1.10f);
            graphEmptyState.setPadding(dp(12),dp(14),dp(12),dp(14));
            chartCard.addView(graphEmptyState,Math.max(0,chartIndex+1));

            graphHeaterScale=makeText("",11,true,MUTED);
            graphHeaterScale.setGravity(Gravity.CENTER);
            graphHeaterScale.setPadding(0,dp(4),0,0);
            setHeaterScaleText();
            chartCard.addView(graphHeaterScale);

            graphBaseSummary=findTextStarting(chartCard,"Свежих данных");
            if(graphBaseSummary==null)graphBaseSummary=findTextStarting(chartCard,"В текущем сеансе");
        }

        decorateSeriesCheck(findCheck(graphPage,"Камера"),CAMERA,"Камера");
        decorateSeriesCheck(findCheck(graphPage,"Уставка"),SETPOINT,"Уставка");
        decorateSeriesCheck(findCheck(graphPage,"Щуп K"),PROBE_K,"Щуп K");
        decorateSeriesCheck(findCheck(graphPage,"Щуп T"),PROBE_T,"Щуп T");

        TextView heaterHint=findTextStarting(graphPage,"Мощность ТЭНа отображается");
        if(heaterHint!=null)heaterHint.setText("Цвет = линия графика · ТЭН — отдельная шкала 0–100 %");

        TextView pointTitle=findText(graphPage,"Точка графика");
        if(pointTitle!=null&&pointTitle.getParent() instanceof View)graphPointCard=(View)pointTitle.getParent();

        graphEnhanced=true;
        refreshGraphUx();
    }

    private View buildRecordCard(){
        LinearLayout card=new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(12),dp(8),dp(12),dp(8));
        card.setBackground(roundStroke(CARD,18,BORDER,1));
        if(android.os.Build.VERSION.SDK_INT>=21)card.setElevation(dp(1));

        graphRecordStatus=makeText("Ожидание телеметрии",14,true,ORANGE);
        graphRecordStatus.setPadding(0,0,0,dp(1));
        card.addView(graphRecordStatus);

        TextView hint=makeText("Локальная история · до 24 ч · retained не записывается",11,false,MUTED);
        card.addView(hint);

        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);
        lp.setMargins(dp(8),dp(8),dp(8),dp(4));
        card.setLayoutParams(lp);
        return card;
    }

    private void refreshGraphUx(){
        if(!graphEnhanced||uxHistory==null)return;
        long now=System.currentTimeMillis();
        List<TelemetryHistoryStore.Sample> samples=uxHistory.query(now-HISTORY_MS,now,2);
        TelemetryHistoryStore.Sample last=samples.isEmpty()?null:samples.get(samples.size()-1);
        boolean live=last!=null&&Math.abs(now-last.ts)<=LIVE_MS;

        if(graphRecordStatus!=null){
            if(last==null){
                graphRecordStatus.setText("Ожидание телеметрии");
                graphRecordStatus.setTextColor(ORANGE);
            }else if(live){
                graphRecordStatus.setText("● Запись данных");
                graphRecordStatus.setTextColor(GREEN);
            }else{
                graphRecordStatus.setText("Нет свежих данных");
                graphRecordStatus.setTextColor(MUTED);
            }
        }

        if(graphLiveValues!=null){
            if(last==null){
                graphLiveValues.setVisibility(View.GONE);
            }else{
                String prefix=live?"Сейчас: ":"Последние: ";
                graphLiveValues.setText(prefix+"Камера "+value(last.camera,"°")+" · Уставка "+value(last.setpoint,"°")+" · K "+value(last.probeK,"°")+" · T "+value(last.probeT,"°")+" · ТЭН "+value(last.heater,"%"));
                graphLiveValues.setTextColor(live?TEXT:MUTED);
                graphLiveValues.setVisibility(View.VISIBLE);
            }
        }

        boolean any=last!=null;
        if(graphChartView!=null){
            graphChartView.setVisibility(any?View.VISIBLE:View.GONE);
            ViewGroup.LayoutParams lp=graphChartView.getLayoutParams();
            if(lp!=null){lp.height=dp(300);graphChartView.setLayoutParams(lp);}
        }
        if(graphEmptyState!=null)graphEmptyState.setVisibility(any?View.GONE:View.VISIBLE);
        if(graphHeaterScale!=null)graphHeaterScale.setVisibility(any?View.VISIBLE:View.GONE);
        if(graphBaseSummary!=null)graphBaseSummary.setVisibility(any?View.VISIBLE:View.GONE);
        if(graphPointCard!=null)graphPointCard.setVisibility(any?View.VISIBLE:View.GONE);
    }

    private void setHeaterScaleText(){
        if(graphHeaterScale==null)return;
        String text="━  ТЭН, %   ·   0 — 50 — 100";
        SpannableString span=new SpannableString(text);
        span.setSpan(new ForegroundColorSpan(HEATER),0,1,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        graphHeaterScale.setText(span);
    }

    private void decorateSeriesCheck(CheckBox box,int color,String label){
        if(box==null)return;
        SpannableString span=new SpannableString("━  "+label);
        span.setSpan(new ForegroundColorSpan(color),0,1,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        box.setText(span);
    }

    private void updateVersionLabels(){
        View content=findViewById(android.R.id.content);
        if(content==null)return;
        replaceVersionText(content);
    }

    private void replaceVersionText(View root){
        if(root instanceof TextView){
            TextView tv=(TextView)root;
            CharSequence cs=tv.getText();
            if(cs!=null){
                String s=cs.toString();
                if(s.startsWith("HomeSmoke Remote ")&&s.matches("HomeSmoke Remote \\d+\\.\\d+\\.\\d+.*")){
                    String suffix=s.replaceFirst("HomeSmoke Remote \\d+\\.\\d+\\.\\d+","HomeSmoke Remote "+appVersion());
                    if(!suffix.equals(s))tv.setText(suffix);
                }
            }
        }
        if(root instanceof ViewGroup){
            ViewGroup g=(ViewGroup)root;
            for(int i=0;i<g.getChildCount();i++)replaceVersionText(g.getChildAt(i));
        }
    }

    private String appVersion(){
        try{
            String version=getPackageManager().getPackageInfo(getPackageName(),0).versionName;
            return version==null||version.trim().isEmpty()?"2.0.15":version;
        }catch(Exception ignored){
            return "2.0.15";
        }
    }

    private boolean containsText(View root,String exact){return findText(root,exact)!=null;}

    private TextView findText(View root,String exact){
        if(root instanceof TextView&&exact.contentEquals(((TextView)root).getText()))return (TextView)root;
        if(root instanceof ViewGroup){
            ViewGroup g=(ViewGroup)root;
            for(int i=0;i<g.getChildCount();i++){
                TextView found=findText(g.getChildAt(i),exact);
                if(found!=null)return found;
            }
        }
        return null;
    }

    private TextView findTextStarting(View root,String prefix){
        if(root instanceof TextView){
            CharSequence cs=((TextView)root).getText();
            if(cs!=null&&cs.toString().startsWith(prefix))return (TextView)root;
        }
        if(root instanceof ViewGroup){
            ViewGroup g=(ViewGroup)root;
            for(int i=0;i<g.getChildCount();i++){
                TextView found=findTextStarting(g.getChildAt(i),prefix);
                if(found!=null)return found;
            }
        }
        return null;
    }

    private CheckBox findCheck(View root,String exact){
        if(root instanceof CheckBox&&exact.contentEquals(((CheckBox)root).getText()))return (CheckBox)root;
        if(root instanceof ViewGroup){
            ViewGroup g=(ViewGroup)root;
            for(int i=0;i<g.getChildCount();i++){
                CheckBox found=findCheck(g.getChildAt(i),exact);
                if(found!=null)return found;
            }
        }
        return null;
    }

    private TemperatureChartView findChart(View root){
        if(root instanceof TemperatureChartView)return (TemperatureChartView)root;
        if(root instanceof ViewGroup){
            ViewGroup g=(ViewGroup)root;
            for(int i=0;i<g.getChildCount();i++){
                TemperatureChartView found=findChart(g.getChildAt(i));
                if(found!=null)return found;
            }
        }
        return null;
    }

    private TextView makeText(String text,int sp,boolean bold,int color){
        TextView tv=new TextView(this);
        tv.setText(text);
        tv.setTextSize(sp);
        tv.setTextColor(color);
        if(bold)tv.setTypeface(Typeface.DEFAULT_BOLD);
        return tv;
    }

    private GradientDrawable roundStroke(int color,int radius,int stroke,int width){
        GradientDrawable g=new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(radius));
        g.setStroke(dp(width),stroke);
        return g;
    }

    private int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}

    private static String value(double v,String suffix){
        if(Double.isNaN(v)||Double.isInfinite(v))return "—";
        return String.format(Locale.getDefault(),"%.1f",v)+suffix;
    }
}
