package bd.sammalani.alumni.ui.fragment;

import android.app.AlertDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.os.LocaleListCompat;
import androidx.fragment.app.Fragment;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

import java.util.ArrayList;
import java.util.List;

import bd.sammalani.alumni.R;
import bd.sammalani.alumni.api.ApiCallback;
import bd.sammalani.alumni.api.ApiClient;
import bd.sammalani.alumni.model.Coordinator;
import bd.sammalani.alumni.model.EventInfo;
import bd.sammalani.alumni.model.Guest;
import bd.sammalani.alumni.model.Registration;
import bd.sammalani.alumni.model.TicketType;
import bd.sammalani.alumni.session.SessionManager;
import bd.sammalani.alumni.util.Fmt;

public class GuestsFragment extends Fragment {

    private static final String[] TSHIRT_SIZES  = {"XS", "S", "M", "L", "XL", "XXL"};
    private static final String[] FOOD_PREFS    = {"REGULAR", "NO_BEEF", "VEGETARIAN"};
    private static final String[] PAY_METHODS   = {"BKASH", "NAGAD", "ROCKET", "BANK", "CASH", "OTHER"};
    private static final String[] RELATIONS     = {"SPOUSE", "CHILD", "PARENT", "SIBLING", "OTHER"};

    private TextView tvState, tvLang;
    private View bannerStatus, contentContainer, cardPayment;
    private TextView tvStatusTitle, tvStatusSub;
    private TextView tvMyTicketType, tvMyTicketNote, tvMyTicketPrice, tvTotalAmount, tvError;
    private TextView tvCoordinatorInfo, tvNoGuests;
    private Spinner spinnerMyTshirt, spinnerMyFood, spinnerPayMethod;
    private TextInputEditText etNote, etPayRef;
    private MaterialButton btnAddGuest, btnSave, btnSubmit, btnReportPayment;
    private ProgressBar pbSave, pbSubmit, pbReportPayment;
    private LinearLayout guestsContainer, costRows;

    private Registration currentReg;
    private EventInfo eventInfo;
    private List<Guest> guests = new ArrayList<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_guests, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        tvState          = view.findViewById(R.id.tvState);
        bannerStatus     = view.findViewById(R.id.bannerStatus);
        contentContainer = view.findViewById(R.id.contentContainer);
        cardPayment      = view.findViewById(R.id.cardPayment);
        tvStatusTitle    = view.findViewById(R.id.tvStatusTitle);
        tvStatusSub      = view.findViewById(R.id.tvStatusSub);
        tvMyTicketType   = view.findViewById(R.id.tvMyTicketType);
        tvMyTicketNote   = view.findViewById(R.id.tvMyTicketNote);
        tvMyTicketPrice  = view.findViewById(R.id.tvMyTicketPrice);
        tvTotalAmount    = view.findViewById(R.id.tvTotalAmount);
        tvError          = view.findViewById(R.id.tvError);
        tvCoordinatorInfo = view.findViewById(R.id.tvCoordinatorInfo);
        tvNoGuests       = view.findViewById(R.id.tvNoGuests);
        spinnerMyTshirt  = view.findViewById(R.id.spinnerMyTshirt);
        spinnerMyFood    = view.findViewById(R.id.spinnerMyFood);
        spinnerPayMethod = view.findViewById(R.id.spinnerPayMethod);
        etNote           = view.findViewById(R.id.etNote);
        etPayRef         = view.findViewById(R.id.etPayRef);
        btnAddGuest      = view.findViewById(R.id.btnAddGuest);
        btnSave          = view.findViewById(R.id.btnSave);
        btnSubmit        = view.findViewById(R.id.btnSubmit);
        btnReportPayment = view.findViewById(R.id.btnReportPayment);
        pbSave           = view.findViewById(R.id.pbSave);
        pbSubmit         = view.findViewById(R.id.pbSubmit);
        pbReportPayment  = view.findViewById(R.id.pbReportPayment);
        guestsContainer  = view.findViewById(R.id.guestsContainer);
        costRows         = view.findViewById(R.id.costRows);

