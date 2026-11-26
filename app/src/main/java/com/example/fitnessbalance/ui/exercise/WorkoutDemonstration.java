package com.example.fitnessbalance.ui.exercise;

import android.animation.ValueAnimator;
import android.content.res.Resources;
import android.graphics.ImageDecoder;
import android.graphics.drawable.Animatable;
import android.graphics.drawable.AnimatedImageDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import androidx.annotation.RequiresApi;
import com.example.fitnessbalance.R;
import java.io.IOException;

/** Owns preview playback independently of the exercise timer. */
final class WorkoutDemonstration {
  private enum Clip {
    MARCH(R.raw.demo_march, R.drawable.demo_march_still),
    SQUAT(R.raw.demo_chair_squat, R.drawable.demo_chair_squat_still),
    PUSH_UP(R.raw.demo_wall_pushup, R.drawable.demo_wall_pushup_still);

    final int animation, still;

    Clip(int animation, int still) {
      this.animation = animation;
      this.still = still;
    }

    static Clip forExercise(String exerciseId) {
      switch (exerciseId) {
        case "EX001": return MARCH;
        case "EX002": return SQUAT;
        case "EX003": return PUSH_UP;
        default: return null;
      }
    }
  }

  private final View panel;
  private final ImageView image;
  private final Button playback;
  private int itemIndex = -1;
  private int pausedItem;
  private boolean motionAllowed;
  private Animatable animation;

  WorkoutDemonstration(View panel, int restoredPausedItem) {
    this.panel = panel;
    pausedItem = restoredPausedItem;
    image = panel.findViewById(R.id.exDemoImage);
    playback = panel.findViewById(R.id.exDemoPlayback);
    playback.setOnClickListener(v -> {
      pausedItem = pausedItem == itemIndex ? -1 : itemIndex;
      updatePlayback();
    });
  }

  void show(int index, String exerciseId, String name, boolean foreground) {
    Clip clip = Clip.forExercise(exerciseId);
    if (clip == null) {
      hide();
      return;
    }
    boolean allow = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && ValueAnimator.areAnimatorsEnabled();
    if (itemIndex != index || motionAllowed != allow) {
      stopPlayback();
      itemIndex = index;
      motionAllowed = allow;
      animation = null;
      image.setImageResource(clip.still);
      image.setContentDescription(image.getContext().getString(R.string.ex_demo_description, name));
      if (allow) {
        try {
          Drawable drawable = AnimatedClip.decode(image.getResources(), clip.animation);
          if (drawable instanceof Animatable) {
            animation = (Animatable) drawable;
            image.setImageDrawable(drawable);
          }
        } catch (IOException | Resources.NotFoundException unavailable) {
          // The still and written cues are already visible.
        }
      }
      playback.setVisibility(animation == null ? View.GONE : View.VISIBLE);
      panel.setVisibility(View.VISIBLE);
    }
    if (foreground) updatePlayback();
  }

  int pausedItem() {
    return pausedItem;
  }

  void hide() {
    if (itemIndex == -1) return;
    stopPlayback();
    itemIndex = -1;
    animation = null;
    image.setImageDrawable(null);
    panel.setVisibility(View.GONE);
  }

  void stopPlayback() {
    if (animation != null) animation.stop();
  }

  private void updatePlayback() {
    if (animation == null) return;
    boolean paused = pausedItem == itemIndex;
    if (paused) {
      if (animation.isRunning()) animation.stop();
    } else if (!animation.isRunning()) {
      animation.start();
    }
    int label = paused ? R.string.ex_demo_play : R.string.ex_demo_pause;
    if (!playback.getText().equals(playback.getContext().getText(label))) playback.setText(label);
  }

  @RequiresApi(Build.VERSION_CODES.P)
  private static final class AnimatedClip {
    static Drawable decode(Resources resources, int id) throws IOException {
      Drawable drawable = ImageDecoder.decodeDrawable(ImageDecoder.createSource(resources, id));
      if (drawable instanceof AnimatedImageDrawable) {
        ((AnimatedImageDrawable) drawable).setRepeatCount(AnimatedImageDrawable.REPEAT_INFINITE);
      }
      return drawable;
    }
  }
}
