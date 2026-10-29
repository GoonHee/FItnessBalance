package com.example.fitnessbalance.service;

import com.example.fitnessbalance.model.WorkoutLesson;
import com.example.fitnessbalance.util.Values;
import java.time.*;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class AdaptiveRecommendationServiceTest {
    private final ZoneId zone = ZoneId.of("UTC");
    private final long now = Instant.parse("2026-10-29T09:00:00Z").toEpochMilli();
    private final AdaptiveRecommendationService service = new AdaptiveRecommendationService();
    private WorkoutLesson lesson(String id,int minutes) {
        WorkoutLesson l = new WorkoutLesson(); l.setId(id); l.setName(id); l.setEstimatedMinutes(minutes);
        l.setSequence(minutes); l.setDifficulty("beginner"); l.setEquipment(Arrays.asList("none"));
        l.setStandalone(id.equals("recovery")); return l;
    }
    private List<WorkoutLesson> lessons() { return Arrays.asList(lesson("long",20),lesson("short",10),lesson("recovery",5)); }
    private RecommendationService.Preferences preferences() {
        RecommendationService.Preferences p = new RecommendationService.Preferences(); p.minutes=20;
        p.durationPreferenceKnown=true; p.equipment=Arrays.asList("none"); return p;
    }
    private Map<String,Object> snapshot(long seconds) {
        return Values.of("day","2026-10-29","zone","UTC","syncedAt",now,
                "windowEnd",now,"sleepWindowStart",now-86400000,
                "sleep",Values.of("state","available","value",seconds,"observedAt",now-3600000));
    }
    private List<Map<String,Object>> history(int count) {
        List<Map<String,Object>> result = new ArrayList<>();
        for(int i=1;i<=count;i++) result.add(Values.of("seconds",8*3600L,"observedAt",now-i*86400000L-3600000,
                "windowEnd",now-i*86400000L,"zone","UTC")); return result;
    }
    private AdaptiveRecommendationService.Decision decide(Map<String,Object> health,List<Map<String,Object>> history) {
        return service.recommend(preferences(),lessons(),health,history,true,true,now,zone);
    }
    @Test public void lowerSleepChoosesNearestShorterAvailableWorkout() {
        AdaptiveRecommendationService.Decision d=decide(snapshot(5*3600),history(3));
        assertEquals("long",d.usual.lesson.getId()); assertEquals("short",d.suggested.lesson.getId());
        assertTrue(d.adjusted()); assertEquals(8*3600,d.baselineSleep); assertEquals("sleep_shortfall",d.healthReason);
        assertEquals(20,lessons().get(0).getEstimatedMinutes());
    }
    @Test public void optedOutNeverUsesHealthValues() {
        AdaptiveRecommendationService.Decision d=service.recommend(preferences(),lessons(),snapshot(5*3600),history(3),false,true,now,zone);
        assertFalse(d.adjusted()); assertEquals("disabled",d.healthReason); assertFalse(d.inputs.containsKey("currentSleep"));
    }
    @Test public void missingPermissionRetainsUsualAndOmitsHealthInputs() {
        AdaptiveRecommendationService.Decision d=service.recommend(preferences(),lessons(),snapshot(5*3600),history(3),true,false,now,zone);
        assertFalse(d.adjusted()); assertEquals("permission_unavailable",d.healthReason); assertFalse(d.inputs.containsKey("currentSleep"));
    }
    @Test public void missingAndZeroAndStaleAndFutureDataFallBack() {
        List<Map<String,Object>> bad=new ArrayList<>(); bad.add(Collections.emptyMap()); bad.add(snapshot(0));
        Map<String,Object> stale=snapshot(5*3600); stale.put("syncedAt",now-86400000); bad.add(stale);
        Map<String,Object> future=snapshot(5*3600); future.put("syncedAt",now+1); bad.add(future);
        Map<String,Object> wrongZone=snapshot(5*3600); wrongZone.put("zone","Asia/Kuala_Lumpur"); bad.add(wrongZone);
        Map<String,Object> yesterday=snapshot(5*3600); yesterday.put("day","2026-10-28"); bad.add(yesterday);
        for(Map<String,Object> data:bad) { assertFalse(decide(data,history(3)).adjusted()); assertEquals("missing_or_stale",decide(data,history(3)).healthReason); }
    }
    @Test public void fewerThanThreePreviousDaysDoesNotInventBaseline() {
        assertEquals("building_baseline",decide(snapshot(5*3600),history(2)).healthReason);
    }
    @Test public void repeatedSameDayObservationsDoNotInflateBaseline() {
        Map<String,Object> one=history(1).get(0);
        assertEquals(1,decide(snapshot(5*3600),Arrays.asList(one,one,one)).baselineDays);
    }
    @Test public void currentDayAndOldAndDifferentTimeObservationsAreExcluded() {
        List<Map<String,Object>> samples=history(2);
        samples.add(Values.of("seconds",8*3600L,"observedAt",now-3600000,"windowEnd",now,"zone","UTC"));
        samples.add(Values.of("seconds",8*3600L,"observedAt",now-20L*86400000,"windowEnd",now-20L*86400000,"zone","UTC"));
        samples.add(Values.of("seconds",8*3600L,"observedAt",now-3L*86400000+5*3600000,"windowEnd",now-3L*86400000+6*3600000,"zone","UTC"));
        assertEquals(2,decide(snapshot(5*3600),samples).baselineDays);
    }
    @Test public void discomfortAndRecoveryRemainPriority() {
        RecommendationService.Preferences p=preferences(); p.discomfort=true;
        AdaptiveRecommendationService.Decision d=service.recommend(p,lessons(),snapshot(5*3600),history(3),true,true,now,zone);
        assertFalse(d.adjusted()); assertEquals("recovery",d.suggested.lesson.getId()); assertEquals("priority",d.healthReason);
        p.discomfort=false; p.recoveryDay=true;
        assertFalse(service.recommend(p,lessons(),snapshot(5*3600),history(3),true,true,now,zone).adjusted());
    }
    @Test public void unavailableEquipmentIsNeverRecommended() {
        List<WorkoutLesson> available=lessons(); available.get(1).setEquipment(Arrays.asList("chair"));
        AdaptiveRecommendationService.Decision d=service.recommend(preferences(),available,snapshot(5*3600),history(3),true,true,now,zone);
        assertEquals("recovery",d.suggested.lesson.getId());
    }
    @Test public void normalSleepDoesNotIncreaseDurationOrChangeTargets() {
        AdaptiveRecommendationService.Decision d=decide(snapshot(8*3600),history(3));
        assertFalse(d.adjusted()); assertEquals("no_adjustment",d.healthReason);
        assertFalse(d.inputs.containsKey("dailyCalorieGoal")); assertFalse(d.inputs.containsKey("caloriesBurned"));
    }
    @Test public void noShorterAndMissingCatalogueHaveSafeFallback() {
        AdaptiveRecommendationService.Decision d=service.recommend(preferences(),Arrays.asList(lesson("long",20)),snapshot(5*3600),history(3),true,true,now,zone);
        assertFalse(d.adjusted()); assertEquals("no_shorter",d.healthReason);
        assertNull(service.recommend(preferences(),Collections.emptyList(),snapshot(5*3600),history(3),true,true,now,zone).suggested.lesson);
    }
    @Test public void auditRecordsVersionInputsAndActualUserChoice() {
        AdaptiveRecommendationService.Decision d=decide(snapshot(5*3600),history(3));
        Map<String,Object> suggested=d.audit(now,false),usual=d.audit(now,true);
        assertEquals(AdaptiveRecommendationService.VERSION,suggested.get("ruleVersion"));
        assertEquals("short",suggested.get("selectedLessonId")); assertEquals("long",usual.get("selectedLessonId"));
        assertEquals(true,suggested.get("usedHealthAdjustment")); assertEquals(false,usual.get("usedHealthAdjustment"));
        assertEquals("shown",suggested.get("confirmation")); assertSame(d.inputs,suggested.get("inputs"));
    }
}
