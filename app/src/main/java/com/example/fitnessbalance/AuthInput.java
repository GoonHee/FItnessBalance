package com.example.fitnessbalance;

/** Shared registration/profile validation; passwords are never normalised. */
final class AuthInput {
    static final String RESET_ACCEPTED = "If an account exists for this email, a reset link will be sent. Check your inbox and spam folder.";
    static boolean validName(String name) { return name != null && !name.trim().isEmpty() && name.length() <= 200; }
    static boolean validWeight(double value) { return Double.isFinite(value) && value > 0 && value <= 300; }
    static boolean validLevel(String value) { return "Beginner".equals(value) || "Intermediate".equals(value) || "Advanced".equals(value); }
    static boolean completeProfile(java.util.Map<String,Object> data) {
        if (data == null || !(data.get("fullName") instanceof String) || !validName((String)data.get("fullName"))) return false;
        Object weight=data.get("weight");
        return weight instanceof Number && validWeight(((Number)weight).doubleValue()) && validLevel(String.valueOf(data.get("fitnessLevel")));
    }
    static String error(Exception error, String fallback) {
        if (error instanceof com.google.firebase.FirebaseNetworkException) return "No connection. Check your internet and try again.";
        if (error instanceof com.google.firebase.FirebaseTooManyRequestsException) return "Too many requests. Please wait before trying again.";
        return fallback;
    }
}
