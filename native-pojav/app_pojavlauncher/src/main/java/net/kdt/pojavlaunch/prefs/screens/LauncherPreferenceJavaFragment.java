package net.kdt.pojavlaunch.prefs.screens;

import static net.kdt.pojavlaunch.Architecture.is32BitsDevice;
import static net.kdt.pojavlaunch.Tools.getTotalDeviceMemory;

import android.os.Bundle;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.preference.EditTextPreference;
import androidx.preference.ListPreference;

import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.contracts.OpenDocumentWithExtension;
import net.kdt.pojavlaunch.multirt.MultiRTConfigDialog;
import net.kdt.pojavlaunch.prefs.CustomSeekBarPreference;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;

public class LauncherPreferenceJavaFragment extends LauncherPreferenceFragment {
    private MultiRTConfigDialog mDialogScreen;
    private final ActivityResultLauncher<Object> mVmInstallLauncher =
            registerForActivityResult(new OpenDocumentWithExtension("xz"), (data)->{
                if(data != null) Tools.installRuntimeFromUri(getContext(), data);
            });

    @Override
    public void onCreatePreferences(Bundle b, String str) {
        int ramAllocation = LauncherPreferences.PREF_RAM_ALLOCATION;
        // Triggers a write for some reason
        addPreferencesFromResource(R.xml.pref_java);

        CustomSeekBarPreference memorySeekbar = requirePreference("allocation",
                CustomSeekBarPreference.class);

        int maxRAM;
        int deviceRam = getTotalDeviceMemory(memorySeekbar.getContext());

        if(is32BitsDevice() || deviceRam < 2048) maxRAM = Math.min(1024, deviceRam);
        else maxRAM = deviceRam - (deviceRam < 3064 ? 800 : 1024); //To have a minimum for the device to breathe

        ListPreference memoryMode = requirePreference("memory_mode", ListPreference.class);
        memoryMode.setSummaryProvider(preference -> {
            String mode = ((ListPreference) preference).getValue();
            return "adaptive".equals(mode)
                    ? "Swap/RAM Plus detectado: " + getSwapMb() + " MB; usado apenas como fallback lento."
                    : "Usando apenas RAM física; melhor latência e estabilidade.";
        });
        memoryMode.setOnPreferenceChangeListener((preference, value) -> {
            preference.setSummaryProvider(ListPreference.SimpleSummaryProvider.getInstance());
            return true;
        });
        if ("adaptive".equals(memoryMode.getValue())) maxRAM += Math.min(getSwapMb() / 4, 2048);

        memorySeekbar.setMaxKeepIncrement(maxRAM);
        memorySeekbar.setValue(ramAllocation);
        memorySeekbar.setSuffix(" MB");

        EditTextPreference editJVMArgs = findPreference("javaArgs");
        if (editJVMArgs != null) {
            editJVMArgs.setOnBindEditTextListener(TextView::setSingleLine);
        }

        requirePreference("install_jre").setOnPreferenceClickListener(preference->{
            openMultiRTDialog();
            return true;
        });
    }

    private void openMultiRTDialog() {
        if (mDialogScreen == null) {
            mDialogScreen = new MultiRTConfigDialog();
            mDialogScreen.prepare(getContext(), mVmInstallLauncher);
        }
        mDialogScreen.show();
    }

    private int getSwapMb() {
        try {
            String memInfo = Tools.read("/proc/meminfo");
            for (String line : memInfo.split("\\n")) {
                if (line.startsWith("SwapTotal:")) {
                    String[] parts = line.trim().split("\\s+");
                    return Integer.parseInt(parts[1]) / 1024;
                }
            }
        } catch (Exception ignored) { }
        return 0;
    }
}
