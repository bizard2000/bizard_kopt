package com.bizard.homesmokemqtt;

import com.bizard.homesmokecore.Telemetry;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** Telemetry-confirmed ACK manager. Bluetooth socket write alone is never treated as controller ACK. */
final class CommandAckManager {
    interface Listener { void onResult(String requestId,double value,boolean ok,String reason); }
    static final long TIMEOUT_MS=6000L;
    private static final class Pending { String id; double value; long created; Pending(String id,double value,long created){this.id=id;this.value=value;this.created=created;} }
    private final List<Pending> pending=new ArrayList<>();

    synchronized void track(String requestId,double value,long now){pending.add(new Pending(requestId,value,now));FieldTestRecorder.record("REMOTE_SETPOINT_TRACK","id="+requestId+" value="+value);}
    synchronized void onTelemetry(Telemetry t,long now,Listener listener){
        Iterator<Pending> it=pending.iterator();
        while(it.hasNext()){
            Pending p=it.next();
            if(Math.abs(t.chamberSetpoint-p.value)<0.01){it.remove();result(listener,p,true,"applied");}
            else if(now-p.created>=TIMEOUT_MS){it.remove();result(listener,p,false,"controller_ack_timeout");}
        }
    }
    synchronized void expire(long now,Listener listener){
        Iterator<Pending> it=pending.iterator();
        while(it.hasNext()){
            Pending p=it.next();
            if(now-p.created>=TIMEOUT_MS){it.remove();result(listener,p,false,"controller_ack_timeout");}
        }
    }
    synchronized int pendingCount(){return pending.size();}
    synchronized void failAll(String reason,Listener listener){for(Pending p:pending)result(listener,p,false,reason);pending.clear();}
    private static void result(Listener listener,Pending p,boolean ok,String reason){FieldTestRecorder.recordAck(p.id,p.value,ok,reason);listener.onResult(p.id,p.value,ok,reason);}
}
