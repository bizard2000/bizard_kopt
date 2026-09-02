package com.bizard.homesmokecore;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class AutoProgram {
    public String id=UUID.randomUUID().toString();
    public String name="Моя программа";
    public String description="";
    public long modifiedAt=System.currentTimeMillis();
    public final List<AutoStage> stages=new ArrayList<>();

    public AutoProgram() {
        for(int i=0;i<4;i++) { AutoStage s=new AutoStage(); s.name="Этап "+(i+1); s.enabled=(i==0); stages.add(s); }
    }

    public AutoProgram copy() {
        AutoProgram p=new AutoProgram(); p.stages.clear();
        p.id=UUID.randomUUID().toString(); p.name=name+" — копия"; p.description=description; p.modifiedAt=System.currentTimeMillis();
        for(AutoStage s:stages) p.stages.add(s.copy());
        return p;
    }
}
