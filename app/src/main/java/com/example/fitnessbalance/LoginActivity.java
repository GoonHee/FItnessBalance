package com.example.fitnessbalance;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Patterns;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class LoginActivity extends AppCompatActivity {
    private TextInputEditText emailEditText, passwordEditText;
    private Button loginButton;
    private ProgressBar progressBar;
    private FirebaseAuth auth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        auth = FirebaseAuth.getInstance();
        initializeViews();
        setupClickListeners();
    }

    private void initializeViews() {
        emailEditText = findViewById(R.id.emailEditText);
        passwordEditText = findViewById(R.id.passwordEditText);
        loginButton = findViewById(R.id.loginButton);
        progressBar = findViewById(R.id.progressBar);
    }

    private void setupClickListeners() {
        loginButton.setOnClickListener(v -> attemptLogin());

        findViewById(R.id.forgotPasswordText).setOnClickListener(v -> {
            startActivity(new Intent(LoginActivity.this, ForgetPasswordActivity.class));
        });

        findViewById(R.id.signUpText).setOnClickListener(v -> {
            startActivity(new Intent(LoginActivity.this, RegistrationActivity.class));
        });
    }

    private void attemptLogin() {
        String email = emailEditText.getText().toString().trim();
        String password = passwordEditText.getText().toString().trim();

        if (validateInputs(email, password)) {
            showLoading(true);
            auth.signInWithEmailAndPassword(email, password)
                    .addOnCompleteListener(this, task -> {
                        showLoading(false);
                        if (task.isSuccessful()) {
                            checkEmailVerification();
                        } else {
                            showError("Incorrect email or password. Please try again.");
                        }
                    });
        }
    }

    private void checkEmailVerification() {
        FirebaseUser user = auth.getCurrentUser();
        if (user != null) {
            if (user.isEmailVerified()) {
                Toast.makeText(this, "Login Successful!", Toast.LENGTH_SHORT).show();
                startActivity(new Intent(this, MainActivity.class));
                finish();
            } else {
                showError("Please verify your email before logging in.");
                auth.signOut();
            }
        }
    }

    private boolean validateInputs(String email, String password) {
        if (TextUtils.isEmpty(email)) {
            emailEditText.setError("Email is required.");
            emailEditText.requestFocus();
            return false;
        }
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            emailEditText.setError("Please enter a valid email address.");
            emailEditText.requestFocus();
            return false;
        }
        if (TextUtils.isEmpty(password)) {
            passwordEditText.setError("Password is required.");
            passwordEditText.requestFocus();
            return false;
        }
        return true;
    }

    private void showLoading(boolean isLoading) {
        progressBar.setVisibility(isLoading ? View.VISIBLE : View.GONE);
        loginButton.setEnabled(!isLoading);
    }

    private void showError(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }
}
