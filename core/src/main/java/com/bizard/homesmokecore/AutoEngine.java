package com.bizard.homesmokecore;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Pure Java Auto state machine. No Android/UI dependencies. */
public final class AutoEngine {
    public enum State { STOPPED, RUNNING, COMPLETED, ABORTED }

    public static final class Update {
        public final List<String> commands;
        public final String message;
        public final boolean changed;
        Update(List<String> commands,String message,boolean changed){this.commands=commands;this.message=message;this.changed=changed;}
        public static Update none(String message){return new Update(Collections.emptyList(),message,false);}
    }

    private AutoProgram program;
    private State state=State.STOPPED;
    private int stageIndex=-1;
    private long stableSince=-1L;
    private long lastTick=-1L;
    private long accumulatedHoldMs=0L;
    private boolean chamberReady=false;
    private String reason="";

    public synchronized Update start(AutoProgram source,long nowMs) {
        if(state==State.RUNNING) return Update.none("Auto уже выполняется");
        validate(source);program=cloneProgram(source);stageIndex=nextEnabled(-1);
        if(stageIndex<0) throw new IllegalArgumentException("Нет активных этапов");
        state=State.RUNNING;stableSince=-1L;lastTick=nowMs;accumulatedHoldMs=0L;chamberReady=false;reason="";
        List<String> c=new ArrayList<>();c.add("x1");c.add("a1");c.add("h");c.add(setpointCommand(current().chamberTarget));
        return new Update(c,statusText(Double.NaN,Double.NaN,Double.NaN),true);
    }

    public synchronized Update stop(String why) {
        if(state!=State.RUNNING) return Update.none(why==null?"Auto выключено":why);
        state=State.ABORTED;reason=why==null?"Остановлено пользователем":why;return new Update(list("a3","x0"),reason,true);
    }

    public synchronized Update onTelemetry(Telemetry t,long nowMs) {
        if(state!=State.RUNNING) return Update.none(reason);AutoStage s=current();
        if(!TelemetryParser.isSensorValueValid(t.chamber)) return abort("Ошибка датчика камеры");
        if(s.usesK()&&!TelemetryParser.isSensorValueValid(t.probeK)) return abort("Ошибка щупа K");
        if(s.usesT()&&!TelemetryParser.isSensorValueValid(t.probeT)) return abort("Ошибка щупа T");

        long dt=lastTick<0?0:Math.max(0,nowMs-lastTick);lastTick=nowMs;
        boolean inBand=Math.abs(t.chamber-s.chamberTarget)<=Math.max(0.0,s.tolerance);
        boolean wasReady=chamberReady;
        if(inBand){
            if(stableSince<0)stableSince=nowMs;
            if(!chamberReady&&nowMs-stableSince>=Math.max(0,s.stableSeconds)*1000L)chamberReady=true;
        }else{
            // Falling outside tolerance pauses the hold and requires stabilization again.
            stableSince=-1L;chamberReady=false;
        }
        // Do not count the stabilization interval itself. Count only intervals that started ready and stayed in band.
        if(wasReady&&chamberReady&&inBand&&s.usesTime())accumulatedHoldMs+=dt;

        boolean timeDone=!s.usesTime()||accumulatedHoldMs>=Math.max(0L,s.holdMs);
        boolean probeAllowed=s.probeActivation==AutoStage.ProbeActivation.IMMEDIATE||chamberReady;
        boolean probeDone=false;
        if(probeAllowed){if(s.usesK())probeDone=t.probeK>=s.probeTarget;else if(s.usesT())probeDone=t.probeT>=s.probeTarget;}
        boolean complete;
        switch(s.finishCondition){
            case TIME:complete=timeDone;break;
            case PROBE_K:case PROBE_T:complete=probeDone;break;
            case TIME_OR_K:case TIME_OR_T:complete=timeDone||probeDone;break;
            case TIME_AND_K:case TIME_AND_T:complete=timeDone&&probeDone;break;
            default:complete=false;
        }
        if(!complete)return Update.none(statusText(t.chamber,t.probeK,t.probeT));
        if(s.stopAfter)return finish("Этап "+(stageIndex+1)+" завершён — СТОП");
        int next=nextEnabled(stageIndex);if(next<0)return finish("Программа завершена");
        stageIndex=next;stableSince=-1L;accumulatedHoldMs=0L;chamberReady=false;lastTick=nowMs;
        return new Update(list(setpointCommand(current().chamberTarget)),statusText(t.chamber,t.probeK,t.probeT),true);
    }

