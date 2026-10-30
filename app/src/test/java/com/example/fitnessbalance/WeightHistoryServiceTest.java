package com.example.fitnessbalance;

import org.junit.Test;
import java.time.LocalDate;
import java.util.*;
import static org.junit.Assert.*;

public class WeightHistoryServiceTest {
    private final LocalDate today=LocalDate.parse("2026-10-30");
    @Test public void addingAndEditingOneDateDoesNotCreateDuplicates(){TreeMap<String,WeightHistoryService.Entry> index=WeightHistoryService.mutate(new TreeMap<>(),"2026-10-29",0,70,false,today);TreeMap<String,WeightHistoryService.Entry> changed=WeightHistoryService.mutate(index,"2026-10-29",1,69.5,false,today);assertEquals(1,changed.size());assertEquals(2,changed.get("2026-10-29").revision);assertEquals(70,index.get("2026-10-29").kg,0);}
    @Test public void entriesStayChronologicalAndDeleteDoesNotInventAnotherWeight(){TreeMap<String,WeightHistoryService.Entry> index=WeightHistoryService.mutate(new TreeMap<>(),"2026-10-29",0,70,false,today);index=WeightHistoryService.mutate(index,"2026-10-20",0,72,false,today);assertEquals("2026-10-20",index.firstKey());index=WeightHistoryService.mutate(index,"2026-10-29",1,70,true,today);assertEquals("2026-10-20",index.lastKey());assertEquals(1,index.size());}
    @Test(expected=IllegalStateException.class) public void staleRevisionCannotOverwrite(){TreeMap<String,WeightHistoryService.Entry> index=WeightHistoryService.mutate(new TreeMap<>(),"2026-10-29",0,70,false,today);WeightHistoryService.mutate(index,"2026-10-29",0,68,false,today);}
    @Test public void deletionRetryDoesNotRecreateEntry(){assertTrue(WeightHistoryService.mutate(new TreeMap<>(),"2026-10-29",1,70,true,today).isEmpty());}
    @Test(expected=IllegalArgumentException.class) public void futureMeasurementRejected(){WeightHistoryService.mutate(new TreeMap<>(),"2026-10-31",0,70,false,today);}
    @Test public void invalidWeightsRejected(){for(double value:new double[]{0,-1,Double.NaN,Double.POSITIVE_INFINITY,301}){try{new WeightHistoryService.Entry("2026-10-29",value,1);fail();}catch(IllegalArgumentException expected){}}}
    @Test public void indexRoundTripPreservesMeasurements(){TreeMap<String,WeightHistoryService.Entry> index=WeightHistoryService.mutate(new TreeMap<>(),"2026-10-29",0,70.25,false,today);assertEquals(70.25,WeightHistoryService.parse(WeightHistoryService.values(index)).get("2026-10-29").kg,0);}
    @Test public void csvContainsOnlyExplicitMeasurementDates(){List<WeightHistoryService.Entry> entries=Arrays.asList(new WeightHistoryService.Entry("2026-10-20",72,1),new WeightHistoryService.Entry("2026-10-29",70,1));String csv=ProgressCsv.weights(entries);assertTrue(csv.contains("2026-10-20"));assertFalse(csv.contains("2026-10-21"));assertFalse(csv.contains("uid"));}
}
