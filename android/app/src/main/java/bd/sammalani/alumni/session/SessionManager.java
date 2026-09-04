package bd.sammalani.alumni.session;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.gson.Gson;

import bd.sammalani.alumni.model.Person;

public class SessionManager {
    private static final String PREFS = "sammalani_session";
    private static final String KEY_ACCESS  = "member.access";
    private static final String KEY_REFRESH = "member.refresh";
    private static final String KEY_PERSON  = "member.person";
    private static final String KEY_LANG    = "app.lang";

    private static SessionManager instance;
    private final SharedPreferences prefs;
    private final Gson gson = new Gson();

    private SessionManager(Context ctx) {
        prefs = ctx.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static synchronized SessionManager get(Context ctx) {
        if (instance == null) instance = new SessionManager(ctx);
        return instance;
    }

    // ── Tokens ─────────────────────────────────────────────────────────

    public String getAccessToken()  { return prefs.getString(KEY_ACCESS, null); }
    public String getRefreshToken() { return prefs.getString(KEY_REFRESH, null); }

    public void saveTokens(String access, String refresh) {
        prefs.edit().putString(KEY_ACCESS, access).putString(KEY_REFRESH, refresh).apply();
    }

    public void clearTokens() {
        prefs.edit().remove(KEY_ACCESS).remove(KEY_REFRESH).remove(KEY_PERSON).apply();
    }

    public boolean isLoggedIn() { return getAccessToken() != null; }

    // ── Person ──────────────────────────────────────────────────────────

    public Person getPerson() {
        String json = prefs.getString(KEY_PERSON, null);
        return json != null ? gson.fromJson(json, Person.class) : null;
    }

    public void savePerson(Person p) {
        prefs.edit().putString(KEY_PERSON, gson.toJson(p)).apply();
    }

    // ── Language ────────────────────────────────────────────────────────

    public String getLang() { return prefs.getString(KEY_LANG, "bn"); }

    public void setLang(String lang) {
        prefs.edit().putString(KEY_LANG, lang).apply();
    }

    public boolean isBn() { return "bn".equals(getLang()); }
}
