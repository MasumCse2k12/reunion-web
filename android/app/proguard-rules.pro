# WebView JavaScript interface methods must not be renamed.
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# Keep source file names in crash stack traces.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# AndroidX / Material — already covered by consumer rules but listed for clarity.
-keep class androidx.** { *; }
