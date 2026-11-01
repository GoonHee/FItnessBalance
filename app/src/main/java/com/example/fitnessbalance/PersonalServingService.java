package com.example.fitnessbalance;
final class PersonalServingService {
    private PersonalServingService(){}
    static String key(String foodId,String barcode){String key=barcode!=null?"barcode:"+barcode:"food:"+(foodId==null?"":foodId);
        if(key.equals("food:")||key.length()>300)throw new IllegalArgumentException("Food identity is unavailable.");return key;}
    static void validate(String name,double amount,String unit){SavedMealService.name(name);RecipeService.positive(amount);
        if(!"g".equals(unit)&&!"ml".equals(unit))throw new IllegalArgumentException("Use grams or millilitres.");}
    static boolean compatible(String basis,String unit,boolean manual){String expected=FoodNutrition.portionUnit(basis);return manual || expected!=null&&expected.equals(unit);}
}
