package bd.sammalani.alumni.ui.fragment;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.os.LocaleListCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.textfield.TextInputEditText;

import java.util.ArrayList;
import java.util.List;

import bd.sammalani.alumni.R;
import bd.sammalani.alumni.api.ApiCallback;
import bd.sammalani.alumni.api.ApiClient;
import bd.sammalani.alumni.model.Batch;
import bd.sammalani.alumni.session.SessionManager;
import bd.sammalani.alumni.ui.BatchDetailActivity;
import bd.sammalani.alumni.ui.adapter.BatchAdapter;

public class BatchesFragment extends Fragment implements BatchAdapter.OnBatchClickListener {

    private RecyclerView rvBatches;
    private TextView tvLoading, tvLang;
    private TextInputEditText etSearch;
    private BatchAdapter adapter;
    private List<Batch> allBatches = new ArrayList<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_batches, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        rvBatches = view.findViewById(R.id.rvBatches);
        tvLoading = view.findViewById(R.id.tvBatchesLoading);
        etSearch  = view.findViewById(R.id.etBatchSearch);
        tvLang    = view.findViewById(R.id.tvLang);

        SessionManager sm = SessionManager.get(requireContext());
        boolean bn = sm.isBn();
        tvLang.setText(bn ? getString(R.string.lang_en) : getString(R.string.lang_toggle));
        tvLang.setOnClickListener(v -> {
            String newLang = sm.isBn() ? "en" : "bn";
            sm.setLang(newLang);
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(newLang));
        });

        adapter = new BatchAdapter(allBatches, sm.isBn(),
                sm.getPerson() != null ? sm.getPerson().batchYear : 0, this);
        rvBatches.setLayoutManager(new LinearLayoutManager(requireContext()));
        rvBatches.setAdapter(adapter);

        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            @Override public void onTextChanged(CharSequence s, int st, int b, int c) {}
            @Override public void afterTextChanged(Editable s) { filter(s.toString()); }
        });

        loadBatches();
    }

    private void loadBatches() {
        tvLoading.setVisibility(View.VISIBLE);
        rvBatches.setVisibility(View.GONE);

        ApiClient.get().getBatches(new ApiCallback<List<Batch>>() {
            @Override public void onSuccess(List<Batch> batches) {
                if (getContext() == null) return;
                tvLoading.setVisibility(View.GONE);
                rvBatches.setVisibility(View.VISIBLE);
                allBatches.clear();
                allBatches.addAll(batches);
                // Sort newest first
                allBatches.sort((a, b) -> Integer.compare(b.year, a.year));
                adapter.setData(new ArrayList<>(allBatches));
            }
            @Override public void onError(String en, String bn) {
                if (getContext() == null) return;
                tvLoading.setVisibility(View.VISIBLE);
                tvLoading.setText(getString(R.string.common_error));
            }
        });
    }

    private void filter(String query) {
        if (query.isEmpty()) {
            adapter.setData(allBatches);
            return;
        }
        List<Batch> filtered = new ArrayList<>();
        for (Batch b : allBatches) {
            if (String.valueOf(b.year).contains(query)) filtered.add(b);
        }
        adapter.setData(filtered);
    }

    @Override
    public void onBatchClick(Batch batch) {
        Intent intent = new Intent(requireContext(), BatchDetailActivity.class);
        intent.putExtra("batchYear", batch.year);
        intent.putExtra("rosterCount", batch.rosterCount);
        intent.putExtra("claimedCount", batch.claimedCount);
        startActivity(intent);
    }
}
