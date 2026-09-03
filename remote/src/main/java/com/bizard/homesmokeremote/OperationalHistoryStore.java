package com.bizard.homesmokeremote;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Persistent local sessions and operational event journal. No protocol data is changed. */
final class OperationalHistoryStore extends SQLiteOpenHelper {
    private static final String DB_NAME="remote_operational_history.db";
    private static final int DB_VERSION=1;
    private static final long OUTAGE_THRESHOLD_MS=15000L;
    private static final long SESSION_SPLIT_MS=10L*60L*1000L;
    private static final int MAX_EVENTS=500;

    OperationalHistoryStore(Context context){super(context,DB_NAME,null,DB_VERSION);}

    @Override public void onCreate(SQLiteDatabase db){
        db.execSQL("CREATE TABLE sessions (id INTEGER PRIMARY KEY AUTOINCREMENT, start_ts INTEGER NOT NULL, end_ts INTEGER NOT NULL DEFAULT 0, last_ts INTEGER NOT NULL, samples INTEGER NOT NULL DEFAULT 0, camera_min REAL, camera_max REAL, k_min REAL, k_max REAL, t_min REAL, t_max REAL, heater_max REAL, outage_ms INTEGER NOT NULL DEFAULT 0)");
        db.execSQL("CREATE INDEX idx_sessions_start ON sessions(start_ts DESC)");
        db.execSQL("CREATE TABLE events (id INTEGER PRIMARY KEY AUTOINCREMENT, ts INTEGER NOT NULL, type TEXT NOT NULL, message TEXT NOT NULL)");
        db.execSQL("CREATE INDEX idx_events_ts ON events(ts DESC)");
        db.execSQL("CREATE TABLE meta (k TEXT PRIMARY KEY, v TEXT)");
    }

    @Override public void onUpgrade(SQLiteDatabase db,int oldVersion,int newVersion){
        db.execSQL("DROP TABLE IF EXISTS sessions");
        db.execSQL("DROP TABLE IF EXISTS events");
        db.execSQL("DROP TABLE IF EXISTS meta");
        onCreate(db);
    }

    synchronized long getLastProcessedTs(){
        SQLiteDatabase db=getReadableDatabase();
        Cursor c=db.query("meta",new String[]{"v"},"k=?",new String[]{"last_sample_ts"},null,null,null);
        try{
            if(!c.moveToFirst())return 0L;
            try{return Long.parseLong(c.getString(0));}catch(Exception ignored){return 0L;}
        }finally{c.close();}
    }

    synchronized List<Transition> processSamples(List<TelemetryHistoryStore.Sample> samples){
        ArrayList<Transition> transitions=new ArrayList<>();
        if(samples==null||samples.isEmpty())return transitions;
        SQLiteDatabase db=getWritableDatabase();
        long processed=getLastProcessedTs();
        Session active=getActiveSession(db);
        db.beginTransaction();
        try{
            for(TelemetryHistoryStore.Sample s:samples){
                if(s==null||s.ts<=processed)continue;
                if(active==null){
                    active=startSession(db,s.ts);
                    addEventInternal(db,s.ts,"session","Сеанс начат");
                    transitions.add(new Transition(Transition.START,active));
                }else{
                    long gap=s.ts-active.lastTs;
                    if(gap>SESSION_SPLIT_MS){
                        Session finished=closeSession(db,active,active.lastTs);
                        addEventInternal(db,active.lastTs,"session","Сеанс завершён · "+duration(finished.durationMs()));
                        transitions.add(new Transition(Transition.END,finished));
                        active=startSession(db,s.ts);
                        addEventInternal(db,s.ts,"session","Сеанс начат");
                        transitions.add(new Transition(Transition.START,active));
                    }else if(gap>OUTAGE_THRESHOLD_MS){
                        active.outageMs+=gap;
                    }
                }
                active=updateSession(db,active,s);
                processed=s.ts;
            }
            putMeta(db,"last_sample_ts",String.valueOf(processed));
            db.setTransactionSuccessful();
        }finally{db.endTransaction();}
        trimEvents(db);
        return transitions;
    }

