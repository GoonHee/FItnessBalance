package com.example.fitnessbalance.model;

import com.google.firebase.firestore.Exclude;
import java.util.*;

public class WorkoutPath {
  public WorkoutPath() {}

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

  private boolean active = false;

  public boolean getActive() {
    return active;
  }

  public void setActive(boolean value) {
    active = value;
  }
}
