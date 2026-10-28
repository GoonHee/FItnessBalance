package com.example.fitnessbalance.model;

import com.google.firebase.firestore.Exclude;
import java.util.*;

public class Quest {
  private String id = "";
  private String name = "";
  private int target;
  private int bonusXp;
  private boolean active;

  public Quest() {}

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

  public int getTarget() {
    return target;
  }

  public void setTarget(int value) {
    target = value;
  }

  public int getBonusXp() {
    return bonusXp;
  }

  public void setBonusXp(int value) {
    bonusXp = value;
  }

  public boolean getActive() {
    return active;
  }

  public void setActive(boolean value) {
    active = value;
  }
}
