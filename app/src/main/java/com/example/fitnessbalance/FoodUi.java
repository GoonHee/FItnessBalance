package com.example.fitnessbalance;

import android.app.Activity;
import android.util.Log;
import android.view.View;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.firebase.firestore.FirebaseFirestoreException;

import java.text.NumberFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Map;

final class FoodUi {

    private static final int[] LABELS = {
            R.string.fl_nutrient_energy_kcal,
            R.string.fl_nutrient_protein_g,
            R.string.fl_nutrient_carbohydrate_g,
            R.string.fl_nutrient_fat_g,
            R.string.fl_nutrient_fiber_g,
            R.string.fl_nutrient_sodium_mg
    };

    static String date(Date date) {
        return new SimpleDateFormat(
                "yyyy-MM-dd",
                Locale.ROOT
        ).format(date);
    }

    static boolean validDate(String date) {
        if (date == null
                || !date.matches("\\d{4}-\\d{2}-\\d{2}")) {
            return false;
        }

        SimpleDateFormat format = new SimpleDateFormat(
                "yyyy-MM-dd",
                Locale.ROOT
        );
        format.setLenient(false);

        try {
            format.parse(date);
            return true;
        } catch (ParseException e) {
            return false;
        }
    }

    static String number(Object value) {
        Double numeric = FoodNutrition.number(value);

        if (numeric == null) return "—";

        NumberFormat format =
                NumberFormat.getNumberInstance();
        format.setMaximumFractionDigits(1);

        return format.format(numeric);
    }

    static String nutrients(
            Activity activity,
            Map<?, ?> values,
            Map<?, ?> coverage,
            int count
    ) {
        StringBuilder text = new StringBuilder();

        for (int i = 0; i < FoodNutrition.KEYS.length; i++) {
            String key = FoodNutrition.KEYS[i];

            String value =
                    FoodNutrition.number(values.get(key)) == null
                            ? activity.getString(R.string.fl_missing)
                            : number(values.get(key));

            // An empty day has zero totals.
            if (coverage != null && count == 0) {
                value = number(0);
            }

            // Some entries may not contain this nutrient.
            if (coverage != null && values.containsKey(key)) {
                Object covered = coverage.get(key);

                if (!(covered instanceof Number)
                        || ((Number) covered).intValue() < count) {
                    value = activity.getString(
                            R.string.fl_partial,
                            value
                    );
                }
            }

            if (text.length() > 0) {
                text.append('\n');
            }

            text.append(activity.getString(
                    R.string.fl_nutrient_row,
                    activity.getString(LABELS[i]),
                    value
            ));
        }

        return text.toString();
    }

    static String error(
            Activity activity,
            int operation,
            Exception error
    ) {
        Log.e(
                "FoodLog",
                activity.getString(operation),
                error
        );

        String code =
                error instanceof FirebaseFirestoreException
                        ? ((FirebaseFirestoreException) error)
                        .getCode().name()
                        : error.getClass().getSimpleName();

        return activity.getString(
                R.string.fl_error,
                activity.getString(operation),
                code
        );
    }

    static void insets(Activity activity) {
        View root = activity.findViewById(R.id.foodRoot);

        ViewCompat.setOnApplyWindowInsetsListener(
                root,
                (view, windowInsets) -> {
                    Insets bars = windowInsets.getInsets(
                            WindowInsetsCompat.Type.systemBars()
                                    | WindowInsetsCompat.Type.ime()
                    );

                    view.setPadding(
                            bars.left,
                            bars.top,
                            bars.right,
                            bars.bottom
                    );

                    return windowInsets;
                }
        );

        ViewCompat.requestApplyInsets(root);
    }
}