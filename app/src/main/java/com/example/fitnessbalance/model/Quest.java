package com.example.fitnessbalance.model;

import com.google.firebase.firestore.Exclude;
import java.util.*;

public class Quest {
  public Quest() {}

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

  private int target = 0;

  public int getTarget() {
    return target;
  }

  public void setTarget(int value) {
    target = value;
  }

  private int bonusXp = 0;

  public int getBonusXp() {
    return bonusXp;
  }

  public void setBonusXp(int value) {
    bonusXp = value;
  }
}
