package com.example.fitnessbalance.model;

import com.google.firebase.firestore.Exclude;
import java.util.*;

public class WorkoutLesson {
  public WorkoutLesson() {}

  private String id = "";

  @Exclude
  public String getId() {
    return id;
  }

  public void setId(String value) {
    id = value == null ? "" : value;
  }

  private String name = "";

  public String getName() {
    return name;
  }

  public void setName(String value) {
    name = value == null ? "" : value;
  }

  private String description = "";

  public String getDescription() {
    return description;
  }

  public void setDescription(String value) {
    description = value == null ? "" : value;
  }

  private String stageName = "";

  public String getStageName() {
    return stageName;
  }

  public void setStageName(String value) {
    stageName = value == null ? "" : value;
  }

  private int sequence = 0;

  public int getSequence() {
    return sequence;
  }

  public void setSequence(int value) {
    sequence = value;
  }

  private String difficulty = "";

  public String getDifficulty() {
    return difficulty;
  }

  public void setDifficulty(String value) {
    difficulty = value == null ? "" : value;
  }

  private int estimatedMinutes = 0;

  public int getEstimatedMinutes() {
    return estimatedMinutes;
  }

  public void setEstimatedMinutes(int value) {
    estimatedMinutes = value;
  }

  private int baseXp = 0;

  public int getBaseXp() {
    return baseXp;
  }

  public void setBaseXp(int value) {
    baseXp = value;
  }

  private String prerequisiteLessonId = "";

  public String getPrerequisiteLessonId() {
    return prerequisiteLessonId;
  }

  public void setPrerequisiteLessonId(String value) {
    prerequisiteLessonId = value == null ? "" : value;
  }

  private boolean active = false;

  public boolean getActive() {
    return active;
  }

  public void setActive(boolean value) {
    active = value;
  }

  private boolean standalone = false;

  public boolean getStandalone() {
    return standalone;
  }

  public void setStandalone(boolean value) {
    standalone = value;
  }

  private String category = "";

  public String getCategory() {
    return category;
  }

  public void setCategory(String value) {
    category = value == null ? "" : value;
  }

  private List<String> equipment = new ArrayList<>();

  public List<String> getEquipment() {
    return equipment;
  }

  public void setEquipment(List<String> value) {
    equipment = value == null ? new ArrayList<>() : value;
  }
}
