package com.bizard.homesmokeremote;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

/** Local 24-hour cache of fresh HomeSmoke telemetry for charts. */
final class TelemetryHistoryStore extends SQLiteOpenHelper {
    private static final String DB_NAME="remote_telemetry_history.db";
    private static final int DB_VERSION=1;
    private static final String TABLE="samples";
    private static final long RETENTION_MS=24L*60L*60L*1000L;
    private static final long MIN_SAMPLE_INTERVAL_MS=5000L;

    private long lastInsertedAt=0L;
    private int insertsSincePrune=0;

    TelemetryHistoryStore(Context context){super(context,DB_NAME,null,DB_VERSION);}

    @Override public void onCreate(SQLiteDatabase db){
        db.execSQL("CREATE TABLE "+TABLE+" (ts INTEGER PRIMARY KEY, camera REAL, setpoint REAL, probe_k REAL, probe_t REAL, heater REAL)");
        db.execSQL("CREATE INDEX idx_samples_ts ON "+TABLE+"(ts)");
    }

    @Override public void onUpgrade(SQLiteDatabase db,int oldVersion,int newVersion){
        db.execSQL("DROP TABLE IF EXISTS "+TABLE);
        onCreate(db);
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
            prune(db,System.currentTimeMillis()-RETENTION_MS);
            insertsSincePrune=0;
        }
    }

    synchronized List<Sample> query(long from,long to,int maxPoints){
        ArrayList<Sample> all=new ArrayList<>();
        SQLiteDatabase db=getReadableDatabase();
        Cursor c=db.query(TABLE,new String[]{"ts","camera","setpoint","probe_k","probe_t","heater"},
                "ts>=? AND ts<=?",new String[]{String.valueOf(from),String.valueOf(to)},null,null,"ts ASC");
        try{
            while(c.moveToNext()){
                all.add(new Sample(c.getLong(0),readDouble(c,1),readDouble(c,2),readDouble(c,3),readDouble(c,4),readDouble(c,5)));
            }
        }finally{c.close();}
        if(maxPoints<=0||all.size()<=maxPoints)return all;
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

    synchronized long latestTimestamp(){
        SQLiteDatabase db=getReadableDatabase();
        Cursor c=db.rawQuery("SELECT MAX(ts) FROM "+TABLE,null);
        try{return c.moveToFirst()&&!c.isNull(0)?c.getLong(0):0L;}finally{c.close();}
    }

    private static void putDouble(ContentValues v,String key,double value){if(Double.isNaN(value)||Double.isInfinite(value))v.putNull(key);else v.put(key,value);}
    private static double readDouble(Cursor c,int column){return c.isNull(column)?Double.NaN:c.getDouble(column);}
    private static void prune(SQLiteDatabase db,long cutoff){db.delete(TABLE,"ts<?",new String[]{String.valueOf(cutoff)});}

    static final class Sample{
        final long ts;
        final double camera,setpoint,probeK,probeT,heater;
        Sample(long ts,double camera,double setpoint,double probeK,double probeT,double heater){
            this.ts=ts;this.camera=camera;this.setpoint=setpoint;this.probeK=probeK;this.probeT=probeT;this.heater=heater;
        }
    }
}
