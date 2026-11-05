package com.example.fitnessbalance;

import android.os.Bundle;
import android.text.TextUtils;
import android.util.Patterns;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;


public class ForgetPasswordActivity extends AppCompatActivity {

    private EditText emailEditText;
    private Button sendResetButton;
    private ProgressBar progressBar;
    private AuthFlowViewModel model;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_forget_password);

        model = new androidx.lifecycle.ViewModelProvider(this).get(AuthFlowViewModel.class);
        initializeViews();
        setupClickListeners();
        model.state.observe(this,state->{
            showLoading(state.busy);
            if(AuthInput.RESET_ACCEPTED.equals(state.message))showSuccessDialog();
            else if(!state.message.isEmpty())Toast.makeText(this,state.message,Toast.LENGTH_LONG).show();
        });
    }

    private void initializeViews() {
        findViewById(R.id.backButton).setOnClickListener(v -> finish());
        emailEditText = findViewById(R.id.emailEditText);
        sendResetButton = findViewById(R.id.sendResetButton);
        progressBar = findViewById(R.id.progressBar);
        emailEditText.clearFocus();
    }

    private void setupClickListeners() {
        sendResetButton.setOnClickListener(v -> attemptPasswordReset());
    }

    private void attemptPasswordReset() {
        String email = emailEditText.getText().toString().trim();

        if (validateEmail(email)) {
            showLoading(true);
            sendPasswordResetEmail(email);
        }
    }

    private boolean validateEmail(String email) {
        if (TextUtils.isEmpty(email)) {
            emailEditText.setError("Email is required");
            return false;
        }
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            emailEditText.setError("Enter a valid email address");
            return false;
        }
        return true;
    }

    private void sendPasswordResetEmail(String email) {
        model.reset(email);
    }

    private void showSuccessDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Password reset requested")
                .setMessage(AuthInput.RESET_ACCEPTED)
                .setPositiveButton("OK", (dialog, which) -> finish())
                .setCancelable(false)
                .show();
    }

    private void showLoading(boolean show) {
        progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
        sendResetButton.setEnabled(!show);
        sendResetButton.setText(show ? "Sending..." : "Send Reset Link");
    }
}