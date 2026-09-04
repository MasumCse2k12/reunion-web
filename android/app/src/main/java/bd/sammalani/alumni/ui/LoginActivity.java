package bd.sammalani.alumni.ui;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.os.LocaleListCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

import bd.sammalani.alumni.R;
import bd.sammalani.alumni.api.ApiCallback;
import bd.sammalani.alumni.api.ApiClient;
import bd.sammalani.alumni.session.SessionManager;

public class LoginActivity extends AppCompatActivity {

    private LinearLayout stepPhone, stepOtp;
    private TextInputEditText etPhone;
    private TextView tvPhoneError, tvSignupHint, tvOtpPhone, tvOtpError, tvResend, tvGoSignup, tvLang;
    private MaterialButton btnSendCode, btnVerify;
    private ProgressBar pbSendCode, pbVerify;
    private View btnBack;
    private LinearLayout otpBoxes;
    private EditText[] otpDigits = new EditText[6];

    private String challengeId;
    private String phone;
    private boolean busy = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        stepPhone    = findViewById(R.id.stepPhone);
        stepOtp      = findViewById(R.id.stepOtp);
        etPhone      = findViewById(R.id.etPhone);
        tvPhoneError = findViewById(R.id.tvPhoneError);
        tvSignupHint = findViewById(R.id.tvSignupHint);
        tvOtpPhone   = findViewById(R.id.tvOtpPhone);
        tvOtpError   = findViewById(R.id.tvOtpError);
        tvResend     = findViewById(R.id.tvResend);
        btnSendCode  = findViewById(R.id.btnSendCode);
        btnVerify    = findViewById(R.id.btnVerify);
        pbSendCode   = findViewById(R.id.pbSendCode);
        pbVerify     = findViewById(R.id.pbVerify);
        btnBack      = findViewById(R.id.btnBack);
        otpBoxes     = findViewById(R.id.otpBoxes);
        tvGoSignup   = findViewById(R.id.tvGoSignup);
        tvLang       = findViewById(R.id.tvLang);

