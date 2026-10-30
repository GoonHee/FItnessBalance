package com.example.fitnessbalance;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import com.example.fitnessbalance.health.HealthSummaryStore;
import com.google.firebase.auth.*;
import java.util.*;

public final class FoodRecommendationsActivity extends AppCompatActivity {
    private String uid,day; private EditText allowance; private Spinner meal;
    private CheckBox respect; private Button generate; private TextView status,context;
    private LinearLayout results; private int version;
    private FoodRecommendationService.Result latest;
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        FirebaseUser user=FirebaseAuth.getInstance().getCurrentUser(); if(user==null) { finish(); return; } uid=user.getUid();
        day=getIntent().getStringExtra("date"); if(!FoodUi.validDate(day)) day=FoodUi.date(new Date());
        ScrollView root=new ScrollView(this); root.setId(R.id.foodRoot);
        LinearLayout form=new LinearLayout(this); form.setOrientation(LinearLayout.VERTICAL);
        int pad=(int)(20*getResources().getDisplayMetrics().density); form.setPadding(pad,pad,pad,pad);
        root.addView(form); setContentView(root); FoodUi.insets(this);
        Button back=button(form,R.string.back); back.setOnClickListener(v->finish());
        text(form,R.string.fr_title).setTextSize(24); text(form,R.string.fr_intro);
        TextView date=text(form,0); date.setText(getString(R.string.fl_date,day));
        text(form,R.string.fs_meal); meal=new Spinner(this);
        meal.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,getResources().getStringArray(R.array.fs_meals))); form.addView(meal);
        TextView label=text(form,R.string.fr_allowance);
        allowance=new EditText(this); allowance.setId(View.generateViewId()); label.setLabelFor(allowance.getId());
        allowance.setInputType(android.text.InputType.TYPE_CLASS_NUMBER|android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        allowance.setSingleLine(true); allowance.setSaveEnabled(false); form.addView(allowance);
        respect=new CheckBox(this); respect.setText(R.string.fr_respect_goal); respect.setSaveEnabled(false); respect.setChecked(true); form.addView(respect);
        generate=button(form,R.string.fr_generate); generate.setOnClickListener(v->generate());
        status=text(form,0); status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        context=text(form,0); results=new LinearLayout(this); results.setOrientation(LinearLayout.VERTICAL); form.addView(results);
        button(form,R.string.fr_clear_audit).setOnClickListener(v -> {
            new HealthSummaryStore(this,uid,"food_recommendations").clear();
            status.setText(R.string.fr_audit_cleared);
        });
        if(state!=null) { allowance.setText(state.getString("allowance","")); meal.setSelection(Math.max(0,Math.min(3,state.getInt("meal")))); respect.setChecked(state.getBoolean("respect",true)); }
        allowance.addTextChangedListener(new android.text.TextWatcher() {
            public void beforeTextChanged(CharSequence s,int start,int count,int after) {}
            public void onTextChanged(CharSequence s,int start,int before,int count) { invalidateResults(); }
            public void afterTextChanged(android.text.Editable e) {}
        });
        respect.setOnCheckedChangeListener((b,checked)->invalidateResults());
        meal.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> p,View v,int position,long id) { invalidateResults(); }
            public void onNothingSelected(AdapterView<?> p) {}
        });
    }
    private TextView text(LinearLayout form,int id) { TextView t=new TextView(this); if(id!=0)t.setText(id); t.setTextSize(16); t.setPadding(0,12,0,12); form.addView(t); return t; }
    private Button button(LinearLayout form,int id) { Button b=new Button(this); b.setText(id); form.addView(b); return b; }
    private boolean current(int request) { FirebaseUser u=FirebaseAuth.getInstance().getCurrentUser(); return request==version && !isDestroyed() && !isFinishing() && u!=null && uid.equals(u.getUid()); }
    private void invalidateResults() { version++; latest=null; if(results!=null)results.removeAllViews(); if(context!=null)context.setText(""); if(status!=null)status.setText(""); if(generate!=null)generate.setEnabled(true); }
    private void generate() {
        final double budget;
        try { budget=Double.parseDouble(allowance.getText().toString().trim().replace(',','.')); if(!Double.isFinite(budget)||budget<=0||budget>20000)throw new IllegalArgumentException(); }
        catch(IllegalArgumentException invalid) { allowance.setError(getString(R.string.fr_invalid)); return; }
        int request=++version; latest=null; results.removeAllViews(); context.setText(""); status.setText(R.string.fl_loading); generate.setEnabled(false);
        final int selectedMeal=meal.getSelectedItemPosition(); final boolean cap=respect.isChecked();
        new FoodRecommendationRepository().load(uid,day).addOnSuccessListener(this,data->{
            if(!current(request))return;
            generate.setEnabled(true);
            try {
                latest=new FoodRecommendationService().recommend(data.profile,data.logged,data.recent,data.foods,
                        FoodRecommendationService.MEALS.get(selectedMeal),budget,cap);
                latest.audit.put("date",day);
                boolean auditSaved=true;
                try { new HealthSummaryStore(this,uid,"food_recommendations").write(latest.audit); }
                catch(java.io.IOException failed) { auditSaved=false; }
                show(latest,data.foods.isEmpty(),selectedMeal,request);
                if(!auditSaved)status.append("\n"+getString(R.string.fr_audit_error));
            } catch(IllegalArgumentException invalid) { status.setText(R.string.fr_metadata_error); }
        }).addOnFailureListener(this,error->{ if(!current(request))return; generate.setEnabled(true); status.setText(R.string.fr_load_error); });
    }
    private void show(FoodRecommendationService.Result result,boolean emptyCatalogue,int selectedMeal,int request) {
        context.setText(getString(R.string.fr_context,FoodUi.number(result.effectiveAllowance),
                result.remainingCalories==null?getString(R.string.fr_remaining_unknown):FoodUi.number(result.remainingCalories)));
        List<String> dietaryLabels = new ArrayList<>();
        for (String diet : FoodRecommendationService.strictStrings(result.audit.get("dietaryPreferences"))) {
            int index = UserPreferences.DIETS.indexOf(diet);
            if (index >= 0) dietaryLabels.add(getResources().getStringArray(R.array.prefs_diets)[index]);
        }
        context.append("\n"+getString(R.string.fr_applied_diets, dietaryLabels.isEmpty()
                ? getString(R.string.fr_no_diet_preferences) : android.text.TextUtils.join(", ",dietaryLabels)));
        Map<String,Object> macroNeeds = com.example.fitnessbalance.util.Values.map(result.audit.get("macroNeeds"));
        List<String> macroLabels = new ArrayList<>();
        int[] macroResources = {R.string.fl_nutrient_protein_g,R.string.fl_nutrient_carbohydrate_g,R.string.fl_nutrient_fat_g};
        for (int i=0;i<FoodRecommendationService.MACROS.size();i++) {
            Object need = macroNeeds.get(FoodRecommendationService.MACROS.get(i));
            if (need instanceof Number) macroLabels.add(getString(macroResources[i])+": "+FoodUi.number(need));
        }
        if (!macroLabels.isEmpty()) context.append("\n"+getString(R.string.fr_macro_needs,android.text.TextUtils.join("; ",macroLabels)));
        if(result.partialCalories||result.partialMacros)context.append("\n"+getString(R.string.fr_partial));
        status.setText(result.suggestions.isEmpty() ? emptyCatalogue ? R.string.fr_not_ready : R.string.fr_no_matches : R.string.fr_results);
        for(FoodRecommendationService.Suggestion suggestion:result.suggestions) {
            LinearLayout card=new LinearLayout(this); card.setOrientation(LinearLayout.VERTICAL); card.setBackgroundResource(R.drawable.food_panel);
            TextView name=text(card,0); name.setText(suggestion.name); name.setTextSize(20);
            text(card,0).setText(getString(R.string.fr_portion,suggestion.label,FoodUi.number(suggestion.amount),suggestion.unit,FoodUi.number(suggestion.calories)));
            text(card,0).setText(FoodUi.nutrients(this,suggestion.nutrients,null,1));
            for(String reason:suggestion.reasons)text(card,reasonResource(reason));
            if(suggestion.estimated)text(card,R.string.fc_estimate);
            Button details=button(card,R.string.fr_details); details.setOnClickListener(v->{
                if(!current(request))return;
                Intent intent=new Intent(this,FoodDetailsActivity.class);
                intent.putExtra("foodId",suggestion.id); intent.putExtra("date",day);
                intent.putExtra("recommendedAmount",suggestion.amount); intent.putExtra("recommendedUnit",suggestion.unit);
                intent.putExtra("recommendedMeal",getResources().getStringArray(R.array.fs_meals)[selectedMeal]);
                startActivity(intent);
            }); results.addView(card);
        }
    }
    private int reasonResource(String reason) {
        switch(reason) {
            case "preferred":return R.string.fr_reason_preferred;
            case "variety":return R.string.fr_reason_variety;
            case "macro":return R.string.fr_reason_macro;
            case "dietary":return R.string.fr_reason_diet;
            default:return R.string.fr_reason_allowance;
        }
    }
    @Override protected void onResume() { super.onResume(); if(uid==null)return;
        FirebaseUser u=FirebaseAuth.getInstance().getCurrentUser(); if(u==null||!uid.equals(u.getUid())) { finish(); return; }
        // Logs/goals may have changed while viewing a suggested food; require regeneration.
        invalidateResults();
    }
    @Override protected void onStop() { version++; super.onStop(); }
    @Override protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state); if(allowance==null)return;
        state.putString("allowance",allowance.getText().toString()); state.putInt("meal",meal.getSelectedItemPosition()); state.putBoolean("respect",respect.isChecked());
    }
}
