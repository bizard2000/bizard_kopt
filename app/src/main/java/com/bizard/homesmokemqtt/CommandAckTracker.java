package com.bizard.homesmokemqtt;

import com.bizard.homesmokecore.Telemetry;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** Correlates commands with subsequent Arduino telemetry instead of treating socket write as ACK. */
final class CommandAckTracker {
    interface Listener { void onApplied(long id,String command,boolean ok,String reason); }
    private static final long TIMEOUT_MS=6000L;

    private static final class Pending {
        long id; String command; Double expectedSetpoint; long created;
        Pending(long id,String command,Double expectedSetpoint,long created){this.id=id;this.command=command;this.expectedSetpoint=expectedSetpoint;this.created=created;}
    }
    private final List<Pending> pending=new ArrayList<>();
    private long nextId=1;

    synchronized long trackSetpoint(double value,long now){long id=nextId++;pending.add(new Pending(id,"set_temp",value,now));return id;}

    synchronized void onTelemetry(Telemetry t,long now,Listener l){
        Iterator<Pending> it=pending.iterator();
        while(it.hasNext()){
            Pending p=it.next();
            if(p.expectedSetpoint!=null && Math.abs(t.chamberSetpoint-p.expectedSetpoint)<0.01){it.remove();l.onApplied(p.id,p.command,true,"applied");}
            else if(now-p.created>=TIMEOUT_MS){it.remove();l.onApplied(p.id,p.command,false,"controller_ack_timeout");}
        }
    }
    synchronized void failAll(String reason,Listener l){for(Pending p:pending)l.onApplied(p.id,p.command,false,reason);pending.clear();}
}
