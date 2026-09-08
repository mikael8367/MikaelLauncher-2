package net.kdt.pojavlaunch.modloaders.modpacks.models;


import androidx.annotation.NonNull;

import java.util.Arrays;

public class ModDetail extends ModItem {
    /* A cheap way to map from the front facing name to the underlying id */
    public String[] versionNames;
    public String [] mcVersionNames;
    public String[] versionIds;
    public String[] versionUrls;
    /* SHA 1 hashes, null if a hash is unavailable */
    public String[] versionHashes;
    /** Required dependency files per selected version; optional dependencies are excluded. */
    public String[][] dependencyUrls;
    public String[][] dependencyHashes;
    public String[][] dependencyNames;
    public ModDetail(ModItem item, String[] versionNames, String[] mcVersionNames, String[] versionUrls, String[] hashes, String[] versionIds) {
        super(item.apiSource, item.contentType, item.id, item.title, item.description, item.imageUrl);
        this.versionNames = versionNames;
        this.mcVersionNames = mcVersionNames;
        this.versionUrls = versionUrls;
        this.versionHashes = hashes;
        this.versionIds = versionIds;
        this.dependencyUrls = new String[versionUrls.length][];
        this.dependencyHashes = new String[versionUrls.length][];
        this.dependencyNames = new String[versionUrls.length][];

        // Add the mc version to the version model
        for (int i=0; i<versionNames.length; i++){
            if (mcVersionNames[i] != null && !mcVersionNames[i].isEmpty()
                    && !versionNames[i].contains(mcVersionNames[i]))
                versionNames[i] += " - " + mcVersionNames[i];
        }
    }

    public void setRequiredDependencies(int version, String[] urls, String[] hashes, String[] names) {
        if (version < 0 || version >= versionUrls.length) return;
        dependencyUrls[version] = urls;
        dependencyHashes[version] = hashes;
        dependencyNames[version] = names;
    }

    @NonNull
    @Override
    public String toString() {
        return "ModDetail{" +
                "versionNames=" + Arrays.toString(versionNames) +
                ", mcVersionNames=" + Arrays.toString(mcVersionNames) +
                ", versionIds=" + Arrays.toString(versionUrls) +
                ", id='" + id + '\'' +
                ", title='" + title + '\'' +
                ", description='" + description + '\'' +
                ", imageUrl='" + imageUrl + '\'' +
                ", apiSource=" + apiSource +
                ", isModpack=" + isModpack +
                '}';
    }
}
