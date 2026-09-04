package bd.sammalani.alumni.ui.fragment;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.os.LocaleListCompat;
import androidx.fragment.app.Fragment;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.button.MaterialButton;

import java.util.List;

import bd.sammalani.alumni.R;
import bd.sammalani.alumni.api.ApiCallback;
import bd.sammalani.alumni.api.ApiClient;
import bd.sammalani.alumni.model.Notice;
import bd.sammalani.alumni.model.Person;
import bd.sammalani.alumni.model.Registration;
import bd.sammalani.alumni.session.SessionManager;
import bd.sammalani.alumni.util.AvatarView;
import bd.sammalani.alumni.util.Fmt;

public class DashboardFragment extends Fragment {

    private SwipeRefreshLayout swipeRefresh;
    private TextView tvGreeting, tvBatchTag, tvProfilePct, tvProfileHint, tvLang;
    private TextView tvRegStatus, tvRegSub, tvRegTotal;
    private ProgressBar progressProfile;
    private MaterialButton btnRegAction;
    private ImageView ivAvatar;
    private LinearLayout noticesContainer;
    private View cardReg;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_dashboard, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        swipeRefresh    = view.findViewById(R.id.swipeRefresh);
        tvGreeting      = view.findViewById(R.id.tvGreeting);
        tvBatchTag      = view.findViewById(R.id.tvBatchTag);
        tvProfilePct    = view.findViewById(R.id.tvProfilePct);
        tvProfileHint   = view.findViewById(R.id.tvProfileHint);
        progressProfile = view.findViewById(R.id.progressProfile);
        tvRegStatus     = view.findViewById(R.id.tvRegStatus);
        tvRegSub        = view.findViewById(R.id.tvRegSub);
        tvRegTotal      = view.findViewById(R.id.tvRegTotal);
        btnRegAction    = view.findViewById(R.id.btnRegAction);
        ivAvatar        = view.findViewById(R.id.ivAvatar);
        noticesContainer = view.findViewById(R.id.noticesContainer);
        cardReg         = view.findViewById(R.id.cardReg);
        tvLang          = view.findViewById(R.id.tvLang);

        swipeRefresh.setColorSchemeResources(R.color.brand_600);
        swipeRefresh.setOnRefreshListener(this::load);

        boolean bn = SessionManager.get(requireContext()).isBn();
        tvLang.setText(bn ? getString(R.string.lang_en) : getString(R.string.lang_toggle));
        tvLang.setOnClickListener(v -> toggleLang());

