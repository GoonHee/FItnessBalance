package com.example.fitnessbalance;
import java.util.*;
final class NutritionAvailabilityService {
    private NutritionAvailabilityService(){}
    static final class Stats {
        final int entries,known,missing;final Double subtotal;final boolean coverageKnown;
        Stats(int entries,int known,Double subtotal,boolean coverageKnown){this.entries=entries;this.known=known;this.missing=Math.max(0,entries-known);this.subtotal=subtotal;this.coverageKnown=coverageKnown;}
        boolean complete(){return coverageKnown&&missing==0;}
        Double net(double exercise){return complete()&&subtotal!=null?subtotal-exercise:null;}
    }
    static Stats stats(List<Map<String,Object>> entries){double total=0;int known=0;for(Map<String,Object> entry:entries){Double kcal=energy(entry);if(kcal!=null){total+=kcal;known++;}}
        return new Stats(entries.size(),known,known>0||entries.isEmpty()?total:null,true);}
    static Double energy(Map<String,Object> entry){Object n=entry.get("nutrients");Object value=n instanceof Map?((Map<?,?>)n).get("energy_kcal"):null;return FoodNutrition.number(value==null?entry.get("calories"):value);}
    static Stats legacy(Object total){return new Stats(0,0,FoodNutrition.number(total),false);}
    static Stats dashboard(Map<String,Object> data){
        Double sum=FoodNutrition.number(data.get("caloriesConsumed"));
        Object raw=data.get("foodEntryCount");int entries=raw instanceof Number?((Number)raw).intValue():0;
        if(entries>0){Object count=data.get("knownCalorieCount");if(!(count instanceof Number)&&data.get("nutrientCoverage") instanceof Map)count=((Map<?,?>)data.get("nutrientCoverage")).get("energy_kcal");
            if(count instanceof Number){int known=((Number)count).intValue();if(known>=0&&known<=entries)return new Stats(entries,known,known==0?null:sum,true);}return legacy(sum);}
        return sum!=null&&sum>0?legacy(sum):stats(Collections.emptyList());
    }
    static Map<String,Object> unknown(String name,String id,String entryId,String meal,Map<String,Object> source,
            String basis,String unit,Double amount,double count,String note){
        if(note==null)note="";if(note.length()>200)throw new IllegalArgumentException("Portion note must be at most 200 characters.");
        Map<String,Object> entry=new HashMap<>(),nutrients=new HashMap<>();entry.put("name",name);entry.put("foodId",id);entry.put("entryId",entryId);entry.put("meal",meal);
        if(amount!=null){PersonalServingService.validate("portion",amount,unit);double total=FoodNutrition.totalAmount(amount,count);entry.put("servingAmount",amount);entry.put("servings",count);entry.put("totalAmount",total);entry.put("portionUnit",unit);
            entry.put("g".equals(unit)?"portionGrams":"portionMillilitres",total);
            if(unit.equals(FoodNutrition.portionUnit(basis)))nutrients.putAll(FoodNutrition.servingNutrition(source,basis,unit,amount,count));}
        nutrients.remove("energy_kcal");entry.put("nutrients",nutrients);entry.put("portionDescription",note.trim());entry.put("nutritionBasis",basis);
        entry.put("calculationMode","unknown");entry.put("nutritionStatus","calories_unavailable");entry.put("energyMethod","unavailable");return entry;
    }
    static Map<String,Object> addCalories(Map<String,Object> original,double kcal){
        if(FoodNutrition.number(kcal)==null)throw new IllegalArgumentException("Calories must be finite and non-negative.");
        Map<String,Object> result=new HashMap<>(original),nutrients=new HashMap<>();Object raw=original.get("nutrients");if(raw instanceof Map)for(Map.Entry<?,?> e:((Map<?,?>)raw).entrySet())if(e.getKey() instanceof String)nutrients.put((String)e.getKey(),e.getValue());
        nutrients.put("energy_kcal",kcal);result.put("nutrients",nutrients);result.put("calories",kcal);result.put("calculationMode","user_ingredient");result.put("energyMethod","user_entered");result.put("nutritionStatus","user_entered_calories");return result;
    }
}
