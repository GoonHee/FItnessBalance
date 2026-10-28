package com.example.fitnessbalance.model;

import com.google.firebase.firestore.Exclude;
import java.util.*;

public class WorkoutExercise {
  public WorkoutExercise() {}

  private String id = "";

  @Exclude
  public String getId() {
    return id;
  }

  public void setId(String value) {
    id = value == null ? "" : value;
  }

  private String exerciseId = "";

  public String getExerciseId() {
    return exerciseId;
  }

  public void setExerciseId(String value) {
    exerciseId = value == null ? "" : value;
  }

  private int sequence = 0;

  public int getSequence() {
    return sequence;
  }

  public void setSequence(int value) {
    sequence = value;
  }

  private String targetType = "";

  public String getTargetType() {
    return targetType;
  }

  public void setTargetType(String value) {
    targetType = value == null ? "" : value;
  }

  private int targetValue = 0;

  public int getTargetValue() {
    return targetValue;
  }

  public void setTargetValue(int value) {
    targetValue = value;
  }

  private int restAfterSeconds = 0;

  public int getRestAfterSeconds() {
    return restAfterSeconds;
  }

  public void setRestAfterSeconds(int value) {
    restAfterSeconds = value;
  }

  private boolean optional = false;

  public boolean getOptional() {
    return optional;
  }

  public void setOptional(boolean value) {
    optional = value;
  }
}
