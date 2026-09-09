package net.kdt.pojavlaunch.fragments;

import android.app.AlertDialog;
import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import net.kdt.pojavlaunch.PojavApplication;
import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;
import net.kdt.pojavlaunch.value.launcherprofiles.LauncherProfiles;
import net.kdt.pojavlaunch.value.launcherprofiles.MinecraftProfile;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.FileWriter;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public class ModManagerFragment extends Fragment {
    public static final String TAG = "ModManagerFragment";
    private LinearLayout list;
    private TextView status;
    private EditText search;
    private File modsDir;
    private String targetDir;
    private String filter = "";
    private final ActivityResultLauncher<String[]> importLauncher = registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> { if (uri != null) importMod(uri); });

    public ModManagerFragment() { super(R.layout.fragment_mod_manager); }
    @Nullable @Override public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup parent, @Nullable Bundle state) { return super.onCreateView(inflater, parent, state); }

    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        list = view.findViewById(R.id.mod_manager_list); status = view.findViewById(R.id.mod_manager_status); search = view.findViewById(R.id.mod_manager_search);
        targetDir = getArguments() == null ? null : getArguments().getString("curseforge_target_game_dir");
        if (targetDir == null) targetDir = LauncherPreferences.DEFAULT_PREF.getString("curseforge_target_game_dir", null);
        if (targetDir == null || targetDir.isEmpty()) { LauncherProfiles.load(); MinecraftProfile p = LauncherProfiles.getCurrentProfile(); if (p != null) targetDir = Tools.getGameDirPath(p).getAbsolutePath(); }
        modsDir = targetDir == null ? null : new File(targetDir, "mods");
        ((TextView)view.findViewById(R.id.mod_manager_path)).setText(modsDir == null ? "Instalação não encontrada" : modsDir.getAbsolutePath());
        search.addTextChangedListener(new TextWatcher() { public void beforeTextChanged(CharSequence s,int a,int c,int d){} public void onTextChanged(CharSequence s,int a,int b,int c){ filter=s.toString().toLowerCase(); refresh(); } public void afterTextChanged(Editable e){} });
        view.findViewById(R.id.mod_manager_refresh).setOnClickListener(v -> refresh());
        view.findViewById(R.id.mod_manager_import).setOnClickListener(v -> importLauncher.launch(new String[]{"application/java-archive", "application/zip"}));
        view.findViewById(R.id.mod_manager_backup).setOnClickListener(v -> backupMods());
        view.findViewById(R.id.mod_manager_restore).setOnClickListener(v -> confirmRestore());
        view.findViewById(R.id.mod_manager_curseforge).setOnClickListener(v -> openCurseForge());
        view.findViewById(R.id.mod_manager_enable_all).setOnClickListener(v -> setAllEnabled(true));
        view.findViewById(R.id.mod_manager_disable_all).setOnClickListener(v -> setAllEnabled(false));
        view.findViewById(R.id.mod_manager_report).setOnClickListener(v -> generateReport());
        refresh();
    }

    private File[] getFiles() { return modsDir == null ? null : modsDir.listFiles((d,n) -> (n.endsWith(".jar") || n.endsWith(".jar.disabled")) && n.toLowerCase().contains(filter)); }
    private void refresh() {
        if (list == null) return; list.removeAllViews();
        if (modsDir == null) { status.setText("Pasta da instalação indisponível"); return; }
        File[] files = getFiles(); File[] all = modsDir.listFiles((d,n) -> n.endsWith(".jar") || n.endsWith(".jar.disabled"));
        if (files == null) files = new File[0]; if (all == null) all = new File[0]; Arrays.sort(files, Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER));
        long bytes = 0; for (File f : all) bytes += f.length();
        status.setText(all.length + " mod(s) • " + formatBytes(bytes) + (filter.isEmpty() ? "" : " • filtro ativo"));
        if (files.length == 0) { TextView empty = new TextView(requireContext()); empty.setText("Nenhum mod corresponde ao filtro"); empty.setPadding(0,16,0,16); list.addView(empty); return; }
        Set<String> bases = new HashSet<>(); int duplicates = 0;
        for (File file : files) { String base = baseName(file.getName()); if (!bases.add(base)) duplicates++; addRow(file); }
        if (duplicates > 0) { TextView warning = new TextView(requireContext()); warning.setText("Atenção: " + duplicates + " possível(is) duplicata(s) detectada(s)"); warning.setTextColor(getResources().getColor(R.color.mikael_accent)); list.addView(warning, 0); }
    }
    private String baseName(String n) { String x=n.endsWith(".disabled")?n.substring(0,n.length()-9):n; return x.replaceFirst("[-_]v?[0-9].*$", "").toLowerCase(); }
    private String formatBytes(long b) { if (b < 1024*1024) return (b/1024)+" KB"; return String.format(java.util.Locale.ROOT,"%.1f MB",b/1048576.0); }

    private void addRow(File file) {
        boolean enabled = !file.getName().endsWith(".disabled"); String display = enabled ? file.getName() : file.getName().substring(0,file.getName().length()-9);
        LinearLayout row = new LinearLayout(requireContext()); row.setOrientation(LinearLayout.HORIZONTAL); row.setPadding(0,8,0,8);
        TextView name = new TextView(requireContext()); name.setText((enabled?"● ":"○ ")+display); name.setTextSize(14); name.setTextColor(getResources().getColor(enabled?R.color.mikael_lime:R.color.secondary_text));
        row.addView(name,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));
        Button toggle=new Button(requireContext()); toggle.setText(enabled?"Desativar":"Ativar"); toggle.setAllCaps(false); toggle.setOnClickListener(v->toggle(file)); row.addView(toggle);
        Button remove=new Button(requireContext()); remove.setText("Excluir"); remove.setAllCaps(false); remove.setOnClickListener(v->confirmDelete(file)); row.addView(remove); list.addView(row);
    }
    private void toggle(File file) { File out=file.getName().endsWith(".disabled")?new File(file.getParentFile(),file.getName().substring(0,file.getName().length()-9)):new File(file.getParentFile(),file.getName()+".disabled"); if(file.renameTo(out)) refresh(); else toast("Não foi possível alterar o estado"); }
    private void confirmDelete(File file) { new AlertDialog.Builder(requireContext()).setMessage("Excluir "+file.getName()+"?").setNegativeButton(android.R.string.cancel,null).setPositiveButton(android.R.string.ok,(d,w)->{if(file.delete())refresh();else toast("Falha ao excluir");}).show(); }

    private void setAllEnabled(boolean enabled) {
        if (modsDir == null) return;
        PojavApplication.sExecutorService.execute(() -> {
            int changed = 0; File[] files = modsDir.listFiles((d,n) -> n.endsWith(".jar") || n.endsWith(".jar.disabled"));
            if (files != null) for (File file : files) {
                boolean current = !file.getName().endsWith(".disabled");
                if (current == enabled) continue;
                File out = enabled ? new File(file.getParentFile(), file.getName().substring(0, file.getName().length() - 9)) : new File(file.getParentFile(), file.getName() + ".disabled");
                if (file.renameTo(out)) changed++;
            }
            final int total = changed;
            Tools.runOnUiThread(() -> { toast(total + " mod(s) alterado(s)"); refresh(); });
        });
    }

    private void generateReport() {
        if (modsDir == null) return;
        PojavApplication.sExecutorService.execute(() -> {
            StringBuilder report = new StringBuilder("MikaelLauncher - Relatório de mods\n");
            report.append("Pasta: ").append(modsDir.getAbsolutePath()).append("\n\n");
            File[] files = modsDir.listFiles((d,n) -> n.endsWith(".jar") || n.endsWith(".jar.disabled"));
            int invalid = 0, suspicious = 0;
            if (files != null) for (File file : files) {
                boolean valid = validateJar(file);
                String lower = file.getName().toLowerCase();
                boolean likelyDependency = lower.contains("api") || lower.contains("library") || lower.contains("lib");
                if (!valid) invalid++; if (likelyDependency) suspicious++;
                report.append(valid ? "OK" : "INVALIDO").append(" | ").append(file.getName()).append(" | ").append(formatBytes(file.length()));
                if (likelyDependency) report.append(" | possível biblioteca/dependência");
                report.append("\n");
            }
            report.append("\nArquivos inválidos: ").append(invalid).append("\nPossíveis dependências: ").append(suspicious).append("\n");
            final int invalidCount = invalid;
            try {
                File out = new File(modsDir.getParentFile(), "mikael-mod-report-" + System.currentTimeMillis() + ".txt");
                try (OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(out), StandardCharsets.UTF_8)) { writer.write(report.toString()); }
                Tools.runOnUiThread(() -> toast("Relatório criado: " + out.getName() + (invalidCount > 0 ? " — há arquivos inválidos" : "")));
            } catch (Exception e) { Tools.runOnUiThread(() -> toast("Falha ao criar relatório: " + e.getMessage())); }
        });
    }

    private boolean validateJar(File file) {
        if (file.length() < 4) return false;
        try (java.util.zip.ZipFile zip = new java.util.zip.ZipFile(file)) {
            return zip.getEntry("META-INF/MANIFEST.MF") != null || zip.size() > 0;
        } catch (Exception e) { return false; }
    }

    private void importMod(Uri uri) {
        if (modsDir == null) return;
        PojavApplication.sExecutorService.execute(() -> {
            File temp = null;
            try {
                if (!modsDir.exists() && !modsDir.mkdirs()) throw new IOException("Pasta mods indisponível");
                String name = Tools.getFileName(requireContext(), uri);
                if (name == null || name.isEmpty()) name = "imported-mod.jar";
                name = name.replaceAll("[^A-Za-z0-9._-]", "_");
                temp = new File(Tools.DIR_CACHE, "mikael-import-" + System.nanoTime() + ".zip");
                try (InputStream in = requireContext().getContentResolver().openInputStream(uri);
                     FileOutputStream fos = new FileOutputStream(temp)) {
                    if (in == null) throw new IOException("Arquivo ilegível");
                    copy(in, fos);
                }

                String lower = name.toLowerCase(java.util.Locale.ROOT);
                if (lower.endsWith(".jar")) {
                    File out = uniqueFile(modsDir, name);
                    copyFile(temp, out);
                    finishImport("Mod importado: " + out.getName());
                } else if (lower.endsWith(".zip")) {
                    ImportResult result = importZip(temp);
                    finishImport(result.message);
                } else {
                    throw new IOException("Formato não suportado: use .jar ou .zip");
                }
            } catch (Exception e) {
                final String message = e.getMessage() == null ? "arquivo inválido" : e.getMessage();
                Tools.runOnUiThread(() -> toast("Falha ao importar: " + message));
            } finally {
                if (temp != null) temp.delete();
            }
        });
    }

    private static class ImportResult {
        final String message;
        ImportResult(String message) { this.message = message; }
    }

    private ImportResult importZip(File zipFile) throws IOException {
        boolean hasManifest = false, hasModMetadata = false, hasMods = false, hasRecognizedFiles = false;
        try (ZipFile zip = new ZipFile(zipFile)) {
            java.util.Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                String path = entries.nextElement().getName().replace('\\', '/');
                String p = path.toLowerCase(java.util.Locale.ROOT);
                if (p.equals("manifest.json") || p.equals("modrinth.index.json") || p.endsWith("/manifest.json")) hasManifest = true;
                if (p.endsWith("mods.toml") || p.endsWith("fabric.mod.json") || p.endsWith("mcmod.info")) hasModMetadata = true;
                if (p.startsWith("mods/") || p.contains("/mods/")) hasMods = true;
                if (isPackFile(p)) hasRecognizedFiles = true;
            }
        }
        if (hasModMetadata && !hasManifest && !hasMods) {
            File out = uniqueFile(modsDir, zipFile.getName().replaceFirst("(?i)\\.zip$", ".jar"));
            copyFile(zipFile, out);
            return new ImportResult("Mod ZIP importado como: " + out.getName());
        }
        int extracted = 0;
        try (ZipFile zip = new ZipFile(zipFile)) {
            java.util.Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (entry.isDirectory()) continue;
                String raw = entry.getName().replace('\\', '/');
                String path = safeZipPath(raw);
                if (path == null || path.isEmpty()) continue;
                String lower = path.toLowerCase(java.util.Locale.ROOT);
                if (lower.equals("manifest.json") || lower.equals("modrinth.index.json") || lower.endsWith("/manifest.json")) continue;
                File destination = destinationForZipPath(path);
                if (destination == null) continue;
                File parent = destination.getParentFile();
                if (parent != null && !parent.exists() && !parent.mkdirs()) throw new IOException("Não foi possível criar " + parent);
                try (InputStream in = zip.getInputStream(entry); FileOutputStream out = new FileOutputStream(destination)) { copy(in, out); }
                extracted++;
            }
        }
        if (extracted == 0 && !hasRecognizedFiles && !hasManifest && !hasMods) {
            throw new IOException("ZIP não contém mods, overrides ou arquivos de modpack reconhecidos");
        }
        return new ImportResult("Modpack importado: " + extracted + " arquivo(s) extraído(s)");
    }

    private File destinationForZipPath(String path) {
        String lower = path.toLowerCase(java.util.Locale.ROOT);
        if (lower.startsWith("mods/")) return new File(modsDir, safeZipPath(path.substring(5)));
        if (lower.startsWith("overrides/")) return new File(modsDir.getParentFile(), safeZipPath(path.substring(10)));
        if (lower.endsWith(".jar") && !lower.contains("/")) return new File(modsDir, safeZipPath(path));
        if (lower.startsWith("config/") || lower.startsWith("resourcepacks/")
                || lower.startsWith("shaderpacks/") || lower.startsWith("defaultconfigs/")
                || lower.startsWith("kubejs/") || lower.startsWith("scripts/")
                || lower.equals("options.txt") || lower.equals("servers.dat")) {
            return new File(modsDir.getParentFile(), safeZipPath(path));
        }
        return null;
    }

    private boolean isPackFile(String path) {
        return path.endsWith(".jar") || path.startsWith("config/") || path.startsWith("resourcepacks/")
                || path.startsWith("shaderpacks/") || path.startsWith("defaultconfigs/") || path.startsWith("kubejs/")
                || path.startsWith("scripts/") || path.equals("options.txt") || path.equals("servers.dat");
    }

    private String safeZipPath(String path) {
        String normalized = path.replace('\\', '/');
        while (normalized.startsWith("/")) normalized = normalized.substring(1);
        if (normalized.isEmpty() || normalized.contains("../") || normalized.equals("..") || normalized.contains("/..")) return null;
        return normalized;
    }

    private File uniqueFile(File dir, String name) {
        File out = new File(dir, name);
        int i = 1;
        while (out.exists()) out = new File(dir, name.replaceFirst("(\\.[^.]+)$", "-" + i++ + "$1"));
        return out;
    }

    private void copyFile(File source, File destination) throws IOException {
        try (InputStream in = new FileInputStream(source); FileOutputStream out = new FileOutputStream(destination)) { copy(in, out); }
    }

    private void copy(InputStream in, java.io.OutputStream out) throws IOException {
        byte[] buffer = new byte[8192]; int count;
        while ((count = in.read(buffer)) != -1) out.write(buffer, 0, count);
    }

    private void finishImport(String message) { Tools.runOnUiThread(() -> { toast(message); refresh(); }); }

    private void backupMods() { if(modsDir==null)return; PojavApplication.sExecutorService.execute(()->{try{File dir=new File(modsDir.getParentFile(),"mikael-backups");if(!dir.exists())dir.mkdirs();File zip=new File(dir,"mods-"+System.currentTimeMillis()+".zip");try(ZipOutputStream out=new ZipOutputStream(new FileOutputStream(zip))){File[]fs=modsDir.listFiles();if(fs!=null)for(File f:fs)if(f.isFile()){out.putNextEntry(new ZipEntry(f.getName()));try(FileInputStream in=new FileInputStream(f)){byte[]b=new byte[8192];int n;while((n=in.read(b))!=-1)out.write(b,0,n);}out.closeEntry();}}Tools.runOnUiThread(()->toast("Backup criado: "+zip.getName()));}catch(Exception e){Tools.runOnUiThread(()->toast("Falha no backup: "+e.getMessage()));}}); }
    private void confirmRestore() { File latest=latestBackup(); if(latest==null){toast("Nenhum backup encontrado");return;} new AlertDialog.Builder(requireContext()).setMessage("Restaurar "+latest.getName()+"? Os mods atuais serão substituídos.").setNegativeButton(android.R.string.cancel,null).setPositiveButton(android.R.string.ok,(d,w)->restore(latest)).show(); }
    private File latestBackup(){if(modsDir==null)return null;File dir=new File(modsDir.getParentFile(),"mikael-backups");File[]fs=dir.listFiles((d,n)->n.endsWith(".zip"));if(fs==null||fs.length==0)return null;Arrays.sort(fs,Comparator.comparingLong(File::lastModified).reversed());return fs[0];}
    private void restore(File zip){PojavApplication.sExecutorService.execute(()->{try{if(!modsDir.exists())modsDir.mkdirs();File[]old=modsDir.listFiles();if(old!=null)for(File f:old)if(f.isFile()&&(f.getName().endsWith(".jar")||f.getName().endsWith(".disabled")))f.delete();try(ZipInputStream in=new ZipInputStream(new FileInputStream(zip))){ZipEntry e;byte[]b=new byte[8192];while((e=in.getNextEntry())!=null){File out=new File(modsDir,e.getName().replace("/","_"));try(FileOutputStream fos=new FileOutputStream(out)){int n;while((n=in.read(b))!=-1)fos.write(b,0,n);}in.closeEntry();}}Tools.runOnUiThread(()->{toast("Backup restaurado");refresh();});}catch(Exception e){Tools.runOnUiThread(()->toast("Falha ao restaurar: "+e.getMessage()));}});}
    private void openCurseForge(){Bundle b=new Bundle();b.putString("curseforge_target_game_dir",targetDir);b.putString("curseforge_mc_version",getArguments()==null?"":getArguments().getString("curseforge_mc_version",""));Tools.swapFragment(requireActivity(),SearchModFragment.class,SearchModFragment.TAG,b);}
    private void toast(String text){if(isAdded())Toast.makeText(requireContext(),text,Toast.LENGTH_SHORT).show();}
}