        boolean isBn = SessionManager.get(this).isBn();
        tvLang.setText(isBn ? getString(R.string.lang_en) : getString(R.string.lang_toggle));
        tvLang.setOnClickListener(v -> {
            SessionManager sm = SessionManager.get(this);
            String newLang = sm.isBn() ? "en" : "bn";
            sm.setLang(newLang);
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(newLang));
        });

        buildOtpBoxes();

        etPhone.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_ENTER) {
                sendCode(); return true;
            }
            return false;
        });

        btnSendCode.setOnClickListener(v -> sendCode());
        btnVerify.setOnClickListener(v -> verify(null));
        tvResend.setOnClickListener(v -> sendCode());
        btnBack.setOnClickListener(v -> showPhoneStep());
        tvGoSignup.setOnClickListener(v -> {
            startActivity(new Intent(this, SignupActivity.class));
            finish();
        });
    }

    private void buildOtpBoxes() {
        float density = getResources().getDisplayMetrics().density;
        // Use weight=1 so all 6 boxes share the container width equally.
        // Height is capped at 52dp but calculated from available space to stay square-ish.
        int screenW  = getResources().getDisplayMetrics().widthPixels;
        int hPad     = (int)(88 * density);  // ~24dp outer + 20dp card padding each side
        int margin   = (int)(4 * density);
        int boxSize  = Math.min((int)(52 * density), (screenW - hPad - margin * 12) / 6);
        boxSize      = Math.max(boxSize, (int)(36 * density)); // never go below 36dp

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

                    // Strip non-digits and keep only the last digit
                    String digits = current.replaceAll("[^0-9]", "");
                    String target = digits.isEmpty() ? "" : String.valueOf(digits.charAt(digits.length() - 1));

                    if (!current.equals(target)) {
                        // Text needs cleaning — setText re-triggers afterTextChanged with `target`
                        box.setText(target);
                        return; // side-effects handled in the next (clean) callback
                    }

                    // Text is already a single valid digit — handle focus and completion
                    box.setSelection(current.length());
                    if (!target.isEmpty() && idx < 5) otpDigits[idx + 1].requestFocus();
                    checkAutoVerify();
                }
            });

            box.setOnKeyListener((v, keyCode, event) -> {
                if (event.getAction() == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_DEL
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
        if (sb.length() == 6) verify(sb.toString());
    }

    private String getOtpCode() {
        StringBuilder sb = new StringBuilder();
        for (EditText d : otpDigits) sb.append(d.getText().toString());
        return sb.toString();
    }

    private void sendCode() {
        if (busy) return;
        phone = etPhone.getText() != null ? etPhone.getText().toString().trim() : "";
        if (phone.isEmpty()) { showPhoneError(getString(R.string.auth_phone_label)); return; }
        tvPhoneError.setVisibility(View.GONE);
        tvSignupHint.setVisibility(View.GONE);
        setBusy(true);
        ApiClient.get().requestOtp(phone, new ApiCallback<ApiClient.ChallengeResult>() {
            @Override public void onSuccess(ApiClient.ChallengeResult r) {
                challengeId = r.challengeId;
                setBusy(false);
                showOtpStep();
            }
            @Override public void onError(String en, String bn) {
                setBusy(false);
                boolean isBn = SessionManager.get(LoginActivity.this).isBn();
                String msg = isBn ? bn : en;
                if (en.contains("not_found") || en.toLowerCase().contains("not found")) {
                    tvSignupHint.setText(isBn ? "প্রথমবার? নাম খুঁজুন" : "First time? Find your name");
                    tvSignupHint.setVisibility(View.VISIBLE);
                } else {
                    showPhoneError(msg);
                }
            }
        });
    }

    private void verify(String fullCode) {
        if (busy) return;
        String code = fullCode != null ? fullCode : getOtpCode();
        if (code.length() != 6) return;
        setBusy(true);
        tvOtpError.setVisibility(View.GONE);
        ApiClient.get().verifyOtp(challengeId, code, new ApiCallback<ApiClient.SessionResult>() {
            @Override public void onSuccess(ApiClient.SessionResult r) {
                setBusy(false);
                hideKeyboard();
                startActivity(new Intent(LoginActivity.this, HomeActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
                finish();
            }
            @Override public void onError(String en, String bn) {
                setBusy(false);
                boolean isBn = SessionManager.get(LoginActivity.this).isBn();
                showOtpError(isBn ? bn : en);
                for (EditText d : otpDigits) d.setText("");
                otpDigits[0].requestFocus();
            }
        });
    }

    private void showOtpStep() {
        stepPhone.setVisibility(View.GONE);
        stepOtp.setVisibility(View.VISIBLE);
        btnBack.setVisibility(View.VISIBLE);
        boolean isBn = SessionManager.get(this).isBn();
        tvOtpPhone.setText((isBn ? "কোড পাঠানো হয়েছে: " : "Code sent to ") + phone);
        // Post so the view is fully laid out before requesting focus/keyboard
        otpDigits[0].postDelayed(() -> {
            otpDigits[0].requestFocus();
            showKeyboard(otpDigits[0]);
        }, 100);
    }

    private void showPhoneStep() {
        stepOtp.setVisibility(View.GONE);
        stepPhone.setVisibility(View.VISIBLE);
        btnBack.setVisibility(View.GONE);
        tvOtpError.setVisibility(View.GONE);
        for (EditText d : otpDigits) d.setText("");
    }

    private void showPhoneError(String msg) {
        tvPhoneError.setText(msg);
        tvPhoneError.setVisibility(View.VISIBLE);
    }

    private void showOtpError(String msg) {
        tvOtpError.setText(msg);
        tvOtpError.setVisibility(View.VISIBLE);
    }

    private void setBusy(boolean b) {
        busy = b;
        btnSendCode.setEnabled(!b);
        btnVerify.setEnabled(!b);
        btnSendCode.setText(b ? "" : getString(R.string.auth_send_code));
        btnVerify.setText(b ? "" : getString(R.string.auth_verify));
        pbSendCode.setVisibility(b && stepPhone.getVisibility() == View.VISIBLE ? View.VISIBLE : View.GONE);
        pbVerify.setVisibility(b && stepOtp.getVisibility() == View.VISIBLE ? View.VISIBLE : View.GONE);
    }

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        View focus = getCurrentFocus();
        if (focus != null) imm.hideSoftInputFromWindow(focus.getWindowToken(), 0);
    }

    private void showKeyboard(View v) {
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        imm.showSoftInput(v, InputMethodManager.SHOW_FORCED);
    }
}
