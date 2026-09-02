package com.bizard.homesmokecore;

public final class AutoStage {
    public enum FinishCondition { TIME, PROBE_K, PROBE_T, TIME_OR_K, TIME_OR_T, TIME_AND_K, TIME_AND_T }
    public enum ProbeActivation { IMMEDIATE, AFTER_CHAMBER_READY }

    public String name="Этап";
    public boolean enabled=true;
    public double chamberTarget=40.0;
    public double tolerance=1.0;
    public int stableSeconds=20;
    public long holdMs=15L*60L*1000L;
    public FinishCondition finishCondition=FinishCondition.TIME;
    public double probeTarget=0.0;
    public ProbeActivation probeActivation=ProbeActivation.AFTER_CHAMBER_READY;
    public boolean stopAfter=false;

    public AutoStage copy() {
        AutoStage s=new AutoStage();
        s.name=name; s.enabled=enabled; s.chamberTarget=chamberTarget; s.tolerance=tolerance;
        s.stableSeconds=stableSeconds; s.holdMs=holdMs; s.finishCondition=finishCondition;
        s.probeTarget=probeTarget; s.probeActivation=probeActivation; s.stopAfter=stopAfter;
        return s;
    }

    public boolean usesTime() {
        return finishCondition==FinishCondition.TIME || finishCondition==FinishCondition.TIME_OR_K ||
               finishCondition==FinishCondition.TIME_OR_T || finishCondition==FinishCondition.TIME_AND_K ||
               finishCondition==FinishCondition.TIME_AND_T;
    }
    public boolean usesK() { return finishCondition==FinishCondition.PROBE_K || finishCondition==FinishCondition.TIME_OR_K || finishCondition==FinishCondition.TIME_AND_K; }
    public boolean usesT() { return finishCondition==FinishCondition.PROBE_T || finishCondition==FinishCondition.TIME_OR_T || finishCondition==FinishCondition.TIME_AND_T; }
}
