package com.example.fitnessbalance.adapter;

import android.view.*;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.example.fitnessbalance.R;
import java.util.*;

public class ExerciseRowsAdapter extends RecyclerView.Adapter<ExerciseRowsAdapter.Holder> {
  public static final class Row {
    public final String title, body, state;
    public final Runnable action;

    public Row(String title, String body, String state, Runnable action) {
      this.title = title;
      this.body = body;
      this.state = state;
      this.action = action;
    }
  }

  private final List<Row> rows;

  public ExerciseRowsAdapter(List<Row> rows) {
    this.rows = new ArrayList<>(rows);
  }

  public static final class Holder extends RecyclerView.ViewHolder {
    final TextView title, body, state;

    Holder(View v) {
      super(v);
      title = v.findViewById(R.id.exRowTitle);
      body = v.findViewById(R.id.exRowBody);
      state = v.findViewById(R.id.exRowState);
    }
  }

  @NonNull
  @Override
  public Holder onCreateViewHolder(@NonNull ViewGroup p, int type) {
    return new Holder(
        LayoutInflater.from(p.getContext()).inflate(R.layout.item_exercise_module, p, false));
  }

  @Override
  public void onBindViewHolder(@NonNull Holder h, int position) {
    Row row = rows.get(position);
    h.title.setText(row.title);
    h.body.setText(row.body);
    h.state.setText(row.state);
    h.body.setVisibility(row.body.isEmpty() ? View.GONE : View.VISIBLE);
    h.state.setVisibility(row.state.isEmpty() ? View.GONE : View.VISIBLE);
    h.itemView.setOnClickListener(row.action == null ? null : v -> row.action.run());
    h.itemView.setClickable(row.action != null);
    h.itemView.setFocusable(row.action != null);
  }

  @Override
  public int getItemCount() {
    return rows.size();
  }
}
