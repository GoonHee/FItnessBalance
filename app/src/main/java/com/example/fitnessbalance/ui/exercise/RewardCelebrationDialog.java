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
import com.example.fitnessbalance.service.GamificationService;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

final class RewardCelebrationDialog {
    private RewardCelebrationDialog(){}
    static AlertDialog show(AppCompatActivity activity,WorkoutRecord record,String unlocked){
        LinearLayout content=new LinearLayout(activity);content.setOrientation(LinearLayout.VERTICAL);int p=(int)(24*activity.getResources().getDisplayMetrics().density);content.setPadding(p,p,p,p);
        content.setBackgroundResource(R.drawable.reward_panel_gold);
        ImageView star=new ImageView(activity);star.setImageResource(R.drawable.ic_reward_star);star.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);LinearLayout.LayoutParams icon=new LinearLayout.LayoutParams(p*3,p*3);icon.gravity=android.view.Gravity.CENTER_HORIZONTAL;content.addView(star,icon);
        String title=activity.getString(record.getDiscomfortReported()?R.string.gm_saved:RewardCelebrationPolicy.levelUp(record)?R.string.gm_level_up:!record.getUnlocked().isEmpty()?R.string.gm_unlock:RewardCelebrationPolicy.weeklyTargetReached(record)?R.string.gm_target:record.getCompletionPercentage()>=50?R.string.gm_saved:R.string.gm_attempt);
        add(activity,content,title,25,true);add(activity,content,record.getLessonName(),18,true);
        TextView xp = add(activity,content,activity.getString(R.string.gm_xp_earned,record.getXpEarned()),28,true);
        add(activity,content,breakdown(activity, record.getXpBreakdown()),15,false);
        if(RewardCelebrationPolicy.levelUp(record))add(activity,content,activity.getString(R.string.gm_level_detail,RewardCelebrationPolicy.levelAfter(record)),18,true);
        add(activity,content,activity.getString(R.string.gm_week_detail,record.getWeeklyCompleted(),record.getWeeklyTarget()),16,false);
        add(activity,content,activity.getString(R.string.gm_next_level, new GamificationService().nextLevelXp(record.getTotalXpAfter())),16,false);
        if(!unlocked.isEmpty())add(activity,content,unlocked,16,false);
        if (record.getDiscomfortReported()) add(activity,content,activity.getString(R.string.gm_discomfort),14,false);
        ScrollView scroll=new ScrollView(activity);scroll.addView(content);
        AlertDialog dialog=new AlertDialog.Builder(activity).setView(scroll).setPositiveButton(R.string.gm_continue,(d,w)->{}).create();
        dialog.setOnDismissListener(d -> xp.animate().cancel());
        dialog.setOnShowListener(d -> {
            if (record.getXpEarned() > 0 && RewardCelebrationPolicy.canAnimate(
                    ValueAnimator.areAnimatorsEnabled(), record.getDiscomfortReported())) {
                xp.setAlpha(.65f);
                xp.animate().alpha(1).setDuration(250).start();
            }
        });
        dialog.show();return dialog;
    }
    static String breakdown(AppCompatActivity activity, Map<String, Integer> xp) {
        if (xp.isEmpty()) return activity.getString(R.string.gm_legacy_reward);
        String[] keys = {"lesson", "feedback", "planned", "minutes", "weekly"};
        int[] labels = {R.string.gm_xp_lesson, R.string.gm_xp_feedback, R.string.gm_xp_planned,
            R.string.gm_xp_minutes, R.string.gm_xp_weekly};
        List<String> lines = new ArrayList<>();
        for (int i = 0; i < keys.length; i++) {
            int amount = xp.getOrDefault(keys[i], 0);
            if (amount > 0) lines.add(activity.getString(labels[i], amount));
        }
        return lines.isEmpty() ? activity.getString(R.string.gm_no_reward) : android.text.TextUtils.join("\n", lines);
    }
    static String stars(int value){int n=Math.max(0,Math.min(3,value));return "★★★".substring(0,n)+"☆☆☆".substring(n);}
    private static TextView add(AppCompatActivity a,LinearLayout parent,String text,int size,boolean bold){TextView view=new TextView(a);view.setText(text);view.setTextSize(size);view.setTextColor(ContextCompat.getColor(a,R.color.gm_ink));int gap=Math.round(4*a.getResources().getDisplayMetrics().density);view.setPadding(0,gap,0,gap);if(bold)view.setTypeface(null,android.graphics.Typeface.BOLD);parent.addView(view);return view;}
}
