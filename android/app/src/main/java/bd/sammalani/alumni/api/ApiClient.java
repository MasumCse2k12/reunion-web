package bd.sammalani.alumni.api;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import bd.sammalani.alumni.BuildConfig;
import bd.sammalani.alumni.model.Batch;
import bd.sammalani.alumni.model.Coordinator;
import bd.sammalani.alumni.model.DeletionPreview;
import bd.sammalani.alumni.model.EventInfo;
import bd.sammalani.alumni.model.Notice;
import bd.sammalani.alumni.model.Person;
import bd.sammalani.alumni.model.Registration;
import bd.sammalani.alumni.model.TicketType;
import bd.sammalani.alumni.session.SessionManager;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * Central HTTP API client.  Every API call runs on a background thread and
 * delivers its result via {@link ApiCallback} on the main thread.
 */
public class ApiClient {

    private static ApiClient instance;

    private final OkHttpClient http = new OkHttpClient();
    private final Gson gson = new Gson();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService exec = Executors.newCachedThreadPool();
    private final String base;
    private SessionManager session;

    /** Called on the main thread when a session cannot be refreshed. */
    public interface OnSessionExpiredListener {
        void onSessionExpired();
    }
    private static OnSessionExpiredListener sessionExpiredListener;
    public static void setOnSessionExpiredListener(OnSessionExpiredListener l) {
        sessionExpiredListener = l;
    }

    private ApiClient() {
        base = BuildConfig.API_BASE_URL;
    }

    public static synchronized ApiClient get() {
        if (instance == null) instance = new ApiClient();
        return instance;
    }

    public void init(Context ctx) {
        session = SessionManager.get(ctx);
    }

    // ── Low-level request helpers ───────────────────────────────────────

    private Request.Builder authed() {
        Request.Builder b = new Request.Builder();
        String token = session.getAccessToken();
        if (token != null) b.header("Authorization", "Bearer " + token);
        return b;
    }

    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private RequestBody jsonBody(Object obj) {
        return RequestBody.create(gson.toJson(obj), JSON);
    }

    /** Parses error body → (messageEn, messageBn). */
    private String[] parseError(Response res) {
        try {
            String body = res.body() != null ? res.body().string() : "";
            JsonObject obj = gson.fromJson(body, JsonObject.class);
            String en = obj.has("detail") ? obj.get("detail").getAsString()
                      : obj.has("message") ? obj.get("message").getAsString()
                      : "Something went wrong";
            String bn = obj.has("messageBn") ? obj.get("messageBn").getAsString()
                      : "কিছু একটা সমস্যা হয়েছে";
            return new String[]{en, bn};
        } catch (Exception e) {
            return new String[]{"Something went wrong", "কিছু একটা সমস্যা হয়েছে"};
        }
    }

    // Serialises token refresh so concurrent 401s don't trigger duplicate refresh calls.
    // Without this, two simultaneous authenticated requests can both get 401, both attempt
    // a refresh, and the second refresh fails (old token already consumed) → session expiry.
    private final Object refreshLock = new Object();

    /** Executes a request, refreshes token on 401, then delivers result on main thread. */
    private <T> void execute(Request req, Type type, boolean allowRefresh, ApiCallback<T> cb) {
        exec.submit(() -> {
            try {
                Response res = http.newCall(req).execute();

                if (res.code() == 401 && allowRefresh) {
                    // Remember which refresh token caused the 401, then take the lock.
                    // If another thread already refreshed by the time we enter the block,
                    // the saved refresh token will have changed — just retry without refreshing.
                    String refreshAtStart = session != null ? session.getRefreshToken() : null;
                    synchronized (refreshLock) {
                        String currentRefresh = session != null ? session.getRefreshToken() : null;
                        if (currentRefresh == null) {
                            notifySessionExpired();
                            return;
                        }
                        if (currentRefresh.equals(refreshAtStart)) {
                            // No other thread refreshed yet — do it ourselves.
                            JsonObject body = new JsonObject();
                            body.addProperty("refreshToken", currentRefresh);
                            Request refreshReq = new Request.Builder()
                                .url(base + "/api/v1/auth/refresh")
                                .post(jsonBody(body))
                                .build();
                            Response refreshRes = http.newCall(refreshReq).execute();
                            if (refreshRes.isSuccessful() && refreshRes.body() != null) {
                                JsonObject s = gson.fromJson(refreshRes.body().string(), JsonObject.class);
                                String newAccess  = s.get("accessToken").getAsString();
                                String newRefresh = s.get("refreshToken").getAsString();
                                session.saveTokens(newAccess, newRefresh);
                            } else {
                                session.clearTokens();
                                notifySessionExpired();
                                return;
                            }
                        }
                        // Either we just refreshed or another thread did — retry with current token.
                        String newAccess = session.getAccessToken();
                        Request retry = req.newBuilder()
                            .header("Authorization", "Bearer " + newAccess)
                            .build();
                        res = http.newCall(retry).execute();
                    }
                }

                if (res.code() == 204) {
                    deliverSuccess(cb, null);
                    return;
                }

                if (!res.isSuccessful()) {
                    String[] err = parseError(res);
                    deliverError(cb, err[0], err[1]);
                    return;
                }

                String bodyStr = res.body() != null ? res.body().string() : "null";
                T result = gson.fromJson(bodyStr, type);
                deliverSuccess(cb, result);

            } catch (IOException e) {
                deliverError(cb, "Network error. Check your connection.", "নেটওয়ার্ক সমস্যা। সংযোগ পরীক্ষা করুন।");
            }
        });
    }

