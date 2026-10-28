package com.example.fitnessbalance.model;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.Exclude;
import java.util.*;

public class WorkoutRecord {
  public WorkoutRecord() {}

  private String id = "";

  @Exclude
  public String getId() {
    return id;
  }

  public void setId(String value) {
    id = value == null ? "" : value;
  }

  private String pathId = "";

  public String getPathId() {
    return pathId;
  }

  public void setPathId(String value) {
    pathId = value == null ? "" : value;
  }

  private String lessonId = "";

  public String getLessonId() {
    return lessonId;
  }

  public void setLessonId(String value) {
    lessonId = value == null ? "" : value;
  }

  private String lessonName = "";

  public String getLessonName() {
    return lessonName;
  }

  public void setLessonName(String value) {
    lessonName = value == null ? "" : value;
  }

  private Timestamp startedAt = null;

  public Timestamp getStartedAt() {
    return startedAt;
  }

  public void setStartedAt(Timestamp value) {
    startedAt = value;
  }

  private Timestamp completedAt = null;

  public Timestamp getCompletedAt() {
    return completedAt;
  }

  public void setCompletedAt(Timestamp value) {
    completedAt = value;
  }

  private int plannedExercises = 0;

  public int getPlannedExercises() {
    return plannedExercises;
  }

  public void setPlannedExercises(int value) {
    plannedExercises = value;
  }

  private int completedExercises = 0;

  public int getCompletedExercises() {
    return completedExercises;
  }

  public void setCompletedExercises(int value) {
    completedExercises = value;
  }

  private int skippedExercises = 0;

  public int getSkippedExercises() {
    return skippedExercises;
  }

  public void setSkippedExercises(int value) {
    skippedExercises = value;
  }

  private double completionPercentage = 0;

  public double getCompletionPercentage() {
    return completionPercentage;
  }

  public void setCompletionPercentage(double value) {
    completionPercentage = value;
  }

  private int durationSeconds = 0;

  public int getDurationSeconds() {
    return durationSeconds;
  }

  public void setDurationSeconds(int value) {
    durationSeconds = value;
  }

  private int activeDurationSeconds = 0;

  public int getActiveDurationSeconds() {
    return activeDurationSeconds;
  }

  public void setActiveDurationSeconds(int value) {
    activeDurationSeconds = value;
  }

  private double estimatedCaloriesBurned = 0;

  public double getEstimatedCaloriesBurned() {
    return estimatedCaloriesBurned;
  }

  public void setEstimatedCaloriesBurned(double value) {
    estimatedCaloriesBurned = value;
  }

  private int xpEarned = 0;

  public int getXpEarned() {
    return xpEarned;
  }

  public void setXpEarned(int value) {
    xpEarned = value;
  }

  private int starsEarned = 0;

  public int getStarsEarned() {
    return starsEarned;
  }

  public void setStarsEarned(int value) {
    starsEarned = value;
  }

  private String difficultyFeedback = "";

  public String getDifficultyFeedback() {
    return difficultyFeedback;
  }

  public void setDifficultyFeedback(String value) {
    difficultyFeedback = value == null ? "" : value;
  }

  private String feelingFeedback = "";

  public String getFeelingFeedback() {
    return feelingFeedback;
  }

  public void setFeelingFeedback(String value) {
    feelingFeedback = value == null ? "" : value;
  }

  private boolean discomfortReported = false;

  public boolean getDiscomfortReported() {
    return discomfortReported;
  }

  public void setDiscomfortReported(boolean value) {
    discomfortReported = value;
  }

  private boolean firstCompletion = false;

  public boolean getFirstCompletion() {
    return firstCompletion;
  }

  public void setFirstCompletion(boolean value) {
    firstCompletion = value;
  }

  private int totalXpAfter = 0;

  public int getTotalXpAfter() {
    return totalXpAfter;
  }

  public void setTotalXpAfter(int value) {
    totalXpAfter = value;
  }

  private int weeklyCompleted = 0;

  public int getWeeklyCompleted() {
    return weeklyCompleted;
  }

  public void setWeeklyCompleted(int value) {
    weeklyCompleted = value;
  }

  private int weeklyTarget = 0;

  public int getWeeklyTarget() {
    return weeklyTarget;
  }

  public void setWeeklyTarget(int value) {
    weeklyTarget = value;
  }

  private List<String> unlocked = new ArrayList<>();

  public List<String> getUnlocked() {
    return unlocked;
  }

  public void setUnlocked(List<String> value) {
    unlocked = value == null ? new ArrayList<>() : value;
  }
}
