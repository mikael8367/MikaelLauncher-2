package net.kdt.pojavlaunch.fragments;

import android.app.AlertDialog;
import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.PojavApplication;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;
import net.kdt.pojavlaunch.value.launcherprofiles.LauncherProfiles;
import net.kdt.pojavlaunch.value.launcherprofiles.MinecraftProfile;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Comparator;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class ModManagerFragment extends Fragment {
    public static final String TAG = "ModManagerFragment";
    private LinearLayout list;
    private TextView status;
    private File modsDir;
    private String targetDir;
    private final ActivityResultLauncher<String[]> importLauncher = registerForActivityResult(
            new ActivityResultContracts.OpenDocument(), uri -> { if (uri != null) importMod(uri); });

    public ModManagerFragment() { super(R.layout.fragment_mod_manager); }

    @Nullable @Override public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup parent, @Nullable Bundle state) {
        return super.onCreateView(inflater, parent, state);
    }

    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        list = view.findViewById(R.id.mod_manager_list);
        status = view.findViewById(R.id.mod_manager_status);
        targetDir = getArguments() == null ? null : getArguments().getString("curseforge_target_game_dir");
        if (targetDir == null) targetDir = LauncherPreferences.DEFAULT_PREF.getString("curseforge_target_game_dir", null);
        if (targetDir == null || targetDir.isEmpty()) {
            LauncherProfiles.load();
            MinecraftProfile p = LauncherProfiles.getCurrentProfile();
            if (p != null) targetDir = Tools.getGameDirPath(p).getAbsolutePath();
        }
        modsDir = targetDir == null ? null : new File(targetDir, "mods");
        TextView path = view.findViewById(R.id.mod_manager_path);
        path.setText(modsDir == null ? "Instalação não encontrada" : modsDir.getAbsolutePath());
        view.findViewById(R.id.mod_manager_refresh).setOnClickListener(v -> refresh());
        view.findViewById(R.id.mod_manager_import).setOnClickListener(v -> importLauncher.launch(new String[]{"application/java-archive", "application/zip"}));
        view.findViewById(R.id.mod_manager_backup).setOnClickListener(v -> backupMods());
        view.findViewById(R.id.mod_manager_curseforge).setOnClickListener(v -> openCurseForge());
        refresh();
    }

    private void refresh() {
        if (list == null) return;
        list.removeAllViews();
        if (modsDir == null) { status.setText("Pasta da instalação indisponível"); return; }
        File[] files = modsDir.listFiles((d, n) -> n.endsWith(".jar") || n.endsWith(".jar.disabled"));
        if (files == null || files.length == 0) { status.setText("Nenhum mod instalado"); return; }
        Arrays.sort(files, Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER));
        status.setText(files.length + " mod(s) nesta instalação");
        for (File file : files) addRow(file);
    }

    private void addRow(File file) {
        boolean enabled = !file.getName().endsWith(".disabled");
        String display = enabled ? file.getName() : file.getName().substring(0, file.getName().length() - ".disabled".length());
        LinearLayout row = new LinearLayout(requireContext()); row.setOrientation(LinearLayout.HORIZONTAL); row.setPadding(0, 8, 0, 8);
        TextView name = new TextView(requireContext()); name.setText((enabled ? "● " : "○ ") + display); name.setTextSize(14); name.setTextColor(getResources().getColor(enabled ? R.color.mikael_lime : R.color.secondary_text));
        row.addView(name, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        Button toggle = new Button(requireContext()); toggle.setText(enabled ? "Desativar" : "Ativar"); toggle.setAllCaps(false); toggle.setOnClickListener(v -> toggle(file)); row.addView(toggle);
        Button remove = new Button(requireContext()); remove.setText("Excluir"); remove.setAllCaps(false); remove.setOnClickListener(v -> confirmDelete(file)); row.addView(remove);
        list.addView(row);
    }

    private void toggle(File file) {
        File out = file.getName().endsWith(".disabled") ? new File(file.getParentFile(), file.getName().substring(0, file.getName().length() - 9)) : new File(file.getParentFile(), file.getName() + ".disabled");
        if (file.renameTo(out)) refresh(); else toast("Não foi possível alterar o estado do mod");
    }

    private void confirmDelete(File file) {
        new AlertDialog.Builder(requireContext()).setMessage("Excluir " + file.getName() + "?")
                .setNegativeButton(android.R.string.cancel, null).setPositiveButton(android.R.string.ok, (d, w) -> { if (file.delete()) refresh(); else toast("Falha ao excluir"); }).show();
    }

    private void importMod(Uri uri) {
        if (modsDir == null) return;
        PojavApplication.sExecutorService.execute(() -> {
            try {
                if (!modsDir.exists() && !modsDir.mkdirs()) throw new IOException("Pasta mods indisponível");
                String name = Tools.getFileName(requireContext(), uri);
                if (name == null || name.isEmpty()) name = "imported-mod.jar";
                name = name.replaceAll("[^A-Za-z0-9._-]", "_");
                if (!name.endsWith(".jar")) name += ".jar";
                final String importedName = name;
                File out = new File(modsDir, importedName);
                try (InputStream in = requireContext().getContentResolver().openInputStream(uri); FileOutputStream fos = new FileOutputStream(out)) {
                    if (in == null) throw new IOException("Arquivo não pode ser lido");
                    byte[] buf = new byte[8192]; int n; while ((n = in.read(buf)) != -1) fos.write(buf, 0, n);
                }
                Tools.runOnUiThread(() -> { toast("Mod importado: " + importedName); refresh(); });
            } catch (Exception e) { Tools.runOnUiThread(() -> toast("Falha ao importar mod: " + e.getMessage())); }
        });
    }

    private void backupMods() {
        if (modsDir == null) return;
        PojavApplication.sExecutorService.execute(() -> {
            try {
                File backupDir = new File(modsDir.getParentFile(), "mikael-backups"); if (!backupDir.exists()) backupDir.mkdirs();
                File zip = new File(backupDir, "mods-" + System.currentTimeMillis() + ".zip");
                try (ZipOutputStream out = new ZipOutputStream(new FileOutputStream(zip))) {
                    File[] files = modsDir.listFiles(); if (files != null) for (File f : files) if (f.isFile()) { out.putNextEntry(new ZipEntry(f.getName())); try (FileInputStream in = new FileInputStream(f)) { byte[] b = new byte[8192]; int n; while ((n = in.read(b)) != -1) out.write(b, 0, n); } out.closeEntry(); }
                }
                Tools.runOnUiThread(() -> toast("Backup criado: " + zip.getName()));
            } catch (Exception e) { Tools.runOnUiThread(() -> toast("Falha no backup: " + e.getMessage())); }
        });
    }

    private void openCurseForge() {
        Bundle b = new Bundle(); b.putString("curseforge_target_game_dir", targetDir);
        b.putString("curseforge_mc_version", getArguments() == null ? "" : getArguments().getString("curseforge_mc_version", ""));
        Tools.swapFragment(requireActivity(), SearchModFragment.class, SearchModFragment.TAG, b);
    }
    private void toast(String text) { if (isAdded()) Toast.makeText(requireContext(), text, Toast.LENGTH_SHORT).show(); }
}
