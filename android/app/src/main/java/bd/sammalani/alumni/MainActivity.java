package bd.sammalani.alumni;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.os.LocaleListCompat;
import androidx.core.splashscreen.SplashScreen;

import bd.sammalani.alumni.api.ApiClient;
import bd.sammalani.alumni.session.SessionManager;
import bd.sammalani.alumni.ui.HomeActivity;
import bd.sammalani.alumni.ui.LandingActivity;

/**
 * Entry point: shows the Grand Reunion 2027 branded splash animation,
 * then navigates to HomeActivity (logged in) or LandingActivity (guest).
 *
 * All WebView code has been replaced with a native API-driven app.
 */
public class MainActivity extends AppCompatActivity {

    private static final long MIN_SPLASH_MS = 2600;

    private View splashOverlay, emblemView, topDivider, schoolNameEn, schoolNameBn;
    private View grandReunionText, year2027Text, bottomDivider, taglineText, splashSpinner;

    private long splashShownAt;
    private boolean splashDismissed = false;
    private boolean destinationReady = false;
    private boolean loggedIn = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        SplashScreen.installSplashScreen(this);
        super.onCreate(savedInstanceState);

        // Apply saved locale before inflation (default: Bangla for new installs)
        String lang = SessionManager.get(this).getLang();
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(lang));

        setContentView(R.layout.activity_main);

        ApiClient.get().init(this);

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

        splashShownAt = SystemClock.elapsedRealtime();
        initSplashViews();
        runSplashAnimation();

        // Check login status in background; navigate once MIN_SPLASH_MS has elapsed
        loggedIn = SessionManager.get(this).isLoggedIn();
        destinationReady = true;
        scheduleNavigate();
    }

    private void scheduleNavigate() {
        long elapsed = SystemClock.elapsedRealtime() - splashShownAt;
        long delay   = Math.max(0, MIN_SPLASH_MS - elapsed);
        new Handler(Looper.getMainLooper()).postDelayed(this::navigate, delay);
    }

    private void navigate() {
        dismissSplash();
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            Intent intent = loggedIn
                ? new Intent(this, HomeActivity.class)
                : new Intent(this, LandingActivity.class);
            startActivity(intent);
            finish();
        }, 650); // wait for fade-out animation
    }

    // ── Splash animation (unchanged from WebView version) ─────────────

    private void initSplashViews() {
        float slide = dp(28);
        emblemView.setAlpha(0f);  emblemView.setScaleX(0.35f);  emblemView.setScaleY(0.35f);
        topDivider.setAlpha(0f);  topDivider.setScaleX(0f);
        schoolNameEn.setAlpha(0f); schoolNameEn.setTranslationY(slide);
        schoolNameBn.setAlpha(0f); schoolNameBn.setTranslationY(slide);
        bottomDivider.setAlpha(0f); bottomDivider.setScaleX(0f);
        grandReunionText.setAlpha(0f); grandReunionText.setTranslationY(dp(20));
        year2027Text.setAlpha(0f); year2027Text.setScaleX(0.55f); year2027Text.setScaleY(0.55f);
        taglineText.setAlpha(0f);  taglineText.setTranslationY(dp(16));
        splashSpinner.setAlpha(0f);
    }

    private void runSplashAnimation() {
        Handler h = new Handler(Looper.getMainLooper());

        emblemView.animate().alpha(1f).scaleX(1f).scaleY(1f)
            .setDuration(520).setInterpolator(new OvershootInterpolator(1.6f)).start();

        h.postDelayed(() -> topDivider.animate().alpha(1f).scaleX(1f)
            .setDuration(380).setInterpolator(new DecelerateInterpolator()).start(), 320);
        h.postDelayed(() -> slideUp(schoolNameEn, 360), 480);
        h.postDelayed(() -> slideUp(schoolNameBn, 360), 580);
        h.postDelayed(() -> bottomDivider.animate().alpha(0.6f).scaleX(1f)
            .setDuration(360).setInterpolator(new DecelerateInterpolator()).start(), 720);
        h.postDelayed(() -> slideUp(grandReunionText, 400), 840);
        h.postDelayed(() -> year2027Text.animate().alpha(1f).scaleX(1f).scaleY(1f)
            .setDuration(560).setInterpolator(new OvershootInterpolator(1.4f)).start(), 1050);
        h.postDelayed(() -> slideUp(taglineText, 380), 1350);
        h.postDelayed(() -> splashSpinner.animate().alpha(1f).setDuration(400).start(), 1500);
        h.postDelayed(() -> startPulse(year2027Text), 1900);
    }

    private void slideUp(View v, long duration) {
        v.animate().alpha(1f).translationY(0f)
            .setDuration(duration).setInterpolator(new DecelerateInterpolator()).start();
    }

    private void startPulse(View v) {
        if (splashDismissed) return;
        v.animate().scaleX(1.07f).scaleY(1.07f).setDuration(950)
            .setInterpolator(new DecelerateInterpolator())
            .withEndAction(() -> {
                if (splashDismissed) return;
                v.animate().scaleX(1f).scaleY(1f).setDuration(950)
                    .setInterpolator(new DecelerateInterpolator())
                    .withEndAction(() -> startPulse(v)).start();
            }).start();
    }

    private void dismissSplash() {
        if (splashDismissed) return;
        splashDismissed = true;
        splashOverlay.animate().alpha(0f).setDuration(650)
            .withEndAction(() -> splashOverlay.setVisibility(View.GONE)).start();
    }

    private float dp(int dp) {
        return dp * getResources().getDisplayMetrics().density;
    }
}
