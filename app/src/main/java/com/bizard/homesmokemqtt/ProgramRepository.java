package com.bizard.homesmokemqtt;

import android.content.Context;
import android.content.SharedPreferences;
import com.bizard.homesmokecore.AutoProgram;
import com.bizard.homesmokecore.AutoStage;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

final class ProgramRepository {
    private static final String PREF="homesmoke_full";
    private static final String KEY="auto_library_json_v2";
    private static final String OLD_KEY="auto_library_json_v1";
    private final SharedPreferences prefs;

    ProgramRepository(Context c){prefs=c.getSharedPreferences(PREF,Context.MODE_PRIVATE);}

    synchronized List<AutoProgram> load(){
        try {
            String raw=prefs.getString(KEY,"");
            if(raw!=null&&!raw.trim().isEmpty()) return decodeLibrary(raw);
            List<AutoProgram> migrated=migrateV1(); save(migrated); return migrated;
        } catch(Exception e){List<AutoProgram> x=new ArrayList<>();x.add(defaultProgram());return x;}
    }

    synchronized void save(List<AutoProgram> programs){
        try {prefs.edit().putString(KEY,encodeLibrary(programs)).apply();}catch(Exception ignored){}
    }

    synchronized String exportJson(List<AutoProgram> programs)throws Exception{return encodeLibrary(programs);}
    synchronized List<AutoProgram> importJson(String json)throws Exception{List<AutoProgram> p=decodeLibrary(json);if(p.isEmpty())throw new IllegalArgumentException("Нет программ");save(p);return p;}

    static AutoProgram defaultProgram(){
        AutoProgram p=new AutoProgram(); p.name="Моя программа";
        AutoStage s=p.stages.get(0);s.enabled=true;s.chamberTarget=40;s.tolerance=1;s.stableSeconds=20;s.holdMs=15L*60L*1000L;
        for(int i=1;i<4;i++)p.stages.get(i).enabled=false;
        return p;
    }

    private List<AutoProgram> migrateV1() throws Exception {
        String raw=prefs.getString(OLD_KEY,"");
        if(raw==null||raw.trim().isEmpty()){List<AutoProgram>x=new ArrayList<>();x.add(defaultProgram());return x;}
        JSONArray a=new JSONArray(raw); List<AutoProgram> out=new ArrayList<>();
        for(int i=0;i<a.length();i++){
            JSONObject o=a.optJSONObject(i); if(o==null)continue;
            AutoProgram p=new AutoProgram();p.name=o.optString("name","Программа "+(i+1));p.id=UUID.randomUUID().toString();p.stages.clear();
            JSONArray st=o.optJSONArray("stages");
            for(int j=0;j<4;j++){
                JSONObject x=st==null?null:st.optJSONObject(j);AutoStage s=new AutoStage();s.name="Этап "+(j+1);
                s.enabled=x!=null?x.optBoolean("enabled",j==0):j==0;
                s.chamberTarget=num(x,"temp",j==0?40:0);s.holdMs=(long)(num(x,"minutes",j==0?15:0)*60000.0);
                int c=x==null?0:x.optInt("condition",0);s.finishCondition=conditionFromOld(c);
                s.probeTarget=num(x,"probe",0);s.stopAfter=x!=null&&x.optBoolean("stop",false);
                s.tolerance=1.0;s.stableSeconds=20;s.probeActivation=AutoStage.ProbeActivation.AFTER_CHAMBER_READY;
                p.stages.add(s);
            }
            out.add(p);
        }
        if(out.isEmpty())out.add(defaultProgram());return out;
    }

    private static AutoStage.FinishCondition conditionFromOld(int c){switch(c){case 1:return AutoStage.FinishCondition.PROBE_K;case 2:return AutoStage.FinishCondition.PROBE_T;case 3:return AutoStage.FinishCondition.TIME_OR_K;case 4:return AutoStage.FinishCondition.TIME_OR_T;case 5:return AutoStage.FinishCondition.TIME_AND_K;case 6:return AutoStage.FinishCondition.TIME_AND_T;default:return AutoStage.FinishCondition.TIME;}}
    private static double num(JSONObject o,String key,double d){if(o==null)return d;try{return Double.parseDouble(o.optString(key,String.valueOf(d)).replace(',','.'));}catch(Exception e){return d;}}

    private static String encodeLibrary(List<AutoProgram> programs)throws Exception{
        JSONArray a=new JSONArray();for(AutoProgram p:programs)a.put(encodeProgram(p));return a.toString();
    }
    private static List<AutoProgram> decodeLibrary(String raw)throws Exception{
        JSONArray a=new JSONArray(raw);List<AutoProgram> out=new ArrayList<>();for(int i=0;i<a.length();i++){JSONObject o=a.optJSONObject(i);if(o!=null)out.add(decodeProgram(o));}return out;
    }
    private static JSONObject encodeProgram(AutoProgram p)throws Exception{
        JSONObject o=new JSONObject();o.put("v",2);o.put("id",p.id);o.put("name",p.name);o.put("description",p.description);o.put("modifiedAt",p.modifiedAt);JSONArray a=new JSONArray();
        for(AutoStage s:p.stages){JSONObject x=new JSONObject();x.put("name",s.name);x.put("enabled",s.enabled);x.put("chamberTarget",s.chamberTarget);x.put("tolerance",s.tolerance);x.put("stableSeconds",s.stableSeconds);x.put("holdMs",s.holdMs);x.put("finishCondition",s.finishCondition.name());x.put("probeTarget",s.probeTarget);x.put("probeActivation",s.probeActivation.name());x.put("stopAfter",s.stopAfter);a.put(x);}o.put("stages",a);return o;
    }
    private static AutoProgram decodeProgram(JSONObject o)throws Exception{
        AutoProgram p=new AutoProgram();p.id=o.optString("id",UUID.randomUUID().toString());p.name=o.optString("name","Программа");p.description=o.optString("description","");p.modifiedAt=o.optLong("modifiedAt",System.currentTimeMillis());p.stages.clear();JSONArray a=o.optJSONArray("stages");
        for(int i=0;i<4;i++){JSONObject x=a==null?null:a.optJSONObject(i);AutoStage s=new AutoStage();s.name=x==null?"Этап "+(i+1):x.optString("name","Этап "+(i+1));s.enabled=x!=null?x.optBoolean("enabled",i==0):i==0;s.chamberTarget=x==null?(i==0?40:0):x.optDouble("chamberTarget",0);s.tolerance=x==null?1:x.optDouble("tolerance",1);s.stableSeconds=x==null?20:x.optInt("stableSeconds",20);s.holdMs=x==null?(i==0?900000:0):x.optLong("holdMs",0);try{s.finishCondition=AutoStage.FinishCondition.valueOf(x.optString("finishCondition","TIME"));}catch(Exception e){s.finishCondition=AutoStage.FinishCondition.TIME;}s.probeTarget=x==null?0:x.optDouble("probeTarget",0);try{s.probeActivation=AutoStage.ProbeActivation.valueOf(x.optString("probeActivation","AFTER_CHAMBER_READY"));}catch(Exception e){s.probeActivation=AutoStage.ProbeActivation.AFTER_CHAMBER_READY;}s.stopAfter=x!=null&&x.optBoolean("stopAfter",false);p.stages.add(s);}return p;
    }
}
