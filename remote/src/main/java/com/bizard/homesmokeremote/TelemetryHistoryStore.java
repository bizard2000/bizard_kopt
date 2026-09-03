package com.bizard.homesmokeremote;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Local 24-hour cache of fresh HomeSmoke telemetry for charts. */
final class TelemetryHistoryStore extends SQLiteOpenHelper {
    private static final String DB_NAME="remote_telemetry_history.db";
    private static final int DB_VERSION=2;
    private static final String TABLE="samples";
    private static final String BOUNDARIES="session_boundaries";
    private static final long RETENTION_MS=24L*60L*60L*1000L;
    private static final long MIN_SAMPLE_INTERVAL_MS=5000L;
    private static final long VISUAL_GAP_MS=30000L;
    private static final long DEFAULT_SESSION_SPLIT_MS=10L*60L*1000L;

    private long lastInsertedAt=0L;
    private int insertsSincePrune=0;

    TelemetryHistoryStore(Context context){super(context,DB_NAME,null,DB_VERSION);}

    @Override public void onCreate(SQLiteDatabase db){
        db.execSQL("CREATE TABLE "+TABLE+" (ts INTEGER PRIMARY KEY, camera REAL, setpoint REAL, probe_k REAL, probe_t REAL, heater REAL)");
        db.execSQL("CREATE INDEX idx_samples_ts ON "+TABLE+"(ts)");
        createBoundaryTable(db);
    }

    @Override public void onUpgrade(SQLiteDatabase db,int oldVersion,int newVersion){
        if(oldVersion<2){
            createBoundaryTable(db);
            return;
        }
        db.execSQL("DROP TABLE IF EXISTS "+TABLE);
        db.execSQL("DROP TABLE IF EXISTS "+BOUNDARIES);
        onCreate(db);
    }

    private static void createBoundaryTable(SQLiteDatabase db){
        db.execSQL("CREATE TABLE IF NOT EXISTS "+BOUNDARIES+" (ts INTEGER PRIMARY KEY)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_session_boundaries_ts ON "+BOUNDARIES+"(ts)");
    }

    synchronized void addFresh(Sample sample){
        if(sample==null||sample.ts<=0)return;
        if(lastInsertedAt>0&&sample.ts-lastInsertedAt<MIN_SAMPLE_INTERVAL_MS)return;
        SQLiteDatabase db=getWritableDatabase();
        ContentValues v=new ContentValues();
        v.put("ts",sample.ts);
        putDouble(v,"camera",sample.camera);
        putDouble(v,"setpoint",sample.setpoint);
        putDouble(v,"probe_k",sample.probeK);
        putDouble(v,"probe_t",sample.probeT);
        putDouble(v,"heater",sample.heater);
        db.insertWithOnConflict(TABLE,null,v,SQLiteDatabase.CONFLICT_REPLACE);
        lastInsertedAt=sample.ts;
        insertsSincePrune++;
        if(insertsSincePrune>=60){
            long cutoff=System.currentTimeMillis()-RETENTION_MS;
            prune(db,cutoff);
            pruneBoundaries(db,cutoff);
            insertsSincePrune=0;
        }
    }

    /** Explicit local boundary, used for test runs and other locally known session starts. */
    synchronized void markSessionBoundary(long ts){
        if(ts<=0)return;
        SQLiteDatabase db=getWritableDatabase();
        ContentValues v=new ContentValues();v.put("ts",ts);
        db.insertWithOnConflict(BOUNDARIES,null,v,SQLiteDatabase.CONFLICT_IGNORE);
        pruneBoundaries(db,System.currentTimeMillis()-RETENTION_MS);
    }

    synchronized List<Sample> query(long from,long to,int maxPoints){
        ArrayList<Sample> all=new ArrayList<>();
        SQLiteDatabase db=getReadableDatabase();
        List<Long> boundaries=queryBoundaries(db,from,to);
        int boundaryIndex=0;
        long prevTs=0L;
        int segmentId=0,sessionId=0;
        Cursor c=db.query(TABLE,new String[]{"ts","camera","setpoint","probe_k","probe_t","heater"},
                "ts>=? AND ts<=?",new String[]{String.valueOf(from),String.valueOf(to)},null,null,"ts ASC");
        try{
            while(c.moveToNext()){
                long ts=c.getLong(0);
                boolean explicitBoundary=false;
                while(boundaryIndex<boundaries.size()&&boundaries.get(boundaryIndex)<=ts){
                    long b=boundaries.get(boundaryIndex++);
                    if(prevTs>0&&b>prevTs)explicitBoundary=true;
                }
                if(prevTs>0){
                    long gap=ts-prevTs;
                    if(explicitBoundary||gap>VISUAL_GAP_MS)segmentId++;
                    if(explicitBoundary||gap>DEFAULT_SESSION_SPLIT_MS)sessionId++;
                }
                all.add(new Sample(ts,readDouble(c,1),readDouble(c,2),readDouble(c,3),readDouble(c,4),readDouble(c,5),segmentId,sessionId));
                prevTs=ts;
            }
        }finally{c.close();}
        if(maxPoints<=0||all.size()<=maxPoints)return all;
        if(maxPoints==1)return Collections.singletonList(all.get(all.size()-1));
        ArrayList<Sample> out=new ArrayList<>(maxPoints);
        double step=(all.size()-1.0)/(maxPoints-1.0);
        int last=-1;
        for(int i=0;i<maxPoints;i++){
            int idx=(int)Math.round(i*step);
            if(idx==last)continue;
            out.add(all.get(idx));
            last=idx;
        }
        if(out.isEmpty()||out.get(out.size()-1).ts!=all.get(all.size()-1).ts)out.add(all.get(all.size()-1));
        return out;
    }

