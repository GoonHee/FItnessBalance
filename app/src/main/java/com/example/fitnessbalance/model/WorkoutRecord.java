package com.example.fitnessbalance.model;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.Exclude;
import java.util.*;

public class WorkoutRecord {
  private int activeDurationSeconds;

  public int getActiveDurationSeconds() {
    return activeDurationSeconds;
  }

  public void setActiveDurationSeconds(int value) {
    activeDurationSeconds = value;
  }

  private String id = "";
  private String pathId = "";
  private String lessonId = "";
  private String lessonName = "";
  private Timestamp startedAt;
  private Timestamp completedAt;
  private int plannedExercises;
  private int completedExercises;
  private int skippedExercises;
  private double completionPercentage;
  private int durationSeconds;
  private double estimatedCaloriesBurned;
  private int xpEarned;
  private int starsEarned;
  private String difficultyFeedback = "";
  private String feelingFeedback = "";
  private boolean discomfortReported;
  private boolean firstCompletion;
  private int totalXpAfter;
  private int weeklyCompleted;
  private int weeklyTarget;
  private List<String> unlocked = new ArrayList<>();

  public WorkoutRecord() {}

  @Exclude
  public String getId() {
    return id;
  }

  public void setId(String value) {
    id = value == null ? "" : value;
  }

  public String getPathId() {
    return pathId;
  }

  public void setPathId(String value) {
    pathId = value == null ? "" : value;
  }

  public String getLessonId() {
    return lessonId;
  }

  public void setLessonId(String value) {
    lessonId = value == null ? "" : value;
  }

  public String getLessonName() {
    return lessonName;
  }

  public void setLessonName(String value) {
    lessonName = value == null ? "" : value;
  }

  public Timestamp getStartedAt() {
    return startedAt;
  }

  public void setStartedAt(Timestamp value) {
    startedAt = value;
  }

  public Timestamp getCompletedAt() {
    return completedAt;
  }

  public void setCompletedAt(Timestamp value) {
    completedAt = value;
  }

  public int getPlannedExercises() {
    return plannedExercises;
  }

  public void setPlannedExercises(int value) {
    plannedExercises = value;
  }

  public int getCompletedExercises() {
    return completedExercises;
  }

  public void setCompletedExercises(int value) {
    completedExercises = value;
  }

  public int getSkippedExercises() {
    return skippedExercises;
  }

  public void setSkippedExercises(int value) {
    skippedExercises = value;
  }

  public double getCompletionPercentage() {
    return completionPercentage;
  }

  public void setCompletionPercentage(double value) {
    completionPercentage = value;
  }

  public int getDurationSeconds() {
    return durationSeconds;
  }

  public void setDurationSeconds(int value) {
    durationSeconds = value;
  }

  public double getEstimatedCaloriesBurned() {
    return estimatedCaloriesBurned;
  }

  public void setEstimatedCaloriesBurned(double value) {
    estimatedCaloriesBurned = value;
  }

  public int getXpEarned() {
    return xpEarned;
  }

  public void setXpEarned(int value) {
    xpEarned = value;
  }

  public int getStarsEarned() {
    return starsEarned;
  }

  public void setStarsEarned(int value) {
    starsEarned = value;
  }

  public String getDifficultyFeedback() {
    return difficultyFeedback;
  }

  public void setDifficultyFeedback(String value) {
    difficultyFeedback = value == null ? "" : value;
  }

  public String getFeelingFeedback() {
    return feelingFeedback;
  }

  public void setFeelingFeedback(String value) {
    feelingFeedback = value == null ? "" : value;
  }

  public boolean getDiscomfortReported() {
    return discomfortReported;
  }

  public void setDiscomfortReported(boolean value) {
    discomfortReported = value;
  }

  public boolean getFirstCompletion() {
    return firstCompletion;
  }

  public void setFirstCompletion(boolean value) {
    firstCompletion = value;
  }

  public int getTotalXpAfter() {
    return totalXpAfter;
  }

  public void setTotalXpAfter(int value) {
    totalXpAfter = value;
  }

  public int getWeeklyCompleted() {
    return weeklyCompleted;
  }

  public void setWeeklyCompleted(int value) {
    weeklyCompleted = value;
  }

  public int getWeeklyTarget() {
    return weeklyTarget;
  }

  public void setWeeklyTarget(int value) {
    weeklyTarget = value;
  }

  public List<String> getUnlocked() {
    return unlocked;
  }

  public void setUnlocked(List<String> value) {
    unlocked = value == null ? new ArrayList<>() : value;
  }
}
