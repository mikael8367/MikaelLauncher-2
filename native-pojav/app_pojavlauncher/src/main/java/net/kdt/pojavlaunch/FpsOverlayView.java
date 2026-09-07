package net.kdt.pojavlaunch;

import android.content.Context;
import android.graphics.Color;
import android.view.Choreographer;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.TextView;

import java.util.Locale;

/** Displays the measured presentation rate of the game surface. */
public final class FpsOverlayView extends TextView implements Choreographer.FrameCallback {
    private static final long SAMPLE_WINDOW_NS = 500_000_000L;
    private boolean mRunning;
    private long mWindowStartNs;
    private int mFrames;

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
            mWindowStartNs = 0L;
            mFrames = 0;
            setText("FPS: --");
            Choreographer.getInstance().postFrameCallback(this);
        } else {
            Choreographer.getInstance().removeFrameCallback(this);
        }
    }

    @Override
    public void doFrame(long frameTimeNanos) {
        if (!mRunning) return;
        if (mWindowStartNs == 0L) mWindowStartNs = frameTimeNanos;
        mFrames++;
        long elapsed = frameTimeNanos - mWindowStartNs;
        if (elapsed >= SAMPLE_WINDOW_NS) {
            float fps = mFrames * 1_000_000_000f / elapsed;
            setText(String.format(Locale.US, "FPS: %.1f", fps));
            mFrames = 0;
            mWindowStartNs = frameTimeNanos;
        }
        Choreographer.getInstance().postFrameCallback(this);
    }
}

