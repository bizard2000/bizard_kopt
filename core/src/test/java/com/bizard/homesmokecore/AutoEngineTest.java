package com.bizard.homesmokecore;

import org.junit.Test;
import static org.junit.Assert.*;

public class AutoEngineTest {
    private static Telemetry t(double chamber,double k,double probeT,long now){
        return new Telemetry("",chamber,k,probeT,40,0,20,1,"",10,.02,1,6,now);
    }

    @Test public void holdAccumulatesOnlyInsideToleranceAfterStabilization(){
        AutoProgram p=new AutoProgram(); AutoStage s=p.stages.get(0);
        s.chamberTarget=40;s.tolerance=1;s.stableSeconds=2;s.holdMs=5000;s.finishCondition=AutoStage.FinishCondition.TIME;
        AutoEngine e=new AutoEngine(); e.start(p,0);
        e.onTelemetry(t(40,20,20,1000),1000);
        e.onTelemetry(t(40,20,20,3000),3000); // chamber ready
        e.onTelemetry(t(40,20,20,5000),5000);
        assertEquals(2000,e.getAccumulatedHoldMs());
        e.onTelemetry(t(35,20,20,8000),8000); // outside band: pause
        assertEquals(2000,e.getAccumulatedHoldMs());
        e.onTelemetry(t(40,20,20,9000),9000);
        e.onTelemetry(t(40,20,20,11000),11000); // stable again
        e.onTelemetry(t(40,20,20,14000),14000);
        assertEquals(AutoEngine.State.COMPLETED,e.getState());
    }

    @Test public void probeCanWaitUntilChamberReady(){
        AutoProgram p=new AutoProgram(); AutoStage s=p.stages.get(0);
        s.chamberTarget=40;s.tolerance=1;s.stableSeconds=2;s.finishCondition=AutoStage.FinishCondition.PROBE_K;
        s.probeTarget=30;s.probeActivation=AutoStage.ProbeActivation.AFTER_CHAMBER_READY;
        AutoEngine e=new AutoEngine(); e.start(p,0);
        e.onTelemetry(t(25,35,20,1000),1000);
        assertEquals(AutoEngine.State.RUNNING,e.getState());
        e.onTelemetry(t(40,35,20,2000),2000);
        e.onTelemetry(t(40,35,20,4000),4000);
        assertEquals(AutoEngine.State.COMPLETED,e.getState());
    }

    @Test public void movesAcrossDisabledStages(){
        AutoProgram p=new AutoProgram();
        p.stages.get(0).holdMs=0;p.stages.get(0).stableSeconds=0;p.stages.get(0).chamberTarget=40;
        p.stages.get(1).enabled=false;
        p.stages.get(2).enabled=true;p.stages.get(2).holdMs=0;p.stages.get(2).stableSeconds=0;p.stages.get(2).chamberTarget=60;
        AutoEngine e=new AutoEngine(); e.start(p,0);
        AutoEngine.Update u=e.onTelemetry(t(40,20,20,1000),1000);
        assertEquals(2,e.getStageIndex());
        assertTrue(u.commands.contains("k60"));
    }
}
