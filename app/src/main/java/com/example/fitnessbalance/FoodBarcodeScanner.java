package com.example.fitnessbalance;

import android.app.Activity;
import android.content.Intent;
import android.text.InputType;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import com.google.mlkit.vision.barcode.common.Barcode;
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions;
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning;

public final class FoodBarcodeScanner {

    private FoodBarcodeScanner() {}

    public static void scan(
            Activity activity,
            Button button,
            String date
    ) {
        button.setEnabled(false);

        GmsBarcodeScannerOptions options =
                new GmsBarcodeScannerOptions.Builder()
                        .setBarcodeFormats(
                                Barcode.FORMAT_EAN_13,
                                Barcode.FORMAT_EAN_8,
                                Barcode.FORMAT_UPC_A,
                                Barcode.FORMAT_UPC_E,
                                Barcode.FORMAT_ITF
                        )
                        .enableAutoZoom()
                        .build();

        GmsBarcodeScanning.getClient(activity, options)
                .startScan()
                .addOnSuccessListener(result -> {
                    if (activity.isDestroyed()
                            || activity.isFinishing()) return;

                    button.setEnabled(true);
                    open(activity, result.getRawValue(), date);
                })
                .addOnCanceledListener(() -> {
                    if (!activity.isDestroyed()) {
                        button.setEnabled(true);
                    }
                })
                .addOnFailureListener(error -> {
                    if (activity.isDestroyed()
                            || activity.isFinishing()) return;

                    button.setEnabled(true);

                    Toast.makeText(
                            activity,
                            R.string.bc_scan_failed,
                            Toast.LENGTH_LONG
                    ).show();
                });
    }

    public static void enter(Activity activity, String date) {
        EditText input = new EditText(activity);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setHint(R.string.bc_hint);

        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle(R.string.bc_enter)
                .setView(input)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.fl_search, null)
                .create();

        dialog.setOnShowListener(unused ->
                dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                        .setOnClickListener(v -> {
                            String code = input.getText()
                                    .toString().trim();

                            if (!BarcodeFood.validBarcode(code)) {
                                input.setError(activity.getString(
                                        R.string.bc_invalid
                                ));
                                return;
                            }

                            dialog.dismiss();
                            open(activity, code, date);
                        })
        );

        dialog.show();
    }

    private static void open(
            Activity activity,
            String code,
            String date
    ) {
        if (!BarcodeFood.validBarcode(code)) {
            Toast.makeText(
                    activity,
                    R.string.bc_invalid,
                    Toast.LENGTH_LONG
            ).show();
            return;
        }

        Intent intent = new Intent(
                activity,
                FoodDetailsActivity.class
        );

        intent.putExtra("barcode", code);
        intent.putExtra("date", date);

        activity.startActivity(intent);
    }
}