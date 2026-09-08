package net.kdt.pojavlaunch.modloaders;

import com.kdt.mcgui.ProgressLayout;
import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.progresskeeper.ProgressKeeper;
import net.kdt.pojavlaunch.utils.DownloadUtils;

import java.io.File;
import java.io.IOException;
import java.util.List;

/** Downloads both installers before starting the chained Forge -> OptiFine flow. */
public class ForgeOptiFineDownloadTask implements Runnable, Tools.DownloaderFeedback {
    private final String forgeVersion;
    private final ModloaderDownloadListener listener;

    public ForgeOptiFineDownloadTask(String forgeVersion, ModloaderDownloadListener listener) {
        this.forgeVersion = forgeVersion;
        this.listener = listener;
    }

    @Override public void run() {
        try {
            String gameVersion = forgeVersion.substring(0, forgeVersion.indexOf('-'));
            File forgeFile = new File(Tools.DIR_CACHE, "forge-installer.jar");
            ProgressKeeper.submitProgress(ProgressLayout.INSTALL_MODPACK, 5, R.string.forge_dl_progress, forgeVersion);
            DownloadUtils.downloadFileMonitored(ForgeUtils.getInstallerUrl(forgeVersion), forgeFile, new byte[8192], this);

            OptiFineUtils.OptiFineVersions versions = OptiFineUtils.downloadOptiFineVersions();
            OptiFineUtils.OptiFineVersion selected = findCompatible(versions, gameVersion);
            if (selected == null) throw new IOException("OptiFine não encontrada para " + gameVersion);
            File optiFineFile = new File(Tools.DIR_CACHE, "optifine-installer.jar");
            String url = OFDownloadPageScraper.run(selected.downloadUrl);
            if (url == null) throw new IOException("Não foi possível obter o download da OptiFine");
            DownloadUtils.downloadFileMonitored(url, optiFineFile, new byte[8192], this);
            listener.onDownloadFinished(new CombinedInstallFile(forgeFile, optiFineFile));
        } catch (Exception e) {
            listener.onDownloadError(e);
        } finally {
            ProgressLayout.clearProgress(ProgressLayout.INSTALL_MODPACK);
        }
    }

    private OptiFineUtils.OptiFineVersion findCompatible(OptiFineUtils.OptiFineVersions all, String gameVersion) {
        if (all == null || all.minecraftVersions == null || all.optifineVersions == null) return null;
        for (int i = 0; i < all.minecraftVersions.size(); i++) {
            if (!gameVersion.equals(all.minecraftVersions.get(i))) continue;
            List<OptiFineUtils.OptiFineVersion> choices = all.optifineVersions.get(i);
            if (choices != null && !choices.isEmpty()) return choices.get(0);
        }
        return null;
    }

    @Override public void updateProgress(int current, int max) {
        int percent = max <= 0 ? 0 : Math.min(100, current * 100 / max);
        ProgressKeeper.submitProgress(ProgressLayout.INSTALL_MODPACK, percent, R.string.forge_dl_progress, forgeVersion);
    }

    public static final class CombinedInstallFile extends File {
        public final File forge;
        public final File optiFine;
        public CombinedInstallFile(File forge, File optiFine) {
            super(forge.getAbsolutePath());
            this.forge = forge;
            this.optiFine = optiFine;
        }
    }
}
