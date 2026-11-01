package com.example.fitnessbalance;
import java.util.*;

final class RecipeService {
    private RecipeService(){}
    static double positive(double value){if(!Double.isFinite(value)||value<=0||value>10000)throw new IllegalArgumentException("Enter a value greater than 0 and at most 10,000.");return value;}
    static Map<String,Object> scaleIngredient(Map<String,Object> original,double factor){
        positive(factor);Map<String,Object> row=SavedMealService.snapshots(Arrays.asList(original)).get(0);
        Map<String,Object> nutrients=new HashMap<>();Map<?,?> old=(Map<?,?>)row.get("nutrients");
        for(String key:FoodNutrition.KEYS){Double n=FoodNutrition.number(old.get(key));if(n!=null){double v=n*factor;if(!Double.isFinite(v))throw new IllegalArgumentException("Nutrition total is too large.");nutrients.put(key,v);}}
        row.put("nutrients",nutrients);row.remove("calories");if(nutrients.containsKey("energy_kcal"))row.put("calories",nutrients.get("energy_kcal"));
        for(String key:Arrays.asList("servings","totalAmount","portionGrams","portionMillilitres")){Double amount=FoodNutrition.number(row.get(key));if(amount!=null)row.put(key,positive(amount*factor));}
        return row;
    }
    static List<Map<String,Object>> portions(List<Map<String,Object>> ingredients,double yield,double servings){
        positive(yield);positive(servings);double factor=positive(servings/yield);
        List<Map<String,Object>> out=new ArrayList<>();for(Map<String,Object> ingredient:SavedMealService.snapshots(ingredients))out.add(scaleIngredient(ingredient,factor));return out;
    }
    static List<Map<String,Object>> log(List<Map<String,Object>> ingredients,double yield,double servings,
            String id,String name,long revision,String operation,String meal){
        List<Map<String,Object>> rows=SavedMealService.loggingEntries(portions(ingredients,yield,servings),"recipe:"+id,name,operation,meal);
        for(Map<String,Object> row:rows){row.put("recipeId",id);row.put("recipeName",name);row.put("recipeRevision",revision);row.put("recipeYield",yield);row.put("recipeServings",servings);}return rows;
    }
}
