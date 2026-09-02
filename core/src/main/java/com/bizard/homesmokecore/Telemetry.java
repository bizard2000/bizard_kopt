package com.bizard.homesmokecore;

public final class Telemetry {
    public final String raw;
    public final double chamber;
    public final double probeK;
    public final double probeT;
    public final double chamberSetpoint;
    public final double productSetpoint;
    public final double heaterPower;
    public final int mode;
    public final String lastCommand;
    public final double kP;
    public final double kI;
    public final double kD;
    public final double zP;
    public final long receivedAtMs;

    public Telemetry(String raw,double chamber,double probeK,double probeT,double chamberSetpoint,
                     double productSetpoint,double heaterPower,int mode,String lastCommand,
                     double kP,double kI,double kD,double zP,long receivedAtMs) {
        this.raw=raw; this.chamber=chamber; this.probeK=probeK; this.probeT=probeT;
        this.chamberSetpoint=chamberSetpoint; this.productSetpoint=productSetpoint;
        this.heaterPower=heaterPower; this.mode=mode; this.lastCommand=lastCommand;
        this.kP=kP; this.kI=kI; this.kD=kD; this.zP=zP; this.receivedAtMs=receivedAtMs;
    }
}
