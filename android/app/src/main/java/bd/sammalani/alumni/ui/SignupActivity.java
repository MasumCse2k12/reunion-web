package bd.sammalani.alumni.ui;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.os.LocaleListCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import bd.sammalani.alumni.R;
import bd.sammalani.alumni.api.ApiCallback;
import bd.sammalani.alumni.api.ApiClient;
import bd.sammalani.alumni.model.Person;
import bd.sammalani.alumni.session.SessionManager;
import bd.sammalani.alumni.ui.adapter.MemberAdapter;

public class SignupActivity extends AppCompatActivity implements MemberAdapter.OnMemberClickListener {

    private View btnBack;
    private TextView tvLang;
    private LinearLayout stepYear, stepRoster, stepPhone, stepDone;

    // Step 1
    private TextInputEditText etYear;
    private TextView tvYearError;
    private MaterialButton btnSearchYear;

    // Step 2
    private TextView tvRosterBatch, tvRosterEmpty;
    private TextInputEditText etSearch;
    private RecyclerView rvRoster;
    private MemberAdapter rosterAdapter;
    private List<Person> allRoster = new ArrayList<>();
    private List<Person> filteredRoster = new ArrayList<>();

    // Step 3 - Phone sub-step A
    private LinearLayout substepPhoneEntry, substepOtp;
    private TextView tvConfirmName, tvPhoneError, tvOtpSentTo, tvOtpError;
    private TextInputEditText etPhone;
    private MaterialButton btnSendOtp, btnVerifyOtp;
    private ProgressBar pbSearchYear, pbSendOtp, pbVerifyOtp;
    private TextView tvResend;
    private LinearLayout otpBoxes;
    private EditText[] otpDigits = new EditText[6];

    // Step 4
    private TextView tvWelcomeName;
    private MaterialButton btnGoHome;

    // Not-on-roster path
    private MaterialButton btnNotOnRoster;
    private TextInputLayout tilNewName;
    private TextInputEditText etNewName;
    private boolean isRegisterNew = false;

