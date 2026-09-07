package net.kdt.pojavlaunch.prefs.screens;

import static net.kdt.pojavlaunch.Architecture.is32BitsDevice;
import static net.kdt.pojavlaunch.Tools.getTotalDeviceMemory;

import android.os.Bundle;
import android.app.ActivityManager;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.preference.EditTextPreference;
import androidx.preference.ListPreference;
import androidx.preference.Preference;

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
        if ("adaptive".equals(memoryMode.getValue())) maxRAM += getSwapBudgetMb();

        memorySeekbar.setMaxKeepIncrement(maxRAM);
        memorySeekbar.setValue(ramAllocation);
        memorySeekbar.setSuffix(" MB");

        final int selectorMaxRAM = maxRAM;
        Preference ramSelector = requirePreference("ram_available_button", Preference.class);
        ramSelector.setOnPreferenceClickListener(preference -> {
            showRamSelector(memorySeekbar, selectorMaxRAM);
            return true;
        });

        Preference ramPlusButton = requirePreference("ram_plus_button", Preference.class);
        updateRamPlusSummary(ramPlusButton, memoryMode.getValue());
        ramPlusButton.setOnPreferenceClickListener(preference -> {
            boolean enable = !"adaptive".equals(memoryMode.getValue());
            memoryMode.setValue(enable ? "adaptive" : "physical");
            updateRamPlusSummary(ramPlusButton, memoryMode.getValue());
            if (enable && getSwapMb() <= 0) {
                Toast.makeText(requireContext(), "RAM Plus/Swap não foi detectada pelo Android.", Toast.LENGTH_LONG).show();
            }
            return true;
        });

        Preference ramPlusLimit = requirePreference("ram_plus_limit_button", Preference.class);
        updateRamPlusLimitSummary(ramPlusLimit);
        ramPlusLimit.setOnPreferenceClickListener(preference -> {
            showRamPlusLimitSelector(ramPlusLimit);
            return true;
        });

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

    private int getSwapBudgetMb() {
        int percent = LauncherPreferences.DEFAULT_PREF.getInt("ram_plus_limit_percent", 25);
        return Math.min(getSwapMb() * percent / 100, 4096);
    }

    private void showRamPlusLimitSelector(Preference preference) {
        int[] percentages = {0, 25, 50, 75, 100};
        String[] labels = {"0% — desativado", "25% — conservador", "50% — equilibrado", "75% — agressivo", "100% — máximo detectado"};
        int current = LauncherPreferences.DEFAULT_PREF.getInt("ram_plus_limit_percent", 25);
        int checked = 1;
        for (int i = 0; i < percentages.length; i++) if (percentages[i] == current) checked = i;
        final int[] selected = {checked};
        new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle(R.string.mikael_ram_plus_limit_title)
                .setMessage("Swap/RAM Plus detectada: " + getSwapMb() + " MB. O Android decide o uso físico real.")
                .setSingleChoiceItems(labels, checked, (dialog, which) -> selected[0] = which)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    int percent = percentages[selected[0]];
                    LauncherPreferences.DEFAULT_PREF.edit().putInt("ram_plus_limit_percent", percent).apply();
                    updateRamPlusLimitSummary(preference);
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void showRamSelector(CustomSeekBarPreference memorySeekbar, int maxRAM) {
        if (getContext() == null) return;
        int available = getAvailableRamMb();
        int physical = getTotalDeviceMemory(getContext());
        int safeMax = Math.min(maxRAM, Math.max(1024, available > 0 ? available - 512 : maxRAM));
        java.util.ArrayList<Integer> values = new java.util.ArrayList<>();
        for (int value = 1024; value <= safeMax; value += 512) values.add(value);
        if (values.isEmpty() || values.get(values.size() - 1) != safeMax) values.add(safeMax);
        String[] labels = new String[values.size()];
        int checked = 0;
        int current = LauncherPreferences.PREF_RAM_ALLOCATION;
        for (int i = 0; i < values.size(); i++) {
            labels[i] = values.get(i) + " MB";
            if (Math.abs(values.get(i) - current) < Math.abs(values.get(checked) - current)) checked = i;
        }
        final int[] selected = {checked};
        new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle(R.string.mikael_ram_selector_title)
                .setMessage(getString(R.string.mikael_ram_physical_info, physical, getSwapMb()))
                .setSingleChoiceItems(labels, checked, (dialog, which) -> selected[0] = which)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    int chosen = values.get(selected[0]);
                    LauncherPreferences.PREF_RAM_ALLOCATION = chosen;
                    LauncherPreferences.DEFAULT_PREF.edit().putInt("allocation", chosen).apply();
                    memorySeekbar.setValue(chosen);
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private int getAvailableRamMb() {
        try {
            ActivityManager manager = (ActivityManager) requireContext().getSystemService(android.content.Context.ACTIVITY_SERVICE);
            ActivityManager.MemoryInfo info = new ActivityManager.MemoryInfo();
            if (manager != null) {
                manager.getMemoryInfo(info);
                return (int) (info.availMem / 1024 / 1024);
            }
        } catch (Exception ignored) { }
        return 0;
    }

    private void updateRamPlusSummary(Preference preference, String mode) {
        if ("adaptive".equals(mode)) {
            preference.setSummary("RAM Plus ativada: " + getSwapMb() + " MB detectados; usada como complemento mais lento.");
        } else {
            preference.setSummary("RAM Plus desativada; usar somente RAM física.");
        }
    }

    private void updateRamPlusLimitSummary(Preference preference) {
        int percent = LauncherPreferences.DEFAULT_PREF.getInt("ram_plus_limit_percent", 25);
        preference.setSummary(percent + "% do Swap considerado pelo launcher; limite suave, controlado pelo Android.");
    }
}
