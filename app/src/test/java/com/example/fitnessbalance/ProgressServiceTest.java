package com.example.fitnessbalance;

import com.example.fitnessbalance.util.Values;
import org.junit.Test;
import java.time.LocalDate;
import java.util.*;
import static org.junit.Assert.*;

public class ProgressServiceTest {
    private final LocalDate monday=LocalDate.parse("2026-10-26"),today=LocalDate.parse("2026-10-30");
    private Map<String,Map<String,Object>> days(String day,Map<String,Object> data){Map<String,Map<String,Object>> result=new HashMap<>();result.put(day,data);return result;}
    private Map<String,Object> log(Map<String,Object>...items){return Values.of("foodItems",Arrays.asList(items));}
    private ProgressService.Week build(Map<String,Object> profile,Map<String,Map<String,Object>> food,Map<String,Map<String,Object>> daily,Map<String,Map<String,Object>> manual,List<Map<String,Object>> guided){return new ProgressService().build(monday,today,profile,food,daily,manual,guided);}
    private ProgressService.Week food(Map<String,Map<String,Object>> food){return build(Collections.emptyMap(),food,Collections.emptyMap(),Collections.emptyMap(),Collections.emptyList());}
    @Test public void weekStartsOnMondayAcrossYearBoundary(){assertEquals(LocalDate.parse("2025-12-29"),ProgressService.week(LocalDate.parse("2026-01-04")));}
    @Test public void missingDaysAreNotZeroInAverage(){ProgressService.Week w=food(days("2026-10-26",log(Values.of("calories",200))));assertEquals(7,w.days.size());assertEquals(200,w.average("energy_kcal"),0);assertEquals(1,w.count("energy_kcal"));assertNull(w.days.get(1).food);}
    @Test public void recordedZeroIsNotMissing(){ProgressService.Week w=food(days("2026-10-26",log(Values.of("calories",0))));assertEquals(0,w.days.get(0).food,0);assertEquals(1,w.count("energy_kcal"));}
    @Test public void emptyLogDoesNotResurrectStaleDailyTotals(){ProgressService.Week w=build(Collections.emptyMap(),days("2026-10-26",log()),days("2026-10-26",Values.of("caloriesConsumed",900)),Collections.emptyMap(),Collections.emptyList());assertNull(w.days.get(0).food);}
    @Test public void partialNutrientsRetainCoverageAndBlockMacroPercentages(){ProgressService.Week w=food(days("2026-10-26",log(Values.of("calories",200,"nutrients",Values.of("energy_kcal",200,"protein_g",10)),Values.of("calories",100))));assertEquals(300,w.days.get(0).food,0);assertEquals(10,w.days.get(0).nutrient("protein_g"),0);assertTrue(w.days.get(0).partial("protein_g"));assertFalse(w.completeMacros());}
    @Test public void missingCalorieEntryMakesEnergyPartialNotComplete(){ProgressService.Week w=food(days("2026-10-26",log(Values.of("calories",200),Collections.emptyMap())));assertTrue(w.days.get(0).partialEnergy);assertEquals(200,w.days.get(0).food,0);}
    @Test public void mealStacksIncludeUnassignedWithoutDroppingCalories(){ProgressService.Week w=food(days("2026-10-26",log(Values.of("calories",100,"meal","Breakfast"),Values.of("calories",50,"meal","Unknown"))));assertArrayEquals(new double[]{100,0,0,0,50},w.days.get(0).meals,0);}
    @Test public void savedSourceValuesAndFoodIdsAreAggregated(){ProgressService.Week w=food(days("2026-10-26",log(Values.of("foodId","id","name","Food","calories",100),Values.of("foodId","id","name","Food","calories",200))));assertEquals(1,w.highest.size());assertEquals(300,(Double)w.highest.get(0).get("calories"),0);}
    @Test public void legacyCalorieOnlyDayHasNoInventedMacros(){ProgressService.Week w=build(Collections.emptyMap(),Collections.emptyMap(),days("2026-10-26",Values.of("caloriesConsumed",500)),Collections.emptyMap(),Collections.emptyList());assertEquals(500,w.days.get(0).food,0);assertNull(w.days.get(0).nutrient("protein_g"));assertTrue(w.highest.isEmpty());}
    @Test public void guidedContributionIsCountedOnceAndNetMayBeNegative(){Map<String,Object> daily=Values.of("structuredExerciseContributions",Values.of("session",Values.of("calories",300,"activeSeconds",600)));List<Map<String,Object>> records=Arrays.asList(Values.of("recordId","session","localDay","2026-10-26","estimatedCaloriesBurned",300,"activeDurationSeconds",600));ProgressService.Week w=build(Values.of("weight",120),days("2026-10-26",log(Values.of("calories",100))),days("2026-10-26",daily),Collections.emptyMap(),records);assertEquals(300,w.days.get(0).exercise,0);assertEquals(-200,w.days.get(0).net,0);assertEquals(300,w.average("exercise"),0);}
    @Test public void uncompletedManualEntriesDoNotBurnCalories(){Map<String,Object> m=Values.of("exercises",Arrays.asList(Values.of("name","Run","duration",30,"calories",200,"completed",false)));ProgressService.Week w=build(Collections.emptyMap(),days("2026-10-26",log(Values.of("calories",100))),Collections.emptyMap(),days("2026-10-26",m),Collections.emptyList());assertNull(w.days.get(0).exercise);assertEquals(100,w.days.get(0).net,0);}
    @Test public void futureDaysRemainBlankEvenIfFutureRecordsExist(){ProgressService.Week w=food(days("2026-10-31",log(Values.of("calories",200))));assertNull(w.days.get(5).food);assertNull(w.average("energy_kcal"));}
    @Test public void oldDefaultIsNotAConfirmedReferenceGoal(){ProgressService.Week w=build(Values.of("dailyCalorieGoal",2000),Collections.emptyMap(),Collections.emptyMap(),Collections.emptyMap(),Collections.emptyList());assertNull(w.goal("energy_kcal"));}
    @Test public void completeRecordedMacrosPermitEnergyShare(){ProgressService.Week w=food(days("2026-10-26",log(Values.of("nutrients",Values.of("energy_kcal",165,"protein_g",10,"carbohydrate_g",20,"fat_g",5)))));assertTrue(w.completeMacros());}
    @Test public void csvKeepsMissingCellsEmpty(){ProgressService.Week w=food(days("2026-10-26",log(Values.of("calories",0))));String csv=ProgressCsv.week(w);assertTrue(csv.contains("2026-10-27\",,,,,,,,,"));assertFalse(csv.contains("null"));}
}