    public synchronized State getState(){return state;}
    public synchronized int getStageIndex(){return stageIndex;}
    public synchronized long getAccumulatedHoldMs(){return accumulatedHoldMs;}
    public synchronized boolean isChamberReady(){return chamberReady;}
    public synchronized String getReason(){return reason;}
    public synchronized AutoProgram getProgram(){return program;}
    public synchronized AutoStage getCurrentStage(){return state==State.RUNNING?current():null;}

    public synchronized String statusText(double chamber,double k,double t){
        if(state!=State.RUNNING)return reason.length()==0?state.name():reason;AutoStage s=current();StringBuilder b=new StringBuilder();
        b.append(program.name).append(" · ").append(stageIndex+1).append("/4 ").append(s.name);
        if(!Double.isNaN(chamber))b.append(" · камера ").append(one(chamber)).append("/").append(one(s.chamberTarget)).append("°C");
        if(!chamberReady)b.append(" · стабилизация ±").append(one(s.tolerance)).append("°C");
        if(s.usesTime())b.append(" · выдержка ").append(clock(accumulatedHoldMs)).append("/").append(clock(s.holdMs));
        if(s.usesK()&&!Double.isNaN(k))b.append(" · K ").append(one(k)).append("/").append(one(s.probeTarget)).append("°C");
        if(s.usesT()&&!Double.isNaN(t))b.append(" · T ").append(one(t)).append("/").append(one(s.probeTarget)).append("°C");return b.toString();
    }

    private Update abort(String why){state=State.ABORTED;reason=why;return new Update(list("a3","x0"),why,true);}
    private Update finish(String why){state=State.COMPLETED;reason=why;return new Update(list("a3","x0"),why,true);}
    private AutoStage current(){return program.stages.get(stageIndex);}
    private int nextEnabled(int after){for(int i=after+1;i<program.stages.size()&&i<4;i++)if(program.stages.get(i).enabled)return i;return -1;}
    private static String setpointCommand(double value){return "k"+BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();}
    private static List<String> list(String...x){List<String> r=new ArrayList<>();Collections.addAll(r,x);return r;}
    private static String one(double v){return BigDecimal.valueOf(v).setScale(1,java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();}
    private static String clock(long ms){long s=Math.max(0,ms/1000),h=s/3600,m=(s%3600)/60,q=s%60;return h>0?String.format(java.util.Locale.US,"%02d:%02d:%02d",h,m,q):String.format(java.util.Locale.US,"%02d:%02d",m,q);}

    private static void validate(AutoProgram p){
        if(p==null)throw new IllegalArgumentException("Программа не задана");if(p.stages.size()==0||p.stages.size()>4)throw new IllegalArgumentException("Допустимо 1..4 этапа");boolean any=false;
        for(int i=0;i<p.stages.size();i++){AutoStage s=p.stages.get(i);if(!s.enabled)continue;any=true;if(s.chamberTarget<0||s.chamberTarget>100)throw new IllegalArgumentException("Этап "+(i+1)+": температура 0..100");if(s.tolerance<0||s.tolerance>10)throw new IllegalArgumentException("Этап "+(i+1)+": допуск 0..10");if(s.stableSeconds<0||s.stableSeconds>3600)throw new IllegalArgumentException("Этап "+(i+1)+": стабилизация 0..3600 сек");if(s.holdMs<0||s.holdMs>24L*60L*60L*1000L)throw new IllegalArgumentException("Этап "+(i+1)+": выдержка до 24 ч");if((s.usesK()||s.usesT())&&(s.probeTarget<0||s.probeTarget>100))throw new IllegalArgumentException("Этап "+(i+1)+": щуп 0..100");}
        if(!any)throw new IllegalArgumentException("Нет активных этапов");
    }
    private static AutoProgram cloneProgram(AutoProgram src){AutoProgram p=new AutoProgram();p.stages.clear();p.id=src.id;p.name=src.name;p.description=src.description;p.modifiedAt=src.modifiedAt;for(AutoStage s:src.stages)p.stages.add(s.copy());return p;}
}
