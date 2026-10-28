package com.example.fitnessbalance.adapter;

import android.content.Context;
import android.view.*;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.example.fitnessbalance.R;
import com.example.fitnessbalance.model.ActivityDay;
import com.example.fitnessbalance.util.Values;
import java.util.*;

public final class ActivityLogAdapter extends RecyclerView.Adapter<ActivityLogAdapter.Holder> {
  public interface Actions {
    void edit(Map<String, Object> item);

    void delete(Map<String, Object> item);

    void view(String recordId);
  }

  private final List<Map<String, Object>> rows = new ArrayList<>();
  private final int manualCount;
  private final Actions actions;

  public ActivityLogAdapter(ActivityDay day, Actions actions) {
    rows.addAll(day.manual);
    manualCount = rows.size();
    rows.addAll(day.guided);
    this.actions = actions;
  }

  public static final class Holder extends RecyclerView.ViewHolder {
    final TextView source, name, details, state;
    final View edit, delete, view;

    Holder(View v) {
      super(v);
      source = v.findViewById(R.id.alRowSource);
      name = v.findViewById(R.id.alRowName);
      details = v.findViewById(R.id.alRowDetails);
      state = v.findViewById(R.id.alRowState);
      edit = v.findViewById(R.id.alRowEdit);
      delete = v.findViewById(R.id.alRowDelete);
      view = v.findViewById(R.id.alRowView);
    }
  }

  @NonNull
  @Override
  public Holder onCreateViewHolder(@NonNull ViewGroup parent, int type) {
    return new Holder(
        LayoutInflater.from(parent.getContext())
            .inflate(R.layout.item_activity_log, parent, false));
  }

  @Override
  public void onBindViewHolder(@NonNull Holder h, int position) {
    Map<String, Object> row = rows.get(position);
    boolean manual = position < manualCount;
    Context c = h.itemView.getContext();
    h.source.setText(manual ? R.string.al_manual : R.string.al_guided);
    String name = Values.text(row.get(manual ? "name" : "lessonName"), "");
    h.name.setText(
        name.isEmpty() ? c.getString(manual ? R.string.al_legacy : R.string.al_guided) : name);
    double minutes =
        manual
            ? Values.decimal(row.get("duration"), 0)
            : Values.decimal(row.get("durationSeconds"), 0) / 60;
    double calories = Values.decimal(row.get(manual ? "calories" : "estimatedCaloriesBurned"), 0);
    h.details.setText(
        manual && Boolean.TRUE.equals(row.get("legacySummary"))
            ? c.getString(R.string.al_legacy_duration, calories)
            : c.getString(R.string.al_row, minutes, calories));
    h.state.setText(
        manual
            ? c.getString(
                Boolean.TRUE.equals(row.get("completed"))
                    ? R.string.al_completed
                    : R.string.al_planned)
            : c.getString(
                    Values.decimal(row.get("completionPercentage"), 0) >= 50
                        ? R.string.al_completed
                        : R.string.al_attempted)
                + " · "
                + c.getString(
                    R.string.al_guided_completion,
                    Values.decimal(row.get("completionPercentage"), 0)));
    h.edit.setVisibility(manual ? View.VISIBLE : View.GONE);
    h.delete.setVisibility(manual ? View.VISIBLE : View.GONE);
    h.view.setVisibility(manual ? View.GONE : View.VISIBLE);
    h.edit.setOnClickListener(v -> actions.edit(row));
    h.delete.setOnClickListener(v -> actions.delete(row));
    h.view.setOnClickListener(v -> actions.view(Values.text(row.get("recordId"), "")));
  }

  @Override
  public int getItemCount() {
    return rows.size();
  }
}
