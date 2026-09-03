# Proguard / R8 Optimization & Shrinking Rules for Nexo Player

# Keep all data models for JSON parsing & serialization
-keep class com.example.data.models.** { *; }
-keepclassmembers class com.example.data.models.** { *; }

# Serialization & Reflection attributes
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod,SourceFile,LineNumberTable

# LibVLC Native JNI & Player Classes
-keep class org.videolan.libvlc.** { *; }
-keepclassmembers class org.videolan.libvlc.** { *; }
-dontwarn org.videolan.libvlc.**

# Gson
-keep class com.google.gson.** { *; }
-keepclassmembers enum * { *; }
-dontwarn sun.misc.**

# OkHttp & Okio
-dontwarn okhttp3.**
-dontwarn okio.**
-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase

# Coil Image Loader
-keep class coil.** { *; }
-dontwarn coil.**

# ZXing QR Scanner & Generator
-keep class com.google.zxing.** { *; }
-dontwarn com.google.zxing.**

# Cling DLNA Cast
-keep class org.fourthline.cling.** { *; }
-dontwarn org.fourthline.cling.**
-keep class org.seamless.** { *; }
-dontwarn org.seamless.**

# AndroidX Security Crypto
-keep class androidx.security.crypto.** { *; }
-dontwarn androidx.security.crypto.**

# AndroidX Datastore
-keep class androidx.datastore.** { *; }
-dontwarn androidx.datastore.**
