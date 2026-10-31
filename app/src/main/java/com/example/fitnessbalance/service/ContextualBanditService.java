package com.example.fitnessbalance.service;

import com.example.fitnessbalance.model.WorkoutLesson;
import com.example.fitnessbalance.util.Values;
import java.util.*;

/** Disjoint LinUCB: ridge=1, alpha=0.35; context fixed before the action. */
public final class ContextualBanditService {
  public static final String VERSION = "workout-linucb-v1";
  public static final int DIM = 6;
  public static final double ALPHA = .35;
  public static final class Selection {
    public final WorkoutLesson lesson;
    public final Map<String,Object> audit;
    Selection(WorkoutLesson lesson, Map<String,Object> audit) { this.lesson=lesson; this.audit=audit; }
  }
  public static double[] context(RecommendationService.Preferences p) {
    return new double[]{1, Math.min(180,Math.max(1,p.minutes))/180.0,
      Math.min(7,Math.max(0,p.inactiveDays))/7.0,
      p.lastDifficulties.isEmpty()?0:1,
      p.lastDifficulties.contains("too_easy")?1:0,
      p.recentCategories.contains("strength")?1:0};
  }
  public Selection select(RecommendationService.Preferences p, List<WorkoutLesson> available,
      RecommendationService.Recommendation base, List<Map<String,Object>> observations) {
    if (base.lesson==null || !Arrays.asList("beginner","preference","micro","next").contains(base.reason)) return null;
    List<WorkoutLesson> choices=new ArrayList<>();
    RecommendationService rules=new RecommendationService();
    for (WorkoutLesson l:available) if (!l.getStandalone() && rules.equipmentFits(p,l)
        && l.getEstimatedMinutes()>0 && l.getEstimatedMinutes()<=base.lesson.getEstimatedMinutes()
        && l.getDifficulty().equalsIgnoreCase(base.lesson.getDifficulty())) choices.add(l);
    choices.sort(Comparator.comparing(WorkoutLesson::getId));
    if (choices.size()<2) return null;
    double[] x=context(p); WorkoutLesson chosen=base.lesson; double best=-Double.MAX_VALUE;
    List<Map<String,Object>> scores=new ArrayList<>();
    // Prefer normal rule on exact ties, then stable lesson ID order.
    choices.sort(Comparator.comparingInt(l -> l.getId().equals(base.lesson.getId())?0:1));
    for (WorkoutLesson l:choices) {
      double[][] a=new double[DIM][DIM]; double[] b=new double[DIM]; int n=0;
      for(int i=0;i<DIM;i++) a[i][i]=1;
      for(Map<String,Object> row:observations) {
        if (!VERSION.equals(row.get("version")) || !l.getId().equals(row.get("lessonId"))) continue;
        double[] z=vector(row.get("context")); double reward=Values.decimal(row.get("reward"),-1);
        if(z==null || !Double.isFinite(reward) || reward<0 || reward>1) continue;
        n++;
        for(int i=0;i<DIM;i++){b[i]+=reward*z[i];for(int j=0;j<DIM;j++)a[i][j]+=z[i]*z[j];}
      }
      double[] theta=solve(a,b), uncertainty=solve(a,x);
      double prediction=dot(theta,x), bonus=ALPHA*Math.sqrt(Math.max(0,dot(x,uncertainty)));
      double score=prediction+bonus;
      scores.add(Values.of("lessonId",l.getId(),"observations",n,"prediction",prediction,"bonus",bonus,"score",score));
      if(score>best+1e-12){best=score;chosen=l;}
    }
    return new Selection(chosen,Values.of("version",VERSION,"lessonId",chosen.getId(),"context",list(x),
      "createdAt",System.currentTimeMillis(),"baselineLessonId",base.lesson.getId(),"baselineReason",base.reason,
      "candidates",scores,"alpha",ALPHA,"ridge",1,"rewardVersion","completion-fit-v1"));
  }
  /** Credit only a bound session/lesson, once. Returns true when binding can be consumed. */
  public static boolean credit(String boundSession, Map<String,Object> decision,
      Map<String,Object> record, List<Map<String,Object>> observations) {
    if(!boundSession.equals(record.get("recordId")) || !VERSION.equals(decision.get("version"))
        || !Objects.equals(decision.get("lessonId"),record.get("lessonId"))
        || vector(decision.get("context"))==null)return false;
    Double value=reward(record);if(value==null)return false;
    for(Map<String,Object> o:observations)if(boundSession.equals(o.get("sessionId")))return true;
    Map<String,Object> row=new HashMap<>(decision);row.remove("candidates");
    row.put("sessionId",boundSession);row.put("reward",value);observations.add(row);
    return true;
  }
  public static Double reward(Map<String,Object> record) {
    double completion=Values.decimal(record.get("completionPercentage"),-1);
    String difficulty=Values.text(record.get("difficultyFeedback"),""), feeling=Values.text(record.get("feelingFeedback"),"");
    if(!Double.isFinite(completion)||completion<0||completion>100
        || !Arrays.asList("too_easy","suitable","too_difficult").contains(difficulty)
        || !Arrays.asList("energized","normal","tired","discomfort").contains(feeling))return null;
    if("discomfort".equals(feeling))return 0.0;
    double fit="suitable".equals(difficulty)?1:"too_easy".equals(difficulty)?.5:0;
    double feelingFit="energized".equals(feeling)?1:"normal".equals(feeling)?.5:0;
    return .6*completion/100+.25*fit+.15*feelingFit;
  }
  public static double[] vector(Object value){
    if(!(value instanceof List)||((List<?>)value).size()!=DIM)return null;
    double[] x=new double[DIM];
    for(int i=0;i<DIM;i++){Object v=((List<?>)value).get(i);if(!(v instanceof Number))return null;
      x[i]=((Number)v).doubleValue();if(!Double.isFinite(x[i])||x[i]<0||x[i]>1)return null;}
    return x;
  }
  private static List<Double> list(double[] x){List<Double> l=new ArrayList<>();for(double v:x)l.add(v);return l;}
  private static double dot(double[] a,double[] b){double n=0;for(int i=0;i<DIM;i++)n+=a[i]*b[i];return n;}
  private static double[] solve(double[][] a,double[] b){
    double[][] m=new double[DIM][DIM+1];for(int i=0;i<DIM;i++){System.arraycopy(a[i],0,m[i],0,DIM);m[i][DIM]=b[i];}
    for(int i=0;i<DIM;i++){
      int pivot=i;for(int j=i+1;j<DIM;j++)if(Math.abs(m[j][i])>Math.abs(m[pivot][i]))pivot=j;
      double[] t=m[i];m[i]=m[pivot];m[pivot]=t;
      double scale=m[i][i];for(int k=i;k<=DIM;k++)m[i][k]/=scale;
      for(int j=0;j<DIM;j++)if(j!=i){double f=m[j][i];for(int k=i;k<=DIM;k++)m[j][k]-=f*m[i][k];}
    }
    double[] out=new double[DIM];for(int i=0;i<DIM;i++)out[i]=m[i][DIM];return out;
  }
}