    private <T> void deliverSuccess(ApiCallback<T> cb, T result) {
        main.post(() -> cb.onSuccess(result));
    }

    private <T> void deliverError(ApiCallback<T> cb, String en, String bn) {
        main.post(() -> cb.onError(en, bn));
    }

    private void notifySessionExpired() {
        main.post(() -> {
            if (sessionExpiredListener != null) sessionExpiredListener.onSessionExpired();
        });
    }

    private <T> void get(String path, Type type, boolean auth, ApiCallback<T> cb) {
        Request.Builder b = auth ? authed() : new Request.Builder();
        Request req = b.url(base + path).get().build();
        execute(req, type, auth, cb);
    }

    private <T> void post(String path, Object body, Type type, boolean auth, ApiCallback<T> cb) {
        Request.Builder b = auth ? authed() : new Request.Builder();
        Request req = b.url(base + path).post(body != null ? jsonBody(body) : RequestBody.create(new byte[0], null)).build();
        execute(req, type, auth, cb);
    }

    private <T> void patch(String path, Object body, Type type, ApiCallback<T> cb) {
        Request req = authed().url(base + path).patch(jsonBody(body)).build();
        execute(req, type, true, cb);
    }

    private <T> void put(String path, Object body, Type type, ApiCallback<T> cb) {
        Request req = authed().url(base + path).put(jsonBody(body)).build();
        execute(req, type, true, cb);
    }

    private <T> void delete(String path, Type type, ApiCallback<T> cb) {
        Request req = authed().url(base + path).delete().build();
        execute(req, type, true, cb);
    }

    // ── Auth ────────────────────────────────────────────────────────────

    public static class ChallengeResult {
        public String challengeId;
        public int expiresInSeconds;
        public String devCode; // non-null in dev builds only
    }

    public static class SessionResult {
        public String accessToken;
        public String refreshToken;
        public int expiresInSeconds;
        public Person person;
    }

    public void requestOtp(String phone, ApiCallback<ChallengeResult> cb) {
        JsonObject body = new JsonObject();
        body.addProperty("phone", phone);
        post("/api/v1/auth/otp/request", body, ChallengeResult.class, false, cb);
    }

    public void verifyOtp(String challengeId, String code, ApiCallback<SessionResult> cb) {
        JsonObject body = new JsonObject();
        body.addProperty("challengeId", challengeId);
        body.addProperty("code", code);
        post("/api/v1/auth/otp/verify", body, SessionResult.class, false, new ApiCallback<SessionResult>() {
            @Override public void onSuccess(SessionResult r) {
                session.saveTokens(r.accessToken, r.refreshToken);
                session.savePerson(r.person);
                cb.onSuccess(r);
            }
            @Override public void onError(String en, String bn) { cb.onError(en, bn); }
        });
    }

    public void logout(ApiCallback<Void> cb) {
        post("/api/v1/auth/logout", null, Void.class, true, new ApiCallback<Void>() {
            @Override public void onSuccess(Void r) { session.clearTokens(); cb.onSuccess(null); }
            @Override public void onError(String en, String bn) { session.clearTokens(); cb.onSuccess(null); }
        });
    }

    public void claimProfile(String personId, String phone, ApiCallback<ChallengeResult> cb) {
        JsonObject body = new JsonObject();
        body.addProperty("personId", personId);
        body.addProperty("phone", phone);
        post("/api/v1/public/claims", body, ChallengeResult.class, false, cb);
    }

    public void registerNew(String name, String nameBn, int batchYear, String phone, ApiCallback<ChallengeResult> cb) {
        JsonObject body = new JsonObject();
        body.addProperty("name", name);
        body.addProperty("nameBn", nameBn);
        body.addProperty("batchYear", batchYear);
        body.addProperty("phone", phone);
        post("/api/v1/public/register", body, ChallengeResult.class, false, cb);
    }

    // ── Me ──────────────────────────────────────────────────────────────

