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
import android.net.NetworkRequest;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.MediaStore;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
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
import java.net.HttpURLConnection;
import java.net.URL;
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
 *   <li>Grand Reunion 2027 animated loading overlay</li>
 * </ul>
 */
public class MainActivity extends AppCompatActivity {

    private static final int REQ_FILE_CHOOSER = 1001;
    private static final int REQ_CAMERA_PERM  = 1002;

    // ── Web views ──────────────────────────────────────────────────────────

    private WebView            webView;
    private SwipeRefreshLayout swipeRefresh;
    private ProgressBar        progressBar;

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

    // ── Grand Reunion 2027 loading overlay ────────────────────────────────

    private View splashOverlay;
    private View emblemView;
    private View topDivider;
    private View schoolNameEn;
    private View schoolNameBn;
    private View grandReunionText;
    private View year2027Text;
    private View bottomDivider;
    private View taglineText;
    private View splashSpinner;

    /** Wall-clock ms when the splash was first shown, used to enforce a minimum display time. */
    private long    splashShownAt;
    private boolean splashDismissed  = false;

    /**
     * Set to true the first time onPageFinished fires (whether success or error page).
     * Used by the MAX_SPLASH_MS watchdog to decide whether to force-abort a hanging request.
     */
    private boolean webPageSettled   = false;

    // ── Connectivity / server-retry state ─────────────────────────────────

    /** True while the offline error page is displayed. */
    private boolean isShowingOfflinePage      = false;
    /** True while the server-unreachable error page is displayed. */
    private boolean isShowingServerErrorPage  = false;

    /** Registered when offline; fires when any network becomes available. */
    private ConnectivityManager.NetworkCallback networkCallback;

    /** Drives the periodic server-reachability ping shown during server errors. */
    private final Handler  retryHandler       = new Handler(Looper.getMainLooper());
    private       Runnable serverRetryRunnable;

    /** How often to ping the server while the server-error page is visible. */
    private static final long SERVER_RETRY_MS = 15_000;

    /** Minimum time the splash is shown regardless of how fast the page loads. */
    private static final long MIN_SPLASH_MS = 2600;

    /**
     * Hard upper bound. If onPageFinished has not fired by this point the request is
     * likely hanging (server reachable on the network layer but not responding).
     * We stop the load, show the appropriate error page, and dismiss the overlay.
     */
    private static final long MAX_SPLASH_MS = 7000;

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

        // isRestore = true on configuration changes (rotation, etc.) —
        // skip the splash in that case since the WebView state is already loaded.
        setupSplash(savedInstanceState != null);

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
        stopServerRetry();
        unregisterNetworkCallback();
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

        // Background — must be set explicitly; the default is black on some devices
        // which causes a black flash when the loading overlay fades out before
        // the first WebView frame is painted.
        webView.setBackgroundColor(ContextCompat.getColor(this, R.color.paper));

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
        // Clear any active error state and stop background recovery mechanisms.
        isShowingOfflinePage     = false;
        isShowingServerErrorPage = false;
        stopServerRetry();
        unregisterNetworkCallback();