    synchronized Transition closeIfInactive(long now){
        SQLiteDatabase db=getWritableDatabase();
        Session active=getActiveSession(db);
        if(active==null||now-active.lastTs<=SESSION_SPLIT_MS)return null;
        Session finished=closeSession(db,active,active.lastTs);
        addEventInternal(db,active.lastTs,"session","Сеанс завершён · "+duration(finished.durationMs()));
        trimEvents(db);
        return new Transition(Transition.END,finished);
    }

    synchronized void addEvent(long ts,String type,String message){
        if(message==null||message.trim().isEmpty())return;
        SQLiteDatabase db=getWritableDatabase();
        addEventInternal(db,ts>0?ts:System.currentTimeMillis(),type==null?"info":type,message.trim());
        trimEvents(db);
    }

    synchronized List<Session> querySessions(int limit){
        ArrayList<Session> out=new ArrayList<>();
        SQLiteDatabase db=getReadableDatabase();
        Cursor c=db.query("sessions",null,null,null,null,null,"start_ts DESC",String.valueOf(Math.max(1,limit)));
        try{while(c.moveToNext())out.add(readSession(c));}finally{c.close();}
        return out;
    }

    synchronized Session querySession(long id){
        if(id<=0)return null;
        SQLiteDatabase db=getReadableDatabase();
        Cursor c=db.query("sessions",null,"id=?",new String[]{String.valueOf(id)},null,null,null,"1");
        try{return c.moveToFirst()?readSession(c):null;}finally{c.close();}
    }

    synchronized List<Event> queryEvents(int limit){
        ArrayList<Event> out=new ArrayList<>();
        SQLiteDatabase db=getReadableDatabase();
        Cursor c=db.query("events",new String[]{"id","ts","type","message"},null,null,null,null,"ts DESC",String.valueOf(Math.max(1,limit)));
        try{while(c.moveToNext())out.add(new Event(c.getLong(0),c.getLong(1),c.getString(2),c.getString(3)));}finally{c.close();}
        return out;
    }

    synchronized List<Event> queryEvents(long from,long to,int limit){
        ArrayList<Event> out=new ArrayList<>();
        SQLiteDatabase db=getReadableDatabase();
        Cursor c=db.query("events",new String[]{"id","ts","type","message"},"ts>=? AND ts<=?",
                new String[]{String.valueOf(Math.max(0L,from)),String.valueOf(Math.max(from,to))},null,null,"ts DESC",String.valueOf(Math.max(1,limit)));
        try{while(c.moveToNext())out.add(new Event(c.getLong(0),c.getLong(1),c.getString(2),c.getString(3)));}finally{c.close();}
        return out;
    }

    synchronized void clearEvents(){getWritableDatabase().delete("events",null,null);}

    private Session startSession(SQLiteDatabase db,long ts){
        ContentValues v=new ContentValues();
        v.put("start_ts",ts);v.put("end_ts",0);v.put("last_ts",ts);v.put("samples",0);v.put("outage_ms",0);
        long id=db.insertOrThrow("sessions",null,v);
        return new Session(id,ts,0,ts,0,Double.NaN,Double.NaN,Double.NaN,Double.NaN,Double.NaN,Double.NaN,Double.NaN,0);
    }

    private Session updateSession(SQLiteDatabase db,Session x,TelemetryHistoryStore.Sample s){
        x.lastTs=s.ts;x.samples++;
        x.cameraMin=min(x.cameraMin,s.camera);x.cameraMax=max(x.cameraMax,s.camera);
        x.kMin=min(x.kMin,s.probeK);x.kMax=max(x.kMax,s.probeK);
        x.tMin=min(x.tMin,s.probeT);x.tMax=max(x.tMax,s.probeT);
        x.heaterMax=max(x.heaterMax,s.heater);
        ContentValues v=new ContentValues();
        v.put("last_ts",x.lastTs);v.put("samples",x.samples);v.put("outage_ms",x.outageMs);
        put(v,"camera_min",x.cameraMin);put(v,"camera_max",x.cameraMax);put(v,"k_min",x.kMin);put(v,"k_max",x.kMax);put(v,"t_min",x.tMin);put(v,"t_max",x.tMax);put(v,"heater_max",x.heaterMax);
        db.update("sessions",v,"id=?",new String[]{String.valueOf(x.id)});
        return x;
    }

