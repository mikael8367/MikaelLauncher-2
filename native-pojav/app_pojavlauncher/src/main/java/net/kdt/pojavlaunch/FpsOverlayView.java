package net.kdt.pojavlaunch;

import android.content.Context;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.MotionEvent;
import android.widget.FrameLayout;
import android.widget.TextView;

import net.kdt.pojavlaunch.prefs.LauncherPreferences;

import java.util.Locale;

/** Displays successful Minecraft buffer swaps and frame pacing from the native renderer. */
public final class FpsOverlayView extends TextView {
    private static final long SAMPLE_WINDOW_MS = 1000L;
    private static final long POLL_INTERVAL_MS = 250L;
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private boolean mRunning;
    private long mWindowStartMs;
    private long mFrames;
    private long mFrameTimeNs;
    private long mWorstFrameTimeNs;
    private float mDragOffsetX;
    private float mDragOffsetY;
    private FrameLayout mParent;

    private final Runnable mPoller = new Runnable() {
        @Override
        public void run() {
            if (!mRunning) return;
            long now = android.os.SystemClock.elapsedRealtime();
            long[] stats = consumeStatsSafe();
            if (stats != null && stats.length >= 3) {
                mFrames += stats[0];
                mFrameTimeNs += stats[1];
                mWorstFrameTimeNs = Math.max(mWorstFrameTimeNs, stats[2]);
            }
            if (mWindowStartMs == 0L) mWindowStartMs = now;
            long elapsed = now - mWindowStartMs;
            if (elapsed >= SAMPLE_WINDOW_MS) {
                double fps = mFrames * 1000.0 / elapsed;
                double averageMs = mFrames > 1 ? (mFrameTimeNs / 1_000_000.0) / (mFrames - 1) : 0.0;
                double worstMs = mWorstFrameTimeNs / 1_000_000.0;
                setText(String.format(Locale.US, "FPS: %.1f\nFrame: %.2f ms | pior: %.1f ms", fps, averageMs, worstMs));
                mFrames = 0L;
                mFrameTimeNs = 0L;
                mWorstFrameTimeNs = 0L;
                mWindowStartMs = now;
            }
            mHandler.postDelayed(this, POLL_INTERVAL_MS);
        }
    };

    public FpsOverlayView(Context context) {
        super(context);
        setTextColor(Color.WHITE);
        setTextSize(14f);
        setGravity(Gravity.CENTER);
        setPadding(12, 6, 12, 6);
        setBackgroundColor(0xB8000000);
        setText("FPS: --\nFrame: --");
        setVisibility(GONE);
        setElevation(12f);
    }

    public void attachTo(FrameLayout parent) {
        mParent = parent;
        if (getParent() == null) {
            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    Gravity.TOP | Gravity.START);
            params.leftMargin = 16;
            params.topMargin = 16;
            parent.addView(this, params);
            post(() -> {
                setX(LauncherPreferences.DEFAULT_PREF.getFloat("fps_overlay_x", 16f));
                setY(LauncherPreferences.DEFAULT_PREF.getFloat("fps_overlay_y", 16f));
            });
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (mParent == null) return super.onTouchEvent(event);
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                mDragOffsetX = event.getRawX() - getX();
                mDragOffsetY = event.getRawY() - getY();
                return true;
            case MotionEvent.ACTION_MOVE:
                float maxX = Math.max(0f, mParent.getWidth() - getWidth());
                float maxY = Math.max(0f, mParent.getHeight() - getHeight());
                setX(Math.max(0f, Math.min(maxX, event.getRawX() - mDragOffsetX)));
                setY(Math.max(0f, Math.min(maxY, event.getRawY() - mDragOffsetY)));
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                LauncherPreferences.DEFAULT_PREF.edit()
                        .putFloat("fps_overlay_x", getX())
                        .putFloat("fps_overlay_y", getY())
                        .apply();
                return true;
            default:
                return true;
        }
    }

    public void setEnabled(boolean enabled) {
        if (enabled == mRunning) return;
        mRunning = enabled;
        setVisibility(enabled ? VISIBLE : GONE);
        if (enabled) {
            consumeStatsSafe();
            mWindowStartMs = android.os.SystemClock.elapsedRealtime();
            mFrames = 0L;
            mFrameTimeNs = 0L;
            mWorstFrameTimeNs = 0L;
            setText("FPS: --\nFrame: --");
            mHandler.post(mPoller);
        } else {
            mHandler.removeCallbacks(mPoller);
            consumeStatsSafe();
        }
    }

    private static long[] consumeStatsSafe() {
        try {
            return nativeConsumeFrameStats();
        } catch (UnsatisfiedLinkError ignored) {
            return new long[]{0L, 0L, 0L};
        }
    }

    public static long[] consumeNativeFrameStatsForDiagnostics() {
        return consumeStatsSafe();
    }

    private static native long nativeConsumeFrameCount();
    private static native long[] nativeConsumeFrameStats();
}
