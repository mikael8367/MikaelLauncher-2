package net.kdt.pojavlaunch;

import android.os.Bundle;
import android.webkit.WebSettings;
import android.webkit.WebView;
import androidx.annotation.Nullable;

/** Offline, read-only update and bug-fix catalog. */
public class UpdatesCatalogActivity extends BaseActivity {
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WebView webView = new WebView(this);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(false);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        webView.setBackgroundColor(0xFF070B16);
        webView.loadUrl("file:///android_asset/mikael_updates.html");
        setTitle("Catálogo de versões");
        setContentView(webView);
    }
}
