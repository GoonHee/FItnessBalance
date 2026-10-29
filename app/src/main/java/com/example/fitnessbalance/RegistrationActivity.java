package com.example.fitnessbalance;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.util.Patterns;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class RegistrationActivity extends AppCompatActivity {
    private EditText fullNameEditText, emailEditText, passwordEditText, confirmPasswordEditText, weightEditText;
    private AutoCompleteTextView fitnessLevelSpinner;
    private Button signUpButton;
    private ProgressBar progressBar;
    private FirebaseAuth auth;
    private FirebaseFirestore firestore;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_registration);

        auth = FirebaseAuth.getInstance();
        firestore = FirebaseFirestore.getInstance();

        initializeViews();
        setupFitnessLevelSpinner();
        setupClickListeners();
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
        String password = passwordEditText.getText().toString().trim();
        String confirmPassword = confirmPasswordEditText.getText().toString().trim();
        String weightStr = weightEditText.getText().toString().trim();
        String fitnessLevel = fitnessLevelSpinner.getText().toString().trim();

        if (validateInputs(fullName, email, password, confirmPassword, weightStr, fitnessLevel)) {
            showLoading(true);
            registerUser(fullName, email, password, Double.parseDouble(weightStr), fitnessLevel);
        }
    }

    private boolean validateInputs(String fullName, String email, String password,
                                   String confirmPassword, String weightStr, String fitnessLevel) {

        String passwordPattern = "^(?=.*[0-9])(?=.*[!@#$%^&*])(?=\\S+$).{8,}$";

        if (TextUtils.isEmpty(fullName)) {
            fullNameEditText.setError("Full name is required");
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
            passwordEditText.setError("Password must be at least 8 characters and include at least one number and one special symbol (!@#$%^&*)");
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
            if (weight <= 0 || weight > 300) {
                weightEditText.setError("Enter a valid weight (1-300 kg)");
                return false;
            }
        } catch (NumberFormatException e) {
            weightEditText.setError("Enter a valid weight");
            return false;
        }

        if (TextUtils.isEmpty(fitnessLevel)) {
            fitnessLevelSpinner.setError("Select your fitness level");
            return false;
        }

        return true;
    }

    private void registerUser(String fullName, String email, String password,
                              double weight, String fitnessLevel) {

        auth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        sendEmailVerification(fullName, weight, fitnessLevel);
                    } else {
                        showLoading(false);
                        String errorMessage = task.getException() != null ?
                                task.getException().getMessage() : "Registration failed";
                        Toast.makeText(RegistrationActivity.this,
                                "Error: " + errorMessage, Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void sendEmailVerification(String fullName, double weight, String fitnessLevel) {
        FirebaseUser user = auth.getCurrentUser();

        if (user != null) {
            user.sendEmailVerification()
                    .addOnCompleteListener(task -> {
                        if (task.isSuccessful()) {
                            saveUserToFirestore(user.getUid(), fullName, weight, fitnessLevel, user.getEmail());
                            showVerificationDialog();
                        } else {
                            showLoading(false);
                            Toast.makeText(RegistrationActivity.this,
                                    "Failed to send verification email.", Toast.LENGTH_LONG).show();
                        }
                    });
        }
    }

    private void saveUserToFirestore(String userId, String fullName, double weight,
                                     String fitnessLevel, String email) {

        Map<String, Object> userData = new HashMap<>();
        userData.put("fullName", fullName);
        userData.put("weight", weight);
        userData.put("fitnessLevel", fitnessLevel);
        userData.put("email", email);
        userData.put("createdAt", new java.util.Date());
        userData.put("isVerified", false);
        // Nutrition goals stay unset until the user explicitly chooses them.
        userData.put("nutritionGoalSource", "not_set");

        firestore.collection("users")
                .document(userId)
                .set(userData)
                .addOnSuccessListener(aVoid -> Log.d("Registration", "User data saved to Firestore"))
                .addOnFailureListener(e -> Log.e("Registration", "Error saving user data", e));
    }

    private void showVerificationDialog() {
        showLoading(false);

        new AlertDialog.Builder(this)
                .setTitle("Verify Your Email")
                .setMessage("We've sent a verification link to your email address. Please verify your account before logging in.")
                .setPositiveButton("OK", (dialog, which) -> {
                    startActivity(new Intent(RegistrationActivity.this, LoginActivity.class));
                    finish();
                })
                .setCancelable(false)
                .show();
    }

    private void showLoading(boolean show) {
        progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
        signUpButton.setEnabled(!show);
        signUpButton.setText(show ? "Creating Account..." : "Create Account");
    }
}