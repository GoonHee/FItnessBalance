package com.example.fitnessbalance.adapter;

import java.util.List;

public final class AchievementAdapter extends ExerciseRowsAdapter {
  public AchievementAdapter(List<Row> rows) {
    super(rows);
  }

  @Override public void onBindViewHolder(@androidx.annotation.NonNull Holder holder,int position){
    super.onBindViewHolder(holder,position);
    android.content.Context context=holder.itemView.getContext();boolean earned=holder.state.getText().toString().equals(context.getString(com.example.fitnessbalance.R.string.ex_earned));
    com.google.android.material.card.MaterialCardView card=(com.google.android.material.card.MaterialCardView)holder.itemView;
    card.setCardBackgroundColor(androidx.core.content.ContextCompat.getColor(context,earned?com.example.fitnessbalance.R.color.gm_teal_soft:com.example.fitnessbalance.R.color.gm_lavender));
    card.setStrokeColor(androidx.core.content.ContextCompat.getColor(context,com.example.fitnessbalance.R.color.gm_border));card.setStrokeWidth((int)context.getResources().getDisplayMetrics().density);
    holder.title.setTextColor(androidx.core.content.ContextCompat.getColor(context,com.example.fitnessbalance.R.color.gm_ink));
    holder.body.setTextColor(androidx.core.content.ContextCompat.getColor(context,com.example.fitnessbalance.R.color.gm_muted));
    holder.state.setTextColor(androidx.core.content.ContextCompat.getColor(context,earned?com.example.fitnessbalance.R.color.gm_success:com.example.fitnessbalance.R.color.gm_ink));
    holder.state.setCompoundDrawablePadding((int)(8*context.getResources().getDisplayMetrics().density));
    holder.state.setCompoundDrawablesRelativeWithIntrinsicBounds(earned?com.example.fitnessbalance.R.drawable.ic_reward_check:0,0,0,0);
  }
}
