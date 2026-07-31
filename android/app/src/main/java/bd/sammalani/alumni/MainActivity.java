package bd.sammalani.alumni;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.PermissionRequest;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ProgressBar;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.core.splashscreen.SplashScreen;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Single-activity WebView shell for the Sammalani Alumni web app.
 *
 * <p>Every screen lives in the web layer; this activity is responsible for:
 * <ul>
 *   <li>Configuring the WebView for a native-feeling experience</li>
 *   <li>Routing URLs (same-origin stays in WebView, external opens in browser)</li>
 *   <li>File chooser for profile-photo upload (gallery + camera)</li>
 *   <li>Runtime camera permission flow</li>
 *   <li>Back-navigation that follows web history before exiting</li>
 *   <li>Offline detection with a bilingual error page</li>
 *   <li>Pull-to-refresh</li>
 * </ul>
 */
public class MainActivity extends AppCompatActivity {

    private static final int REQ_FILE_CHOOSER = 1001;
    private static final int REQ_CAMERA_PERM  = 1002;

    // ── Views ──────────────────────────────────────────────────────────────

    private WebView           webView;
    private SwipeRefreshLayout swipeRefresh;
    private ProgressBar       progressBar;

    // ── File-chooser state ────────────────────────────────────────────────

    /** Active callback — must be resolved (even with null) before the next one. */
    private ValueCallback<Uri[]> fileCallback;

    /**
     * URI written by the camera before the photo is taken.
     * Resolved in onActivityResult if the user completed the capture.
     */
    private Uri cameraUri;

    /**
     * Stored when the file chooser fires before camera permission is granted.
     * Resumed in onRequestPermissionsResult.
     */
    private ValueCallback<Uri[]> pendingCallback;

    // ══════════════════════════════════════════════════════════════════════
    // Lifecycle
    // ══════════════════════════════════════════════════════════════════════

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // installSplashScreen must be called before super.onCreate.
        SplashScreen.installSplashScreen(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        webView      = findViewById(R.id.webView);
        swipeRefresh = findViewById(R.id.swipeRefresh);
        progressBar  = findViewById(R.id.progressBar);

        setupWebView();
        setupSwipeRefresh();
        setupBackHandler();

        if (savedInstanceState != null) {
            // Restore scroll position and history across configuration changes.
            webView.restoreState(savedInstanceState);
        } else {
            loadApp();
        }
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle out) {
        super.onSaveInstanceState(out);
        webView.saveState(out);
    }

    @Override
    protected void onResume() {
        super.onResume();
        webView.onResume();       // resume JS timers, animations
    }

    @Override
    protected void onPause() {
        super.onPause();
        webView.onPause();        // pause JS timers to save battery
        CookieManager.getInstance().flush();
    }

    @Override
    protected void onDestroy() {
        webView.stopLoading();
        webView.destroy();        // release native resources
        super.onDestroy();
    }

    // ══════════════════════════════════════════════════════════════════════
    // WebView configuration
    // ══════════════════════════════════════════════════════════════════════

    @SuppressLint("SetJavaScriptEnabled")
    private void setupWebView() {
        WebSettings s = webView.getSettings();

        // Core
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);      // localStorage — JWT tokens live here

        // File access — needed for the profile-photo upload flow
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(true);

        // Viewport — let the web app control its own layout; never zoom
        s.setUseWideViewPort(true);
        s.setLoadWithOverviewMode(true);
        s.setSupportZoom(false);
        s.setBuiltInZoomControls(false);
        s.setDisplayZoomControls(false);

        // Fix text size — ignore system accessibility scaling so the web
        // app's own responsive units work correctly
        s.setTextZoom(100);

        // Cache — use network when available, fall back to cache when offline
        s.setCacheMode(WebSettings.LOAD_DEFAULT);

