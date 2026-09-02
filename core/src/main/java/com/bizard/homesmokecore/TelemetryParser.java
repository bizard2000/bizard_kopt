package com.bizard.homesmokecore;

public final class TelemetryParser {
    private TelemetryParser() {}

    public static Telemetry parse(String frame,long nowMs) {
        if(frame==null) throw new IllegalArgumentException("frame is null");
        String raw=frame.trim();
        if(!raw.contains("end")) throw new IllegalArgumentException("incomplete frame");
        String[] a=raw.split("\\|",-1);
        // Original Arduino/AIA frame starts with '|': a[0] is empty.
        if(a.length<14 || !"end".equals(a[13].trim())) throw new IllegalArgumentException("bad frame fields="+a.length);
        return new Telemetry(raw,
                number(a[1]), number(a[2]), number(a[12]), number(a[3]), number(a[4]),
                number(a[5]), integer(a[6]), a[7].trim(), number(a[8]), number(a[9]),
                number(a[10]), number(a[11]), nowMs);
    }

    public static boolean isSensorValueValid(double v) {
        return !Double.isNaN(v) && !Double.isInfinite(v) && v>-40.0 && v<200.0;
    }

    private static double number(String s) {
        try { return Double.parseDouble(s.trim().replace(',','.')); }
        catch(Exception e) { return Double.NaN; }
    }

    private static int integer(String s) {
        try { return Integer.parseInt(s.trim()); }
        catch(Exception e) { return -1; }
    }
}
