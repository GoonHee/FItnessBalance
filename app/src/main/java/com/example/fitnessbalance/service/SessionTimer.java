package com.example.fitnessbalance.service;

/** Countdown arithmetic uses monotonic elapsed time and never becomes negative. */
public final class SessionTimer {
  public long remaining(long remaining, long delta, boolean paused) {
    return paused ? Math.max(0, remaining) : Math.max(0, remaining - Math.max(0, delta));
  }

  public boolean restFinished(boolean rest, long remaining, boolean paused) {
    return rest && !paused && remaining <= 0;
  }
}
