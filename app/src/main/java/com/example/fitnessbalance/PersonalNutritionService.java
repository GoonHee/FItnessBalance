package com.example.fitnessbalance;
import java.util.*;
final class PersonalNutritionService {
    static final String CONFIRMED_REFERENCE="User-confirmed source reference";
    private PersonalNutritionService(){}
    static Map<String,Object> reference(String foodId,String name,Map<String,Object> source,double amount,String unit,String evidence,boolean confirmed){
        if(!confirmed)throw new IllegalArgumentException("Confirm the reference amount and unit before using these values.");
        Map<String,Object> result=label(foodId,name,amount,unit,source,evidence);
        result.put("source_database",CONFIRMED_REFERENCE);return result;
    }
    static Map<String,Object> label(String foodId,String name,double reference,String unit,Map<String,Object> label,String note){
        RecipeService.positive(reference);PersonalServingService.validate("reference",reference,unit);
        if(name==null||name.trim().isEmpty()||name.length()>1000)throw new IllegalArgumentException("Food name is unavailable or too long.");
        if(note==null||note.trim().isEmpty()||note.length()>300)throw new IllegalArgumentException("Describe the label or source (1–300 characters).");
        Map<String,Object> nutrition=new HashMap<>(),reported=new HashMap<>();
        for(String key:FoodNutrition.KEYS){if(label.get(key)==null)continue;Double value=FoodNutrition.number(label.get(key));if(value==null||!Double.isFinite(value*100/reference))throw new IllegalArgumentException("Invalid nutrient value.");reported.put(key,value);nutrition.put(key,value*100/reference);}
        if(FoodNutrition.number(FoodNutrition.withEstimatedEnergy(nutrition).get("energy_kcal"))==null)throw new IllegalArgumentException("Enter calories or all three macros. Otherwise log without calories.");
        Map<String,Object> result=new HashMap<>();result.put("originalFoodId",foodId);result.put("food_name",name);result.put("nutrition",nutrition);result.put("nutrition_basis","g".equals(unit)?"per_100g":"per_100ml");result.put("source_kind","label");result.put("source_database","Personal package-label nutrition");result.put("source_note",note.trim());result.put("nutrition_verification","user_entered");result.put("label_amount",reference);result.put("label_unit",unit);result.put("label_nutrition",reported);return result;
    }
    @SuppressWarnings("unchecked") static Map<String,Object> alternative(String originalId,String originalName,String candidateId,Map<String,Object> candidate){
        String basis=candidate.get("nutrition_basis") instanceof String?(String)candidate.get("nutrition_basis"):null;
        if(FoodNutrition.portionUnit(basis)==null||!(candidate.get("nutrition") instanceof Map))throw new IllegalArgumentException("This alternative has no usable reference amount.");
        Map<String,Object> nutrition=new HashMap<>();for(String key:FoodNutrition.KEYS){Double value=FoodNutrition.number(((Map<?,?>)candidate.get("nutrition")).get(key));if(value!=null)nutrition.put(key,value);}
        if(FoodNutrition.number(FoodNutrition.withEstimatedEnergy(nutrition).get("energy_kcal"))==null)throw new IllegalArgumentException("This alternative has no usable calories.");
        String name=Objects.toString(candidate.get("food_name"),candidateId);Map<String,Object> result=new HashMap<>();
        result.put("originalFoodId",originalId);result.put("food_name",originalName);result.put("nutrition",nutrition);result.put("nutrition_basis","g".equals(FoodNutrition.portionUnit(basis))?"per_100g":"per_100ml");result.put("source_kind","alternative");result.put("source_database","User-confirmed alternative: "+Objects.toString(candidate.get("source_database"),"catalogue"));
        result.put("source_note","Approximate substitution; confirm food type, preparation and unit.");result.put("nutrition_verification","approximate_substitution");result.put("alternativeFoodId",candidateId);result.put("alternativeName",name);return result;
    }
}
