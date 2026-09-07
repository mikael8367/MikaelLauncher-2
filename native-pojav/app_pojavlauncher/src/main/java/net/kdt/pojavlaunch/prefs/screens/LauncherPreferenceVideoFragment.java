package net.kdt.pojavlaunch.prefs.screens;

import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;

import androidx.preference.ListPreference;
import androidx.preference.SwitchPreference;
import androidx.preference.SwitchPreferenceCompat;

import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.prefs.CustomSeekBarPreference;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;

/**
 * Fragment for any settings video related
 */
public class LauncherPreferenceVideoFragment extends LauncherPreferenceFragment {
    @Override
    public void onCreatePreferences(Bundle b, String str) {
        addPreferencesFromResource(R.xml.pref_video);
        int resolution = (int) (LauncherPreferences.PREF_SCALE_FACTOR * 100);

        ListPreference performanceProfile = requirePreference("performance_profile", ListPreference.class);
        performanceProfile.setSummaryProvider(preference -> {
            CharSequence entry = ((ListPreference) preference).getEntry();
            return entry == null ? getString(R.string.mikael_profile_balanced) : entry;
        });
        performanceProfile.setOnPreferenceChangeListener((preference, newValue) -> {
            applyPerformanceProfile(String.valueOf(newValue));
            return true;
        });

        //Disable notch checking behavior on android 8.1 and below.
        requirePreference("ignoreNotch").setVisible(Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && LauncherPreferences.PREF_NOTCH_SIZE > 0);

        CustomSeekBarPreference resolutionSeekbar = requirePreference("resolutionRatio",
                CustomSeekBarPreference.class);
        resolutionSeekbar.setSuffix(" %");

        // #724 bug fix
        if (resolution < 25) {
            resolutionSeekbar.setValue(100);
        } else {
            resolutionSeekbar.setValue(resolution);
        }

        // Sustained performance is only available since Nougat
        SwitchPreference sustainedPerfSwitch = requirePreference("sustainedPerformance",
                SwitchPreference.class);
        sustainedPerfSwitch.setVisible(Build.VERSION.SDK_INT >= Build.VERSION_CODES.N);
        sustainedPerfSwitch.setChecked(LauncherPreferences.PREF_SUSTAINED_PERFORMANCE);

        requirePreference("alternate_surface", SwitchPreferenceCompat.class).setChecked(LauncherPreferences.PREF_USE_ALTERNATE_SURFACE);
        requirePreference("force_vsync", SwitchPreferenceCompat.class).setChecked(LauncherPreferences.PREF_FORCE_VSYNC);
        requirePreference("uncapped_fps", SwitchPreferenceCompat.class)
                .setChecked(LauncherPreferences.DEFAULT_PREF.getBoolean("uncapped_fps", false));
        requirePreference("bigCoreAffinity", SwitchPreferenceCompat.class)
                .setChecked(LauncherPreferences.PREF_BIG_CORE_AFFINITY);

        ListPreference rendererListPreference = requirePreference("renderer",
                ListPreference.class);
        Tools.RenderersList renderersList = Tools.getCompatibleRenderers(getContext());
        rendererListPreference.setEntries(renderersList.rendererDisplayNames);
        rendererListPreference.setEntryValues(renderersList.rendererIds.toArray(new String[0]));
        rendererListPreference.setOnPreferenceChangeListener((preference, value) -> {
            updateRendererVisibility(String.valueOf(value));
            return true;
        });
        requirePreference("shader_cache_enabled", SwitchPreferenceCompat.class)
                .setChecked(LauncherPreferences.PREF_SHADER_CACHE_ENABLED);
        requirePreference("zink_threaded", SwitchPreferenceCompat.class)
                .setChecked(LauncherPreferences.PREF_ZINK_THREADED);
        updateRendererVisibility(rendererListPreference.getValue());

        computeVisibility();
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences p, String s) {
        super.onSharedPreferenceChanged(p, s);
        if ("force_vsync".equals(s)) {
            LauncherPreferences.PREF_FORCE_VSYNC = p.getBoolean(s, false);
            if (LauncherPreferences.PREF_FORCE_VSYNC) p.edit().putBoolean("uncapped_fps", false).apply();
        } else if ("uncapped_fps".equals(s)) {
            if (p.getBoolean(s, false)) p.edit().putBoolean("force_vsync", false).apply();
        } else if ("sustainedPerformance".equals(s)) {
            LauncherPreferences.PREF_SUSTAINED_PERFORMANCE = p.getBoolean(s, false);
        } else if ("bigCoreAffinity".equals(s)) {
            LauncherPreferences.PREF_BIG_CORE_AFFINITY = p.getBoolean(s, false);
        } else if ("alternate_surface".equals(s)) {
            LauncherPreferences.PREF_USE_ALTERNATE_SURFACE = p.getBoolean(s, true);
        } else if ("shader_cache_enabled".equals(s)) {
            LauncherPreferences.PREF_SHADER_CACHE_ENABLED = p.getBoolean(s, true);
        } else if ("zink_threaded".equals(s)) {
            LauncherPreferences.PREF_ZINK_THREADED = p.getBoolean(s, true);
        } else if ("renderer_profile".equals(s)) {
            LauncherPreferences.PREF_RENDERER_PROFILE = p.getString(s, "performance");
        }
        computeVisibility();
    }

    private void applyPerformanceProfile(String profile) {
        boolean turbo = "turbo".equals(profile);
        boolean eco = "eco".equals(profile);
        LauncherPreferences.DEFAULT_PREF.edit()
                .putBoolean("uncapped_fps", turbo)
                .putBoolean("force_vsync", eco)
                .putBoolean("sustainedPerformance", false)
                .putBoolean("bigCoreAffinity", turbo)
                .putBoolean("alternate_surface", !eco)
                .apply();
        LauncherPreferences.PREF_FORCE_VSYNC = eco;
        LauncherPreferences.PREF_SUSTAINED_PERFORMANCE = false;
        LauncherPreferences.PREF_BIG_CORE_AFFINITY = turbo;
        LauncherPreferences.PREF_USE_ALTERNATE_SURFACE = !eco;
        requirePreference("uncapped_fps", SwitchPreferenceCompat.class).setChecked(turbo);
        requirePreference("force_vsync", SwitchPreferenceCompat.class).setChecked(eco);
        requirePreference("sustainedPerformance", SwitchPreference.class).setChecked(false);
        requirePreference("bigCoreAffinity", SwitchPreferenceCompat.class).setChecked(turbo);
        requirePreference("alternate_surface", SwitchPreferenceCompat.class).setChecked(!eco);
    }

    private void computeVisibility(){
        requirePreference("force_vsync", SwitchPreferenceCompat.class)
                .setVisible(LauncherPreferences.PREF_USE_ALTERNATE_SURFACE);
    }

    private void updateRendererVisibility(String renderer) {
        boolean zink = renderer != null && renderer.contains("zink");
        requirePreference("zink_threaded", SwitchPreferenceCompat.class).setVisible(zink);
    }
}
