package com.example.fitnessbalance.model;

import com.google.firebase.firestore.Exclude;
import java.util.*;

public class Exercise {
  private String id = "";
  private String name = "";
  private String category = "";
  private String difficulty = "";
  private String description = "";
  private List<String> instructions = new ArrayList<>();
  private String targetType = "";
  private int defaultTarget;
  private String targetUnit = "";
  private List<String> equipment = new ArrayList<>();
  private double metValue;
  private String imageUrl = "";
  private boolean active;

  public Exercise() {}

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

  public String getCategory() {
    return category;
  }

  public void setCategory(String value) {
    category = value == null ? "" : value;
  }

  public String getDifficulty() {
    return difficulty;
  }

  public void setDifficulty(String value) {
    difficulty = value == null ? "" : value;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String value) {
    description = value == null ? "" : value;
  }

  public List<String> getInstructions() {
    return instructions;
  }

  public void setInstructions(List<String> value) {
    instructions = value == null ? new ArrayList<>() : value;
  }

  public String getTargetType() {
    return targetType;
  }

  public void setTargetType(String value) {
    targetType = value == null ? "" : value;
  }

  public int getDefaultTarget() {
    return defaultTarget;
  }

  public void setDefaultTarget(int value) {
    defaultTarget = value;
  }

  public String getTargetUnit() {
    return targetUnit;
  }

  public void setTargetUnit(String value) {
    targetUnit = value == null ? "" : value;
  }

  public List<String> getEquipment() {
    return equipment;
  }

  public void setEquipment(List<String> value) {
    equipment = value == null ? new ArrayList<>() : value;
  }

  public double getMetValue() {
    return metValue;
  }

  public void setMetValue(double value) {
    metValue = value;
  }

  public String getImageUrl() {
    return imageUrl;
  }

  public void setImageUrl(String value) {
    imageUrl = value == null ? "" : value;
  }

  public boolean getActive() {
    return active;
  }

  public void setActive(boolean value) {
    active = value;
  }
}
