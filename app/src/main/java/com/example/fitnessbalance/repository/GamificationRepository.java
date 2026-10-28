package com.example.fitnessbalance.repository;

import static com.example.fitnessbalance.util.FirestoreCollections.*;

import com.example.fitnessbalance.model.*;
import com.example.fitnessbalance.service.*;
import com.example.fitnessbalance.util.*;
import com.google.android.gms.tasks.*;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.*;
import java.util.*;

/** One transaction, stable session/record ID, and no reads after the first write. */
public final class GamificationRepository {
  private final WorkoutRepository repository = new WorkoutRepository();
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
      return Tasks.forException(new IllegalArgumentException("Feedback required"));
    DocumentReference user = repository.user(), record = user.collection(RECORDS).document(session);
    Date now = new Date();
    TimeZone zone = TimeZone.getDefault();
    String day = DateUtils.day(now, zone), week = DateUtils.week(now, zone);
    return FirebaseFirestore.getInstance()
        .runTransaction(
            tx -> {
              // A retried or resumed submission returns the immutable record without any writes.
              DocumentSnapshot saved = tx.get(record);
              if (saved.exists()) return session;
              DocumentSnapshot start = tx.get(user.collection(SESSIONS).document(session)),
                  profile = tx.get(user);
              if (!start.exists() || start.getTimestamp("startedAt") == null)
                throw new IllegalStateException("Session start missing");
              String path = start.getString("pathId"), lessonId = start.getString("lessonId");
              DocumentReference progressRef = user.collection(PROGRESS).document(path),
                  weekRef = user.collection(WEEKS).document(week);
              DocumentSnapshot progress = tx.get(progressRef),
                  lessonDoc = tx.get(repository.lessonRef(path, lessonId)),
                  weekly = tx.get(weekRef);
              WorkoutLesson lesson = lessonDoc.toObject(WorkoutLesson.class);
              if (lesson == null || !lesson.getActive() || !repository.unlocked(lesson, progress))
                throw new IllegalStateException("Lesson locked or unavailable");
              String[] questIds = {"planned", "minutes", "feedback"},
                  achievementIds =
                      {
                        "first_step",
                        "routine_builder",
                        "path_explorer",
                        "welcome_back",
                        "honest_reflection",
                        "balanced_week"
                      };
              List<DocumentSnapshot> quests = new ArrayList<>(), achievements = new ArrayList<>();
              for (String id : questIds)
                quests.add(tx.get(user.collection(QUEST_PROGRESS).document(day + "_" + id)));
              for (String id : achievementIds)
                achievements.add(tx.get(user.collection(UNLOCKED).document(id)));
              int planned = Values.integer(start.get("plannedExercises"), 0);
              if (planned < 1
                  || planned > 64
                  || completed < 0
                  || skipped < 0
                  || completed + skipped > planned
                  || duration < 0
                  || duration > 86400
                  || activeDuration < 0
                  || activeDuration > duration
                  || Double.isNaN(calories)
                  || Double.isInfinite(calories)
                  || calories < 0
                  || calories > 100000)
                throw new IllegalArgumentException("Invalid session totals");
              double percentage = game.completion(completed, planned);
              boolean qualifying = percentage >= 50;
              int stars = game.stars(percentage, difficulty);
              Map<String, Object> attempts = Values.map(progress.get("completionCounts")),
                  best = Values.map(progress.get("bestPercent")),
                  bestStars = Values.map(progress.get("bestStars"));
              int previousAttempts = Values.integer(attempts.get(lessonId), 0);
              double previousBest = Values.decimal(best.get(lessonId), 0);
              attempts.put(lessonId, previousAttempts + 1);
              best.put(lessonId, Math.max(previousBest, percentage));
              bestStars.put(lessonId, Math.max(Values.integer(bestStars.get(lessonId), 0), stars));
              int target =
                  new MomentumService()
                      .target(Values.integer(profile.get("weeklyWorkoutTarget"), 3));
              int oldWeekCount =
                  Values.integer(
                      weekly.get("completed"),
                      Values.integer(Values.map(profile.get("weeklyCounts")).get(week), 0));
              int count = oldWeekCount + (qualifying ? 1 : 0);
              List<String> goalWeeks = Values.strings(profile.get("metGoalWeeks"));
              boolean priorWeeklyBonus =
                  Boolean.TRUE.equals(weekly.get("bonusAwarded")) || goalWeeks.contains(week);
              int weeklyBonus = game.weeklyBonus(count, target, priorWeeklyBonus);
              if (weeklyBonus > 0) goalWeeks.add(week);
              List<String> categories = Values.strings(weekly.get("categories"));
              if (qualifying && !categories.contains(lesson.getCategory()))
                categories.add(lesson.getCategory());
              boolean[] eligible = {qualifying, activeDuration >= 300, true};
              int[] questXp = {5, 5, 0};
              int questBonus = 0;
              for (int n = 0; n < 3; n++)
                questBonus += game.dailyBonus(eligible[n], quests.get(n).exists(), questXp[n]);
              int earned =
                  game.workoutXp(lesson.getBaseXp(), percentage, previousAttempts)
                      + game.feedbackXp(false)
                      + questBonus
                      + weeklyBonus;
              int oldXp = Values.integer(profile.get("totalXp"), 0);
              if (oldXp < 0 || oldXp > 1000000000 - earned)
                throw new IllegalStateException("Invalid XP total");
              int totalXp = oldXp + earned,
                  feedbackCount = Values.integer(profile.get("feedbackCount"), 0) + 1;
              boolean pathComplete = true;
              for (int n = 1; n <= 7; n++)
                if (Values.decimal(best.get(String.format(Locale.ROOT, "lesson_%02d", n)), 0) < 50)
                  pathComplete = false;
              Timestamp lastWorkout = profile.getTimestamp("lastWorkoutAt");
              boolean comeback =
                  lastWorkout != null
                      && DateUtils.daysBetween(lastWorkout.toDate(), now, zone) >= 7;
              boolean[] eligibleAchievements = {
                qualifying,
                goalWeeks.size() >= 2,
                pathComplete,
                qualifying && comeback,
                feedbackCount >= 5,
                categories.containsAll(Arrays.asList("strength", "mobility", "recovery"))
              };
              List<String> achievementList = Values.strings(profile.get("achievementIds")),
                  newUnlocks = new ArrayList<>();
              // All transaction reads are complete. Every mutation below commits together.
              for (int n = 0; n < 3; n++)
                if (eligible[n] && !quests.get(n).exists())
                  tx.set(
                      quests.get(n).getReference(),
                      Values.of(
                          "day",
                          day,
                          "questId",
                          questIds[n],
                          "completed",
                          true,
                          "xpEarned",
                          questXp[n],
                          "recordId",
                          session,
                          "completedAt",
                          FieldValue.serverTimestamp()));
              for (int n = 0; n < 6; n++)
                if (eligibleAchievements[n] && !achievements.get(n).exists()) {
                  if (!achievementList.contains(achievementIds[n]))
                    achievementList.add(achievementIds[n]);
                  newUnlocks.add(achievementIds[n]);
                  tx.set(
                      achievements.get(n).getReference(),
                      Values.of("recordId", session, "unlockedAt", FieldValue.serverTimestamp()));
                }
              if (!lesson.getStandalone()
                  && percentage >= 50
                  && previousBest < 50
                  && lesson.getSequence() < 7)
                newUnlocks.add(String.format(Locale.ROOT, "lesson_%02d", lesson.getSequence() + 1));
              tx.set(
                  progressRef,
                  Values.of(
                      "completionCounts",
                      attempts,
                      "bestPercent",
                      best,
                      "bestStars",
                      bestStars,
                      "updatedAt",
                      FieldValue.serverTimestamp()),
                  SetOptions.merge());
              tx.set(
                  weekRef,
                  Values.of(
                      "completed",
                      count,
                      "categories",
                      categories,
                      "bonusAwarded",
                      priorWeeklyBonus || weeklyBonus > 0,
                      "target",
                      target,
                      "lastRecordId",
                      session,
                      "updatedAt",
                      FieldValue.serverTimestamp()));
              Map<String, Object> userValues =
                  Values.of(
                      "lastRecordId",
                      session,
                      "totalXp",
                      totalXp,
                      "level",
                      game.level(totalXp),
                      "weeklyWorkoutTarget",
                      target,
                      "metGoalWeeks",
                      goalWeeks,
                      "achievementIds",
                      achievementList,
                      "feedbackCount",
                      feedbackCount,
                      "lastDifficulty",
                      difficulty,
                      "lastFeeling",
                      feeling,
                      "timeZone",
                      zone.getID());
              if (qualifying) userValues.put("lastWorkoutAt", FieldValue.serverTimestamp());
              if ("discomfort".equals(feeling))
                userValues.put("lastDiscomfortAt", FieldValue.serverTimestamp());
              tx.set(user, userValues, SetOptions.merge());
              tx.set(
                  record,
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
                      earned,
                      "starsEarned",
                      stars,
                      "difficultyFeedback",
                      difficulty,
                      "feelingFeedback",
                      feeling,
                      "discomfortReported",
                      "discomfort".equals(feeling),
                      "firstCompletion",
                      previousAttempts == 0,
                      "totalXpAfter",
                      totalXp,
                      "weeklyCompleted",
                      count,
                      "weeklyTarget",
                      target,
                      "unlocked",
                      newUnlocks,
                      "localDay",
                      day,
                      "localWeek",
                      week,
                      "timeZone",
                      zone.getID(),
                      "category",
                      lesson.getCategory()));
              return session;
            })
        .continueWithTask(
            outcome -> {
              if (outcome.isSuccessful()) return Tasks.forResult(session);
              // A simultaneous writer can trigger immutable-record rules before SDK retry handling.
              // Verify the authoritative record before treating an uncertain commit as failed.
              return record
                  .get(Source.SERVER)
                  .continueWith(
                      confirmed -> {
                        if (confirmed.isSuccessful() && confirmed.getResult().exists())
                          return session;
                        throw outcome.getException() == null
                            ? new IllegalStateException("Workout save failed")
                            : outcome.getException();
                      });
            });
  }
}
