package bd.sammalani.alumni.ui.fragment;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.content.FileProvider;
import androidx.core.os.LocaleListCompat;
import androidx.fragment.app.Fragment;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import bd.sammalani.alumni.R;
import bd.sammalani.alumni.api.ApiCallback;
import bd.sammalani.alumni.api.ApiClient;
import bd.sammalani.alumni.model.Person;
import bd.sammalani.alumni.session.SessionManager;
import bd.sammalani.alumni.ui.LandingActivity;
import bd.sammalani.alumni.util.AvatarView;
import bd.sammalani.alumni.util.Fmt;

public class ProfileFragment extends Fragment {

    private static final String[] GENDERS    = {"", "MALE", "FEMALE", "OTHER"};
    private static final String[] BLOOD_GROUPS = {"", "A+", "A-", "B+", "B-", "AB+", "AB-", "O+", "O-"};

    private ImageView ivPhoto;
    private TextView tvName, tvBatch, tvUploadStatus, tvSaveStatus;
    private TextInputEditText etOccupation, etCity, etEmail;
    private Spinner spinnerGender, spinnerBlood;
    private MaterialButton btnChangePhoto, btnRemovePhoto, btnSave, btnToggleLang, btnLogout;
    private ProgressBar pbSave;

    private Uri cameraUri;
    private Person currentPerson;

