package com.bizard.homesmokeremote;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** Pure local calculations over already stored Remote telemetry. No protocol changes. */
final class SessionAnalytics {
    static final long OUTAGE_THRESHOLD_MS=15000L;
    private static final double SETPOINT_CHANGE_EPS=0.5;

    static Result analyze(List<TelemetryHistoryStore.Sample> source){
        if(source==null||source.isEmpty())return Result.empty();
        ArrayList<TelemetryHistoryStore.Sample> samples=new ArrayList<>(source);
        Collections.sort(samples,Comparator.comparingLong(s->s.ts));

        int outages=0,setpointChanges=0;
        long outageMs=0L,validTelemetryMs=0L,cameraTime=0L,heaterTime=0L,errorTime=0L,band1Ms=0L,band2Ms=0L,band3Ms=0L;
        double cameraIntegral=0.0,heaterIntegral=0.0,errorIntegral=0.0,maxOvershoot=Double.NaN;
        double initialSetpoint=Double.NaN;
        long targetStartTs=0L,timeToFirstTargetMs=-1L;
        double firstK=Double.NaN,lastK=Double.NaN,firstT=Double.NaN,lastT=Double.NaN;
        int finiteCamera=0,finiteHeater=0,finiteError=0;
        double cameraPointSum=0.0,heaterPointSum=0.0,errorPointSum=0.0;

        TelemetryHistoryStore.Sample previous=null;
        for(TelemetryHistoryStore.Sample s:samples){
            if(finite(s.camera)){cameraPointSum+=s.camera;finiteCamera++;}
            if(finite(s.heater)){heaterPointSum+=s.heater;finiteHeater++;}
            if(finite(s.camera)&&validSetpoint(s.setpoint)){
                double err=Math.abs(s.camera-s.setpoint);errorPointSum+=err;finiteError++;
                double over=s.camera-s.setpoint;if(Double.isNaN(maxOvershoot)||over>maxOvershoot)maxOvershoot=over;
            }
            if(finite(s.probeK)){if(Double.isNaN(firstK))firstK=s.probeK;lastK=s.probeK;}
            if(finite(s.probeT)){if(Double.isNaN(firstT))firstT=s.probeT;lastT=s.probeT;}
            if(Double.isNaN(initialSetpoint)&&validSetpoint(s.setpoint)){initialSetpoint=s.setpoint;targetStartTs=s.ts;}
            if(timeToFirstTargetMs<0&&validSetpoint(initialSetpoint)&&finite(s.camera)&&validSetpoint(s.setpoint)&&Math.abs(s.setpoint-initialSetpoint)<=SETPOINT_CHANGE_EPS&&Math.abs(s.camera-initialSetpoint)<=1.0){timeToFirstTargetMs=Math.max(0L,s.ts-targetStartTs);}

            if(previous!=null){
                long dt=s.ts-previous.ts;
                if(dt>OUTAGE_THRESHOLD_MS){outages++;outageMs+=dt;}
                else if(dt>0){
                    validTelemetryMs+=dt;
                    if(finite(previous.camera)&&finite(s.camera)){
                        cameraIntegral+=((previous.camera+s.camera)/2.0)*dt;cameraTime+=dt;
                    }
                    if(finite(previous.heater)&&finite(s.heater)){
                        heaterIntegral+=((previous.heater+s.heater)/2.0)*dt;heaterTime+=dt;
                    }
                    boolean sameSetpoint=validSetpoint(previous.setpoint)&&validSetpoint(s.setpoint)&&Math.abs(previous.setpoint-s.setpoint)<=SETPOINT_CHANGE_EPS;
                    if(sameSetpoint&&finite(previous.camera)&&finite(s.camera)){
                        double e1=Math.abs(previous.camera-previous.setpoint),e2=Math.abs(s.camera-s.setpoint),em=(e1+e2)/2.0;
                        errorIntegral+=em*dt;errorTime+=dt;
                        if(em<=1.0)band1Ms+=dt;
                        if(em<=2.0)band2Ms+=dt;
                        if(em<=3.0)band3Ms+=dt;
                    }
                }
                if(validSetpoint(previous.setpoint)&&validSetpoint(s.setpoint)&&Math.abs(previous.setpoint-s.setpoint)>SETPOINT_CHANGE_EPS)setpointChanges++;
            }
            previous=s;
        }

        double avgCamera=cameraTime>0?cameraIntegral/cameraTime:(finiteCamera>0?cameraPointSum/finiteCamera:Double.NaN);
        double avgHeater=heaterTime>0?heaterIntegral/heaterTime:(finiteHeater>0?heaterPointSum/finiteHeater:Double.NaN);
        double avgAbsError=errorTime>0?errorIntegral/errorTime:(finiteError>0?errorPointSum/finiteError:Double.NaN);
        double stability1=errorTime>0?100.0*band1Ms/errorTime:Double.NaN;
        double stability2=errorTime>0?100.0*band2Ms/errorTime:Double.NaN;
        double stability3=errorTime>0?100.0*band3Ms/errorTime:Double.NaN;
        double probeKDelta=finite(firstK)&&finite(lastK)?lastK-firstK:Double.NaN;
        double probeTDelta=finite(firstT)&&finite(lastT)?lastT-firstT:Double.NaN;
        if(!Double.isNaN(maxOvershoot)&&maxOvershoot<0)maxOvershoot=0.0;

        return new Result(true,samples.size(),avgCamera,avgHeater,avgAbsError,maxOvershoot,stability1,stability2,stability3,
                initialSetpoint,timeToFirstTargetMs,setpointChanges,probeKDelta,probeTDelta,outages,outageMs,validTelemetryMs,errorTime);
    }

    private static boolean finite(double v){return !Double.isNaN(v)&&!Double.isInfinite(v);}
    private static boolean validSetpoint(double v){return finite(v)&&v>0.0;}

    static final class Result{
        final boolean available;
        final int samples;
        final double averageCamera,averageHeater,averageAbsoluteError,maxOvershoot,stability1,stability2,stability3,initialSetpoint,probeKDelta,probeTDelta;
        final long timeToFirstTargetMs,outageMs,validTelemetryMs,errorMetricMs;
        final int setpointChanges,outageEpisodes;

        Result(boolean available,int samples,double averageCamera,double averageHeater,double averageAbsoluteError,double maxOvershoot,
               double stability1,double stability2,double stability3,double initialSetpoint,long timeToFirstTargetMs,int setpointChanges,
               double probeKDelta,double probeTDelta,int outageEpisodes,long outageMs,long validTelemetryMs,long errorMetricMs){
            this.available=available;this.samples=samples;this.averageCamera=averageCamera;this.averageHeater=averageHeater;this.averageAbsoluteError=averageAbsoluteError;this.maxOvershoot=maxOvershoot;
            this.stability1=stability1;this.stability2=stability2;this.stability3=stability3;this.initialSetpoint=initialSetpoint;this.timeToFirstTargetMs=timeToFirstTargetMs;this.setpointChanges=setpointChanges;
            this.probeKDelta=probeKDelta;this.probeTDelta=probeTDelta;this.outageEpisodes=outageEpisodes;this.outageMs=outageMs;this.validTelemetryMs=validTelemetryMs;this.errorMetricMs=errorMetricMs;
        }

        static Result empty(){return new Result(false,0,Double.NaN,Double.NaN,Double.NaN,Double.NaN,Double.NaN,Double.NaN,Double.NaN,Double.NaN,-1L,0,Double.NaN,Double.NaN,0,0L,0L,0L);}
    }
}