    public void getMe(ApiCallback<Person> cb) {
        get("/api/v1/me", Person.class, true, new ApiCallback<Person>() {
            @Override public void onSuccess(Person p) { session.savePerson(p); cb.onSuccess(p); }
            @Override public void onError(String en, String bn) { cb.onError(en, bn); }
        });
    }

    public void updateMe(JsonObject patch, ApiCallback<Person> cb) {
        patch("/api/v1/me", patch, Person.class, new ApiCallback<Person>() {
            @Override public void onSuccess(Person p) { session.savePerson(p); cb.onSuccess(p); }
            @Override public void onError(String en, String bn) { cb.onError(en, bn); }
        });
    }

    /** Convenience: build a patch from a Person object and call updateMe. */
    public void updateMe(Person person, ApiCallback<Person> cb) {
        JsonObject patch = new JsonObject();
        if (person.occupation != null) patch.addProperty("occupation", person.occupation);
        if (person.city       != null) patch.addProperty("city", person.city);
        if (person.email      != null) patch.addProperty("email", person.email);
        if (person.gender     != null) patch.addProperty("gender", person.gender);
        if (person.bloodGroup != null) patch.addProperty("bloodGroup", person.bloodGroup);
        updateMe(patch, cb);
    }

    /** Convenience: upload a photo from a content URI. */
    public void uploadPhoto(Context ctx, Uri uri, ApiCallback<Person> cb) {
        exec.submit(() -> {
            try {
                java.io.InputStream is = ctx.getContentResolver().openInputStream(uri);
                if (is == null) { deliverError(cb, "Cannot open image", "ছবি খোলা যায়নি"); return; }
                String name = resolveFileName(ctx, uri);
                File tmp = File.createTempFile("upload_", "_" + name, ctx.getCacheDir());
                try (java.io.FileOutputStream out = new java.io.FileOutputStream(tmp)) {
                    byte[] buf = new byte[8192];
                    int n;
                    while ((n = is.read(buf)) != -1) out.write(buf, 0, n);
                }
                is.close();
                String mimeType = ctx.getContentResolver().getType(uri);
                if (mimeType == null || !mimeType.startsWith("image/")) mimeType = "image/jpeg";
                RequestBody fileBody = RequestBody.create(tmp, MediaType.get(mimeType));
                MultipartBody multipart = new MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("file", name, fileBody)
                    .build();
                Request req = authed().url(base + "/api/v1/me/photo").post(multipart).build();
                Response res = http.newCall(req).execute();
                if (!res.isSuccessful()) {
                    String[] err = parseError(res); deliverError(cb, err[0], err[1]); return;
                }
                String body = res.body() != null ? res.body().string() : "null";
                Person p = gson.fromJson(body, Person.class);
                session.savePerson(p);
                deliverSuccess(cb, p);
                tmp.delete();
            } catch (IOException e) {
                deliverError(cb, "Upload failed. Check your connection.", "আপলোড ব্যর্থ হয়েছে।");
            }
        });
    }

