package net.kdt.pojavlaunch.prefs;

import static net.kdt.pojavlaunch.prefs.LauncherPreferences.PREF_DISABLE_GESTURES;
import static net.kdt.pojavlaunch.prefs.LauncherPreferences.PREF_ENABLE_GYRO;
import static net.kdt.pojavlaunch.prefs.LauncherPreferences.PREF_GYRO_INVERT_X;
import static net.kdt.pojavlaunch.prefs.LauncherPreferences.PREF_GYRO_INVERT_Y;
import static net.kdt.pojavlaunch.prefs.LauncherPreferences.PREF_GYRO_SENSITIVITY;
import static net.kdt.pojavlaunch.prefs.LauncherPreferences.PREF_LONGPRESS_TRIGGER;
import static net.kdt.pojavlaunch.prefs.LauncherPreferences.PREF_MOUSESPEED;
import static net.kdt.pojavlaunch.prefs.LauncherPreferences.PREF_SCALE_FACTOR;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Switch;
import android.widget.TextView;

import com.kdt.CustomSeekbar;

import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.MinecraftGLSurface;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.utils.interfaces.SimpleSeekBarListener;
import net.kdt.pojavlaunch.utils.MCOptionUtils;
import static net.kdt.pojavlaunch.PojavApplication.sExecutorService;

/**
 * Side dialog for quick settings that you can change in game
 * The implementation has to take action on some preference changes
 */
public abstract class QuickSettingSideDialog extends com.kdt.SideDialogView {

    private SharedPreferences.Editor mEditor;
    @SuppressLint("UseSwitchCompatOrMaterialCode")
    private Switch mGyroSwitch, mGyroXSwitch, mGyroYSwitch, mGestureSwitch, mUncappedFpsSwitch, mShowFpsSwitch, mMaxFpsSwitch;
    private CustomSeekbar mGyroSensitivityBar, mMouseSpeedBar, mGestureDelayBar, mResolutionBar;
    private TextView mGyroSensitivityText, mGyroSensitivityDisplayText, mMouseSpeedText, mGestureDelayText, mGestureDelayDisplayText, mResolutionText;

    private boolean mOriginalGyroEnabled, mOriginalGyroXEnabled, mOriginalGyroYEnabled, mOriginalGestureDisabled;
    private float mOriginalGyroSensitivity, mOriginalMouseSpeed, mOriginalResolution;
    private int mOriginalGestureDelay;
    private boolean mOriginalMaxFps;

    public QuickSettingSideDialog(Context context, ViewGroup parent) {
        super(context, parent, R.layout.dialog_quick_setting);
        setTitle(R.string.quick_setting_title);
        setupCancelButton();
    }

    @Override
    protected void onInflate() {
        bindLayout();
        Tools.runOnUiThread(() -> {
            this.setupListeners();
            this.updateGyroCompatibility();
        });
    }

    @Override
    protected void onDestroy() {
        removeListeners();
    }

    private void bindLayout() {
        // Bind layout elements
        mGyroSwitch = mDialogContent.findViewById(R.id.checkboxGyro);
        mGyroXSwitch = mDialogContent.findViewById(R.id.checkboxGyroX);
        mGyroYSwitch = mDialogContent.findViewById(R.id.checkboxGyroY);
        mGestureSwitch = mDialogContent.findViewById(R.id.checkboxGesture);
        mUncappedFpsSwitch = mDialogContent.findViewById(R.id.checkboxUncappedFps);
        mShowFpsSwitch = mDialogContent.findViewById(R.id.checkboxShowFps);
        mMaxFpsSwitch = mDialogContent.findViewById(R.id.checkboxMaxFps);

        mGyroSensitivityBar = mDialogContent.findViewById(R.id.editGyro_seekbar);
        mMouseSpeedBar = mDialogContent.findViewById(R.id.editMouseSpeed_seekbar);
        mGestureDelayBar = mDialogContent.findViewById(R.id.editGestureDelay_seekbar);
        mResolutionBar = mDialogContent.findViewById(R.id.editResolution_seekbar);

        mGyroSensitivityText = mDialogContent.findViewById(R.id.editGyro_textView_percent);
        mGyroSensitivityDisplayText = mDialogContent.findViewById(R.id.editGyro_textView);
        mMouseSpeedText = mDialogContent.findViewById(R.id.editMouseSpeed_textView_percent);
        mGestureDelayText = mDialogContent.findViewById(R.id.editGestureDelay_textView_percent);
        mGestureDelayDisplayText = mDialogContent.findViewById(R.id.editGestureDelay_textView);
        mResolutionText = mDialogContent.findViewById(R.id.editResolution_textView_percent);
    }

