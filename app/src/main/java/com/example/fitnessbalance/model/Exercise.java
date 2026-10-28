package com.example.fitnessbalance.model;

import com.google.firebase.firestore.Exclude;
import java.util.*;

public class Exercise {
  public Exercise() {}

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

  private String category = "";

  public String getCategory() {
    return category;
  }

  public void setCategory(String value) {
    category = value == null ? "" : value;
  }

  private String difficulty = "";

  public String getDifficulty() {
    return difficulty;
  }

  public void setDifficulty(String value) {
    difficulty = value == null ? "" : value;
  }

  private String description = "";

  public String getDescription() {
    return description;
  }

  public void setDescription(String value) {
    description = value == null ? "" : value;
  }

  private List<String> instructions = new ArrayList<>();

  public List<String> getInstructions() {
    return instructions;
  }

  public void setInstructions(List<String> value) {
    instructions = value == null ? new ArrayList<>() : value;
  }

  private String targetType = "";

  public String getTargetType() {
    return targetType;
  }

  public void setTargetType(String value) {
    targetType = value == null ? "" : value;
  }

  private int defaultTarget = 0;

  public int getDefaultTarget() {
    return defaultTarget;
  }

  public void setDefaultTarget(int value) {
    defaultTarget = value;
  }

  private String targetUnit = "";

  public String getTargetUnit() {
    return targetUnit;
  }

  public void setTargetUnit(String value) {
    targetUnit = value == null ? "" : value;
  }

  private List<String> equipment = new ArrayList<>();

  public List<String> getEquipment() {
    return equipment;
  }

  public void setEquipment(List<String> value) {
    equipment = value == null ? new ArrayList<>() : value;
  }

  private double metValue = 0;

  public double getMetValue() {
    return metValue;
  }

  public void setMetValue(double value) {
    metValue = value;
  }

  private String imageUrl = "";

  public String getImageUrl() {
    return imageUrl;
  }

  public void setImageUrl(String value) {
    imageUrl = value == null ? "" : value;
  }

  private boolean active = false;

  public boolean getActive() {
    return active;
  }

  public void setActive(boolean value) {
    active = value;
  }
}
