package com.example.fitnessbalance;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;
import com.example.fitnessbalance.util.Values;
public class PersonalNutritionServiceTest {
    @Test public void labelReferenceNormalizesToPer100(){Map<String,Object> r=PersonalNutritionService.label("food","Milk",250,"ml",Values.of("energy_kcal",150,"protein_g",8),"Package label");assertEquals("per_100ml",r.get("nutrition_basis"));assertEquals(60.0,((Map<?,?>)r.get("nutrition")).get("energy_kcal"));assertEquals(90.0,FoodNutrition.servingNutrition((Map<String,Object>)r.get("nutrition"),(String)r.get("nutrition_basis"),"ml",150,1).get("energy_kcal"));}
    @Test public void optionalNutrientsRemainMissingAndZeroPreserved(){Map<String,Object> r=PersonalNutritionService.label("food","Water",100,"ml",Values.of("energy_kcal",0),"Label");assertEquals(0.0,((Map<?,?>)r.get("nutrition")).get("energy_kcal"));assertFalse(((Map<?,?>)r.get("nutrition")).containsKey("protein_g"));}
    @Test public void completeMacrosCanSupportExistingExplicitEstimate(){Map<String,Object> r=PersonalNutritionService.label("food","Food",100,"g",Values.of("protein_g",10,"carbohydrate_g",20,"fat_g",5),"Label macros");assertFalse(((Map<?,?>)r.get("nutrition")).containsKey("energy_kcal"));assertTrue(FoodNutrition.usesEstimatedEnergy((Map<String,Object>)r.get("nutrition")));}
    @Test public void alternativeKeepsOriginalIdentityAndMarksApproximation(){Map<String,Object> original=Values.of("food_name","Cooked rice","nutrition_basis","100g","nutrition",Values.of("energy_kcal",130),"source_database","Example");Map<String,Object> r=PersonalNutritionService.alternative("missing","My rice","other",original);assertEquals("missing",r.get("originalFoodId"));assertEquals("My rice",r.get("food_name"));assertEquals("other",r.get("alternativeFoodId"));assertEquals("approximate_substitution",r.get("nutrition_verification"));assertEquals("per_100g",r.get("nutrition_basis"));assertFalse(original.containsKey("source_kind"));}
    @Test public void longCatalogueNamesDoNotUseMealNameLimit(){String name=String.join("",Collections.nCopies(100,"a"));assertEquals(name,PersonalNutritionService.label("id",name,100,"g",Values.of("energy_kcal",10),"Label").get("food_name"));}
    @Test(expected=IllegalArgumentException.class)public void unknownAlternativeBasisRejected(){PersonalNutritionService.alternative("id","Food","other",Values.of("nutrition_basis","unknown","nutrition",Values.of("energy_kcal",100)));}
    @Test(expected=IllegalArgumentException.class)public void missingEnergyAndIncompleteMacrosRejected(){PersonalNutritionService.label("id","Food",100,"g",Values.of("fat_g",5),"Label");}
    @Test(expected=IllegalArgumentException.class)public void impossibleReferenceRejected(){PersonalNutritionService.label("id","Food",0,"g",Values.of("energy_kcal",100),"Label");}
    @Test(expected=IllegalArgumentException.class)public void invalidNutrientRejected(){PersonalNutritionService.label("id","Food",100,"g",Values.of("energy_kcal",Double.POSITIVE_INFINITY),"Label");}
}
