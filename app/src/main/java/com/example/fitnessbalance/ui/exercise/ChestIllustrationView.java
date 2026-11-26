package com.example.fitnessbalance.ui.exercise;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

/** Draws the three chest states on a fixed coordinate grid that scales with the view. */
final class ChestIllustrationView extends View {
  enum State { LOCKED, READY, OPENED }

  private static final int OUTLINE = 0xff302340;
  private static final int GOLD = 0xffffd56a;
  private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final RectF bounds = new RectF();
  private final Path sparkle = new Path();
  private State state = State.LOCKED;
  private int accent;
  private float opening;
  private ValueAnimator animator;

  ChestIllustrationView(Context context) {
    super(context);
    setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
  }

  void show(State next, int accent, boolean animate) {
    stopAnimation();
    state = next;
    this.accent = accent;
    opening = next == State.OPENED && !animate ? 1 : 0;
    if (next == State.OPENED && animate) {
      animator = ValueAnimator.ofFloat(0f, 1f);
      animator.setDuration(520);
      animator.setInterpolator(new DecelerateInterpolator());
      animator.addUpdateListener(value -> {
        opening = (float) value.getAnimatedValue();
        invalidate();
      });
      animator.start();
    }
    invalidate();
  }

  void stopAnimation() {
    if (animator != null) {
      animator.cancel();
      animator = null;
    }
    if (state == State.OPENED) {
      opening = 1;
      invalidate();
    }
  }

  @Override protected void onDetachedFromWindow() {
    stopAnimation();
    super.onDetachedFromWindow();
  }

  @Override protected void onDraw(Canvas canvas) {
    super.onDraw(canvas);
    float scale = Math.min(getWidth() / 224f, getHeight() / 144f);
    canvas.save();
    canvas.translate((getWidth() - 224 * scale) / 2f, (getHeight() - 144 * scale) / 2f);
    canvas.scale(scale, scale);

    boolean locked = state == State.LOCKED;
    int band = locked ? 0xffaea2bc : GOLD;
    int lid = locked ? 0xff8c819c : 0xff9362c3;
    int front = locked ? 0xff756a85 : 0xff654197;
    color(locked ? 0xffe9e3ef : 0xfff2e3fb);
    canvas.drawCircle(112, 72, 64, paint);
    color(0x24302340);
    canvas.drawOval(45, 117, 179, 130, paint);

    if (!locked) {
      star(canvas, 29, 66, 7, GOLD);
      star(canvas, 188, 45, 9, GOLD);
      if (opening > 0) {
        star(canvas, 156, 33, 9 * opening, accent);
        star(canvas, 70, 32, 6 * opening, accent);
      }
    }

    rounded(canvas, 48, 61, 176, 113, 12, OUTLINE);
    rounded(canvas, 52, 65, 172, 93, 9, 0xff473359);
    rounded(canvas, 56, 69, 168, 95, 7, opening > 0 ? accent : front);
    rounded(canvas, 48, 80, 176, 120, 9, OUTLINE);
    rounded(canvas, 52, 83, 172, 116, 6, front);
    rounded(canvas, 59, 94, 165, 98, 2, 0x5fffffff);

    canvas.save();
    canvas.translate(0, -9 * opening);
    canvas.rotate(-12 * opening, 50, 78);
    rounded(canvas, 46, 46, 178, 87, 10, OUTLINE);
    rounded(canvas, 50, 50, 174, 83, 8, lid);
    rounded(canvas, 57, 55, 169, 60, 3, 0x65ffffff);
    rounded(canvas, 62, 50, 75, 83, 3, band);
    rounded(canvas, 149, 50, 162, 83, 3, band);
    if (!locked) star(canvas, 112, 65, 8, band);
    canvas.restore();

    rounded(canvas, 62, 87, 75, 116, 3, band);
    rounded(canvas, 149, 87, 162, 116, 3, band);
    rounded(canvas, 98, 83, 126, 107, 5, OUTLINE);
    rounded(canvas, 101, 85, 123, 104, 4, band);
    if (opening > 0) {
      star(canvas, 112, 94, 7, OUTLINE);
    } else {
      color(OUTLINE);
      canvas.drawCircle(112, 92, 3, paint);
      canvas.drawRect(111, 93, 113, 99, paint);
    }
    canvas.restore();
  }

  private void color(int color) {
    paint.setColor(color);
  }

  private void rounded(Canvas canvas, float l, float t, float r, float b, float radius, int color) {
    bounds.set(l, t, r, b);
    color(color);
    canvas.drawRoundRect(bounds, radius, radius, paint);
  }

  private void star(Canvas canvas, float x, float y, float radius, int color) {
    float notch = radius * .28f;
    sparkle.reset();
    sparkle.moveTo(x, y - radius);
    sparkle.lineTo(x + notch, y - notch);
    sparkle.lineTo(x + radius, y);
    sparkle.lineTo(x + notch, y + notch);
    sparkle.lineTo(x, y + radius);
    sparkle.lineTo(x - notch, y + notch);
    sparkle.lineTo(x - radius, y);
    sparkle.lineTo(x - notch, y - notch);
    sparkle.close();
    color(color);
    canvas.drawPath(sparkle, paint);
  }
}
