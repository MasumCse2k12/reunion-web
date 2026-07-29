package bd.sammalani.alumni.common.util;

import bd.sammalani.alumni.common.error.ApiException;

/**
 * Bangladeshi mobile numbers, normalised to the one form the database stores:
 * eleven digits beginning {@code 01}.
 * <p>
 * People type their number as +8801712345678, 8801712345678, 01712345678, or
 * with spaces and dashes in it, and elders often type it in Bangla numerals.
 * All of those are the same person, and the unique index can only know that if
 * exactly one form ever reaches it.
 */
public final class PhoneNumbers {

    private static final char BENGALI_ZERO = '০';

    private PhoneNumbers() {
    }

    /** @return the 11-digit form, or throws if it cannot possibly be a BD mobile */
    public static String normalize(String input) {
        String digits = digitsOnly(input);

        if (digits.startsWith("880")) {
            digits = "0" + digits.substring(3);
        } else if (digits.length() == 10 && digits.startsWith("1")) {
            digits = "0" + digits;
        }

        if (!isValid(digits)) {
            throw ApiException.badRequest("invalid_phone",
                    "That does not look like a mobile number. Example: 01712345678",
                    "এটি সঠিক মোবাইল নম্বর নয়। উদাহরণ: ০১৭১২৩৪৫৬৭৮");
        }
        return digits;
    }

    public static boolean isValid(String digits) {
        return digits != null
                && digits.length() == 11
                && digits.startsWith("01")
                && digits.charAt(2) >= '3' && digits.charAt(2) <= '9';
    }

    /** Strips separators and folds Bangla numerals onto ASCII. */
    private static String digitsOnly(String input) {
        if (input == null) {
            return "";
        }
        StringBuilder out = new StringBuilder(input.length());
        for (char c : input.toCharArray()) {
            if (c >= '0' && c <= '9') {
                out.append(c);
            } else if (c >= BENGALI_ZERO && c <= BENGALI_ZERO + 9) {
                out.append((char) ('0' + (c - BENGALI_ZERO)));
            }
        }
        return out.toString();
    }

    /** For display to someone who is not the owner: 017*****678. */
    public static String mask(String phone) {
        if (phone == null || phone.length() < 11) {
            return null;
        }
        return phone.substring(0, 3) + "*****" + phone.substring(8);
    }
}
