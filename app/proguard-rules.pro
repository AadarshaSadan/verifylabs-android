# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# --- General & Kotlin ---
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
-keep class kotlin.Metadata { *; }
-keepattributes Signature
-keepattributes Exceptions
-keepattributes *Annotation*

# --- Retrofit ---
# Keep Retrofit interfaces
-keepclasseswithmembers class * {
    @retrofit2.http.* <methods>;
}

# --- OkHttp ---
-keepattributes EnclosingMethod

# --- Gson ---
# Keep generated serialization checking
-keep class com.google.gson.reflect.TypeToken
-keep class * extends com.google.gson.reflect.TypeToken
-keep public class * implements java.lang.reflect.Type

# --- Project Specific Data Models ---
# Keep data classes used for serialization/deserialization and database
-keep class com.verifylabs.ai.data.network.** { *; }
-keep class com.verifylabs.ai.data.database.** { *; }
-keep class com.verifylabs.ai.presentation.model.** { *; }

# --- Glide ---
-keep public class * implements com.bumptech.glide.module.GlideModule
-keep public class * extends com.bumptech.glide.module.AppGlideModule
-keep public enum com.bumptech.glide.load.ImageHeaderParser$** {
  **[] $VALUES;
  public *;
}

# --- Room ---
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keepclassmembers class * {
    @androidx.room.PrimaryKey *;
}

# --- Hilt / Dagger ---
-keep class dagger.hilt.internal.aggregatedroot.codegen.** { *; }
-keep class dagger.hilt.android.internal.builders.** { *; }

# --- Lottie ---
-keep class com.airbnb.lottie.** { *; }

# --- RevenueCat ---
-keep class com.revenuecat.purchases.** { *; }

# --- Android Image Cropper ---
-keep class com.canhub.cropper.** { *; }