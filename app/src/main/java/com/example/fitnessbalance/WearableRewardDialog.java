package com.example.fitnessbalance;

import android.widget.*;
import androidx.appcompat.app.*;
import com.example.fitnessbalance.util.Values;
import java.util.*;

/** Presents committed results only; displaying a dialog cannot grant any rewards. */
public final class WearableRewardDialog {
  private WearableRewardDialog(){}
  public static AlertDialog show(AppCompatActivity activity,Map<String,Object> award){
    LinearLayout body=ProgressUi.column(activity);int p=ProgressUi.dp(activity,22);body.setPadding(p,p,p,p);body.setBackgroundResource(R.drawable.reward_panel_gold);
    boolean prior=Boolean.TRUE.equals(award.get("alreadyAwarded")),eligible=Boolean.TRUE.equals(award.get("eligible"))||prior;
    ProgressUi.text(activity,body,activity.getString(R.string.wr_title),24,true);
    if(!eligible)ProgressUi.text(activity,body,activity.getString(R.string.wr_ineligible),16,false);
    else{
      ProgressUi.text(activity,body,activity.getString(prior?R.string.wr_already:R.string.gm_xp_earned,Values.integer(award.get("xpEarned"),0)),24,true);
      ProgressUi.text(activity,body,activity.getString(R.string.gm_week_detail,Values.integer(award.get("weeklyCompleted"),0),Values.integer(award.get("weeklyTarget"),3)),17,true);
      if(!prior&&!Boolean.TRUE.equals(award.get("newDayCredit")))ProgressUi.text(activity,body,activity.getString(R.string.wr_day_cap),15,false);
      if(Values.integer(award.get("weeklyBonusXp"),0)>0)ProgressUi.text(activity,body,activity.getString(R.string.wr_week_bonus),17,true);
      for(String id:Values.strings(award.get("unlocked"))){int label="first_step".equals(id)?R.string.ex_first_step:"routine_builder".equals(id)?R.string.ex_routine_builder:"welcome_back".equals(id)?R.string.ex_welcome_back:R.string.ex_balanced_week;ProgressUi.text(activity,body,activity.getString(label),18,true);}
    }
    ProgressUi.text(activity,body,activity.getString(R.string.wr_policy),14,false);ScrollView scroll=new ScrollView(activity);scroll.addView(body);
    return new AlertDialog.Builder(activity).setView(scroll).setPositiveButton(android.R.string.ok,null).show();
  }
}
