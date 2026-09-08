package net.kdt.pojavlaunch.modloaders.modpacks.api;

import android.util.Log;
import android.net.Uri;

import androidx.annotation.NonNull;

import net.kdt.pojavlaunch.PojavApplication;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.modloaders.modpacks.models.Constants;
import net.kdt.pojavlaunch.modloaders.modpacks.models.ModDetail;
import net.kdt.pojavlaunch.modloaders.modpacks.models.ModItem;
import net.kdt.pojavlaunch.modloaders.modpacks.models.SearchFilters;
import net.kdt.pojavlaunch.modloaders.modpacks.models.SearchResult;
import net.kdt.pojavlaunch.utils.DownloadUtils;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;
import net.kdt.pojavlaunch.value.launcherprofiles.LauncherProfiles;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Group all apis under the same umbrella, as another layer of abstraction
 */
public class CommonApi implements ModpackApi {

    private final ModpackApi mCurseforgeApi;
    private final ModpackApi mModrinthApi;
    private final ModpackApi[] mModpackApis;
    private final ConcurrentHashMap<String, ModDetail> mDetailCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, DependencyBundle> mDependencyCache = new ConcurrentHashMap<>();

    private static class DependencyBundle {
        final String[] urls, hashes, names;
        DependencyBundle(String[] urls, String[] hashes, String[] names) {
            this.urls = urls; this.hashes = hashes; this.names = names;
        }
    }

    public CommonApi(String curseforgeApiKey) {
        mCurseforgeApi = new CurseforgeApi(curseforgeApiKey);
        mModrinthApi = new ModrinthApi();
        mModpackApis = new ModpackApi[]{mModrinthApi, mCurseforgeApi};
    }

    @Override
    public SearchResult searchMod(SearchFilters searchFilters, SearchResult previousPageResult) {
        CommonApiSearchResult commonApiSearchResult = (CommonApiSearchResult) previousPageResult;
        // If there are no previous page results, create a new array. Otherwise, use the one from the previous page
        SearchResult[] results = commonApiSearchResult == null ?
                new SearchResult[mModpackApis.length] : commonApiSearchResult.searchResults;

        int totalSize = 0;

        Future<?>[] futures = new Future<?>[mModpackApis.length];
        for(int i = 0; i < mModpackApis.length; i++) {
            // If there is an array and its length is zero, this means that we've exhausted the results for this
            // search query and we don't need to actually do the search
            if(results[i] != null && results[i].results.length == 0) continue;
            // If the previous page result is not null (aka the arrays aren't fresh)
            // and the previous result is null, it means that na error has occured on the previous
            // page. We lost contingency anyway, so don't bother requesting.
            if(previousPageResult != null && results[i] == null) continue;
            futures[i] = PojavApplication.sExecutorService.submit(new ApiDownloadTask(i, searchFilters,
                    results[i]));
        }

        if(Thread.interrupted()) {
            cancelAllFutures(futures);
            return null;
        }
        boolean hasSuccessful = false;
        // Count up all the results
        for(int i = 0; i < mModpackApis.length; i++) {
            Future<?> future = futures[i];
            if(future == null) continue;
            try {
                SearchResult searchResult = results[i] = (SearchResult) future.get();
                if(searchResult != null) hasSuccessful = true;
                else continue;
                totalSize += searchResult.totalResultCount;
            }catch (Exception e) {
                cancelAllFutures(futures);
                e.printStackTrace();
                return null;
            }
        }
        if(!hasSuccessful) {
            return null;
        }
        // Then build an array with all the mods
        ArrayList<ModItem[]> filteredResults = new ArrayList<>(results.length);

        // Sanitize returned values
        for(SearchResult result : results) {
            if(result == null) continue;
            ModItem[] searchResults = result.results;
            // If the length is zero, we don't need to perform needless copies
            if(searchResults.length == 0) continue;
            filteredResults.add(searchResults);
        }
        filteredResults.trimToSize();
        if(Thread.interrupted()) return null;

        ModItem[] concatenatedItems = buildFusedResponse(filteredResults);
        if(Thread.interrupted()) return null;
        // Recycle or create new search result
        if(commonApiSearchResult == null) commonApiSearchResult = new CommonApiSearchResult();
        commonApiSearchResult.searchResults = results;
        commonApiSearchResult.totalResultCount = totalSize;
        commonApiSearchResult.results = concatenatedItems;
        return commonApiSearchResult;
    }

    @Override
    public ModDetail getModDetails(ModItem item) {
        Log.i("CommonApi", "Invoking getModDetails on item.apiSource="+item.apiSource +" item.title="+item.title);
        String cacheKey = item.apiSource + ":" + item.id;
        ModDetail cached = mDetailCache.get(cacheKey);
        if (cached != null) return cached;
        ModDetail loaded = getModpackApi(item.apiSource).getModDetails(item);
        if (loaded != null) mDetailCache.put(cacheKey, loaded);
        return loaded;
    }

    @Override
    public ModLoader installMod(ModDetail modDetail, int selectedVersion) throws IOException {
        if (!modDetail.isModpack) {
            installIndividualContent(modDetail, selectedVersion);
            return null;
        }
        return getModpackApi(modDetail.apiSource).installMod(modDetail, selectedVersion);
    }

