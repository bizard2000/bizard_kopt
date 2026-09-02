package com.bizard.homesmokecore;

public final class ProtocolRules {
    private ProtocolRules(){}
    public static boolean isPercentOrChamberSetpoint(double v){return !Double.isNaN(v)&&!Double.isInfinite(v)&&v>=0&&v<=100&&Math.abs(v-Math.rint(v))<0.000001;}
    public static boolean isTemperatureThreshold(double v){return !Double.isNaN(v)&&!Double.isInfinite(v)&&v>=0&&v<=100;}
}
