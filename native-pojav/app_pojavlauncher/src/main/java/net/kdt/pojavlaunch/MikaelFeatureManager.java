package net.kdt.pojavlaunch;

import android.app.ActivityManager;
import android.content.Context;
import android.os.BatteryManager;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;

import net.kdt.pojavlaunch.prefs.LauncherPreferences;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Small, offline-first feature services shared by the native launcher screens. */
public final class MikaelFeatureManager {
    private static final String TAG = "MikaelFeatures";
    private static final int BUFFER_SIZE = 32 * 1024;

    private MikaelFeatureManager() { }

    public static void prepareLaunch(@NonNull Context context, @NonNull File gameDir, String versionId) {
        try {
            if (LauncherPreferences.DEFAULT_PREF.getBoolean("thermal_guard", true)
                    && getBatteryTemperatureC(context) >= 45f) {
                int current = LauncherPreferences.DEFAULT_PREF.getInt("resolutionRatio", 100);
                if (current > 70) {
                    LauncherPreferences.DEFAULT_PREF.edit().putInt("resolutionRatio", current - 10).apply();
                    LauncherPreferences.PREF_SCALE_FACTOR = (current - 10) / 100f;
                }
            }
            if (LauncherPreferences.DEFAULT_PREF.getBoolean("auto_backup", false)) {
                backupInstallation(gameDir, versionId);
            }
            writeCompatibilityReport(context, gameDir, versionId);
        } catch (Exception e) {
            Log.w(TAG, "Launch preparation feature failed; continuing launch", e);
        }
    }

    public static File backupInstallation(File gameDir, String versionId) throws IOException {
        File parent = new File(gameDir, "mikael-backups");
        if (!parent.exists() && !parent.mkdirs()) throw new IOException("Cannot create backup directory");
        String stamp = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(new Date());
        File archive = new File(parent, "backup-" + safe(versionId) + "-" + stamp + ".zip");
        try (ZipOutputStream out = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(archive)))) {
            addDirectory(out, gameDir, gameDir, "mikael-backups");
        }
        return archive;
    }

    public static void cloneInstallation(File source, File destination) throws IOException {
        if (!source.isDirectory()) return;
        if (!destination.exists() && !destination.mkdirs()) throw new IOException("Cannot create destination");
        File[] children = source.listFiles();
        if (children == null) return;
        for (File child : children) {
            if ("mikael-backups".equals(child.getName())) continue;
            File target = new File(destination, child.getName());
            if (child.isDirectory()) cloneInstallation(child, target);
            else copyFile(child, target);
        }
    }

    private static void copyFile(File source, File destination) throws IOException {
        File parent = destination.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) throw new IOException("Cannot create parent");
        try (BufferedInputStream in = new BufferedInputStream(new FileInputStream(source));
             BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(destination))) {
            byte[] buffer = new byte[BUFFER_SIZE];
            int read;
            while ((read = in.read(buffer)) != -1) out.write(buffer, 0, read);
        }
    }

    private static void addDirectory(ZipOutputStream out, File root, File current, String excluded) throws IOException {
        File[] files = current.listFiles();
        if (files == null) return;
        for (File file : files) {
            String relative = root.toURI().relativize(file.toURI()).getPath();
            if (relative.startsWith(excluded + "/") || relative.equals(excluded)) continue;
            if (file.isDirectory()) addDirectory(out, root, file, excluded);
            else {
                out.putNextEntry(new ZipEntry(relative));
                try (BufferedInputStream in = new BufferedInputStream(new FileInputStream(file))) {
                    byte[] buffer = new byte[BUFFER_SIZE];
                    int read;
                    while ((read = in.read(buffer)) != -1) out.write(buffer, 0, read);
                }
                out.closeEntry();
            }
        }
    }

    public static String analyzeLatestCrash(File gameDir) {
        File crashDir = new File(gameDir, "crash-reports");
        File latest = newestFile(crashDir, ".txt");
        if (latest == null) return "Nenhum crash report encontrado.";
        try {
            String text = Tools.read(latest);
            StringBuilder report = new StringBuilder("Arquivo: ").append(latest.getName()).append('\n');
            if (text.contains("OutOfMemoryError")) report.append("Diagnóstico: memória Java insuficiente.\n");
            if (text.contains("MixinApplyError") || text.contains("ModLoadingException")) report.append("Diagnóstico: mod incompatível ou dependência ausente.\n");
            if (text.contains("GLFW") || text.contains("OpenGL") || text.contains("EGL")) report.append("Diagnóstico: falha no renderizador gráfico.\n");
            if (report.toString().endsWith("\n")) report.append("Resumo disponível no crash report.");
            return report.toString();
        } catch (IOException e) {
            return "Não foi possível ler o crash report: " + e.getMessage();
        }
    }

    public static void writeCompatibilityReport(Context context, File gameDir, String versionId) {
        File report = new File(gameDir, "mikael-compatibility.txt");
        try (PrintWriter out = new PrintWriter(report, StandardCharsets.UTF_8.name())) {
            ActivityManager.MemoryInfo memory = new ActivityManager.MemoryInfo();
            ((ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE)).getMemoryInfo(memory);
            out.println("MikaelLauncher compatibility report");
            out.println("Minecraft=" + versionId);
            out.println("Android=" + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")");
            out.println("ABI=" + Build.SUPPORTED_ABIS[0]);
            out.println("Processors=" + Runtime.getRuntime().availableProcessors());
            out.println("RAM_MB=" + (memory.totalMem / 1024 / 1024));
            out.println("Java=" + LauncherPreferences.PREF_DEFAULT_RUNTIME);
            out.println("Renderer=" + LauncherPreferences.PREF_RENDERER);
            out.println("Turbo=" + LauncherPreferences.DEFAULT_PREF.getString("performance_profile", "balanced"));
        } catch (Exception e) {
            Log.w(TAG, "Could not write compatibility report", e);
        }
    }

    public static float getBatteryTemperatureC(Context context) {
        BatteryManager battery = (BatteryManager) context.getSystemService(Context.BATTERY_SERVICE);
        if (battery == null) return -1f;
        android.content.Intent intent = context.registerReceiver(null,
                new android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED));
        if (intent == null) return -1f;
        int tenthCelsius = intent.getIntExtra(android.os.BatteryManager.EXTRA_TEMPERATURE, -1);
        return tenthCelsius < 0 ? -1f : tenthCelsius / 10f;
    }

    public static boolean isFavorite(Context context, String id) {
        return LauncherPreferences.DEFAULT_PREF.getBoolean("favorite_content_" + id, false);
    }

    public static void setFavorite(Context context, String id, boolean favorite) {
        LauncherPreferences.DEFAULT_PREF.edit().putBoolean("favorite_content_" + id, favorite).apply();
    }

    private static File newestFile(File directory, String suffix) {
        File[] files = directory.listFiles((dir, name) -> name.endsWith(suffix));
        File newest = null;
        if (files != null) for (File file : files)
            if (newest == null || file.lastModified() > newest.lastModified()) newest = file;
        return newest;
    }

    private static String safe(String value) {
        return value == null ? "unknown" : value.replaceAll("[^A-Za-z0-9._-]", "_");
    }
}
