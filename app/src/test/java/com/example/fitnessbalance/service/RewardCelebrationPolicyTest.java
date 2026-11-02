package com.example.fitnessbalance.service;
import com.example.fitnessbalance.model.WorkoutRecord;
import org.junit.Test;
import static org.junit.Assert.*;

public class RewardCelebrationPolicyTest {
    private WorkoutRecord record(int after,int earned,double completion,int count,int target){WorkoutRecord r=new WorkoutRecord();r.setTotalXpAfter(after);r.setXpEarned(earned);r.setCompletionPercentage(completion);r.setWeeklyCompleted(count);r.setWeeklyTarget(target);return r;}
    @Test public void crossingHundredXpRecognisesNewLevel(){WorkoutRecord r=record(100,12,100,1,3);assertTrue(RewardCelebrationPolicy.levelUp(r));assertEquals(2,RewardCelebrationPolicy.levelAfter(r));}
    @Test public void progressWithinLevelDoesNotInventLevelUp(){assertFalse(RewardCelebrationPolicy.levelUp(record(99,12,100,1,3)));assertFalse(RewardCelebrationPolicy.levelUp(record(235,20,100,2,3)));}
    @Test public void questAndWeeklyBonusAreIncludedInActualLevelTransition(){WorkoutRecord r=record(300,77,100,3,3);assertTrue(RewardCelebrationPolicy.levelUp(r));assertEquals(4,RewardCelebrationPolicy.levelAfter(r));assertEquals(77,r.getXpEarned());}
    @Test public void targetRecognisedOnlyAtQualifyingCrossing(){assertFalse(RewardCelebrationPolicy.weeklyTargetReached(record(50,12,100,2,3)));assertTrue(RewardCelebrationPolicy.weeklyTargetReached(record(75,25,50,3,3)));assertFalse(RewardCelebrationPolicy.weeklyTargetReached(record(100,25,100,4,3)));}
    @Test public void incompleteAttemptDoesNotReCelebratePreviouslyMetTarget(){assertFalse(RewardCelebrationPolicy.weeklyTargetReached(record(100,5,25,3,3)));}
    @Test public void missingLegacyTargetDoesNotInventSuccess(){assertFalse(RewardCelebrationPolicy.weeklyTargetReached(record(100,5,100,0,0)));}
    @Test public void combinedRewardsRecognisedWithoutChangingTheRecord(){WorkoutRecord r=record(100,30,100,3,3);r.setUnlocked(java.util.Arrays.asList("first_step"));assertTrue(RewardCelebrationPolicy.levelUp(r));assertTrue(RewardCelebrationPolicy.weeklyTargetReached(r));assertEquals(30,r.getXpEarned());assertEquals(1,r.getUnlocked().size());}
    @Test public void historyOrUnconfirmedSaveCannotPresentCelebration(){assertFalse(RewardCelebrationPolicy.canPresent(false,true,false));assertFalse(RewardCelebrationPolicy.canPresent(true,false,false));assertTrue(RewardCelebrationPolicy.canPresent(true,true,false));assertFalse(RewardCelebrationPolicy.canPresent(true,true,true));}
    @Test public void disabledMotionAndDiscomfortUseStaticPresentation(){assertFalse(RewardCelebrationPolicy.canAnimate(false,false));assertFalse(RewardCelebrationPolicy.canAnimate(true,true));assertTrue(RewardCelebrationPolicy.canAnimate(true,false));}
}