    private Person selectedPerson;
    private String challengeId;
    private String phone;
    private int batchYear;
    private boolean busy = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_signup);

        btnBack        = findViewById(R.id.btnBack);
        tvLang         = findViewById(R.id.tvLang);
        stepYear       = findViewById(R.id.stepYear);
        stepRoster     = findViewById(R.id.stepRoster);
        stepPhone      = findViewById(R.id.stepPhone);
        stepDone       = findViewById(R.id.stepDone);

        etYear         = findViewById(R.id.etYear);
        tvYearError    = findViewById(R.id.tvYearError);
        btnSearchYear  = findViewById(R.id.btnSearchYear);

        tvRosterBatch  = findViewById(R.id.tvRosterBatch);
        tvRosterEmpty  = findViewById(R.id.tvRosterEmpty);
        etSearch       = findViewById(R.id.etSearch);
        rvRoster       = findViewById(R.id.rvRoster);

        tvConfirmName  = findViewById(R.id.tvConfirmName);
        tvPhoneError   = findViewById(R.id.tvPhoneError);
        tvOtpSentTo    = findViewById(R.id.tvOtpSentTo);
        tvOtpError     = findViewById(R.id.tvOtpError);
        etPhone        = findViewById(R.id.etPhone);
        btnSendOtp     = findViewById(R.id.btnSendOtp);
        btnVerifyOtp   = findViewById(R.id.btnVerifyOtp);
        pbSearchYear   = findViewById(R.id.pbSearchYear);
        pbSendOtp      = findViewById(R.id.pbSendOtp);
        pbVerifyOtp    = findViewById(R.id.pbVerifyOtp);
        tvResend       = findViewById(R.id.tvResend);
        substepPhoneEntry = findViewById(R.id.substepPhoneEntry);
        substepOtp     = findViewById(R.id.substepOtp);
        otpBoxes       = findViewById(R.id.otpBoxes);

        tvWelcomeName  = findViewById(R.id.tvWelcomeName);
        btnGoHome      = findViewById(R.id.btnGoHome);
        btnNotOnRoster = findViewById(R.id.btnNotOnRoster);
        tilNewName     = findViewById(R.id.tilNewName);
        etNewName      = findViewById(R.id.etNewName);

        boolean isBnLang = SessionManager.get(this).isBn();
        tvLang.setText(isBnLang ? getString(R.string.lang_en) : getString(R.string.lang_toggle));
        tvLang.setOnClickListener(v -> {
            SessionManager sm = SessionManager.get(this);
            String newLang = sm.isBn() ? "en" : "bn";
            sm.setLang(newLang);
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(newLang));
        });

        // Roster adapter
        boolean bn = SessionManager.get(this).isBn();
        rosterAdapter = new MemberAdapter(filteredRoster, bn, this);
        rvRoster.setLayoutManager(new LinearLayoutManager(this));
        rvRoster.setAdapter(rosterAdapter);
        rvRoster.setNestedScrollingEnabled(false);

        // Listeners
        btnSearchYear.setOnClickListener(v -> searchBatch());
        etYear.setOnKeyListener((v, kc, ev) -> {
            if (ev.getAction() == KeyEvent.ACTION_DOWN && kc == KeyEvent.KEYCODE_ENTER) {
                searchBatch(); return true;
            }
            return false;
        });

        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            @Override public void onTextChanged(CharSequence s, int st, int b, int c) {}
            @Override public void afterTextChanged(Editable s) { filterRoster(s.toString()); }
        });

        btnSendOtp.setOnClickListener(v -> sendOtp());
        btnVerifyOtp.setOnClickListener(v -> verifyOtp(null));
        tvResend.setOnClickListener(v -> sendOtp());
        btnGoHome.setOnClickListener(v -> goHome());
        btnNotOnRoster.setOnClickListener(v -> startRegisterNew());

        btnBack.setOnClickListener(v -> onBackStep());

        buildOtpBoxes();

        // Pre-fill batch year if launched from landing page search
        int prefilledYear = getIntent().getIntExtra("batchYear", 0);
        if (prefilledYear > 0) {
            etYear.setText(String.valueOf(prefilledYear));
            searchBatch();
        }
    }

    private void buildOtpBoxes() {
        float density = getResources().getDisplayMetrics().density;
        int screenW  = getResources().getDisplayMetrics().widthPixels;
        int hPad     = (int)(88 * density);
        int margin   = (int)(4 * density);
        int boxSize  = Math.min((int)(52 * density), (screenW - hPad - margin * 12) / 6);
        boxSize      = Math.max(boxSize, (int)(36 * density));

        for (int i = 0; i < 6; i++) {
            final int idx = i;
            EditText box = new EditText(this);
            box.setTextSize(20);
            box.setGravity(android.view.Gravity.CENTER);
            box.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
            box.setMaxLines(1);
            box.setBackground(getOtpBoxBg());
            box.setTextColor(androidx.core.content.ContextCompat.getColor(this, R.color.ink_900));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, boxSize);
            lp.weight = 1;
            lp.setMargins(margin, 0, margin, 0);
            box.setLayoutParams(lp);

            box.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
                @Override public void onTextChanged(CharSequence s, int st, int b, int c) {}
                @Override public void afterTextChanged(Editable s) {
                    String current = s.toString();
                    if (current.isEmpty()) return;

                    String digits = current.replaceAll("[^0-9]", "");
                    String target = digits.isEmpty() ? "" : String.valueOf(digits.charAt(digits.length() - 1));

                    if (!current.equals(target)) {
                        box.setText(target);
                        return;
                    }

                    box.setSelection(current.length());
                    if (!target.isEmpty() && idx < 5) otpDigits[idx + 1].requestFocus();
                    checkAutoVerify();
                }
            });
            box.setOnKeyListener((v, kc, ev) -> {
                if (ev.getAction() == KeyEvent.ACTION_DOWN && kc == KeyEvent.KEYCODE_DEL
                        && box.getText().toString().isEmpty() && idx > 0) {
                    otpDigits[idx - 1].requestFocus();
                    otpDigits[idx - 1].setText("");
                    return true;
                }
                return false;
            });

            otpDigits[i] = box;
            otpBoxes.addView(box);
        }
    }

    private android.graphics.drawable.Drawable getOtpBoxBg() {
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
        bg.setCornerRadius(12 * getResources().getDisplayMetrics().density);
        bg.setColor(android.graphics.Color.WHITE);
        bg.setStroke((int)(2 * getResources().getDisplayMetrics().density), androidx.core.content.ContextCompat.getColor(this, R.color.paper_2));
        return bg;
    }

    private void checkAutoVerify() {
        StringBuilder sb = new StringBuilder();
        for (EditText d : otpDigits) sb.append(d.getText().toString());
        if (sb.length() == 6) verifyOtp(sb.toString());
    }

    private String getOtpCode() {
        StringBuilder sb = new StringBuilder();
        for (EditText d : otpDigits) sb.append(d.getText().toString());
        return sb.toString();
    }

    // ── Step 1: search roster ──────────────────────────────────────────────

    private void searchBatch() {
        if (busy) return;
        String yearStr = etYear.getText() != null ? etYear.getText().toString().trim() : "";
        if (yearStr.length() != 4) {
            tvYearError.setText(getString(R.string.signup_year_placeholder));
            tvYearError.setVisibility(View.VISIBLE);
            return;
        }
        try { batchYear = Integer.parseInt(yearStr); } catch (NumberFormatException e) {
            tvYearError.setText(getString(R.string.signup_year_placeholder));
            tvYearError.setVisibility(View.VISIBLE);
            return;
        }
        tvYearError.setVisibility(View.GONE);
        setBusy(true);

        ApiClient.get().lookupBatch(batchYear, null, new ApiCallback<List<Person>>() {
            @Override public void onSuccess(List<Person> people) {
                setBusy(false);
                allRoster.clear();
                allRoster.addAll(people);
                filteredRoster.clear();
                filteredRoster.addAll(allRoster);
                rosterAdapter.setData(filteredRoster);

                boolean bn = SessionManager.get(SignupActivity.this).isBn();
                tvRosterBatch.setText((bn ? "ব্যাচ " : "Batch ") + batchYear);
                tvRosterEmpty.setVisibility(allRoster.isEmpty() ? View.VISIBLE : View.GONE);
                showStep(2);
            }
            @Override public void onError(String en, String bn) {
                setBusy(false);
                tvYearError.setText(SessionManager.get(SignupActivity.this).isBn() ? bn : en);
                tvYearError.setVisibility(View.VISIBLE);
            }
        });
    }

    private void filterRoster(String query) {
        filteredRoster.clear();
        if (query.isEmpty()) {
            filteredRoster.addAll(allRoster);
        } else {
            String q = query.toLowerCase();
            for (Person p : allRoster) {
                if (p.name != null && p.name.toLowerCase().contains(q)) filteredRoster.add(p);
                else if (p.nameBn != null && p.nameBn.contains(query)) filteredRoster.add(p);
            }
        }
        rosterAdapter.setData(filteredRoster);
        tvRosterEmpty.setVisibility(filteredRoster.isEmpty() ? View.VISIBLE : View.GONE);
    }

    // ── Step 2: person selected / not on roster ────────────────────────────

    @Override
    public void onMemberClick(Person person) {
        isRegisterNew = false;
        selectedPerson = person;
        tilNewName.setVisibility(View.GONE);
        etNewName.setText("");
        tvConfirmName.setText(person.displayName());
        substepPhoneEntry.setVisibility(View.VISIBLE);
        substepOtp.setVisibility(View.GONE);
        showStep(3);
    }

    private void startRegisterNew() {
        isRegisterNew = true;
        selectedPerson = null;
        boolean bn = SessionManager.get(this).isBn();
        tvConfirmName.setText(bn ? "নতুন সদস্য নিবন্ধন" : "Register as new member");
        tilNewName.setVisibility(View.VISIBLE);
        substepPhoneEntry.setVisibility(View.VISIBLE);
        substepOtp.setVisibility(View.GONE);
        showStep(3);
    }

    // ── Step 3: phone → OTP ────────────────────────────────────────────────

    private void sendOtp() {
        if (busy) return;
        phone = etPhone.getText() != null ? etPhone.getText().toString().trim() : "";
        if (phone.isEmpty()) {
            tvPhoneError.setText(getString(R.string.auth_phone_label));
            tvPhoneError.setVisibility(View.VISIBLE);
            return;
        }
        if (isRegisterNew) {
            String name = etNewName.getText() != null ? etNewName.getText().toString().trim() : "";
            if (name.isEmpty()) {
                tvPhoneError.setText(getString(R.string.signup_new_name));
                tvPhoneError.setVisibility(View.VISIBLE);
                return;
            }
        }
        tvPhoneError.setVisibility(View.GONE);
        setBusy(true);

        ApiCallback<ApiClient.ChallengeResult> onChallenge = new ApiCallback<ApiClient.ChallengeResult>() {
            @Override public void onSuccess(ApiClient.ChallengeResult r) {
                challengeId = r.challengeId;
                setBusy(false);
                boolean bn = SessionManager.get(SignupActivity.this).isBn();
                tvOtpSentTo.setText((bn ? "কোড পাঠানো হয়েছে: " : "Code sent to ") + phone);
                substepPhoneEntry.setVisibility(View.GONE);
                substepOtp.setVisibility(View.VISIBLE);
                otpDigits[0].postDelayed(() -> {
                    otpDigits[0].requestFocus();
                    showKeyboard(otpDigits[0]);
                }, 100);
            }
            @Override public void onError(String en, String bn) {
                setBusy(false);
                boolean isBn = SessionManager.get(SignupActivity.this).isBn();
                tvPhoneError.setText(isBn ? bn : en);
                tvPhoneError.setVisibility(View.VISIBLE);
            }
        };

        if (isRegisterNew) {
            String name = etNewName.getText().toString().trim();
            ApiClient.get().registerNew(name, "", batchYear, phone, onChallenge);
        } else if (selectedPerson != null && "SEEDED".equals(selectedPerson.status)) {
            ApiClient.get().claimProfile(selectedPerson.id, phone, onChallenge);
        } else {
            ApiClient.get().requestOtp(phone, onChallenge);
        }
    }

    private void verifyOtp(String fullCode) {
        if (busy) return;
        String code = fullCode != null ? fullCode : getOtpCode();
        if (code.length() != 6) return;
        setBusy(true);
        tvOtpError.setVisibility(View.GONE);

        ApiClient.get().verifyOtp(challengeId, code, new ApiCallback<ApiClient.SessionResult>() {
            @Override public void onSuccess(ApiClient.SessionResult r) {
                setBusy(false);
                hideKeyboard();
                if (r.person != null) {
                    SessionManager.get(SignupActivity.this).savePerson(r.person);
                    tvWelcomeName.setText(r.person.displayName());
                    showStep(4);
                } else {
                    goHome();
                }
            }
            @Override public void onError(String en, String bn) {
                setBusy(false);
                boolean isBn = SessionManager.get(SignupActivity.this).isBn();
                tvOtpError.setText(isBn ? bn : en);
                tvOtpError.setVisibility(View.VISIBLE);
                for (EditText d : otpDigits) d.setText("");
                otpDigits[0].requestFocus();
            }
        });
    }

    // ── Step 4 ─────────────────────────────────────────────────────────────

    private void goHome() {
        startActivity(new Intent(this, HomeActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
        finish();
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private void showStep(int step) {
        stepYear.setVisibility(step == 1 ? View.VISIBLE : View.GONE);
        stepRoster.setVisibility(step == 2 ? View.VISIBLE : View.GONE);
        stepPhone.setVisibility(step == 3 ? View.VISIBLE : View.GONE);
        stepDone.setVisibility(step == 4 ? View.VISIBLE : View.GONE);
        btnBack.setVisibility(step > 1 && step < 4 ? View.VISIBLE : View.GONE);
    }

    private void onBackStep() {
        if (stepPhone.getVisibility() == View.VISIBLE) {
            if (substepOtp.getVisibility() == View.VISIBLE) {
                substepOtp.setVisibility(View.GONE);
                substepPhoneEntry.setVisibility(View.VISIBLE);
            } else {
                isRegisterNew = false;
                tilNewName.setVisibility(View.GONE);
                showStep(2);
            }
        } else if (stepRoster.getVisibility() == View.VISIBLE) {
            showStep(1);
        }
    }

    private void setBusy(boolean b) {
        busy = b;
        btnSearchYear.setEnabled(!b);
        btnSendOtp.setEnabled(!b);
        btnVerifyOtp.setEnabled(!b);
        btnSearchYear.setText(b && stepYear.getVisibility() == View.VISIBLE ? "" : getString(R.string.cta_continue));
        btnSendOtp.setText(b && substepPhoneEntry.getVisibility() == View.VISIBLE ? "" : getString(R.string.auth_send_code));
        btnVerifyOtp.setText(b && substepOtp.getVisibility() == View.VISIBLE ? "" : getString(R.string.auth_verify));
        pbSearchYear.setVisibility(b && stepYear.getVisibility() == View.VISIBLE ? View.VISIBLE : View.GONE);
        pbSendOtp.setVisibility(b && substepPhoneEntry.getVisibility() == View.VISIBLE ? View.VISIBLE : View.GONE);
        pbVerifyOtp.setVisibility(b && substepOtp.getVisibility() == View.VISIBLE ? View.VISIBLE : View.GONE);
    }

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        View focus = getCurrentFocus();
        if (focus != null) imm.hideSoftInputFromWindow(focus.getWindowToken(), 0);
    }

    private void showKeyboard(View v) {
        ((InputMethodManager) getSystemService(INPUT_METHOD_SERVICE))
                .showSoftInput(v, InputMethodManager.SHOW_FORCED);
    }
}
