package com.example.fitnessbalance.health;
import com.example.fitnessbalance.util.Values;
import java.time.*;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;
public class WearableWorkoutServiceTest {
    private final long end=Instant.parse("2026-11-02T12:00:00Z").toEpochMilli(),start=end-WearableWorkoutService.WINDOW;
    private Map<String,Object> row(String id,long modified){return Values.of("id",id,"source","com.example.companion","activity","Walking","title","Evening walk","type",79,"start",end-3600000,"end",end-1800000,"modified",modified);}
    private Map<String,Object> snapshot(List<Map<String,Object>> rows){return Values.of("state","available","windowStart",start,"windowEnd",end,"syncedAt",end+1000,"records",rows);}
    @Test public void importsOnlyAllowedMetadataWithoutCaloriesOrRewards(){Map<String,Object> input=row("one",end);input.put("calories",999);input.put("xp",100);Map<String,Object> parsed=WearableWorkoutService.validate(snapshot(Arrays.asList(input))).get(0);assertEquals("one",parsed.get("id"));assertFalse(parsed.containsKey("calories"));assertFalse(parsed.containsKey("xp"));assertEquals(1800000L,WearableWorkoutService.number(parsed.get("end"))-WearableWorkoutService.number(parsed.get("start")));}
    @Test public void sameRecordIdDeduplicatesAndNewestVersionWins(){Map<String,Object> old=row("one",end-2000),latest=row("one",end);latest.put("title","Updated");List<Map<String,Object>> rows=WearableWorkoutService.validate(snapshot(Arrays.asList(latest,old)));assertEquals(1,rows.size());assertEquals("Updated",rows.get(0).get("title"));}
    @Test public void identicalRepeatReadProducesIdenticalSnapshot(){Map<String,Object> data=snapshot(Arrays.asList(row("one",end)));assertEquals(WearableWorkoutService.validate(data),WearableWorkoutService.validate(data));}
    @Test public void distinctIdsWithSameTimesAreRetainedForReview(){assertEquals(2,WearableWorkoutService.validate(snapshot(Arrays.asList(row("one",end),row("two",end)))).size());}
    @Test public void completeEmptyResponseIsValidForDeletionReconciliation(){assertTrue(WearableWorkoutService.validate(snapshot(Collections.emptyList())).isEmpty());}
    @Test public void sourceTitleMayBeAbsentWithoutInventingActivity(){Map<String,Object> input=row("one",0);input.remove("title");assertEquals("",WearableWorkoutService.validate(snapshot(Arrays.asList(input))).get(0).get("title"));}
    @Test public void newestSessionsAreSortedFirst(){Map<String,Object> old=row("old",end);old.put("start",end-7200000);List<Map<String,Object>> rows=WearableWorkoutService.validate(snapshot(Arrays.asList(old,row("new",end))));assertEquals("new",rows.get(0).get("id"));}
    @Test public void overlapExcludesAdjacentAndInvalidIntervals(){assertTrue(WearableWorkoutService.overlaps(100,200,150,250));assertFalse(WearableWorkoutService.overlaps(100,200,200,300));assertFalse(WearableWorkoutService.overlaps(100,100,50,150));}
    @Test public void overnightDatesUseTheCurrentTimeZone(){long a=Instant.parse("2026-11-01T15:30:00Z").toEpochMilli(),b=Instant.parse("2026-11-01T17:00:00Z").toEpochMilli();assertEquals(new HashSet<>(Arrays.asList("2026-11-01","2026-11-02")),WearableWorkoutService.days(a,b,ZoneId.of("Asia/Kuala_Lumpur")));}
    @Test public void midnightEndDoesNotFlagTheFollowingDay(){long a=Instant.parse("2026-11-01T15:30:00Z").toEpochMilli(),b=Instant.parse("2026-11-01T16:00:00Z").toEpochMilli();assertEquals(Collections.singleton("2026-11-01"),WearableWorkoutService.days(a,b,ZoneId.of("Asia/Kuala_Lumpur")));}
    @Test(expected=IllegalArgumentException.class)public void failedReadsCannotReplaceCache(){Map<String,Object> data=snapshot(Collections.emptyList());data.put("state","error");WearableWorkoutService.validate(data);}
    @Test(expected=IllegalArgumentException.class)public void permissionFailureCannotBeAnEmptySuccess(){WearableWorkoutService.validate(Values.of("state","permission_required"));}
    @Test(expected=IllegalArgumentException.class)public void invalidWindowRejected(){Map<String,Object> data=snapshot(Collections.emptyList());data.put("windowStart",start-1);WearableWorkoutService.validate(data);}
    @Test(expected=IllegalArgumentException.class)public void futureSessionIsNotCompleted(){Map<String,Object> r=row("one",end);r.put("end",end+1);WearableWorkoutService.validate(snapshot(Arrays.asList(r)));}
    @Test(expected=IllegalArgumentException.class)public void outOfWindowStartRejected(){Map<String,Object> r=row("one",end);r.put("start",start-1);WearableWorkoutService.validate(snapshot(Arrays.asList(r)));}
    @Test(expected=IllegalArgumentException.class)public void backwardsIntervalRejected(){Map<String,Object> r=row("one",end);r.put("end",r.get("start"));WearableWorkoutService.validate(snapshot(Arrays.asList(r)));}
    @Test(expected=IllegalArgumentException.class)public void missingIdentityRejected(){Map<String,Object> r=row("",end);WearableWorkoutService.validate(snapshot(Arrays.asList(r)));}
    @Test(expected=IllegalArgumentException.class)public void sameIdAcrossDifferentSourcesIsInconsistent(){Map<String,Object> a=row("same",end),b=row("same",end);b.put("source","other.app");WearableWorkoutService.validate(snapshot(Arrays.asList(a,b)));}
    @Test(expected=IllegalArgumentException.class)public void overLimitReadCannotSilentlyTruncate(){List<Map<String,Object>> rows=new ArrayList<>();for(int i=0;i<1001;i++)rows.add(row("id"+i,end));WearableWorkoutService.validate(snapshot(rows));}
    @Test(expected=IllegalArgumentException.class)public void invalidTimestampRejected(){WearableWorkoutService.number(Double.NaN);}
}
