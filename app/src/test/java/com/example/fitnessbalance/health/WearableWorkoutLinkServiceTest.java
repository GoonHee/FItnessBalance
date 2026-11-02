package com.example.fitnessbalance.health;

import org.junit.Test;
import static org.junit.Assert.*;
import java.time.ZoneId;
import java.util.*;
import com.example.fitnessbalance.util.Values;

public class WearableWorkoutLinkServiceTest {
    private static final long START=1800000000000L;
    private Map<String,Object> plan(){return Values.of("type",56,"activity","Running","startedAt",START);}
    private Map<String,Object> session(){return Values.of("id","hc-123","source","com.mi.fitness","type",56,"activity","Running","start",START,"end",START+1800000L,"calorieState","available","activeCalories",180.5);}
    private Map<String,Object> energy(long a,long b,double kcal){return Values.of("source","com.mi.fitness","start",START+a,"end",START+b,"kcal",kcal);}
    @Test public void exactSessionCalories(){assertEquals(180.5,WearableWorkoutLinkService.matchCalories(session(),Arrays.asList(energy(0,1800000,180.5))),0);}
    @Test public void contiguousIntervals(){assertEquals(180,WearableWorkoutLinkService.matchCalories(session(),Arrays.asList(energy(900000,1800000,100),energy(0,900000,80))),0);}
    @Test public void zeroIsAvailable(){assertEquals(0,WearableWorkoutLinkService.matchCalories(session(),Arrays.asList(energy(0,1800000,0))),0);}
    @Test public void missingIsUnavailable(){assertNull(WearableWorkoutLinkService.matchCalories(session(),Collections.emptyList()));}
    @Test public void dailyCaloriesNotAllocated(){assertNull(WearableWorkoutLinkService.matchCalories(session(),Arrays.asList(energy(-10000,86400000,2000))));}
    @Test public void otherSourceNotUsed(){Map<String,Object> e=energy(0,1800000,200);e.put("source","other");assertNull(WearableWorkoutLinkService.matchCalories(session(),Arrays.asList(e)));}
    @Test public void gapsAreUnavailable(){assertNull(WearableWorkoutLinkService.matchCalories(session(),Arrays.asList(energy(0,900000,80),energy(900001,1800000,100))));}
    @Test public void overlapsNotSummed(){assertNull(WearableWorkoutLinkService.matchCalories(session(),Arrays.asList(energy(0,1000000,80),energy(900000,1800000,100))));}
    @Test public void duplicatesNotSummed(){assertNull(WearableWorkoutLinkService.matchCalories(session(),Arrays.asList(energy(0,1800000,80),energy(0,1800000,80))));}
    @Test public void nonFiniteRejected(){assertNull(WearableWorkoutLinkService.matchCalories(session(),Arrays.asList(energy(0,1800000,Double.NaN))));}
    @Test public void matchingPlan(){assertTrue(WearableWorkoutLinkService.matchesPlan(plan(),session()));}
    @Test public void wrongActivityRejected(){Map<String,Object> s=session();s.put("type",79);assertFalse(WearableWorkoutLinkService.matchesPlan(plan(),s));}
    @Test public void unrelatedTimeRejected(){Map<String,Object> p=plan();p.put("startedAt",START-31*60000);assertFalse(WearableWorkoutLinkService.matchesPlan(p,session()));}
    @Test public void endedBeforePlanRejected(){Map<String,Object> p=plan();p.put("startedAt",START+1800001);assertFalse(WearableWorkoutLinkService.matchesPlan(p,session()));}
    @Test public void recordedValuesHaveProvenanceAndNoRewards(){Map<String,Object> e=WearableWorkoutLinkService.entry(plan(),session(),ZoneId.of("Asia/Kuala_Lumpur"),START+1800000);assertEquals(30.0,(Double)e.get("duration"),0);assertEquals(180.5,(Double)e.get("calories"),0);assertEquals("wearable_active",e.get("calorieSource"));assertFalse(e.containsKey("xpEarned"));assertEquals("health_connect",e.get("source"));}
    @Test public void unavailableNotAnInventedEstimate(){Map<String,Object> s=session();s.put("calorieState","permission_required");Map<String,Object> e=WearableWorkoutLinkService.entry(plan(),s,ZoneId.of("UTC"),START+1800000);assertEquals("unavailable",e.get("calorieSource"));assertNull(e.get("calories"));}
    @Test public void stableKeyIgnoresChangedTimes(){Map<String,Object> s=session();String key=WearableWorkoutLinkService.key(s);s.put("start",START+1);assertEquals(key,WearableWorkoutLinkService.key(s));assertTrue(key.matches("wearable_[0-9a-f]{64}"));s.put("source","other");assertNotEquals(key,WearableWorkoutLinkService.key(s));}
    @Test(expected=IllegalArgumentException.class) public void futureSessionRejected(){WearableWorkoutLinkService.entry(plan(),session(),ZoneId.of("UTC"),START+1);}
    @Test public void suppressedGuidedContributionIsNotCountedTwiceAndRestoresOnDelete(){
        com.example.fitnessbalance.service.ExerciseLogService service=new com.example.fitnessbalance.service.ExerciseLogService();
        Map<String,Object> imported=Values.of("entryId","wearable","calories",180,"duration",30,"completed",true);
        Map<String,Object> contributions=Values.of("guided",Values.of("calories",0,"activeSeconds",0,"suppressedByWearable",true,"wearableEntryId","wearable","originalCalories",100,"originalActiveSeconds",1200));
        Map<String,Object> totals=service.totals(Arrays.asList(imported),contributions);assertEquals(180L,totals.get("caloriesBurned"));assertEquals(1,totals.get("exerciseEntryCount"));
        service.guided(contributions,"guided",Values.of("estimatedCaloriesBurned",100,"activeDurationSeconds",1200));
        totals=service.totals(Arrays.asList(imported),contributions);assertEquals(180L,totals.get("caloriesBurned"));
        totals=service.totals(Collections.emptyList(),contributions);assertEquals(100L,totals.get("caloriesBurned"));assertEquals(1200L,totals.get("exerciseActiveSeconds"));
    }
    @Test(expected=IllegalArgumentException.class) public void overnightConfirmationIsDeferred(){
        long start=java.time.Instant.parse("2026-11-02T23:50:00Z").toEpochMilli();Map<String,Object> p=plan(),s=session();p.put("startedAt",start);s.put("start",start);s.put("end",start+1800000);WearableWorkoutLinkService.entry(p,s,ZoneId.of("UTC"),start+1800000);
    }
}
