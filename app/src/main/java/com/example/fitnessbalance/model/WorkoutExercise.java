package com.example.fitnessbalance.model;

import com.google.firebase.firestore.Exclude;
import java.util.*;

public class WorkoutExercise {
  private String id = "";
  private String exerciseId = "";
  private int sequence;
  private String targetType = "";
  private int targetValue;
  private int restAfterSeconds;
  private boolean optional;

  public WorkoutExercise() {}

  @Exclude
  public String getId() {
    return id;
  }

  public void setId(String value) {
    id = value == null ? "" : value;
  }

  public String getExerciseId() {
    return exerciseId;
  }

  public void setExerciseId(String value) {
    exerciseId = value == null ? "" : value;
  }

  public int getSequence() {
    return sequence;
  }

  public void setSequence(int value) {
    sequence = value;
  }

  public String getTargetType() {
    return targetType;
  }

  public void setTargetType(String value) {
    targetType = value == null ? "" : value;
  }

  public int getTargetValue() {
    return targetValue;
  }

  public void setTargetValue(int value) {
    targetValue = value;
  }

  public int getRestAfterSeconds() {
    return restAfterSeconds;
  }

  public void setRestAfterSeconds(int value) {
    restAfterSeconds = value;
  }

  public boolean getOptional() {
    return optional;
  }

  public void setOptional(boolean value) {
    optional = value;
  }
}
