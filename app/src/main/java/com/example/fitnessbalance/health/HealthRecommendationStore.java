package com.example.fitnessbalance.health;

import android.content.Context;
import com.example.fitnessbalance.service.AdaptiveRecommendationService;
import com.example.fitnessbalance.util.Values;
import java.io.IOException;
import java.time.*;
import java.util.*;

/** Bounded account-private, no-backup audit and baseline. Never uploads health inputs. */
public final class HealthRecommendationStore {
    private final HealthSummaryStore file;
    public HealthRecommendationStore(Context context, String uid) { file = new HealthSummaryStore(context,uid,"adaptive"); }
    public boolean enabled() { return Boolean.TRUE.equals(file.read().get("enabled")); }
    public void setEnabled(boolean enabled) throws IOException {
        Map<String,Object> data = file.read();
        data.put("enabled",enabled);
        if (!enabled) { data.remove("history"); data.remove("decisions"); }
        file.write(data);
    }
    public void clear() { file.clear(); }
    public void removeSleepInputs() throws IOException {
        Map<String,Object> data = file.read(); data.remove("history"); data.remove("decisions"); file.write(data);
    }
    public List<Map<String,Object>> history() { return rows(file.read().get("history")); }
    public void observe(Map<String,Object> snapshot, long now, ZoneId zone) throws IOException {
        Map<String,Object> data = file.read(); if (!Boolean.TRUE.equals(data.get("enabled"))) return;
        Map<String,Object> sample = AdaptiveRecommendationService.sample(snapshot,now,zone);
        if (sample.isEmpty()) return;
        LocalDate day = Instant.ofEpochMilli(((Number)sample.get("observedAt")).longValue()).atZone(zone).toLocalDate();
        List<Map<String,Object>> history = rows(data.get("history"));
        history.removeIf(old -> {
            Object time = old.get("observedAt");
            if (!(time instanceof Number) || !zone.getId().equals(old.get("zone"))) return true;
            LocalDate date = Instant.ofEpochMilli(((Number)time).longValue()).atZone(zone).toLocalDate();
            return date.equals(day) || date.isBefore(day.minusDays(14)) || date.isAfter(day);
        });
        history.add(0,sample); while(history.size()>14) history.remove(history.size()-1);
        data.put("history",history); file.write(data);
    }
    public String record(Map<String,Object> decision) throws IOException {
        Map<String,Object> data = file.read(); List<Map<String,Object>> records = rows(data.get("decisions"));
        records.add(0,new HashMap<>(decision)); while(records.size()>20) records.remove(records.size()-1);
        data.put("decisions",records);
        while(records.size()>1 && new org.json.JSONObject(data).toString().getBytes(java.nio.charset.StandardCharsets.UTF_8).length>60000) records.remove(records.size()-1);
        file.write(data); return (String)decision.get("id");
    }
    public void confirm(String id) throws IOException {
        if (id == null) return;
        Map<String,Object> data = file.read(); List<Map<String,Object>> records = rows(data.get("decisions"));
        for (Map<String,Object> record : records) if (id.equals(record.get("id"))) {
            record.put("confirmation","accepted_for_review"); record.put("confirmedAt",System.currentTimeMillis());
        }
        data.put("decisions",records); file.write(data);
    }
    private List<Map<String,Object>> rows(Object raw) {
        List<Map<String,Object>> result = new ArrayList<>();
        if (raw instanceof List) for(Object row:(List<?>)raw) if(row instanceof Map) result.add(Values.map(row)); return result;
    }
}