        // Mixed content — never downgrade HTTPS pages to HTTP sub-resources
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);

        // Media — allow autoplay for any future notification sounds etc.
        s.setMediaPlaybackRequiresUserGesture(false);

        // Cookies
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);

        // Handlers
        webView.setWebViewClient(new AppWebViewClient());
        webView.setWebChromeClient(new AppWebChromeClient());
        webView.setScrollBarStyle(View.SCROLLBARS_INSIDE_OVERLAY);

        // Remote debugging in Chrome (chrome://inspect) for debug builds only
        if (BuildConfig.DEBUG) {
            WebView.setWebContentsDebuggingEnabled(true);
        }
    }

    private void setupSwipeRefresh() {
        swipeRefresh.setColorSchemeResources(R.color.brand_600);
        swipeRefresh.setOnRefreshListener(() -> {
            if (isNetworkAvailable()) {
                webView.reload();
            } else {
                swipeRefresh.setRefreshing(false);
                showOfflinePage();
            }
        });
    }

    private void setupBackHandler() {
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (webView.canGoBack()) {
                    webView.goBack();
                } else {
                    finish();
                }
            }
        });
    }

    // ══════════════════════════════════════════════════════════════════════
    // Navigation
    // ══════════════════════════════════════════════════════════════════════

    private void loadApp() {
        if (isNetworkAvailable()) {
            webView.loadUrl(BuildConfig.WEB_APP_URL);
        } else {
            showOfflinePage();
        }
    }

    private void showOfflinePage() {
        // loadDataWithBaseURL lets relative asset paths and localStorage work
        // even on the error page.
        webView.loadDataWithBaseURL(
                BuildConfig.WEB_APP_URL, OFFLINE_HTML, "text/html", "UTF-8", null);
    }

    @SuppressWarnings("deprecation")
    private boolean isNetworkAvailable() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        if (cm == null) return false;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Network net = cm.getActiveNetwork();
            if (net == null) return false;
            NetworkCapabilities caps = cm.getNetworkCapabilities(net);
            return caps != null && (
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)     ||
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET));
        } else {
            NetworkInfo info = cm.getActiveNetworkInfo();
            return info != null && info.isConnected();
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // Profile-photo upload — file chooser + camera
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Launches an Intent chooser that offers both the system camera (full-res)
     * and the gallery / file picker. The camera option requires CAMERA permission
     * and a FileProvider URI on API 24+; without it the chooser still works but
     * shows only the gallery.
     */
    private void openFileChooser(ValueCallback<Uri[]> callback) {
        // Dispose any orphaned callback from a previous chooser that was
        // abandoned without a result (e.g. dismissed by pressing back).
        if (fileCallback != null) {
            fileCallback.onReceiveValue(null);
        }
        fileCallback = callback;

        // Camera intent — only include if we have permission and a writable URI
        Intent cameraIntent = buildCameraIntent();

        // Gallery / content picker intent
        Intent galleryIntent = new Intent(Intent.ACTION_GET_CONTENT);
        galleryIntent.setType("image/*");
        galleryIntent.addCategory(Intent.CATEGORY_OPENABLE);

        // Combine into a single chooser dialog
        Intent chooser;
        if (cameraIntent != null) {
            chooser = Intent.createChooser(galleryIntent, getString(R.string.choose_photo));
            chooser.putExtra(Intent.EXTRA_INITIAL_INTENTS, new Intent[]{cameraIntent});
        } else {
            chooser = Intent.createChooser(galleryIntent, getString(R.string.choose_photo));
        }

        //noinspection deprecation — ActivityResultLauncher requires a contract
        // defined at construction time; here we need the accept-types from the
        // web's <input> element, which arrive only at call time.
        startActivityForResult(chooser, REQ_FILE_CHOOSER);
    }

    private Intent buildCameraIntent() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            return null;
        }
        try {
            File photoFile = createTempImageFile();
            cameraUri = FileProvider.getUriForFile(
                    this, getPackageName() + ".fileprovider", photoFile);
            Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            intent.putExtra(MediaStore.EXTRA_OUTPUT, cameraUri);
            intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            return intent;
        } catch (IOException e) {
            cameraUri = null;
            return null;
        }
    }

    private File createTempImageFile() throws IOException {
        String ts = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        // getCacheDir() — no storage permission needed; the directory is private
        // to the app and cleaned up automatically by the OS.
        return File.createTempFile("photo_" + ts, ".jpg", getCacheDir());
    }

    @Override
    @SuppressWarnings("deprecation")
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_FILE_CHOOSER || fileCallback == null) return;

        Uri[] results = null;
        if (resultCode == Activity.RESULT_OK) {
            if (data != null && data.getData() != null) {
                // User picked from gallery
                results = new Uri[]{data.getData()};
            } else if (cameraUri != null) {
                // User completed the camera capture
                results = new Uri[]{cameraUri};
            }
        }

        fileCallback.onReceiveValue(results);
        fileCallback = null;
        cameraUri    = null;
    }

    // ══════════════════════════════════════════════════════════════════════
    // Runtime permissions
    // ══════════════════════════════════════════════════════════════════════

    private boolean hasCameraPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED;
    }

    @Override
    public void onRequestPermissionsResult(int code,
                                           @NonNull String[] permissions,
                                           @NonNull int[] results) {
        super.onRequestPermissionsResult(code, permissions, results);
        if (code == REQ_CAMERA_PERM && pendingCallback != null) {
            // Whether granted or denied we open the chooser — denied just means
            // the camera option will not appear.
            openFileChooser(pendingCallback);
            pendingCallback = null;
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // WebViewClient — URL routing and error handling
    // ══════════════════════════════════════════════════════════════════════

    private class AppWebViewClient extends WebViewClient {

        /**
         * API 24+ URL intercept. Same-origin requests stay in WebView; any
         * external link (tel:, mailto:, external sites) opens the system app.
         */
        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            return handleUrl(request.getUrl());
        }

        /** API 21-23 fallback. */
        @Override
        @SuppressWarnings("deprecation")
        public boolean shouldOverrideUrlLoading(WebView view, String url) {
            return handleUrl(Uri.parse(url));
        }

        private boolean handleUrl(Uri uri) {
            String scheme = uri.getScheme();
            // Let the OS handle tel: and mailto: links natively
            if ("tel".equals(scheme) || "mailto".equals(scheme)) {
                try { startActivity(new Intent(Intent.ACTION_VIEW, uri)); } catch (Exception ignored) {}
                return true;
            }
            // Keep same-host navigation inside WebView
            String appHost = Uri.parse(BuildConfig.WEB_APP_URL).getHost();
            if (uri.getHost() != null && uri.getHost().equals(appHost)) return false;
            // Everything else opens in the system browser
            try { startActivity(new Intent(Intent.ACTION_VIEW, uri)); } catch (Exception ignored) {}
            return true;
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            super.onPageFinished(view, url);
            swipeRefresh.setRefreshing(false);
            CookieManager.getInstance().flush();
        }

        /** API 23+ error handler. */
        @Override
        public void onReceivedError(WebView view, WebResourceRequest request,
                                    WebResourceError error) {
            if (request.isForMainFrame()) {
                showOfflinePage();
            }
        }

        /** API 21-22 error handler. */
        @Override
        @SuppressWarnings("deprecation")
        public void onReceivedError(WebView view, int errorCode,
                                    String description, String failingUrl) {
            showOfflinePage();
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // WebChromeClient — progress bar, file chooser, web permissions
    // ══════════════════════════════════════════════════════════════════════

    private class AppWebChromeClient extends WebChromeClient {

        @Override
        public void onProgressChanged(WebView view, int progress) {
            progressBar.setProgress(progress);
            progressBar.setVisibility(progress < 100 ? View.VISIBLE : View.GONE);
            if (progress == 100) {
                swipeRefresh.setRefreshing(false);
            }
        }

        /**
         * Triggered when the web app's {@code <input type="file">} is tapped —
         * i.e. when the user presses the camera button on the Profile screen.
         */
        @Override
        public boolean onShowFileChooser(WebView view,
                                         ValueCallback<Uri[]> callback,
                                         FileChooserParams params) {
            if (!hasCameraPermission()) {
                // Ask for permission first; resume in onRequestPermissionsResult.
                pendingCallback = callback;
                ActivityCompat.requestPermissions(
                        MainActivity.this,
                        new String[]{Manifest.permission.CAMERA},
                        REQ_CAMERA_PERM);
            } else {
                openFileChooser(callback);
            }
            return true;
        }

        /**
         * Grants camera / microphone access to the web content when it asks via
         * the Web permissions API (navigator.mediaDevices). This is separate from
         * the Android runtime permission handled above.
         */
        @Override
        public void onPermissionRequest(PermissionRequest request) {
            request.grant(request.getResources());
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // Offline error page (bilingual — English + Bengali)
    // ══════════════════════════════════════════════════════════════════════

    private static final String OFFLINE_HTML =
        "<!DOCTYPE html><html><head>" +
        "<meta charset='UTF-8'>" +
        "<meta name='viewport' content='width=device-width,initial-scale=1,viewport-fit=cover'>" +
        "<style>" +
        "*{box-sizing:border-box;margin:0;padding:0}" +
        "body{font-family:sans-serif;background:#faf8f4;color:#334155;" +
        "display:flex;flex-direction:column;align-items:center;" +
        "justify-content:center;min-height:100vh;padding:32px;text-align:center}" +
        ".icon{font-size:72px;margin-bottom:20px}" +
        "h1{font-size:20px;font-weight:800;margin-bottom:6px;color:#1e293b}" +
        ".bn{font-size:17px;color:#475569;margin-bottom:6px}" +
        "p{font-size:14px;color:#64748b;margin-bottom:28px}" +
        "button{padding:14px 36px;background:#1f6b4a;color:#fff;border:none;" +
        "border-radius:12px;font-size:16px;font-weight:700;cursor:pointer;" +
        "width:100%;max-width:280px}" +
        "button:active{opacity:.85}" +
        "</style></head><body>" +
        "<div class='icon'>📶</div>" +
        "<h1>No Internet Connection</h1>" +
        "<div class='bn'>ইন্টারনেট সংযোগ নেই</div>" +
        "<p>Please check your network and try again.<br>" +
        "নেটওয়ার্ক পরীক্ষা করে আবার চেষ্টা করুন।</p>" +
        "<button onclick='window.location.href=\"" + BuildConfig.WEB_APP_URL + "\"'>Try Again &nbsp;·&nbsp; আবার চেষ্টা করুন</button>" +
        "</body></html>";
}
