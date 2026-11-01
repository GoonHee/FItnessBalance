package com.example.fitnessbalance;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;
public class RecipeServiceTest {
    private Map<String,Object> ingredient(String name,double kcal){Map<String,Object> item=new HashMap<>(),n=new HashMap<>();item.put("name",name);item.put("totalAmount",100.0);item.put("servingAmount",100.0);item.put("servings",1.0);item.put("portionUnit","g");item.put("portionGrams",100.0);n.put("energy_kcal",kcal);item.put("nutrients",n);return item;}
    private List<Map<String,Object>> ingredients(){return Arrays.asList(ingredient("Rice",390),ingredient("Chicken",330),ingredient("Oil",90));}
    private double energy(List<Map<String,Object>> rows){return ((Number)((Map<?,?>)FoodNutrition.totals(rows).get("nutrientTotals")).get("energy_kcal")).doubleValue();}
    @Test public void dividesWholeRecipeByYield(){assertEquals(270,energy(RecipeService.portions(ingredients(),3,1)),1e-9);}
    @Test public void fractionalServingsScaleCorrectly(){assertEquals(405,energy(RecipeService.portions(ingredients(),3,1.5)),1e-9);}
    @Test public void allNutrientsAndAmountsScale(){Map<String,Object> i=ingredient("Rice",130);((Map<String,Object>)i.get("nutrients")).put("protein_g",4.0);Map<String,Object> scaled=RecipeService.scaleIngredient(i,1.5);assertEquals(150.0,scaled.get("totalAmount"));assertEquals(6.0,((Map<?,?>)scaled.get("nutrients")).get("protein_g"));assertEquals(1.5,scaled.get("servings"));}
    @Test public void sourceIngredientsAndHistoricalEntriesStayUnchanged(){List<Map<String,Object>> original=ingredients();RecipeService.portions(original,3,1);assertEquals(810,energy(original),0);assertEquals(100.0,original.get(0).get("totalAmount"));}
    @Test public void incompleteIngredientKeepsPartialCoverage(){List<Map<String,Object>> rows=new ArrayList<>(ingredients());Map<String,Object> unknown=ingredient("Unknown",0);unknown.put("nutrients",Collections.emptyMap());rows.add(unknown);List<Map<String,Object>> per=RecipeService.portions(rows,4,1);Map<?,?> totals=FoodNutrition.totals(per);assertEquals(3,((Map<?,?>)totals.get("nutrientCoverage")).get("energy_kcal"));assertFalse(per.get(3).containsKey("calories"));assertEquals(202.5,energy(per),0);}
    @Test public void recordedZeroIsKnown(){List<Map<String,Object>> rows=RecipeService.portions(Arrays.asList(ingredient("Water",0)),2,1);assertEquals(0,energy(rows),0);assertEquals(1,((Map<?,?>)FoodNutrition.totals(rows).get("nutrientCoverage")).get("energy_kcal"));}
    @Test public void loggingKeepsIngredientCoverageAndRecipeRevision(){List<Map<String,Object>> rows=RecipeService.log(ingredients(),3,1.5,"id","Lunch",2,UUID.randomUUID().toString(),"Dinner");assertEquals(3,rows.size());assertEquals(405,energy(rows),0);assertEquals("Dinner",rows.get(0).get("meal"));assertEquals(2L,rows.get(0).get("recipeRevision"));assertEquals("recipe:id",rows.get(0).get("savedMealId"));}
    @Test public void retriesUseIdenticalIds(){String op=UUID.randomUUID().toString();assertEquals(RecipeService.log(ingredients(),3,1,"id","Meal",1,op,"Lunch"),RecipeService.log(ingredients(),3,1,"id","Meal",1,op,"Lunch"));}
    @Test public void recipeCanBeSavedInAnotherMealWithoutLosingProvenance(){List<Map<String,Object>> rows=RecipeService.log(ingredients(),3,1,"id","Meal",2,UUID.randomUUID().toString(),"Lunch");assertEquals("id",SavedMealService.snapshots(rows).get(0).get("recipeId"));}
    @Test public void manualCaloriesRemainConsistentWithScaledServings(){Map<String,Object> row=ingredient("Manual",200);row.put("calculationMode","manual");row.put("manualCaloriesPerServing",200.0);Map<String,Object> scaled=RecipeService.scaleIngredient(row,.5);assertEquals(100,((Number)scaled.get("calories")).doubleValue(),0);assertEquals(200.0,scaled.get("manualCaloriesPerServing"));assertEquals(.5,scaled.get("servings"));}
    @Test(expected=IllegalArgumentException.class)public void zeroYieldRejected(){RecipeService.portions(ingredients(),0,1);}
    @Test(expected=IllegalArgumentException.class)public void negativeServingsRejected(){RecipeService.portions(ingredients(),2,-1);}
    @Test(expected=IllegalArgumentException.class)public void nanRejected(){RecipeService.positive(Double.NaN);}
    @Test(expected=IllegalArgumentException.class)public void scaledAmountLimitEnforced(){RecipeService.scaleIngredient(ingredient("Rice",130),101);}
    @Test(expected=IllegalArgumentException.class)public void overflowRejected(){RecipeService.scaleIngredient(ingredient("Corrupt",Double.MAX_VALUE),2);}
    @Test(expected=IllegalArgumentException.class)public void emptyRecipeRejected(){RecipeService.portions(Collections.emptyList(),1,1);}
    @Test public void numericSnapshotsCanonicalAcrossJsonAndFirestore(){
        Map<String,Object> integer=ingredient("Rice",130),decimal=ingredient("Rice",130);
        integer.put("servings",1);integer.put("totalAmount",100L);
        assertEquals(SavedMealService.snapshots(Arrays.asList(integer)),SavedMealService.snapshots(Arrays.asList(decimal)));
    }
    @Test public void loggedManualIngredientRetainsEnteredMacrosWhenDiaryPortionEdited(){
        Map<String,Object> row=ingredient("User ingredient",130);row.put("calculationMode","user_ingredient");
        ((Map<String,Object>)row.get("nutrients")).put("protein_g",5.0);
        Map<String,Object> logged=RecipeService.log(Arrays.asList(row),1,1,"id","Recipe",1,UUID.randomUUID().toString(),"Lunch").get(0);
        Map<String,Object> edited=FoodEntryEditor.resize(logged,200,1,"Lunch",0);
        assertEquals(10.0,((Map<?,?>)edited.get("nutrients")).get("protein_g"));
        assertEquals(260.0,edited.get("calories"));
    }
}