    private void setupListeners() {
        mEditor = LauncherPreferences.DEFAULT_PREF.edit();

        mOriginalGyroEnabled = PREF_ENABLE_GYRO;
        mOriginalGyroXEnabled = PREF_GYRO_INVERT_X;
        mOriginalGyroYEnabled = PREF_GYRO_INVERT_Y;
        mOriginalGestureDisabled = PREF_DISABLE_GESTURES;

        mOriginalGyroSensitivity = PREF_GYRO_SENSITIVITY;
        mOriginalMouseSpeed = PREF_MOUSESPEED;
        mOriginalGestureDelay = PREF_LONGPRESS_TRIGGER;
        mOriginalResolution = PREF_SCALE_FACTOR;

        mGyroSwitch.setChecked(mOriginalGyroEnabled);
        mGyroXSwitch.setChecked(mOriginalGyroXEnabled);
        mGyroYSwitch.setChecked(mOriginalGyroYEnabled);
        mGestureSwitch.setChecked(mOriginalGestureDisabled);
        mUncappedFpsSwitch.setChecked(LauncherPreferences.DEFAULT_PREF.getBoolean("uncapped_fps", false));
        mShowFpsSwitch.setChecked(LauncherPreferences.DEFAULT_PREF.getBoolean("show_fps_overlay", false));
        mOriginalMaxFps = LauncherPreferences.DEFAULT_PREF.getBoolean("max_fps_mode", false);
        mMaxFpsSwitch.setChecked(mOriginalMaxFps);

        mGyroSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            PREF_ENABLE_GYRO = isChecked;
            onGyroStateChanged();
            updateGyroVisibility(isChecked);
            mEditor.putBoolean("enableGyro", isChecked);
        });

        mGyroXSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            PREF_GYRO_INVERT_X = isChecked;
            onGyroStateChanged();
            mEditor.putBoolean("gyroInvertX", isChecked);
        });

        mGyroYSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            PREF_GYRO_INVERT_Y = isChecked;
            onGyroStateChanged();
            mEditor.putBoolean("gyroInvertY", isChecked);
        });

        mGestureSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            PREF_DISABLE_GESTURES = isChecked;
            updateGestureVisibility(isChecked);
            mEditor.putBoolean("disableGestures", isChecked);
        });

        mUncappedFpsSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            mEditor.putBoolean("uncapped_fps", isChecked);
            if (isChecked) mEditor.putBoolean("force_vsync", false);
        });

        mShowFpsSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            mEditor.putBoolean("show_fps_overlay", isChecked);
            MinecraftGLSurface.setFpsOverlayEnabled(isChecked);
        });

        mMaxFpsSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            LauncherPreferences.DEFAULT_PREF.edit().putBoolean("max_fps_mode", isChecked).apply();
            sExecutorService.execute(() -> applyMaxFpsMode(isChecked));
        });

        mGyroSensitivityBar.setOnSeekBarChangeListener((SimpleSeekBarListener) (seekBar, progress, fromUser) -> {
            PREF_GYRO_SENSITIVITY = progress / 100f;
            mEditor.putInt("gyroSensitivity", progress);
            setSeekTextPercent(mGyroSensitivityText, progress);
        });
        mGyroSensitivityBar.setProgress((int) (mOriginalGyroSensitivity * 100f));
        setSeekTextPercent(mGyroSensitivityText, mGyroSensitivityBar.getProgress());

        mMouseSpeedBar.setOnSeekBarChangeListener((SimpleSeekBarListener) (seekBar, progress, fromUser) -> {
            PREF_MOUSESPEED = progress / 100f;
            mEditor.putInt("mousespeed", progress);
            setSeekTextPercent(mMouseSpeedText, progress);
        });
        mMouseSpeedBar.setProgress((int) (mOriginalMouseSpeed * 100f));
        setSeekTextPercent(mMouseSpeedText, mMouseSpeedBar.getProgress());

        mGestureDelayBar.setOnSeekBarChangeListener((SimpleSeekBarListener) (seekBar, progress, fromUser) -> {
            PREF_LONGPRESS_TRIGGER = progress;
            mEditor.putInt("timeLongPressTrigger", progress);
            setSeekTextMillisecond(mGestureDelayText, progress);
        });
        mGestureDelayBar.setProgress(mOriginalGestureDelay);
        setSeekTextMillisecond(mGestureDelayText, mGestureDelayBar.getProgress());

        mResolutionBar.setOnSeekBarChangeListener((SimpleSeekBarListener) (seekBar, progress, fromUser) -> {
            PREF_SCALE_FACTOR = progress/100f;
            mEditor.putInt("resolutionRatio", progress);
            setSeekTextPercent(mResolutionText, progress);
            onResolutionChanged();
        });
        mResolutionBar.setProgress((int) (mOriginalResolution * 100));
        setSeekTextPercent(mResolutionText, mResolutionBar.getProgress());


        updateGyroVisibility(mOriginalGyroEnabled);
        updateGestureVisibility(mOriginalGestureDisabled);
    }

    private static void setSeekTextMillisecond(TextView target, int value) {
        setSeekText(target, R.string.millisecond_format, value);
    }

    private static void setSeekTextPercent(TextView target, int value) {
        setSeekText(target, R.string.percent_format, value);
    }

    private static void setSeekText(TextView target, int format, int value) {
        target.setText(target.getContext().getString(format, value));
    }

    private void updateGyroVisibility(boolean isEnabled) {
        int visibility = isEnabled ? View.VISIBLE : View.GONE;
        mGyroXSwitch.setVisibility(visibility);
        mGyroYSwitch.setVisibility(visibility);

        mGyroSensitivityBar.setVisibility(visibility);
        mGyroSensitivityText.setVisibility(visibility);
        mGyroSensitivityDisplayText.setVisibility(visibility);
    }

    private void updateGyroCompatibility() {
        boolean isGyroAvailable = Tools.deviceSupportsGyro(mDialogContent.getContext());
        if (!isGyroAvailable) {
            mGyroSwitch.setVisibility(View.GONE);
            updateGestureVisibility(false);
        }
    }

    private void updateGestureVisibility(boolean isDisabled) {
        int visibility = isDisabled ? View.GONE : View.VISIBLE;
        mGestureDelayBar.setVisibility(visibility);
        mGestureDelayText.setVisibility(visibility);
        mGestureDelayDisplayText.setVisibility(visibility);
    }

    private void removeListeners() {
        mGyroSwitch.setOnCheckedChangeListener(null);
        mGyroXSwitch.setOnCheckedChangeListener(null);
        mGyroYSwitch.setOnCheckedChangeListener(null);
        mGestureSwitch.setOnCheckedChangeListener(null);
        mUncappedFpsSwitch.setOnCheckedChangeListener(null);
        mShowFpsSwitch.setOnCheckedChangeListener(null);
        mMaxFpsSwitch.setOnCheckedChangeListener(null);

        mGyroSensitivityBar.setOnSeekBarChangeListener(null);
        mMouseSpeedBar.setOnSeekBarChangeListener(null);
        mGestureDelayBar.setOnSeekBarChangeListener(null);
        mResolutionBar.setOnSeekBarChangeListener(null);
    }

    private void setupCancelButton() {
        setStartButtonListener(android.R.string.cancel, v -> cancel());
        setEndButtonListener(android.R.string.ok, v -> {
            mEditor.apply();
            disappear(true);
        });
    }

    /** Resets all settings to their original values */
    public void cancel() {
        // Reset all settings if we were editing
        if(isDisplaying()) {
            PREF_ENABLE_GYRO = mOriginalGyroEnabled;
            PREF_GYRO_INVERT_X = mOriginalGyroXEnabled;
            PREF_GYRO_INVERT_Y = mOriginalGyroYEnabled;
            PREF_DISABLE_GESTURES = mOriginalGestureDisabled;

            PREF_GYRO_SENSITIVITY = mOriginalGyroSensitivity;
            PREF_MOUSESPEED = mOriginalMouseSpeed;
            PREF_LONGPRESS_TRIGGER = mOriginalGestureDelay;
            PREF_SCALE_FACTOR = mOriginalResolution;

            if (LauncherPreferences.DEFAULT_PREF.getBoolean("max_fps_mode", false) != mOriginalMaxFps) {
                sExecutorService.execute(() -> applyMaxFpsMode(mOriginalMaxFps));
                LauncherPreferences.DEFAULT_PREF.edit().putBoolean("max_fps_mode", mOriginalMaxFps).apply();
            }

            onGyroStateChanged();
            onResolutionChanged();
        }

        disappear(true);
    }

    /** Called when the resolution is changed. Use {@link LauncherPreferences#PREF_SCALE_FACTOR} */
    public abstract void onResolutionChanged();

    /** Called when the gyro state is changed.
     * Use {@link LauncherPreferences#PREF_ENABLE_GYRO}
     * Use {@link LauncherPreferences#PREF_GYRO_INVERT_X}
     * Use {@link LauncherPreferences#PREF_GYRO_INVERT_Y}
     */
    public abstract void onGyroStateChanged();

    private void applyMaxFpsMode(boolean enabled) {
        final String[] keys = {
                "particles", "renderDistance", "simulationDistance", "entityDistanceScaling",
                "entityShadows", "fancyGraphics", "smoothLighting", "renderClouds", "useVbo",
                "mipmapLevels", "enableVsync", "maxFps", "ofAnimatedWater", "ofAnimatedLava",
                "ofAnimatedFire", "ofAnimatedPortal", "ofAnimatedTerrain", "ofAnimatedTextures",
                "ofAnimatedExplosion", "ofAnimatedSmoke", "ofAnimatedFirework", "ofDynamicLights",
                "ofFogType", "ofSmoothWorld", "ofRenderRegions", "ofFastMath", "ofAaLevel", "ofAfLevel",
                "ofChunkUpdates", "ofLazyChunkLoading", "ofPreloadedChunks", "ofChunkLoading"
        };
        final SharedPreferences prefs = LauncherPreferences.DEFAULT_PREF;
        try {
            if (enabled) {
                SharedPreferences.Editor backup = prefs.edit();
                for (String key : keys) {
                    String value = MCOptionUtils.get(key);
                    if (value != null) backup.putString("max_fps_backup_" + key, value);
                }
                backup.putBoolean("max_fps_backup_valid", true).apply();
                MCOptionUtils.set("particles", "0");
                MCOptionUtils.set("renderDistance", "5");
                MCOptionUtils.set("simulationDistance", "5");
                MCOptionUtils.set("entityDistanceScaling", "0.5");
                MCOptionUtils.set("entityShadows", "false");
                MCOptionUtils.set("fancyGraphics", "false");
                MCOptionUtils.set("smoothLighting", "false");
                MCOptionUtils.set("renderClouds", "false");
                MCOptionUtils.set("useVbo", "true");
                MCOptionUtils.set("mipmapLevels", "0");
                MCOptionUtils.set("enableVsync", "false");
                // Keep a high practical cap without using the game's "unlimited"
                // sentinel, which causes unstable pacing on some mobile drivers.
                MCOptionUtils.set("maxFps", "300");
                MCOptionUtils.set("ofAnimatedWater", "0");
                MCOptionUtils.set("ofAnimatedLava", "0");
                MCOptionUtils.set("ofAnimatedFire", "0");
                MCOptionUtils.set("ofAnimatedPortal", "0");
                MCOptionUtils.set("ofAnimatedTerrain", "0");
                MCOptionUtils.set("ofAnimatedTextures", "false");
                MCOptionUtils.set("ofAnimatedExplosion", "0");
                MCOptionUtils.set("ofAnimatedSmoke", "0");
                MCOptionUtils.set("ofAnimatedFirework", "0");
                MCOptionUtils.set("ofDynamicLights", "3");
                MCOptionUtils.set("ofFogType", "1");
                MCOptionUtils.set("ofSmoothWorld", "false");
                MCOptionUtils.set("ofRenderRegions", "true");
                MCOptionUtils.set("ofFastMath", "true");
                MCOptionUtils.set("ofAaLevel", "0");
                MCOptionUtils.set("ofAfLevel", "1");
                MCOptionUtils.set("ofChunkUpdates", "1");
                MCOptionUtils.set("ofLazyChunkLoading", "true");
                MCOptionUtils.set("ofPreloadedChunks", "0");
                MCOptionUtils.set("ofChunkLoading", "1");
            } else if (prefs.getBoolean("max_fps_backup_valid", false)) {
                for (String key : keys) {
                    String value = prefs.getString("max_fps_backup_" + key, null);
                    if (value != null) MCOptionUtils.set(key, value);
                }
                prefs.edit().putBoolean("max_fps_backup_valid", false).apply();
            }
            MCOptionUtils.save();
        } catch (Exception e) {
            android.util.Log.w("MikaelLauncher", "Não foi possível aplicar o modo Máximo FPS", e);
        }
    }

}
