package com.example.fitnessbalance.service;

import com.example.fitnessbalance.health.HealthSummaryPolicy;
import com.example.fitnessbalance.model.WorkoutLesson;
import com.example.fitnessbalance.util.Values;
import java.time.*;
import java.util.*;

/** Explainable, opt-in sleep rule. Never alters food goals, lesson targets, calories or XP. */
public final class AdaptiveRecommendationService {
    public static final String VERSION = "sleep-rules-v1";
    public static final int MIN_BASELINE_DAYS = 3;
    public static final long SHORTFALL_SECONDS = 3600;

    public static final class Decision {
        public final RecommendationService.Recommendation usual, suggested;
        public final String healthReason;
        public final long currentSleep, baselineSleep;
        public final int baselineDays;
        public final Map<String,Object> inputs;
        Decision(RecommendationService.Recommendation usual, RecommendationService.Recommendation suggested,
                String reason, long current, long baseline, int days, Map<String,Object> inputs) {
            this.usual = usual; this.suggested = suggested; this.healthReason = reason;
            this.currentSleep = current; this.baselineSleep = baseline; this.baselineDays = days; this.inputs = inputs;
        }
        public boolean adjusted() { return suggested != usual; }
        public Map<String,Object> audit(long now, boolean useUsual) {
            RecommendationService.Recommendation selected = useUsual ? usual : suggested;
            return Values.of("id", UUID.randomUUID().toString(), "createdAt", now, "ruleVersion", VERSION,
                    "inputs", inputs, "healthReason", healthReason, "normalReason", usual.reason, "baseRuleVersion", "workout-rules-v1",
                    "normalLessonId", usual.lesson == null ? "" : usual.lesson.getId(),
                    "selectedLessonId", selected.lesson == null ? "" : selected.lesson.getId(),
                    "selectedMinutes", selected.lesson == null ? 0 : selected.lesson.getEstimatedMinutes(),
                    "usedHealthAdjustment", !useUsual && adjusted(), "confirmation", "shown");
        }
    }
    public Decision recommend(RecommendationService.Preferences p, List<WorkoutLesson> lessons,
            Map<String,Object> snapshot, List<Map<String,Object>> history,
            boolean enabled, boolean sleepPermission, long now, ZoneId zone) {
        RecommendationService base = new RecommendationService();
        RecommendationService.Recommendation usual = base.recommend(p, lessons);
        Map<String,Object> inputs = Values.of("preferredMinutes", p.minutes, "equipment", new ArrayList<>(p.equipment),
                "fitnessLevel", p.fitnessLevel, "goal", p.goal, "preferredCategories", new ArrayList<>(p.categories),
                "recentCategories", new ArrayList<>(p.recentCategories), "recentDifficultyFeedback", new ArrayList<>(p.lastDifficulties),
                "inactiveDays", p.inactiveDays, "discomfort", p.discomfort, "scheduledRecoveryDay", p.recoveryDay,
                "durationPreferenceKnown", p.durationPreferenceKnown, "healthOptIn", enabled,
                "sleepPermission", sleepPermission, "zone", zone.getId());
        List<Map<String,Object>> candidates = new ArrayList<>();
        for (WorkoutLesson l : lessons) candidates.add(Values.of("id", l.getId(), "minutes", l.getEstimatedMinutes(),
                "equipment", l.getEquipment(), "difficulty", l.getDifficulty(), "sequence", l.getSequence(),
                "category", l.getCategory(), "standalone", l.getStandalone()));
        inputs.put("availableLessons", candidates);
        String reason = "disabled";
        long current = -1, baseline = -1;
        List<Long> samples = new ArrayList<>();
        RecommendationService.Recommendation suggested = usual;
        if (enabled && sleepPermission) {
            Map<String,Object> sourceSleep = Values.map(snapshot.get("sleep"));
            inputs.put("sleepSummary", Values.of("state", sourceSleep.get("state"),
                    "value", sourceSleep.get("value"), "observedAt", sourceSleep.get("observedAt")));
            inputs.put("healthSyncAt", snapshot.get("syncedAt"));
            inputs.put("healthDay", snapshot.get("day"));
            inputs.put("healthZone", snapshot.get("zone"));
            inputs.put("sleepWindowStart", snapshot.get("sleepWindowStart"));
            inputs.put("sleepWindowEnd", snapshot.get("windowEnd"));
            Map<String,Object> sample = sample(snapshot, now, zone);
            if (sample.isEmpty()) reason = "missing_or_stale";
            else {
                current = ((Number)sample.get("seconds")).longValue();
                inputs.put("currentSleep", sample);
                LocalDate currentDay = Instant.ofEpochMilli(((Number)sample.get("observedAt")).longValue()).atZone(zone).toLocalDate();
                Set<LocalDate> seen = new HashSet<>();
                List<Map<String,Object>> used = new ArrayList<>();
                int currentMinute = Instant.ofEpochMilli(((Number)sample.get("windowEnd")).longValue()).atZone(zone).getHour() * 60
                        + Instant.ofEpochMilli(((Number)sample.get("windowEnd")).longValue()).atZone(zone).getMinute();
                for (Map<String,Object> old : history) {
                    if (!zone.getId().equals(old.get("zone"))) continue;
                    long seconds = Values.integer(old.get("seconds"), -1);
                    long observed = old.get("observedAt") instanceof Number ? ((Number)old.get("observedAt")).longValue() : -1;
                    long end = old.get("windowEnd") instanceof Number ? ((Number)old.get("windowEnd")).longValue() : -1;
                    if (seconds < 3600 || seconds > 16 * 3600 || observed <= 0 || end < observed || end > now || end-observed >= 24L*3600*1000) continue;
                    LocalDate day = Instant.ofEpochMilli(observed).atZone(zone).toLocalDate();
                    if (!day.isBefore(currentDay) || day.isBefore(currentDay.minusDays(14))) continue;
                    int oldMinute = Instant.ofEpochMilli(end).atZone(zone).getHour() * 60 + Instant.ofEpochMilli(end).atZone(zone).getMinute();
                    int difference = Math.abs(oldMinute - currentMinute);
                    if (Math.min(difference, 1440 - difference) > 120 || !seen.add(day)) continue;
                    samples.add(seconds); used.add(new HashMap<>(old));
                }
                inputs.put("baselineSamples", used);
                reason = "building_baseline";
                if (samples.size() >= MIN_BASELINE_DAYS) {
                    Collections.sort(samples); int middle = samples.size()/2;
                    baseline = samples.size()%2 == 1 ? samples.get(middle) : (samples.get(middle-1)+samples.get(middle))/2;
                    inputs.put("baselineSeconds", baseline);
                    reason = "no_adjustment";
                    if (Arrays.asList("discomfort","recovery","comeback","easier","balance","equipment").contains(usual.reason)) reason = "priority";
                    else if (baseline - current >= SHORTFALL_SECONDS && usual.lesson != null) {
                        List<WorkoutLesson> shorter = new ArrayList<>();
                        for (WorkoutLesson l : lessons) if ((!l.getStandalone() || "recovery".equals(l.getId()))
                                && base.equipmentFits(p,l) && l.getEstimatedMinutes() > 0
                                && l.getEstimatedMinutes() < usual.lesson.getEstimatedMinutes()
                                && ("beginner".equalsIgnoreCase(l.getDifficulty()) || l.getDifficulty().equalsIgnoreCase(usual.lesson.getDifficulty()))) shorter.add(l);
                        shorter.sort(Comparator.comparingInt(WorkoutLesson::getEstimatedMinutes).reversed()
                                .thenComparingInt(WorkoutLesson::getSequence).thenComparing(WorkoutLesson::getId));
                        reason = "no_shorter";
                        if (!shorter.isEmpty()) {
                            suggested = new RecommendationService.Recommendation(shorter.get(0), "sleep_shortfall"); reason = "sleep_shortfall";
                        }
                    }
                }
            }
        } else if (enabled) reason = "permission_unavailable";
        return new Decision(usual,suggested,reason,current,baseline,samples.size(),inputs);
    }
    /** One observation per source day, and only from a fresh, explicit 24-hour window. */
    public static Map<String,Object> sample(Map<String,Object> snapshot, long now, ZoneId zone) {
        Map<String,Object> sleep = Values.map(snapshot.get("sleep"));
        if (!"available".equals(sleep.get("state")) || HealthSummaryPolicy.stale(snapshot.get("syncedAt"),now)
                || HealthSummaryPolicy.stale(sleep.get("observedAt"),now) || !zone.getId().equals(snapshot.get("zone"))) return Collections.emptyMap();
        long seconds = sleep.get("value") instanceof Number ? ((Number)sleep.get("value")).longValue() : -1;
        long end = snapshot.get("windowEnd") instanceof Number ? ((Number)snapshot.get("windowEnd")).longValue() : -1;
        long start = snapshot.get("sleepWindowStart") instanceof Number ? ((Number)snapshot.get("sleepWindowStart")).longValue() : -1;
        long observed = sleep.get("observedAt") instanceof Number ? ((Number)sleep.get("observedAt")).longValue() : -1;
        if (seconds < 3600 || seconds > 16*3600 || end <= 0 || end > now || observed > end || end-start != 24L*3600*1000
                || !LocalDate.now(java.time.Clock.fixed(Instant.ofEpochMilli(now),zone)).toString().equals(snapshot.get("day"))) return Collections.emptyMap();
        return Values.of("seconds",seconds,"observedAt",sleep.get("observedAt"),"windowEnd",end,"zone",zone.getId());
    }
}
