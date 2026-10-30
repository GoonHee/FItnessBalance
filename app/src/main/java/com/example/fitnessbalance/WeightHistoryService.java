package com.example.fitnessbalance;

import com.example.fitnessbalance.util.Values;
import java.time.LocalDate;
import java.util.*;

final class WeightHistoryService {
    static final int LIMIT=5000;
    static final class Entry {
        final String day;final double kg;final long revision;
        Entry(String day,double kg,long revision){LocalDate.parse(day);if(!Double.isFinite(kg)||kg<=0||kg>300||revision<1)throw new IllegalArgumentException("Invalid weight record");this.day=day;this.kg=kg;this.revision=revision;}
        Map<String,Object> value(){return Values.of("weightKg",kg,"revision",revision);}
    }
    static Entry entry(String day,Map<String,Object> raw) {
        Double kg=FoodNutrition.number(raw.get("weightKg"));Object revision=raw.get("revision");
        if(kg==null||!(revision instanceof Number)||((Number)revision).doubleValue()!=((Number)revision).longValue())throw new IllegalArgumentException("Invalid weight record");
        return new Entry(day,kg,((Number)revision).longValue());
    }
    static TreeMap<String,Entry> parse(Object raw) {
        TreeMap<String,Entry> result=new TreeMap<>();if(raw==null)return result;
        if(!(raw instanceof Map))throw new IllegalArgumentException("Invalid weight history");
        Map<?,?> index=(Map<?,?>)raw;if(index.size()>LIMIT)throw new IllegalArgumentException("Weight history limit");
        for(Map.Entry<?,?> item:index.entrySet()){if(!(item.getKey() instanceof String)||!(item.getValue() instanceof Map))throw new IllegalArgumentException("Invalid weight history");String day=(String)item.getKey();result.put(day,entry(day,Values.map(item.getValue())));}return result;
    }
    static TreeMap<String,Entry> mutate(TreeMap<String,Entry> existing,String day,long expected,double kg,boolean remove,LocalDate today) {
        LocalDate date=LocalDate.parse(day);if(!remove&&date.isAfter(today))throw new IllegalArgumentException("Future measurement");
        TreeMap<String,Entry> result=new TreeMap<>(existing);Entry old=result.get(day);
        if(remove&&old==null)return result;
        if((old==null?0:old.revision)!=expected)throw new IllegalStateException("Weight changed; reload before editing");
        if(remove)result.remove(day);else result.put(day,new Entry(day,kg,expected+1));
        if(result.size()>LIMIT)throw new IllegalStateException("Weight history limit");return result;
    }
    static Map<String,Object> values(TreeMap<String,Entry> index){Map<String,Object> result=new TreeMap<>();for(Entry entry:index.values())result.put(entry.day,entry.value());return result;}
}
