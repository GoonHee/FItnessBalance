package com.example.fitnessbalance;

import com.example.fitnessbalance.util.Values;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class FoodRecommendationServiceTest {
    private final FoodRecommendationService service=new FoodRecommendationService();
    private Map<String,Object> food(String id,double kcal) {
        return Values.of("food_key",id,"food_name",id,"recommendation_ready",true,
                "nutrition_basis","per_100g","meal_types",Arrays.asList("lunch"),
                "nutrition",Values.of("energy_kcal",kcal,"protein_g",10.0,"carbohydrate_g",20.0,"fat_g",5.0),
                "serving_options",Arrays.asList(Values.of("label","100 g","amount",100.0,"unit","g")));
    }
    private FoodRecommendationService.Result rank(Map<String,Object> profile,List<Map<String,Object>> logged,List<Map<String,Object>> foods,double allowance,boolean cap) {
        return service.recommend(profile,logged,Collections.emptySet(),foods,"lunch",allowance,cap);
    }
    @Test public void ranksUpToThreeUniqueFoodsWithSupportedPortions() {
        Map<String,Object> a=food("a",100); List<Map<String,Object>> foods=Arrays.asList(a,a,food("b",200),food("c",300),food("d",400));
        FoodRecommendationService.Result r=rank(Collections.emptyMap(),Collections.emptyList(),foods,350,false);
        assertEquals(3,r.suggestions.size()); assertEquals("c",r.suggestions.get(0).id);
        assertEquals(100,r.suggestions.get(0).amount,0);
        assertEquals(FoodRecommendationService.VERSION,r.audit.get("ruleVersion"));
    }
    @Test public void knownGoalCapsWithoutAddingExerciseCalories() {
        Map<String,Object> p=Values.of("dailyCalorieGoal",2000,"nutritionGoalSource","user_entered");
        List<Map<String,Object>> logged=Arrays.asList(Values.of("calories",1800));
        FoodRecommendationService.Result r=rank(p,logged,Arrays.asList(food("a",150),food("b",250)),400,true);
        assertEquals(200,r.effectiveAllowance,0); assertEquals(1,r.suggestions.size());
        assertEquals(200,r.remainingCalories,0);
        assertEquals(400,rank(p,logged,Arrays.asList(food("b",250)),400,false).effectiveAllowance,0);
    }
    @Test public void reachedGoalHasNoBudgetButUserMayChooseIndependentAllowance() {
        Map<String,Object> p=Values.of("dailyCalorieGoal",2000,"nutritionGoalSource","user_entered");
        List<Map<String,Object>> logged=Arrays.asList(Values.of("calories",2100));
        assertTrue(rank(p,logged,Arrays.asList(food("a",100)),200,true).suggestions.isEmpty());
        assertEquals(1,rank(p,logged,Arrays.asList(food("a",100)),200,false).suggestions.size());
    }
    @Test public void unknownAndLegacyGoalsDoNotBecomeAnArbitraryAllowance() {
        FoodRecommendationService.Result r=rank(Values.of("dailyCalorieGoal",2000),Collections.emptyList(),Arrays.asList(food("a",200)),300,true);
        assertNull(r.remainingCalories); assertEquals(300,r.effectiveAllowance,0);
    }
    @Test public void partialCaloriesAreNotTreatedAsExactRemainingGoal() {
        Map<String,Object> p=Values.of("dailyCalorieGoal",2000,"nutritionGoalSource","user_entered");
        FoodRecommendationService.Result r=rank(p,Arrays.asList(Collections.emptyMap()),Arrays.asList(food("a",200)),300,true);
        assertTrue(r.partialCalories); assertNull(r.remainingCalories); assertEquals(300,r.effectiveAllowance,0);
    }
    @Test public void restrictionsRequireReviewedTagsAndEvidence() {
        Map<String,Object> p=Values.of("dietaryPreferences",Arrays.asList("halal")); Map<String,Object> a=food("a",200);
        a.put("dietary_tags",Arrays.asList("halal"));
        assertTrue(rank(p,Collections.emptyList(),Arrays.asList(a),300,false).suggestions.isEmpty());
        a.put("dietary_tags_status","reviewed");
        assertTrue(rank(p,Collections.emptyList(),Arrays.asList(a),300,false).suggestions.isEmpty());
        a.put("dietary_evidence",Values.of("halal","reviewed label/source"));
        assertEquals(1,rank(p,Collections.emptyList(),Arrays.asList(a),300,false).suggestions.size());
    }
    @Test public void namesNeverEstablishDietarySuitability() {
        Map<String,Object> a=food("vegetarian_salad_halal",100);
        Map<String,Object> p=Values.of("dietaryPreferences",Arrays.asList("vegetarian"));
        assertTrue(rank(p,Collections.emptyList(),Arrays.asList(a),300,false).suggestions.isEmpty());
    }
    @Test public void dislikesOverridePreferencesAndBudgetFit() {
        Map<String,Object> p=Values.of("preferredFoodIds",Arrays.asList("a"),"dislikedFoodIds",Arrays.asList("a"));
        assertEquals("b",rank(p,Collections.emptyList(),Arrays.asList(food("a",300),food("b",100)),300,false).suggestions.get(0).id);
    }
    @Test public void explicitPreferenceAndVarietyAffectRanking() {
        Map<String,Object> p=Values.of("preferredFoodIds",Arrays.asList("b"));
        assertEquals("b",rank(p,Collections.emptyList(),Arrays.asList(food("a",200),food("b",200)),300,false).suggestions.get(0).id);
        assertEquals("b",service.recommend(Collections.emptyMap(),Collections.emptyList(),Collections.singleton("a"),
                Arrays.asList(food("a",200),food("b",200)),"lunch",300,false).suggestions.get(0).id);
    }
    @Test public void excludesUnknownBasisWrongMealAndUnreviewedFood() {
        Map<String,Object> a=food("a",200); a.put("nutrition_basis","unknown");
        Map<String,Object> b=food("b",200); b.put("meal_types",Arrays.asList("breakfast"));
        Map<String,Object> c=food("c",200); c.put("recommendation_ready",false);
        assertTrue(rank(Collections.emptyMap(),Collections.emptyList(),Arrays.asList(a,b,c),300,false).suggestions.isEmpty());
    }
    @Test public void noGramMillilitreGuessOrInvalidServing() {
        Map<String,Object> a=food("a",200); a.put("serving_options",Arrays.asList(Values.of("label","100 ml","amount",100,"unit","ml")));
        assertTrue(rank(Collections.emptyMap(),Collections.emptyList(),Arrays.asList(a),300,false).suggestions.isEmpty());
        a.put("serving_options",Arrays.asList(Values.of("label","zero","amount",0,"unit","g")));
        assertTrue(rank(Collections.emptyMap(),Collections.emptyList(),Arrays.asList(a),300,false).suggestions.isEmpty());
    }
    @Test public void missingEnergyNeedsCompleteMacrosAndEstimateIsLabelled() {
        Map<String,Object> a=food("a",200); Map<String,Object> n=Values.map(a.get("nutrition")); n.remove("energy_kcal"); a.put("nutrition",n);
        FoodRecommendationService.Result r=rank(Collections.emptyMap(),Collections.emptyList(),Arrays.asList(a),300,false);
        assertEquals(165,r.suggestions.get(0).calories,0); assertTrue(r.suggestions.get(0).estimated);
        n.remove("fat_g"); assertTrue(rank(Collections.emptyMap(),Collections.emptyList(),Arrays.asList(a),300,false).suggestions.isEmpty());
    }
    @Test public void incompleteLoggedMacrosDoNotCreateFalseProteinGap() {
        Map<String,Object> p=Values.of("nutritionGoalSource","user_entered","dailyProteinGoalG",100);
        FoodRecommendationService.Result r=rank(p,Arrays.asList(Values.of("calories",100)),Arrays.asList(food("a",200)),300,false);
        assertTrue(r.partialMacros); assertFalse(r.suggestions.get(0).reasons.contains("macro"));
    }
    @Test public void completeProteinGoalChangesRankingWithoutInventingOtherGoals() {
        Map<String,Object> p=Values.of("nutritionGoalSource","user_entered","dailyProteinGoalG",100);
        Map<String,Object> low=food("low",250),high=food("high",250);
        Map<String,Object> lowN=Values.map(low.get("nutrition")),highN=Values.map(high.get("nutrition"));
        lowN.put("protein_g",5);highN.put("protein_g",20);low.put("nutrition",lowN);high.put("nutrition",highN);
        FoodRecommendationService.Result r=rank(p,Collections.emptyList(),Arrays.asList(low,high),300,false);
        assertEquals("high",r.suggestions.get(0).id); assertTrue(r.suggestions.get(0).reasons.contains("macro"));
    }
    @Test public void allSelectedDietaryPreferencesMustBeSupported() {
        Map<String,Object> p=Values.of("dietaryPreferences",Arrays.asList("halal","avoid_pork"));
        Map<String,Object> a=food("a",200);a.put("dietary_tags",Arrays.asList("halal"));a.put("dietary_tags_status","reviewed");
        a.put("dietary_evidence",Values.of("halal","source"));
        assertTrue(rank(p,Collections.emptyList(),Arrays.asList(a),300,false).suggestions.isEmpty());
    }
    @Test public void picksOneReviewedPortionPerFoodInsteadOfShrinkingArbitrarily() {
        Map<String,Object> a=food("a",500);a.put("serving_options",Arrays.asList(
                Values.of("label","50 g","amount",50,"unit","g"),Values.of("label","100 g","amount",100,"unit","g")));
        FoodRecommendationService.Result r=rank(Collections.emptyMap(),Collections.emptyList(),Arrays.asList(a),300,false);
        assertEquals(1,r.suggestions.size());assertEquals(50,r.suggestions.get(0).amount,0);assertEquals(250,r.suggestions.get(0).calories,0);
    }
    @Test public void auditDoesNotCopyUnrelatedPrivateFoodHistory() {
        Map<String,Object> p=Values.of("preferredFoodIds",Arrays.asList("a","unrelated_private_food"));
        FoodRecommendationService.Result r=rank(p,Collections.emptyList(),Arrays.asList(food("a",200)),300,false);
        assertEquals(Arrays.asList("a"),r.audit.get("preferredFoodIds"));
    }
    @Test public void sourceZeroIsNotReplacedWithAnEstimateOrSuggestedAsFoodEnergy() {
        assertTrue(rank(Collections.emptyMap(),Collections.emptyList(),Arrays.asList(food("a",0)),300,false).suggestions.isEmpty());
    }
    @Test(expected=IllegalArgumentException.class) public void unsupportedDietarySettingFailsClosed() {
        rank(Values.of("dietaryPreferences",Arrays.asList("unknown_restriction")),Collections.emptyList(),Arrays.asList(food("a",200)),300,false);
    }
    @Test(expected=IllegalArgumentException.class) public void invalidAllowanceRejected() {
        rank(Collections.emptyMap(),Collections.emptyList(),Collections.emptyList(),Double.NaN,false);
    }
}