    synchronized int countSamples(long from,long to){
        SQLiteDatabase db=getReadableDatabase();
        Cursor c=db.rawQuery("SELECT COUNT(*) FROM "+TABLE+" WHERE ts>=? AND ts<=?",
                new String[]{String.valueOf(from),String.valueOf(to)});
        try{return c.moveToFirst()?c.getInt(0):0;}finally{c.close();}
    }

    synchronized long latestTimestamp(){
        SQLiteDatabase db=getReadableDatabase();
        Cursor c=db.rawQuery("SELECT MAX(ts) FROM "+TABLE,null);
        try{return c.moveToFirst()&&!c.isNull(0)?c.getLong(0):0L;}finally{c.close();}
    }

    /** Returns the first sample of the latest logical session, surviving app restarts. */
    synchronized long latestSessionStart(long to,long splitMs){
        SQLiteDatabase db=getReadableDatabase();
        long from=Math.max(0L,to-RETENTION_MS);
        List<Long> boundaries=queryBoundaries(db,from,to);
        int boundaryIndex=0;
        long prevTs=0L,start=0L;
        Cursor c=db.query(TABLE,new String[]{"ts"},"ts>=? AND ts<=?",new String[]{String.valueOf(from),String.valueOf(to)},null,null,"ts ASC");
        try{
            while(c.moveToNext()){
                long ts=c.getLong(0);
                boolean explicitBoundary=false;
                while(boundaryIndex<boundaries.size()&&boundaries.get(boundaryIndex)<=ts){
                    long b=boundaries.get(boundaryIndex++);
                    if(prevTs>0&&b>prevTs)explicitBoundary=true;
                }
                if(start==0L||prevTs==0L||explicitBoundary||ts-prevTs>Math.max(1L,splitMs))start=ts;
                prevTs=ts;
            }
        }finally{c.close();}
        return start;
    }

    /** Counts logical sessions in a selected graph range; short telemetry outages remain one session. */
    synchronized int countSessions(long from,long to,long splitMs){
        SQLiteDatabase db=getReadableDatabase();
        List<Long> boundaries=queryBoundaries(db,from,to);
        int boundaryIndex=0,count=0;
        long prevTs=0L;
        Cursor c=db.query(TABLE,new String[]{"ts"},"ts>=? AND ts<=?",new String[]{String.valueOf(from),String.valueOf(to)},null,null,"ts ASC");
        try{
            while(c.moveToNext()){
                long ts=c.getLong(0);
                boolean explicitBoundary=false;
                while(boundaryIndex<boundaries.size()&&boundaries.get(boundaryIndex)<=ts){
                    long b=boundaries.get(boundaryIndex++);
                    if(prevTs>0&&b>prevTs)explicitBoundary=true;
                }
                if(prevTs==0L)count=1;
                else if(explicitBoundary||ts-prevTs>Math.max(1L,splitMs))count++;
                prevTs=ts;
            }
        }finally{c.close();}
        return count;
    }

    private static List<Long> queryBoundaries(SQLiteDatabase db,long from,long to){
        ArrayList<Long> out=new ArrayList<>();
        Cursor c=db.query(BOUNDARIES,new String[]{"ts"},"ts>=? AND ts<=?",new String[]{String.valueOf(from),String.valueOf(to)},null,null,"ts ASC");
        try{while(c.moveToNext())out.add(c.getLong(0));}finally{c.close();}
        return out;
    }

    private static void putDouble(ContentValues v,String key,double value){if(Double.isNaN(value)||Double.isInfinite(value))v.putNull(key);else v.put(key,value);}
    private static double readDouble(Cursor c,int column){return c.isNull(column)?Double.NaN:c.getDouble(column);}
    private static void prune(SQLiteDatabase db,long cutoff){db.delete(TABLE,"ts<?",new String[]{String.valueOf(cutoff)});}
    private static void pruneBoundaries(SQLiteDatabase db,long cutoff){db.delete(BOUNDARIES,"ts<?",new String[]{String.valueOf(cutoff)});}

    static final class Sample{
        final long ts;
        final double camera,setpoint,probeK,probeT,heater;
        final int segmentId,sessionId;
        Sample(long ts,double camera,double setpoint,double probeK,double probeT,double heater){this(ts,camera,setpoint,probeK,probeT,heater,0,0);}
        Sample(long ts,double camera,double setpoint,double probeK,double probeT,double heater,int segmentId,int sessionId){
            this.ts=ts;this.camera=camera;this.setpoint=setpoint;this.probeK=probeK;this.probeT=probeT;this.heater=heater;this.segmentId=segmentId;this.sessionId=sessionId;
        }
    }
}
