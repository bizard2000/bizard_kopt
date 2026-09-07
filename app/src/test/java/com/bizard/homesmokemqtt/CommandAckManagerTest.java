package com.bizard.homesmokemqtt;

import com.bizard.homesmokecore.Telemetry;
import org.junit.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.Assert.*;

public class CommandAckManagerTest {
    private static Telemetry telemetry(double setpoint){
        return new Telemetry("",40,20,20,setpoint,0,0,1,"",0,0,0,0,0);
    }

    @Test public void confirmsAppliedSetpointFromTelemetry(){
        CommandAckManager m=new CommandAckManager();
        List<String> results=new ArrayList<>();
        m.track("id-1",55,1000);
        m.onTelemetry(telemetry(55),2000,(id,value,ok,reason)->results.add(id+":"+ok+":"+reason));
        assertEquals(1,results.size());
        assertEquals("id-1:true:applied",results.get(0));
        assertEquals(0,m.pendingCount());
    }

    @Test public void expiresWithoutAnyTelemetry(){
        CommandAckManager m=new CommandAckManager();
        List<String> results=new ArrayList<>();
        m.track("id-2",60,1000);
        m.expire(1000+CommandAckManager.TIMEOUT_MS-1,(id,value,ok,reason)->results.add(reason));
        assertTrue(results.isEmpty());
        assertEquals(1,m.pendingCount());
        m.expire(1000+CommandAckManager.TIMEOUT_MS,(id,value,ok,reason)->results.add(reason));
        assertEquals(1,results.size());
        assertEquals("controller_ack_timeout",results.get(0));
        assertEquals(0,m.pendingCount());
    }

    @Test public void failAllClearsPendingCommands(){
        CommandAckManager m=new CommandAckManager();
        List<String> results=new ArrayList<>();
        m.track("a",30,0);
        m.track("b",40,0);
        m.failAll("bluetooth_disconnected",(id,value,ok,reason)->results.add(id+":"+reason));
        assertEquals(2,results.size());
        assertEquals(0,m.pendingCount());
    }
}
