package bd.sammalani.alumni.ui;

import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import com.google.android.material.bottomnavigation.BottomNavigationView;

import bd.sammalani.alumni.R;
import bd.sammalani.alumni.util.LocaleHelper;
import bd.sammalani.alumni.api.ApiClient;
import bd.sammalani.alumni.session.SessionManager;
import bd.sammalani.alumni.ui.fragment.BatchesFragment;
import bd.sammalani.alumni.ui.fragment.DashboardFragment;
import bd.sammalani.alumni.ui.fragment.GuestsFragment;
import bd.sammalani.alumni.ui.fragment.ProfileFragment;

public class HomeActivity extends AppCompatActivity {

    @Override
    protected void attachBaseContext(android.content.Context newBase) {
        String lang = SessionManager.get(newBase).getLang();
        super.attachBaseContext(LocaleHelper.apply(newBase, lang));
    }

    private BottomNavigationView bottomNav;
    private int currentItemId = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        // Redirect to landing when the session can no longer be refreshed
        ApiClient.setOnSessionExpiredListener(() -> {
            SessionManager.get(this).clearTokens();
            startActivity(new Intent(this, LandingActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
        });

        bottomNav = findViewById(R.id.bottomNav);

        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == currentItemId) return true;
            currentItemId = id;
            Fragment fragment;
            if (id == R.id.nav_dashboard) {
                fragment = new DashboardFragment();
            } else if (id == R.id.nav_batches) {
                fragment = new BatchesFragment();
            } else if (id == R.id.nav_guests) {
                fragment = new GuestsFragment();
            } else {
                fragment = new ProfileFragment();
            }
            getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragmentContainer, fragment)
                .commit();
            return true;
        });

        // Restore the last-selected tab (survives locale-change recreation via savedInstanceState)
        // or fall back to the tab requested by the caller intent.
        int startTab;
        if (savedInstanceState != null) {
            startTab = savedInstanceState.getInt("selectedTab", R.id.nav_dashboard);
        } else {
            startTab = getIntent().getIntExtra("tab", R.id.nav_dashboard);
        }
        bottomNav.setSelectedItemId(startTab);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt("selectedTab", currentItemId);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        ApiClient.setOnSessionExpiredListener(null);
    }
}
