package com.example.fitnessbalance;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.*;
import androidx.activity.result.ActivityResultLauncher;
import androidx.appcompat.app.AppCompatActivity;
import androidx.health.connect.client.HealthConnectClient;
import com.example.fitnessbalance.health.*;
import com.google.firebase.auth.*;
import java.text.DateFormat;
import java.time.LocalDate;
import java.util.*;

public final class HealthConnectActivity extends AppCompatActivity {
    private String uid;
    private HealthConnectBridge bridge;
    private HealthSummaryStore store;
    private TextView availability, permissionStatus, summary, lastSync, status;
    private Button connect, sync, settings;
    private boolean busy;
    private int version;
    private Set<String> granted = Collections.emptySet();
    private Map<String, Object> snapshot = new HashMap<>();
    private final ActivityResultLauncher<Set<String>> permissionRequest = registerForActivityResult(
            HealthConnectBridge.permissionContract(), result -> refresh());

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) { finish(); return; }
        uid = user.getUid(); bridge = new HealthConnectBridge(this); store = new HealthSummaryStore(this, uid);
        ScrollView root = new ScrollView(this); root.setId(R.id.foodRoot);
        LinearLayout form = new LinearLayout(this); form.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (20 * getResources().getDisplayMetrics().density); form.setPadding(pad,pad,pad,pad);
        root.addView(form); setContentView(root); FoodUi.insets(this);
        button(form, R.string.back).setOnClickListener(v -> finish());
        TextView title = text(form, R.string.hc_title); title.setTextSize(24);
        text(form, R.string.hc_intro);
        availability = text(form, R.string.fl_loading);
        permissionStatus = text(form, R.string.hc_permissions_checking);
        connect = button(form, R.string.hc_connect);
        connect.setOnClickListener(v -> {
            if (busy) return;
            int sdk = HealthConnectBridge.availability(this);
            if (sdk == HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED) openProvider();
            else if (sdk == HealthConnectClient.SDK_AVAILABLE) {
                try { permissionRequest.launch(HealthConnectBridge.permissions()); }
                catch (RuntimeException e) { status.setText(R.string.hc_error); }
            }
        });
        sync = button(form, R.string.hc_sync); sync.setEnabled(false); sync.setOnClickListener(v -> sync());
        settings = button(form, R.string.hc_settings); settings.setOnClickListener(v -> {
            try { startActivity(HealthConnectBridge.settings(this)); }
            catch (RuntimeException e) { status.setText(R.string.hc_error); }
        });
        lastSync = text(form, R.string.hc_never_synced);
        summary = text(form, R.string.hc_no_summary);
        text(form, R.string.hc_freshness_note);
        status = text(form, 0); status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        button(form, R.string.hc_retry).setOnClickListener(v -> refresh());
        button(form, R.string.hc_privacy).setOnClickListener(v -> startActivity(new Intent(this, HealthPermissionRationaleActivity.class)));
        button(form, R.string.hc_clear).setOnClickListener(v -> {
            version++; bridge.cancelPending(); store.clear();
            new HealthRecommendationStore(this, uid).clear();
            snapshot.clear(); busy = false;
            render(); refresh(); status.setText(R.string.hc_cleared);
        });
    }
    private TextView text(LinearLayout form, int resource) {
        TextView view = new TextView(this); view.setTextSize(16); view.setPadding(0,12,0,12);
        if (resource != 0) view.setText(resource); form.addView(view); return view;
    }
    private Button button(LinearLayout form, int resource) { Button b = new Button(this); b.setText(resource); form.addView(b); return b; }
    private boolean current(int request) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        return request == version && !isFinishing() && !isDestroyed() && user != null && uid.equals(user.getUid());
    }
    private void refresh() {
        if (bridge == null) return;
        int request = ++version; bridge.cancelPending(); busy = true; granted = Collections.emptySet();
        summary.setText(R.string.hc_no_summary); status.setText("");
        Object cachedSync = store.read().get("syncedAt");
        lastSync.setText(cachedSync == null ? getString(R.string.hc_never_synced)
                : getString(R.string.hc_last_sync, date(cachedSync)));
        sync.setEnabled(false); settings.setEnabled(false); connect.setEnabled(false);
        final int sdk;
        try { sdk = HealthConnectBridge.availability(this); }
        catch (RuntimeException unavailable) { busy = false; availability.setText(R.string.hc_unavailable); return; }
        availability.setText(sdk == HealthConnectClient.SDK_AVAILABLE ? R.string.hc_available
                : sdk == HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED ? R.string.hc_install_needed : R.string.hc_unavailable);
        connect.setText(sdk == HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED ? R.string.hc_install : R.string.hc_connect);
        if (sdk != HealthConnectClient.SDK_AVAILABLE) {
            busy = false; connect.setEnabled(sdk == HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED);
            permissionStatus.setText(R.string.hc_permissions_unavailable);
            return;
        }
        permissionStatus.setText(R.string.hc_permissions_checking);
        bridge.check(new HealthConnectBridge.Callback() {
            @Override public void onResult(Map<String, ? extends Object> ignored, Set<String> permissions) {
                if (!current(request)) return;
                granted = new HashSet<>(permissions); snapshot = HealthSummaryPolicy.mask(store.read(), allowed());
                if (!granted.contains(HealthConnectBridge.SLEEP)) {
                    try { new HealthRecommendationStore(HealthConnectActivity.this, uid).removeSleepInputs(); }
                    catch (java.io.IOException e) { status.setText(R.string.hc_cache_error); }
                }
                try { store.write(snapshot); } catch (java.io.IOException e) { status.setText(R.string.hc_cache_error); }
                busy = false; render();
            }
            @Override public void onError() {
                if (!current(request)) return;
                busy = false; permissionStatus.setText(R.string.hc_permissions_unavailable);
                status.setText(R.string.hc_error); connect.setEnabled(true);
            }
        });
    }
    private Set<String> allowed() {
        Set<String> result = new HashSet<>();
        if (granted.contains(HealthConnectBridge.STEPS)) result.add("steps");
        if (granted.contains(HealthConnectBridge.SLEEP)) result.add("sleep");
        if (granted.contains(HealthConnectBridge.HEART)) result.add("heart"); return result;
    }
    private void sync() {
        if (busy || allowed().isEmpty()) return;
        int request = ++version; busy = true; render(); status.setText(R.string.hc_syncing);
        bridge.sync(new HealthConnectBridge.Callback() {
            @Override public void onResult(Map<String, ? extends Object> data, Set<String> permissions) {
                if (!current(request)) return;
                Map<String,Object> latest = new HashMap<>(data);
                if (!latest.containsKey("syncedAt") && snapshot.containsKey("syncedAt")) latest.put("syncedAt", snapshot.get("syncedAt"));
                granted = new HashSet<>(permissions); snapshot = HealthSummaryPolicy.mask(latest, allowed());
                boolean saved = true;
                try {
                    store.write(snapshot);
                    HealthRecommendationStore recommendations = new HealthRecommendationStore(HealthConnectActivity.this, uid);
                    if (!granted.contains(HealthConnectBridge.SLEEP)) recommendations.removeSleepInputs();
                    else recommendations.observe(snapshot, System.currentTimeMillis(), java.time.ZoneId.systemDefault());
                } catch (java.io.IOException e) { saved = false; }
                busy = false; render();
                boolean partial = false;
                for (String key : Arrays.asList("steps", "sleep", "heart")) partial |= !"available".equals(metric(key).get("state"));
                status.setText(!saved ? R.string.hc_cache_error : partial ? R.string.hc_partial : R.string.hc_synced);
            }
            @Override public void onError() {
                if (!current(request)) return;
                busy = false; render(); status.setText(R.string.hc_error);
            }
        });
    }
    private Map<?,?> metric(String key) { Object raw = snapshot.get(key); return raw instanceof Map ? (Map<?,?>)raw : Collections.emptyMap(); }
    private String permission(String key) { return getString(allowed().contains(key) ? R.string.hc_granted : R.string.hc_not_granted); }
    private String date(Object value) { return value instanceof Number ? DateFormat.getDateTimeInstance().format(new Date(((Number)value).longValue())) : getString(R.string.fl_missing); }
    private String metricText(String key, int label) {
        Map<?,?> item = metric(key); String state = String.valueOf(item.get("state"));
        String value;
        if (!allowed().contains(key)) value = getString(R.string.hc_not_granted);
        else if (!snapshot.containsKey("windowEnd")) value = getString(R.string.hc_not_synced);
        else if ("error".equals(state)) value = getString(R.string.hc_read_error);
        else if (!"available".equals(state) || !(item.get("value") instanceof Number)) value = getString(R.string.hc_missing);
        else {
            long n = ((Number)item.get("value")).longValue();
            value = key.equals("sleep") ? getString(R.string.hc_sleep_value, n / 3600, (n % 3600) / 60)
                    : key.equals("heart") ? getString(R.string.hc_heart_value, n, FoodUi.number(item.get("min")), FoodUi.number(item.get("max"))) : String.valueOf(n);
            value += "\n" + getString(R.string.hc_latest, date(item.get("observedAt")));
            if (HealthSummaryPolicy.stale(item.get("observedAt"), System.currentTimeMillis())) value += "\n" + getString(R.string.hc_stale);
        }
        return getString(label) + ": " + value;
    }
    private void render() {
        connect.setEnabled(!busy); settings.setEnabled(!busy); sync.setEnabled(!busy && !allowed().isEmpty());
        permissionStatus.setText(getString(R.string.hc_permissions, permission("steps"), permission("sleep"), permission("heart")));
        Object synced = snapshot.get("syncedAt");
        lastSync.setText(synced == null ? getString(R.string.hc_never_synced) : getString(R.string.hc_last_sync, date(synced)));
        String window = snapshot.get("windowStart") instanceof Number ? getString(R.string.hc_window,
                date(snapshot.get("windowStart")), date(snapshot.get("windowEnd")), date(snapshot.get("sleepWindowStart"))) : "";
        String warning = synced != null && (HealthSummaryPolicy.stale(synced, System.currentTimeMillis())
                || !LocalDate.now().toString().equals(snapshot.get("day"))
                || !java.time.ZoneId.systemDefault().getId().equals(snapshot.get("zone"))) ? getString(R.string.hc_stale) + "\n" : "";
        summary.setText(warning + window + "\n\n" + metricText("steps", R.string.hc_steps) + "\n\n"
                + metricText("sleep", R.string.hc_sleep) + "\n\n" + metricText("heart", R.string.hc_heart));
    }
    private void openProvider() {
        try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.google.android.apps.healthdata"))); }
        catch (RuntimeException e) {
            try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=com.google.android.apps.healthdata"))); }
            catch (RuntimeException ignored) { status.setText(R.string.hc_error); }
        }
    }
    @Override protected void onResume() { super.onResume(); if (uid != null) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null || !uid.equals(user.getUid())) finish(); else refresh();
    } }
    @Override protected void onStop() { version++; if (bridge != null) bridge.cancelPending(); super.onStop(); }
    @Override protected void onDestroy() { if (bridge != null) bridge.close(); super.onDestroy(); }
}
