package com.bizard.homesmokeremote;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.*;

public class SessionAnalyticsTest {
    private static TelemetryHistoryStore.Sample s(long ts,double camera,double setpoint,double k,double t,double heater){
        return new TelemetryHistoryStore.Sample(ts,camera,setpoint,k,t,heater);
    }

    @Test public void stablePidProducesHighStability(){
        SessionAnalytics.Result r=SessionAnalytics.analyze(Arrays.asList(
                s(0,59.5,60,30,29,30),s(5000,60.0,60,31,30,30),s(10000,60.4,60,32,31,30),s(15000,59.8,60,33,32,30)));
        assertTrue(r.available);
        assertEquals(100.0,r.stability1,0.01);
        assertEquals(100.0,r.stability2,0.01);
        assertEquals(30.0,r.averageHeater,0.01);
        assertEquals(0.4,r.maxOvershoot,0.01);
        assertEquals(3.0,r.probeKDelta,0.01);
        assertEquals(0,r.outageEpisodes);
    }

    @Test public void overshootIsDetected(){
        SessionAnalytics.Result r=SessionAnalytics.analyze(Arrays.asList(
                s(0,55,60,30,29,90),s(5000,60,60,31,30,70),s(10000,66.5,60,32,31,30),s(15000,62,60,33,32,20)));
        assertEquals(6.5,r.maxOvershoot,0.01);
        assertTrue(r.averageAbsoluteError>2.0);
        assertTrue(r.stability1<100.0);
    }

    @Test public void underheatNeverReportsPositiveOvershootOrTargetReach(){
        SessionAnalytics.Result r=SessionAnalytics.analyze(Arrays.asList(
                s(0,30,60,30,29,100),s(5000,40,60,31,30,100),s(10000,50,60,32,31,100),s(15000,55,60,33,32,100)));
        assertEquals(0.0,r.maxOvershoot,0.01);
        assertEquals(-1L,r.timeToFirstTargetMs);
        assertEquals(0.0,r.stability3,0.01);
    }

    @Test public void outagesAreExcludedFromAveragesAndCounted(){
        SessionAnalytics.Result r=SessionAnalytics.analyze(Arrays.asList(
                s(0,60,60,30,29,20),s(5000,60,60,30,29,20),s(25000,70,60,31,30,80),s(30000,70,60,31,30,80)));
        assertEquals(1,r.outageEpisodes);
        assertEquals(20000L,r.outageMs);
        assertEquals(10000L,r.validTelemetryMs);
        assertEquals(65.0,r.averageCamera,0.01);
    }

    @Test public void multipleOutagesAreCountedSeparately(){
        SessionAnalytics.Result r=SessionAnalytics.analyze(Arrays.asList(
                s(0,60,60,30,29,30),s(5000,60,60,30,29,30),s(25000,60,60,31,30,30),s(30000,60,60,31,30,30),s(50000,60,60,32,31,30)));
        assertEquals(2,r.outageEpisodes);
        assertEquals(40000L,r.outageMs);
    }

    @Test public void setpointChangesAreNotBlendedIntoStability(){
        SessionAnalytics.Result r=SessionAnalytics.analyze(Arrays.asList(
                s(0,60,60,30,29,25),s(5000,60.5,60,31,30,25),s(10000,64,65,32,31,40),s(15000,65,65,33,32,30),s(20000,69.5,70,34,33,40)));
        assertEquals(2,r.setpointChanges);
        assertEquals(100.0,r.stability1,0.01);
        assertEquals(0L,r.timeToFirstTargetMs);
    }

    @Test public void missingProbeAndSinglePointAreSafe(){
        SessionAnalytics.Result r=SessionAnalytics.analyze(Collections.singletonList(
                s(1000,60,60,35,Double.NaN,45)));
        assertTrue(r.available);
        assertEquals(60.0,r.averageCamera,0.01);
        assertEquals(45.0,r.averageHeater,0.01);
        assertTrue(Double.isNaN(r.probeTDelta));
        assertTrue(Double.isNaN(r.stability1));
        assertEquals(0L,r.timeToFirstTargetMs);
    }

    @Test public void absentSetpointDoesNotInventPidMetrics(){
        SessionAnalytics.Result r=SessionAnalytics.analyze(Arrays.asList(
                s(0,50,Double.NaN,30,29,20),s(5000,51,Double.NaN,31,30,20)));
        assertTrue(Double.isNaN(r.averageAbsoluteError));
        assertTrue(Double.isNaN(r.stability1));
        assertTrue(Double.isNaN(r.initialSetpoint));
        assertEquals(-1L,r.timeToFirstTargetMs);
    }

    @Test public void emptyInputIsUnavailable(){
        SessionAnalytics.Result r=SessionAnalytics.analyze(Collections.emptyList());
        assertFalse(r.available);
        assertEquals(0,r.samples);
    }
}