        tvLang = view.findViewById(R.id.tvLang);
        SessionManager smg = SessionManager.get(requireContext());
        tvLang.setText(smg.isBn() ? getString(R.string.lang_en) : getString(R.string.lang_toggle));
        tvLang.setOnClickListener(v -> {
            String newLang = smg.isBn() ? "en" : "bn";
            smg.setLang(newLang);
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(newLang));
        });

        setupSpinners();
        btnAddGuest.setOnClickListener(v -> showAddGuestDialog());
        btnSave.setOnClickListener(v -> saveRegistration(false));
        btnSubmit.setOnClickListener(v -> saveRegistration(true));
        btnReportPayment.setOnClickListener(v -> reportPayment());

        load();
    }

    private void setupSpinners() {
        boolean bn = SessionManager.get(requireContext()).isBn();
        String[] tshirtLabels = TSHIRT_SIZES;
        spinnerMyTshirt.setAdapter(new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_spinner_item, tshirtLabels));

        String[] foodLabels = {
                getString(R.string.food_regular),
                getString(R.string.food_no_beef),
                getString(R.string.food_veg)
        };
        spinnerMyFood.setAdapter(new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_spinner_item, foodLabels));

        String[] payLabels = {
                getString(R.string.pay_bkash),
                getString(R.string.pay_nagad),
                getString(R.string.pay_rocket),
                getString(R.string.pay_bank),
                getString(R.string.pay_cash),
                getString(R.string.pay_other)
        };
        spinnerPayMethod.setAdapter(new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_spinner_item, payLabels));
    }

    private void load() {
        tvState.setVisibility(View.VISIBLE);
        tvState.setText(getString(R.string.common_loading));
        contentContainer.setVisibility(View.GONE);

        ApiClient.get().getEvent(new ApiCallback<EventInfo>() {
            @Override public void onSuccess(EventInfo e) {
                if (getContext() == null) return;
                eventInfo = e;
                loadRegistration();
            }
            @Override public void onError(String en, String b) {
                if (getContext() == null) return;
                loadRegistration();
            }
        });
    }

    private void loadRegistration() {
        ApiClient.get().getRegistration(new ApiCallback<Registration>() {
            @Override public void onSuccess(Registration r) {
                if (getContext() == null) return;
                currentReg = r;
                if (r.guests != null) guests = new ArrayList<>(r.guests);
                tvState.setVisibility(View.GONE);
                contentContainer.setVisibility(View.VISIBLE);
                bindRegistration(r);
            }
            @Override public void onError(String en, String b) {
                if (getContext() == null) return;
                // No registration yet — create a blank draft UI
                tvState.setVisibility(View.GONE);
                contentContainer.setVisibility(View.VISIBLE);
                bindNewRegistration();
            }
        });
    }

    private void bindNewRegistration() {
        boolean bn = SessionManager.get(requireContext()).isBn();
        bindTicket(null, bn);
        bannerStatus.setVisibility(View.GONE);
        btnSave.setVisibility(View.VISIBLE);
        btnSubmit.setVisibility(View.VISIBLE);
        cardPayment.setVisibility(View.GONE);
        renderGuests(bn);
        updateTotal(bn);
    }

    private void bindRegistration(Registration r) {
        boolean bn = SessionManager.get(requireContext()).isBn();
        bindTicket(r, bn);

        // Status banner
        if (!r.isDraft()) {
            bannerStatus.setVisibility(View.VISIBLE);
            if (r.isApproved()) {
                tvStatusTitle.setText(getString(R.string.guests_approved));
                tvStatusSub.setText(getString(R.string.guests_approved_sub));
            } else if (r.isSubmitted()) {
                tvStatusTitle.setText(getString(R.string.guests_submitted));
                tvStatusSub.setText(getString(R.string.guests_submitted_sub));
            } else if (r.isRejected()) {
                tvStatusTitle.setText(getString(R.string.guests_rejected));
                tvStatusSub.setText(r.memberReview != null && r.memberReview.note != null
                        ? r.memberReview.note : "");
            }
        } else {
            bannerStatus.setVisibility(View.GONE);
        }

        // Lock editing when submitted/approved
        boolean editable = r.isDraft();
        btnAddGuest.setEnabled(editable);
        btnSave.setVisibility(editable ? View.VISIBLE : View.GONE);
        btnSubmit.setVisibility(editable ? View.VISIBLE : View.GONE);

        // Pre-fill note
        if (r.memberNote != null) etNote.setText(r.memberNote);
        // Pre-fill tshirt
        for (int i = 0; i < TSHIRT_SIZES.length; i++) {
            if (TSHIRT_SIZES[i].equals(r.tshirtSize)) { spinnerMyTshirt.setSelection(i); break; }
        }
        // Pre-fill food
        for (int i = 0; i < FOOD_PREFS.length; i++) {
            if (FOOD_PREFS[i].equals(r.foodPref)) { spinnerMyFood.setSelection(i); break; }
        }

        // Payment section
        boolean showPay = r.isApproved() && !r.isPayConfirmed();
        cardPayment.setVisibility(showPay ? View.VISIBLE : View.GONE);

        if (showPay) loadCoordinator(bn);

        renderGuests(bn);
        updateTotal(bn);
    }

    private void bindTicket(Registration r, boolean bn) {
        if (eventInfo == null || eventInfo.ticketTypes == null) return;
        TicketType alumni = eventInfo.alumniTicket();
        if (alumni == null) return;

        tvMyTicketType.setText(alumni.displayName(bn));
        tvMyTicketNote.setText(alumni.displayNote(bn));
        tvMyTicketPrice.setText(alumni.amount > 0
                ? getString(R.string.common_taka_symbol) + " " + Fmt.money(alumni.amount, bn)
                : getString(R.string.guests_free));
    }

    private void loadCoordinator(boolean bn) {
        ApiClient.get().getCoordinators(new ApiCallback<List<Coordinator>>() {
            @Override public void onSuccess(List<Coordinator> cs) {
                if (getContext() == null || cs.isEmpty()) return;
                Coordinator c = cs.get(0);
                tvCoordinatorInfo.setText(c.displayName() + "\n" +
                        (bn ? "ফোন: " : "Phone: ") + c.phone);
            }
            @Override public void onError(String en, String b) {}
        });
    }

    private void renderGuests(boolean bn) {
        guestsContainer.removeAllViews();
        tvNoGuests.setVisibility(guests.isEmpty() ? View.VISIBLE : View.GONE);

        boolean editable = currentReg == null || currentReg.isDraft();
        LayoutInflater inflater = getLayoutInflater();
        for (int i = 0; i < guests.size(); i++) {
            final int idx = i;
            Guest g = guests.get(i);
            View item = inflater.inflate(R.layout.item_guest, guestsContainer, false);
            ((TextView) item.findViewById(R.id.tvGuestName)).setText(g.name);
            ((TextView) item.findViewById(R.id.tvGuestDetails)).setText(
                    relLabel(g.relation, bn) + " · " + Fmt.number(g.age, bn) + " " + getString(R.string.common_yrs));
            ((TextView) item.findViewById(R.id.tvGuestTicket)).setText(
                    g.ticketTypeCode != null ? g.ticketTypeCode : "");
            TextView btnRemove = item.findViewById(R.id.tvRemove);
            if (editable) {
                btnRemove.setOnClickListener(v -> {
                    guests.remove(idx);
                    renderGuests(bn);
                    updateTotal(bn);
                });
            } else {
                btnRemove.setVisibility(View.GONE);
            }
            guestsContainer.addView(item);
        }
    }

    private void updateTotal(boolean bn) {
        double total = 0;
        if (currentReg != null) total = currentReg.amountDue;
        costRows.removeAllViews();
        tvTotalAmount.setText(total > 0
                ? getString(R.string.common_taka_symbol) + " " + Fmt.money(total, bn)
                : getString(R.string.guests_free));
    }

    private void setBtnLoading(MaterialButton btn, ProgressBar pb, boolean loading) {
        btn.setEnabled(!loading);
        btn.setText(loading ? "" : btn.getTag() != null ? (CharSequence) btn.getTag() : "");
        pb.setVisibility(loading ? View.VISIBLE : View.GONE);
    }

    private void saveRegistration(boolean submit) {
        if (currentReg == null) {
            currentReg = new Registration();
        }
        currentReg.tshirtSize  = TSHIRT_SIZES[spinnerMyTshirt.getSelectedItemPosition()];
        currentReg.foodPref    = FOOD_PREFS[spinnerMyFood.getSelectedItemPosition()];
        currentReg.memberNote  = etNote.getText() != null ? etNote.getText().toString().trim() : "";
        currentReg.guests      = guests;

        // Tag buttons with their original text so setBtnLoading can restore it
        if (btnSave.getTag() == null) btnSave.setTag(btnSave.getText());
        if (btnSubmit.getTag() == null) btnSubmit.setTag(btnSubmit.getText());
        if (submit) {
            setBtnLoading(btnSubmit, pbSubmit, true);
        } else {
            setBtnLoading(btnSave, pbSave, true);
        }

        ApiCallback<Registration> cb = new ApiCallback<Registration>() {
            @Override public void onSuccess(Registration r) {
                if (getContext() == null) return;
                setBtnLoading(btnSave, pbSave, false);
                setBtnLoading(btnSubmit, pbSubmit, false);
                currentReg = r;
                if (r.guests != null) guests = new ArrayList<>(r.guests);
                bindRegistration(r);
                tvError.setVisibility(View.GONE);
                if (submit) {
                    submitRegistration();
                }
            }
            @Override public void onError(String en, String b) {
                if (getContext() == null) return;
                setBtnLoading(btnSave, pbSave, false);
                setBtnLoading(btnSubmit, pbSubmit, false);
                boolean bn = SessionManager.get(requireContext()).isBn();
                tvError.setText(bn ? b : en);
                tvError.setVisibility(View.VISIBLE);
            }
        };

        ApiClient.get().putRegistration(currentReg, cb);
    }

    private void submitRegistration() {
        if (btnSubmit.getTag() == null) btnSubmit.setTag(btnSubmit.getText());
        setBtnLoading(btnSubmit, pbSubmit, true);
        ApiClient.get().submitRegistration(new ApiCallback<Registration>() {
            @Override public void onSuccess(Registration r) {
                if (getContext() == null) return;
                setBtnLoading(btnSubmit, pbSubmit, false);
                currentReg = r;
                bindRegistration(r);
            }
            @Override public void onError(String en, String b) {
                if (getContext() == null) return;
                setBtnLoading(btnSubmit, pbSubmit, false);
                boolean bn = SessionManager.get(requireContext()).isBn();
                tvError.setText(bn ? b : en);
                tvError.setVisibility(View.VISIBLE);
            }
        });
    }

    private void reportPayment() {
        String method = PAY_METHODS[spinnerPayMethod.getSelectedItemPosition()];
        String ref    = etPayRef.getText() != null ? etPayRef.getText().toString().trim() : "";
        double amount = currentReg != null ? currentReg.amountDue : 0;

        if (btnReportPayment.getTag() == null) btnReportPayment.setTag(btnReportPayment.getText());
        setBtnLoading(btnReportPayment, pbReportPayment, true);

        ApiClient.get().reportPayment(method, ref, amount, new ApiCallback<Registration>() {
            @Override public void onSuccess(Registration r) {
                if (getContext() == null) return;
                setBtnLoading(btnReportPayment, pbReportPayment, false);
                currentReg = r;
                bindRegistration(r);
                cardPayment.setVisibility(View.GONE);
                bannerStatus.setVisibility(View.VISIBLE);
                tvStatusTitle.setText(getString(R.string.guests_pay_reported));
                tvStatusSub.setText("");
            }
            @Override public void onError(String en, String b) {
                if (getContext() == null) return;
                setBtnLoading(btnReportPayment, pbReportPayment, false);
                boolean bn = SessionManager.get(requireContext()).isBn();
                tvError.setText(bn ? b : en);
                tvError.setVisibility(View.VISIBLE);
            }
        });
    }

    private void showAddGuestDialog() {
        boolean bn = SessionManager.get(requireContext()).isBn();
        View dv = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_add_guest, null);

        TextInputEditText etName = dv.findViewById(R.id.etGuestName);
        TextInputEditText etAge  = dv.findViewById(R.id.etGuestAge);
        Spinner spRel            = dv.findViewById(R.id.spinnerRelation);
        Spinner spTshirt         = dv.findViewById(R.id.spinnerGuestTshirt);
        Spinner spFood           = dv.findViewById(R.id.spinnerGuestFood);

        String[] relLabels = {
                getString(R.string.rel_spouse), getString(R.string.rel_child),
                getString(R.string.rel_parent), getString(R.string.rel_sibling),
                getString(R.string.rel_other)
        };
        spRel.setAdapter(new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_spinner_item, relLabels));
        spTshirt.setAdapter(new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_spinner_item, TSHIRT_SIZES));
        String[] foodLabels = {
                getString(R.string.food_regular),
                getString(R.string.food_no_beef),
                getString(R.string.food_veg)
        };
        spFood.setAdapter(new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_spinner_item, foodLabels));

        new AlertDialog.Builder(requireContext())
                .setTitle(getString(R.string.guests_add_member))
                .setView(dv)
                .setPositiveButton(getString(R.string.cta_add), (d, w) -> {
                    String name = etName.getText() != null ? etName.getText().toString().trim() : "";
                    String ageStr = etAge.getText() != null ? etAge.getText().toString().trim() : "0";
                    if (name.isEmpty()) return;
                    Guest g = new Guest();
                    g.name     = name;
                    g.age      = Integer.parseInt(ageStr.isEmpty() ? "0" : ageStr);
                    g.relation = RELATIONS[spRel.getSelectedItemPosition()];
                    g.tshirtSize = TSHIRT_SIZES[spTshirt.getSelectedItemPosition()];
                    g.foodPref   = FOOD_PREFS[spFood.getSelectedItemPosition()];
                    guests.add(g);
                    renderGuests(bn);
                    updateTotal(bn);
                })
                .setNegativeButton(getString(R.string.common_cancel), null)
                .show();
    }

    private String relLabel(String rel, boolean bn) {
        if (rel == null) return "";
        switch (rel) {
            case "SPOUSE":  return getString(R.string.rel_spouse);
            case "CHILD":   return getString(R.string.rel_child);
            case "PARENT":  return getString(R.string.rel_parent);
            case "SIBLING": return getString(R.string.rel_sibling);
            default:        return getString(R.string.rel_other);
        }
    }
}
