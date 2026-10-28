package com.example.fitnessbalance.adapter;

import android.content.Context;
import androidx.annotation.NonNull;
import com.example.fitnessbalance.R;
import com.example.fitnessbalance.model.WorkoutLesson;
import com.example.fitnessbalance.service.GamificationService;
import com.example.fitnessbalance.util.Values;
import java.util.*;

public class WorkoutLessonAdapter extends TextRowsAdapter {
  public interface OnLesson {
    void open(WorkoutLesson lesson);
  }

  private final List<WorkoutLesson> lessons;
  private final Map<String, Object> best, stars;
  private final OnLesson listener;

  public WorkoutLessonAdapter(
      List<WorkoutLesson> lessons,
      Map<String, Object> best,
      Map<String, Object> stars,
      OnLesson listener) {
    super(Collections.emptyList());
    this.lessons = lessons;
    this.best = best;
    this.stars = stars;
    this.listener = listener;
  }

  @Override
  public int getItemCount() {
    return lessons.size();
  }

  @Override
  public void onBindViewHolder(@NonNull Holder h, int p) {
    WorkoutLesson l = lessons.get(p);
    double percent = Values.decimal(best.get(l.getId()), 0);
    boolean unlocked =
        new GamificationService()
            .unlocked(
                l.getPrerequisiteLessonId(),
                Values.decimal(best.get(l.getPrerequisiteLessonId()), 0));
    Context c = h.itemView.getContext();
    String state =
        c.getString(
            percent >= 50
                ? R.string.ex_completed
                : unlocked ? R.string.ex_unlocked : R.string.ex_locked);
    h.text.setText(
        c.getString(
            R.string.ex_lesson_row,
            l.getName(),
            l.getStageName(),
            l.getEstimatedMinutes(),
            l.getBaseXp(),
            state,
            Values.integer(stars.get(l.getId()), 0),
            percent));
    h.itemView.setEnabled(unlocked);
    h.itemView.setClickable(unlocked);
    h.itemView.setFocusable(unlocked);
    h.itemView.setOnClickListener(unlocked ? v -> listener.open(l) : null);
  }
}