    private String resolveFileName(Context ctx, Uri uri) {
        String name = "photo.jpg";
        try (Cursor cursor = ctx.getContentResolver().query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (idx >= 0) name = cursor.getString(idx);
            }
        } catch (Exception ignored) {}
        return name;
    }

    public void uploadPhoto(File file, ApiCallback<Person> cb) {
        exec.submit(() -> {
            try {
                String ext = file.getName().toLowerCase();
                String mimeType = ext.endsWith(".png") ? "image/png" : "image/jpeg";
                RequestBody fileBody = RequestBody.create(file, MediaType.get(mimeType));
                MultipartBody multipart = new MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("file", file.getName(), fileBody)
                    .build();
                Request req = authed().url(base + "/api/v1/me/photo").post(multipart).build();
                Response res = http.newCall(req).execute();
                if (!res.isSuccessful()) {
                    String[] err = parseError(res);
                    deliverError(cb, err[0], err[1]);
                    return;
                }
                String body = res.body() != null ? res.body().string() : "null";
                Person p = gson.fromJson(body, Person.class);
                session.savePerson(p);
                deliverSuccess(cb, p);
            } catch (IOException e) {
                deliverError(cb, "Upload failed. Check your connection.", "আপলোড ব্যর্থ হয়েছে।");
            }
        });
    }

    /** What deleting the account would cost — shown before asking to confirm. */
    public void deletionPreview(ApiCallback<DeletionPreview> cb) {
        get("/api/v1/me/deletion-preview", DeletionPreview.class, true, cb);
    }

    /**
     * Deletes the account and everything behind it. Required by Google Play for
     * any app that lets a user create one.
     * <p>
     * The session is dead the moment this returns, so the caller's only correct
     * next move is to clear the tokens and go back to the landing screen.
     */
    public void deleteAccount(ApiCallback<Void> cb) {
        delete("/api/v1/me", Void.class, cb);
    }

    public void deletePhoto(ApiCallback<Person> cb) {
        delete("/api/v1/me/photo", Person.class, new ApiCallback<Person>() {
            @Override public void onSuccess(Person p) { if (p != null) session.savePerson(p); cb.onSuccess(p); }
            @Override public void onError(String en, String bn) { cb.onError(en, bn); }
        });
    }

    // ── Batches ──────────────────────────────────────────────────────────

    public void getBatches(ApiCallback<List<Batch>> cb) {
        Type type = TypeToken.getParameterized(List.class, Batch.class).getType();
        get("/api/v1/batches", type, false, cb);
    }

    public static class Totals {
        public int roster;   // total people in roster
        public int claimed;  // alumni who joined (= "alumni found")
        public int batches;  // number of batches
        // convenience aliases
        public int alumni()      { return claimed; }
        public int registered()  { return 0; }  // not exposed by this endpoint
    }

    public void getTotals(ApiCallback<Totals> cb) {
        get("/api/v1/batches/totals", Totals.class, false, cb);
    }

    public void getBatchMembers(int year, ApiCallback<List<Person>> cb) {
        Type type = TypeToken.getParameterized(List.class, Person.class).getType();
        get("/api/v1/batches/" + year + "/members", type, true, cb);
    }

    public void getMissingFromBatch(int year, ApiCallback<List<Person>> cb) {
        Type type = TypeToken.getParameterized(List.class, Person.class).getType();
        get("/api/v1/batches/" + year + "/missing", type, true, cb);
    }

    public void lookupBatch(int year, String query, ApiCallback<List<Person>> cb) {
        Type type = TypeToken.getParameterized(List.class, Person.class).getType();
        String path = "/api/v1/public/lookup?batchYear=" + year;
        if (query != null && !query.trim().isEmpty()) {
            try {
                path += "&q=" + java.net.URLEncoder.encode(query.trim(), "UTF-8");
            } catch (Exception ignored) {}
        }
        get(path, type, false, cb);
    }

    // ── Event ────────────────────────────────────────────────────────────

    public void getEvent(ApiCallback<EventInfo> cb) {
        get("/api/v1/events/current", EventInfo.class, false, cb);
    }

    // ── Notices ──────────────────────────────────────────────────────────

    public void getNotices(ApiCallback<List<Notice>> cb) {
        Type type = TypeToken.getParameterized(List.class, Notice.class).getType();
        get("/api/v1/notices", type, false, cb);
    }

    // ── Registration ─────────────────────────────────────────────────────

    public void getRegistration(ApiCallback<Registration> cb) {
        get("/api/v1/me/registration", Registration.class, true, cb);
    }

    public void putRegistration(JsonObject body, ApiCallback<Registration> cb) {
        put("/api/v1/me/registration", body, Registration.class, cb);
    }

    /** Convenience: serialise a Registration object and PUT it. */
    public void putRegistration(Registration reg, ApiCallback<Registration> cb) {
        put("/api/v1/me/registration", reg, Registration.class, cb);
    }

    public void submitRegistration(ApiCallback<Registration> cb) {
        post("/api/v1/me/registration/submit", null, Registration.class, true, cb);
    }

    public void reportPayment(String method, String reference, double amount, ApiCallback<Registration> cb) {
        reportPayment(method, reference, amount, null, cb);
    }

    public void reportPayment(String method, String reference, double amount, String paidToId, ApiCallback<Registration> cb) {
        JsonObject body = new JsonObject();
        body.addProperty("method", method);
        body.addProperty("reference", reference);
        body.addProperty("amount", amount);
        if (paidToId != null && !paidToId.isEmpty()) body.addProperty("paidToId", paidToId);
        post("/api/v1/me/registration/payment-report", body, Registration.class, true, cb);
    }

    public void getCoordinators(ApiCallback<List<Coordinator>> cb) {
        Type type = TypeToken.getParameterized(List.class, Coordinator.class).getType();
        get("/api/v1/me/registration/coordinators", type, true, cb);
    }

    // ── Referrals ────────────────────────────────────────────────────────

    /** Convenience: referral using just phone (name left blank, batchYear from session person). */
    public void addReferral(String phone, ApiCallback<Void> cb) {
        Person me = session.getPerson();
        int year  = me != null ? me.batchYear : 0;
        addReferral("", phone, year, cb);
    }

    public void addReferral(String name, String phone, int batchYear, ApiCallback<Void> cb) {
        JsonObject body = new JsonObject();
        body.addProperty("name", name);
        body.addProperty("phone", phone);
        body.addProperty("batchYear", batchYear);
        post("/api/v1/referrals", body, Void.class, true, cb);
    }
}
