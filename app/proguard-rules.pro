# Preserve Firestore POJO / data classes accessed via DocumentSnapshot.toObject()
-keepclassmembers class com.example.data.model.** {
    <fields>;
    <init>(...);
}
-keep class com.example.data.model.** { *; }

# Preserve Version Management data models
-keep class com.example.update.** { *; }

# Preserve Kotlin metadata and annotations used by Firestore & Coroutines
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod
-keep class kotlin.Metadata { *; }

# OkHttp / Okio
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# Google Credential Manager / Play Services
-keepnames class androidx.credentials.** { *; }
-keepnames class com.google.android.libraries.identity.googleid.** { *; }
