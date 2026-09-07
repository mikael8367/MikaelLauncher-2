package net.kdt.pojavlaunch.modloaders.modpacks.models;

import org.jetbrains.annotations.Nullable;

/** Search filters passed to content APIs. */
public class SearchFilters {
    public static final int TYPE_MOD = 0;
    public static final int TYPE_RESOURCE_PACK = 1;
    public static final int TYPE_WORLD = 2;
    public static final int TYPE_MODPACK = 3;

    public boolean isModpack;
    public int contentType = TYPE_MODPACK;
    public String name;
    @Nullable public String mcVersion;

    public void setContentType(int type) {
        contentType = type;
        isModpack = type == TYPE_MODPACK;
    }
}
