package com.bizard.homesmokemqtt;

import com.bizard.homesmokecore.Telemetry;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** Telemetry-confirmed ACK manager. Bluetooth socket write alone is never treated as controller ACK. */
final class CommandAckManager {
    interface Listener { void onResult(String requestId,double value,boolean ok,String reason); }
    private static final long TIMEOUT_MS=6000L;
    private static final class Pending { String id; double value; long created; Pending(String id,double value,long created){this.id=id;this.value=value;this.created=created;} }
    private final List<Pending> pending=new ArrayList<>();

    synchronized void track(String requestId,double value,long now){pending.add(new Pending(requestId,value,now));}
    synchronized void onTelemetry(Telemetry t,long now,Listener listener){
        Iterator<Pending> it=pending.iterator();
        while(it.hasNext()){
            Pending p=it.next();
            if(Math.abs(t.chamberSetpoint-p.value)<0.01){it.remove();listener.onResult(p.id,p.value,true,"applied");}
            else if(now-p.created>=TIMEOUT_MS){it.remove();listener.onResult(p.id,p.value,false,"controller_ack_timeout");}
        }
    }
    synchronized void failAll(String reason,Listener listener){for(Pending p:pending)listener.onResult(p.id,p.value,false,reason);pending.clear();}
}
