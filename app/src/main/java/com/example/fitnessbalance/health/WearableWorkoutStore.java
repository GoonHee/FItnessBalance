package com.example.fitnessbalance.health;
import android.content.*;
import android.database.Cursor;
import android.database.sqlite.*;
import java.io.File;
import java.util.*;

/** Private no-backup cache, account isolated. A successful read atomically replaces its seven-day snapshot. */
public final class WearableWorkoutStore implements AutoCloseable {
    private final SQLiteOpenHelper helper;
    public WearableWorkoutStore(Context context){helper=new SQLiteOpenHelper(context.getApplicationContext(),new File(context.getNoBackupFilesDir(),"wearable-workouts.db").getAbsolutePath(),null,1){
        @Override public void onCreate(SQLiteDatabase db){db.execSQL("CREATE TABLE sessions (uid TEXT NOT NULL,id TEXT NOT NULL,source TEXT NOT NULL,activity TEXT NOT NULL,title TEXT NOT NULL,type INTEGER NOT NULL,start INTEGER NOT NULL,end INTEGER NOT NULL,modified INTEGER NOT NULL,PRIMARY KEY(uid,id))");db.execSQL("CREATE TABLE syncs (uid TEXT PRIMARY KEY,windowStart INTEGER,windowEnd INTEGER,syncedAt INTEGER,attemptAt INTEGER,status TEXT)");}
        @Override public void onUpgrade(SQLiteDatabase db,int oldVersion,int newVersion){}
    };}
    public static final class Snapshot {public final Map<String,Object> meta;public final List<Map<String,Object>> records;Snapshot(Map<String,Object> meta,List<Map<String,Object>> records){this.meta=meta;this.records=records;}}
    public void clear(String uid){SQLiteDatabase db=helper.getWritableDatabase();db.beginTransaction();try{db.delete("sessions","uid=?",new String[]{uid});db.delete("syncs","uid=?",new String[]{uid});db.setTransactionSuccessful();}finally{db.endTransaction();}}
    public void apply(String uid,Map<String,Object> snapshot){
        String state=String.valueOf(snapshot.get("state"));if("permission_required".equals(state)){clear(uid);return;}
        if(!"available".equals(state)){failed(uid);return;}
        List<Map<String,Object>> records=WearableWorkoutService.validate(snapshot);
        SQLiteDatabase db=helper.getWritableDatabase();db.beginTransaction();try{
            try(Cursor prior=db.query("syncs",new String[]{"windowEnd"},"uid=?",new String[]{uid},null,null,null)){
                if(prior.moveToFirst()&&!prior.isNull(0)&&prior.getLong(0)>WearableWorkoutService.number(snapshot.get("windowEnd"))){db.setTransactionSuccessful();return;}
            }
            db.delete("sessions","uid=?",new String[]{uid});
            for(Map<String,Object> row:records){ContentValues v=new ContentValues();v.put("uid",uid);for(String key:Arrays.asList("id","source","activity","title"))v.put(key,(String)row.get(key));for(String key:Arrays.asList("type","start","end","modified"))v.put(key,WearableWorkoutService.number(row.get(key)));db.insertOrThrow("sessions",null,v);}
            ContentValues meta=new ContentValues();meta.put("uid",uid);for(String key:Arrays.asList("windowStart","windowEnd","syncedAt"))meta.put(key,WearableWorkoutService.number(snapshot.get(key)));meta.put("attemptAt",System.currentTimeMillis());meta.put("status","available");db.insertWithOnConflict("syncs",null,meta,SQLiteDatabase.CONFLICT_REPLACE);db.setTransactionSuccessful();
        }finally{db.endTransaction();}
    }
    public void failed(String uid){SQLiteDatabase db=helper.getWritableDatabase();db.beginTransaction();try{ContentValues initial=new ContentValues();initial.put("uid",uid);db.insertWithOnConflict("syncs",null,initial,SQLiteDatabase.CONFLICT_IGNORE);ContentValues v=new ContentValues();v.put("attemptAt",System.currentTimeMillis());v.put("status","error");db.update("syncs",v,"uid=?",new String[]{uid});db.setTransactionSuccessful();}finally{db.endTransaction();}}
    public Snapshot read(String uid){SQLiteDatabase db=helper.getWritableDatabase();db.beginTransaction();try{
        Map<String,Object> meta=new HashMap<>();List<Map<String,Object>> records=new ArrayList<>();
        try(Cursor c=db.query("syncs",null,"uid=?",new String[]{uid},null,null,null)){if(c.moveToFirst()){for(String key:Arrays.asList("windowStart","windowEnd","syncedAt","attemptAt")){int i=c.getColumnIndexOrThrow(key);if(!c.isNull(i))meta.put(key,c.getLong(i));}meta.put("status",c.getString(c.getColumnIndexOrThrow("status")));}}
        try(Cursor c=db.query("sessions",null,"uid=?",new String[]{uid},null,null,"start DESC,id ASC")){while(c.moveToNext()){Map<String,Object> row=new HashMap<>();for(String key:Arrays.asList("id","source","activity","title"))row.put(key,c.getString(c.getColumnIndexOrThrow(key)));for(String key:Arrays.asList("type","start","end","modified"))row.put(key,c.getLong(c.getColumnIndexOrThrow(key)));records.add(row);}}
        db.setTransactionSuccessful();return new Snapshot(meta,records);
    }finally{db.endTransaction();}}
    @Override public void close(){helper.close();}
}
