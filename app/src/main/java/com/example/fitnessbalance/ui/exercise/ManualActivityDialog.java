package com.example.fitnessbalance.ui.exercise;

import android.app.Dialog;
import android.os.Bundle;
import android.text.*;
import android.view.*;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import com.example.fitnessbalance.R;
import com.example.fitnessbalance.model.ActivityDay;
import com.example.fitnessbalance.repository.ExerciseLogRepository;
import com.example.fitnessbalance.service.*;
import com.example.fitnessbalance.util.Values;
import com.google.android.gms.tasks.Task;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.auth.FirebaseAuth;
import java.util.*;
import org.json.*;

/** Stable entry/mutation IDs survive fragment recreation and uncertain save responses. */
public final class ManualActivityDialog extends DialogFragment {
  public static final String RESULT = "activity_saved";
  private static final String[] CODES = {
    "walking",
    "running",
    "cycling",
    "swimming",
    "strength",
    "yoga",
    "pilates",
    "basketball",
    "soccer",
    "tennis",
    "hiit",
    "dancing",
    "hiking",
    "jump_rope",
    "rowing",
    "other"
  };
  private static final double[] METS = {
    3.5, 7, 6, 5.8, 3, 2.5, 3, 6.5, 7, 5, 8, 4.5, 4.5, 8.8, 6, 3
  };
  private Spinner type;
  private EditText custom, minutes;
  private CheckBox completed;
  private TextView preview, status;
  private boolean saving;
  private String[] names;
  private final ExerciseCalorieService calories = new ExerciseCalorieService();

  public static ManualActivityDialog create(
      String uid,
      String day,
      double weight,
      ArrayList<String> preferences,
      Map<String, Object> item) {
    ManualActivityDialog dialog = new ManualActivityDialog();
    Bundle args = new Bundle();
    args.putString("uid", uid);
    args.putString("day", day);
    args.putDouble("weight", weight);
    args.putStringArrayList("preferences", preferences);
    args.putString("entry", item == null ? "" : new JSONObject(item).toString());
    args.putString(
        "entryId",
        item == null ? UUID.randomUUID().toString() : Values.text(item.get("entryId"), ""));
    args.putString("mutationId", UUID.randomUUID().toString());
    dialog.setArguments(args);
    return dialog;
  }

