package net.kdt.pojavlaunch.fragments;

import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.widget.ExpandableListAdapter;

import androidx.annotation.NonNull;

import net.kdt.pojavlaunch.JavaGUILauncherActivity;
import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.modloaders.ForgeOptiFineDownloadTask;
import net.kdt.pojavlaunch.modloaders.ForgeUtils;
import net.kdt.pojavlaunch.modloaders.ForgeVersionListAdapter;
import net.kdt.pojavlaunch.modloaders.ModloaderListenerProxy;

import java.io.File;
import java.io.IOException;
import java.util.List;

/** One selection, two downloads, then Forge installer followed automatically by OptiFine. */
public class ForgeOptiFineInstallFragment extends ModVersionListFragment<List<String>> {
    public static final String TAG = "ForgeOptiFineInstallFragment";

    public ForgeOptiFineInstallFragment() { super(TAG); }

    @Override public void onAttach(@NonNull Context context) {
        super.onAttach(context);
    }

    @Override public int getTitleText() { return R.string.mikael_forge_optifine_title; }
    @Override public int getNoDataMsg() { return R.string.forge_dl_no_installer; }
    @Override public List<String> loadVersionList() throws IOException { return ForgeUtils.downloadForgeVersions(); }
    @Override public ExpandableListAdapter createAdapter(List<String> versions, LayoutInflater inflater) {
        return new ForgeVersionListAdapter(versions, inflater);
    }
    @Override public Runnable createDownloadTask(Object selectedVersion, ModloaderListenerProxy proxy) {
        return new ForgeOptiFineDownloadTask((String) selectedVersion, proxy);
    }
    @Override public void onDownloadFinished(Context context, File downloadedFile) {
        ForgeOptiFineDownloadTask.CombinedInstallFile files =
                (ForgeOptiFineDownloadTask.CombinedInstallFile) downloadedFile;
        Intent intent = new Intent(context, JavaGUILauncherActivity.class);
        ForgeUtils.addAutoInstallArgs(intent, files.forge, true);
        intent.putExtra("installOptiFineAfter", files.optiFine.getAbsolutePath());
        context.startActivity(intent);
    }
}
