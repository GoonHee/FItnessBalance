package com.example.fitnessbalance;

import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import com.google.firebase.auth.FirebaseAuth;

/** Reachable recovery for created accounts, email verification and missing profiles. */
public final class AuthGateActivity extends AppCompatActivity {
    private AuthFlowViewModel model;
    private TextView status;
    private LinearLayout profile;
    private EditText name,weight;
    private Spinner level;
    private Button save,check,resend,logout;
    private boolean navigated;
    static void open(android.app.Activity activity) {
        activity.startActivity(new Intent(activity,AuthGateActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));activity.finish();
    }
    private Button button(LinearLayout parent,String label){Button b=new Button(this);b.setText(label);parent.addView(b);return b;}
    private EditText input(LinearLayout parent,String hint,int type){EditText e=new EditText(this);e.setHint(hint);e.setInputType(type);parent.addView(e);return e;}
    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        ScrollView scroll=new ScrollView(this);LinearLayout body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);int pad=(int)(24*getResources().getDisplayMetrics().density);body.setPadding(pad,pad,pad,pad);scroll.addView(body);setContentView(scroll);
        TextView title=new TextView(this);title.setText("Finish account setup");title.setTextSize(24);body.addView(title);
        status=new TextView(this);status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);body.addView(status);
        profile=new LinearLayout(this);profile.setOrientation(LinearLayout.VERTICAL);body.addView(profile);
        name=input(profile,"Full name",InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PERSON_NAME);name.setId(R.id.fullNameEditText);
        weight=input(profile,"Weight (kg)",InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);weight.setId(R.id.weightEditText);
        level=new Spinner(this);level.setId(R.id.fitnessLevelSpinner);level.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"Beginner","Intermediate","Advanced"}));profile.addView(level);
        save=button(profile,"Save profile");check=button(body,"Check verification / retry setup");resend=button(body,"Resend verification email");logout=button(body,"Sign out");
        model=new ViewModelProvider(this).get(AuthFlowViewModel.class);
        save.setOnClickListener(v->{String n=name.getText().toString().trim();double w;try{w=Double.parseDouble(weight.getText().toString());}catch(NumberFormatException error){w=Double.NaN;}
            if(!AuthInput.validName(n)){name.setError("Enter a name of 1–200 characters");return;}if(!AuthInput.validWeight(w)){weight.setError("Enter a finite weight greater than 0 and at most 300 kg");return;}model.repair(n,w,level.getSelectedItem().toString());});
        check.setOnClickListener(v->model.check());resend.setOnClickListener(v->model.resend());
        logout.setOnClickListener(v->{FirebaseAuth.getInstance().signOut();login();});
        model.state.observe(this,s->{
            status.setText(s.busy?"Checking account…":s.message);check.setEnabled(!s.busy);resend.setEnabled(!s.busy);save.setEnabled(!s.busy);logout.setEnabled(!s.busy);
            profile.setVisibility(s.needsProfile?View.VISIBLE:View.GONE);
            if(s.home&&!navigated){navigated=true;startActivity(new Intent(this,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));finish();}
        });
    }
    @Override protected void onStart(){super.onStart();if(FirebaseAuth.getInstance().getCurrentUser()==null)login();else model.check();}
    private void login(){if(navigated)return;navigated=true;startActivity(new Intent(this,LoginActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));finish();}
}
