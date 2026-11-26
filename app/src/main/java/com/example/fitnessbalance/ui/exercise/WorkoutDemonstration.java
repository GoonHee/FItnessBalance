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
import java.util.HashMap;
import java.util.Map;

/** Owns preview playback independently of the exercise timer. */
final class WorkoutDemonstration {
  private enum Clip {
    EX001(R.raw.demo_march, R.drawable.demo_march_still),
    EX002(R.raw.demo_chair_squat, R.drawable.demo_chair_squat_still),
    EX003(R.raw.demo_wall_pushup, R.drawable.demo_wall_pushup_still),
    EX004(R.raw.demo_ex004, R.drawable.demo_ex004_still),
    EX005(R.raw.demo_ex005, R.drawable.demo_ex005_still),
    EX006(R.raw.demo_ex006, R.drawable.demo_ex006_still),
    EX007(R.raw.demo_ex007, R.drawable.demo_ex007_still),
    EX008(R.raw.demo_ex008, R.drawable.demo_ex008_still),
    EX009(R.raw.demo_ex009, R.drawable.demo_ex009_still),
    EX010(R.raw.demo_ex010, R.drawable.demo_ex010_still),
    EX011(R.raw.demo_ex011, R.drawable.demo_ex011_still),
    EX012(R.raw.demo_ex012, R.drawable.demo_ex012_still),
    EX013(R.raw.demo_ex013, R.drawable.demo_ex013_still),
    EX014(R.raw.demo_ex014, R.drawable.demo_ex014_still),
    EX015(R.raw.demo_ex015, R.drawable.demo_ex015_still),
    EX016(R.raw.demo_ex016, R.drawable.demo_ex016_still),
    EX017(R.raw.demo_ex017, R.drawable.demo_ex017_still),
    EX018(R.raw.demo_ex018, R.drawable.demo_ex018_still),
    EX019(R.raw.demo_ex019, R.drawable.demo_ex019_still),
    EX020(R.raw.demo_ex020, R.drawable.demo_ex020_still),
    EX021(R.raw.demo_ex021, R.drawable.demo_ex021_still),
    EX022(R.raw.demo_ex022, R.drawable.demo_ex022_still),
    EX023(R.raw.demo_ex023, R.drawable.demo_ex023_still),
    EX024(R.raw.demo_ex024, R.drawable.demo_ex024_still),
    EX025(R.raw.demo_ex025, R.drawable.demo_ex025_still),
    EX026(R.raw.demo_ex026, R.drawable.demo_ex026_still),
    EX027(R.raw.demo_ex027, R.drawable.demo_ex027_still),
    EX028(R.raw.demo_ex028, R.drawable.demo_ex028_still),
    EX029(R.raw.demo_ex029, R.drawable.demo_ex029_still),
    EX030(R.raw.demo_ex030, R.drawable.demo_ex030_still);

    final int animation, still;

    Clip(int animation, int still) {
      this.animation = animation;
      this.still = still;
    }

    private static final Map<String, Clip> BY_EXERCISE = new HashMap<>();

    static {
      for (Clip clip : values()) BY_EXERCISE.put(clip.name(), clip);
    }

    static Clip forExercise(String exerciseId) {
      return BY_EXERCISE.get(exerciseId);
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
