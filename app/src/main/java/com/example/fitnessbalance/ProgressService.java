package com.example.fitnessbalance;

import com.example.fitnessbalance.service.ExerciseLogService;
import com.example.fitnessbalance.util.Values;
import java.time.*;
import java.time.temporal.TemporalAdjusters;
import java.util.*;

/** Read-only aggregation of saved snapshots. Missing days/nutrients remain null. */
final class ProgressService {
    static final String[] MEALS={"Breakfast","Lunch","Dinner","Snacks","Unassigned"};
    static LocalDate week(LocalDate date) { return date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)); }
    static final class Day {
        final String date; final Map<String,Object> totals,coverage; final double[] meals;
        final int entries; final Double food,exercise,net; final boolean partialEnergy;
        Day(String date,Map<String,Object> totals,Map<String,Object> coverage,double[] meals,int entries,Double food,Double exercise) {
            this.date=date; this.totals=totals;this.coverage=coverage;this.meals=meals;this.entries=entries;this.food=food;this.exercise=exercise;
            this.net=food==null?null:food-(exercise==null?0:exercise);
            partialEnergy=food!=null&&Values.integer(coverage.get("energy_kcal"),0)<entries;
        }
        Double nutrient(String key) { return FoodNutrition.number(totals.get(key)); }
        boolean partial(String key) { return nutrient(key)!=null&&Values.integer(coverage.get(key),0)<entries; }
    }
    static final class Week {
        final List<Day> days; final Map<String,Object> profile; final List<Map<String,Object>> highest;
        Week(List<Day> days,Map<String,Object> profile,List<Map<String,Object>> highest) {this.days=days;this.profile=profile;this.highest=highest;}
        Double average(String key) { double total=0;int count=0; for(Day d:days) {Double n=value(d,key);if(n!=null){total+=n;count++;}}return count==0?null:total/count; }
        int count(String key) {int n=0;for(Day d:days)if(value(d,key)!=null)n++;return n;}
        Double sum(String key) {Double avg=average(key);return avg==null?null:avg*count(key);}
        boolean partial(String key) {for(Day d:days)if(d.partial(key)||("net".equals(key)&&d.partialEnergy))return true;return false;}
        Double goal(String key) {
            if(!"user_entered".equals(profile.get("nutritionGoalSource")))return null;
            String field=key.equals("energy_kcal")?"dailyCalorieGoal":key.equals("protein_g")?"dailyProteinGoalG":key.equals("carbohydrate_g")?"dailyCarbohydrateGoalG":key.equals("fat_g")?"dailyFatGoalG":null;
            return field==null?null:FoodNutrition.number(profile.get(field));
        }
        boolean completeMacros() {
            if(count("energy_kcal")==0)return false;
            for(Day d:days)if(d.entries>0)for(String k:Arrays.asList("protein_g","carbohydrate_g","fat_g"))
                if(d.nutrient(k)==null||d.partial(k))return false;
            return true;
        }
        static Double value(Day d,String key) {return key.equals("net")?d.net:key.equals("exercise")?d.exercise:d.nutrient(key);}
    }
    Week build(LocalDate start,LocalDate today,Map<String,Object> profile,
            Map<String,Map<String,Object>> foods,Map<String,Map<String,Object>> daily,
            Map<String,Map<String,Object>> manual,List<Map<String,Object>> guided) {
        List<Day> days=new ArrayList<>(); Map<String,Map<String,Object>> highest=new HashMap<>();
        ExerciseLogService exercise=new ExerciseLogService();
        for(int i=0;i<7;i++) {
            String date=start.plusDays(i).toString();
            if(start.plusDays(i).isAfter(today)) { days.add(new Day(date,new HashMap<>(),new HashMap<>(),new double[5],0,null,null));continue; }
            Map<String,Object> f=foods.getOrDefault(date,Collections.emptyMap()),d=daily.getOrDefault(date,Collections.emptyMap());
            List<Map<String,Object>> entries=foodEntries(f);
            if(!f.containsKey("foodItems")) {
                Double legacy=FoodNutrition.number(f.get("totalCalories"));if(legacy==null)legacy=FoodNutrition.number(d.get("caloriesConsumed"));
                if(legacy!=null&&legacy>0)entries.add(Values.of("calories",legacy,"legacySummary",true));
            }
            Map<String,Object> total=FoodNutrition.totals(entries),values=Values.map(total.get("nutrientTotals")),coverage=Values.map(total.get("nutrientCoverage"));
            double[] meals=new double[5];
            for(Map<String,Object> row:entries) {
                Double kcal=FoodNutrition.number(Values.map(row.get("nutrients")).get("energy_kcal"));if(kcal==null)kcal=FoodNutrition.number(row.get("calories"));
                if(kcal==null)continue;int meal=4;
                for(int m=0;m<4;m++)if(MEALS[m].equalsIgnoreCase(Values.text(row.get("meal"),"")))meal=m;
                meals[meal]+=kcal;
                if(Boolean.TRUE.equals(row.get("legacySummary")))continue;
                String name=Values.text(row.get("name"),"Unnamed food");String id=Values.text(row.get("foodId"),"name:"+name.toLowerCase(Locale.ROOT));
                Map<String,Object> aggregate=highest.get(id); if(aggregate==null)aggregate=Values.of("name",name,"calories",0.0);
                aggregate.put("calories",Values.decimal(aggregate.get("calories"),0)+kcal);highest.put(id,aggregate);
            }
            Map<String,Object> contributions=exercise.contributions(d.get("structuredExerciseContributions"));
            for(Map<String,Object> record:guided)if(date.equals(record.get("localDay")))exercise.guided(contributions,Values.text(record.get("recordId"),""),record);
            Map<String,Object> ex=exercise.totals(exercise.manualEntries(date,manual.getOrDefault(date,Collections.emptyMap()),d),contributions);
            Double burned=Values.integer(ex.get("exerciseEntryCount"),0)>0?Values.decimal(ex.get("caloriesBurned"),0):null;
            days.add(new Day(date,values,coverage,meals,entries.size(),FoodNutrition.number(values.get("energy_kcal")),burned));
        }
        List<Map<String,Object>> top=new ArrayList<>(highest.values());top.sort(Comparator.comparingDouble((Map<String,Object> r)->Values.decimal(r.get("calories"),0)).reversed());
        return new Week(days,profile,top);
    }
    @SuppressWarnings("unchecked") private List<Map<String,Object>> foodEntries(Map<String,Object> raw) {
        List<Map<String,Object>> entries=new ArrayList<>();Object items=raw.get("foodItems");
        if(items!=null&&!(items instanceof List))throw new IllegalStateException("Invalid food log");
        if(items instanceof List)for(Object item:(List<?>)items){if(!(item instanceof Map))throw new IllegalStateException("Invalid food entry");entries.add(new HashMap<>((Map<String,Object>)item));}
        return entries;
    }
}
