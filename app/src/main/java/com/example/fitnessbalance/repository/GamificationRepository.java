package com.example.fitnessbalance.repository;

import static com.example.fitnessbalance.util.FirestoreCollections.*;

import com.example.fitnessbalance.model.*;
import com.example.fitnessbalance.service.*;
import com.example.fitnessbalance.util.*;
import com.google.android.gms.tasks.Task;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.*;
import java.util.*;

/** All reads precede writes; retries compute from current server state. */
public class GamificationRepository {
  private final WorkoutRepository workouts = new WorkoutRepository();
  private final GamificationService game = new GamificationService();

  public Task<String> complete(
      String session,
      int completed,
      int skipped,
      int duration,
      int activeDuration,
      double calories,
      String difficulty,
      String feeling) {
    if (!Arrays.asList("too_easy", "suitable", "too_difficult").contains(difficulty)
        || !Arrays.asList("energized", "normal", "tired", "discomfort").contains(feeling))
      throw new IllegalArgumentException("Invalid feedback");
    DocumentReference u = workouts.user(),
        r = u.collection(RECORDS).document(session),
        s = u.collection(SESSIONS).document(session);
    Date now = new Date();
    TimeZone zone = TimeZone.getDefault();
    String day = DateUtils.day(now, zone), week = DateUtils.week(now, zone);
    return FirebaseFirestore.getInstance()
        .runTransaction(
            tx -> {
              DocumentSnapshot existing = tx.get(r);
              if (!game.canAward(existing.exists())) return session;
              DocumentSnapshot start = tx.get(s), profile = tx.get(u);
              if (!start.exists() || start.getTimestamp("startedAt") == null)
                throw new IllegalStateException("Session start unavailable");
              String path = start.getString("pathId"), lessonId = start.getString("lessonId");
              DocumentReference p = u.collection(PROGRESS).document(path);
              DocumentSnapshot progress = tx.get(p),
                  lessonDoc = tx.get(workouts.lessonRef(path, lessonId));
              WorkoutLesson lesson = lessonDoc.toObject(WorkoutLesson.class);
              if (lesson == null || !lesson.getActive() || !workouts.unlocked(lesson, progress))
                throw new IllegalStateException("Lesson locked");
              String[] questIds = {"planned", "minutes", "feedback"};
              List<DocumentSnapshot> quests = new ArrayList<>();
              for (String q : questIds)
                quests.add(tx.get(u.collection(QUEST_PROGRESS).document(day + "_" + q)));
              int planned = Values.integer(start.get("plannedExercises"), 0);
              if (planned < 1
                  || completed < 0
                  || skipped < 0
                  || completed + skipped > planned
                  || duration < 0
                  || duration > 86400
                  || activeDuration < 0
                  || activeDuration > duration
                  || !Double.isFinite(calories)
                  || calories < 0) throw new IllegalArgumentException("Invalid workout totals");
              double percentage = game.completion(completed, planned);
              boolean qualifying = percentage >= 50;
              Map<String, Object> counts = Values.map(progress.get("completionCounts")),
                  best = Values.map(progress.get("bestPercent")),
                  stars = Values.map(progress.get("bestStars"));
              int attempts = Values.integer(counts.get(lessonId), 0);
              int earnedStars = game.stars(percentage, difficulty);
              counts.put(lessonId, attempts + 1);
              best.put(lessonId, Math.max(percentage, Values.decimal(best.get(lessonId), 0)));
              stars.put(lessonId, Math.max(earnedStars, Values.integer(stars.get(lessonId), 0)));
              Map<String, Object> weeks = Values.map(profile.get("weeklyCounts")),
                  weekCategories = Values.map(profile.get("weeklyCategories"));
              int target =
                  new MomentumService()
                      .target(Values.integer(profile.get("weeklyWorkoutTarget"), 3));
              int weekly = Values.integer(weeks.get(week), 0) + (qualifying ? 1 : 0);
              weeks.put(week, weekly);
              List<String> categories = Values.strings(weekCategories.get(week));
              if (qualifying && !categories.contains(lesson.getCategory()))
                categories.add(lesson.getCategory());
              weekCategories.put(week, categories);
              List<String> metWeeks = Values.strings(profile.get("metGoalWeeks"));
              boolean weeklyBonus = weekly >= target && !metWeeks.contains(week);
              if (weeklyBonus) metWeeks.add(week);
              boolean[] conditions = {qualifying, activeDuration >= 300, true};
              int[] bonus = {5, 5, 0};
              int questBonus = 0;
              for (int i = 0; i < questIds.length; i++)
                if (conditions[i] && !Boolean.TRUE.equals(quests.get(i).get("completed"))) {
                  questBonus += bonus[i];
                  tx.set(
                      quests.get(i).getReference(),
                      Values.of(
                          "day",
                          day,
                          "questId",
                          questIds[i],
                          "completed",
                          true,
                          "xpEarned",
                          bonus[i],
                          "recordId",
                          session,
                          "completedAt",
                          FieldValue.serverTimestamp()));
                }
              int xp =
                  game.workoutXp(lesson.getBaseXp(), percentage, attempts)
                      + 2
                      + questBonus
                      + (weeklyBonus ? 30 : 0);
              int total = Values.integer(profile.get("totalXp"), 0) + xp,
                  reflections = Values.integer(profile.get("feedbackCount"), 0) + 1;
              List<String> unlocked = Values.strings(profile.get("achievementIds")),
                  newUnlocks = new ArrayList<>();
              Timestamp last = profile.getTimestamp("lastWorkoutAt");
              boolean comeback = last != null && DateUtils.daysSince(last.toDate(), now, zone) >= 7;
              boolean pathComplete = true;
              for (int i = 1; i <= 7; i++)
                if (Values.decimal(best.get(String.format(Locale.ROOT, "lesson_%02d", i)), 0) < 50)
                  pathComplete = false;
              String[] achievementIds = {
                "first_step",
                "routine_builder",
                "path_explorer",
                "welcome_back",
                "honest_reflection",
                "balanced_week"
              };
              boolean[] achieved = {
                qualifying,
                metWeeks.size() >= 2,
                pathComplete,
                qualifying && comeback,
                reflections >= 5,
                categories.containsAll(Arrays.asList("strength", "mobility", "recovery"))
              };
              for (int i = 0; i < achievementIds.length; i++)
                if (achieved[i] && !unlocked.contains(achievementIds[i])) {
                  unlocked.add(achievementIds[i]);
                  newUnlocks.add(achievementIds[i]);
                  tx.set(
                      u.collection(UNLOCKED).document(achievementIds[i]),
                      Values.of("unlockedAt", FieldValue.serverTimestamp(), "recordId", session));
                }
              if (!lesson.getStandalone()
                  && percentage >= 50
                  && Values.decimal(Values.map(progress.get("bestPercent")).get(lessonId), 0) < 50
                  && lesson.getSequence() < 7)
                newUnlocks.add(String.format(Locale.ROOT, "lesson_%02d", lesson.getSequence() + 1));
              tx.set(
                  p,
                  Values.of(
                      "completionCounts",
                      counts,
                      "bestPercent",
                      best,
                      "bestStars",
                      stars,
                      "updatedAt",
                      FieldValue.serverTimestamp()),
                  SetOptions.merge());
              Map<String, Object> userUpdate =
                  Values.of(
                      "lastRecordId",
                      session,
                      "totalXp",
                      total,
                      "level",
                      game.level(total),
                      "weeklyWorkoutTarget",
                      target,
                      "weeklyCounts",
                      weeks,
                      "weeklyCategories",
                      weekCategories,
                      "metGoalWeeks",
                      metWeeks,
                      "achievementIds",
                      unlocked,
                      "feedbackCount",
                      reflections,
                      "timeZone",
                      zone.getID(),
                      "lastDifficulty",
                      difficulty,
                      "lastFeeling",
                      feeling);
              if (qualifying) userUpdate.put("lastWorkoutAt", FieldValue.serverTimestamp());
              if ("discomfort".equals(feeling))
                userUpdate.put("lastDiscomfortAt", FieldValue.serverTimestamp());
              tx.set(u, userUpdate, SetOptions.merge());
              tx.set(
                  r,
                  Values.of(
                      "pathId",
                      path,
                      "lessonId",
                      lessonId,
                      "lessonName",
                      lesson.getName(),
                      "startedAt",
                      start.getTimestamp("startedAt"),
                      "completedAt",
                      FieldValue.serverTimestamp(),
                      "plannedExercises",
                      planned,
                      "completedExercises",
                      completed,
                      "skippedExercises",
                      skipped,
                      "completionPercentage",
                      percentage,
                      "durationSeconds",
                      duration,
                      "activeDurationSeconds",
                      activeDuration,
                      "estimatedCaloriesBurned",
                      Math.round(calories),
                      "xpEarned",
                      xp,
                      "starsEarned",
                      earnedStars,
                      "difficultyFeedback",
                      difficulty,
                      "feelingFeedback",
                      feeling,
                      "discomfortReported",
                      "discomfort".equals(feeling),
                      "firstCompletion",
                      attempts == 0,
                      "totalXpAfter",
                      total,
                      "weeklyCompleted",
                      weekly,
                      "weeklyTarget",
                      target,
                      "unlocked",
                      newUnlocks,
                      "category",
                      lesson.getCategory(),
                      "localDay",
                      day,
                      "localWeek",
                      week,
                      "timeZone",
                      zone.getID()));
              return session;
            });
  }
}
