package com.example.fitnessbalance;

import java.util.*;

final class ProgressCsv {
    private ProgressCsv(){}
    static String week(ProgressService.Week week){StringBuilder out=new StringBuilder("\ufeffdate,food_kcal,logged_exercise_kcal,food_minus_logged_exercise_kcal,protein_g,carbohydrate_g,fat_g,fiber_g,sodium_mg,partial_food_calories,food_entry_count,energy_entries_with_data,protein_entries_with_data,carbohydrate_entries_with_data,fat_entries_with_data,fiber_entries_with_data,sodium_entries_with_data\r\n");for(ProgressService.Day d:week.days){List<Object> row=new ArrayList<>(Arrays.asList(d.date,d.food,d.exercise,d.net,d.nutrient("protein_g"),d.nutrient("carbohydrate_g"),d.nutrient("fat_g"),d.nutrient("fiber_g"),d.nutrient("sodium_mg"),d.partialEnergy));row.add(d.entries);for(String key:FoodNutrition.KEYS)row.add(com.example.fitnessbalance.util.Values.integer(d.coverage.get(key),0));append(out,row);}return out.toString();}
    static String weights(List<WeightHistoryService.Entry> entries){StringBuilder out=new StringBuilder("\ufeffdate,weight_kg\r\n");for(WeightHistoryService.Entry e:entries)append(out,Arrays.asList(e.day,e.kg));return out.toString();}
    private static void append(StringBuilder out,List<?> row){for(int i=0;i<row.size();i++){if(i>0)out.append(',');Object value=row.get(i);if(value!=null){String s=value.toString();out.append('"').append(s.replace("\"","\"\"")).append('"');}}out.append("\r\n");}
}
