package com.example.fitnessbalance;

import android.os.Bundle;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;

/** Permission rationale is public; no account data is displayed here. */
public final class HealthPermissionRationaleActivity extends AppCompatActivity {
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        ScrollView root = new ScrollView(this); root.setId(R.id.foodRoot);
        LinearLayout content = new LinearLayout(this); content.setOrientation(LinearLayout.VERTICAL);
        int padding = (int)(20 * getResources().getDisplayMetrics().density); content.setPadding(padding,padding,padding,padding);
        root.addView(content); setContentView(root); FoodUi.insets(this);
        TextView title = new TextView(this); title.setText(R.string.hc_privacy); title.setTextSize(24); content.addView(title);
        TextView explanation = new TextView(this); explanation.setText(R.string.hc_privacy_text); explanation.setTextSize(17); content.addView(explanation);
        Button close = new Button(this); close.setText(R.string.back); close.setOnClickListener(v -> finish()); content.addView(close);
    }
}
