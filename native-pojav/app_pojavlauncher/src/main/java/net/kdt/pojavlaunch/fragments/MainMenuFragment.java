package net.kdt.pojavlaunch.fragments;

import static net.kdt.pojavlaunch.Tools.openPath;
import static net.kdt.pojavlaunch.Tools.shareLog;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.fragment.app.Fragment;

import com.kdt.mcgui.mcVersionSpinner;

import net.kdt.pojavlaunch.CustomControlsActivity;
import net.kdt.pojavlaunch.PojavProfile;
import net.kdt.pojavlaunch.PojavApplication;
import net.kdt.pojavlaunch.MikaelFeatureManager;
import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.extra.ExtraConstants;
import net.kdt.pojavlaunch.extra.ExtraCore;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;
import net.kdt.pojavlaunch.progresskeeper.ProgressKeeper;
import net.kdt.pojavlaunch.value.launcherprofiles.LauncherProfiles;
import net.kdt.pojavlaunch.value.launcherprofiles.MinecraftProfile;
import net.kdt.pojavlaunch.value.MinecraftAccount;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

public class MainMenuFragment extends Fragment {
    public static final String TAG = "MainMenuFragment";

    private mcVersionSpinner mVersionSpinner;
    private TextView mAccountLabel;
    private int pendingContentType;
    private final ActivityResultLauncher<Intent> contentPicker = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() != android.app.Activity.RESULT_OK || result.getData() == null) return;
                Uri uri = result.getData().getData();
                if (uri != null) importSelectedContent(uri, pendingContentType);
            });

    public MainMenuFragment(){
        super(R.layout.fragment_launcher);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        Button mNewsButton = view.findViewById(R.id.news_button);
        Button mDiscordButton = view.findViewById(R.id.discord_button);
        Button mCustomControlButton = view.findViewById(R.id.custom_control_button);
        Button mInstallJarButton = view.findViewById(R.id.install_jar_button);
        Button mShareLogsButton = view.findViewById(R.id.share_logs_button);
        Button mOpenDirectoryButton = view.findViewById(R.id.open_files_button);
        Button mImportResourcepackButton = view.findViewById(R.id.import_resourcepack_button);
        Button mImportShaderpackButton = view.findViewById(R.id.import_shaderpack_button);

        ImageButton mEditProfileButton = view.findViewById(R.id.edit_profile_button);
        Button mPlayButton = view.findViewById(R.id.play_button);
        mVersionSpinner = view.findViewById(R.id.mc_version_spinner);
        mAccountLabel = view.findViewById(R.id.mikael_account);
        updateAccountLabel();

        mNewsButton.setOnClickListener(v -> Tools.openURL(requireActivity(), Tools.URL_HOME));
        mDiscordButton.setOnClickListener(v -> Tools.openURL(requireActivity(), getString(R.string.discord_invite)));
        mCustomControlButton.setOnClickListener(v -> startActivity(new Intent(requireContext(), CustomControlsActivity.class)));
        mInstallJarButton.setOnClickListener(v -> runInstallerWithConfirmation(false));
        mInstallJarButton.setOnLongClickListener(v->{
            runInstallerWithConfirmation(true);
            return true;
        });
        mEditProfileButton.setOnClickListener(v -> mVersionSpinner.openProfileEditor(requireActivity()));

        mPlayButton.setOnClickListener(v -> ExtraCore.setValue(ExtraConstants.LAUNCH_GAME, true));

        mShareLogsButton.setOnClickListener((v) -> shareLog(requireContext()));
        mShareLogsButton.setOnLongClickListener((v) -> {
            new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                    .setTitle("Diagnóstico de logs")
                    .setMessage(MikaelFeatureManager.analyzeLogs(getCurrentProfileDirectory()))
                    .setPositiveButton(android.R.string.ok, null)
                    .show();
            return true;
        });

        mOpenDirectoryButton.setOnClickListener((v)-> {
            Tools.switchDemo(Tools.isDemoProfile(v.getContext())); // avoid switching accounts being able to access
            if(Tools.isDemoProfile(v.getContext())){
                Toast.makeText(v.getContext(), R.string.toast_not_available_demo, Toast.LENGTH_LONG).show();
                return;
            }

            openPath(v.getContext(), getCurrentProfileDirectory(), false);
        });

        // These import actions exist in the portrait layout only. Keep rotation safe when
        // Android recreates this fragment with the landscape resource variant.
        if (mImportResourcepackButton != null)
            mImportResourcepackButton.setOnClickListener(v -> openContentPicker(1));
        if (mImportShaderpackButton != null)
            mImportShaderpackButton.setOnClickListener(v -> openContentPicker(3));


        mNewsButton.setOnLongClickListener((v)->{
            Tools.swapFragment(requireActivity(), GamepadMapperFragment.class, GamepadMapperFragment.TAG, null);
            return true;
        });
    }

    private File getCurrentProfileDirectory() {
        String currentProfile = LauncherPreferences.DEFAULT_PREF.getString(LauncherPreferences.PREF_KEY_CURRENT_PROFILE, null);
        if(!Tools.isValidString(currentProfile)) return new File(Tools.DIR_GAME_NEW);
        LauncherProfiles.load();
        MinecraftProfile profileObject = LauncherProfiles.mainProfileJson.profiles.get(currentProfile);
        if(profileObject == null) return new File(Tools.DIR_GAME_NEW);
        return Tools.getGameDirPath(profileObject);
    }

    @Override
    public void onResume() {
        super.onResume();
        mVersionSpinner.reloadProfiles();
        updateAccountLabel();
    }

    private void updateAccountLabel() {
        if (mAccountLabel == null) return;
        MinecraftAccount account = PojavProfile.getCurrentProfileContent(requireContext(), null);
        if (account == null || account.isLocal()) {
            mAccountLabel.setText("O  Offline");
        } else if (account.isMicrosoft) {
            mAccountLabel.setText("M  Microsoft");
        } else {
            mAccountLabel.setText("E  Ely.by");
        }
    }

    private void runInstallerWithConfirmation(boolean isCustomArgs) {
        // avoid using custom installers to install a version
        if(Tools.isDemoProfile(requireContext())){
            Toast.makeText(requireContext(), R.string.toast_not_available_demo, Toast.LENGTH_LONG).show();
            return;
        }

        if (ProgressKeeper.getTaskCount() == 0)
            Tools.installMod(requireActivity(), isCustomArgs);
        else
            Toast.makeText(requireContext(), R.string.tasks_ongoing, Toast.LENGTH_LONG).show();
    }

    private void openContentPicker(int contentType) {
        pendingContentType = contentType;
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/zip");
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, false);
        contentPicker.launch(intent);
    }

    private void importSelectedContent(Uri uri, int contentType) {
        final android.content.Context context = requireContext().getApplicationContext();
        final File gameDir = getCurrentProfileDirectory();
        PojavApplication.sExecutorService.execute(() -> {
            File temporary = null;
            try {
                String originalName = Tools.getFileName(context, uri);
                if (originalName == null || originalName.trim().isEmpty()) throw new java.io.IOException("Nome de arquivo inválido");
                String safeName = originalName.replaceAll("[^A-Za-z0-9._-]", "_");
                temporary = new File(context.getCacheDir(), "mikael-import-" + System.nanoTime() + "-" + safeName);
                try (InputStream input = context.getContentResolver().openInputStream(uri);
                     FileOutputStream output = new FileOutputStream(temporary)) {
                    if (input == null) throw new java.io.IOException("Não foi possível abrir o arquivo");
                    byte[] buffer = new byte[32 * 1024];
                    int read;
                    while ((read = input.read(buffer)) != -1) output.write(buffer, 0, read);
                }
                File installed = MikaelFeatureManager.importContent(temporary, gameDir, contentType);
                requireActivity().runOnUiThread(() -> Toast.makeText(requireContext(),
                        "Instalado em " + installed.getParentFile().getName(), Toast.LENGTH_LONG).show());
            } catch (Exception error) {
                requireActivity().runOnUiThread(() -> Toast.makeText(requireContext(),
                        "Falha ao importar conteúdo: " + error.getMessage(), Toast.LENGTH_LONG).show());
            } finally {
                if (temporary != null) temporary.delete();
            }
        });
    }
}
