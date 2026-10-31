package com.example.fitnessbalance.service;
import com.example.fitnessbalance.model.WorkoutLesson;
import com.example.fitnessbalance.util.Values;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;
public class ContextualBanditServiceTest {
  private final ContextualBanditService service=new ContextualBanditService();
  private WorkoutLesson lesson(String id,int duration){WorkoutLesson l=new WorkoutLesson();l.setId(id);l.setEstimatedMinutes(duration);l.setDifficulty("beginner");l.setEquipment(Arrays.asList("none"));return l;}
  private RecommendationService.Preferences prefs(){RecommendationService.Preferences p=new RecommendationService.Preferences();p.minutes=20;p.durationPreferenceKnown=true;p.equipment=Arrays.asList("none");return p;}
  private List<WorkoutLesson> choices(){return Arrays.asList(lesson("a",20),lesson("b",10));}
  private ContextualBanditService.Selection select(List<Map<String,Object>> data){List<WorkoutLesson> c=choices();return service.select(prefs(),c,new RecommendationService.Recommendation(c.get(0),"preference"),data);}
  private Map<String,Object> observation(String id,double reward,List<Double> x){return Values.of("version",ContextualBanditService.VERSION,"lessonId",id,"context",x,"reward",reward);}
  private List<Double> context(){List<Double> out=new ArrayList<>();for(double x:ContextualBanditService.context(prefs()))out.add(x);return out;}
  @Test public void coldStartUsesRuleOnTies(){assertEquals("a",select(Collections.emptyList()).lesson.getId());}
  @Test public void learnsBetterReward(){List<Map<String,Object>> rows=new ArrayList<>();for(int i=0;i<10;i++){rows.add(observation("a",0,context()));rows.add(observation("b",1,context()));}assertEquals("b",select(rows).lesson.getId());}
  @Test public void exploresUnobservedArmAfterPoorOutcome(){assertEquals("b",select(Arrays.asList(observation("a",0,context()))).lesson.getId());}
  @Test public void priorityRulesNeverUseBandit(){for(String reason:Arrays.asList("discomfort","recovery","comeback","easier","balance","equipment","sleep_shortfall"))assertNull(service.select(prefs(),choices(),new RecommendationService.Recommendation(choices().get(0),reason),Collections.emptyList()));}
  @Test public void singleEligibleActionFallsBack(){WorkoutLesson base=lesson("a",10);assertNull(service.select(prefs(),Arrays.asList(base,lesson("b",20)),new RecommendationService.Recommendation(base,"preference"),Collections.emptyList()));}
  @Test public void noEquipmentOrDifficultyEscalation(){List<WorkoutLesson> c=choices();c.get(1).setEquipment(Arrays.asList("dumbbells"));assertNull(service.select(prefs(),c,new RecommendationService.Recommendation(c.get(0),"preference"),Collections.emptyList()));c.get(1).setEquipment(Arrays.asList("none"));c.get(1).setDifficulty("advanced");assertNull(service.select(prefs(),c,new RecommendationService.Recommendation(c.get(0),"preference"),Collections.emptyList()));}
  @Test public void standaloneExcluded(){List<WorkoutLesson> c=choices();c.get(1).setStandalone(true);assertNull(service.select(prefs(),c,new RecommendationService.Recommendation(c.get(0),"preference"),Collections.emptyList()));}
  @Test public void contextChangesWithPreferencesAndHistory(){RecommendationService.Preferences p=prefs();double[] first=ContextualBanditService.context(p);p.minutes=5;p.inactiveDays=3;p.lastDifficulties=Arrays.asList("too_easy");p.recentCategories=Arrays.asList("strength");assertFalse(Arrays.equals(first,ContextualBanditService.context(p)));}
  @Test public void corruptTrainingIgnored(){List<Map<String,Object>> data=Arrays.asList(observation("b",Double.NaN,context()),observation("b",1,Arrays.asList(1.0)));assertEquals("a",select(data).lesson.getId());}
  @Test public void unknownVersionIgnored(){Map<String,Object> row=observation("b",1,context());row.put("version","old");assertEquals("a",select(Arrays.asList(row)).lesson.getId());}
  @Test public void rewardBoundedAndDiscomfortZero(){Map<String,Object> r=Values.of("completionPercentage",100,"difficultyFeedback","suitable","feelingFeedback","energized");assertEquals(1,ContextualBanditService.reward(r),1e-9);r.put("feelingFeedback","discomfort");assertEquals(0,ContextualBanditService.reward(r),0);}
  @Test public void invalidFeedbackNotNegativeReward(){assertNull(ContextualBanditService.reward(Values.of("completionPercentage",100)));assertNull(ContextualBanditService.reward(Values.of("completionPercentage",101,"difficultyFeedback","suitable","feelingFeedback","normal")));}
  @Test public void auditIncludesExactFeaturesAndCandidateScores(){Map<String,Object> audit=select(Collections.emptyList()).audit;assertEquals(context(),audit.get("context"));assertEquals(2,((List<?>)audit.get("candidates")).size());assertEquals(ContextualBanditService.VERSION,audit.get("version"));}
  @Test public void savedSessionOnlyCreditedOnce(){
    Map<String,Object> d=select(Collections.emptyList()).audit;
    Map<String,Object> r=Values.of("recordId","session","lessonId",d.get("lessonId"),"completionPercentage",100,"difficultyFeedback","suitable","feelingFeedback","normal");
    List<Map<String,Object>> rows=new ArrayList<>();
    assertTrue(ContextualBanditService.credit("session",d,r,rows));assertTrue(ContextualBanditService.credit("session",d,r,rows));assertEquals(1,rows.size());
    assertEquals(.925,((Number)rows.get(0).get("reward")).doubleValue(),1e-9);
  }
  @Test public void unrelatedSessionOrLessonNeverTrains(){
    Map<String,Object> d=select(Collections.emptyList()).audit;
    Map<String,Object> r=Values.of("recordId","other","lessonId",d.get("lessonId"),"completionPercentage",100,"difficultyFeedback","suitable","feelingFeedback","normal");
    List<Map<String,Object>> rows=new ArrayList<>();
    assertFalse(ContextualBanditService.credit("session",d,r,rows));r.put("recordId","session");r.put("lessonId","other");
    assertFalse(ContextualBanditService.credit("session",d,r,rows));assertTrue(rows.isEmpty());
  }
  @Test public void learnedRankingDependsOnContext(){
    RecommendationService.Preferences shortP=prefs();shortP.minutes=1;
    List<Double> shortX=new ArrayList<>();for(double x:ContextualBanditService.context(shortP))shortX.add(x);
    List<Map<String,Object>> rows=new ArrayList<>();for(int i=0;i<100;i++){
      rows.add(observation("a",1,context()));rows.add(observation("b",0,context()));
      rows.add(observation("a",0,shortX));rows.add(observation("b",1,shortX));
    }
    assertEquals("a",select(rows).lesson.getId());List<WorkoutLesson> c=choices();
    assertEquals("b",service.select(shortP,c,new RecommendationService.Recommendation(c.get(0),"preference"),rows).lesson.getId());
  }
}
