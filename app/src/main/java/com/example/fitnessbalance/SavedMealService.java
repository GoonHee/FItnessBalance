package com.example.fitnessbalance;

import java.nio.charset.StandardCharsets;
import java.util.*;

/** Reusable historical portions, not a recipe or a mutable catalogue lookup. */
final class SavedMealService {
    static final int MAX_ITEMS=25;
    private static final List<String> FIELDS=Arrays.asList("foodId","name","servingAmount","servings",
        "portionUnit","totalAmount","portionGrams","portionMillilitres","nutritionBasis","sourceDatabase",
        "barcode","sourceUrl","basisConfirmationSource","calculationMode","energyMethod",
        "nutritionVerification","manualCaloriesPerServing","recipeId","recipeName","recipeRevision","recipeYield","recipeServings","portionDescription","nutritionStatus","personalNutritionKind","personalNutritionRevision","alternativeFoodId","alternativeName");
    private SavedMealService(){}
    static String name(String value){String name=value==null?"":value.trim();
        if(name.isEmpty()||name.length()>80)throw new IllegalArgumentException("Use a meal name of 1–80 characters.");return name;}
    static List<Map<String,Object>> snapshots(List<Map<String,Object>> entries){
        if(entries==null||entries.isEmpty()||entries.size()>MAX_ITEMS)throw new IllegalArgumentException("Select 1–25 foods.");
        List<Map<String,Object>> out=new ArrayList<>();
        for(Map<String,Object> entry:entries){
            Map<String,Object> row=new HashMap<>();
            for(String key:FIELDS){Object value=entry.get(key);if(value==null)continue;
                if(value instanceof String){if(((String)value).length()>2000)throw new IllegalArgumentException("Food description is too long.");row.put(key,value);}
                else if(value instanceof Number){if(FoodNutrition.number(value)==null)throw new IllegalArgumentException("Invalid food amount.");if(key.equals("recipeRevision"))row.put(key,((Number)value).longValue());else row.put(key,((Number)value).doubleValue());}
                else throw new IllegalArgumentException("Invalid saved food.");}
            if(!(row.get("name") instanceof String)||((String)row.get("name")).trim().isEmpty())throw new IllegalArgumentException("A selected food has no name.");
            Map<String,Object> nutrients=new HashMap<>();Object raw=entry.get("nutrients");
            if(raw!=null && !(raw instanceof Map))throw new IllegalArgumentException("Invalid nutrients.");
            for(String key:FoodNutrition.KEYS){Object value=raw instanceof Map?((Map<?,?>)raw).get(key):null;
                if(value!=null){Double n=FoodNutrition.number(value);if(n==null)throw new IllegalArgumentException("Invalid nutrient value.");nutrients.put(key,n);}}
            if(!nutrients.containsKey("energy_kcal") && entry.get("calories")!=null){Double kcal=FoodNutrition.number(entry.get("calories"));
                if(kcal==null)throw new IllegalArgumentException("Invalid calories.");nutrients.put("energy_kcal",kcal);}
            row.put("nutrients",nutrients);if(nutrients.containsKey("energy_kcal"))row.put("calories",nutrients.get("energy_kcal"));
            out.add(row);
        }
        return out;
    }
    @SuppressWarnings("unchecked") static List<Map<String,Object>> items(Object raw){
        if(!(raw instanceof List))throw new IllegalArgumentException("Invalid saved meal.");
        List<Map<String,Object>> rows=new ArrayList<>();for(Object item:(List<?>)raw){if(!(item instanceof Map))throw new IllegalArgumentException("Invalid saved food.");rows.add((Map<String,Object>)item);}return snapshots(rows);
    }
    static List<Map<String,Object>> loggingEntries(List<Map<String,Object>> saved,String mealId,String mealName,String operation,String meal){
        if(!Arrays.asList("Breakfast","Lunch","Dinner","Snacks").contains(meal))throw new IllegalArgumentException("Choose a meal.");
        UUID.fromString(operation);List<Map<String,Object>> out=snapshots(saved);int index=0;
        for(Map<String,Object> entry:out){entry.put("entryId",UUID.nameUUIDFromBytes((operation+":"+index++).getBytes(StandardCharsets.UTF_8)).toString());
            entry.put("meal",meal);entry.put("savedMealId",mealId);entry.put("savedMealName",name(mealName));entry.put("savedMealOperation",operation);}
        return out;
    }
    static void append(List<Map<String,Object>> diary,List<Map<String,Object>> additions){
        Set<Object> ids=new HashSet<>();for(Map<String,Object> entry:diary)ids.add(entry.get("entryId"));
        for(Map<String,Object> entry:additions){Object id=entry.get("entryId");if(!(id instanceof String))throw new IllegalArgumentException("Invalid entry ID.");if(ids.add(id))diary.add(new HashMap<>(entry));}
        if(diary.size()>500)throw new IllegalArgumentException("This day has too many food entries.");
    }
}
