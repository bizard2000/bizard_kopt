package com.bizard.homesmokecore;

import org.junit.Test;
import static org.junit.Assert.*;

public class TelemetryParserTest {
    @Test public void parsesOriginalArduinoMapping() {
        Telemetry t=TelemetryParser.parse("|54.19|58.50|50|0|2|1|k50|10.00|0.02|1.00|6.00|45.06|end",123);
        assertEquals(54.19,t.chamber,0.001);
        assertEquals(58.50,t.probeK,0.001);
        assertEquals(45.06,t.probeT,0.001);
        assertEquals(50.0,t.chamberSetpoint,0.001);
        assertEquals(2.0,t.heaterPower,0.001);
        assertEquals(1,t.mode);
        assertEquals("k50",t.lastCommand);
        assertEquals(6.0,t.zP,0.001);
    }

    @Test(expected=IllegalArgumentException.class)
    public void rejectsIncompleteFrame(){TelemetryParser.parse("|40|41|end",0);}
}
