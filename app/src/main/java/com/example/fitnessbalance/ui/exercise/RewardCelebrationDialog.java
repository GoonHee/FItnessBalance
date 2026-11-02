package com.example.fitnessbalance.ui.exercise;

import android.animation.ValueAnimator;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import com.example.fitnessbalance.R;
import com.example.fitnessbalance.model.WorkoutRecord;
import com.example.fitnessbalance.service.RewardCelebrationPolicy;

final class RewardCelebrationDialog {
    private RewardCelebrationDialog(){}
    static AlertDialog show(AppCompatActivity activity,WorkoutRecord record,String unlocked){
        LinearLayout content=new LinearLayout(activity);content.setOrientation(LinearLayout.VERTICAL);int p=(int)(24*activity.getResources().getDisplayMetrics().density);content.setPadding(p,p,p,p);
        content.setBackgroundResource(R.drawable.reward_panel_gold);
        ImageView star=new ImageView(activity);star.setImageResource(R.drawable.ic_reward_star);star.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);LinearLayout.LayoutParams icon=new LinearLayout.LayoutParams(p*3,p*3);icon.gravity=android.view.Gravity.CENTER_HORIZONTAL;content.addView(star,icon);
        String title=activity.getString(record.getDiscomfortReported()?R.string.gm_saved:RewardCelebrationPolicy.levelUp(record)?R.string.gm_level_up:!record.getUnlocked().isEmpty()?R.string.gm_unlock:RewardCelebrationPolicy.weeklyTargetReached(record)?R.string.gm_target:record.getCompletionPercentage()>=50?R.string.gm_saved:R.string.gm_attempt);
        add(activity,content,title,25,true);add(activity,content,record.getLessonName(),18,true);
        add(activity,content,activity.getString(R.string.gm_xp_earned,record.getXpEarned()),28,true);
        add(activity,content,stars(record.getStarsEarned())+"  "+activity.getString(R.string.gm_stars,record.getStarsEarned()),18,false);
        if(RewardCelebrationPolicy.levelUp(record))add(activity,content,activity.getString(R.string.gm_level_detail,RewardCelebrationPolicy.levelAfter(record)),18,true);
        add(activity,content,activity.getString(R.string.gm_week_detail,record.getWeeklyCompleted(),record.getWeeklyTarget()),16,false);
        if(RewardCelebrationPolicy.weeklyTargetReached(record))add(activity,content,activity.getString(R.string.gm_target),18,true);
        if(!unlocked.isEmpty())add(activity,content,unlocked,16,false);
        add(activity,content,activity.getString(record.getDiscomfortReported()?R.string.gm_discomfort:R.string.gm_reward_note),14,false);
        ScrollView scroll=new ScrollView(activity);scroll.addView(content);
        AlertDialog dialog=new AlertDialog.Builder(activity).setView(scroll).setPositiveButton(R.string.gm_continue,(d,w)->{}).create();
        dialog.setOnDismissListener(d->star.animate().cancel());
        dialog.setOnShowListener(d->{if(RewardCelebrationPolicy.canAnimate(ValueAnimator.areAnimatorsEnabled(),record.getDiscomfortReported())){star.setScaleX(.8f);star.setScaleY(.8f);star.animate().scaleX(1).scaleY(1).setDuration(350).start();}});
        dialog.show();return dialog;
    }
    static String stars(int value){int n=Math.max(0,Math.min(3,value));return "★★★".substring(0,n)+"☆☆☆".substring(n);}
    private static void add(AppCompatActivity a,LinearLayout parent,String text,int size,boolean bold){TextView view=new TextView(a);view.setText(text);view.setTextSize(size);view.setTextColor(ContextCompat.getColor(a,R.color.gm_ink));view.setPadding(0,8,0,8);if(bold)view.setTypeface(null,android.graphics.Typeface.BOLD);parent.addView(view);}
}
