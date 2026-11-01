package com.example.fitnessbalance;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;
import com.example.fitnessbalance.util.Values;

public class NutritionAvailabilityServiceTest {
    private Map<String,Object> unknown(String basis,Double amount){return NutritionAvailabilityService.unknown("Rice","rice","entry","Lunch",Values.of("energy_kcal",130,"protein_g",2),basis,"g",amount,1,"one plate");}
    @Test public void unknownBasisNeverScalesReferenceValues(){Map<String,Object> row=unknown("unknown",100.0);assertTrue(((Map<?,?>)row.get("nutrients")).isEmpty());assertFalse(row.containsKey("calories"));assertEquals("unknown",row.get("calculationMode"));}
    @Test public void knownBasisMayPreserveOtherNutrientsButNoCalories(){Map<String,Object> row=unknown("per_100g",150.0);assertEquals(3.0,((Map<?,?>)row.get("nutrients")).get("protein_g"));assertFalse(((Map<?,?>)row.get("nutrients")).containsKey("energy_kcal"));}
    @Test public void unmeasuredPortionHasNoInventedAmountOrNutrients(){Map<String,Object> row=unknown("per_100g",null);assertFalse(row.containsKey("totalAmount"));assertTrue(((Map<?,?>)row.get("nutrients")).isEmpty());assertEquals("one plate",row.get("portionDescription"));}
    @Test public void mixedDayReportsSubtotalAndWithholdsNet(){NutritionAvailabilityService.Stats s=NutritionAvailabilityService.stats(Arrays.asList(Values.of("calories",150),unknown("unknown",null)));assertEquals(150,s.subtotal,0);assertEquals(1,s.missing);assertEquals(1,s.known);assertFalse(s.complete());assertNull(s.net(200));}
    @Test public void allUnknownIsNotZero(){NutritionAvailabilityService.Stats s=NutritionAvailabilityService.stats(Arrays.asList(unknown("unknown",null)));assertNull(s.subtotal);assertNull(s.net(0));assertEquals(1,s.missing);}
    @Test public void recordedZeroIsKnownAndCanHaveNegativeNet(){NutritionAvailabilityService.Stats s=NutritionAvailabilityService.stats(Arrays.asList(Values.of("calories",0)));assertEquals(0,s.subtotal,0);assertTrue(s.complete());assertEquals(-50,s.net(50),0);}
    @Test public void emptyDayIsDistinctFromUnknownFoods(){NutritionAvailabilityService.Stats s=NutritionAvailabilityService.stats(Collections.emptyList());assertEquals(0,s.missing);assertTrue(s.complete());assertEquals(0,s.subtotal,0);}
    @Test public void totalsPersistCoverageWithoutInventingCalories(){Map<String,Object> t=FoodNutrition.totals(Arrays.asList(unknown("unknown",null)));assertEquals(1,t.get("missingCalorieCount"));assertEquals(0,t.get("knownCalorieCount"));assertEquals(false,t.get("caloriesComplete"));assertFalse(((Map<?,?>)t.get("nutrientTotals")).containsKey("energy_kcal"));}
    @Test public void legacyAggregateHasUnknownCoverageAndNoNet(){NutritionAvailabilityService.Stats s=NutritionAvailabilityService.dashboard(Values.of("caloriesConsumed",100));assertFalse(s.coverageKnown);assertNull(s.net(0));}
    @Test public void dashboardAllUnknownDoesNotUseNumericZero(){NutritionAvailabilityService.Stats s=NutritionAvailabilityService.dashboard(Values.of("caloriesConsumed",0,"foodEntryCount",1,"knownCalorieCount",0));assertNull(s.subtotal);assertEquals(1,s.missing);}
    @Test public void addCaloriesPreservesOtherSnapshotFieldsAndOriginal(){Map<String,Object> row=unknown("per_100g",100.0),resolved=NutritionAvailabilityService.addCalories(row,140);assertFalse(row.containsKey("calories"));assertEquals(140.0,resolved.get("calories"));assertEquals(2.0,((Map<?,?>)resolved.get("nutrients")).get("protein_g"));assertEquals("entry",resolved.get("entryId"));}
    @Test public void unknownSnapshotSurvivesSavedMealsAndRecipeScaling(){Map<String,Object> row=unknown("unknown",null);List<Map<String,Object>> saved=SavedMealService.snapshots(Arrays.asList(row));assertEquals("one plate",saved.get(0).get("portionDescription"));assertFalse(RecipeService.portions(saved,2,1).get(0).containsKey("calories"));}
    @Test public void unknownMeasuredSnapshotCanBeResizedWithoutCreatingCalories(){Map<String,Object> row=unknown("per_100g",100.0);Map<String,Object> resized=FoodEntryEditor.resize(row,200,1,"Dinner",0);assertFalse(resized.containsKey("calories"));assertEquals(4.0,((Map<?,?>)resized.get("nutrients")).get("protein_g"));}
    @Test(expected=IllegalArgumentException.class)public void unknownPortionRejectsBadMeasurements(){unknown("unknown",-1.0);}
    @Test(expected=IllegalArgumentException.class)public void unknownCaloriesCannotBeResolvedWithNaN(){NutritionAvailabilityService.addCalories(unknown("unknown",null),Double.NaN);}
    @Test public void invalidReportedEnergyDoesNotBecomeKnownFromConflictingLegacyField(){
        Map<String,Object> entry=Values.of("calories",100,"nutrients",Values.of("energy_kcal",-1));
        assertEquals(0,NutritionAvailabilityService.stats(Arrays.asList(entry)).known);
        assertEquals(0,((Map<?,?>)FoodNutrition.totals(Arrays.asList(entry)).get("nutrientCoverage")).get("energy_kcal"));
    }
}
