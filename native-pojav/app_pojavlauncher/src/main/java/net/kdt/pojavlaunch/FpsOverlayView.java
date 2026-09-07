package net.kdt.pojavlaunch;

import android.content.Context;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.TextView;

import java.util.Locale;

/** Displays the number of successful Minecraft buffer swaps measured by the native renderer. */
public final class FpsOverlayView extends TextView {
    private static final long SAMPLE_WINDOW_MS = 1000L;
    private static final long POLL_INTERVAL_MS = 100L;
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private boolean mRunning;
    private long mWindowStartMs;
    private long mFrames;

    private final Runnable mPoller = new Runnable() {
        @Override
        public void run() {
            if (!mRunning) return;
            long now = android.os.SystemClock.elapsedRealtime();
            mFrames += nativeConsumeFrameCount();
            if (mWindowStartMs == 0L) mWindowStartMs = now;
            long elapsed = now - mWindowStartMs;
            if (elapsed >= SAMPLE_WINDOW_MS) {
                double fps = mFrames * 1000.0 / elapsed;
                setText(String.format(Locale.US, "FPS: %.1f", fps));
                mFrames = 0L;
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
        setText("FPS: --");
        setVisibility(GONE);
        setElevation(12f);
    }

    public void attachTo(FrameLayout parent) {
        if (getParent() == null) {
            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    Gravity.TOP | Gravity.START);
            params.leftMargin = 16;
            params.topMargin = 16;
            parent.addView(this, params);
        }
    }

    public void setEnabled(boolean enabled) {
        if (enabled == mRunning) return;
        mRunning = enabled;
        setVisibility(enabled ? VISIBLE : GONE);
        if (enabled) {
            nativeConsumeFrameCount();
            mWindowStartMs = android.os.SystemClock.elapsedRealtime();
            mFrames = 0L;
            setText("FPS: --");
            mHandler.post(mPoller);
        } else {
            mHandler.removeCallbacks(mPoller);
            nativeConsumeFrameCount();
        }
    }

    private static native long nativeConsumeFrameCount();
}
