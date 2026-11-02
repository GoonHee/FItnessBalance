package com.example.fitnessbalance.service;

import com.example.fitnessbalance.util.*;
import java.time.*;
import java.util.*;

/** Versioned, calorie-independent application rewards; elapsed time is not active sensor time. */
public final class WearableRewardService {
  public static final String VERSION="wearable-rewards-v1";
  public static final long MINIMUM_MILLIS=300000L,WINDOW_MILLIS=7L*86400000;
  public boolean eligible(Map<String,Object> entry,long now){
    if(!"health_connect".equals(entry.get("source"))||!Boolean.TRUE.equals(entry.get("completed")))return false;
    long from=com.example.fitnessbalance.health.WearableWorkoutService.number(entry.get("startedAtMillis")),to=com.example.fitnessbalance.health.WearableWorkoutService.number(entry.get("endedAtMillis"));
    return from>0&&from>=now-WINDOW_MILLIS&&to<=now&&to-from>=MINIMUM_MILLIS&&to-from<=86400000L;
  }
  public boolean countedOverlap(List<Map<String,Object>> entries,long from,long to){
    for(Map<String,Object> row:entries)if("health_connect".equals(row.get("source"))&&Boolean.TRUE.equals(row.get("completed"))&&row.get("startedAtMillis") instanceof Number&&row.get("endedAtMillis") instanceof Number
      && com.example.fitnessbalance.health.WearableWorkoutService.overlaps(from,to,((Number)row.get("startedAtMillis")).longValue(),((Number)row.get("endedAtMillis")).longValue()))return true;
    return false;
  }
  public String category(String activity){
    if(Arrays.asList("Strength training","Weight lifting").contains(activity))return "strength";
    if(Arrays.asList("Yoga","Pilates").contains(activity))return "mobility";
    if(Arrays.asList("Walking","Running","Treadmill running","Cycling","Stationary cycling","Pool swimming","Open-water swimming","Hiking","HIIT","Dancing","Rowing").contains(activity))return "cardio";
    return "mixed";
  }
  public Map<String,Object> decide(Map<String,Object> entry,Map<String,Object> profile,Map<String,Object> weekly,
      boolean dayCredited,boolean plannedQuest,boolean minutesQuest,Set<String> unlocked,long now){
    if(!eligible(entry,now))return Values.of("eligible",false,"xpEarned",0,"unlocked",Collections.emptyList());
    long from=((Number)entry.get("startedAtMillis")).longValue(),to=((Number)entry.get("endedAtMillis")).longValue();
    ZoneId zone=ZoneId.of(Values.text(entry.get("timeZone"),"UTC"));String day=Instant.ofEpochMilli(from).atZone(zone).toLocalDate().toString();
    if(!day.equals(entry.get("localDay")))throw new IllegalArgumentException("Workout date does not match its recorded time zone");
    String week=DateUtils.week(new Date(from),TimeZone.getTimeZone(zone));
    GamificationService game=new GamificationService();int target=new MomentumService().target(Values.integer(profile.get("weeklyWorkoutTarget"),3));
    int oldCount=Values.integer(weekly.get("completed"),Values.integer(Values.map(profile.get("weeklyCounts")).get(week),0));
    if(oldCount<0||oldCount>1000000)throw new IllegalStateException("Invalid weekly progress");
    int count=oldCount+(dayCredited?0:1),base=dayCredited?0:20,quest=(plannedQuest?0:5)+(minutesQuest?0:5);
    List<String> goalWeeks=Values.strings(profile.get("metGoalWeeks"));boolean priorBonus=Boolean.TRUE.equals(weekly.get("bonusAwarded"))||goalWeeks.contains(week);
    int bonus=game.weeklyBonus(count,target,priorBonus);if(bonus>0&&!goalWeeks.contains(week))goalWeeks.add(week);
    String category=category(Values.text(entry.get("name"),""));List<String> categories=Values.strings(weekly.get("categories"));if(!categories.contains(category))categories.add(category);
    List<String> award=new ArrayList<>();if(!unlocked.contains("first_step"))award.add("first_step");
    if(goalWeeks.size()>=2&&!unlocked.contains("routine_builder"))award.add("routine_builder");
    long previous=profile.get("lastWorkoutAtMillis") instanceof Number?((Number)profile.get("lastWorkoutAtMillis")).longValue():0;
    if(previous>0&&previous<from&&from-previous>=WINDOW_MILLIS&&!unlocked.contains("welcome_back"))award.add("welcome_back");
    if(categories.containsAll(Arrays.asList("strength","mobility","recovery"))&&!unlocked.contains("balanced_week"))award.add("balanced_week");
    int earned=base+quest+bonus,oldXp=Values.integer(profile.get("totalXp"),0);if(oldXp<0||oldXp>1000000000-earned)throw new IllegalStateException("Invalid XP total");
    return Values.of("eligible",true,"source","health_connect","activity",Values.text(entry.get("name"),"Workout"),"day",day,"week",week,"category",category,"baseXp",base,"questXp",quest,"weeklyBonusXp",bonus,"xpEarned",earned,
        "weeklyCompleted",count,"weeklyTarget",target,"totalXpAfter",oldXp+earned,"newDayCredit",!dayCredited,"unlocked",award,"ruleVersion",VERSION,
        "durationSeconds",(to-from)/1000,"startedAtMillis",from,"endedAtMillis",to,"metGoalWeeks",goalWeeks,"categories",categories,"bonusAwarded",priorBonus||bonus>0,"lastWorkoutAtMillis",Math.max(previous,to));
  }
}
