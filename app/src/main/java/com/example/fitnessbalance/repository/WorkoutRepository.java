package com.example.fitnessbalance.repository;

import static com.example.fitnessbalance.util.FirestoreCollections.*;

import com.example.fitnessbalance.model.*;
import com.example.fitnessbalance.service.GamificationService;
import com.example.fitnessbalance.util.*;
import com.google.android.gms.tasks.*;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.*;
import java.util.*;

public class WorkoutRepository {
  private final FirebaseFirestore db = FirebaseFirestore.getInstance();

  public DocumentReference user() {
    if (FirebaseAuth.getInstance().getCurrentUser() == null)
      throw new IllegalStateException("Signed out");
    return db.collection(USERS).document(FirebaseAuth.getInstance().getCurrentUser().getUid());
  }

  public DocumentReference lessonRef(String path, String lesson) {
    return db.collection(PATHS).document(path).collection(LESSONS).document(lesson);
  }

  public Task<List<WorkoutLesson>> lessons(String path) {
    return db.collection(PATHS)
        .document(path)
        .collection(LESSONS)
        .orderBy("sequence")
        .get(Source.SERVER)
        .continueWith(
            t -> {
              if (!t.isSuccessful()) throw t.getException();
              List<WorkoutLesson> out = new ArrayList<>();
              for (DocumentSnapshot d : t.getResult()) {
                WorkoutLesson l = d.toObject(WorkoutLesson.class);
                if (l != null && l.getActive()) {
                  l.setId(d.getId());
                  out.add(l);
                }
              }
              return out;
            });
  }

  public Task<WorkoutLesson> lesson(String path, String id) {
    return lessonRef(path, id)
        .get(Source.SERVER)
        .continueWith(
            t -> {
              if (!t.isSuccessful()) throw t.getException();
              WorkoutLesson l = t.getResult().toObject(WorkoutLesson.class);
              if (l == null || !l.getActive())
                throw new IllegalStateException("Lesson unavailable");
              l.setId(id);
              return l;
            });
  }

  public Task<List<WorkoutExercise>> items(String path, String lesson) {
    return lessonRef(path, lesson)
        .collection(ITEMS)
        .orderBy("sequence")
        .get(Source.SERVER)
        .continueWith(
            t -> {
              if (!t.isSuccessful()) throw t.getException();
              List<WorkoutExercise> out = new ArrayList<>();
              for (DocumentSnapshot d : t.getResult()) {
                WorkoutExercise i = d.toObject(WorkoutExercise.class);
                if (i != null) {
                  i.setId(d.getId());
                  out.add(i);
                }
              }
              if (out.isEmpty()) throw new IllegalStateException("Empty workout");
              return out;
            });
  }

  public Task<DocumentSnapshot> profile() {
    return user().get(Source.SERVER);
  }

  public Task<DocumentSnapshot> progress(String path) {
    return user().collection(PROGRESS).document(path).get(Source.SERVER);
  }

  public Task<QuerySnapshot> records() {
    return user()
        .collection(RECORDS)
        .orderBy("completedAt", Query.Direction.DESCENDING)
        .limit(30)
        .get(Source.SERVER);
  }

  public Task<DocumentSnapshot> record(String session) {
    return user().collection(RECORDS).document(session).get(Source.SERVER);
  }

  public Task<QuerySnapshot> achievements() {
    return db.collection(ACHIEVEMENTS).get(Source.SERVER);
  }

  public Task<QuerySnapshot> unlockedAchievements() {
    return user().collection(UNLOCKED).get(Source.SERVER);
  }

  public Task<QuerySnapshot> quests() {
    return user()
        .collection(QUEST_PROGRESS)
        .whereEqualTo("day", DateUtils.day(new Date(), TimeZone.getDefault()))
        .get(Source.SERVER);
  }

  public boolean unlocked(WorkoutLesson lesson, DocumentSnapshot progress) {
    if (lesson.getStandalone()) return true;
    Map<String, Object> best = Values.map(progress.get("bestPercent"));
    return new GamificationService()
        .unlocked(
            lesson.getPrerequisiteLessonId(),
            Values.decimal(best.get(lesson.getPrerequisiteLessonId()), 0));
  }

  /** Server-acknowledged session start. Stable ID also becomes the record idempotency key. */
  public Task<Void> begin(String path, String lesson, String session, int planned) {
    DocumentReference u = user(), s = u.collection(SESSIONS).document(session);
    return db.runTransaction(
        tx -> {
          DocumentSnapshot old = tx.get(s);
          DocumentSnapshot l = tx.get(lessonRef(path, lesson));
          DocumentSnapshot p = tx.get(u.collection(PROGRESS).document(path));
          WorkoutLesson model = l.toObject(WorkoutLesson.class);
          if (model == null || !model.getActive() || !unlocked(model, p))
            throw new IllegalStateException("Lesson locked or unavailable");
          if (!old.exists())
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
