package com.example.fitnessbalance.model;

import java.util.*;

/** In-memory catalog snapshot; never passed as a serialized Intent extra. */
public final class LessonPlan {
  public WorkoutLesson lesson;
  public final List<WorkoutExercise> items = new ArrayList<>();
  public final List<Exercise> exercises = new ArrayList<>();
}
