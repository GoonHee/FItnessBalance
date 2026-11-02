package com.example.fitnessbalance.health;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.example.fitnessbalance.util.Values;
import org.junit.*;
import org.junit.runner.RunWith;
import java.util.*;
import static org.junit.Assert.*;

/** Run on an Android device; uses random account namespaces, not a user's records. */
@RunWith(AndroidJUnit4.class)
public class WearableWorkoutStoreInstrumentedTest {
    private WearableWorkoutStore store;private String uid,other;private long end;
    @Before public void setup(){uid="workout-test-"+UUID.randomUUID();other=uid+"-other";end=System.currentTimeMillis();store=new WearableWorkoutStore(InstrumentationRegistry.getInstrumentation().getTargetContext());}
    @After public void cleanup(){if(store!=null){store.clear(uid);store.clear(other);store.close();}}
    private Map<String,Object> row(String id){return Values.of("id",id,"source","example.companion","activity","Walking","title","Test","type",79,"start",end-3600000,"end",end-1800000,"modified",end-1000);}
    private Map<String,Object> snapshot(Map<String,Object>... rows){return Values.of("state","available","windowStart",end-WearableWorkoutService.WINDOW,"windowEnd",end,"syncedAt",end+1000,"records",Arrays.asList(rows));}
    @Test public void repeatedSyncUpsertsAndAccountCachesAreIsolated(){Map<String,Object> data=snapshot(row("one"));store.apply(uid,data);store.apply(uid,data);assertEquals(1,store.read(uid).records.size());assertTrue(store.read(other).records.isEmpty());store.apply(other,snapshot(row("other")));store.clear(uid);assertEquals(1,store.read(other).records.size());}
    @Test public void completeSnapshotRemovesDeletedRecordsAndAcceptsEmptyResult(){store.apply(uid,snapshot(row("one"),row("two")));store.apply(uid,snapshot(row("two")));assertEquals("two",store.read(uid).records.get(0).get("id"));store.apply(uid,snapshot());assertTrue(store.read(uid).records.isEmpty());assertNotNull(store.read(uid).meta.get("syncedAt"));}
    @Test public void failureKeepsRowsAndLastSuccessfulSync(){store.apply(uid,snapshot(row("one")));Object last=store.read(uid).meta.get("syncedAt");store.apply(uid,Values.of("state","error"));assertEquals(1,store.read(uid).records.size());assertEquals(last,store.read(uid).meta.get("syncedAt"));assertEquals("error",store.read(uid).meta.get("status"));}
    @Test public void newerSourceDataUpdatesTheSameRecord(){store.apply(uid,snapshot(row("one")));Map<String,Object> updated=row("one");updated.put("title","Edited");updated.put("modified",end);store.apply(uid,snapshot(updated));assertEquals(1,store.read(uid).records.size());assertEquals("Edited",store.read(uid).records.get(0).get("title"));}
    @Test public void revokedPermissionRemovesItsCache(){store.apply(uid,snapshot(row("one")));store.apply(uid,Values.of("state","permission_required"));assertTrue(store.read(uid).records.isEmpty());assertTrue(store.read(uid).meta.isEmpty());}
    @Test public void invalidReadCannotDeletePreviouslySavedRows(){store.apply(uid,snapshot(row("one")));Map<String,Object> invalid=row("bad");invalid.put("end",end+10000);try{store.apply(uid,snapshot(invalid));fail("Expected rejection");}catch(IllegalArgumentException expected){}assertEquals("one",store.read(uid).records.get(0).get("id"));}
    @Test public void olderConcurrentSnapshotCannotOverwriteNewerWindow(){Map<String,Object> newer=snapshot(row("new"));newer.put("windowEnd",end+1000);newer.put("windowStart",end+1000-WearableWorkoutService.WINDOW);newer.put("syncedAt",end+2000);store.apply(uid,newer);store.apply(uid,snapshot(row("old")));assertEquals("new",store.read(uid).records.get(0).get("id"));}
    @Test public void pendingWorkoutsSurviveReopenAndStayAccountIsolated(){store.startPlan(uid,56,"Running",end);store.close();store=new WearableWorkoutStore(InstrumentationRegistry.getInstrumentation().getTargetContext());assertEquals("Running",store.plan(uid).get("activity"));assertTrue(store.plan(other).isEmpty());store.cancelPlan(uid);assertTrue(store.plan(uid).isEmpty());}
    @Test public void calorieRevocationPreservesDurationAndRemovesEnergy(){Map<String,Object> r=row("energy");r.put("activeCalories",123.5);r.put("calorieState","available");store.apply(uid,snapshot(r));assertEquals(123.5,(Double)store.read(uid).records.get(0).get("activeCalories"),0);store.removeCalories(uid);Map<String,Object> saved=store.read(uid).records.get(0);assertFalse(saved.containsKey("activeCalories"));assertEquals("permission_required",saved.get("calorieState"));assertEquals(r.get("start"),saved.get("start"));}
}