    private final ActivityResultLauncher<Intent> pickPhotoLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    uploadPhoto(result.getData().getData());
                }
            });

    private final ActivityResultLauncher<Intent> cameraLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == Activity.RESULT_OK && cameraUri != null) {
                    uploadPhoto(cameraUri);
                }
            });

    private final ActivityResultLauncher<String> cameraPermLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) launchCamera();
            });

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_profile, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        ivPhoto        = view.findViewById(R.id.ivProfilePhoto);
        tvName         = view.findViewById(R.id.tvProfileName);
        tvBatch        = view.findViewById(R.id.tvProfileBatch);
        tvUploadStatus = view.findViewById(R.id.tvUploadStatus);
        tvSaveStatus   = view.findViewById(R.id.tvSaveStatus);
        etOccupation   = view.findViewById(R.id.etOccupation);
        etCity         = view.findViewById(R.id.etCity);
        etEmail        = view.findViewById(R.id.etEmail);
        spinnerGender  = view.findViewById(R.id.spinnerGender);
        spinnerBlood   = view.findViewById(R.id.spinnerBlood);
        btnChangePhoto = view.findViewById(R.id.btnChangePhoto);
        btnRemovePhoto = view.findViewById(R.id.btnRemovePhoto);
        btnSave        = view.findViewById(R.id.btnSaveProfile);
        pbSave         = view.findViewById(R.id.pbSaveProfile);
        btnToggleLang  = view.findViewById(R.id.btnToggleLang);
        btnLogout      = view.findViewById(R.id.btnLogout);

        setupSpinners();

        btnChangePhoto.setOnClickListener(v -> showPhotoPicker());
        btnRemovePhoto.setOnClickListener(v -> removePhoto());
        btnSave.setOnClickListener(v -> saveProfile());
        boolean isBn = SessionManager.get(requireContext()).isBn();
        btnToggleLang.setText(isBn ? getString(R.string.lang_en) : getString(R.string.lang_toggle));
        btnToggleLang.setOnClickListener(v -> toggleLang());
        btnLogout.setOnClickListener(v -> logout());

        loadProfile();
    }

    private void setupSpinners() {
        SessionManager sm = SessionManager.get(requireContext());
        boolean bn = sm.isBn();

        String[] genderLabels = {
                getString(R.string.profile_not_set),
                getString(R.string.gender_male),
                getString(R.string.gender_female),
                getString(R.string.gender_other)
        };
        spinnerGender.setAdapter(new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_spinner_item, genderLabels));

        spinnerBlood.setAdapter(new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_spinner_item, BLOOD_GROUPS));
    }

    private void loadProfile() {
        SessionManager sm = SessionManager.get(requireContext());
        boolean bn = sm.isBn();
        Person cached = sm.getPerson();
        if (cached != null) bindPerson(cached, bn);

        ApiClient.get().getMe(new ApiCallback<Person>() {
            @Override public void onSuccess(Person p) {
                if (getContext() == null) return;
                sm.savePerson(p);
                bindPerson(p, bn);
            }
            @Override public void onError(String en, String b) { /* use cached */ }
        });
    }

    private void bindPerson(Person p, boolean bn) {
        currentPerson = p;
        tvName.setText(p.displayName());
        tvBatch.setText((bn ? "SSC ব্যাচ " : "SSC Batch ") + Fmt.year(p.batchYear, bn));
        AvatarView.load(requireContext(), ivPhoto, p.photoUrl, p.displayName());

        btnRemovePhoto.setVisibility(
                (p.photoUrl != null && !p.photoUrl.isEmpty()) ? View.VISIBLE : View.GONE);

        if (p.occupation != null) etOccupation.setText(p.occupation);
        if (p.city != null)       etCity.setText(p.city);
        if (p.email != null)      etEmail.setText(p.email);

        // Gender spinner
        for (int i = 0; i < GENDERS.length; i++) {
            if (GENDERS[i].equals(p.gender)) { spinnerGender.setSelection(i); break; }
        }
        // Blood spinner
        for (int i = 0; i < BLOOD_GROUPS.length; i++) {
            if (BLOOD_GROUPS[i].equals(p.bloodGroup)) { spinnerBlood.setSelection(i); break; }
        }
    }

    private void saveProfile() {
        if (currentPerson == null) return;
        currentPerson.occupation = getText(etOccupation);
        currentPerson.city       = getText(etCity);
        currentPerson.email      = getText(etEmail);
        int gi = spinnerGender.getSelectedItemPosition();
        currentPerson.gender     = gi > 0 ? GENDERS[gi] : null;
        int bi = spinnerBlood.getSelectedItemPosition();
        currentPerson.bloodGroup = bi > 0 ? BLOOD_GROUPS[bi] : null;

        btnSave.setEnabled(false);
        btnSave.setText("");
        pbSave.setVisibility(View.VISIBLE);
        ApiClient.get().updateMe(currentPerson, new ApiCallback<Person>() {
            @Override public void onSuccess(Person p) {
                if (getContext() == null) return;
                btnSave.setEnabled(true);
                btnSave.setText(getString(R.string.profile_save));
                pbSave.setVisibility(View.GONE);
                SessionManager.get(requireContext()).savePerson(p);
                boolean bn = SessionManager.get(requireContext()).isBn();
                tvSaveStatus.setText(getString(R.string.profile_saved));
                tvSaveStatus.setVisibility(View.VISIBLE);
                tvSaveStatus.postDelayed(() -> {
                    if (tvSaveStatus != null) tvSaveStatus.setVisibility(View.GONE);
                }, 3000);
            }
            @Override public void onError(String en, String b) {
                if (getContext() == null) return;
                btnSave.setEnabled(true);
                btnSave.setText(getString(R.string.profile_save));
                pbSave.setVisibility(View.GONE);
                boolean isBn = SessionManager.get(requireContext()).isBn();
                tvSaveStatus.setText(isBn ? b : en);
                tvSaveStatus.setTextColor(androidx.core.content.ContextCompat.getColor(requireContext(), R.color.red_500));
                tvSaveStatus.setVisibility(View.VISIBLE);
            }
        });
    }

    private void showPhotoPicker() {
        String[] options = {getString(R.string.choose_photo), "Camera"};
        new android.app.AlertDialog.Builder(requireContext())
                .setItems(options, (d, which) -> {
                    if (which == 0) openGallery();
                    else openCamera();
                })
                .show();
    }

    private void openGallery() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("image/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        pickPhotoLauncher.launch(intent);
    }

    private void openCamera() {
        if (androidx.core.content.ContextCompat.checkSelfPermission(requireContext(),
                android.Manifest.permission.CAMERA)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            cameraPermLauncher.launch(android.Manifest.permission.CAMERA);
            return;
        }
        launchCamera();
    }

    private void launchCamera() {
        try {
            File photoFile = createImageFile();
            cameraUri = FileProvider.getUriForFile(requireContext(),
                    requireContext().getPackageName() + ".fileprovider", photoFile);
            Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            intent.putExtra(MediaStore.EXTRA_OUTPUT, cameraUri);
            cameraLauncher.launch(intent);
        } catch (IOException e) {
            // ignore
        }
    }

    private File createImageFile() throws IOException {
        String stamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        File storageDir = requireContext().getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        return File.createTempFile("PHOTO_" + stamp, ".jpg", storageDir);
    }

    private void uploadPhoto(Uri uri) {
        if (uri == null) return;
        tvUploadStatus.setText(getString(R.string.profile_uploading));
        tvUploadStatus.setVisibility(View.VISIBLE);
        btnChangePhoto.setEnabled(false);

        ApiClient.get().uploadPhoto(requireContext(), uri, new ApiCallback<Person>() {
            @Override public void onSuccess(Person p) {
                if (getContext() == null) return;
                SessionManager.get(requireContext()).savePerson(p);
                bindPerson(p, SessionManager.get(requireContext()).isBn());
                tvUploadStatus.setVisibility(View.GONE);
                btnChangePhoto.setEnabled(true);
            }
            @Override public void onError(String en, String b) {
                if (getContext() == null) return;
                boolean bn = SessionManager.get(requireContext()).isBn();
                tvUploadStatus.setText(bn ? b : en);
                btnChangePhoto.setEnabled(true);
            }
        });
    }

    private void removePhoto() {
        ApiClient.get().deletePhoto(new ApiCallback<Person>() {
            @Override public void onSuccess(Person p) {
                if (getContext() == null) return;
                SessionManager.get(requireContext()).savePerson(p);
                bindPerson(p, SessionManager.get(requireContext()).isBn());
            }
            @Override public void onError(String en, String b) { /* ignore */ }
        });
    }

    private void toggleLang() {
        SessionManager sm = SessionManager.get(requireContext());
        String newLang = sm.isBn() ? "en" : "bn";
        sm.setLang(newLang);
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(newLang));
    }

    private void logout() {
        new android.app.AlertDialog.Builder(requireContext())
                .setTitle(getString(R.string.auth_logout))
                .setMessage(SessionManager.get(requireContext()).isBn()
                        ? "লগ আউট করবেন?" : "Log out of your account?")
                .setPositiveButton(getString(R.string.auth_logout), (d, w) -> doLogout())
                .setNegativeButton(getString(R.string.common_cancel), null)
                .show();
    }

    private void doLogout() {
        ApiClient.get().logout(new ApiCallback<Void>() {
            @Override public void onSuccess(Void v) {}
            @Override public void onError(String en, String b) {}
        });
        SessionManager.get(requireContext()).clearTokens();
        Intent intent = new Intent(requireContext(), LandingActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
    }

    private String getText(TextInputEditText et) {
        return et.getText() != null ? et.getText().toString().trim() : "";
    }
}
