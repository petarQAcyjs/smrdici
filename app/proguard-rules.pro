# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# Игнорисање грешке за libpenguin.so
-dontwarn libpenguin.so
-dontwarn com.qualcomm.**
-dontwarn org.codeaurora.**
-dontwarn com.qti.**

# Игнорисање ziparchive грешака
-dontwarn ziparchive.**

# Игнорисање Android ресурс-повезаних грешака
-dontwarn android.content.res.**

# Смањивање логова за Adreno профилирање
-dontwarn Adreno.AppProfiles.**

# Спречавање лога за audit упозорења
-dontwarn audit.**

# Ignorisanje ART warning poruka
-dontwarn art.**

# Ignorisanje DexFile warning poruka
-dontwarn dalvik.system.DexFile

# Android Runtime грешке
-dontwarn AndroidRuntime.**

# Google API Manager грешке
-dontwarn GoogleApiManager
-dontwarn com.google.android.gms.common.api.**