    @Override
    public void resolveRequiredDependencies(ModDetail detail, int selectedVersion) throws IOException {
        if (detail.versionIds == null || selectedVersion < 0 || selectedVersion >= detail.versionIds.length) return;
        if (detail.dependencyUrls[selectedVersion] != null) return;
        String cacheKey = detail.apiSource + ":" + detail.versionIds[selectedVersion];
        DependencyBundle cached = mDependencyCache.get(cacheKey);
        if (cached != null) {
            detail.setRequiredDependencies(selectedVersion, cached.urls, cached.hashes, cached.names);
            return;
        }
        getModpackApi(detail.apiSource).resolveRequiredDependencies(detail, selectedVersion);
        String[] urls = detail.dependencyUrls[selectedVersion];
        if (urls != null) {
            DependencyBundle resolved = new DependencyBundle(urls,
                    detail.dependencyHashes[selectedVersion], detail.dependencyNames[selectedVersion]);
            mDependencyCache.putIfAbsent(cacheKey, resolved);
        }
    }

    private void installIndividualContent(ModDetail detail, int selectedVersion) throws IOException {
        resolveRequiredDependencies(detail, selectedVersion);
        LauncherProfiles.load();
            String targetDir = LauncherPreferences.DEFAULT_PREF.getString("curseforge_target_game_dir", null);
            File base = targetDir == null || targetDir.isEmpty()
                    ? Tools.getGameDirPath(LauncherProfiles.getCurrentProfile())
                    : new File(targetDir);
            if (!base.exists() && !base.mkdirs()) throw new IOException("Unable to create selected profile directory");
        String folder;
        switch (detail.contentType) {
            case SearchFilters.TYPE_RESOURCE_PACK: folder = "resourcepacks"; break;
            case SearchFilters.TYPE_WORLD: folder = "mikael-content/worlds"; break;
            case SearchFilters.TYPE_MOD: folder = "mods"; break;
            default: folder = "downloads"; break;
        }
        String url = detail.versionUrls[selectedVersion];
        String name = Uri.parse(url).getLastPathSegment();
        if (name == null || name.isEmpty()) name = detail.id + "-" + selectedVersion + ".zip";
        name = name.replaceAll("[^A-Za-z0-9._-]", "_");
        File destination = new File(new File(base, folder), name);
        byte[] buffer = new byte[8192];
        DownloadUtils.ensureSha1(destination, detail.versionHashes[selectedVersion], () -> {
            DownloadUtils.downloadFileMonitored(url, destination, buffer, (current, total) -> { });
            return null;
        });
        installRequiredDependencies(detail, selectedVersion, base);
    }

    private void installRequiredDependencies(ModDetail detail, int selectedVersion, File base) throws IOException {
        if (detail.dependencyUrls == null || selectedVersion >= detail.dependencyUrls.length
                || detail.dependencyUrls[selectedVersion] == null) return;
        File modsDir = new File(base, "mods");
        if (!modsDir.exists() && !modsDir.mkdirs()) throw new IOException("Não foi possível criar a pasta de mods");
        String[] urls = detail.dependencyUrls[selectedVersion];
        String[] hashes = detail.dependencyHashes[selectedVersion];
        String[] names = detail.dependencyNames[selectedVersion];
        for (int i = 0; i < urls.length; i++) {
            String name = names != null && i < names.length ? names[i] : "dependency-" + i + ".jar";
            name = name.replaceAll("[^A-Za-z0-9._-]", "_");
            File destination = new File(modsDir, name);
            String hash = hashes != null && i < hashes.length ? hashes[i] : null;
            byte[] buffer = new byte[8192];
            final String dependencyUrl = urls[i];
            DownloadUtils.ensureSha1(destination, hash, () -> {
                DownloadUtils.downloadFileMonitored(dependencyUrl, destination, buffer, (current, total) -> { });
                return null;
            });
        }
    }

    private @NonNull ModpackApi getModpackApi(int apiSource) {
        switch (apiSource) {
            case Constants.SOURCE_MODRINTH:
                return mModrinthApi;
            case Constants.SOURCE_CURSEFORGE:
                return mCurseforgeApi;
            default:
                throw new UnsupportedOperationException("Unknown API source: " + apiSource);
        }
    }

    /** Fuse the arrays in a way that's fair for every endpoint */
    private ModItem[] buildFusedResponse(List<ModItem[]> modMatrix){
        int totalSize = 0;

        // Calculate the total size of the merged array
        for (ModItem[] array : modMatrix) {
            totalSize += array.length;
        }

        ModItem[] fusedItems = new ModItem[totalSize];

        int mergedIndex = 0;
        int maxLength = 0;

        // Find the maximum length of arrays
        for (ModItem[] array : modMatrix) {
            if (array.length > maxLength) {
                maxLength = array.length;
            }
        }

        // Populate the merged array
        for (int i = 0; i < maxLength; i++) {
            for (ModItem[] matrix : modMatrix) {
                if (i < matrix.length) {
                    fusedItems[mergedIndex] = matrix[i];
                    mergedIndex++;
                }
            }
        }

        return fusedItems;
    }

    private void cancelAllFutures(Future<?>[] futures) {
        for(Future<?> future : futures) {
            if(future == null) continue;
            future.cancel(true);
        }
    }

    private class ApiDownloadTask implements Callable<SearchResult> {
        private final int mModApi;
        private final SearchFilters mSearchFilters;
        private final SearchResult mPreviousPageResult;

        private ApiDownloadTask(int modApi, SearchFilters searchFilters, SearchResult previousPageResult) {
            this.mModApi = modApi;
            this.mSearchFilters = searchFilters;
            this.mPreviousPageResult = previousPageResult;
        }

        @Override
        public SearchResult call() {
            return mModpackApis[mModApi].searchMod(mSearchFilters, mPreviousPageResult);
        }
    }

    class CommonApiSearchResult extends SearchResult {
        SearchResult[] searchResults = new SearchResult[mModpackApis.length];
    }
}
