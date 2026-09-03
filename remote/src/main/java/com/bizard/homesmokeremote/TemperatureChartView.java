package com.bizard.homesmokeremote;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.MotionEvent;
import android.view.View;

import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Lightweight Android 5+ chart for locally cached HomeSmoke telemetry. */
final class TemperatureChartView extends View {
    interface OnSelectionListener{void onSelected(TelemetryHistoryStore.Sample sample);}

    private static final int CAMERA=Color.rgb(9,47,73);
    private static final int SETPOINT=Color.rgb(31,122,210);
    private static final int PROBE_K=Color.rgb(35,151,83);
    private static final int PROBE_T=Color.rgb(126,87,194);
    private static final int HEATER=Color.rgb(231,138,7);
    private static final int GRID=Color.rgb(226,231,237);
    private static final int MUTED=Color.rgb(101,116,139);
    private static final int TEXT=Color.rgb(21,31,47);
    private static final int SESSION=Color.rgb(170,181,194);

    private final float density;
    private final Paint gridPaint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint axisPaint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint linePaint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint selectedPaint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint sessionPaint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private List<TelemetryHistoryStore.Sample> data=Collections.emptyList();
    private boolean showCamera=true,showSetpoint=true,showK=true,showT=true;
    private int selected=-1;
    private OnSelectionListener selectionListener;
    private final SimpleDateFormat timeFormat=new SimpleDateFormat("HH:mm",Locale.getDefault());
    private final SimpleDateFormat shortTimeFormat=new SimpleDateFormat("HH:mm:ss",Locale.getDefault());

    TemperatureChartView(Context context){
        super(context);
        density=getResources().getDisplayMetrics().density;
        setBackgroundColor(Color.WHITE);
        gridPaint.setColor(GRID);gridPaint.setStrokeWidth(dp(1));
        axisPaint.setColor(MUTED);axisPaint.setTextSize(sp(10));
        linePaint.setStyle(Paint.Style.STROKE);linePaint.setStrokeWidth(dp(2));linePaint.setStrokeCap(Paint.Cap.ROUND);linePaint.setStrokeJoin(Paint.Join.ROUND);
        selectedPaint.setColor(Color.rgb(151,164,180));selectedPaint.setStrokeWidth(dp(1));
        sessionPaint.setColor(SESSION);sessionPaint.setStrokeWidth(dp(1));sessionPaint.setPathEffect(new DashPathEffect(new float[]{dp(3),dp(4)},0));
        setClickable(true);
    }

    void setData(List<TelemetryHistoryStore.Sample> samples){
        data=samples==null?Collections.emptyList():samples;
        if(selected>=data.size())selected=-1;
        invalidate();
    }

    void setSeries(boolean camera,boolean setpoint,boolean probeK,boolean probeT){
        showCamera=camera;showSetpoint=setpoint;showK=probeK;showT=probeT;invalidate();
    }

