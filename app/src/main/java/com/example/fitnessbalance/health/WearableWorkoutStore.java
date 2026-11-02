package com.example.fitnessbalance.health;
import android.content.*;
import android.database.Cursor;
import android.database.sqlite.*;
import java.io.File;
import java.util.*;

/** Private no-backup cache, account isolated. A successful read atomically replaces its seven-day snapshot. */
public final class WearableWorkoutStore implements AutoCloseable {
    private final SQLiteOpenHelper helper;
    public WearableWorkoutStore(Context context){helper=new SQLiteOpenHelper(context.getApplicationContext(),new File(context.getNoBackupFilesDir(),"wearable-workouts.db").getAbsolutePath(),null,2){
        @Override public void onCreate(SQLiteDatabase db){db.execSQL("CREATE TABLE sessions (uid TEXT NOT NULL,id TEXT NOT NULL,source TEXT NOT NULL,activity TEXT NOT NULL,title TEXT NOT NULL,type INTEGER NOT NULL,start INTEGER NOT NULL,end INTEGER NOT NULL,modified INTEGER NOT NULL,activeCalories REAL,calorieState TEXT,PRIMARY KEY(uid,id))");db.execSQL("CREATE TABLE plans (uid TEXT PRIMARY KEY,type INTEGER NOT NULL,activity TEXT NOT NULL,startedAt INTEGER NOT NULL)");db.execSQL("CREATE TABLE syncs (uid TEXT PRIMARY KEY,windowStart INTEGER,windowEnd INTEGER,syncedAt INTEGER,attemptAt INTEGER,status TEXT)");}
        @Override public void onUpgrade(SQLiteDatabase db,int oldVersion,int newVersion){if(oldVersion<2){db.execSQL("ALTER TABLE sessions ADD COLUMN activeCalories REAL");db.execSQL("ALTER TABLE sessions ADD COLUMN calorieState TEXT");db.execSQL("CREATE TABLE plans (uid TEXT PRIMARY KEY,type INTEGER NOT NULL,activity TEXT NOT NULL,startedAt INTEGER NOT NULL)");}}
    };}
    public static final class Snapshot {public final Map<String,Object> meta;public final List<Map<String,Object>> records;Snapshot(Map<String,Object> meta,List<Map<String,Object>> records){this.meta=meta;this.records=records;}}
    public void clear(String uid){SQLiteDatabase db=helper.getWritableDatabase();db.beginTransaction();try{db.delete("sessions","uid=?",new String[]{uid});db.delete("syncs","uid=?",new String[]{uid});db.delete("plans","uid=?",new String[]{uid});db.setTransactionSuccessful();}finally{db.endTransaction();}}
    public void apply(String uid,Map<String,Object> snapshot){
        String state=String.valueOf(snapshot.get("state"));if("permission_required".equals(state)){clear(uid);return;}
        if(!"available".equals(state)){failed(uid);return;}
        List<Map<String,Object>> records=WearableWorkoutService.validate(snapshot);
        SQLiteDatabase db=helper.getWritableDatabase();db.beginTransaction();try{
            try(Cursor prior=db.query("syncs",new String[]{"windowEnd"},"uid=?",new String[]{uid},null,null,null)){
                if(prior.moveToFirst()&&!prior.isNull(0)&&prior.getLong(0)>WearableWorkoutService.number(snapshot.get("windowEnd"))){db.setTransactionSuccessful();return;}
            }
            db.delete("sessions","uid=?",new String[]{uid});
            for(Map<String,Object> row:records){ContentValues v=new ContentValues();v.put("uid",uid);for(String key:Arrays.asList("id","source","activity","title"))v.put(key,(String)row.get(key));for(String key:Arrays.asList("type","start","end","modified"))v.put(key,WearableWorkoutService.number(row.get(key)));v.put("calorieState",(String)row.get("calorieState"));if(row.get("activeCalories") instanceof Number)v.put("activeCalories",((Number)row.get("activeCalories")).doubleValue());db.insertOrThrow("sessions",null,v);}
            ContentValues meta=new ContentValues();meta.put("uid",uid);for(String key:Arrays.asList("windowStart","windowEnd","syncedAt"))meta.put(key,WearableWorkoutService.number(snapshot.get(key)));meta.put("attemptAt",System.currentTimeMillis());meta.put("status","available");db.insertWithOnConflict("syncs",null,meta,SQLiteDatabase.CONFLICT_REPLACE);db.setTransactionSuccessful();
        }finally{db.endTransaction();}
    }
    public void failed(String uid){SQLiteDatabase db=helper.getWritableDatabase();db.beginTransaction();try{ContentValues initial=new ContentValues();initial.put("uid",uid);db.insertWithOnConflict("syncs",null,initial,SQLiteDatabase.CONFLICT_IGNORE);ContentValues v=new ContentValues();v.put("attemptAt",System.currentTimeMillis());v.put("status","error");db.update("syncs",v,"uid=?",new String[]{uid});db.setTransactionSuccessful();}finally{db.endTransaction();}}
    public Snapshot read(String uid){SQLiteDatabase db=helper.getWritableDatabase();db.beginTransaction();try{
        Map<String,Object> meta=new HashMap<>();List<Map<String,Object>> records=new ArrayList<>();
        try(Cursor c=db.query("syncs",null,"uid=?",new String[]{uid},null,null,null)){if(c.moveToFirst()){for(String key:Arrays.asList("windowStart","windowEnd","syncedAt","attemptAt")){int i=c.getColumnIndexOrThrow(key);if(!c.isNull(i))meta.put(key,c.getLong(i));}meta.put("status",c.getString(c.getColumnIndexOrThrow("status")));}}
        try(Cursor c=db.query("sessions",null,"uid=?",new String[]{uid},null,null,"start DESC,id ASC")){while(c.moveToNext()){Map<String,Object> row=new HashMap<>();for(String key:Arrays.asList("id","source","activity","title"))row.put(key,c.getString(c.getColumnIndexOrThrow(key)));for(String key:Arrays.asList("type","start","end","modified"))row.put(key,c.getLong(c.getColumnIndexOrThrow(key)));int k=c.getColumnIndexOrThrow("activeCalories");if(!c.isNull(k))row.put("activeCalories",c.getDouble(k));row.put("calorieState",c.getString(c.getColumnIndexOrThrow("calorieState")));records.add(row);}}
        db.setTransactionSuccessful();return new Snapshot(meta,records);
    }finally{db.endTransaction();}}
    public void startPlan(String uid,int type,String activity,long startedAt){ContentValues v=new ContentValues();v.put("uid",uid);v.put("type",type);v.put("activity",activity);v.put("startedAt",startedAt);helper.getWritableDatabase().insertWithOnConflict("plans",null,v,SQLiteDatabase.CONFLICT_REPLACE);}
    public Map<String,Object> plan(String uid){Map<String,Object> out=new HashMap<>();try(Cursor c=helper.getReadableDatabase().query("plans",null,"uid=?",new String[]{uid},null,null,null)){if(c.moveToFirst()){out.put("type",c.getLong(c.getColumnIndexOrThrow("type")));out.put("activity",c.getString(c.getColumnIndexOrThrow("activity")));out.put("startedAt",c.getLong(c.getColumnIndexOrThrow("startedAt")));}}return out;}
    public void cancelPlan(String uid){helper.getWritableDatabase().delete("plans","uid=?",new String[]{uid});}
    public void removeCalories(String uid){ContentValues v=new ContentValues();v.putNull("activeCalories");v.put("calorieState","permission_required");helper.getWritableDatabase().update("sessions",v,"uid=?",new String[]{uid});}
    @Override public void close(){helper.close();}
}