  @NonNull
  @Override
  public Dialog onCreateDialog(Bundle saved) {
    View view = getLayoutInflater().inflate(R.layout.dialog_manual_activity, null, false);
    type = view.findViewById(R.id.alType);
    custom = view.findViewById(R.id.alCustom);
    minutes = view.findViewById(R.id.alMinutes);
    completed = view.findViewById(R.id.alDone);
    preview = view.findViewById(R.id.alPreview);
    status = view.findViewById(R.id.alFormStatus);
    names = getResources().getStringArray(R.array.al_activity_names);
    ArrayAdapter<String> adapter =
        new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_item, names);
    adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
    type.setAdapter(adapter);
    JSONObject old = original();
    int selected = 0;
    if (editing()) {
      selected = CODES.length - 1;
      String code = old.optString("activityType"), name = old.optString("name");
      for (int n = 0; n < CODES.length; n++)
        if (code.equals(CODES[n]) || name.equalsIgnoreCase(names[n])) selected = n;
      custom.setText(name);
      double duration = old.optDouble("duration", 0);
      minutes.setText(duration <= 0 ? "" : String.valueOf(duration));
      completed.setChecked(old.optBoolean("completed", false));
    } else {
      List<String> preferred = requireArguments().getStringArrayList("preferences");
      if (preferred != null)
        for (String choice : preferred) {
          boolean found = false;
          for (int n = 0; n < names.length; n++)
            if (choice.equalsIgnoreCase(names[n])) {
              selected = n;
              found = true;
              break;
            }
          if (found) break;
        }
    }
    type.setSelection(selected);
    type.setOnItemSelectedListener(
        new android.widget.AdapterView.OnItemSelectedListener() {
          @Override
          public void onItemSelected(
              android.widget.AdapterView<?> parent, View v, int position, long id) {
            custom.setVisibility(position == CODES.length - 1 ? View.VISIBLE : View.GONE);
            updatePreview();
          }

          @Override
          public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });
    minutes.addTextChangedListener(
        new TextWatcher() {
          public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

          public void onTextChanged(CharSequence s, int start, int before, int count) {
            updatePreview();
          }

          public void afterTextChanged(Editable e) {}
        });
    return new MaterialAlertDialogBuilder(requireContext())
        .setTitle(editing() ? R.string.al_form_edit : R.string.al_form_title)
        .setView(view)
        .setNegativeButton(R.string.cancel, null)
        .setPositiveButton(R.string.al_save, null)
        .create();
  }

  @Override
  public void onStart() {
    super.onStart();
    saving = false;
    inputsEnabled(true);
    setCancelable(true);
    AlertDialog dialog = (AlertDialog) requireDialog();
    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);
    dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setEnabled(true);
    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setText(R.string.al_save);
    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> save());
    updatePreview();
  }

  @Override
  public void onDismiss(@NonNull android.content.DialogInterface dialog) {
    super.onDismiss(dialog);
    if (isAdded()) {
      Bundle result = new Bundle();
      result.putString("day", requireArguments().getString("day"));
      getParentFragmentManager().setFragmentResult(RESULT, result);
    }
  }

  private boolean editing() {
    return !requireArguments().getString("entry", "").isEmpty();
  }

  private JSONObject original() {
    try {
      return new JSONObject(requireArguments().getString("entry", "{}"));
    } catch (JSONException e) {
      return new JSONObject();
    }
  }

  private double duration() {
    return Double.parseDouble(minutes.getText().toString().trim().replace(',', '.'));
  }

  private void updatePreview() {
    if (preview == null) return;
    try {
      long value =
          calories.estimate(
              METS[type.getSelectedItemPosition()],
              requireArguments().getDouble("weight", 70),
              duration());
      preview.setText(
          getString(
              R.string.al_estimate,
              value,
              calories.weight(requireArguments().getDouble("weight", 70))));
    } catch (RuntimeException e) {
      preview.setText(R.string.al_estimate_prompt);
    }
  }

  private void save() {
    if (saving) return;
    String uid = requireArguments().getString("uid"), day = requireArguments().getString("day");
    if (FirebaseAuth.getInstance().getCurrentUser() == null
        || !uid.equals(FirebaseAuth.getInstance().getCurrentUser().getUid())) {
      showError(R.string.ex_sign_in);
      return;
    }
    int selected = type.getSelectedItemPosition();
    String name =
        selected == CODES.length - 1 ? custom.getText().toString().trim() : names[selected];
    if (name.isEmpty() || name.length() > 100) {
      custom.setError(getString(R.string.al_name_error));
      return;
    }
    double duration;
    long estimate;
    try {
      duration = duration();
      estimate =
          calories.estimate(METS[selected], requireArguments().getDouble("weight", 70), duration);
    } catch (RuntimeException e) {
      minutes.setError(getString(R.string.al_duration_error));
      return;
    }
    Map<String, Object> entry =
        Values.of(
            "name",
            name,
            "activityType",
            CODES[selected],
            "duration",
            duration,
            "calories",
            estimate,
            "completed",
            completed.isChecked(),
            "metValue",
            METS[selected],
            "weightKg",
            calories.weight(requireArguments().getDouble("weight", 70)),
            "source",
            "manual",
            "calorieMethod",
            "met_estimate",
            "legacySummary",
            false,
            "lastMutationId",
            requireArguments().getString("mutationId"));
    saving = true;
    inputsEnabled(false);
    setCancelable(false);
    AlertDialog dialog = (AlertDialog) requireDialog();
    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
    dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setEnabled(false);
    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setText(R.string.al_saving);
    ExerciseLogRepository repository = new ExerciseLogRepository(uid);
    Task<ActivityDay> task =
        editing()
            ? repository.edit(
                day,
                requireArguments().getString("entryId"),
                original().optInt("revision", 0),
                entry)
            : repository.add(day, requireArguments().getString("entryId"), entry);
    task.addOnSuccessListener(
            requireActivity(),
            result -> {
              if (!isAdded() || getParentFragmentManager().isStateSaved()) return;
              Toast.makeText(requireContext(), R.string.al_saved, Toast.LENGTH_SHORT).show();
              dismiss();
            })
        .addOnFailureListener(
            requireActivity(),
            e -> {
              if (!isAdded() || getParentFragmentManager().isStateSaved()) return;
              saving = false;
              inputsEnabled(true);
              setCancelable(true);
              dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);
              dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setEnabled(true);
              dialog.getButton(AlertDialog.BUTTON_POSITIVE).setText(R.string.al_save);
              showError(
                  e instanceof ExerciseLogService.Conflict
                      ? R.string.al_conflict
                      : R.string.al_save_error);
            });
  }

  private void inputsEnabled(boolean enabled) {
    type.setEnabled(enabled);
    custom.setEnabled(enabled);
    minutes.setEnabled(enabled);
    completed.setEnabled(enabled);
  }

  private void showError(int message) {
    status.setText(message);
    status.setVisibility(View.VISIBLE);
  }
}
