package com.example.fitnessbalance.service;

import com.example.fitnessbalance.util.Values;
import org.junit.Test;
import static org.junit.Assert.*;
import java.time.Instant;
import java.util.*;

public class WearableRewardServiceTest {
  private final WearableRewardService service=new WearableRewardService();
  private final long now=Instant.parse("2026-11-03T08:00:00Z").toEpochMilli();
  private Map<String,Object> entry(){return Values.of("source","health_connect","completed",true,"startedAtMillis",now-900000,"endedAtMillis",now-300000,"timeZone","Asia/Kuala_Lumpur","localDay","2026-11-03","name","Running","calories",null);}
  private Map<String,Object> decide(Map<String,Object> e,Map<String,Object> profile,Map<String,Object> week,boolean credited,boolean q1,boolean q2){return service.decide(e,profile,week,credited,q1,q2,Collections.emptySet(),now);}
  private Map<String,Object> normal(){return decide(entry(),Values.of("totalXp",0,"weeklyWorkoutTarget",3),Collections.emptyMap(),false,false,false);}
  @Test public void firstEligibleSessionEarns30AndOneCredit(){Map<String,Object> r=normal();assertEquals(30,r.get("xpEarned"));assertEquals(20,r.get("baseXp"));assertEquals(10,r.get("questXp"));assertEquals(1,r.get("weeklyCompleted"));assertEquals(Arrays.asList("first_step"),r.get("unlocked"));}
  @Test public void missingAndHugeCaloriesDoNotChangeXp(){Map<String,Object> e=entry();e.put("calories",200000);assertEquals(normal().get("xpEarned"),decide(e,Collections.emptyMap(),Collections.emptyMap(),false,false,false).get("xpEarned"));}
  @Test public void repeatDayGetsNoBaseOrWeekCredit(){Map<String,Object> r=decide(entry(),Values.of("totalXp",30),Values.of("completed",1),true,true,true);assertEquals(0,r.get("xpEarned"));assertEquals(1,r.get("weeklyCompleted"));assertEquals(false,r.get("newDayCredit"));}
  @Test public void guidedQuestsDoNotAwardTwice(){Map<String,Object> r=decide(entry(),Collections.emptyMap(),Values.of("completed",1),false,true,true);assertEquals(20,r.get("xpEarned"));assertEquals(2,r.get("weeklyCompleted"));}
  @Test public void onlyMissingQuestGetsBonus(){assertEquals(25,decide(entry(),Collections.emptyMap(),Collections.emptyMap(),false,true,false).get("xpEarned"));}
  @Test public void weeklyTargetAwards30Once(){Map<String,Object> r=decide(entry(),Values.of("weeklyWorkoutTarget",3),Values.of("completed",2),false,false,false);assertEquals(60,r.get("xpEarned"));assertEquals(Arrays.asList("2026-11-02"),r.get("metGoalWeeks"));assertEquals(0,decide(entry(),Values.of("weeklyWorkoutTarget",3),Values.of("completed",3,"bonusAwarded",true),false,true,true).get("weeklyBonusXp"));}
  @Test public void oldProfileWeeklyBonusIsRespected(){assertEquals(0,decide(entry(),Values.of("metGoalWeeks",Arrays.asList("2026-11-02")),Values.of("completed",3),false,true,true).get("weeklyBonusXp"));}
  @Test public void shortWorkoutDoesNotEarn(){Map<String,Object> e=entry();e.put("endedAtMillis",now-601000);assertFalse(service.eligible(e,now));assertEquals(0,decide(e,Collections.emptyMap(),Collections.emptyMap(),false,false,false).get("xpEarned"));}
  @Test public void exactFiveMinutesQualifies(){Map<String,Object> e=entry();e.put("endedAtMillis",now-600000);assertTrue(service.eligible(e,now));}
  @Test public void staleWorkoutDoesNotEarn(){Map<String,Object> e=entry();e.put("startedAtMillis",now-8*86400000L);e.put("endedAtMillis",now-8*86400000L+600000);assertFalse(service.eligible(e,now));}
  @Test public void futureWorkoutDoesNotEarn(){Map<String,Object> e=entry();e.put("endedAtMillis",now+1);assertFalse(service.eligible(e,now));}
  @Test public void unconfirmedAndManualAreNotEligible(){Map<String,Object> e=entry();e.put("completed",false);assertFalse(service.eligible(e,now));e.put("completed",true);e.put("source","manual");assertFalse(service.eligible(e,now));}
  @Test public void historicalImportUsesWorkoutDayAndKeepsNewerLastWorkout(){Map<String,Object> e=entry();long from=Instant.parse("2026-11-01T02:00:00Z").toEpochMilli();e.put("startedAtMillis",from);e.put("endedAtMillis",from+600000);e.put("localDay","2026-11-01");Map<String,Object> r=decide(e,Values.of("lastWorkoutAtMillis",now-1000),Collections.emptyMap(),false,false,false);assertEquals("2026-11-01",r.get("day"));assertEquals("2026-10-26",r.get("week"));assertEquals(now-1000,r.get("lastWorkoutAtMillis"));assertFalse(Values.strings(r.get("unlocked")).contains("welcome_back"));}
  @Test public void consistencyAndComebackAchievements(){Map<String,Object> r=decide(entry(),Values.of("metGoalWeeks",Arrays.asList("2026-10-19","2026-10-26"),"lastWorkoutAtMillis",now-9*86400000L),Collections.emptyMap(),false,false,false);assertTrue(Values.strings(r.get("unlocked")).containsAll(Arrays.asList("routine_builder","welcome_back")));assertFalse(Values.strings(r.get("unlocked")).contains("honest_reflection"));assertFalse(Values.strings(r.get("unlocked")).contains("path_explorer"));}
  @Test public void existingAchievementsAreNotUnlockedAgain(){Map<String,Object> r=service.decide(entry(),Collections.emptyMap(),Collections.emptyMap(),false,false,false,new HashSet<>(Arrays.asList("first_step")),now);assertTrue(Values.strings(r.get("unlocked")).isEmpty());}
  @Test public void categorySupportsBalancedWeekWithoutInventedRecovery(){Map<String,Object> e=entry();e.put("name","Yoga");Map<String,Object> r=decide(e,Collections.emptyMap(),Values.of("categories",Arrays.asList("strength","recovery")),false,false,false);assertTrue(Values.strings(r.get("unlocked")).contains("balanced_week"));assertEquals("cardio",service.category("Walking"));assertEquals("strength",service.category("Strength training"));}
  @Test(expected=IllegalArgumentException.class) public void dateMismatchRejected(){Map<String,Object> e=entry();e.put("localDay","2026-11-04");decide(e,Collections.emptyMap(),Collections.emptyMap(),false,false,false);}
  @Test(expected=IllegalStateException.class) public void xpOverflowRejected(){decide(entry(),Values.of("totalXp",1000000000),Collections.emptyMap(),false,false,false);}
  @Test public void countedOverlapRequiresCompletedWearable(){Map<String,Object> e=entry();assertTrue(service.countedOverlap(Arrays.asList(e),now-800000,now-1000));assertFalse(service.countedOverlap(Arrays.asList(e),now,now+1000));e.put("completed",false);assertFalse(service.countedOverlap(Arrays.asList(e),now-800000,now-1000));}
}
