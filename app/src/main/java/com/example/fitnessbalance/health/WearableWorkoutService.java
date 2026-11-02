package com.example.fitnessbalance.health;
import java.time.*;
import java.util.*;

/** Complete rolling-window validation; no calorie, reward or workout-count contribution. */
public final class WearableWorkoutService {
    public static final long WINDOW=7L*24*60*60*1000;
    private WearableWorkoutService(){}
    public static long number(Object v){if(!(v instanceof Number))throw new IllegalArgumentException("Missing time");double n=((Number)v).doubleValue();long l=((Number)v).longValue();if(!Double.isFinite(n)||n!=l)throw new IllegalArgumentException("Invalid time");return l;}
    public static String text(Object v,int max){if(!(v instanceof String)||((String)v).isEmpty()||((String)v).length()>max)throw new IllegalArgumentException("Invalid session identity");return (String)v;}
    public static List<Map<String,Object>> validate(Map<String,Object> snapshot){
        if(!"available".equals(snapshot.get("state")))throw new IllegalArgumentException("Incomplete read");
        long start=number(snapshot.get("windowStart")),end=number(snapshot.get("windowEnd")),sync=number(snapshot.get("syncedAt"));
        if(start<=0||end-start!=WINDOW||sync<end)throw new IllegalArgumentException("Invalid sync window");
        Object raw=snapshot.get("records");if(!(raw instanceof List)||((List<?>)raw).size()>1000)throw new IllegalArgumentException("Incomplete records");
        Map<String,Map<String,Object>> unique=new LinkedHashMap<>();
        for(Object item:(List<?>)raw){if(!(item instanceof Map))throw new IllegalArgumentException("Invalid workout");Map<?,?> row=(Map<?,?>)item;
            String id=text(row.get("id"),256),source=text(row.get("source"),300),activity=text(row.get("activity"),100);
            String title=row.get("title") instanceof String?(String)row.get("title"):"";if(title.length()>200)throw new IllegalArgumentException("Invalid title");
            long from=number(row.get("start")),to=number(row.get("end")),modified=number(row.get("modified")),type=number(row.get("type"));
            if(from<start||from>=end||to<=from||to>end||modified<0||type<0||type>Integer.MAX_VALUE)throw new IllegalArgumentException("Invalid interval");
            Map<String,Object> record=new HashMap<>();record.put("id",id);record.put("source",source);record.put("activity",activity);record.put("title",title);record.put("start",from);record.put("end",to);record.put("modified",modified);record.put("type",type);
            Map<String,Object> old=unique.get(id);if(old!=null&&!source.equals(old.get("source")))throw new IllegalArgumentException("Conflicting record identity");
            if(old==null||modified>=number(old.get("modified")))unique.put(id,record);
        }
        List<Map<String,Object>> out=new ArrayList<>(unique.values());out.sort(Comparator.comparingLong((Map<String,Object> r)->number(r.get("start"))).reversed().thenComparing(r->(String)r.get("id")));return out;
    }
    public static boolean overlaps(long firstStart,long firstEnd,long secondStart,long secondEnd){return firstEnd>firstStart&&secondEnd>secondStart&&firstStart<secondEnd&&secondStart<firstEnd;}
    public static Set<String> days(long start,long end,ZoneId zone){Set<String> out=new HashSet<>();LocalDate first=Instant.ofEpochMilli(start).atZone(zone).toLocalDate(),last=Instant.ofEpochMilli(Math.max(start,end-1)).atZone(zone).toLocalDate();for(LocalDate day=first;!day.isAfter(last)&&out.size()<9;day=day.plusDays(1))out.add(day.toString());return out;}
}
