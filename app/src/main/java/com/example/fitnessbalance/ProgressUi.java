package com.example.fitnessbalance;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.view.View;
import android.widget.*;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

final class ProgressUi {
    static final int INK=0xFF1B2638,BLUE=0xFF2F80ED,TEAL=0xFF28B4AE,PURPLE=0xFF8015A9,AMBER=0xFFFFB23F;
    static final int[] MEAL_COLORS={0xFF2544C5,0xFF19344D,0xFF0998E6,0xFF57B9F2,0xFF8793A1};
    private ProgressUi(){}
    static int dp(Activity a,int n){return (int)(n*a.getResources().getDisplayMetrics().density);}
    static LinearLayout column(Activity a){LinearLayout l=new LinearLayout(a);l.setOrientation(LinearLayout.VERTICAL);return l;}
    static TextView text(Activity a,LinearLayout parent,CharSequence text,int size,boolean bold){TextView v=new TextView(a);v.setText(text);v.setTextSize(size);v.setTextColor(INK);if(bold)v.setTypeface(null,Typeface.BOLD);v.setPadding(0,dp(a,7),0,dp(a,7));parent.addView(v);return v;}
    static Button button(Activity a,LinearLayout parent,int label,Runnable action){Button b=new Button(a);b.setText(label);b.setAllCaps(false);parent.addView(b);b.setOnClickListener(v->action.run());return b;}
    static LinearLayout card(Activity a,LinearLayout parent,CharSequence title){LinearLayout c=column(a);int p=dp(a,18);c.setPadding(p,p,p,p);GradientDrawable bg=new GradientDrawable();bg.setColor(Color.WHITE);bg.setCornerRadius(dp(a,20));c.setBackground(bg);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.bottomMargin=dp(a,16);parent.addView(c,lp);if(title!=null)text(a,c,title,22,true);return c;}
    static ProgressChartView chart(Activity a,LinearLayout parent,int height){ProgressChartView chart=new ProgressChartView(a);parent.addView(chart,new LinearLayout.LayoutParams(-1,dp(a,height)));return chart;}
    static String number(Activity a,Object value){return value==null?a.getString(R.string.fl_missing):FoodUi.number(value);}
    static void export(Activity a,Uri uri,String data){if(uri==null||data==null)return;new Thread(()->{
        boolean success=false;try(OutputStream out=a.getContentResolver().openOutputStream(uri)){if(out==null)throw new java.io.IOException();out.write(data.getBytes(StandardCharsets.UTF_8));success=true;}catch(Exception ignored){}
        final boolean ok=success;a.runOnUiThread(()->{if(!a.isDestroyed())Toast.makeText(a,ok?R.string.pr_exported:R.string.pr_export_error,Toast.LENGTH_LONG).show();});
    },"progress-export").start();}
}
