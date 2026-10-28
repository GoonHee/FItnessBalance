package com.example.fitnessbalance.model;

import com.google.firebase.firestore.Exclude;
import java.util.*;

public class WorkoutLesson {
  private String id = "";
  private String name = "";
  private String description = "";
  private String stageName = "";
  private int sequence;
  private String difficulty = "";
  private int estimatedMinutes;
  private int baseXp;
  private String prerequisiteLessonId = "";
  private boolean active;
  private boolean standalone;
  private String category = "";
  private List<String> equipment = new ArrayList<>();

  public WorkoutLesson() {}

  @Exclude
  public String getId() {
    return id;
  }

  public void setId(String value) {
    id = value == null ? "" : value;
  }

  public String getName() {
    return name;
  }

  public void setName(String value) {
    name = value == null ? "" : value;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String value) {
    description = value == null ? "" : value;
  }

  public String getStageName() {
    return stageName;
  }

  public void setStageName(String value) {
    stageName = value == null ? "" : value;
  }

  public int getSequence() {
    return sequence;
  }

  public void setSequence(int value) {
    sequence = value;
  }

  public String getDifficulty() {
    return difficulty;
  }

  public void setDifficulty(String value) {
    difficulty = value == null ? "" : value;
  }

  public int getEstimatedMinutes() {
    return estimatedMinutes;
  }

  public void setEstimatedMinutes(int value) {
    estimatedMinutes = value;
  }

  public int getBaseXp() {
    return baseXp;
  }

  public void setBaseXp(int value) {
    baseXp = value;
  }

  public String getPrerequisiteLessonId() {
    return prerequisiteLessonId;
  }

  public void setPrerequisiteLessonId(String value) {
    prerequisiteLessonId = value == null ? "" : value;
  }

  public boolean getActive() {
    return active;
  }

  public void setActive(boolean value) {
    active = value;
  }

  public boolean getStandalone() {
    return standalone;
  }

  public void setStandalone(boolean value) {
    standalone = value;
  }

  public String getCategory() {
    return category;
  }

  public void setCategory(String value) {
    category = value == null ? "" : value;
  }

  public List<String> getEquipment() {
    return equipment;
  }

  public void setEquipment(List<String> value) {
    equipment = value == null ? new ArrayList<>() : value;
  }
}
