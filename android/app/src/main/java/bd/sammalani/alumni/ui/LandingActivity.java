package bd.sammalani.alumni.ui;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.os.LocaleListCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

import java.util.List;

import bd.sammalani.alumni.R;
import bd.sammalani.alumni.api.ApiCallback;
import bd.sammalani.alumni.api.ApiClient;
import bd.sammalani.alumni.model.Person;
import bd.sammalani.alumni.session.SessionManager;
import bd.sammalani.alumni.util.AvatarView;
import bd.sammalani.alumni.util.Fmt;

public class LandingActivity extends AppCompatActivity {

    // Grand Reunion 2027: Jan 1, 2027 00:00:00 UTC+6
    private static final long EVENT_TIME_MS = 1798912800000L;

    private TextView tvDays, tvHours, tvMins, tvSecs;
    private TextView tvStatAlumni, tvStatRegistered, tvStatBatches;
    private TextView tvLang;
    private TextInputEditText etSearchYear;
    private MaterialButton btnSearchBatch;
    private LinearLayout searchResultsContainer;
    private TextView tvSearchEmpty;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable tickRunnable = this::tick;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_landing);

        tvDays    = findViewById(R.id.tvDays);
        tvHours   = findViewById(R.id.tvHours);
        tvMins    = findViewById(R.id.tvMins);
        tvSecs    = findViewById(R.id.tvSecs);
        tvStatAlumni     = findViewById(R.id.tvStatAlumni);
        tvStatRegistered = findViewById(R.id.tvStatRegistered);
        tvStatBatches    = findViewById(R.id.tvStatBatches);
        tvLang           = findViewById(R.id.tvLang);
        etSearchYear     = findViewById(R.id.etSearchYear);
        btnSearchBatch   = findViewById(R.id.btnSearchBatch);
        searchResultsContainer = findViewById(R.id.searchResultsContainer);
        tvSearchEmpty    = findViewById(R.id.tvSearchEmpty);

        MaterialButton btnFindMe = findViewById(R.id.btnFindMe);
        MaterialButton btnLogin  = findViewById(R.id.btnLogin);

        btnFindMe.setOnClickListener(v -> startActivity(new Intent(this, SignupActivity.class)));
        btnLogin.setOnClickListener(v -> startActivity(new Intent(this, LoginActivity.class)));

        boolean isBn = SessionManager.get(this).isBn();
        tvLang.setText(isBn ? getString(R.string.lang_en) : getString(R.string.lang_toggle));
        tvLang.setOnClickListener(v -> toggleLang());

        btnSearchBatch.setOnClickListener(v -> searchBatch());
        etSearchYear.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_ENTER) {
                searchBatch(); return true;
            }
            return false;
        });

        tick();
        loadStats();
    }

    @Override
    protected void onResume() {
        super.onResume();
        handler.postDelayed(tickRunnable, 1000);
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(tickRunnable);
    }

    private void tick() {
        long diff = EVENT_TIME_MS - System.currentTimeMillis();
        boolean isBn = SessionManager.get(this).isBn();
        if (diff <= 0) {
            tvDays.setText(Fmt.number(0, isBn));
            tvHours.setText(Fmt.number(0, isBn));
            tvMins.setText(Fmt.number(0, isBn));
            tvSecs.setText(Fmt.number(0, isBn));
        } else {
            long totalSecs = diff / 1000;
            long days  = totalSecs / 86400;
            long hours = (totalSecs % 86400) / 3600;
            long mins  = (totalSecs % 3600) / 60;
            long secs  = totalSecs % 60;
            tvDays.setText(Fmt.number((int) days, isBn));
            tvHours.setText(pad((int) hours, isBn));
            tvMins.setText(pad((int) mins, isBn));
            tvSecs.setText(pad((int) secs, isBn));
        }
        handler.postDelayed(tickRunnable, 1000);
    }

    private String pad(int n, boolean bn) {
        return n < 10 ? (bn ? "০" : "0") + Fmt.number(n, bn) : Fmt.number(n, bn);
    }

    private void loadStats() {
        ApiClient.get().getTotals(new ApiCallback<ApiClient.Totals>() {
            @Override public void onSuccess(ApiClient.Totals t) {
                boolean bn = SessionManager.get(LandingActivity.this).isBn();
                tvStatAlumni.setText(Fmt.number(t.claimed, bn));
                tvStatRegistered.setText(Fmt.number(t.roster, bn));
                tvStatBatches.setText(Fmt.number(t.batches, bn));
            }
            @Override public void onError(String en, String bn) {
                tvStatAlumni.setText("?");
                tvStatRegistered.setText("?");
                tvStatBatches.setText("?");
            }
        });
    }

    private void searchBatch() {
        String yearStr = etSearchYear.getText() != null
                ? etSearchYear.getText().toString().trim() : "";
        if (yearStr.length() != 4) return;
        int year;
        try { year = Integer.parseInt(yearStr); } catch (NumberFormatException e) { return; }

        // Hide keyboard
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        View focus = getCurrentFocus();
        if (focus != null) imm.hideSoftInputFromWindow(focus.getWindowToken(), 0);

        searchResultsContainer.setVisibility(View.GONE);
        tvSearchEmpty.setVisibility(View.GONE);
        btnSearchBatch.setEnabled(false);

        ApiClient.get().lookupBatch(year, null, new ApiCallback<List<Person>>() {
            @Override public void onSuccess(List<Person> people) {
                btnSearchBatch.setEnabled(true);
                if (people == null || people.isEmpty()) {
                    tvSearchEmpty.setVisibility(View.VISIBLE);
                    return;
                }
                boolean bn = SessionManager.get(LandingActivity.this).isBn();
                searchResultsContainer.removeAllViews();
                searchResultsContainer.setVisibility(View.VISIBLE);

                LayoutInflater inflater = getLayoutInflater();
                int shown = Math.min(people.size(), 20);
                for (int i = 0; i < shown; i++) {
                    Person p = people.get(i);
                    View row = inflater.inflate(R.layout.item_member, searchResultsContainer, false);
                    ((TextView) row.findViewById(R.id.tvName)).setText(p.displayName());
                    String sub = (bn ? "SSC ব্যাচ " : "SSC Batch ") + Fmt.year(year, bn);
                    ((TextView) row.findViewById(R.id.tvSub)).setText(sub);
                    AvatarView.load(LandingActivity.this,
                            (ImageView) row.findViewById(R.id.ivAvatar),
                            p.photoUrl, p.displayName());

                    TextView tvTag = row.findViewById(R.id.tvTag);
                    if ("CLAIMED".equals(p.status)) {
                        tvTag.setVisibility(View.VISIBLE);
                        tvTag.setText(getString(R.string.signup_already_joined));
                        tvTag.setBackgroundResource(R.drawable.bg_tag_brand);
                        tvTag.setTextColor(androidx.core.content.ContextCompat
                                .getColor(LandingActivity.this, R.color.brand_700));
                    } else {
                        tvTag.setVisibility(View.VISIBLE);
                        tvTag.setText(getString(R.string.signup_not_joined));
                        tvTag.setBackgroundResource(R.drawable.bg_tag_gold);
                        tvTag.setTextColor(androidx.core.content.ContextCompat
                                .getColor(LandingActivity.this, R.color.ink_700));
                    }

                    // Clicking a not-joined person opens Signup with year pre-filled
                    final int finalYear = year;
                    row.setOnClickListener(v -> {
                        Intent intent = new Intent(LandingActivity.this, SignupActivity.class);
                        intent.putExtra("batchYear", finalYear);
                        startActivity(intent);
                    });

                    searchResultsContainer.addView(row);
                }

                if (people.size() > 20) {
                    TextView more = new TextView(LandingActivity.this);
                    more.setText((bn ? "+" : "+") + Fmt.number(people.size() - 20, bn)
                            + (bn ? " আরো দেখতে নাম খুঁজুন" : " more — use Find My Name to see all"));
                    more.setTextSize(13);
                    more.setTextColor(androidx.core.content.ContextCompat
                            .getColor(LandingActivity.this, R.color.ink_500));
                    more.setPadding(48, 16, 48, 16);
                    searchResultsContainer.addView(more);
                }
            }
            @Override public void onError(String en, String bn) {
                btnSearchBatch.setEnabled(true);
                tvSearchEmpty.setVisibility(View.VISIBLE);
            }
        });
    }

    private void toggleLang() {
        SessionManager sm = SessionManager.get(this);
        boolean wasBn = sm.isBn();
        String newLang = wasBn ? "en" : "bn";
        sm.setLang(newLang);
        LocaleListCompat localeList = LocaleListCompat.forLanguageTags(newLang);
        AppCompatDelegate.setApplicationLocales(localeList);
    }
}