    void setOnSelectionListener(OnSelectionListener listener){selectionListener=listener;}

    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);
        int w=getWidth(),h=getHeight();
        if(w<=0||h<=0)return;
        float left=dp(42),right=w-dp(10),top=dp(26),heaterBottom=h-dp(24),heaterTop=h-dp(78),tempBottom=heaterTop-dp(24);
        if(data.isEmpty()){
            Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);p.setColor(MUTED);p.setTextSize(sp(14));p.setTextAlign(Paint.Align.CENTER);
            canvas.drawText("Свежих данных за этот период нет",w/2f,h/2f,p);
            return;
        }

        long minTs=data.get(0).ts,maxTs=data.get(data.size()-1).ts;
        if(maxTs<=minTs)maxTs=minTs+1;
        double[] mm=tempBounds();
        double min=mm[0],max=mm[1];
        if(Double.isNaN(min)||Double.isNaN(max)){
            min=0;max=100;
        }else if(Math.abs(max-min)<0.5){
            min-=2;max+=2;
        }else{
            double pad=Math.max(1.0,(max-min)*0.10);min-=pad;max+=pad;
        }

        axisPaint.setTextAlign(Paint.Align.RIGHT);
        for(int i=0;i<=4;i++){
            float y=top+(tempBottom-top)*i/4f;
            canvas.drawLine(left,y,right,y,gridPaint);
            double value=max-(max-min)*i/4.0;
            canvas.drawText(format(value),left-dp(5),y+sp(3),axisPaint);
        }
        axisPaint.setTextAlign(Paint.Align.LEFT);
        canvas.drawText("°C",dp(8),top-sp(5),axisPaint);

        drawSessionSeparators(canvas,minTs,maxTs,left,right,top,heaterBottom);
        drawSeries(canvas,minTs,maxTs,min,max,left,right,top,tempBottom,0,CAMERA,showCamera);
        drawSeries(canvas,minTs,maxTs,min,max,left,right,top,tempBottom,1,SETPOINT,showSetpoint);
        drawSeries(canvas,minTs,maxTs,min,max,left,right,top,tempBottom,2,PROBE_K,showK);
        drawSeries(canvas,minTs,maxTs,min,max,left,right,top,tempBottom,3,PROBE_T,showT);
        drawLegend(canvas,left,top-dp(11));

        canvas.drawLine(left,heaterTop,right,heaterTop,gridPaint);
        canvas.drawLine(left,heaterBottom,right,heaterBottom,gridPaint);
        axisPaint.setTextAlign(Paint.Align.LEFT);
        canvas.drawText("ТЭН, %",dp(8),heaterTop-dp(6),axisPaint);
        drawHeater(canvas,minTs,maxTs,left,right,heaterTop,heaterBottom);

        SimpleDateFormat axisTime=(maxTs-minTs)<10L*60L*1000L?shortTimeFormat:timeFormat;
        for(int i=0;i<=3;i++){
            float x=left+(right-left)*i/3f;
            long ts=minTs+Math.round((maxTs-minTs)*i/3.0);
            axisPaint.setTextAlign(i==0?Paint.Align.LEFT:(i==3?Paint.Align.RIGHT:Paint.Align.CENTER));
            canvas.drawText(axisTime.format(new Date(ts)),x,h-dp(7),axisPaint);
        }
        axisPaint.setTextAlign(Paint.Align.CENTER);

        if(selected>=0&&selected<data.size()){
            TelemetryHistoryStore.Sample s=data.get(selected);
            float x=xFor(s.ts,minTs,maxTs,left,right);
            canvas.drawLine(x,top,x,heaterBottom,selectedPaint);
            drawSelectionPoint(canvas,x,s.camera,min,max,top,tempBottom,CAMERA,showCamera);
            drawSelectionPoint(canvas,x,s.setpoint,min,max,top,tempBottom,SETPOINT,showSetpoint);
            drawSelectionPoint(canvas,x,s.probeK,min,max,top,tempBottom,PROBE_K,showK);
            drawSelectionPoint(canvas,x,s.probeT,min,max,top,tempBottom,PROBE_T,showT);
        }
    }

    private void drawSessionSeparators(Canvas c,long minTs,long maxTs,float left,float right,float top,float bottom){
        if(data.size()<2)return;
        int previous=data.get(0).sessionId;
        int transitions=0;
        for(int i=1;i<data.size();i++)if(data.get(i).sessionId!=previous){transitions++;previous=data.get(i).sessionId;}
        if(transitions==0)return;
        previous=data.get(0).sessionId;
        for(int i=1;i<data.size();i++){
            TelemetryHistoryStore.Sample s=data.get(i);
            if(s.sessionId==previous)continue;
            float x=xFor(s.ts,minTs,maxTs,left,right);
            c.drawLine(x,top,x,bottom,sessionPaint);
            previous=s.sessionId;
        }
    }

    private double[] tempBounds(){
        double min=Double.NaN,max=Double.NaN;
        for(TelemetryHistoryStore.Sample s:data){
            if(showCamera){double[] r=include(min,max,s.camera);min=r[0];max=r[1];}
            if(showSetpoint){double[] r=include(min,max,s.setpoint);min=r[0];max=r[1];}
            if(showK){double[] r=include(min,max,s.probeK);min=r[0];max=r[1];}
            if(showT){double[] r=include(min,max,s.probeT);min=r[0];max=r[1];}
        }
        return new double[]{min,max};
    }

    private static double[] include(double min,double max,double v){
        if(Double.isNaN(v)||Double.isInfinite(v))return new double[]{min,max};
        if(Double.isNaN(min)||v<min)min=v;
        if(Double.isNaN(max)||v>max)max=v;
        return new double[]{min,max};
    }

    private void drawSeries(Canvas c,long minTs,long maxTs,double min,double max,float left,float right,float top,float bottom,int field,int color,boolean enabled){
        if(!enabled)return;
        Path path=new Path();boolean started=false;int segment=-1;
        for(TelemetryHistoryStore.Sample s:data){
            double value=value(s,field);
            if(Double.isNaN(value)||Double.isInfinite(value)){started=false;segment=-1;continue;}
            float x=xFor(s.ts,minTs,maxTs,left,right),y=yFor(value,min,max,top,bottom);
            if(!started||s.segmentId!=segment){path.moveTo(x,y);started=true;}else path.lineTo(x,y);
            segment=s.segmentId;
        }
        linePaint.setColor(color);linePaint.setStrokeWidth(dp(field==1?1.6f:2f));
        c.drawPath(path,linePaint);
        drawSinglePointSegments(c,minTs,maxTs,min,max,left,right,top,bottom,field,color);
    }

    /** A one-sample segment would otherwise be invisible because Path.moveTo draws no mark. */
    private void drawSinglePointSegments(Canvas c,long minTs,long maxTs,double min,double max,float left,float right,float top,float bottom,int field,int color){
        Paint dot=new Paint(Paint.ANTI_ALIAS_FLAG);dot.setColor(color);dot.setStyle(Paint.Style.FILL);
        for(int i=0;i<data.size();i++){
            TelemetryHistoryStore.Sample s=data.get(i);
            boolean sameBefore=i>0&&data.get(i-1).segmentId==s.segmentId;
            boolean sameAfter=i+1<data.size()&&data.get(i+1).segmentId==s.segmentId;
            if(sameBefore||sameAfter)continue;
            double v=value(s,field);if(Double.isNaN(v)||Double.isInfinite(v))continue;
            c.drawCircle(xFor(s.ts,minTs,maxTs,left,right),yFor(v,min,max,top,bottom),dp(2.2f),dot);
        }
    }

    private void drawHeater(Canvas c,long minTs,long maxTs,float left,float right,float top,float bottom){
        Path path=new Path();boolean started=false;int segment=-1;
        for(TelemetryHistoryStore.Sample s:data){
            if(Double.isNaN(s.heater)||Double.isInfinite(s.heater)){started=false;segment=-1;continue;}
            double v=Math.max(0,Math.min(100,s.heater));
            float x=xFor(s.ts,minTs,maxTs,left,right),y=(float)(bottom-(bottom-top)*(v/100.0));
            if(!started||s.segmentId!=segment){path.moveTo(x,y);started=true;}else path.lineTo(x,y);
            segment=s.segmentId;
        }
        linePaint.setColor(HEATER);linePaint.setStrokeWidth(dp(1.8f));c.drawPath(path,linePaint);
        drawSingleHeaterPoints(c,minTs,maxTs,left,right,top,bottom);
    }

    private void drawSingleHeaterPoints(Canvas c,long minTs,long maxTs,float left,float right,float top,float bottom){
        Paint dot=new Paint(Paint.ANTI_ALIAS_FLAG);dot.setColor(HEATER);dot.setStyle(Paint.Style.FILL);
        for(int i=0;i<data.size();i++){
            TelemetryHistoryStore.Sample s=data.get(i);
            boolean sameBefore=i>0&&data.get(i-1).segmentId==s.segmentId;
            boolean sameAfter=i+1<data.size()&&data.get(i+1).segmentId==s.segmentId;
            if(sameBefore||sameAfter||Double.isNaN(s.heater)||Double.isInfinite(s.heater))continue;
            double v=Math.max(0,Math.min(100,s.heater));
            float y=(float)(bottom-(bottom-top)*(v/100.0));
            c.drawCircle(xFor(s.ts,minTs,maxTs,left,right),y,dp(2f),dot);
        }
    }

    private void drawLegend(Canvas c,float x,float y){
        Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);p.setTextSize(sp(9));p.setColor(TEXT);
        float pos=x;
        if(showCamera)pos=legendItem(c,p,pos,y,CAMERA,"Камера");
        if(showSetpoint)pos=legendItem(c,p,pos,y,SETPOINT,"Уставка");
        if(showK)pos=legendItem(c,p,pos,y,PROBE_K,"K");
        if(showT)legendItem(c,p,pos,y,PROBE_T,"T");
    }

    private float legendItem(Canvas c,Paint text,float x,float y,int color,String label){
        Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);p.setColor(color);p.setStrokeWidth(dp(2));
        c.drawLine(x,y,x+dp(12),y,p);c.drawText(label,x+dp(16),y+sp(3),text);
        return x+dp(20)+text.measureText(label)+dp(10);
    }

    private void drawSelectionPoint(Canvas c,float x,double value,double min,double max,float top,float bottom,int color,boolean enabled){
        if(!enabled||Double.isNaN(value)||Double.isInfinite(value))return;
        Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);p.setColor(color);p.setStyle(Paint.Style.FILL);
        c.drawCircle(x,yFor(value,min,max,top,bottom),dp(3),p);
    }

    private static double value(TelemetryHistoryStore.Sample s,int field){
        if(field==0)return s.camera;if(field==1)return s.setpoint;if(field==2)return s.probeK;return s.probeT;
    }

    private static float xFor(long ts,long minTs,long maxTs,float left,float right){return left+(right-left)*(float)((ts-minTs)/(double)(maxTs-minTs));}
    private static float yFor(double value,double min,double max,float top,float bottom){return (float)(bottom-(bottom-top)*((value-min)/(max-min)));}

    @Override public boolean onTouchEvent(MotionEvent event){
        if(data.isEmpty())return super.onTouchEvent(event);
        int action=event.getActionMasked();
        if(action==MotionEvent.ACTION_DOWN||action==MotionEvent.ACTION_MOVE){
            getParent().requestDisallowInterceptTouchEvent(true);
            float left=dp(42),right=getWidth()-dp(10);
            long minTs=data.get(0).ts,maxTs=data.get(data.size()-1).ts;
            if(maxTs<=minTs)maxTs=minTs+1;
            double ratio=Math.max(0,Math.min(1,(event.getX()-left)/(right-left)));
            long target=minTs+Math.round((maxTs-minTs)*ratio);
            int best=0;long bestDiff=Long.MAX_VALUE;
            for(int i=0;i<data.size();i++){long d=Math.abs(data.get(i).ts-target);if(d<bestDiff){bestDiff=d;best=i;}}
            selected=best;invalidate();
            if(selectionListener!=null)selectionListener.onSelected(data.get(best));
            return true;
        }
        if(action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_CANCEL){getParent().requestDisallowInterceptTouchEvent(false);performClick();return true;}
        return true;
    }

    @Override public boolean performClick(){super.performClick();return true;}

    private float dp(float v){return v*density;}
    private float sp(float v){return v*getResources().getDisplayMetrics().scaledDensity;}
    private static String format(double v){return Math.abs(v-Math.rint(v))<0.05?String.valueOf((long)Math.rint(v)):String.format(Locale.getDefault(),"%.1f",v);}
}
