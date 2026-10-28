package com.example.fitnessbalance.repository;

import static com.example.fitnessbalance.util.FirestoreCollections.*;

import com.example.fitnessbalance.model.*;
import com.example.fitnessbalance.service.GamificationService;
import com.example.fitnessbalance.util.*;
import com.google.android.gms.tasks.*;
import com.google.firebase.auth.*;
import com.google.firebase.firestore.*;
import java.util.*;

public final class WorkoutRepository {
  private final FirebaseFirestore db = FirebaseFirestore.getInstance();

  public DocumentReference user() {
    FirebaseUser u = FirebaseAuth.getInstance().getCurrentUser();
    if (u == null) throw new IllegalStateException("Authentication required");
    return db.collection(USERS).document(u.getUid());
  }

  public DocumentReference lessonRef(String path, String lesson) {
    return db.collection(PATHS).document(path).collection(LESSONS).document(lesson);
  }

  public Task<DocumentSnapshot> profile() {
    return user().get();
  }

  public Task<DocumentSnapshot> progress(String path) {
    return user().collection(PROGRESS).document(path).get();
  }

  public Task<DocumentSnapshot> week(String week) {
    return user().collection(WEEKS).document(week).get();
  }

  public Task<QuerySnapshot> recent() {
    return user()
        .collection(RECORDS)
        .orderBy("completedAt", Query.Direction.DESCENDING)
        .limit(30)
        .get();
  }

  public Task<DocumentSnapshot> record(String id) {
    return user().collection(RECORDS).document(id).get();
  }

  public Task<QuerySnapshot> dailyQuests(String day) {
    return user().collection(QUEST_PROGRESS).whereEqualTo("day", day).get();
  }

  public Task<QuerySnapshot> achievements() {
    return db.collection(ACHIEVEMENTS).get();
  }

  public Task<QuerySnapshot> unlockedAchievements() {
    return user().collection(UNLOCKED).get();
  }

  public Task<List<WorkoutLesson>> lessons(String path) {
    return db.collection(PATHS)
        .document(path)
        .collection(LESSONS)
        .orderBy("sequence")
        .get()
        .continueWith(
            t -> {
              if (!t.isSuccessful()) throw t.getException();
              List<WorkoutLesson> lessons = new ArrayList<>();
              for (DocumentSnapshot d : t.getResult()) {
                WorkoutLesson l = d.toObject(WorkoutLesson.class);
                if (l != null && l.getActive()) {
                  l.setId(d.getId());
                  lessons.add(l);
                }
              }
              return lessons;
            });
  }

  public boolean unlocked(WorkoutLesson l, DocumentSnapshot progress) {
    return l.getStandalone()
        || new GamificationService()
            .unlocked(
                l.getPrerequisiteLessonId(),
                Values.decimal(
                    Values.map(progress.get("bestPercent")).get(l.getPrerequisiteLessonId()), 0));
  }

  public Task<LessonPlan> plan(String path, String lesson) {
    Task<DocumentSnapshot> l = lessonRef(path, lesson).get();
    Task<QuerySnapshot> i = lessonRef(path, lesson).collection(ITEMS).orderBy("sequence").get();
    return Tasks.whenAll(l, i)
        .continueWithTask(
            t -> {
              if (!t.isSuccessful()) return Tasks.forException(t.getException());
              LessonPlan plan = new LessonPlan();
              plan.lesson = l.getResult().toObject(WorkoutLesson.class);
              if (plan.lesson == null || !plan.lesson.getActive())
                return Tasks.forException(new IllegalStateException("Lesson content missing"));
              plan.lesson.setId(lesson);
              List<Task<Exercise>> references = new ArrayList<>();
              for (DocumentSnapshot d : i.getResult()) {
                WorkoutExercise item = d.toObject(WorkoutExercise.class);
                if (item == null
                    || item.getExerciseId().isEmpty()
                    || item.getTargetValue() < 1
                    || item.getTargetValue() > 3600
                    || item.getRestAfterSeconds() < 0
                    || item.getRestAfterSeconds() > 600
                    || (!"duration".equals(item.getTargetType())
                        && !"repetitions".equals(item.getTargetType())))
                  return Tasks.forException(new IllegalStateException("Invalid exercise content"));
                item.setId(d.getId());
                plan.items.add(item);
                references.add(new ExerciseRepository().get(item.getExerciseId()));
              }
              if (plan.items.isEmpty() || plan.items.size() > 64)
                return Tasks.forException(new IllegalStateException("Empty workout"));
              return Tasks.whenAll(references)
                  .continueWith(
                      done -> {
                        if (!done.isSuccessful()) throw done.getException();
                        for (Task<Exercise> ref : references) plan.exercises.add(ref.getResult());
                        return plan;
                      });
            });
  }

  public Task<Void> begin(String path, String lesson, String session, int planned) {
    DocumentReference u = user(), s = u.collection(SESSIONS).document(session);
    return db.runTransaction(
        tx -> {
          DocumentSnapshot old = tx.get(s);
          if (old.exists()) {
            if (!path.equals(old.getString("pathId"))
                || !lesson.equals(old.getString("lessonId"))
                || Values.integer(old.get("plannedExercises"), 0) != planned)
              throw new IllegalStateException("Session ID mismatch");
            return null;
          }
          DocumentSnapshot l = tx.get(lessonRef(path, lesson)),
              p = tx.get(u.collection(PROGRESS).document(path));
          WorkoutLesson model = l.toObject(WorkoutLesson.class);
          if (model == null || !model.getActive() || !unlocked(model, p))
            throw new IllegalStateException("Lesson locked or unavailable");
          tx.set(
              s,
              Values.of(
                  "pathId",
                  path,
                  "lessonId",
                  lesson,
                  "plannedExercises",
                  planned,
                  "startedAt",
                  FieldValue.serverTimestamp()));
          return null;
        });
  }
}
