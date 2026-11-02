package com.example.fitnessbalance.repository;

import com.example.fitnessbalance.service.WearableRewardService;
import com.example.fitnessbalance.util.*;
import com.google.android.gms.tasks.*;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.*;
import java.util.*;

/** Prepares all reads before any mutation; the caller commits logging and rewards atomically. */
public final class WearableGamificationRepository {
  private static final String[] ACHIEVEMENTS={"first_step","routine_builder","welcome_back","balanced_week"};
  public static final class Award {
    public final Map<String,Object> result;
    private final DocumentReference user,awardRef,weekRef,dayRef;
    private final List<DocumentSnapshot> quests,achievements;
    private final Map<String,Object> profile;
    private final boolean already;
    private Award(Map<String,Object> result,DocumentReference user,DocumentReference awardRef,DocumentReference weekRef,DocumentReference dayRef,List<DocumentSnapshot> quests,List<DocumentSnapshot> achievements,Map<String,Object> profile,boolean already){this.result=result;this.user=user;this.awardRef=awardRef;this.weekRef=weekRef;this.dayRef=dayRef;this.quests=quests;this.achievements=achievements;this.profile=profile;this.already=already;}
    public void apply(Transaction tx){
      if(already||!Boolean.TRUE.equals(result.get("eligible")))return;
      String id=awardRef.getId(),day=(String)result.get("day"),week=(String)result.get("week");
      Map<String,Object> ledger=new HashMap<>(result);for(String key:Arrays.asList("eligible","metGoalWeeks","categories","bonusAwarded","lastWorkoutAtMillis"))ledger.remove(key);ledger.put("entryId",id);ledger.put("createdAt",FieldValue.serverTimestamp());tx.set(awardRef,ledger);
      if(Boolean.TRUE.equals(result.get("newDayCredit")))tx.set(dayRef,Values.of("day",day,"week",week,"awardId",id,"createdAt",FieldValue.serverTimestamp()));
      for(int n=0;n<quests.size();n++)if(!quests.get(n).exists())tx.set(quests.get(n).getReference(),Values.of("day",day,"questId",n==0?"planned":"minutes","completed",true,"xpEarned",5,"recordId",id,"completedAt",FieldValue.serverTimestamp()));
      List<String> unlocked=Values.strings(result.get("unlocked")),owned=Values.strings(profile.get("achievementIds"));
      for(DocumentSnapshot achievement:achievements)if(unlocked.contains(achievement.getId())){tx.set(achievement.getReference(),Values.of("recordId",id,"unlockedAt",FieldValue.serverTimestamp()));if(!owned.contains(achievement.getId()))owned.add(achievement.getId());}
      tx.set(weekRef,Values.of("completed",result.get("weeklyCompleted"),"categories",result.get("categories"),"bonusAwarded",result.get("bonusAwarded"),"target",result.get("weeklyTarget"),"lastRecordId",id,"updatedAt",FieldValue.serverTimestamp()));
      tx.set(user,Values.of("lastRecordId",id,"totalXp",result.get("totalXpAfter"),"level",Values.integer(result.get("totalXpAfter"),0)/100+1,"metGoalWeeks",result.get("metGoalWeeks"),"achievementIds",owned,"lastWorkoutAt",new Timestamp(new Date(((Number)result.get("lastWorkoutAtMillis")).longValue()))),SetOptions.merge());
    }
  }
  public static Award prepare(Transaction tx,DocumentReference user,String id,Map<String,Object> entry,long now) throws FirebaseFirestoreException {
    WearableRewardService policy=new WearableRewardService();
    if(!policy.eligible(entry,now))return new Award(Values.of("eligible",false,"xpEarned",0,"unlocked",Collections.emptyList()),user,null,null,null,Collections.emptyList(),Collections.emptyList(),Collections.emptyMap(),false);
    DocumentReference award=user.collection("wearableWorkoutRewards").document(id);DocumentSnapshot saved=tx.get(award);
    if(saved.exists()){Map<String,Object> prior=new HashMap<>(saved.getData());prior.put("alreadyAwarded",true);return new Award(prior,user,award,null,null,Collections.emptyList(),Collections.emptyList(),Collections.emptyMap(),true);}
    long start=((Number)entry.get("startedAtMillis")).longValue();TimeZone zone=TimeZone.getTimeZone(Values.text(entry.get("timeZone"),"UTC"));String day=DateUtils.day(new Date(start),zone),week=DateUtils.week(new Date(start),zone);
    DocumentReference weekRef=user.collection("exerciseWeeks").document(week),dayRef=user.collection("wearableRewardDays").document(day);
    DocumentSnapshot profile=tx.get(user),weekly=tx.get(weekRef),credited=tx.get(dayRef);if(!profile.exists())throw new IllegalStateException("Profile missing");
    List<DocumentSnapshot> quests=new ArrayList<>(),achievements=new ArrayList<>();
    for(String q:Arrays.asList("planned","minutes"))quests.add(tx.get(user.collection("questProgress").document(day+"_"+q)));
    Set<String> owned=new HashSet<>();for(String a:ACHIEVEMENTS){DocumentSnapshot doc=tx.get(user.collection("unlockedAchievements").document(a));achievements.add(doc);if(doc.exists())owned.add(a);}
    Map<String,Object> p=new HashMap<>(profile.getData());Timestamp last=profile.getTimestamp("lastWorkoutAt");if(last!=null)p.put("lastWorkoutAtMillis",last.toDate().getTime());
    Map<String,Object> result=policy.decide(entry,p,weekly.getData()==null?Collections.emptyMap():weekly.getData(),credited.exists(),quests.get(0).exists(),quests.get(1).exists(),owned,now);
    return new Award(result,user,award,weekRef,dayRef,quests,achievements,p,false);
  }
  /** Optional claim for previously confirmed entries; never recreates a deleted activity. */
  public Task<Map<String,Object>> claim(String uid,String day,String id){
    try{ExerciseLogRepository.validateDay(day);if(id==null||!id.matches("wearable_[0-9a-f]{64}"))throw new IllegalArgumentException("Invalid wearable entry");}catch(RuntimeException e){return Tasks.forException(e);}
    return claimAttempt(uid,day,id).continueWithTask(outcome->{
      if(outcome.isSuccessful())return Tasks.forResult(outcome.getResult());
      Exception error=outcome.getException();if(error instanceof FirebaseFirestoreException&&Arrays.asList(FirebaseFirestoreException.Code.PERMISSION_DENIED,FirebaseFirestoreException.Code.ABORTED,FirebaseFirestoreException.Code.UNAVAILABLE).contains(((FirebaseFirestoreException)error).getCode()))return claimAttempt(uid,day,id);
      return Tasks.forException(error);
    });
  }
  private Task<Map<String,Object>> claimAttempt(String uid,String day,String id){
    FirebaseFirestore db=FirebaseFirestore.getInstance();DocumentReference user=db.collection("users").document(uid);
    return db.runTransaction(tx->{
      DocumentSnapshot receipt=tx.get(user.collection("wearableWorkoutLinks").document(id)),log=tx.get(user.collection("exerciseLogs").document(day));
      if(!receipt.exists()||!day.equals(receipt.getString("date")))throw new IllegalStateException("Confirm this workout before claiming progress");
      Map<String,Object> entry=null;for(Map<String,Object> row:new com.example.fitnessbalance.service.ExerciseLogService().entries(day,log.getData()==null?Collections.emptyMap():log.getData()))if(id.equals(row.get("entryId")))entry=row;
      if(entry==null)throw new IllegalStateException("The confirmed workout was deleted");
      Award award=prepare(tx,user,id,entry,System.currentTimeMillis());award.apply(tx);return award.result;
    });
  }
}
