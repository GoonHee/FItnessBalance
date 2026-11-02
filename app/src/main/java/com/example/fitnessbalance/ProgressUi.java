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
    static final int INK=0xFF241D3B,BLUE=0xFF8052CF,TEAL=0xFF149D91,PURPLE=0xFFB9578F,AMBER=0xFFDB951E;
    static final int[] MEAL_COLORS={0xFF8052CF,0xFF149D91,0xFFE58562,0xFFDB951E,0xFF8C8496};
    private ProgressUi(){}
    static int ink(Activity a){return androidx.core.content.ContextCompat.getColor(a,R.color.gm_ink);}
    static int canvas(Activity a){return androidx.core.content.ContextCompat.getColor(a,R.color.gm_canvas);}
    static void theme(Activity a,android.view.View root){root.setBackgroundColor(canvas(a));}
    static int dp(Activity a,int n){return (int)(n*a.getResources().getDisplayMetrics().density);}
    static LinearLayout column(Activity a){LinearLayout l=new LinearLayout(a);l.setOrientation(LinearLayout.VERTICAL);return l;}
    static TextView text(Activity a,LinearLayout parent,CharSequence text,int size,boolean bold){TextView v=new TextView(a);v.setText(text);v.setTextSize(size);v.setTextColor(ink(a));if(bold)v.setTypeface(null,Typeface.BOLD);v.setPadding(0,dp(a,7),0,dp(a,7));parent.addView(v);return v;}
    static com.google.android.material.button.MaterialButton action(Activity a){com.google.android.material.button.MaterialButton b=new com.google.android.material.button.MaterialButton(a);b.setAllCaps(false);b.setMinHeight(dp(a,48));b.setCornerRadius(dp(a,16));int[][] states={new int[]{android.R.attr.state_enabled},new int[]{}};
        b.setBackgroundTintList(new android.content.res.ColorStateList(states,new int[]{androidx.core.content.ContextCompat.getColor(a,R.color.gm_purple),androidx.core.content.ContextCompat.getColor(a,R.color.gm_border)}));
        b.setTextColor(new android.content.res.ColorStateList(states,new int[]{Color.WHITE,androidx.core.content.ContextCompat.getColor(a,R.color.gm_muted)}));return b;}
    static Button button(Activity a,LinearLayout parent,int label,Runnable action){Button b=action(a);b.setText(label);parent.addView(b);b.setOnClickListener(v->action.run());return b;}
    static LinearLayout card(Activity a,LinearLayout parent,CharSequence title){LinearLayout c=column(a);int p=dp(a,18);c.setPadding(p,p,p,p);GradientDrawable bg=new GradientDrawable();bg.setColor(androidx.core.content.ContextCompat.getColor(a,R.color.gm_surface));bg.setCornerRadius(dp(a,22));bg.setStroke(dp(a,1),androidx.core.content.ContextCompat.getColor(a,R.color.gm_border));c.setBackground(bg);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.bottomMargin=dp(a,16);parent.addView(c,lp);if(title!=null)text(a,c,title,22,true);return c;}
    static LinearLayout accentCard(Activity a,LinearLayout parent,CharSequence title,int tone){LinearLayout c=card(a,parent,null);View accent=new View(a);accent.setBackgroundColor(tone);c.addView(accent,new LinearLayout.LayoutParams(dp(a,48),dp(a,5)));if(title!=null)text(a,c,title,22,true);return c;}
    static LinearLayout hero(Activity a,LinearLayout parent,int title,int note){LinearLayout c=card(a,parent,null);c.setBackgroundResource(R.drawable.reward_panel_xp);text(a,c,a.getString(title),26,true).setTextColor(Color.WHITE);text(a,c,a.getString(note),15,false).setTextColor(Color.WHITE);return c;}
    static void toolbar(Activity a,com.google.android.material.appbar.MaterialToolbar toolbar){toolbar.setTitleTextColor(ink(a));toolbar.setNavigationIconTint(ink(a));toolbar.setBackgroundColor(canvas(a));}
    static int mealIndex(Object meal){for(int i=0;i<4;i++)if(ProgressService.MEALS[i].equalsIgnoreCase(String.valueOf(meal)))return i;return 4;}
    static void mealPanel(Activity a,View panel,int meal){int[] colors={R.color.gm_lavender,R.color.gm_teal_soft,R.color.gm_gold_soft,R.color.gm_surface,R.color.gm_surface};GradientDrawable bg=new GradientDrawable();bg.setColor(androidx.core.content.ContextCompat.getColor(a,colors[Math.max(0,Math.min(4,meal))]));bg.setCornerRadius(dp(a,20));bg.setStroke(dp(a,1),androidx.core.content.ContextCompat.getColor(a,R.color.gm_border));panel.setBackground(bg);}
    static TextView legend(Activity a,LinearLayout parent,String label,int color){TextView v=text(a,parent,label,16,false);GradientDrawable dot=new GradientDrawable();dot.setColor(color);dot.setCornerRadius(dp(a,4));dot.setBounds(0,0,dp(a,18),dp(a,18));v.setCompoundDrawablesRelative(dot,null,null,null);v.setCompoundDrawablePadding(dp(a,10));return v;}
    static ProgressChartView chart(Activity a,LinearLayout parent,int height){ProgressChartView chart=new ProgressChartView(a);parent.addView(chart,new LinearLayout.LayoutParams(-1,dp(a,height)));return chart;}
    static String number(Activity a,Object value){String text=ProgressNumbers.format(value,java.util.Locale.getDefault());return text==null?a.getString(R.string.fl_missing):text;}
    static void export(Activity a,Uri uri,String data){if(uri==null||data==null)return;new Thread(()->{
        boolean success=false;try(OutputStream out=a.getContentResolver().openOutputStream(uri)){if(out==null)throw new java.io.IOException();out.write(data.getBytes(StandardCharsets.UTF_8));success=true;}catch(Exception ignored){}
        final boolean ok=success;a.runOnUiThread(()->{if(!a.isDestroyed())Toast.makeText(a,ok?R.string.pr_exported:R.string.pr_export_error,Toast.LENGTH_LONG).show();});
    },"progress-export").start();}
}
