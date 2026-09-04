package bd.sammalani.alumni.util;

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.Locale;

/** Bilingual number, money, and date formatting — mirrors the web app's n() / money() / yr() helpers. */
public class Fmt {

    private static final String[] BN_DIGITS = {"০","১","২","৩","৪","৫","৬","৭","৮","৯"};

    /** Convert ASCII digits in a string to Bengali digits. */
    public static String toBn(String s) {
        StringBuilder sb = new StringBuilder();
        for (char c : s.toCharArray()) {
            if (c >= '0' && c <= '9') sb.append(BN_DIGITS[c - '0']);
            else sb.append(c);
        }
        return sb.toString();
    }

    /** Format an integer, optionally in Bengali digits. */
    public static String number(int value, boolean bn) {
        String s = NumberFormat.getNumberInstance(Locale.US).format(value);
        return bn ? toBn(s) : s;
    }

    /** Format a double as money (৳ with commas), optionally in Bengali. */
    public static String money(double value, boolean bn) {
        String s = "৳ " + new DecimalFormat("#,##0").format(value);
        return bn ? toBn(s) : s;
    }

    /** Format a 4-digit year, optionally in Bengali. */
    public static String year(int year, boolean bn) {
        return bn ? toBn(String.valueOf(year)) : String.valueOf(year);
    }

    /** Format a phone number, optionally in Bengali. */
    public static String phone(String phone, boolean bn) {
        return bn ? toBn(phone) : phone;
    }

    /** Abbreviate a long string with ellipsis. */
    public static String ellipsis(String s, int maxLen) {
        if (s == null) return "";
        if (s.length() <= maxLen) return s;
        return s.substring(0, maxLen - 1) + "…";
    }
}
