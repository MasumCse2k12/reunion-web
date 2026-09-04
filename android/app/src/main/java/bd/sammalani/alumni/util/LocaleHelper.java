package bd.sammalani.alumni.util;

import android.content.Context;
import android.content.res.Configuration;

import java.util.Locale;

/**
 * Applies the saved language preference to an Activity's base context.
 * Called from every Activity's attachBaseContext() so strings are correct
 * before any view is inflated — reliable in both debug and release (R8) builds.
 */
public class LocaleHelper {

    public static Context apply(Context context, String lang) {
        Locale locale = new Locale(lang);
        Locale.setDefault(locale);
        Configuration config = new Configuration(context.getResources().getConfiguration());
        config.setLocale(locale);
        return context.createConfigurationContext(config);
    }
}
