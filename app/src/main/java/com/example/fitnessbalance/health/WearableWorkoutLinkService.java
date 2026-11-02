package com.example.fitnessbalance.health;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import com.example.fitnessbalance.util.Values;

/** Pure policies for explicit wearable workout confirmation. No reward or medical inference. */
public final class WearableWorkoutLinkService {
    private WearableWorkoutLinkService() {}
    public static final long MINUTE=60000L;
    // Require complete, non-overlapping coverage from the session's source. No daily totals,
    // total-energy records, cross-source estimates, or proportional allocation.
    public static Double matchCalories(Map<String,?> session,List<? extends Map<String,?>> energy) {
        long from=WearableWorkoutService.number(session.get("start")),to=WearableWorkoutService.number(session.get("end"));
        List<Map<String,?>> matches=new ArrayList<>();
        for(Map<String,?> row:energy) {
            if(!Objects.equals(session.get("source"),row.get("source")))continue;
            long a=WearableWorkoutService.number(row.get("start")),b=WearableWorkoutService.number(row.get("end"));
            if(!WearableWorkoutService.overlaps(from,to,a,b))continue;
            if(a<from||b>to||b<=a)return null;
            Object value=row.get("kcal");if(!(value instanceof Number))return null;
            double n=((Number)value).doubleValue();if(!Double.isFinite(n)||n<0||n>200000)return null;
            matches.add(row);
        }
        matches.sort(Comparator.comparingLong(r->WearableWorkoutService.number(r.get("start"))));
        long cursor=from;double total=0;
        for(Map<String,?> row:matches){long a=WearableWorkoutService.number(row.get("start"));if(a!=cursor)return null;cursor=WearableWorkoutService.number(row.get("end"));total+=((Number)row.get("kcal")).doubleValue();}
        return !matches.isEmpty()&&cursor==to&&total<=200000?total:null;
    }
    public static boolean matchesPlan(Map<String,?> plan,Map<String,?> session) {
        if(plan==null||plan.isEmpty())return false;
        long began=WearableWorkoutService.number(plan.get("startedAt")),from=WearableWorkoutService.number(session.get("start")),to=WearableWorkoutService.number(session.get("end"));
        return WearableWorkoutService.number(plan.get("type"))==WearableWorkoutService.number(session.get("type"))
            && from>=began-10*MINUTE&&from<=began+30*MINUTE&&to>began;
    }
    public static String key(Map<String,?> session) {
        String identity=WearableWorkoutService.text(session.get("source"),300)+"|"+WearableWorkoutService.text(session.get("id"),256);
        try{byte[] hash=MessageDigest.getInstance("SHA-256").digest(identity.getBytes(StandardCharsets.UTF_8));StringBuilder out=new StringBuilder("wearable_");for(byte b:hash)out.append(String.format(Locale.ROOT,"%02x",b&255));return out.toString();}catch(Exception e){throw new IllegalStateException(e);}
    }
    public static Map<String,Object> entry(Map<String,?> plan,Map<String,?> session,ZoneId zone,long now) {
        if(!matchesPlan(plan,session))throw new IllegalArgumentException("Session does not match the selected workout and start time");
        long from=WearableWorkoutService.number(session.get("start")),to=WearableWorkoutService.number(session.get("end"));
        if(from<=0||to<=from||to>now||to-from>1440*MINUTE)throw new IllegalArgumentException("Invalid workout duration");
        if(WearableWorkoutService.days(from,to,zone).size()>1)throw new IllegalArgumentException("Overnight workouts cannot be confirmed yet. Keep them in wearable history to avoid counting on two dates.");
        Object raw=session.get("activeCalories");boolean available="available".equals(session.get("calorieState"))&&raw instanceof Number;
        double kcal=available?((Number)raw).doubleValue():0;
        if(!Double.isFinite(kcal)||kcal<0||kcal>200000)throw new IllegalArgumentException("Invalid active calories");
        return Values.of("name",WearableWorkoutService.text(plan.get("activity"),100),"duration",(to-from)/60000.0,
            "calories",available?kcal:null,"completed",true,"source","health_connect","sourcePackage",session.get("source"),
            "healthConnectId",session.get("id"),"startedAtMillis",from,"endedAtMillis",to,
            "calorieSource",available?"wearable_active":"unavailable","durationSource","wearable_elapsed",
            "workoutStartedAtMillis",plan.get("startedAt"),"localDay",Instant.ofEpochMilli(from).atZone(zone).toLocalDate().toString(),"timeZone",zone.getId());
    }
}
