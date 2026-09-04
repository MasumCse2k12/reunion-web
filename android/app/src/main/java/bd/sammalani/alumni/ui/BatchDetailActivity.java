package bd.sammalani.alumni.ui;

import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.os.LocaleListCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import bd.sammalani.alumni.R;
import bd.sammalani.alumni.util.LocaleHelper;
import bd.sammalani.alumni.api.ApiCallback;
import bd.sammalani.alumni.api.ApiClient;
import bd.sammalani.alumni.model.Person;
import bd.sammalani.alumni.session.SessionManager;
import bd.sammalani.alumni.ui.adapter.MemberAdapter;
import bd.sammalani.alumni.util.Fmt;

public class BatchDetailActivity extends AppCompatActivity {

    @Override
    protected void attachBaseContext(android.content.Context newBase) {
        String lang = SessionManager.get(newBase).getLang();
        super.attachBaseContext(LocaleHelper.apply(newBase, lang));
    }

    private TextView tvToolbarTitle, tvBatchYear, tvBatchStats, tvLoading, tvLang;
    private ProgressBar progressBatch;
    private RecyclerView rvMembers;
    private MemberAdapter adapter;
    private final List<Person> members = new ArrayList<>();

    private int batchYear;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_batch_detail);

        tvToolbarTitle = findViewById(R.id.tvToolbarTitle);
        tvBatchYear    = findViewById(R.id.tvBatchYear);
        tvBatchStats   = findViewById(R.id.tvBatchStats);
        progressBatch  = findViewById(R.id.progressBatch);
        rvMembers      = findViewById(R.id.rvMembers);
        tvLoading      = findViewById(R.id.tvLoading);
        tvLang         = findViewById(R.id.tvLang);

        boolean isBn = SessionManager.get(this).isBn();
        tvLang.setText(isBn ? getString(R.string.lang_en) : getString(R.string.lang_toggle));
        tvLang.setOnClickListener(v -> {
            SessionManager sm = SessionManager.get(this);
            String newLang = sm.isBn() ? "en" : "bn";
            sm.setLang(newLang);
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(newLang));
        });

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        batchYear = getIntent().getIntExtra("batchYear", 0);
        boolean bn = SessionManager.get(this).isBn();

        tvToolbarTitle.setText((bn ? "ব্যাচ " : "Batch ") + Fmt.year(batchYear, bn));
        tvBatchYear.setText(Fmt.year(batchYear, bn));

        adapter = new MemberAdapter(members, bn, null);
        rvMembers.setLayoutManager(new LinearLayoutManager(this));
        rvMembers.setAdapter(adapter);

        loadMembers(bn);
    }

    private void loadMembers(boolean bn) {
        tvLoading.setVisibility(View.VISIBLE);
        rvMembers.setVisibility(View.GONE);

        ApiClient.get().getBatchMembers(batchYear, new ApiCallback<List<Person>>() {
            @Override public void onSuccess(List<Person> people) {
                if (isDestroyed()) return;
                tvLoading.setVisibility(View.GONE);
                rvMembers.setVisibility(View.VISIBLE);

                int total = people.size();
                int found = 0;
                for (Person p : people) {
                    if ("CLAIMED".equals(p.status) || "VERIFIED".equals(p.status)) found++;
                }
                int missing = total - found;
                int pct = total > 0 ? (found * 100 / total) : 0;

                tvBatchStats.setText(
                        Fmt.number(found, bn) + " " + getString(R.string.batches_found) +
                        "  ·  " +
                        Fmt.number(missing, bn) + " " + getString(R.string.batches_missing));
                progressBatch.setProgress(pct);

                members.clear();
                members.addAll(people);
                adapter.setData(new ArrayList<>(members));
            }
            @Override public void onError(String en, String b) {
                if (isDestroyed()) return;
                tvLoading.setText(getString(R.string.common_error));
            }
        });
    }
}