    private Session closeSession(SQLiteDatabase db,Session x,long endTs){
        x.endTs=Math.max(x.startTs,endTs);
        ContentValues v=new ContentValues();v.put("end_ts",x.endTs);
        db.update("sessions",v,"id=?",new String[]{String.valueOf(x.id)});
        return x;
    }

    private Session getActiveSession(SQLiteDatabase db){
        Cursor c=db.query("sessions",null,"end_ts=0",null,null,null,"id DESC","1");
        try{return c.moveToFirst()?readSession(c):null;}finally{c.close();}
    }

    private static Session readSession(Cursor c){
        return new Session(
                c.getLong(c.getColumnIndexOrThrow("id")),c.getLong(c.getColumnIndexOrThrow("start_ts")),c.getLong(c.getColumnIndexOrThrow("end_ts")),c.getLong(c.getColumnIndexOrThrow("last_ts")),c.getInt(c.getColumnIndexOrThrow("samples")),
                d(c,"camera_min"),d(c,"camera_max"),d(c,"k_min"),d(c,"k_max"),d(c,"t_min"),d(c,"t_max"),d(c,"heater_max"),c.getLong(c.getColumnIndexOrThrow("outage_ms")));
    }

    private static double d(Cursor c,String name){int i=c.getColumnIndexOrThrow(name);return c.isNull(i)?Double.NaN:c.getDouble(i);}
    private static double min(double a,double b){if(Double.isNaN(b)||Double.isInfinite(b))return a;return Double.isNaN(a)?b:Math.min(a,b);}
    private static double max(double a,double b){if(Double.isNaN(b)||Double.isInfinite(b))return a;return Double.isNaN(a)?b:Math.max(a,b);}
    private static void put(ContentValues v,String k,double x){if(Double.isNaN(x)||Double.isInfinite(x))v.putNull(k);else v.put(k,x);}

    private static void putMeta(SQLiteDatabase db,String k,String v){ContentValues cv=new ContentValues();cv.put("k",k);cv.put("v",v);db.insertWithOnConflict("meta",null,cv,SQLiteDatabase.CONFLICT_REPLACE);}
    private static void addEventInternal(SQLiteDatabase db,long ts,String type,String message){ContentValues v=new ContentValues();v.put("ts",ts);v.put("type",type);v.put("message",message);db.insert("events",null,v);}
    private static void trimEvents(SQLiteDatabase db){db.execSQL("DELETE FROM events WHERE id NOT IN (SELECT id FROM events ORDER BY ts DESC LIMIT "+MAX_EVENTS+")");}
    private static String duration(long ms){long min=Math.max(0,ms/60000L);long h=min/60,m=min%60;return h>0?h+" ч "+m+" мин":m+" мин";}

    static final class Session{
        final long id,startTs;long endTs,lastTs;int samples;double cameraMin,cameraMax,kMin,kMax,tMin,tMax,heaterMax;long outageMs;
        Session(long id,long startTs,long endTs,long lastTs,int samples,double cameraMin,double cameraMax,double kMin,double kMax,double tMin,double tMax,double heaterMax,long outageMs){this.id=id;this.startTs=startTs;this.endTs=endTs;this.lastTs=lastTs;this.samples=samples;this.cameraMin=cameraMin;this.cameraMax=cameraMax;this.kMin=kMin;this.kMax=kMax;this.tMin=tMin;this.tMax=tMax;this.heaterMax=heaterMax;this.outageMs=outageMs;}
        long effectiveEnd(){return endTs>0?endTs:lastTs;}
        long durationMs(){return Math.max(0,effectiveEnd()-startTs);}
        boolean active(){return endTs==0;}
        String title(){return new SimpleDateFormat("dd.MM.yyyy · HH:mm",Locale.getDefault()).format(new Date(startTs));}
    }

    static final class Event{final long id,ts;final String type,message;Event(long id,long ts,String type,String message){this.id=id;this.ts=ts;this.type=type;this.message=message;}}
    static final class Transition{static final int START=1,END=2;final int kind;final Session session;Transition(int kind,Session session){this.kind=kind;this.session=session;}}
}
