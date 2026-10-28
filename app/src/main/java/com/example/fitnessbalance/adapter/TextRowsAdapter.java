package com.example.fitnessbalance.adapter;

import android.view.*;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.example.fitnessbalance.R;
import java.util.*;

public class TextRowsAdapter extends RecyclerView.Adapter<TextRowsAdapter.Holder> {
  protected final List<String> rows;

  public TextRowsAdapter(List<String> rows) {
    this.rows = rows;
  }

  public static class Holder extends RecyclerView.ViewHolder {
    public final TextView text;

    Holder(View v) {
      super(v);
      text = v.findViewById(R.id.exRowText);
    }
  }

  @NonNull
  @Override
  public Holder onCreateViewHolder(@NonNull ViewGroup p, int type) {
    return new Holder(LayoutInflater.from(p.getContext()).inflate(R.layout.item_workout, p, false));
  }

  @Override
  public void onBindViewHolder(@NonNull Holder h, int pos) {
    h.text.setText(rows.get(pos));
  }

  @Override
  public int getItemCount() {
    return rows.size();
  }
}
