package net.kdt.pojavlaunch;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;

import net.kdt.pojavlaunch.multirt.MultiRTUtils;
import net.kdt.pojavlaunch.multirt.Runtime;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Installs the bundled Java runtimes before the first Minecraft launch. */
public final class RuntimeBootstrap {
    private static final String TAG = "RuntimeBootstrap";
    private static final String PREFS = "mikael_runtime_bootstrap";
    private static final String COMPLETE = "complete";
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();

    private static final RuntimeSpec[] RUNTIMES = {
            new RuntimeSpec("java8", "Internal-8"),
            new RuntimeSpec("java17", "Internal-17"),
            new RuntimeSpec("java21", "Internal-21")
    };

    private RuntimeBootstrap() {}

    public interface Callback {
        void onComplete(boolean success, Throwable error);
    }

    public static void ensureInstalled(@NonNull Context context, @NonNull Callback callback) {
        Context appContext = context.getApplicationContext();
        EXECUTOR.execute(() -> {
            try {
                boolean complete = true;
                for (RuntimeSpec spec : RUNTIMES) {
                    Runtime runtime = MultiRTUtils.read(spec.name);
                    if (runtime.javaVersion < spec.minimumVersion) {
                        complete = false;
                        break;
                    }
                }

                SharedPreferences prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
                if (!complete || !prefs.getBoolean(COMPLETE, false)) {
                    installAll(appContext);
                    prefs.edit().putBoolean(COMPLETE, true).apply();
                }
                Tools.runOnUiThread(() -> callback.onComplete(true, null));
            } catch (Throwable error) {
                Log.e(TAG, "Failed to install bundled Java runtimes", error);
                Tools.runOnUiThread(() -> callback.onComplete(false, error));
            }
        });
    }

    private static void installAll(Context context) throws IOException {
        // The bundled bootstrap packages are currently arm64-v8a, matching the supported
        // Android build target. Other ABIs must use the runtime manager's architecture packages.
        if (!isArm64Device()) {
            throw new IOException("Este APK requer um pacote de runtime compatível com a arquitetura do aparelho.");
        }

        for (RuntimeSpec spec : RUNTIMES) {
            Runtime runtime = MultiRTUtils.read(spec.name);
            if (runtime.javaVersion >= spec.minimumVersion) continue;

            String assetRoot = "runtime-bootstrap/" + spec.assetDirectory;
            try {
                MultiRTUtils.installRuntimeNamedBinpack(
                        context.getAssets().open(assetRoot + "/universal.tar.xz"),
                        context.getAssets().open(assetRoot + "/bin-arm64.tar.xz"),
                        spec.name,
                        readAsset(context, assetRoot + "/version")
                );
                MultiRTUtils.postPrepare(spec.name);
            } catch (IOException error) {
                throw new IOException("Falha ao instalar " + spec.name, error);
            }
        }
    }

    private static String readAsset(Context context, String path) throws IOException {
        return Tools.read(context.getAssets().open(path)).trim();
    }

    private static boolean isArm64Device() {
        for (String abi : Build.SUPPORTED_ABIS) {
            if ("arm64-v8a".equals(abi)) return true;
        }
        return false;
    }

    private static final class RuntimeSpec {
        final String assetDirectory;
        final String name;
        final int minimumVersion;

        RuntimeSpec(String assetDirectory, String name) {
            this.assetDirectory = assetDirectory;
            this.name = name;
            this.minimumVersion = Integer.parseInt(name.substring(name.lastIndexOf('-') + 1));
        }
    }
}