        load();
    }

    private void toggleLang() {
        SessionManager sm = SessionManager.get(requireContext());
        String newLang = sm.isBn() ? "en" : "bn";
        sm.setLang(newLang);
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(newLang));
    }

    private void load() {
        swipeRefresh.setRefreshing(true);
        SessionManager sm = SessionManager.get(requireContext());
        boolean bn = sm.isBn();
        Person cached = sm.getPerson();
        if (cached != null) {
            applyPerson(cached, bn);
        }

        ApiClient.get().getMe(new ApiCallback<Person>() {
            @Override public void onSuccess(Person p) {
                if (getContext() == null) return;
                sm.savePerson(p);
                applyPerson(p, bn);
                loadRegistration(bn);
                loadNotices(bn);
            }
            @Override public void onError(String en, String b) {
                if (getContext() == null) return;
                swipeRefresh.setRefreshing(false);
            }
        });
    }

    private void applyPerson(Person p, boolean bn) {
        tvGreeting.setText((bn ? "স্বাগতম, " : "Welcome, ") + p.displayName());
        tvBatchTag.setText((bn ? "SSC ব্যাচ " : "SSC Batch ") + Fmt.year(p.batchYear, bn));
        AvatarView.load(requireContext(), ivAvatar, p.photoUrl, p.displayName());

        int pct = computeCompleteness(p);
        progressProfile.setProgress(pct);
        tvProfilePct.setText(Fmt.number(pct, bn) + "%");
        tvProfileHint.setText(pct == 100
                ? getString(R.string.dash_complete)
                : getString(R.string.dash_few_more));
    }

    private int computeCompleteness(Person p) {
        int filled = 0;
        if (p.name != null && !p.name.isEmpty())             filled++;
        if (p.batchYear > 0)                                 filled++;
        if (p.phone != null && !p.phone.isEmpty())           filled++;
        if (p.occupation != null && !p.occupation.isEmpty()) filled++;
        if (p.city != null && !p.city.isEmpty())             filled++;
        if (p.email != null && !p.email.isEmpty())           filled++;
        return Math.round(filled * 100f / 6);
    }

    private void loadRegistration(boolean bn) {
        ApiClient.get().getRegistration(new ApiCallback<Registration>() {
            @Override public void onSuccess(Registration r) {
                if (getContext() == null) return;
                swipeRefresh.setRefreshing(false);
                cardReg.setVisibility(View.VISIBLE);
                bindReg(r, bn);
            }
            @Override public void onError(String en, String b) {
                if (getContext() == null) return;
                swipeRefresh.setRefreshing(false);
                cardReg.setVisibility(View.VISIBLE);
                tvRegStatus.setText(getString(R.string.dash_draft));
                tvRegStatus.setBackgroundResource(R.drawable.bg_tag_gold);
                tvRegSub.setText(getString(R.string.dash_not_registered));
                tvRegTotal.setVisibility(View.GONE);
                btnRegAction.setText(getString(R.string.dash_register_now));
                btnRegAction.setOnClickListener(v -> switchToGuestsTab());
            }
        });
    }

    private void bindReg(Registration r, boolean bn) {
        String status;
        int bgRes;
        if (r.isRejected()) {
            status = getString(R.string.dash_rejected);
            bgRes  = R.drawable.bg_tag_red;
        } else if (r.isApproved()) {
            status = getString(R.string.dash_registered);
            bgRes  = R.drawable.bg_tag_brand;
        } else if (r.isSubmitted()) {
            status = getString(R.string.dash_awaiting_review);
            bgRes  = R.drawable.bg_tag_gold;
        } else {
            status = getString(R.string.dash_draft);
            bgRes  = R.drawable.bg_tag_gold;
        }
        tvRegStatus.setText(status);
        tvRegStatus.setBackgroundResource(bgRes);

        double total = r.totalAmount();
        if (total > 0) {
            tvRegTotal.setVisibility(View.VISIBLE);
            tvRegTotal.setText(getString(R.string.dash_total_due) + ": " + Fmt.money(total, bn));
        } else {
            tvRegTotal.setVisibility(View.GONE);
        }

        tvRegSub.setText((bn ? "অতিথি: " : "Guests: ") + Fmt.number(r.guestCount(), bn));
        btnRegAction.setText(r.isDraft()
                ? getString(R.string.cta_continue)
                : getString(R.string.cta_view_all));
        btnRegAction.setOnClickListener(v -> switchToGuestsTab());
    }

    private void loadNotices(boolean bn) {
        ApiClient.get().getNotices(new ApiCallback<List<Notice>>() {
            @Override public void onSuccess(List<Notice> notices) {
                if (getContext() == null) return;
                noticesContainer.removeAllViews();
                LayoutInflater inflater = getLayoutInflater();
                int shown = Math.min(notices.size(), 3);
                for (int i = 0; i < shown; i++) {
                    Notice n = notices.get(i);
                    View item = inflater.inflate(R.layout.item_notice, noticesContainer, false);
                    ((TextView) item.findViewById(R.id.tvNoticeTitle)).setText(n.displayTitle(bn));
                    ((TextView) item.findViewById(R.id.tvNoticeBody)).setText(n.displayBody(bn));
                    noticesContainer.addView(item);
                    if (i < shown - 1) {
                        View div = new View(getContext());
                        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT, 1);
                        lp.setMargins(48, 0, 48, 0);
                        div.setLayoutParams(lp);
                        div.setBackgroundColor(androidx.core.content.ContextCompat
                                .getColor(requireContext(), R.color.paper_2));
                        noticesContainer.addView(div);
                    }
                }
            }
            @Override public void onError(String en, String b) { /* no-op */ }
        });
    }

    private void switchToGuestsTab() {
        if (getActivity() == null) return;
        BottomNavigationView nav = getActivity().findViewById(R.id.bottomNav);
        if (nav != null) nav.setSelectedItemId(R.id.nav_guests);
    }
}
