package com.bizard.homesmokemqtt;

import android.content.Context;
import com.bizard.homesmokecore.AutoProgram;
import com.bizard.homesmokecore.Telemetry;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;

final class HistoryStore {
    static final class ChartPoint { final long ts; final double chamber,setpoint,k,t,power; ChartPoint(long ts,double chamber,double setpoint,double k,double t,double power){this.ts=ts;this.chamber=chamber;this.setpoint=setpoint;this.k=k;this.t=t;this.power=power;} }
    private final File dir;
    private File active;
    private long lastTelemetryWrite;

    HistoryStore(Context c){dir=new File(c.getFilesDir(),"history");if(!dir.exists())dir.mkdirs();}

    synchronized void start(AutoProgram p){
        String stamp=new SimpleDateFormat("yyyyMMdd_HHmmss",Locale.US).format(new Date());active=new File(dir,stamp+"_"+safeName(p==null?"Auto":p.name)+".csv");lastTelemetryWrite=0;
        append("kind;timestamp;stage;chamber;setpoint;probeK;probeT;power;message\n");event("START",0,p==null?"Auto":p.name);
    }
    synchronized void event(String kind,int stage,String message){if(active==null)return;append(kind+";"+System.currentTimeMillis()+";"+stage+";;;;;;\""+esc(message)+"\"\n");}
    synchronized void telemetry(Telemetry t,int stage,String message){if(active==null||t==null)return;if(t.receivedAtMs-lastTelemetryWrite<5000)return;lastTelemetryWrite=t.receivedAtMs;append("DATA;"+t.receivedAtMs+";"+stage+";"+t.chamber+";"+t.chamberSetpoint+";"+t.probeK+";"+t.probeT+";"+t.heaterPower+";\""+esc(message)+"\"\n");}
    synchronized void finish(String result){if(active==null)return;event("FINISH",0,result);active=null;}
    synchronized File activeFile(){return active;}

    synchronized List<File> list(){File[] fs=dir.listFiles((d,n)->n.endsWith(".csv"));List<File> out=new ArrayList<>();if(fs!=null)Collections.addAll(out,fs);out.sort((a,b)->Long.compare(b.lastModified(),a.lastModified()));return out;}

    List<ChartPoint> readPoints(File f,int max)throws Exception{
        List<ChartPoint> out=new ArrayList<>();try(BufferedReader r=new BufferedReader(new InputStreamReader(new FileInputStream(f),StandardCharsets.UTF_8))){String line;while((line=r.readLine())!=null){if(!line.startsWith("DATA;"))continue;String[] a=line.split(";",9);if(a.length<8)continue;try{out.add(new ChartPoint(Long.parseLong(a[1]),Double.parseDouble(a[3]),Double.parseDouble(a[4]),Double.parseDouble(a[5]),Double.parseDouble(a[6]),Double.parseDouble(a[7])));}catch(Exception ignored){}}}
        if(max>0&&out.size()>max){int step=Math.max(1,out.size()/max);List<ChartPoint> sampled=new ArrayList<>();for(int i=0;i<out.size();i+=step)sampled.add(out.get(i));if(sampled.get(sampled.size()-1)!=out.get(out.size()-1))sampled.add(out.get(out.size()-1));return sampled;}return out;
    }

    private void append(String s){try(FileOutputStream o=new FileOutputStream(active,true)){o.write(s.getBytes(StandardCharsets.UTF_8));}catch(Exception ignored){}}
    private static String safeName(String s){s=s==null?"Auto":s.replaceAll("[^A-Za-zА-Яа-я0-9._-]+","_");return s.length()>40?s.substring(0,40):s;}
    private static String esc(String s){return s==null?"":s.replace("\"","\"\"").replace("\n"," ").replace("\r"," ");}
}
