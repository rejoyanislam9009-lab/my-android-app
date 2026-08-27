package com.rejoy.webviewapp;

import android.Manifest;
import android.app.Activity;
import android.app.DownloadManager;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.DownloadListener;
import android.webkit.URLUtil;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

public class MainActivity extends Activity {
    private static final int FILE_CHOOSER_REQUEST = 1001;
    private static final int STORAGE_PERMISSION_REQUEST = 1002;

    private WebView webView;
    private ProgressBar progressBar;
    private SwipeRefreshLayout swipeRefreshLayout;
    private View errorView;
    private TextView errorMessage;
    private ValueCallback<Uri[]> filePathCallback;
    private String pendingDownloadUrl;
    private String pendingDownloadUserAgent;
    private String pendingDownloadContentDisposition;
    private String pendingDownloadMimeType;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        webView = findViewById(R.id.webView);
        progressBar = findViewById(R.id.progressBar);
        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout);
        errorView = findViewById(R.id.errorView);
        errorMessage = findViewById(R.id.errorMessage);
        Button retryButton = findViewById(R.id.retryButton);

        configureWebView();
        configurePullToRefresh();
        retryButton.setOnClickListener(v -> retry());

        if (savedInstanceState == null) {
            if (isConfiguredUrlValid()) {
                webView.loadUrl(BuildConfig.WEBVIEW_URL);
            } else {
                showError(getString(R.string.url_not_configured));
            }
        } else {
            webView.restoreState(savedInstanceState);
        }
    }

    private boolean isConfiguredUrlValid() {
        Uri uri = Uri.parse(BuildConfig.WEBVIEW_URL);
        return "https".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null
                && !"example.com".equalsIgnoreCase(uri.getHost());
    }

    private void configurePullToRefresh() {
        swipeRefreshLayout.setColorSchemeResources(R.color.brand);
        swipeRefreshLayout.setProgressBackgroundColorSchemeResource(R.color.background);
        swipeRefreshLayout.setDistanceToTriggerSync(dpToPx(88));
        swipeRefreshLayout.setOnChildScrollUpCallback(
                (parent, child) -> webView != null && webView.canScrollVertically(-1)
        );
        swipeRefreshLayout.setOnRefreshListener(this::refreshCurrentPage);
    }

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }

    private void refreshCurrentPage() {
        if (!isConfiguredUrlValid()) {
            swipeRefreshLayout.setRefreshing(false);
            showError(getString(R.string.url_not_configured));
            return;
        }

        hideError();
        if (webView.getUrl() == null) {
            webView.loadUrl(BuildConfig.WEBVIEW_URL);
        } else {
            webView.reload();
        }
    }

    private void configureWebView() {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setLoadsImagesAutomatically(true);
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        settings.setSupportMultipleWindows(false);
        settings.setJavaScriptCanOpenWindowsAutomatically(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            settings.setSafeBrowsingEnabled(true);
        }

        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.setAcceptCookie(true);
        cookieManager.setAcceptThirdPartyCookies(webView, true);

        webView.setWebViewClient(new BrowserClient());
        webView.setWebChromeClient(new BrowserChromeClient());
        webView.setDownloadListener(createDownloadListener());
    }

    private DownloadListener createDownloadListener() {
        return (url, userAgent, contentDisposition, mimeType, contentLength) -> {
            pendingDownloadUrl = url;
            pendingDownloadUserAgent = userAgent;
            pendingDownloadContentDisposition = contentDisposition;
            pendingDownloadMimeType = mimeType;

            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P
                    && checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, STORAGE_PERMISSION_REQUEST);
                return;
            }
            enqueueDownload();
        };
    }

    private void enqueueDownload() {
        if (pendingDownloadUrl == null) return;

        try {
            String fileName = URLUtil.guessFileName(
                    pendingDownloadUrl,
                    pendingDownloadContentDisposition,
                    pendingDownloadMimeType
            );

            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(pendingDownloadUrl));
            request.setTitle(fileName);
            request.setDescription(getString(R.string.downloading));
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            request.setAllowedOverMetered(true);
            request.setAllowedOverRoaming(true);

            if (pendingDownloadMimeType != null && !pendingDownloadMimeType.isEmpty()) {
                request.setMimeType(pendingDownloadMimeType);
            }
            if (pendingDownloadUserAgent != null) {
                request.addRequestHeader("User-Agent", pendingDownloadUserAgent);
            }

            String cookies = CookieManager.getInstance().getCookie(pendingDownloadUrl);
            if (cookies != null) {
                request.addRequestHeader("Cookie", cookies);
            }

            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName);
            DownloadManager manager = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
            manager.enqueue(request);
            Toast.makeText(this, R.string.download_started, Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, R.string.download_failed, Toast.LENGTH_LONG).show();
        } finally {
            clearPendingDownload();
        }
    }

    private void clearPendingDownload() {
        pendingDownloadUrl = null;
        pendingDownloadUserAgent = null;
        pendingDownloadContentDisposition = null;
        pendingDownloadMimeType = null;
    }

    private void retry() {
        if (!isConfiguredUrlValid()) {
            showError(getString(R.string.url_not_configured));
            return;
        }
        hideError();
        if (webView.getUrl() == null) {
            webView.loadUrl(BuildConfig.WEBVIEW_URL);
        } else {
            webView.reload();
        }
    }

    private void showError(String message) {
        if (swipeRefreshLayout != null) {
            swipeRefreshLayout.setRefreshing(false);
        }
        errorMessage.setText(message);
        errorView.setVisibility(View.VISIBLE);
        webView.setVisibility(View.INVISIBLE);
        progressBar.setVisibility(View.GONE);
    }

    private void hideError() {
        errorView.setVisibility(View.GONE);
        webView.setVisibility(View.VISIBLE);
    }

    private boolean openExternal(Uri uri) {
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase();
        if ("http".equals(scheme) || "https".equals(scheme)) {
            return false;
        }

        try {
            Intent intent;
            if ("intent".equals(scheme)) {
                intent = Intent.parseUri(uri.toString(), Intent.URI_INTENT_SCHEME);
                if (intent.resolveActivity(getPackageManager()) == null) {
                    String fallback = intent.getStringExtra("browser_fallback_url");
                    if (fallback != null) {
                        webView.loadUrl(fallback);
                        return true;
                    }
                    return true;
                }
            } else {
                intent = new Intent(Intent.ACTION_VIEW, uri);
            }
            startActivity(intent);
            return true;
        } catch (Exception ignored) {
            Toast.makeText(this, R.string.no_app_for_link, Toast.LENGTH_SHORT).show();
            return true;
        }
    }

    private class BrowserClient extends WebViewClient {
        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            return openExternal(request.getUrl());
        }

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, String url) {
            return openExternal(Uri.parse(url));
        }

        @Override
        public void onPageStarted(WebView view, String url, Bitmap favicon) {
            hideError();
            progressBar.setVisibility(View.VISIBLE);
            super.onPageStarted(view, url, favicon);
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            progressBar.setVisibility(View.GONE);
            swipeRefreshLayout.setRefreshing(false);
            super.onPageFinished(view, url);
        }

        @Override
        public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
            if (request.isForMainFrame()) {
                showError(getString(R.string.page_load_failed));
            }
            super.onReceivedError(view, request, error);
        }
    }

    private class BrowserChromeClient extends WebChromeClient {
        @Override
        public void onProgressChanged(WebView view, int newProgress) {
            progressBar.setProgress(newProgress);
            progressBar.setVisibility(newProgress >= 100 ? View.GONE : View.VISIBLE);
            if (newProgress >= 100 && swipeRefreshLayout != null) {
                swipeRefreshLayout.setRefreshing(false);
            }
            super.onProgressChanged(view, newProgress);
        }

        @Override
        public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> filePathCallback,
                                         FileChooserParams fileChooserParams) {
            if (MainActivity.this.filePathCallback != null) {
                MainActivity.this.filePathCallback.onReceiveValue(null);
            }
            MainActivity.this.filePathCallback = filePathCallback;

            try {
                Intent chooserIntent = fileChooserParams.createIntent();
                chooserIntent.addCategory(Intent.CATEGORY_OPENABLE);
                startActivityForResult(chooserIntent, FILE_CHOOSER_REQUEST);
                return true;
            } catch (ActivityNotFoundException e) {
                MainActivity.this.filePathCallback = null;
                Toast.makeText(MainActivity.this, R.string.file_picker_unavailable, Toast.LENGTH_SHORT).show();
                return false;
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == FILE_CHOOSER_REQUEST && filePathCallback != null) {
            Uri[] result = WebChromeClient.FileChooserParams.parseResult(resultCode, data);
            filePathCallback.onReceiveValue(result);
            filePathCallback = null;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == STORAGE_PERMISSION_REQUEST) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                enqueueDownload();
            } else {
                clearPendingDownload();
                Toast.makeText(this, R.string.download_permission_denied, Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        webView.saveState(outState);
        super.onSaveInstanceState(outState);
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        if (filePathCallback != null) {
            filePathCallback.onReceiveValue(null);
            filePathCallback = null;
        }
        if (swipeRefreshLayout != null) {
            swipeRefreshLayout.setRefreshing(false);
        }
        if (webView != null) {
            webView.stopLoading();
            webView.setWebChromeClient(null);
            webView.setWebViewClient(null);
            webView.destroy();
        }
        super.onDestroy();
    }
}
