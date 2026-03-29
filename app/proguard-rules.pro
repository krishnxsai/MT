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

# Preserve the line number information for debugging stack traces.
-keepattributes SourceFile,LineNumberTable

# Hide the original source file name.
-renamesourcefileattribute SourceFile

# ---- Razorpay Payment Gateway ----
# Keep Razorpay classes and methods for payment processing
-keep class com.razorpay.** { *; }
-dontwarn com.razorpay.**
-keepattributes *Annotation*
-keep class com.razorpay.AnalyticsEvent { *; }
-keep class com.razorpay.* { *; }
-keep interface com.razorpay.* { *; }

# Keep ProGuard annotations (required by Razorpay SDK)
-dontnote proguard.annotation.Keep
-dontnote proguard.annotation.KeepClassMembers

# ---- Firebase ----
# Firebase and Google Play Services ship their own consumer ProGuard rules.
# Only add specific keeps if crashes occur after minification.
-dontwarn com.google.firebase.**
-dontwarn com.google.android.gms.**

# ---- Firebase Firestore model classes ----
# Keep MediTrack data model classes used with Firestore
-keep class com.meditrack.app.data.model.** { *; }

# ---- Glide ----
-keep public class * implements com.bumptech.glide.module.GlideModule
-keep class * extends com.bumptech.glide.module.AppGlideModule { <init>(...); }
-keep public enum com.bumptech.glide.load.ImageHeaderParser$** {
    **[] $VALUES;
    public *;
}
-dontwarn com.bumptech.glide.**

# ---- MPAndroidChart ----
-keep class com.github.mikephil.charting.** { *; }
-dontwarn com.github.mikephil.charting.**

# ---- Credentials / Google Identity ----
-keep class androidx.credentials.** { *; }
-keep class com.google.android.libraries.identity.googleid.** { *; }
-dontwarn androidx.credentials.**

# ---- Keep ViewBinding generated classes ----
-keep class com.meditrack.app.databinding.** { *; }

# ---- Keep Kotlin Serialization (if used) ----
-keepattributes *Annotation*

# ---- General ----
-dontwarn org.bouncycastle.**
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**
