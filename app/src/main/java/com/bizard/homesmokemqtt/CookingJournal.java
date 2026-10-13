package com.bizard.homesmokemqtt;

import android.content.Context;
import com.bizard.homesmokecore.Telemetry;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.io.FileInputStream;

/** Independent named manual cooking journal; does not send controller commands. */
final class CookingJournal {
    private final File file;
    private JSONObject session;
    CookingJournal(Context context) {
        file = new File(context.getFilesDir(), "manual_cooking_active.json");
        try { if(file.exists()) session=new JSONObject(read(file)); }
        catch(Exception ignored) { session=null; }
    }
    synchronized boolean isActive(){return session!=null;}
    synchronized JSONObject snapshot(){return session==null?null:new JSONObject(session.toString());}
    synchronized void start(String title,String firstStage,Telemetry t)throws Exception{
        if(isActive())throw new IllegalStateException("Приготовление уже записывается");
        session=new JSONObject().put("name",title).put("startedAt",System.currentTimeMillis()).put("stages",new JSONArray());
        begin(firstStage,t);save();
    }
    private JSONObject values(Telemetry t)throws Exception{
        JSONObject j=new JSONObject();
        if(t!=null){
            j.put("sampleAt",t.receivedAtMs);
            j.put("chamber",t.chamber);
            j.put("setpoint",t.chamberSetpoint);
            j.put("probeK",t.probeK);
            j.put("probeT",t.probeT);
        } else j.put("missingTelemetry",true);
        return j;
    }
    private void begin(String name,Telemetry t)throws Exception{
        session.getJSONArray("stages").put(new JSONObject().put("name",name).put("startedAt",System.currentTimeMillis()).put("start",values(t)).put("samples",new JSONArray()));
    }
    synchronized void sample(Telemetry t)throws Exception{
        if(session==null||t==null)return;
        JSONArray stages=session.getJSONArray("stages");
        JSONObject stage=stages.getJSONObject(stages.length()-1);
        JSONArray samples=stage.getJSONArray("samples");
        if(samples.length()>0&&t.receivedAtMs-samples.getJSONObject(samples.length()-1).optLong("sampleAt")<5000)return;
        samples.put(values(t));save();
    }
    private void closeStage(Telemetry t)throws Exception{
        JSONArray stages=session.getJSONArray("stages");
        JSONObject stage=stages.getJSONObject(stages.length()-1);
        stage.put("endedAt",System.currentTimeMillis()).put("end",values(t));
    }
    synchronized void next(String name,Telemetry t)throws Exception{
        if(session==null)throw new IllegalStateException("Нет активного приготовления");
        closeStage(t);begin(name,t);save();
    }
    synchronized File finish(Telemetry t)throws Exception{
        if(session==null)throw new IllegalStateException("Нет активного приготовления");
        closeStage(t);session.put("endedAt",System.currentTimeMillis());
        File dir=new File(file.getParentFile(),"cooking-journals");if(!dir.exists()&&!dir.mkdirs())throw new java.io.IOException("Cannot create journal directory");
        File output=new File(dir,"cooking_"+session.getLong("startedAt")+".json");
        write(output,session.toString(2));session=null;
        if(file.exists()&&!file.delete())throw new java.io.IOException("Cannot clear active journal");
        return output;
    }
    private static String read(File f)throws Exception{try(FileInputStream in=new FileInputStream(f)){byte[] bytes=new byte[(int)f.length()];int offset=0,n;while(offset<bytes.length&&(n=in.read(bytes,offset,bytes.length-offset))>0)offset+=n;return new String(bytes,0,offset,StandardCharsets.UTF_8);}}
    private void save()throws Exception{write(file,session.toString());}
    private static void write(File f,String s)throws Exception{
        File tmp=new File(f.getParentFile(),f.getName()+".tmp");
        try(FileOutputStream out=new FileOutputStream(tmp)){out.write(s.getBytes(StandardCharsets.UTF_8));out.getFD().sync();}
        if(!tmp.renameTo(f))throw new java.io.IOException("Cannot commit journal");
    }
}