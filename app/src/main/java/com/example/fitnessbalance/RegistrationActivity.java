package com.example.fitnessbalance;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Patterns;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;


public class RegistrationActivity extends AppCompatActivity {
    private EditText fullNameEditText, emailEditText, passwordEditText, confirmPasswordEditText, weightEditText;
    private AutoCompleteTextView fitnessLevelSpinner;
    private Button signUpButton;
    private ProgressBar progressBar;
    private FirebaseAuth auth;
    private AuthFlowViewModel model;
    private boolean routing;

    private void continueSetup() { if (!routing) { routing=true; AuthGateActivity.open(this); } }

    @Override protected void onStart() {
        super.onStart();
        if (auth.getCurrentUser() != null && !model.state.getValue().busy) continueSetup();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_registration);

        auth = FirebaseAuth.getInstance();

        initializeViews();
        setupFitnessLevelSpinner();
        setupClickListeners();
        model = new androidx.lifecycle.ViewModelProvider(this).get(AuthFlowViewModel.class);
        model.state.observe(this, state -> {
            showLoading(state.busy);
            if (state.gate) { continueSetup(); }
            else if (!state.message.isEmpty()) Toast.makeText(this,state.message,Toast.LENGTH_LONG).show();
        });
    }

    private void initializeViews() {
        fullNameEditText = findViewById(R.id.fullNameEditText);
        emailEditText = findViewById(R.id.emailEditText);
        passwordEditText = findViewById(R.id.passwordEditText);
        confirmPasswordEditText = findViewById(R.id.confirmPasswordEditText);
        weightEditText = findViewById(R.id.weightEditText);
        fitnessLevelSpinner = findViewById(R.id.fitnessLevelSpinner);
        signUpButton = findViewById(R.id.signUpButton);
        progressBar = findViewById(R.id.progressBar);

        findViewById(R.id.loginRedirectText).setOnClickListener(v -> {
            startActivity(new Intent(RegistrationActivity.this, LoginActivity.class));
            finish();
        });

        findViewById(R.id.backButton).setOnClickListener(v -> finish());
    }

    private void setupFitnessLevelSpinner() {
        String[] FITNESS_LEVELS = {"Beginner", "Intermediate", "Advanced"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_dropdown_item_1line,
                FITNESS_LEVELS
        );

        fitnessLevelSpinner.setAdapter(adapter);
        fitnessLevelSpinner.setOnClickListener(v -> fitnessLevelSpinner.showDropDown());
        fitnessLevelSpinner.setFocusable(false);
        fitnessLevelSpinner.setFocusableInTouchMode(false);
    }

    private void setupClickListeners() {
        signUpButton.setOnClickListener(v -> attemptRegistration());

        findViewById(R.id.loginRedirectText).setOnClickListener(v -> {
            startActivity(new Intent(RegistrationActivity.this, LoginActivity.class));
            finish();
        });
    }

    private void attemptRegistration() {
        String fullName = fullNameEditText.getText().toString().trim();
        String email = emailEditText.getText().toString().trim();
        String password = passwordEditText.getText().toString();
        String confirmPassword = confirmPasswordEditText.getText().toString();
        String weightStr = weightEditText.getText().toString().trim();
        String fitnessLevel = fitnessLevelSpinner.getText().toString().trim();

        if (validateInputs(fullName, email, password, confirmPassword, weightStr, fitnessLevel)) {
            showLoading(true);
            model.register(fullName, email, password, Double.parseDouble(weightStr), fitnessLevel);
        }
    }

    private boolean validateInputs(String fullName, String email, String password,
                                   String confirmPassword, String weightStr, String fitnessLevel) {

        String passwordPattern = "^(?=.*[0-9])(?=.*[!@#$%^&*])(?=\\S+$).{8,}$";

        if (!AuthInput.validName(fullName)) {
            fullNameEditText.setError("Enter a name of 1–200 characters");
            return false;
        }

        if (TextUtils.isEmpty(email)) {
            emailEditText.setError("Email is required");
            return false;
        }
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            emailEditText.setError("Enter a valid email address");
            return false;
        }

        if (TextUtils.isEmpty(password)) {
            passwordEditText.setError("Password is required");
            return false;
        }
        if (!password.matches(passwordPattern)) {
            passwordEditText.setError("Password must be at least 8 characters and include at least one number and one special symbol (!@#$%^&*), with no whitespace");
            return false;
        }

        if (TextUtils.isEmpty(confirmPassword)) {
            confirmPasswordEditText.setError("Please confirm your password");
            return false;
        }
        if (!password.equals(confirmPassword)) {
            confirmPasswordEditText.setError("Passwords do not match");
            return false;
        }

        if (TextUtils.isEmpty(weightStr)) {
            weightEditText.setError("Weight is required");
            return false;
        }
        try {
            double weight = Double.parseDouble(weightStr);
            if (!AuthInput.validWeight(weight)) {
                weightEditText.setError("Enter a valid weight (1-300 kg)");
                return false;
            }
        } catch (NumberFormatException e) {
            weightEditText.setError("Enter a valid weight");
            return false;
        }

        if (!AuthInput.validLevel(fitnessLevel)) {
            fitnessLevelSpinner.setError("Select your fitness level");
            return false;
        }

        return true;
    }

    private void showLoading(boolean show) {
        progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
        signUpButton.setEnabled(!show);
        findViewById(R.id.loginRedirectText).setEnabled(!show);
        findViewById(R.id.backButton).setEnabled(!show);
        signUpButton.setText(show ? "Creating Account..." : "Create Account");
    }
}