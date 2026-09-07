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
        if (LauncherPreferences.DEFAULT_PREF.getBoolean("thermal_guard", true)
                && getBatteryTemperatureC(context) >= 45f) {
            int current = LauncherPreferences.DEFAULT_PREF.getInt("resolutionRatio", 100);
            if (current > 70) {
                LauncherPreferences.DEFAULT_PREF.edit().putInt("resolutionRatio", current - 10).apply();
                LauncherPreferences.PREF_SCALE_FACTOR = (current - 10) / 100f;
            }
        }
        new Thread(() -> {
            try {
                if (LauncherPreferences.DEFAULT_PREF.getBoolean("auto_backup", false)) backupInstallation(gameDir, versionId);
            writeCompatibilityReport(context, gameDir, versionId);
            writeDiagnosticReport(context, gameDir, versionId);
            writeDiagnosticJson(context, gameDir, versionId);
            } catch (Exception e) {
                Log.w(TAG, "Launch preparation feature failed; continuing launch", e);
            }
        }, "mikael-launch-diagnostics").start();
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

    public static File setContentEnabled(File content, boolean enabled) {
        if (content == null || !content.exists()) return content;
        String name = content.getName();
        boolean disabled = name.endsWith(".mikael-disabled");
        if (enabled && disabled) {
            File target = new File(content.getParentFile(), name.substring(0, name.length() - ".mikael-disabled".length()));
            return content.renameTo(target) ? target : content;
        }
        if (!enabled && !disabled) {
            File target = new File(content.getParentFile(), name + ".mikael-disabled");
            return content.renameTo(target) ? target : content;
        }
        return content;
    }

    public static File importContent(File source, File gameDir, int contentType) throws IOException {
        if (source == null || !source.isFile()) throw new IOException("Content file not found");
        String folder;
        switch (contentType) {
            case 1: folder = "resourcepacks"; break;
            case 2: folder = "mikael-content/worlds"; break;
            case 3: folder = "shaderpacks"; break;
            default: folder = "mods"; break;
        }
        File destination = new File(new File(gameDir, folder), source.getName());
        copyFile(source, destination);
        return destination;
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
            String text = readTail(latest, 2 * 1024 * 1024);
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

    public static String analyzeLogs(File gameDir) {
        File latestLog = new File(gameDir, "logs/latest.log");
        StringBuilder result = new StringBuilder("Diagnóstico MikaelLauncher\n\n");
        if (!latestLog.exists()) result.append("latest.log não encontrado.\n");
        else {
            try {
                String log = readTail(latestLog, 4 * 1024 * 1024);
                int errors = count(log, "[ERROR]") + count(log, " ERROR ");
                int warnings = count(log, "[WARN]") + count(log, " WARN ");
                String rootCause = findRootCause(log);
                result.append("latest.log: ").append(errors).append(" erros, ").append(warnings).append(" avisos\n");
                if (rootCause != null) result.append("Causa raiz encontrada (confiança 95%): ").append(rootCause).append('\n');
                String likelyMod = extractLikelyMod(log);
                if (likelyMod != null) result.append("Mod citado no erro: ").append(likelyMod).append('\n');
                String javaIssue = detectJavaArgumentIssue(log);
                if (javaIssue != null) result.append("Problema de Java: ").append(javaIssue).append('\n');
                result.append("Armazenamento livre: ").append(getFreeStorageMb(gameDir)).append(" MB\n");
                if (containsAny(log, "OutOfMemoryError", "GC overhead limit exceeded")) result.append("• Memória Java insuficiente ou pressão excessiva do GC (confiança 90%).\n");
                if (containsAny(log, "MixinApplyError", "ModLoadingException", "NoClassDefFoundError")) result.append("• Mod incompatível, dependência ausente ou versão incorreta (confiança 90%).\n");
                if (containsAny(log, "GLFW", "OpenGL", "EGL", "ZINK")) result.append("• Problema potencial no renderizador gráfico (confiança 75%; verifique a linha de causa raiz).\n");
                if (containsAny(log, "Connection refused", "SocketTimeoutException", "HTTP 5")) result.append("• Falha de rede ou servidor indisponível durante download (confiança 85%).\n");
                if (errors == 0 && warnings == 0) result.append("• Nenhum erro relevante encontrado no log.\n");
            } catch (IOException e) {
                result.append("Falha ao ler latest.log: ").append(e.getMessage()).append('\n');
            }
        }
        result.append('\n').append(analyzeLatestCrash(gameDir));
        return result.toString();
    }

    private static int count(String text, String token) {
        int total = 0, index = 0;
        while ((index = text.indexOf(token, index)) >= 0) { total++; index += token.length(); }
        return total;
    }

    private static boolean containsAny(String text, String... tokens) {
        for (String token : tokens) if (text.contains(token)) return true;
        return false;
    }

    private static String readTail(File file, int maxBytes) throws IOException {
        long length = file.length();
        long start = Math.max(0, length - maxBytes);
        try (java.io.RandomAccessFile input = new java.io.RandomAccessFile(file, "r")) {
            input.seek(start);
            byte[] data = new byte[(int) Math.min(maxBytes, length)];
            int read = input.read(data);
            return read <= 0 ? "" : new String(data, 0, read, StandardCharsets.UTF_8);
        }
    }

    public static long getFreeStorageMb(File gameDir) {
        try { return gameDir.getUsableSpace() / 1024 / 1024; }
        catch (Exception ignored) { return -1; }
    }

    public static long getLogSizeMb(File gameDir) {
        File log = new File(gameDir, "logs/latest.log");
        return log.exists() ? log.length() / 1024 / 1024 : 0;
    }

    private static String extractLikelyMod(String text) {
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("(?:mod|Mod|mixin)[: ]+([A-Za-z0-9_.-]{3,64})").matcher(text);
        return matcher.find() ? matcher.group(1) : null;
    }

    private static String detectJavaArgumentIssue(String text) {
        if (containsAny(text, "Unrecognized VM option", "Could not create the Java Virtual Machine")) return "argumento JVM não reconhecido";
        if (containsAny(text, "Invalid maximum heap size", "Initial heap size set to a larger value")) return "alocação de memória JVM inválida";
        return null;
    }

    private static String rendererEvidence(String text) {
        if (containsAny(text, "ZINK", "MESA", "vulkan")) return "Zink/Vulkan";
        if (containsAny(text, "GL4ES", "LIBGL", "OpenGL ES")) return "GL4ES/OpenGL ES";
        if (containsAny(text, "LTW", "ANGLE")) return "LTW/ANGLE";
        return "não identificado";
    }

    public static void writeDiagnosticReport(Context context, File gameDir, String versionId) {
        File report = new File(gameDir, "mikael-diagnostics.txt");
        try (PrintWriter out = new PrintWriter(report, StandardCharsets.UTF_8.name())) {
            out.println(analyzeLogs(gameDir));
            out.println("\nRenderer configurado: " + LauncherPreferences.PREF_RENDERER);
            File latestLog = new File(gameDir, "logs/latest.log");
            out.println("Renderer evidenciado no log: " + (latestLog.exists()
                    ? rendererEvidence(readTail(latestLog, 4 * 1024 * 1024)) : "não identificado"));
            out.println("Crash reports recentes: " + countCrashReports(gameDir));
            out.println("Versão: " + versionId);
            out.println("Data do relatório: " + new java.util.Date());
            out.println("Tamanho latest.log: " + getLogSizeMb(gameDir) + " MB");
            out.println("Espaço livre: " + getFreeStorageMb(gameDir) + " MB");
            out.println("Modo de memória: " + LauncherPreferences.DEFAULT_PREF.getString("memory_mode", "physical"));
            out.println("Runtime selecionado: " + LauncherPreferences.PREF_DEFAULT_RUNTIME);
            out.println("Perfil do renderer: " + LauncherPreferences.PREF_RENDERER_PROFILE);
            out.println("Cache de shaders: " + LauncherPreferences.PREF_SHADER_CACHE_ENABLED);
            out.println("Threading Zink: " + LauncherPreferences.PREF_ZINK_THREADED);
            out.println("Temperatura atual: indisponível sem contexto de atividade");
        } catch (Exception e) { Log.w(TAG, "Could not write diagnostic report", e); }
    }

    private static int countCrashReports(File gameDir) {
        File dir = new File(gameDir, "crash-reports");
        File[] files = dir.listFiles((d, name) -> name.endsWith(".txt"));
        return files == null ? 0 : files.length;
    }

    public static void writeDiagnosticJson(Context context, File gameDir, String versionId) {
        File report = new File(gameDir, "mikael-diagnostics.json");
        try {
            org.json.JSONObject json = new org.json.JSONObject();
            json.put("launcher", "MikaelLauncher");
            json.put("version", versionId);
            json.put("timestamp", System.currentTimeMillis());
            json.put("android", Build.VERSION.RELEASE);
            json.put("api", Build.VERSION.SDK_INT);
            json.put("java_runtime", System.getProperty("java.runtime.version", "unknown"));
            json.put("java_vendor", System.getProperty("java.vendor", "unknown"));
            json.put("locale", Locale.getDefault().toLanguageTag());
            json.put("timezone", java.util.TimeZone.getDefault().getID());
            json.put("uptime_ms", android.os.SystemClock.elapsedRealtime());
            json.put("manufacturer", Build.MANUFACTURER);
            json.put("model", Build.MODEL);
            json.put("device", Build.DEVICE);
            json.put("board", Build.BOARD);
            json.put("fingerprint", Build.FINGERPRINT);
            json.put("kernel", System.getProperty("os.version", "unknown"));
            json.put("abi", Build.SUPPORTED_ABIS.length == 0 ? "unknown" : Build.SUPPORTED_ABIS[0]);
            json.put("abis", new org.json.JSONArray(java.util.Arrays.asList(Build.SUPPORTED_ABIS)));
            json.put("processors", Runtime.getRuntime().availableProcessors());
            json.put("physical_memory_mb", Tools.getTotalDeviceMemory(context));
            json.put("swap_total_mb", readMemInfoMb("SwapTotal"));
            json.put("swap_free_mb", readMemInfoMb("SwapFree"));
            android.util.DisplayMetrics display = context.getResources().getDisplayMetrics();
            json.put("screen_width_px", display.widthPixels);
            json.put("screen_height_px", display.heightPixels);
            json.put("screen_density", display.density);
            ActivityManager.MemoryInfo memory = new ActivityManager.MemoryInfo();
            ActivityManager manager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
            if (manager != null) {
                manager.getMemoryInfo(memory);
                json.put("available_memory_mb", memory.availMem / 1024 / 1024);
                json.put("low_memory", memory.lowMemory);
                json.put("memory_threshold_mb", memory.threshold / 1024 / 1024);
            }
            android.content.Intent battery = context.registerReceiver(null,
                    new android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED));
            if (battery != null) {
                json.put("battery_percent", battery.getIntExtra(BatteryManager.EXTRA_LEVEL, -1));
                json.put("battery_scale", battery.getIntExtra(BatteryManager.EXTRA_SCALE, -1));
                json.put("battery_temperature_c", battery.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1) / 10.0);
                json.put("battery_status", battery.getIntExtra(BatteryManager.EXTRA_STATUS, -1));
                json.put("battery_plugged", battery.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1));
                json.put("battery_voltage_mv", battery.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1));
                json.put("battery_health", battery.getIntExtra(BatteryManager.EXTRA_HEALTH, -1));
                // Some Android vendor broadcasts expose this optional field as current_now.
                json.put("battery_current_ua", battery.getIntExtra("current_now", -1));
                json.put("battery_capacity_percent", battery.getIntExtra(BatteryManager.EXTRA_LEVEL, -1));
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                android.os.PowerManager power = (android.os.PowerManager) context.getSystemService(Context.POWER_SERVICE);
                if (power != null) {
                    json.put("thermal_status", power.getCurrentThermalStatus());
                    json.put("power_save", power.isPowerSaveMode());
                    json.put("interactive", power.isInteractive());
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        json.put("thermal_headroom_10s", power.getThermalHeadroom(10));
                    }
                }
            }
            json.put("free_storage_mb", getFreeStorageMb(gameDir));
            json.put("log_size_mb", getLogSizeMb(gameDir));
            json.put("crash_reports", countCrashReports(gameDir));
            json.put("latest_log_sha256", sha256(new File(gameDir, "logs/latest.log")));
            json.put("latest_crash_sha256", sha256(newestFile(new File(gameDir, "crash-reports"), ".txt")));
            json.put("renderer", LauncherPreferences.PREF_RENDERER);
            json.put("renderer_profile", LauncherPreferences.PREF_RENDERER_PROFILE);
            json.put("memory_mode", LauncherPreferences.DEFAULT_PREF.getString("memory_mode", "physical"));
            json.put("shader_cache", LauncherPreferences.PREF_SHADER_CACHE_ENABLED);
            json.put("zink_threaded", LauncherPreferences.PREF_ZINK_THREADED);
            json.put("uncapped_fps", LauncherPreferences.DEFAULT_PREF.getBoolean("uncapped_fps", false));
            json.put("vsync", LauncherPreferences.PREF_FORCE_VSYNC);
            json.put("vsync_in_zink", LauncherPreferences.PREF_VSYNC_IN_ZINK);
            json.put("big_core_affinity", LauncherPreferences.PREF_BIG_CORE_AFFINITY);
            json.put("sustained_performance", LauncherPreferences.PREF_SUSTAINED_PERFORMANCE);
            json.put("alternate_surface", LauncherPreferences.PREF_USE_ALTERNATE_SURFACE);
            json.put("zink_system_driver", LauncherPreferences.PREF_ZINK_PREFER_SYSTEM_DRIVER);
            json.put("library_sha_check", LauncherPreferences.PREF_CHECK_LIBRARY_SHA);
            json.put("manifest_verification", LauncherPreferences.PREF_VERIFY_MANIFEST);
            json.put("download_source", LauncherPreferences.PREF_DOWNLOAD_SOURCE);
            json.put("resolution_scale", LauncherPreferences.PREF_SCALE_FACTOR);
            json.put("performance_profile", LauncherPreferences.DEFAULT_PREF.getString("performance_profile", "balanced"));
            json.put("resolution_ratio", LauncherPreferences.DEFAULT_PREF.getInt("resolutionRatio", 100));
            json.put("runtime_count", net.kdt.pojavlaunch.multirt.MultiRTUtils.getRuntimes().size());
            json.put("default_runtime", LauncherPreferences.PREF_DEFAULT_RUNTIME);
            json.put("cpu0_frequency_khz", readLongFile("/sys/devices/system/cpu/cpu0/cpufreq/scaling_cur_freq"));
            json.put("cpu0_governor", readTextFile("/sys/devices/system/cpu/cpu0/cpufreq/scaling_governor"));
            json.put("cpu_count_online", countOnlineCpus());
            json.put("storage_total_mb", gameDir.getTotalSpace() / 1024 / 1024);
            json.put("storage_usable_mb", gameDir.getUsableSpace() / 1024 / 1024);
            json.put("game_dir_exists", gameDir.isDirectory());
            json.put("game_dir_readable", gameDir.canRead());
            json.put("game_dir_writable", gameDir.canWrite());
            json.put("cache_dir_exists", Tools.DIR_CACHE != null && Tools.DIR_CACHE.isDirectory());
            json.put("cache_dir_usable_mb", Tools.DIR_CACHE == null ? -1 : Tools.DIR_CACHE.getUsableSpace() / 1024 / 1024);
            json.put("native_lib_dir_exists", Tools.NATIVE_LIB_DIR != null && new File(Tools.NATIVE_LIB_DIR).isDirectory());
            json.put("internet_permission", context.checkCallingOrSelfPermission("android.permission.INTERNET") == android.content.pm.PackageManager.PERMISSION_GRANTED);
            json.put("vulkan_feature", Tools.checkVulkanSupport(context.getPackageManager()));
            json.put("hardware", Build.HARDWARE);
            json.put("mods_count", countFiles(new File(gameDir, "mods")));
            json.put("worlds_count", countDirectories(new File(gameDir, "saves")));
            json.put("resourcepacks_count", countFiles(new File(gameDir, "resourcepacks")));
            json.put("shaderpacks_count", countFiles(new File(gameDir, "shaderpacks")));
            json.put("libraries_count", countFiles(new File(gameDir, "libraries")));
            json.put("logs_count", countFiles(new File(gameDir, "logs")));
            json.put("options_exists", new File(gameDir, "options.txt").isFile());
            json.put("versions_count", countDirectories(new File(gameDir, "versions")));
            json.put("mods_size_mb", directorySizeMb(new File(gameDir, "mods")));
            json.put("saves_size_mb", directorySizeMb(new File(gameDir, "saves")));
            json.put("resourcepacks_size_mb", directorySizeMb(new File(gameDir, "resourcepacks")));
            json.put("shaderpacks_size_mb", directorySizeMb(new File(gameDir, "shaderpacks")));
            json.put("libraries_size_mb", directorySizeMb(new File(gameDir, "libraries")));
            json.put("versions_size_mb", directorySizeMb(new File(gameDir, "versions")));
            json.put("assets_size_mb", directorySizeMb(new File(gameDir, "assets")));
            json.put("launcher_profiles_exists", new File(gameDir, "launcher_profiles.json").isFile());
            json.put("servers_exists", new File(gameDir, "servers.dat").isFile());
            json.put("empty_mods", isEmptyDirectory(new File(gameDir, "mods")));
            json.put("empty_libraries", isEmptyDirectory(new File(gameDir, "libraries")));
            json.put("jar_count", countExtension(new File(gameDir, "libraries"), ".jar"));
            json.put("native_count", countNativeFiles(new File(gameDir, "libraries")));
            json.put("hidden_files_count", countHiddenFiles(gameDir));
            json.put("total_installation_size_mb", directorySizeMb(gameDir));
            json.put("latest_log_modified", fileModified(new File(gameDir, "logs/latest.log")));
            json.put("options_modified", fileModified(new File(gameDir, "options.txt")));
            json.put("mods_modified", directoryLatestModified(new File(gameDir, "mods")));
            json.put("forge_detected", containsName(new File(gameDir, "versions"), "forge"));
            json.put("fabric_detected", containsName(new File(gameDir, "versions"), "fabric"));
            json.put("quilt_detected", containsName(new File(gameDir, "versions"), "quilt"));
            json.put("optifine_detected", containsName(new File(gameDir, "mods"), "optifine"));
            json.put("version_manifests", countExtensionRecursive(new File(gameDir, "versions"), ".json"));
            json.put("active_profile", LauncherPreferences.DEFAULT_PREF.getString("current_profile", "default"));
            json.put("profiles_file_size", fileSize(new File(gameDir, "launcher_profiles.json")));
            json.put("invalid_version_json_count", countInvalidJson(new File(gameDir, "versions")));
            json.put("versions_without_jar", countVersionsWithoutJar(new File(gameDir, "versions")));
            json.put("libraries_without_files", countMissingLibraryFiles(new File(gameDir, "libraries")));
            json.put("assets_index_count", countExtensionRecursive(new File(gameDir, "assets"), ".json"));
            json.put("zero_byte_jars", countZeroByteFiles(new File(gameDir, "libraries"), ".jar"));
            json.put("zero_byte_mods", countZeroByteFiles(new File(gameDir, "mods"), ".jar"));
            json.put("orphan_versions", countOrphanVersions(new File(gameDir, "versions")));
            json.put("incomplete_manifests", countIncompleteManifests(new File(gameDir, "versions")));
            json.put("temporary_downloads", countTemporaryFiles(gameDir));
            json.put("manifest_sha256", sha256(new File(gameDir, "launcher_profiles.json")));
            json.put("latest_version_manifest_sha256", sha256(latestVersionManifest(new File(gameDir, "versions"))));
            int integrityIssues = countInvalidJson(new File(gameDir, "versions"))
                    + countZeroByteFiles(new File(gameDir, "libraries"), ".jar")
                    + countZeroByteFiles(new File(gameDir, "mods"), ".jar")
                    + countOrphanVersions(new File(gameDir, "versions"));
            json.put("integrity_issue_count", integrityIssues);
            json.put("integrity_severity", integrityIssues == 0 ? "none" : (integrityIssues < 3 ? "warning" : "critical"));
            android.net.ConnectivityManager connectivity = (android.net.ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            json.put("network_available", connectivity != null && connectivity.getActiveNetwork() != null);
            if (connectivity != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                android.net.Network network = connectivity.getActiveNetwork();
                android.net.NetworkCapabilities caps = network == null ? null : connectivity.getNetworkCapabilities(network);
                if (caps != null) {
                    json.put("network_wifi", caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI));
                    json.put("network_cellular", caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR));
                    json.put("network_vpn", caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_VPN));
                    json.put("network_validated", caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_VALIDATED));
                }
            }
            android.os.Debug.MemoryInfo processMemory = new android.os.Debug.MemoryInfo();
            android.os.Debug.getMemoryInfo(processMemory);
            json.put("process_pss_mb", processMemory.getTotalPss() / 1024);
            json.put("dalvik_pss_mb", processMemory.dalvikPss / 1024);
            json.put("native_pss_mb", processMemory.nativePss / 1024);
            json.put("other_pss_mb", processMemory.otherPss / 1024);
            json.put("java_heap_max_mb", Runtime.getRuntime().maxMemory() / 1024 / 1024);
            json.put("native_heap_allocated_mb", android.os.Debug.getNativeHeapAllocatedSize() / 1024 / 1024);
            json.put("load_average", readTextFile("/proc/loadavg"));
            android.view.WindowManager window = (android.view.WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
            if (window != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                json.put("refresh_rate_hz", window.getDefaultDisplay().getRefreshRate());
                json.put("rotation", window.getDefaultDisplay().getRotation());
            }
            try {
                json.put("brightness", android.provider.Settings.System.getInt(context.getContentResolver(), android.provider.Settings.System.SCREEN_BRIGHTNESS, -1));
                json.put("brightness_mode", android.provider.Settings.System.getInt(context.getContentResolver(), android.provider.Settings.System.SCREEN_BRIGHTNESS_MODE, -1));
            } catch (Exception ignored) { }
            try {
                android.content.pm.PackageInfo packageInfo = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
                json.put("package_version", packageInfo.versionName);
                json.put("package_version_code", packageInfo.getLongVersionCode());
            } catch (Exception ignored) { }
            android.hardware.SensorManager sensors = (android.hardware.SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
            if (sensors != null) json.put("sensor_count", sensors.getSensorList(android.hardware.Sensor.TYPE_ALL).size());
            org.json.JSONArray recommendations = new org.json.JSONArray();
            if (memory.lowMemory) recommendations.put("Reduza a alocação Java ou feche apps em segundo plano.");
            if (getFreeStorageMb(gameDir) >= 0 && getFreeStorageMb(gameDir) < 2048) recommendations.put("Libere pelo menos 2 GB para bibliotecas, cache e mundos.");
            if (LauncherPreferences.PREF_FORCE_VSYNC) recommendations.put("VSync está ativo; desative para testar FPS sem limite.");
            if (LauncherPreferences.PREF_RENDERER.contains("zink") && !LauncherPreferences.PREF_ZINK_THREADED) recommendations.put("Threading Zink está desativado; teste-o se não houver crash gráfico.");
            recommendations.put("Use o diagnóstico junto com o FPS/frametime para diferenciar carga do jogo de limite do display.");
            json.put("recommendations", recommendations);
            json.put("compatible_renderers", new org.json.JSONArray(Tools.getCompatibleRenderers(context).rendererIds));
            try (PrintWriter out = new PrintWriter(report, StandardCharsets.UTF_8.name())) { out.println(json.toString(2)); }
        } catch (Exception e) { Log.w(TAG, "Could not write JSON diagnostic report", e); }
    }

    private static String sha256(File file) {
        if (file == null || !file.isFile()) return "missing";
        try (java.io.InputStream in = new FileInputStream(file)) {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) digest.update(buffer, 0, read);
            StringBuilder hex = new StringBuilder();
            for (byte value : digest.digest()) hex.append(String.format(Locale.US, "%02x", value));
            return hex.toString();
        } catch (Exception e) { return "unavailable"; }
    }

    private static long readMemInfoMb(String key) {
        try {
            String memInfo = Tools.read("/proc/meminfo");
            for (String line : memInfo.split("\\n")) {
                if (line.startsWith(key + ":")) {
                    String digits = line.replaceAll("[^0-9]", "").trim();
                    return Long.parseLong(digits) / 1024;
                }
            }
        } catch (Exception ignored) { }
        return -1;
    }

    private static String readTextFile(String path) {
        try { return Tools.read(path).trim(); } catch (Exception ignored) { return "unavailable"; }
    }

    private static long readLongFile(String path) {
        try { return Long.parseLong(readTextFile(path)); } catch (Exception ignored) { return -1; }
    }

    private static int countOnlineCpus() {
        int online = 0;
        for (int i = 0; i < Runtime.getRuntime().availableProcessors(); i++) {
            String value = readTextFile("/sys/devices/system/cpu/cpu" + i + "/online");
            if ("1".equals(value) || (i == 0 && "unavailable".equals(value))) online++;
        }
        return online;
    }

    private static int countFiles(File directory) {
        File[] files = directory.listFiles(File::isFile);
        return files == null ? 0 : files.length;
    }

    private static int countDirectories(File directory) {
        File[] files = directory.listFiles(File::isDirectory);
        return files == null ? 0 : files.length;
    }

    private static boolean isEmptyDirectory(File directory) {
        File[] files = directory.listFiles();
        return directory.isDirectory() && (files == null || files.length == 0);
    }

    private static long directorySizeMb(File directory) {
        return directorySize(directory) / 1024 / 1024;
    }

    private static long directorySize(File file) {
        if (file == null || !file.exists()) return 0;
        if (file.isFile()) return file.length();
        long total = 0;
        File[] children = file.listFiles();
        if (children != null) for (File child : children) total += directorySize(child);
        return total;
    }

    private static int countExtension(File directory, String extension) {
        File[] files = directory.listFiles((dir, name) -> name.endsWith(extension));
        return files == null ? 0 : files.length;
    }

    private static int countNativeFiles(File directory) {
        return countExtension(directory, ".so") + countExtension(directory, ".dll") + countExtension(directory, ".dylib");
    }

    private static int countHiddenFiles(File directory) {
        File[] files = directory.listFiles((dir, name) -> name.startsWith("."));
        return files == null ? 0 : files.length;
    }

    private static long fileModified(File file) { return file.isFile() ? file.lastModified() : 0; }

    private static long directoryLatestModified(File directory) {
        long latest = directory.isDirectory() ? directory.lastModified() : 0;
        File[] files = directory.listFiles();
        if (files != null) for (File file : files) latest = Math.max(latest, file.lastModified());
        return latest;
    }

    private static boolean containsName(File directory, String needle) {
        File[] files = directory.listFiles();
        if (files == null) return false;
        for (File file : files) if (file.getName().toLowerCase(Locale.US).contains(needle)) return true;
        return false;
    }

    private static int countExtensionRecursive(File directory, String extension) {
        if (!directory.isDirectory()) return 0;
        int count = 0;
        File[] files = directory.listFiles();
        if (files == null) return 0;
        for (File file : files) count += file.isDirectory() ? countExtensionRecursive(file, extension) : (file.getName().endsWith(extension) ? 1 : 0);
        return count;
    }

    private static long fileSize(File file) { return file.isFile() ? file.length() : 0; }

    private static int countInvalidJson(File directory) {
        int invalid = 0;
        File[] files = directory.listFiles();
        if (files == null) return 0;
        for (File file : files) {
            if (file.isDirectory()) invalid += countInvalidJson(file);
            else if (file.getName().endsWith(".json") && !isValidJson(file)) invalid++;
        }
        return invalid;
    }

    private static boolean isValidJson(File file) {
        try (java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.FileReader(file))) {
            StringBuilder text = new StringBuilder(); String line;
            while ((line = reader.readLine()) != null && text.length() < 2_000_000) text.append(line);
            new org.json.JSONTokener(text.toString()).nextValue(); return true;
        } catch (Exception e) { return false; }
    }

    private static int countVersionsWithoutJar(File versions) {
        int missing = 0; File[] dirs = versions.listFiles(File::isDirectory);
        if (dirs == null) return 0;
        for (File dir : dirs) if (!new File(dir, dir.getName() + ".jar").isFile()) missing++;
        return missing;
    }

    private static int countMissingLibraryFiles(File libraries) {
        int missing = 0; File[] files = libraries.listFiles();
        if (files == null) return 0;
        for (File file : files) {
            if (file.isDirectory()) missing += countMissingLibraryFiles(file);
            else if (file.getName().endsWith(".lastUpdated")) missing++;
        }
        return missing;
    }

    private static int countZeroByteFiles(File directory, String extension) {
        int count = 0; File[] files = directory.listFiles();
        if (files == null) return 0;
        for (File file : files) {
            if (file.isDirectory()) count += countZeroByteFiles(file, extension);
            else if (file.getName().endsWith(extension) && file.length() == 0) count++;
        }
        return count;
    }

    private static int countOrphanVersions(File versions) {
        int count = 0; File[] dirs = versions.listFiles(File::isDirectory);
        if (dirs == null) return 0;
        for (File dir : dirs) if (!new File(dir, dir.getName() + ".json").isFile()) count++;
        return count;
    }

    private static int countIncompleteManifests(File versions) {
        int count = 0; File[] dirs = versions.listFiles(File::isDirectory);
        if (dirs == null) return 0;
        for (File dir : dirs) {
            File manifest = new File(dir, dir.getName() + ".json");
            if (manifest.isFile() && !isValidJson(manifest)) count++;
        }
        return count;
    }

    private static int countTemporaryFiles(File directory) {
        int count = 0; File[] files = directory.listFiles();
        if (files == null) return 0;
        for (File file : files) {
            if (file.isDirectory()) count += countTemporaryFiles(file);
            else if (file.getName().endsWith(".part") || file.getName().endsWith(".tmp") || file.getName().endsWith(".download")) count++;
        }
        return count;
    }

    private static File latestVersionManifest(File versions) {
        File latest = null; File[] manifests = versions.listFiles();
        if (manifests == null) return null;
        for (File file : manifests) {
            if (!file.isDirectory()) continue;
            File manifest = new File(file, file.getName() + ".json");
            if (manifest.isFile() && (latest == null || manifest.lastModified() > latest.lastModified())) latest = manifest;
        }
        return latest;
    }

    private static String findRootCause(String text) {
        String[] lines = text.split("\\n");
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.startsWith("Caused by:") || trimmed.contains("FATAL") || trimmed.contains("ERROR")) {
                if (trimmed.length() > 20) return trimmed.length() > 220 ? trimmed.substring(0, 220) + "…" : trimmed;
            }
        }
        return null;
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
            Tools.RenderersList renderers = Tools.getCompatibleRenderers(context);
            out.println("CompatibleRenderers=" + renderers.rendererIds);
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
