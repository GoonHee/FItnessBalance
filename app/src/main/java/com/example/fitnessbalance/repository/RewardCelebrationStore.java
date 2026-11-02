package com.example.fitnessbalance.repository;

import android.content.ContentValues;
import android.content.Context;
import android.database.sqlite.*;
import java.io.File;

/** Local, no-backup, account/session receipt. Claim before showing; never writes rewards. */
public final class RewardCelebrationStore {
    private RewardCelebrationStore(){}
    public static boolean claim(Context context,String uid,String session){
        if(uid==null||uid.isEmpty()||session==null||session.isEmpty())return false;
        SQLiteOpenHelper helper=new SQLiteOpenHelper(context.getApplicationContext(),new File(context.getNoBackupFilesDir(),"workout-celebrations.db").getAbsolutePath(),null,1){
            @Override public void onCreate(SQLiteDatabase db){db.execSQL("CREATE TABLE shown (uid TEXT NOT NULL, session TEXT NOT NULL, PRIMARY KEY(uid, session))");}
            @Override public void onUpgrade(SQLiteDatabase db,int oldVersion,int newVersion){}
        };
        try{ContentValues row=new ContentValues();row.put("uid",uid);row.put("session",session);
            return helper.getWritableDatabase().insertWithOnConflict("shown",null,row,SQLiteDatabase.CONFLICT_IGNORE)!=-1;
        }catch(RuntimeException unavailable){android.util.Log.w("RewardCelebration","Local celebration receipt unavailable");return false;}
        finally{helper.close();}
    }
}
