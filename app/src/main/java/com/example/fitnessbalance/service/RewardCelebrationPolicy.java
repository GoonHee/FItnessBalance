package com.example.fitnessbalance.service;

import com.example.fitnessbalance.model.WorkoutRecord;

/** Presentation of immutable rewards only. Never awards XP or modifies targets. */
public final class RewardCelebrationPolicy {
    private RewardCelebrationPolicy(){}
    public static boolean canPresent(boolean requested,boolean serverConfirmed,boolean handled){return requested&&serverConfirmed&&!handled;}
    public static int levelAfter(WorkoutRecord record){return Math.max(0,record.getTotalXpAfter())/100+1;}
    public static boolean levelUp(WorkoutRecord record){int before=Math.max(0,record.getTotalXpAfter()-Math.max(0,record.getXpEarned()));return levelAfter(record)>before/100+1;}
    public static boolean weeklyTargetReached(WorkoutRecord record){return record.getWeeklyTarget()>0&&record.getWeeklyCompleted()==record.getWeeklyTarget()&&record.getCompletionPercentage()>=50;}
    public static boolean canAnimate(boolean systemAnimations,boolean discomfort){return systemAnimations&&!discomfort;}
}
