# WebView JavaScript interface methods must not be renamed.
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# Keep source file names in crash stack traces.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# AndroidX / Material — already covered by consumer rules but listed for clarity.
-keep class androidx.** { *; }

# ── Gson ────────────────────────────────────────────────────────────────────
# Gson uses reflection to read/write field names. Without these rules R8
# renames every field (person.name → person.a) and all API responses
# deserialise to null/empty objects silently in release builds.
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.google.gson.** { *; }
-dontwarn com.google.gson.**

# Keep every model class field intact so Gson can map JSON keys to them.
-keep class bd.sammalani.alumni.model.** { *; }

# ApiClient inner classes are also used for Gson deserialisation but live
# outside the model package — keep them explicitly.
-keep class bd.sammalani.alumni.api.ApiClient$ChallengeResult { *; }
-keep class bd.sammalani.alumni.api.ApiClient$SessionResult { *; }
-keep class bd.sammalani.alumni.api.ApiClient$Totals { *; }

# ── OkHttp / Okio ───────────────────────────────────────────────────────────
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }

# ── Glide ────────────────────────────────────────────────────────────────────
# R8 strips Glide's module discovery and transformation classes without these.
-keep public class * implements com.bumptech.glide.module.GlideModule
-keep class * extends com.bumptech.glide.module.AppGlideModule { <init>(...); }
-keep public enum com.bumptech.glide.load.ImageHeaderParser$** {
    **[] $VALUES;
    public *;
}
-keep class com.bumptech.glide.load.data.ParcelFileDescriptorRewinder$InternalRewinder {
    *** rewind();
}
-dontwarn com.bumptech.glide.**
