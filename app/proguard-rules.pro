# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Keep JavaScript interface methods - required for WebView JS bridge
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# Keep line numbers for better stack traces
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Keep WebViewClient implementations
-keep class de.radicalfishgames.crosscode.GameWebViewClient { *; }

# Keep AboutLibraries classes (required by the library)
-keep class com.mikepenz.aboutlibraries.** { *; }

# Keep Material Design R class attributes (required by AboutLibraries)
-keep class com.google.android.material.R$attr { *; }
