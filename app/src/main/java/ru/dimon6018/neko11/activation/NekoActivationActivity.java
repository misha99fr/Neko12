package ru.dimon6018.neko11.activation;

import static ru.dimon6018.neko11.ui.activities.NekoSettingsActivity.STATEPREF;
import ru.dimon6018.neko11.workers.NekoWorker;

import android.animation.ObjectAnimator;
import android.annotation.SuppressLint;
import android.app.ActionBar;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.AttributeSet;
import android.util.Log;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.PathInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.core.view.WindowCompat;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.content.res.AppCompatResources;

import org.json.JSONObject;

import ru.dimon6018.neko11.ui.oobe.NekoOOBE;
import ru.dimon6018.neko11.workers.NekoWorker;
import ru.dimon6018.neko11.R;

public class NekoActivationActivity extends Activity {
	private static final String TAG = "NekoActivation";
    private static final String R_EGG_UNLOCK_SETTING = "egg_mode_r";
    private static final int UNLOCK_TRIES = 3;
    BigDialView mDialView;

    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        final float dp = getResources().getDisplayMetrics().density;
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        getWindow().setNavigationBarColor(0);
        getWindow().setStatusBarColor(0);
        final ActionBar ab = getActionBar();
        if (ab != null) ab.hide();
        mDialView = new BigDialView(this, null);
        if (Settings.System.getLong(getContentResolver(),
                R_EGG_UNLOCK_SETTING, 0) == 0) {
            mDialView.setUnlockTries(UNLOCK_TRIES);
        } else {
            mDialView.setUnlockTries(0);
        }
        final FrameLayout layout = new FrameLayout(this);
        layout.setBackgroundColor(0xFFFF0000);
        layout.addView(mDialView, FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT);
        setContentView(layout);
    }
    private void toastUp() {
        Toast toast = Toast.makeText(this, "\uD83D\uDC31", Toast.LENGTH_SHORT);
        toast.show();
    }
    private void launchNextStage() {
        SharedPreferences state = getSharedPreferences(STATEPREF, Context.MODE_PRIVATE);
        try {
            Log.i(TAG,"Deactivate egg and start NekoLand");
            toastUp();
            NekoWorker.setupNotificationChannels(this);
            SharedPreferences.Editor editor = state.edit();
            editor.putInt("state", 3);
            editor.apply();
            startActivity(new Intent(this, NekoOOBE.class)
            .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                            | Intent.FLAG_ACTIVITY_CLEAR_TASK));;
        } catch (ActivityNotFoundException ex) {
            Log.e("PlatLogoActivity", "No more eggs.");
        }
    }
    static final String TOUCH_STATS = "touch.stats";
    double mPressureMin = 0, mPressureMax = -1;

    private void syncTouchPressure() {
        try {
            final String touchDataJson = Settings.System.getString(
                    getContentResolver(), TOUCH_STATS);
            final JSONObject touchData = new JSONObject(
                    touchDataJson != null ? touchDataJson : "{}");
            if (touchData.has("min")) {
                mPressureMin = Math.min(mPressureMin, touchData.getDouble("min"));
            }
            if (touchData.has("max")) {
                mPressureMax = Math.max(mPressureMax, touchData.getDouble("max"));
            }
            if (mPressureMax >= 0) {
                touchData.put("min", mPressureMin);
                touchData.put("max", mPressureMax);
            }
        } catch (Exception e) {
            Log.e("PlatLogoActivity", "Can't write touch settings", e);
        }
    }
    @Override
    public void onStart() {
        super.onStart();
        syncTouchPressure();
    }
    @Override
    public void onStop() {
        syncTouchPressure();
        super.onStop();
    }
    @SuppressLint("AppCompatCustomView")
    class BigDialView extends ImageView {
        private static final int COLOR_GREEN = 0xff3ddc84;
        private static final int COLOR_NAVY = 0xff073042;
        private static final int COLOR_ORANGE = 0xfff86734;
        private static final int COLOR_LIGHTBLUE = 0xffd7effe;
        private static final int STEPS = 11;
        private static final float VALUE_CHANGE_MAX = 1f / STEPS;
        private BigDialDrawable mDialDrawable;
        private boolean mWasLocked;
        BigDialView(Context context, @Nullable AttributeSet attrs) {
            super(context, attrs);
            init();
        }

        private void init() {
            mDialDrawable = new BigDialDrawable();
            setImageDrawable(mDialDrawable);
        }
        @Override
        public void onDraw(Canvas c) {
            super.onDraw(c);
        }
        double toPositiveDegrees(double rad) {
            return (Math.toDegrees(rad) + 360 - 90) % 360;
        }
        @Override
        public boolean onTouchEvent(MotionEvent ev) {
            switch (ev.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    mWasLocked = mDialDrawable.isLocked();
                    // pass through
                case MotionEvent.ACTION_MOVE:
                    float x = ev.getX();
                    float y = ev.getY();
                    float cx = (getLeft() + getRight()) / 2f;
                    float cy = (getTop() + getBottom()) / 2f;
                    float angle = (float) toPositiveDegrees(Math.atan2(x - cx, y - cy));
                    final int oldLevel = mDialDrawable.getUserLevel();
                    mDialDrawable.touchAngle(angle);
                    final int newLevel = mDialDrawable.getUserLevel();
                    if (oldLevel != newLevel) {
                        performHapticFeedback(newLevel == STEPS
                                ? HapticFeedbackConstants.CONFIRM
                                : HapticFeedbackConstants.CLOCK_TICK);
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                    if (mWasLocked != mDialDrawable.isLocked()) {
                        launchNextStage();
                    }
                    return true;
            }
            return false;
        }
        @Override
        public boolean performClick() {
            if (mDialDrawable.getUserLevel() < STEPS - 1) {
                mDialDrawable.setUserLevel(mDialDrawable.getUserLevel() + 1);
                performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
            }
            return true;
        }
        void setUnlockTries(int tries) {
            mDialDrawable.setUnlockTries(tries);
        }
        private class BigDialDrawable extends Drawable {
            public final int STEPS = 10;
            private int mUnlockTries = 0;
            final Paint mPaint = new Paint();
            final Drawable mEleven;
            private final boolean mNightMode;
            private float mValue = 0f;
            float mElevenAnim = 0f;
            ObjectAnimator mElevenShowAnimator = ObjectAnimator.ofFloat(this, "elevenAnim", 0f,
                    1f).setDuration(300);
            ObjectAnimator mElevenHideAnimator = ObjectAnimator.ofFloat(this, "elevenAnim", 1f,
                    0f).setDuration(500);
            BigDialDrawable() {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    mNightMode = getContext().getResources().getConfiguration().isNightModeActive();
                } else {
                    mNightMode = false;
                }
                mEleven = AppCompatResources.getDrawable(getContext(), R.drawable.ic_11);
                mElevenShowAnimator.setInterpolator(new PathInterpolator(0.4f, 0f, 0.2f, 1f));
                mElevenHideAnimator.setInterpolator(new PathInterpolator(0.8f, 0.2f, 0.6f, 1f));
            }
            public void setUnlockTries(int count) {
                if (mUnlockTries != count) {
                    mUnlockTries = count;
                    setValue(getValue());
                    invalidateSelf();
                }
            }
            boolean isLocked() {
                return mUnlockTries > 0;
            }
            public void setValue(float v) {
                // until the dial is "unlocked", you can't turn it all the way to 11
                final float max = isLocked() ? 1f - 1f / STEPS : 1f;
                mValue = v < 0f ? 0f : v > max ? max : v;
                invalidateSelf();
            }
            public float getValue() {
                return mValue;
            }
            public int getUserLevel() {
                return Math.round(getValue() * STEPS - 0.25f);
            }
            public void setUserLevel(int i) {
                setValue(getValue() + ((float) i) / STEPS);
            }

            public void setElevenAnim(float f) {
                if (mElevenAnim != f) {
                    mElevenAnim = f;
                    invalidateSelf();
                }
            }
            @Override
            public void draw(@NonNull Canvas canvas) {
                final Rect bounds = getBounds();
                final int w = bounds.width();
                final int h = bounds.height();
                final float w2 = w / 2f;
                final float h2 = h / 2f;
                final float radius = w / 4f;
                canvas.drawColor(mNightMode ? COLOR_NAVY : COLOR_LIGHTBLUE);
                canvas.save();
                canvas.rotate(45, w2, h2);
                canvas.clipRect(w2, h2 - radius, Math.min(w, h), h2 + radius);
                final int gradientColor = mNightMode ? 0x60000020 : (0x10FFFFFF & COLOR_NAVY);
                mPaint.setShader(
                        new LinearGradient(w2, h2, Math.min(w, h), h2, gradientColor,
                                0x00FFFFFF & gradientColor, Shader.TileMode.CLAMP));
                mPaint.setColor(Color.BLACK);
                canvas.drawPaint(mPaint);
                mPaint.setShader(null);
                canvas.restore();
                mPaint.setStyle(Paint.Style.FILL);
                mPaint.setColor(COLOR_GREEN);
                canvas.drawCircle(w2, h2, radius, mPaint);
                mPaint.setColor(mNightMode ? COLOR_LIGHTBLUE : COLOR_NAVY);
                final float cx = w * 0.85f;
                for (int i = 0; i < STEPS; i++) {
                    final float f = (float) i / STEPS;
                    canvas.save();
                    final float angle = valueToAngle(f);
                    canvas.rotate(-angle, w2, h2);
                    canvas.drawCircle(cx, h2, (i <= getUserLevel()) ? 20 : 5, mPaint);
                    canvas.restore();
                }
                if (mElevenAnim > 0f) {
                    final int color = COLOR_ORANGE;
                    final int size2 = (int) ((0.5 + 0.5f * mElevenAnim) * w / 14);
                    final float cx11 = cx + size2 / 4f;
                    mEleven.setBounds((int) cx11 - size2, (int) h2 - size2,
                            (int) cx11 + size2, (int) h2 + size2);
                    final int alpha = 0xFFFFFF | ((int) clamp(0xFF * 2 * mElevenAnim, 0, 0xFF)
                            << 24);
                    mEleven.setTint(alpha & color);
                    mEleven.draw(canvas);
                }
                // don't want to use the rounded value here since the quantization will be visible
                final float angle = valueToAngle(mValue);
                // it's easier to draw at far-right and rotate backwards
                canvas.rotate(-angle, w2, h2);
                mPaint.setColor(Color.WHITE);
                final float dimple = w2 / 12f;
                canvas.drawCircle(w - radius - dimple * 2, h2, dimple, mPaint);
            }
            float clamp(float x, float a, float b) {
                return x < a ? a : x > b ? b : x;
            }
            float angleToValue(float a) {
                return 1f - clamp(a / (360 - 45), 0f, 1f);
            }
            // rotation: min is at 4:30, max is at 3:00
            float valueToAngle(float v) {
                return (1f - v) * (360 - 45);
            }
            public void touchAngle(float a) {
                final int oldUserLevel = getUserLevel();
                final float newValue = angleToValue(a);
                // this is how we prevent the knob from snapping from max back to min, or from
                // jumping around wherever the user presses. The new value must be pretty close
                // to the
                // previous one.
                if (Math.abs(newValue - getValue()) < VALUE_CHANGE_MAX) {
                    setValue(newValue);
                    if (isLocked() && oldUserLevel != STEPS - 1 && getUserLevel() == STEPS - 1) {
                        mUnlockTries--;
                    } else if (!isLocked() && getUserLevel() == 0) {
                        mUnlockTries = UNLOCK_TRIES;
                    }
                    if (!isLocked()) {
                        if (getUserLevel() == STEPS && mElevenAnim != 1f
                                && !mElevenShowAnimator.isRunning()) {
                            mElevenHideAnimator.cancel();
                            mElevenShowAnimator.start();
                        } else if (getUserLevel() != STEPS && mElevenAnim == 1f
                                && !mElevenHideAnimator.isRunning()) {
                            mElevenShowAnimator.cancel();
                            mElevenHideAnimator.start();
                        }
                    }
                }
            }
            @Override
            public void setAlpha(int i) {
            }
            @Override
            public void setColorFilter(@Nullable ColorFilter colorFilter) {
            }
            @Override
            public int getOpacity() {
                return PixelFormat.TRANSLUCENT;
            }
        }
    }
}