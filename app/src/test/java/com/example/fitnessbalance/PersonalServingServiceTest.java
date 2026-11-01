package com.example.fitnessbalance;
import org.junit.Test;
import static org.junit.Assert.*;
public class PersonalServingServiceTest {
    @Test public void identitiesAreFoodAndSourceSpecific(){assertNotEquals(PersonalServingService.key("rice",null),PersonalServingService.key("soup",null));assertNotEquals(PersonalServingService.key("123",null),PersonalServingService.key("123","123"));}
    @Test public void matchingBasisPermitsAutomaticCalculation(){assertTrue(PersonalServingService.compatible("per_100g","g",false));assertFalse(PersonalServingService.compatible("per_100g","ml",false));}
    @Test public void unknownBasisDoesNotBecomeKnown(){assertFalse(PersonalServingService.compatible("unknown","g",false));assertTrue(PersonalServingService.compatible("unknown","g",true));}
    @Test public void namedBowlUsesExistingCalculation(){PersonalServingService.validate("My rice bowl",150,"g");java.util.Map<String,Object> n=new java.util.HashMap<>();n.put("energy_kcal",130.0);assertEquals(292.5,((Number)FoodNutrition.servingNutrition(n,"per_100g","g",150,1.5).get("energy_kcal")).doubleValue(),0);}
    @Test(expected=IllegalArgumentException.class)public void zeroAmountRejected(){PersonalServingService.validate("Bowl",0,"g");}
    @Test(expected=IllegalArgumentException.class)public void imaginaryUnitRejected(){PersonalServingService.validate("Bowl",150,"bowl");}
    @Test(expected=IllegalArgumentException.class)public void anonymousFoodRejected(){PersonalServingService.key(null,null);}
}