        if (isNetworkAvailable()) {
            webView.loadUrl(BuildConfig.WEB_APP_URL);
        } else {
            showOfflinePage();
        }
    }

    private void showOfflinePage() {
        isShowingOfflinePage     = true;
        isShowingServerErrorPage = false;
        stopServerRetry();
        webView.loadDataWithBaseURL(
                BuildConfig.WEB_APP_URL, OFFLINE_HTML, "text/html", "UTF-8", null);
        // Watch for connectivity to return so we can auto-reload.
        registerNetworkCallback();
    }

    private void showServerErrorPage() {
        isShowingServerErrorPage = true;
        isShowingOfflinePage     = false;
        unregisterNetworkCallback();
        webView.loadDataWithBaseURL(
                BuildConfig.WEB_APP_URL, SERVER_ERROR_HTML, "text/html", "UTF-8", null);
        // Ping the server periodically and reload as soon as it responds.
        startServerRetry();
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
    // Connectivity recovery — offline auto-reconnect
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Registers a {@link ConnectivityManager.NetworkCallback} that fires when any
     * network with internet capability becomes available.  Called when we enter the
     * offline-error state; triggers an automatic reload when connectivity returns.
     */
    private void registerNetworkCallback() {
        if (networkCallback != null) return; // already registered
        ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        if (cm == null) return;

        networkCallback = new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(Network network) {
                // onAvailable runs on the ConnectivityThread — dispatch to UI thread.
                runOnUiThread(() -> {
                    if (isShowingOfflinePage) {
                        loadApp();
                    }
                });
            }
        };

        NetworkRequest req = new NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build();
        try {
            cm.registerNetworkCallback(req, networkCallback);
        } catch (Exception e) {
            networkCallback = null; // registration failed; fall back to manual retry only
        }
    }

    private void unregisterNetworkCallback() {
        if (networkCallback == null) return;
        ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        if (cm != null) {
            try { cm.unregisterNetworkCallback(networkCallback); } catch (Exception ignored) {}
        }
        networkCallback = null;
    }

    // ══════════════════════════════════════════════════════════════════════
    // Connectivity recovery — server periodic ping
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Schedules a repeating HEAD request to the app URL every {@link #SERVER_RETRY_MS}
     * milliseconds.  When the server responds with a non-5xx status the app is
     * reloaded automatically.  Runs on a daemon thread so it does not block the UI.
     */
    private void startServerRetry() {
        stopServerRetry(); // clear any previous runnable
        serverRetryRunnable = new Runnable() {
            @Override
            public void run() {
                if (!isShowingServerErrorPage) return;
                pingServer();
                retryHandler.postDelayed(this, SERVER_RETRY_MS);
            }
        };
        retryHandler.postDelayed(serverRetryRunnable, SERVER_RETRY_MS);
    }

    private void stopServerRetry() {
        if (serverRetryRunnable != null) {
            retryHandler.removeCallbacks(serverRetryRunnable);
            serverRetryRunnable = null;
        }
    }

    /**
     * Opens a short-timeout HEAD connection to {@link BuildConfig#WEB_APP_URL}.
     * If the server replies (any non-5xx response) we reload the app on the UI thread.
     */
    private void pingServer() {
        new Thread(() -> {
            HttpURLConnection conn = null;
            try {
                conn = (HttpURLConnection) new URL(BuildConfig.WEB_APP_URL).openConnection();
                conn.setRequestMethod("HEAD");
                conn.setConnectTimeout(5_000);
                conn.setReadTimeout(5_000);
                conn.setInstanceFollowRedirects(true);
                int code = conn.getResponseCode();
                if (code < 500) {
                    // Server is back — reload on the main thread.
                    runOnUiThread(() -> {
                        if (isShowingServerErrorPage) loadApp();
                    });
                }
            } catch (Exception ignored) {
                // Still unreachable — next attempt in SERVER_RETRY_MS.
            } finally {
                if (conn != null) conn.disconnect();
            }
        }).start();
    }

    // ══════════════════════════════════════════════════════════════════════
    // Grand Reunion 2027 — loading overlay animation
    // ══════════════════════════════════════════════════════════════════════

    private void setupSplash(boolean isRestore) {
        splashOverlay    = findViewById(R.id.splashOverlay);
        emblemView       = findViewById(R.id.emblemView);
        topDivider       = findViewById(R.id.topDivider);
        schoolNameEn     = findViewById(R.id.schoolNameEn);
        schoolNameBn     = findViewById(R.id.schoolNameBn);
        grandReunionText = findViewById(R.id.grandReunionText);
        year2027Text     = findViewById(R.id.year2027Text);
        bottomDivider    = findViewById(R.id.bottomDivider);
        taglineText      = findViewById(R.id.taglineText);
        splashSpinner    = findViewById(R.id.splashSpinner);

        if (isRestore) {
            // Configuration change — WebView already has its content; skip the splash.
            splashOverlay.setVisibility(View.GONE);
            splashDismissed = true;
            return;
        }

        splashShownAt = SystemClock.elapsedRealtime();
        initSplashViews();
        runSplashAnimation();

        // Watchdog: if the page has not settled after MAX_SPLASH_MS the request
        // is hanging (server unreachable / no response). Stop the load, show the
        // right error page, and dismiss the overlay so the user is never stuck.
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (!webPageSettled) {
                webView.stopLoading();
                if (isNetworkAvailable()) {
                    showServerErrorPage();
                } else {
                    showOfflinePage();
                }
            }
            dismissSplash();
        }, MAX_SPLASH_MS);
    }

    /** Set every animated view to its invisible starting state. */
    private void initSplashViews() {
        float slide = dp(28);

        emblemView.setAlpha(0f);
        emblemView.setScaleX(0.35f);
        emblemView.setScaleY(0.35f);

        topDivider.setAlpha(0f);
        topDivider.setScaleX(0f);

        schoolNameEn.setAlpha(0f);
        schoolNameEn.setTranslationY(slide);

        schoolNameBn.setAlpha(0f);
        schoolNameBn.setTranslationY(slide);

        bottomDivider.setAlpha(0f);
        bottomDivider.setScaleX(0f);

        grandReunionText.setAlpha(0f);
        grandReunionText.setTranslationY(dp(20));

        year2027Text.setAlpha(0f);
        year2027Text.setScaleX(0.55f);
        year2027Text.setScaleY(0.55f);

        taglineText.setAlpha(0f);
        taglineText.setTranslationY(dp(16));

        splashSpinner.setAlpha(0f);
    }

    /**
     * Runs the staggered entrance animation sequence.
     *
     * Timeline (approximate):
     *   0 ms  — emblem scales in with overshoot
     * 320 ms  — top gold divider expands
     * 480 ms  — school name (EN) slides up
     * 580 ms  — school name (BN) slides up
     * 720 ms  — bottom divider expands
     * 840 ms  — "GRAND REUNION" slides up
     * 1050 ms — "2027" scales in with overshoot
     * 1350 ms — tagline fades in
     * 1500 ms — spinner appears
     * 1900 ms — "2027" starts pulsing (repeating scale breathe)
     */
    private void runSplashAnimation() {
        Handler h = new Handler(Looper.getMainLooper());

        // Emblem: scale-in with overshoot bounce
        emblemView.animate()
                .alpha(1f).scaleX(1f).scaleY(1f)
                .setDuration(520)
                .setInterpolator(new OvershootInterpolator(1.6f))
                .start();

        // Top divider: horizontal expand
        h.postDelayed(() ->
                topDivider.animate()
                        .alpha(1f).scaleX(1f)
                        .setDuration(380)
                        .setInterpolator(new DecelerateInterpolator())
                        .start(),
                320);

        // School name (EN)
        h.postDelayed(() -> slideUp(schoolNameEn, 360), 480);

        // School name (BN)
        h.postDelayed(() -> slideUp(schoolNameBn, 360), 580);

        // Bottom divider
        h.postDelayed(() ->
                bottomDivider.animate()
                        .alpha(0.6f).scaleX(1f)
                        .setDuration(360)
                        .setInterpolator(new DecelerateInterpolator())
                        .start(),
                720);

        // "GRAND REUNION"
        h.postDelayed(() -> slideUp(grandReunionText, 400), 840);

        // "2027": scale-in with overshoot — the hero element
        h.postDelayed(() ->
                year2027Text.animate()
                        .alpha(1f).scaleX(1f).scaleY(1f)
                        .setDuration(560)
                        .setInterpolator(new OvershootInterpolator(1.4f))
                        .start(),
                1050);

        // Tagline
        h.postDelayed(() -> slideUp(taglineText, 380), 1350);

        // Spinner
        h.postDelayed(() ->
                splashSpinner.animate()
                        .alpha(1f)
                        .setDuration(400)
                        .start(),
                1500);

        // Start the pulsing breathe on "2027" after the entrance finishes
        h.postDelayed(() -> startPulse(year2027Text), 1900);
    }

    /** Fade-in + translate-up helper. */
    private void slideUp(View v, long duration) {
        v.animate()
                .alpha(1f).translationY(0f)
                .setDuration(duration)
                .setInterpolator(new DecelerateInterpolator())
                .start();
    }

    /**
     * Gentle repeating scale "breathe" on the year number while loading.
     * Stops automatically once the splash is dismissed.
     */
    private void startPulse(View v) {
        if (splashDismissed) return;
        v.animate()
                .scaleX(1.07f).scaleY(1.07f)
                .setDuration(950)
                .setInterpolator(new DecelerateInterpolator())
                .withEndAction(() -> {
                    if (splashDismissed) return;
                    v.animate()
                            .scaleX(1f).scaleY(1f)
                            .setDuration(950)
                            .setInterpolator(new DecelerateInterpolator())
                            .withEndAction(() -> startPulse(v))
                            .start();
                })
                .start();
    }

    /** Fades out and removes the overlay. Called once the first page finishes loading. */
    private void dismissSplash() {
        if (splashDismissed) return;
        splashDismissed = true;
        splashOverlay.animate()
                .alpha(0f)
                .setDuration(650)
                .withEndAction(() -> splashOverlay.setVisibility(View.GONE))
                .start();
    }

    private float dp(int dp) {
        return dp * getResources().getDisplayMetrics().density;
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
            webPageSettled = true;
            swipeRefresh.setRefreshing(false);
            CookieManager.getInstance().flush();

            // Dismiss the loading overlay, but honour the minimum display time
            // so the animation always completes gracefully.
            long elapsed = SystemClock.elapsedRealtime() - splashShownAt;
            long delay   = Math.max(0, MIN_SPLASH_MS - elapsed);
            new Handler(Looper.getMainLooper())
                    .postDelayed(MainActivity.this::dismissSplash, delay);
        }

        /** API 23+ error handler. */
        @Override
        public void onReceivedError(WebView view, WebResourceRequest request,
                                    WebResourceError error) {
            if (!request.isForMainFrame()) return;
            if (isNetworkAvailable()) {
                showServerErrorPage();
            } else {
                showOfflinePage();
            }
        }

        /** API 21-22 error handler. */
        @Override
        @SuppressWarnings("deprecation")
        public void onReceivedError(WebView view, int errorCode,
                                    String description, String failingUrl) {
            if (isNetworkAvailable()) {
                showServerErrorPage();
            } else {
                showOfflinePage();
            }
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
    // Error pages (bilingual — English + Bengali)
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

    /** Shown when the device has internet but the app server cannot be reached. */
    private static final String SERVER_ERROR_HTML =
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
        "p{font-size:14px;color:#64748b;margin-bottom:28px;line-height:1.6}" +
        "button{padding:14px 36px;background:#1f6b4a;color:#fff;border:none;" +
        "border-radius:12px;font-size:16px;font-weight:700;cursor:pointer;" +
        "width:100%;max-width:280px}" +
        "button:active{opacity:.85}" +
        ".hint{margin-top:20px;font-size:12px;color:#94a3b8}" +
        "</style></head><body>" +
        "<div class='icon'>🖥️</div>" +
        "<h1>Server Unreachable</h1>" +
        "<div class='bn'>সার্ভার পাওয়া যাচ্ছে না</div>" +
        "<p>Your internet is working, but the Sammalani Alumni server could not be reached. " +
        "It may be temporarily down.<br><br>" +
        "আপনার ইন্টারনেট সংযোগ আছে, কিন্তু সার্ভারে পৌঁছানো যাচ্ছে না। " +
        "সার্ভারটি সাময়িকভাবে বন্ধ থাকতে পারে।</p>" +
        "<button onclick='window.location.href=\"" + BuildConfig.WEB_APP_URL + "\"'>Try Again &nbsp;·&nbsp; আবার চেষ্টা করুন</button>" +
        "<div class='hint'>" + BuildConfig.WEB_APP_URL + "</div>" +
        "</body></html>";
}
