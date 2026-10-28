package com.example.fitnessbalance.util;

import android.content.*;
import android.os.Handler;
import android.os.Looper;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;
import java.util.*;

/** Watches only while resumed; no midnight background job or destructive data reset. */
public final class LocalDayObserver implements DefaultLifecycleObserver {
  private final Context context;
  private final LocalDayState state;
  private final Runnable changed;
  private final Handler handler = new Handler(Looper.getMainLooper());
  private boolean watching;
  private final Runnable tick = this::check;
  private final BroadcastReceiver receiver =
      new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent intent) {
          check();
        }
      };

  public LocalDayObserver(
      Context context, LifecycleOwner owner, String observed, Runnable changed) {
    this.context = context;
    this.state = new LocalDayState(observed == null ? today() : observed);
    this.changed = changed;
    owner.getLifecycle().addObserver(this);
  }

  public static String today() {
    return DateUtils.day(new Date(), TimeZone.getDefault());
  }

  public String day() {
    return state.day();
  }

  @Override
  public void onResume(LifecycleOwner owner) {
    watching = true;
    IntentFilter filter = new IntentFilter();
    filter.addAction(Intent.ACTION_DATE_CHANGED);
    filter.addAction(Intent.ACTION_TIME_CHANGED);
    filter.addAction(Intent.ACTION_TIMEZONE_CHANGED);
    ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_EXPORTED);
    check();
  }

  @Override
  public void onPause(LifecycleOwner owner) {
    watching = false;
    handler.removeCallbacks(tick);
    context.unregisterReceiver(receiver);
  }

  private void check() {
    if (!watching) return;
    handler.removeCallbacks(tick);
    if (state.update(today())) changed.run();
    handler.postDelayed(tick, DateUtils.untilNextDay(new Date(), TimeZone.getDefault()));
  }
}
