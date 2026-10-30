package com.example.fitnessbalance;

import com.example.fitnessbalance.util.Values;
import java.util.*;

/** Deterministic single-food suggestions; not a balanced-meal generator or dietary prescription. */
final class FoodRecommendationService {
    static final String VERSION = "food-rules-v1";
    static final List<String> MEALS = Arrays.asList("breakfast","lunch","dinner","snacks");
    static final List<String> MACROS = Arrays.asList("protein_g","carbohydrate_g","fat_g");
    static final List<String> GOALS = Arrays.asList("dailyProteinGoalG","dailyCarbohydrateGoalG","dailyFatGoalG");
    static final class Suggestion {
        final String id,name,label,unit; final double amount,calories,score;
        final Map<String,Object> nutrients; final List<String> reasons; final boolean estimated;
        Suggestion(String id,String name,String label,String unit,double amount,double calories,double score,
                Map<String,Object> nutrients,List<String> reasons,boolean estimated) {
            this.id=id; this.name=name; this.label=label; this.unit=unit; this.amount=amount;
            this.calories=calories; this.score=score; this.nutrients=nutrients; this.reasons=reasons; this.estimated=estimated;
        }
        Map<String,Object> snapshot() { return Values.of("id",id,"name",name,"label",label,"unit",unit,"amount",amount,
                "calories",calories,"score",score,"nutrients",nutrients,"reasons",reasons,"estimated",estimated); }
    }
    static final class Result {
        final List<Suggestion> suggestions; final double effectiveAllowance;
        final Double remainingCalories; final boolean partialCalories,partialMacros;
        final Map<String,Object> audit;
        Result(List<Suggestion> suggestions,double allowance,Double remaining,boolean partialCalories,
                boolean partialMacros,Map<String,Object> audit) {
            this.suggestions=suggestions; this.effectiveAllowance=allowance; this.remainingCalories=remaining;
            this.partialCalories=partialCalories; this.partialMacros=partialMacros; this.audit=audit;
        }
    }
    Result recommend(Map<String,Object> profile,List<Map<String,Object>> logged,
            Set<String> recentIds,List<Map<String,Object>> foods,String meal,double allowance,boolean respectGoal) {
        if (!MEALS.contains(meal) || !Double.isFinite(allowance) || allowance<=0 || allowance>20000)
            throw new IllegalArgumentException("Invalid meal or allowance");
        List<String> diets = strictStrings(profile.get("dietaryPreferences"));
        if (!UserPreferences.DIETS.containsAll(diets)) throw new IllegalArgumentException("Unsupported dietary preference");
        Set<String> preferred = new HashSet<>(strictStrings(profile.get("preferredFoodIds")));
        Set<String> disliked = new HashSet<>(strictStrings(profile.get("dislikedFoodIds")));
        Map<String,Object> totals = FoodNutrition.totals(logged);
        Map<String,Object> coverage = Values.map(totals.get("nutrientCoverage"));
        Map<String,Object> consumed = Values.map(totals.get("nutrientTotals"));
        boolean partialCalories = Values.integer(coverage.get("energy_kcal"),0)<logged.size();
        boolean confirmed = "user_entered".equals(profile.get("nutritionGoalSource"));
        Double goal = confirmed ? FoodNutrition.number(profile.get("dailyCalorieGoal")) : null;
        Double remaining = goal != null && goal>0 && !partialCalories
                ? goal - Values.decimal(consumed.get("energy_kcal"),0) : null;
        double budget = respectGoal && remaining != null ? Math.max(0,Math.min(allowance,remaining)) : allowance;
        Map<String,Double> needs = new HashMap<>(); boolean partialMacros=false;
        for (int i=0;i<MACROS.size();i++) {
            Double target=confirmed ? FoodNutrition.number(profile.get(GOALS.get(i))) : null;
            if(target==null) continue;
            if(Values.integer(coverage.get(MACROS.get(i)),0)<logged.size()) { partialMacros=true; continue; }
            double need=target-Values.decimal(consumed.get(MACROS.get(i)),0);
            if(need>0) needs.put(MACROS.get(i),need);
        }
        Set<String> poolIds = new HashSet<>();
        for (Map<String,Object> item : foods) poolIds.add(Values.text(item.get("food_key"),""));
        List<Suggestion> ranked=new ArrayList<>(); List<Map<String,Object>> decisions=new ArrayList<>();
        Set<String> seen=new HashSet<>();
        for(Map<String,Object> food:foods) {
            String id=Values.text(food.get("food_key"),"");
            if(id.isEmpty() || !seen.add(id) || disliked.contains(id)) continue;
            if(!Boolean.TRUE.equals(food.get("recommendation_ready"))
                    || !strictStrings(food.get("meal_types")).contains(meal)) continue;
            String unit=FoodNutrition.portionUnit(Values.text(food.get("nutrition_basis"),null));
            if(unit==null || Boolean.TRUE.equals(food.get("basis_confirmation_required"))) continue;
            List<String> tags=strictStrings(food.get("dietary_tags"));
            Map<String,Object> evidence=Values.map(food.get("dietary_evidence"));
            boolean suitable=diets.isEmpty() || "reviewed".equals(food.get("dietary_tags_status"));
            for(String diet:diets) suitable &= tags.contains(diet) && !Values.text(evidence.get(diet),"").trim().isEmpty();
            if(!suitable) continue;
            Map<String,Object> nutrition=Values.map(food.get("nutrition"));
            Object options=food.get("serving_options"); if(!(options instanceof List)) continue;
            Suggestion best=null; int count=0;
            for(Object raw:(List<?>)options) {
                if(++count>5) break;
                Map<String,Object> option=Values.map(raw);
                Double amount=FoodNutrition.number(option.get("amount"));
                String label=Values.text(option.get("label"),"").trim();
                if(amount==null || amount<=0 || amount>10000 || label.isEmpty() || !unit.equals(option.get("unit"))) continue;
                Map<String,Object> scaled;
                try { scaled=FoodNutrition.servingNutrition(nutrition,Values.text(food.get("nutrition_basis"),null),unit,amount,1); }
                catch(IllegalArgumentException invalid) { continue; }
                Double energy=FoodNutrition.number(scaled.get("energy_kcal"));
                if(energy==null || energy<=0 || energy>budget) continue;
                double macro=0; int available=0;
                for(Map.Entry<String,Double> need:needs.entrySet()) {
                    Double value=FoodNutrition.number(scaled.get(need.getKey()));
                    if(value!=null) { macro+=Math.min(1,value/need.getValue()); available++; }
                }
                double score=60*(energy/budget)+(available==0?0:20*macro/available)
                        +(preferred.contains(id)?15:0)-(recentIds.contains(id)?10:0);
                List<String> reasons=new ArrayList<>(); reasons.add("allowance");
                if(preferred.contains(id)) reasons.add("preferred");
                if(!recentIds.contains(id)) reasons.add("variety");
                if(available>0 && macro>0) reasons.add("macro");
                if(!diets.isEmpty()) reasons.add("dietary");
                Suggestion suggestion=new Suggestion(id,Values.text(food.get("food_name"),id),label,unit,amount,
                        energy,score,scaled,reasons,FoodNutrition.usesEstimatedEnergy(nutrition));
                if(best==null || suggestion.score>best.score) best=suggestion;
            }
            if(best!=null) {
                ranked.add(best);
                Map<String,Object> trace=best.snapshot();
                trace.remove("name"); trace.remove("label");
                Map<String,Object> rankingNutrients = new HashMap<>();
                for (String key : Arrays.asList("energy_kcal","protein_g","carbohydrate_g","fat_g"))
                    if (best.nutrients.containsKey(key)) rankingNutrients.put(key,best.nutrients.get(key));
                trace.put("nutrients",rankingNutrients);
                trace.put("mealTypes",strictStrings(food.get("meal_types")));
                trace.put("dietaryTags",tags); trace.put("metadataVersion",Values.integer(food.get("recommendation_metadata_version"),1));
                decisions.add(trace);
            }
        }
        ranked.sort(Comparator.comparingDouble((Suggestion s)->s.score).reversed().thenComparing(s->s.id));
        List<Suggestion> top=new ArrayList<>(ranked.subList(0,Math.min(3,ranked.size())));
        Map<String,Object> audit=Values.of("ruleVersion",VERSION,"createdAt",System.currentTimeMillis(),"meal",meal,
                "requestedAllowance",allowance,"effectiveAllowance",budget,"respectDailyGoal",respectGoal,
                "remainingCalories",remaining,"partialCalories",partialCalories,"partialMacros",partialMacros,
                "goalSource",profile.get("nutritionGoalSource"),"nutrientTotals",consumed,"coverage",coverage,"entryCount",logged.size(),
                "macroNeeds",needs,"dietaryPreferences",diets,"preferredFoodIds",relevant(preferred,poolIds),
                "dislikedFoodIds",relevant(disliked,poolIds),"recentFoodIds",relevant(recentIds,poolIds),
                "eligibleScores",decisions,"catalogueCount",foods.size());
        Map<String,Object> goalInputs = new HashMap<>();
        for (String key : UserPreferences.GOALS) if (FoodNutrition.number(profile.get(key)) != null) goalInputs.put(key,profile.get(key));
        audit.put("nutritionGoals",goalInputs);
        List<String> selectedIds = new ArrayList<>();
        for(Suggestion s:top) selectedIds.add(s.id);
        audit.put("selectedFoodIds",selectedIds);
        List<Map<String,Object>> selectedSnapshots = new ArrayList<>();
        for (Suggestion suggestion : top) selectedSnapshots.add(suggestion.snapshot());
        audit.put("suggestions",selectedSnapshots);
        return new Result(top,budget,remaining,partialCalories,partialMacros,audit);
    }
    private List<String> relevant(Set<String> values, Set<String> pool) {
        List<String> result = new ArrayList<>();
        for (String value : values) if (pool.contains(value)) result.add(value);
        Collections.sort(result); return result;
    }
    static List<String> strictStrings(Object raw) {
        List<String> result=new ArrayList<>(); if(raw==null) return result;
        if(!(raw instanceof List)) throw new IllegalArgumentException("Invalid preference metadata");
        for(Object item:(List<?>)raw) {
            if(!(item instanceof String)) throw new IllegalArgumentException("Invalid preference metadata");
            result.add((String)item);
        }
        return result;
    }
}
